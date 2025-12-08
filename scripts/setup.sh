#!/bin/bash

echo "=== Kafka-Flink CTR Calculator Setup ==="

# 전체 환경 셋업 스크립트

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Docker 확인
print_step "Checking Docker installation..."
if ! command -v docker &> /dev/null; then
    print_error "Docker is not installed. Please install Docker Desktop."
    exit 1
fi

if ! docker info &> /dev/null; then
    print_error "Docker is not running. Please start Docker Desktop."
    exit 1
fi

print_success "Docker is installed and running"

# Docker Compose 확인
print_step "Checking Docker Compose installation..."
if ! command -v docker compose &> /dev/null; then
    print_error "Docker Compose is not installed. Please install Docker Compose."
    exit 1
fi

print_success "Docker Compose is installed"

# Python 확인
print_step "Checking Python installation..."
if ! command -v python3 &> /dev/null; then
    print_error "Python 3 is not installed. Please install Python 3."
    exit 1
fi

print_success "Python 3 is installed"

# JDK 17 고정 (macOS: /usr/libexec/java_home 사용)
JAVA_17_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null)
if [ -n "$JAVA_17_HOME" ]; then
    export JAVA_HOME="$JAVA_17_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
    print_success "JAVA_HOME set to JDK 17 ($JAVA_HOME)"
else
    print_warning "JDK 17 not found via /usr/libexec/java_home -v 17. Please install temurin-17 or set JAVA_HOME manually."
fi

# Gradle 확인
print_step "Checking Gradle installation..."
GRADLE_CMD=""
if [ -x "flink-app/gradlew" ]; then
    GRADLE_CMD="./gradlew"
    print_success "Using project Gradle wrapper"
elif command -v gradle &> /dev/null; then
    GRADLE_CMD="gradle"
    print_success "Using system Gradle"
else
    print_warning "Gradle is not installed. Flink app build will be skipped."
fi

# 기존 컨테이너 중지 및 제거
print_step "Stopping and removing existing containers..."
docker compose down -v
print_success "Existing containers stopped and removed"

# 네트워크 정리
print_step "Cleaning up Docker networks..."
docker network prune -f &> /dev/null
print_success "Docker networks cleaned up"

# Flink 애플리케이션 빌드 (Gradle)
if [ -n "$GRADLE_CMD" ]; then
    print_step "Building Flink application (Gradle)..."
    (cd flink-app && $GRADLE_CMD clean shadowJar -x test)
    if [ $? -eq 0 ]; then
        print_success "Flink application built successfully"
    else
        print_error "Failed to build Flink application"
        exit 1
    fi
else
    print_warning "Skipping Flink app build due to missing Gradle"
fi

# Python 의존성 설치
print_step "Installing Python dependencies..."
cd producers

# uv 설치 확인
if command -v uv &> /dev/null; then
    print_step "Using uv for dependency management..."
    uv sync
    if [ $? -eq 0 ]; then
        print_success "Python dependencies installed with uv"
    else
        print_error "Failed to install dependencies with uv"
        exit 1
    fi
elif [ -f "requirements.txt" ]; then
    print_step "Using pip for dependency management..."
    pip3 install -r requirements.txt
    if [ $? -eq 0 ]; then
        print_success "Python dependencies installed with pip"
    else
        print_error "Failed to install Python dependencies with pip"
        exit 1
    fi
else
    print_error "Neither uv nor requirements.txt found"
    exit 1
fi
cd ..

# Docker 서비스 시작
FLINK_TASKMANAGER_INSTANCES=3
TASK_MANAGER_SLOTS=2
TOTAL_AVAILABLE_SLOTS=$((FLINK_TASKMANAGER_INSTANCES * TASK_MANAGER_SLOTS))
print_step "Starting Docker services with $FLINK_TASKMANAGER_INSTANCES TaskManagers (≈$TOTAL_AVAILABLE_SLOTS slots)..."
docker compose up -d --build --scale flink-taskmanager=$FLINK_TASKMANAGER_INSTANCES

if [ $? -ne 0 ]; then
    print_error "Failed to start Docker services"
    exit 1
fi

print_step "Ensuring Flink TaskManagers provide $TOTAL_AVAILABLE_SLOTS slots..."
echo "Configured slots: $FLINK_TASKMANAGER_INSTANCES TM × $TASK_MANAGER_SLOTS slots = $TOTAL_AVAILABLE_SLOTS total slots"

# 서비스 시작 대기
print_step "Waiting for services to start..."
sleep 30

# Kafka 클러스터 상태 확인
print_step "Checking Kafka cluster status..."
for i in {1..30}; do
    if docker exec kafka1 kafka-topics --bootstrap-server kafka1:29092 --list &> /dev/null; then
        print_success "Kafka cluster is ready"
        break
    fi
    if [ $i -eq 30 ]; then
        print_error "Kafka cluster failed to start"
        exit 1
    fi
    echo "Waiting for Kafka... ($i/30)"
    sleep 2
done

# Kafka 토픽 생성
# Kafka 토픽 생성
print_step "Creating Kafka topics..."

# impressions 토픽
docker exec kafka1 kafka-topics \
    --create \
    --bootstrap-server kafka1:29092 \
    --replication-factor 3 \
    --partitions 3 \
    --topic impressions \
    --if-not-exists

if [ $? -eq 0 ]; then
    print_success "Topic 'impressions' created (or already exists)"
else
    print_error "Failed to create topic 'impressions'"
    exit 1
fi

# clicks 토픽
docker exec kafka1 kafka-topics \
    --create \
    --bootstrap-server kafka1:29092 \
    --replication-factor 3 \
    --partitions 3 \
    --topic clicks \
    --if-not-exists

if [ $? -eq 0 ]; then
    print_success "Topic 'clicks' created (or already exists)"
else
    print_error "Failed to create topic 'clicks'"
    exit 1
fi

# 토픽 리스트 확인
print_step "Verifying topics..."
docker exec kafka1 kafka-topics --bootstrap-server kafka1:29092 --list

# ClickHouse schema 및 Materialized View 초기화
print_step "Initializing ClickHouse schema and materialized views..."
chmod +x scripts/init-clickhouse.sh
./scripts/init-clickhouse.sh
print_success "ClickHouse schema and materialized views initialized"



# Flink Job 배포
print_step "Deploying Flink Job..."
chmod +x scripts/deploy-flink-job.sh
./scripts/deploy-flink-job.sh

# Data Producer 시작
print_step "Starting Data Producers..."
chmod +x scripts/start-producers.sh
./scripts/start-producers.sh

# 서비스 상태 확인
print_step "Checking service status..."
echo ""
echo "Services Status:"
echo "=================="
echo "Kafka UI: http://localhost:8080"
echo "Flink Web UI: http://localhost:8081"
echo "ClickHouse HTTP: http://localhost:8123"
echo "TABiX (ClickHouse UI): http://localhost:8082"
echo "Superset Dashboard: http://localhost:8088"
echo "Grafana Dashboard: http://localhost:3000"
echo "Prometheus: http://localhost:19090"
echo ""

print_success "Setup completed successfully! The entire pipeline is running."
echo ""
echo "You can now:"
echo "1. Explore CTR materialized views via Superset: http://localhost:8088"
echo "2. Query ClickHouse via TABiX: http://localhost:8082"
echo "3. Monitor metrics via Grafana: http://localhost:3000"
echo "4. View the running Flink job: http://localhost:8081"
