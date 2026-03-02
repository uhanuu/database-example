package com.example.example.service;

import com.example.example.domain.entity.Board;
import com.example.example.repository.BoardRepository;
import com.example.example.service.dto.request.CreateBoardServiceRequest;
import com.example.example.service.dto.response.BoardServiceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;

    /**
     * 게시글 생성 - SOURCE DB 사용 (쓰기 작업)
     */
    @Transactional
    public BoardServiceResponse createBoard(CreateBoardServiceRequest request) {
        Board board = request.toEntity();
        Board savedBoard = boardRepository.save(board);
        return BoardServiceResponse.from(savedBoard);
    }

    /**
     * 게시글 전체 조회 - SLAVE DB 사용 (읽기 작업)
     * readOnly = true로 설정하면 자동으로 Slave DB로 라우팅
     */
    @Transactional(readOnly = true)
    public List<BoardServiceResponse> getAllBoards() {
        return boardRepository.findAll().stream()
                .map(BoardServiceResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 게시글 단건 조회 - SLAVE DB 사용
     */
    @Transactional(readOnly = true)
    public BoardServiceResponse getBoard(Long id) {
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Board not found: " + id));
        return BoardServiceResponse.from(board);
    }

    /**
     * 게시글 수정 - MASTER DB 사용 (쓰기 작업)
     */
    @Transactional
    public BoardServiceResponse updateBoard(Long id, CreateBoardServiceRequest request) {
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Board not found: " + id));

        // 엔티티 업데이트 (Dirty Checking)
        Board updatedBoard = Board.builder()
                .id(board.getId())
                .title(request.getTitle())
                .content(request.getContent())
                .build();

        Board savedBoard = boardRepository.save(updatedBoard);
        return BoardServiceResponse.from(savedBoard);
    }

    /**
     * 게시글 삭제 - MASTER DB 사용 (쓰기 작업)
     */
    @Transactional
    public void deleteBoard(Long id) {
        if (!boardRepository.existsById(id)) {
            throw new IllegalArgumentException("Board not found: " + id);
        }
        boardRepository.deleteById(id);
    }
}
