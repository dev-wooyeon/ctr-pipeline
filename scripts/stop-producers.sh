#!/bin/bash

echo "=== Stopping Data Producers ==="

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

# PID 파일에서 프로세스 ID 읽기
print_step "Reading producer PIDs..."
if [ -f "logs/producer_pids.txt" ]; then
    mapfile -t PRODUCER_PIDS < <(awk '{print $3}' logs/producer_pids.txt)
    echo "Found ${#PRODUCER_PIDS[@]} producer processes from pid file"
else
    print_warning "PID file not found. Trying to find producers by name..."
fi

# 프로세스 이름으로 종료 시도
print_step "Stopping producer processes..."

KILLED_COUNT=0

if [ "${#PRODUCER_PIDS[@]}" -gt 0 ]; then
    for PID in "${PRODUCER_PIDS[@]}"; do
        if [ -n "$PID" ] && ps -p "$PID" > /dev/null 2>&1; then
            kill "$PID" && KILLED_COUNT=$((KILLED_COUNT + 1))
            print_success "Producer process (PID: $PID) stopped"
        fi
    done
fi

if [ "$KILLED_COUNT" -eq 0 ]; then
    # 프로세스 이름으로 찾아서 종료
    PIDS=$(pgrep -f "impression_producer.py")
    if [ -n "$PIDS" ]; then
        kill_count=$(echo "$PIDS" | wc -w)
        echo "$PIDS" | xargs kill
        KILLED_COUNT=$((KILLED_COUNT + kill_count))
        print_success "Impression producer processes stopped"
    fi

    PIDS=$(pgrep -f "click_producer.py")
    if [ -n "$PIDS" ]; then
        kill_count=$(echo "$PIDS" | wc -w)
        echo "$PIDS" | xargs kill
        KILLED_COUNT=$((KILLED_COUNT + kill_count))
        print_success "Click producer processes stopped"
    fi
fi

# 강제 종료가 필요한 경우
sleep 2
print_step "Checking for remaining processes..."

REMAINING_IMPRESSION=$(pgrep -f "impression_producer.py")
REMAINING_CLICK=$(pgrep -f "click_producer.py")

if [ ! -z "$REMAINING_IMPRESSION" ]; then
    print_warning "Force killing remaining impression producer processes..."
    echo $REMAINING_IMPRESSION | xargs kill -9
fi

if [ ! -z "$REMAINING_CLICK" ]; then
    print_warning "Force killing remaining click producer processes..."
    echo $REMAINING_CLICK | xargs kill -9
fi

# PID 파일 정리
if [ -f "logs/producer_pids.txt" ]; then
    rm logs/producer_pids.txt
    print_success "PID file cleaned up"
fi

# 최종 확인
print_step "Final verification..."
FINAL_IMPRESSION=$(pgrep -f "impression_producer.py")
FINAL_CLICK=$(pgrep -f "click_producer.py")

if [ -z "$FINAL_IMPRESSION" ] && [ -z "$FINAL_CLICK" ]; then
    print_success "All producer processes stopped successfully"

    if [ "$KILLED_COUNT" -eq 0 ]; then
        print_warning "No producer processes were found running"
    fi
else
    print_warning "Some processes may still be running. Check manually with 'ps aux | grep producer'"
fi

echo ""
echo "Log files preserved in logs/ directory for review"
echo "To restart producers: ./scripts/start-producers.sh"
