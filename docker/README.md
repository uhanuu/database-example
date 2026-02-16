# MySQL Primary-Secondary Replication 구성

## 디렉토리 구조
```
docker/
├── docker-compose.yml
├── primary/
│   └── primary.cnf
├── secondary1/
│   └── secondary1.cnf
├── secondary2/
│   └── secondary2.cnf
└── setup-replication.sh
```

## 시작하기

### 1. Docker Compose로 MySQL 컨테이너 실행
```bash
cd docker
docker-compose up -d
```

### 2. 컨테이너 상태 확인
```bash
docker-compose ps
```

### 3. Replication 설정
```bash
chmod +x setup-replication.sh
./setup-replication.sh
```

## 포트 구성
- Primary: 3306
- Secondary1: 3307
- Secondary2: 3308

## 관리 명령어

### 로그 확인
```bash
# 전체 로그
docker-compose logs -f

# 특정 컨테이너 로그
docker-compose logs -f mysql-primary
docker-compose logs -f mysql-secondary1
docker-compose logs -f mysql-secondary2
```

### 컨테이너 중지
```bash
docker-compose down
```

### 볼륨까지 삭제 (데이터 초기화)
```bash
docker-compose down -v
```

### MySQL 접속
```bash
# Primary
docker exec -it mysql-primary mysql -uroot -p1234

# Secondary1
docker exec -it mysql-secondary1 mysql -uroot -p1234

# Secondary2
docker exec -it mysql-secondary2 mysql -uroot -p1234
```

## Replication 상태 확인

### Primary에서
```sql
SHOW MASTER STATUS;
SHOW REPLICAS;  -- MySQL 8.4+
```

### Secondary에서
```sql
SHOW REPLICA STATUS\G
```

## 주요 설정

### Primary
- server-id: 1
- GTID 활성화
- Binary Log 활성화
- 읽기/쓰기 모두 가능

### Secondary (1, 2)
- server-id: 2, 3
- GTID 활성화
- read_only, super_read_only 활성화
- 병렬 복제 (4 workers)
- Relay Log 복구 활성화
