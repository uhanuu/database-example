package com.example.example.controller;

import com.example.example.dto.res.BoardResponse;
import com.example.example.service.BoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class BoardController {

    private final BoardService boardService;

    /**
     * 게시글 생성
     */
    @PostMapping("/boards/bulk")
    public ResponseEntity<List<BoardResponse>> createBoard() {
        List<BoardResponse> serviceResponse = boardService.createBoards();
        return ResponseEntity.ok(serviceResponse);
    }
}
