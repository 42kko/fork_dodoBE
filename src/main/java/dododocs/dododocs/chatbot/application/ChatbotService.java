package dododocs.dododocs.chatbot.application;

import dododocs.dododocs.analyze.domain.RepoAnalyze;
import dododocs.dododocs.analyze.domain.repository.RepoAnalyzeRepository;
import dododocs.dododocs.analyze.exception.NoExistRepoAnalyzeException;
import dododocs.dododocs.chatbot.domain.ChatLog;
import dododocs.dododocs.chatbot.domain.repository.ChatLogRepository;
import dododocs.dododocs.chatbot.dto.ExternalQuestToChatbotRequest;
import dododocs.dododocs.chatbot.dto.ExternalQuestToChatbotResponse;
import dododocs.dododocs.chatbot.dto.FindChatLogResponses;
import dododocs.dododocs.chatbot.infrastructure.ExternalChatbotClient;
import dododocs.dododocs.chatbot.infrastructure.ExternalChatbotClientByWebFlux;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RequiredArgsConstructor
@Service
public class ChatbotService {
    private final ExternalChatbotClientByWebFlux externalChatbotClientByWebFlux;
    private final ExternalChatbotClient externalChatbotClient;
    private final RepoAnalyzeRepository repoAnalyzeRepository;
    private final ChatLogRepository chatLogRepository;

    public Mono<ExternalQuestToChatbotResponse> questionToChatbotAndSaveLogsByWebFlux(final long registeredRepoId, final String question) {
        return Mono.justOrEmpty(repoAnalyzeRepository.findById(registeredRepoId))
                .switchIfEmpty(Mono.error(new NoExistRepoAnalyzeException("레포지토리 정보가 존재하지 않습니다.")))
                .flatMap(repoAnalyze -> Mono.fromCallable(() -> chatLogRepository.findTop3ByRepoAnalyzeOrderBySequenceDesc(repoAnalyze))
                        .flatMapMany(Flux::fromIterable)
                        .map(chatLog -> new ExternalQuestToChatbotRequest.RecentChatLog(chatLog.getQuestion(), chatLog.getAnswer()))
                        .collectList()
                        .flatMap(recentChatLogs -> {
                            final ExternalQuestToChatbotRequest questToChatbotRequest = new ExternalQuestToChatbotRequest(
                                    repoAnalyze.getRepoUrl() + "/" + repoAnalyze.getBranchName(),
                                    question,
                                    recentChatLogs
                            );

                            return externalChatbotClientByWebFlux.questToChatbot(questToChatbotRequest)
                                    .doOnSuccess(response -> {
                                        chatLogRepository.save(new ChatLog(question, response.getAnswer(), repoAnalyze));
                                        System.out.println("Chatbot Response: " + response.getAnswer());
                                    });
                        })
                );
    }

    public ExternalQuestToChatbotResponse questionToChatbotAndSaveLogs(final long registeredRepoId, final String question) {
        final RepoAnalyze repoAnalyze = repoAnalyzeRepository.findById(registeredRepoId)
                .orElseThrow(() -> new NoExistRepoAnalyzeException("레포지토리 정보가 존재하지 않습니다."));

        final List<ChatLog> chatLogs = chatLogRepository.findTop3ByRepoAnalyzeOrderBySequenceDesc(repoAnalyze);

        final List<ExternalQuestToChatbotRequest.RecentChatLog> recentChatLogs = chatLogs.stream()
                .map(chatLog -> new ExternalQuestToChatbotRequest.RecentChatLog(chatLog.getQuestion(), chatLog.getAnswer()))
                .toList();

        final ExternalQuestToChatbotRequest questToChatbotRequest = new ExternalQuestToChatbotRequest(
                repoAnalyze.getRepoUrl() + "/" + repoAnalyze.getBranchName(),
                question,
                recentChatLogs
        );

        final ExternalQuestToChatbotResponse externalQuestToChatbotResponse = externalChatbotClient.questToChatbot(questToChatbotRequest);
        chatLogRepository.save(new ChatLog(question, externalQuestToChatbotResponse.getAnswer(), repoAnalyze));
        return externalQuestToChatbotResponse;
    }

    public FindChatLogResponses findChatbotHistory(final long registeredRepoId, final Pageable pageable) {
        final RepoAnalyze repoAnalyze = repoAnalyzeRepository.findById(registeredRepoId)
                .orElseThrow(() -> new NoExistRepoAnalyzeException("레포 정보 없습니다."));

        return new FindChatLogResponses(chatLogRepository.findByRepoAnalyzeOrderBySequenceWithPagination(repoAnalyze, pageable).getContent());
    }
}

