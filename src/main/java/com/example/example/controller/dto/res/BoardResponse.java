package com.example.example.controller.dto.res;

import com.example.example.service.dto.response.BoardServiceResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BoardResponse {
    private Long id;
    private String title;
    private String content;

    public static BoardResponse from(BoardServiceResponse serviceResponse) {
        return BoardResponse.builder()
                .id(serviceResponse.getId())
                .title(serviceResponse.getTitle())
                .content(serviceResponse.getContent())
                .build();
    }
}
