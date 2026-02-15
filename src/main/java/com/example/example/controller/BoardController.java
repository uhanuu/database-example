package com.example.example.controller;

import com.example.example.controller.dto.req.CreateBoardRequest;
import com.example.example.controller.dto.res.BoardResponse;
import com.example.example.service.BoardService;
import com.example.example.service.dto.request.CreateBoardServiceRequest;
import com.example.example.service.dto.response.BoardServiceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class BoardController {

    private final BoardService boardService;

    /**
     * 게시글 생성
     */
    @PostMapping("/boards")
    public ResponseEntity<BoardResponse> createBoard(@RequestBody CreateBoardRequest request) {
        // Controller DTO -> Service DTO 변환
        CreateBoardServiceRequest serviceRequest = CreateBoardServiceRequest.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .build();

        // Service 호출
        BoardServiceResponse serviceResponse = boardService.createBoard(serviceRequest);

        // Service DTO -> Controller DTO 변환
        BoardResponse response = BoardResponse.from(serviceResponse);

        return ResponseEntity.ok(response);
    }

    /**
     * 게시글 전체 조회
     */
    @GetMapping("/boards")
    public ResponseEntity<List<BoardResponse>> getAllBoards() {
        List<BoardServiceResponse> serviceResponses = boardService.getAllBoards();

        // Service DTO -> Controller DTO 변환
        List<BoardResponse> responses = serviceResponses.stream()
                .map(BoardResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * 게시글 단건 조회
     */
    @GetMapping("/boards/{id}")
    public ResponseEntity<BoardResponse> getBoard(@PathVariable Long id) {
        BoardServiceResponse serviceResponse = boardService.getBoard(id);
        BoardResponse response = BoardResponse.from(serviceResponse);
        return ResponseEntity.ok(response);
    }

    /**
     * 게시글 수정
     */
    @PutMapping("/boards/{id}")
    public ResponseEntity<BoardResponse> updateBoard(
            @PathVariable Long id,
            @RequestBody CreateBoardRequest request
    ) {
        CreateBoardServiceRequest serviceRequest = CreateBoardServiceRequest.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .build();

        BoardServiceResponse serviceResponse = boardService.updateBoard(id, serviceRequest);
        BoardResponse response = BoardResponse.from(serviceResponse);

        return ResponseEntity.ok(response);
    }

    /**
     * 게시글 삭제
     */
    @DeleteMapping("/boards/{id}")
    public ResponseEntity<Void> deleteBoard(@PathVariable Long id) {
        boardService.deleteBoard(id);
        return ResponseEntity.noContent().build();
    }
}
