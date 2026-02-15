package com.example.example.dto;

public record ExternalApiResponse(
    Long userId,
    Long boardId,
    String title,
    String body
) {
}
