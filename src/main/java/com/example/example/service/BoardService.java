package com.example.example.service;

import com.example.example.domain.entity.Board;
import com.example.example.dto.ExternalApiResponse;
import com.example.example.dto.req.CreateBoardRequest;
import com.example.example.dto.res.BoardResponse;
import com.example.example.repository.BoardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;
    private final RestClient restClient;

    private static final String EXTERNAL_API_URL = "https://jsonplaceholder.typicode.com/posts";

    /**
     * 외부 API에서 게시글 데이터를 가져와 저장 - MASTER DB 사용
     */
    @Transactional
    public List<BoardResponse> createBoards() {
        log.info("외부 API에서 게시글 데이터를 가져오기 시작");

        // 1. 외부 API 호출
        List<ExternalApiResponse> externalData = fetchExternalApiData();
        log.info("외부 API에서 {}개의 게시글 데이터를 가져왔습니다", externalData.size());

        // 2. 도메인 엔티티로 변환
        List<Board> boards = convertToBoards(externalData);

        // 3. DB 저장
        List<Board> savedBoards = boardRepository.saveAll(boards);
        log.info("{}개의 게시글을 저장했습니다", savedBoards.size());

        // 4. 응답 DTO로 변환하여 반환
        return convertToResponses(savedBoards);
    }

    /**
     * 외부 API에서 게시글 데이터 조회
     */
    private List<ExternalApiResponse> fetchExternalApiData() {
        return restClient.get()
                .uri(EXTERNAL_API_URL)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    /**
     * 외부 API 응답을 Board 엔티티로 변환
     */
    private List<Board> convertToBoards(List<ExternalApiResponse> responses) {
        return responses.stream()
                .map(this::toBoard)
                .toList();
    }

    /**
     * ExternalApiResponse를 Board 엔티티로 변환
     */
    private Board toBoard(ExternalApiResponse response) {
        return Board.builder()
                .title(response.title())
                .content(response.body())
                .build();
    }

    /**
     * Board 엔티티 리스트를 BoardServiceResponse 리스트로 변환
     */
    private List<BoardResponse> convertToResponses(List<Board> boards) {
        return boards.stream()
                .map(BoardResponse::from)
                .toList();
    }



}
