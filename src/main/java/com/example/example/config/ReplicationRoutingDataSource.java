package com.example.example.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ReplicationRoutingDataSource extends AbstractRoutingDataSource {

    private final AtomicInteger counter = new AtomicInteger(0);
    private final List<String> slaveDataSourceKeys;

    public ReplicationRoutingDataSource(List<String> slaveDataSourceKeys) {
        this.slaveDataSourceKeys = slaveDataSourceKeys;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        DataSourceType dataSourceType = DataSourceContextHolder.getDataSourceType();

        if (dataSourceType == DataSourceType.SLAVE) {
            // Round-robin 방식으로 Slave 서버 로드밸런싱
            int index = counter.getAndIncrement() % slaveDataSourceKeys.size();
            String selectedSlave = slaveDataSourceKeys.get(index);
            log.debug("Routing to SLAVE DataSource: {}", selectedSlave);
            return selectedSlave;
        }

        log.debug("Routing to MASTER DataSource");
        return "master";
    }
}
