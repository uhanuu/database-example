package com.example.example.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Aspect
@Component
@Order(0) // 트랜잭션보다 먼저 실행되어야 함
public class TransactionRoutingAspect {

    private static final Logger log = LoggerFactory.getLogger(TransactionRoutingAspect.class);

    @Around("@annotation(transactional)")
    public Object proceed(ProceedingJoinPoint pjp, Transactional transactional) throws Throwable {
        try {
            if (transactional.readOnly()) {
                log.info("Setting DataSource to SLAVE for read-only transaction");
                DataSourceContextHolder.setDataSourceType(DataSourceType.SLAVE);
            } else {
                log.info("Setting DataSource to MASTER for write transaction");
                DataSourceContextHolder.setDataSourceType(DataSourceType.MASTER);
            }
            return pjp.proceed();
        } finally {
            DataSourceContextHolder.clearDataSourceType();
        }
    }
}
