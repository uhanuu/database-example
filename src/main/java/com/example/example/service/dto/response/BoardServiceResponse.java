package com.example.example.service.dto.response;

import com.example.example.domain.entity.Board;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BoardServiceResponse {
    private Long id;
    private String title;
    private String content;

    public static BoardServiceResponse from(Board board) {
        return BoardServiceResponse.builder()
                .id(board.getId())
                .title(board.getTitle())
                .content(board.getContent())
                .build();
    }
}
