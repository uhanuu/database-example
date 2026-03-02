package com.example.example.config;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ReplicationRoutingDataSource extends AbstractRoutingDataSource {

    // MDC key: Filter(e.g. AuthenticationFilter)에서 요청마다 MDC.put("userId", userId) 로 주입한다고 가정
    private static final String MDC_USER_ID_KEY = "userId";

    private static final String SOURCE_KEY = "source";
    private final AtomicInteger replicaIndex = new AtomicInteger(0);
    private final List<String> replicaDataSourceKeys;

    public ReplicationRoutingDataSource(List<String> replicaDataSourceKeys) {
        this.replicaDataSourceKeys = replicaDataSourceKeys;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();

        if (isReadOnly) {
            return determineReplicaKey();
        }

        log.debug("Routing to SOURCE DataSource");
        return SOURCE_KEY;
    }

    /**
     * 단조 읽기(Monotonic Reads) 전략으로 Replica를 선택한다.
     *
     * <p>세션 ID 대신 User ID 기반으로 Replica를 선택하는 이유:
     * 세션은 로그아웃/재로그인, 브라우저 변경 등으로 달라질 수 있어
     * 동일 사용자라도 서로 다른 Replica로 라우팅될 수 있다.
     * User ID는 사용자가 바뀌지 않는 한 일정하므로 단조 읽기 보장에 적합하다.
     *
     * <p>MDC에서 userId를 읽는 이유:
     * Filter(e.g. AuthenticationFilter)가 인증 처리 후 MDC.put("userId", userId)로
     * 현재 스레드의 MDC에 사용자 ID를 주입하면, DataSource 레이어에서 별도의
     * 웹 의존성(HttpServletRequest 등) 없이 사용자 ID를 안전하게 조회할 수 있다.
     *
     * <p>비인증 요청(userId가 없는 경우) 또는 HTTP 컨텍스트 외부(배치 등)에서는
     * Round-Robin으로 폴백하여 부하를 분산한다.
     */
    private String determineReplicaKey() {
        String userId = MDC.get(MDC_USER_ID_KEY);

        if (userId != null) {
            // 동일한 User ID는 항상 같은 Replica로 해시되어 단조 읽기를 보장
            int index = Math.abs(userId.hashCode()) % replicaDataSourceKeys.size();
            String selectedReplica = replicaDataSourceKeys.get(index);
            log.debug("Monotonic Read - UserId: {}, Routing to REPLICA DataSource: {}", userId, selectedReplica);
            return selectedReplica;
        }

        // 비인증 요청 또는 HTTP 컨텍스트 외부(배치 등)는 Round-Robin으로 폴백
        int index = replicaIndex.getAndIncrement() % replicaDataSourceKeys.size();
        String selected = replicaDataSourceKeys.get(index);
        log.debug("Round-Robin fallback - Routing to REPLICA DataSource: {}", selected);
        return selected;
    }
}
