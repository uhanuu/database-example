# MySQL Replication에서의 단조 읽기(Monotonic Reads) 전략

## 1. 문제 배경

### 발생한 문제

```
[사용자 요청 흐름]

① POST /boards       → Primary (쓰기)  → 게시글 생성 완료
② GET  /boards/{id}  → Replica1 (읽기) → 아직 복제 안 됨 → 404 or 빈 결과
```

현재 구현된 Round Robin 방식은 두 번째 요청이 다른 Replica로 가거나, 같은 Replica라도
복제 지연(Replication Lag) 상태일 수 있어 사용자가 방금 쓴 데이터를 읽지 못한다.

### 단조 읽기(Monotonic Reads)란?

> "내가 한 번 읽은 데이터는 이후 읽기에서도 반드시 다시 읽을 수 있어야 한다."

시간이 뒤로 가는 것처럼 데이터가 사라지는 현상(Read Regression)을 방지하는 일관성 보장.

---

## 2. MySQL 복제 메커니즘 이해

### 복제 흐름

```
Primary
  └─ Binary Log (binlog) 기록
        └─ Replica가 Relay Log로 가져옴 (IO Thread)
              └─ SQL Thread가 Relay Log를 실행 → 데이터 반영
```

### 핵심 개념

| 개념 | 설명 |
|------|------|
| **Binlog** | Primary의 모든 변경 사항을 기록하는 로그 |
| **GTID** (Global Transaction ID) | `server_uuid:sequence_number` 형태의 트랜잭션 식별자 (e.g. `a1b2c3:1234`) |
| **Replication Lag** | Primary 대비 Replica가 얼마나 뒤처져 있는지 (초 단위) |
| **Seconds_Behind_Source** | `SHOW REPLICA STATUS`에서 조회하는 복제 지연 시간 |

### 복제 지연 확인 (MySQL 8.0+)

```sql
-- Replica에서 실행
SHOW REPLICA STATUS\G

-- 핵심 필드
-- Seconds_Behind_Source: 0이면 동기화됨, NULL이면 복제 연결 끊김
-- Retrieved_Gtid_Set: Replica가 받은 GTID 범위
-- Executed_Gtid_Set: Replica가 실제 실행한 GTID 범위
```

---

## 3. 운영환경에서 사용하는 단조 읽기 전략들

### 전략 1: 사용자 ID 해시 기반 고정 라우팅 (현재 구현)

**구현된 방식:**

```java
// ReplicationRoutingDataSource.java
if (userId != null) {
    int index = (userId.hashCode() & Integer.MAX_VALUE) % replicaDataSourceKeys.size();
    return replicaDataSourceKeys.get(index);
}
```

**동작 원리:**
- 동일한 userId는 항상 동일한 Replica로 라우팅
- userId → hashCode → Replica 인덱스 결정 → 고정

```
userId = "123" → hashCode % 2 = 1 → replica2 (항상)
userId = "456" → hashCode % 2 = 0 → replica1 (항상)
```

**장점:**
- 구현이 단순하다
- 외부 의존성이 없다 (Redis, DB 추가 조회 불필요)
- MDC를 이미 쓰고 있다면 추가 비용 없음

**단점:**
- 진짜 단조 읽기 보장이 아님: 특정 Replica가 lag 상태일 때 사용자는 여전히 오래된 데이터를 읽을 수 있음
- Replica 수가 바뀌면 모든 사용자의 라우팅이 바뀜
- 특정 Replica에 부하가 집중될 수 있음 (userId 분포에 따라)
- `Integer.MIN_VALUE.hashCode()`의 `Math.abs()` 결과는 음수 → IOOBE 발생 가능

**운영에서 이 방식을 쓰는 경우:**
- 복제 지연이 거의 없는(< 1초) 안정적인 환경
- 구현 복잡도를 낮추고 싶을 때
- 완벽한 단조 읽기보다는 "대부분의 경우" 동작하면 충분한 서비스

---

### 전략 2: 쓰기 후 Primary 강제 라우팅 (Write-After-Read Primary Routing)

**동작 원리:**

```
① 쓰기 요청 → Primary → 완료 후 Redis에 마킹 (TTL: 3~5초)
② 읽기 요청 → Redis에 마킹 있음? → Primary로 라우팅
③ TTL 만료 → 이후 읽기는 Replica로 라우팅
```

**구현 예시:**

```java
// 쓰기 서비스에서
public void createBoard(Long userId, BoardRequest request) {
    boardRepository.save(request.toEntity());
    // 쓰기 완료 후 마킹 (TTL = 복제 지연 예상 시간 + 여유분)
    redisTemplate.opsForValue().set("wrote:" + userId, "1", 5, TimeUnit.SECONDS);
}

// ReplicationRoutingDataSource에서
protected Object determineCurrentLookupKey() {
    boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    if (!isReadOnly) return SOURCE_KEY;

    // 최근 쓰기가 있었던 유저는 Primary로
    String userId = MDC.get("userId");
    if (userId != null && redisTemplate.hasKey("wrote:" + userId)) {
        return SOURCE_KEY;
    }

    return determineReplicaKey(); // 그 외엔 Replica
}
```

**장점:**
- 쓰기 직후 읽기 보장 (Read-Your-Writes)
- Replica 수 변경에도 영향 없음

**단점:**
- Redis 의존성 추가
- DataSource 레이어에 Redis가 들어오는 것은 레이어 오염
- 쓰기가 잦은 사용자는 항상 Primary → Primary 부하 증가
- TTL을 너무 짧게 잡으면 보장 실패, 너무 길면 Primary 부하

**운영에서 이 방식을 쓰는 경우:**
- 읽기/쓰기가 섞이는 API가 있을 때
- "내가 쓴 것은 반드시 내가 읽어야 한다" (Read-Your-Writes)가 핵심 요구사항일 때

---

### 전략 3: GTID 기반 인과 읽기 (Causal Reads / Session Consistency)

MySQL 8.0+의 `SOURCE_AUTO_POSITION = 1` + GTID 모드에서 사용 가능한 가장 정확한 방법.

**동작 원리:**

```
① Primary에서 트랜잭션 커밋
② Primary의 현재 GTID 조회 → 클라이언트/Redis에 저장
③ 읽기 시 → Replica에 "이 GTID까지 실행했어?" 확인
④ 실행했으면 해당 Replica에서 읽기, 아니면 다른 Replica 시도 or Primary
```

**MySQL GTID 조회 방법:**

```sql
-- Primary에서 현재 GTID 조회
SELECT @@global.gtid_executed;
-- 결과: "a1b2c3d4-e5f6-...:1-1234"

-- Replica에서 특정 GTID까지 실행됐는지 확인
SELECT WAIT_FOR_EXECUTED_GTID_SET('a1b2c3d4-e5f6-...:1-1234', 0);
-- 0: 이미 실행됨 (즉시 반환)
-- 1: 타임아웃 (해당 GTID 미실행)
```

**Spring 구현 예시:**

```java
// 쓰기 완료 후 GTID 저장
@Transactional
public void createBoard(Long userId, BoardRequest request) {
    boardRepository.save(request.toEntity());
    // flush 후 GTID 조회
    entityManager.flush();
    String gtid = jdbcTemplate.queryForObject(
        "SELECT @@global.gtid_executed", String.class
    );
    redisTemplate.opsForValue().set("gtid:" + userId, gtid, 60, TimeUnit.SECONDS);
}

// Replica 선택 시 GTID 체크
private String determineReplicaKeyWithGtid(String userId) {
    String requiredGtid = redisTemplate.opsForValue().get("gtid:" + userId);
    if (requiredGtid == null) return roundRobinReplica(); // GTID 없으면 RR

    for (String replicaKey : replicaDataSourceKeys) {
        DataSource replica = getDataSource(replicaKey);
        if (hasExecutedGtid(replica, requiredGtid)) {
            return replicaKey;
        }
    }
    return SOURCE_KEY; // 모든 Replica가 lag 상태면 Primary
}

private boolean hasExecutedGtid(DataSource ds, String gtid) {
    try (Connection conn = ds.getConnection()) {
        // 0 = 즉시 확인 (대기 없음)
        Integer result = new JdbcTemplate(new SingleConnectionDataSource(conn, true))
            .queryForObject(
                "SELECT WAIT_FOR_EXECUTED_GTID_SET(?, 0)", Integer.class, gtid
            );
        return result != null && result == 0;
    } catch (Exception e) {
        return false;
    }
}
```

**장점:**
- 진짜 단조 읽기 보장
- Replica 수 변경에도 안전
- 어느 Replica든 따라잡으면 사용 가능

**단점:**
- 구현 복잡도 높음
- 모든 읽기마다 Replica에 GTID 체크 쿼리 추가 발생
- Redis 의존성
- GTID 모드가 활성화되어 있어야 함 (`gtid_mode = ON`)

**운영에서 이 방식을 쓰는 경우:**
- 금융, 커머스 등 데이터 일관성이 매우 중요한 서비스
- Replica 수를 동적으로 변경해야 하는 환경
- ProxySQL의 `causal_reads` 옵션도 이 원리

---

### 전략 4: Seconds_Behind_Source 기반 Replica 필터링

**동작 원리:**
- 라우팅 전 각 Replica의 복제 지연을 조회하고, 임계값 이하인 Replica만 사용

**구현 예시:**

```java
// 주기적으로 Replica 상태를 캐싱 (매 쿼리마다 조회하면 너무 비쌈)
@Scheduled(fixedDelay = 1000) // 1초마다
public void refreshReplicaLag() {
    for (String key : replicaDataSourceKeys) {
        try {
            Long lag = jdbcTemplate.queryForObject(
                "SELECT IFNULL(TIMESTAMPDIFF(SECOND, MIN(LAST_APPLIED_TRANSACTION_ORIGINAL_COMMIT_TIMESTAMP), NOW()), 0) " +
                "FROM performance_schema.replication_applier_status_by_worker",
                Long.class
            );
            replicaLagCache.put(key, lag != null ? lag : Long.MAX_VALUE);
        } catch (Exception e) {
            replicaLagCache.put(key, Long.MAX_VALUE); // 조회 실패 = 사용 불가로 처리
        }
    }
}

// 라우팅 시 lag이 낮은 Replica만 사용
private String determineReplicaKey() {
    long maxLagSeconds = 3;
    List<String> healthyReplicas = replicaDataSourceKeys.stream()
        .filter(key -> replicaLagCache.getOrDefault(key, Long.MAX_VALUE) <= maxLagSeconds)
        .toList();

    if (healthyReplicas.isEmpty()) {
        log.warn("All replicas are lagging. Routing to PRIMARY.");
        return SOURCE_KEY;
    }

    // 건강한 Replica 중에서 userId 해시 or RR
    return healthyReplicas.get(index % healthyReplicas.size());
}
```

> **주의:** `Seconds_Behind_Source`는 SQL Thread 기준이라 IO Thread의 지연은 반영 안 됨.
> 더 정확한 값은 `performance_schema.replication_applier_status_by_worker` 사용.

**장점:**
- 지연이 심한 Replica를 자동으로 배제
- 간단한 헬스체크 느낌으로 동작

**단점:**
- 단조 읽기를 보장하진 않음 (lag이 낮아도 내 데이터는 아직 없을 수 있음)
- 스케줄러 + 캐시 관리 필요

---

### 전략 5: ProxySQL (인프라 레벨 해결)

애플리케이션 코드 변경 없이 인프라에서 처리하는 방식.

```
Application (HikariCP) → ProxySQL (6033포트) → Primary / Replica
```

**설정 예시 (읽기/쓰기 분리):**

```sql
INSERT INTO mysql_query_rules (rule_id, active, match_digest, destination_hostgroup, apply)
VALUES
  (1, 1, '^SELECT.*FOR UPDATE', 10, 1),
  (2, 1, '^SELECT', 20, 1),
  (3, 1, '.*', 10, 1);

LOAD MYSQL QUERY RULES TO RUNTIME;
SAVE MYSQL QUERY RULES TO DISK;
```

---

#### ⚠️ ProxySQL causal_reads와 커넥션 풀의 함정

ProxySQL의 `causal_reads`에는 세 가지 모드가 있고, 각각 보장 범위가 다르다.

| 모드 | GTID 추적 범위 | 설명 |
|------|:---:|------|
| `none` | 없음 | 단조 읽기 보장 없음 |
| `session` | 동일 ProxySQL 세션(커넥션) 안에서만 | 커넥션 풀과 함께 쓰면 보장 깨짐 |
| `global` | ProxySQL 전체 | 모든 읽기가 가장 최근 쓰기 GTID를 기다림 |

**session 모드의 핵심 문제:**

```
[HikariCP Pool]
  Connection #1 ──→ ProxySQL Session #1
  Connection #2 ──→ ProxySQL Session #2

① 사용자 A: POST /boards  → HikariCP가 Connection #1 배정 → ProxySQL Session #1에 GTID 저장
② 사용자 A: GET  /boards  → HikariCP가 Connection #2 배정 → ProxySQL Session #2 (GTID 없음) → 보장 실패
```

ProxySQL 세션 = 물리적 커넥션이다.
HikariCP는 요청마다 풀에서 유휴 커넥션을 가져오므로, 같은 사용자라도 요청 간 다른 커넥션이 배정되면 session 모드의 GTID 상태가 사라진다.

**global 모드의 문제:**

```sql
UPDATE global_variables SET variable_value='global' WHERE variable_name='mysql-causal_reads';
```

- 모든 읽기가 시스템 전체의 최신 GTID를 기다림
- 트래픽이 많을수록 모든 읽기가 느려짐 → 사실상 운영에서 쓰기 어려움

**결론: ProxySQL만으로는 커넥션 풀 환경에서 사용자 단위 단조 읽기를 보장할 수 없다.**

---

#### ProxySQL + GTID HTTP 헤더 전달 (실제 운영 패턴)

ProxySQL의 한계를 극복하기 위해 애플리케이션이 GTID를 HTTP 레이어로 전달하는 방식을 함께 쓴다.

```
① 쓰기 응답 → 서버가 Response Header에 GTID 포함
   X-Write-Gtid: a1b2c3:1234

② 클라이언트가 이후 읽기 요청 시 Header에 포함
   X-Read-Gtid: a1b2c3:1234

③ 서버가 Header에서 GTID를 읽어 ProxySQL 세션 변수로 주입
   SET @SESSION.proxysql_gtid_to_wait = 'a1b2c3:1234';

④ ProxySQL이 해당 GTID를 따라잡은 Replica로 라우팅
```

**Spring 구현 예시:**

```java
// 쓰기 완료 후 Response Header에 GTID 삽입
@PostMapping("/boards")
public ResponseEntity<Void> createBoard(..., HttpServletResponse response) {
    boardService.createBoard(request);
    String gtid = jdbcTemplate.queryForObject("SELECT @@global.gtid_executed", String.class);
    response.setHeader("X-Write-Gtid", gtid);
    return ResponseEntity.ok().build();
}

// 읽기 요청 시 Header에서 GTID를 꺼내 세션 변수 주입
@GetMapping("/boards/{id}")
public BoardResponse getBoard(@RequestHeader(value = "X-Read-Gtid", required = false) String gtid, ...) {
    if (gtid != null) {
        // 이 커넥션에서 먼저 GTID 대기 설정 → ProxySQL이 인식
        jdbcTemplate.execute("SET @SESSION.proxysql_gtid_to_wait = '" + gtid + "'");
    }
    return boardService.getBoard(id);
}
```

이 방식도 결국 **애플리케이션 코드가 개입**해야 하므로, "인프라 레벨에서 투명하게 처리"라는 ProxySQL의 장점이 희석된다.

---

**ProxySQL의 실제 강점:**
- 단조 읽기 보장보다는 **읽기/쓰기 분리**, **커넥션 풀 관리**, **장애 감지**, **쿼리 통계** 등의 인프라 역할
- 단조 읽기는 애플리케이션 레벨(전략 2, 3)과 조합해서 해결하는 것이 일반적

**운영에서 이 방식을 쓰는 경우:**
- 읽기/쓰기 분리 라우팅을 코드 없이 인프라에서 처리하고 싶을 때
- 마이크로서비스 환경에서 모든 서비스에 라우팅 로직 중복을 피하고 싶을 때
- 커넥션 풀 통합 관리, 모니터링이 필요할 때

---

## 4. 전략 비교

| 전략 | 사용자 단위 단조 읽기 보장 | 구현 복잡도 | 외부 의존성 | Replica 스케일 변경 | 비고 |
|------|:---:|:---:|:---:|:---:|------|
| **해시 기반 고정 라우팅** (현재) | 부분적 | 낮음 | 없음 | 라우팅 변경됨 | Replica lag이면 여전히 실패 |
| **쓰기 후 Primary 강제** | Read-Your-Writes | 중간 | Redis | 안전 | TTL 내 Primary 부하 |
| **GTID 기반 인과 읽기** | 완전 | 높음 | Redis + GTID | 안전 | 읽기마다 GTID 체크 쿼리 추가 |
| **Lag 필터링** | 부분적 | 중간 | 없음 | 안전 | 단조 읽기 보장 아님 |
| **ProxySQL session** | ❌ (커넥션 풀과 충돌) | 낮음(앱) | ProxySQL | 안전 | 요청마다 다른 커넥션이면 보장 깨짐 |
| **ProxySQL global** | 완전 | 낮음(앱) | ProxySQL | 안전 | 전체 읽기 성능 저하 |
| **ProxySQL + GTID 헤더** | 완전 | 높음 | ProxySQL + 앱 코드 | 안전 | 결국 앱 코드 개입 필요 |

---

## 5. 현재 프로젝트의 선택 (해시 기반)과 한계

### 선택 이유
- 외부 의존성(Redis 등) 추가 없이 단순하게 구현 가능
- MDC는 이미 로깅 목적으로 사용 중
- 복제 지연이 크지 않은 환경에서 대부분의 케이스 커버

### 한계 및 보완 방법

**문제 1: Integer.MIN_VALUE 버그**
```java
// 버그
int index = Math.abs(userId.hashCode()) % replicaDataSourceKeys.size();

// 수정: Integer.MIN_VALUE의 abs()는 음수이므로 비트 마스킹 사용
int index = (userId.hashCode() & Integer.MAX_VALUE) % replicaDataSourceKeys.size();
```

**문제 2: Replica 수 변경 시 라우팅 변경**
- Replica를 추가/제거하면 size()가 바뀌어 모든 유저의 라우팅이 변경됨
- 해결: Consistent Hashing 적용 or 변경 주기를 새벽 등 트래픽 최소 시간대에

**문제 3: MDC 비전파 문제**
```java
// @Async, CompletableFuture, Virtual Thread 등에서 MDC 전파 필요
// TaskDecorator를 사용하여 MDC 컨텍스트를 복사
@Bean
public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(runnable -> {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (mdcContext != null) MDC.setContextMap(mdcContext);
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    });
    return executor;
}
```

---

## 6. 권장 마이그레이션 경로

```
[현재] 해시 기반 고정 라우팅
   ↓ (트래픽/요구사항 증가)
[중간] 쓰기 후 Primary 강제 라우팅 (Redis 추가)
   ↓ (규모 성장, 일관성 요구사항 강화)
[최종] ProxySQL causal_reads 또는 GTID 기반 인과 읽기
```

소규모 서비스에서는 현재 구현으로 충분하다.
트래픽이 늘어나고 복제 지연이 문제가 되기 시작하면 ProxySQL 도입을 검토하는 것이 일반적인 경로.
