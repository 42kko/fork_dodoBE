package dododocs.dododocs.member.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class FindRepoNameListResponse {
    private List<String> names;

    private FindRepoNameListResponse() {
    }

    public FindRepoNameListResponse(List<String> names) {
        this.names = names;
    }
}
