package com.example.example.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ReplicationRoutingDataSource extends AbstractRoutingDataSource {

    private static final String MASTER_KEY = "master";
    private final AtomicInteger slaveIndex = new AtomicInteger(0);
    private final List<String> slaveDataSourceKeys;

    public ReplicationRoutingDataSource(List<String> slaveDataSourceKeys) {
        this.slaveDataSourceKeys = slaveDataSourceKeys;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        // Spring의 TransactionSynchronizationManager를 활용하여 readOnly 속성 확인
        boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();

        if (isReadOnly) {
            // Round-robin 방식으로 Slave 서버 로드밸런싱
            int index = slaveIndex.getAndIncrement() % slaveDataSourceKeys.size();
            String selectedSlave = slaveDataSourceKeys.get(index);
            log.debug("Routing to SLAVE DataSource: {}", selectedSlave);
            return selectedSlave;
        }

        log.debug("Routing to MASTER DataSource");
        return MASTER_KEY;
    }
}
