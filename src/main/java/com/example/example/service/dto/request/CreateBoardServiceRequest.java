package com.example.example.service.dto.request;

import com.example.example.domain.entity.Board;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CreateBoardServiceRequest {
    private String title;
    private String content;

    public Board toEntity() {
        return Board.builder()
                .title(title)
                .content(content)
                .build();
    }
}
