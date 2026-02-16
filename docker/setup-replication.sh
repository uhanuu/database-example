#!/bin/bash

set -e

echo "====================================="
echo "MySQL Replication 설정 시작"
echo "====================================="

# Primary에 복제 계정 생성
echo ""
echo "[1/4] Primary에 복제 계정 생성..."
docker exec -i mysql-primary mysql -uroot -p1234 <<EOF
CREATE USER IF NOT EXISTS 'repl'@'%' IDENTIFIED BY 'repl1234';
GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';
FLUSH PRIVILEGES;
SELECT user, host FROM mysql.user WHERE user = 'repl';
EOF

# Primary 상태 확인
echo ""
echo "[2/4] Primary 상태 확인..."
docker exec mysql-primary mysql -uroot -p1234 -e "SHOW BINARY LOG STATUS\G"

# Secondary1 Replication 설정
echo ""
echo "[3/4] Secondary1 Replication 설정..."
docker exec -i mysql-secondary1 mysql -uroot -p1234 <<EOF
STOP REPLICA;
SET GLOBAL super_read_only = ON;
CHANGE REPLICATION SOURCE TO
    SOURCE_HOST='mysql-primary',
    SOURCE_PORT=3306,
    SOURCE_USER='repl',
    SOURCE_PASSWORD='repl1234',
    SOURCE_AUTO_POSITION=1,
    GET_SOURCE_PUBLIC_KEY=1;
START REPLICA;
SET GLOBAL super_read_only = ON;
SHOW REPLICA STATUS\G
EOF

# Secondary2 Replication 설정
echo ""
echo "[4/4] Secondary2 Replication 설정..."
docker exec -i mysql-secondary2 mysql -uroot -p1234 <<EOF
STOP REPLICA;
SET GLOBAL super_read_only = ON;
CHANGE REPLICATION SOURCE TO
    SOURCE_HOST='mysql-primary',
    SOURCE_PORT=3306,
    SOURCE_USER='repl',
    SOURCE_PASSWORD='repl1234',
    SOURCE_AUTO_POSITION=1,
    GET_SOURCE_PUBLIC_KEY=1;
START REPLICA;
SHOW REPLICA STATUS\G
EOF

echo ""
echo "====================================="
echo "Replication 설정 완료!"
echo "====================================="
echo ""
echo "복제 상태 확인:"
echo "  docker exec mysql-secondary1 mysql -uroot -p1234 -e \"SHOW REPLICA STATUS\G\""
echo "  docker exec mysql-secondary2 mysql -uroot -p1234 -e \"SHOW REPLICA STATUS\G\""
echo ""
echo "테스트 데이터베이스 생성 (Primary에서):"
echo "  docker exec -i mysql-primary mysql -uroot -p1234 <<< \"CREATE DATABASE regram;\""
echo "  docker exec -i mysql-secondary1 mysql -uroot -p1234 <<< \"SHOW DATABASES;\""
echo "  docker exec -i mysql-secondary2 mysql -uroot -p1234 <<< \"SHOW DATABASES;\""
