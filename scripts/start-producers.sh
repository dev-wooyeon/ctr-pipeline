#!/bin/bash

echo "=== Starting Data Producers ==="

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

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Python 의존성 확인
print_step "Checking Python dependencies..."
cd producers

# uv로 Python 실행
if command -v uv &> /dev/null && [ -f "pyproject.toml" ]; then
    print_step "Using uv environment..."
    PYTHON_CMD="uv run"
else
    # Fallback to python3
    PYTHON_CMD="python3"
    print_warning "uv not found, using python3"
fi

# Kafka 클러스터 연결 확인
print_step "Checking Kafka cluster connection..."
if ! docker exec kafka2 kafka-topics --bootstrap-server kafka2:29093 --list | grep -E "(impressions|clicks)" &> /dev/null; then
    print_error "Required Kafka topics not found. Please run ./scripts/create-topics.sh first"
    exit 1
fi

print_success "Kafka topics are available"

# 기존 프로듀서 프로세스 종료
print_step "Stopping existing producer processes..."
pkill -f "impression_producer.py" &> /dev/null
pkill -f "click_producer.py" &> /dev/null
sleep 2
print_success "Existing producers stopped"

# 로그 디렉토리 생성
mkdir -p ../logs
PID_FILE="../logs/producer_pids.txt"
: > "$PID_FILE"

# --- MULTI-INSTANCE PRODUCERS START (High Load) ---
NUM_INSTANCES=4

# Impression Producers
print_step "Starting impression producer ($NUM_INSTANCES instances)..."
for i in $(seq 1 $NUM_INSTANCES); do
    $PYTHON_CMD impression_producer.py > ../logs/impression_producer_$i.log 2>&1 &
    PID=$!
    echo "impression $i $PID" >> "$PID_FILE"
    print_success "Impression producer #$i started (PID: $PID)"
done

# Click Producers
print_step "Starting click producer ($NUM_INSTANCES instances)..."
for i in $(seq 1 $NUM_INSTANCES); do
    $PYTHON_CMD click_producer.py > ../logs/click_producer_$i.log 2>&1 &
    PID=$!
    echo "click $i $PID" >> "$PID_FILE"
    print_success "Click producer #$i started (PID: $PID)"
done
# --------------------------------------------------

cd ..

print_success "Data producers started successfully!"
echo ""
echo "Log files are in logs/"
echo "- Impression Producer: logs/impression_producer_*.log"
echo "- Click Producer: logs/click_producer_*.log"
echo ""
echo "Monitoring commands:"
echo "- View logs: tail -f logs/impression_producer_1.log"
echo "- View live topics: docker exec kafka2 kafka-console-consumer --bootstrap-server kafka2:29093 --topic impressions --from-beginning"
echo ""
echo "To stop producers: ./scripts/stop-producers.sh"
echo ""
