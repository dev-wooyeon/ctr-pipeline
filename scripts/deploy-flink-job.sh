#!/bin/bash

echo "=== Deploying Flink CTR Calculator Job ==="

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

# Flink 클러스터 상태 확인
print_step "Checking Flink cluster status..."
if ! curl -s http://localhost:8081/overview &> /dev/null; then
    print_error "Flink cluster is not accessible. Make sure Flink is running."
    echo "Try: docker-compose up -d flink-jobmanager flink-taskmanager"
    exit 1
fi

print_success "Flink cluster is accessible"

# JAR 파일 경로 설정 (shadowJar: classifier 없음)
JAR_FILE="flink-app/build/libs/ctr-calculator-1.0-SNAPSHOT.jar"

# JAR 파일 존재 확인
print_step "Checking for Flink application JAR..."
if [ ! -f "$JAR_FILE" ]; then
    print_warning "JAR file not found. Building Flink application..."

    if [ -f "flink-app/gradlew" ]; then
        GRADLE_CMD="./gradlew"
        print_step "Using project Gradle wrapper"
    elif command -v gradle &> /dev/null; then
        GRADLE_CMD="gradle"
        print_step "Using system Gradle"
    else
        print_error "Gradle not found. Cannot build Flink application."
        exit 1
    fi

    print_step "Building Flink application with Gradle..."
    (cd flink-app && $GRADLE_CMD clean shadowJar -x test)
    if [ $? -ne 0 ]; then
        print_error "Gradle build failed."
        exit 1
    fi

    print_success "Flink application built successfully"
fi

print_success "JAR file found: $JAR_FILE"

# JAR 업로드
print_step "Uploading JAR to Flink..."
UPLOAD_RESPONSE=$(curl -s -X POST -H "Expect:" -F "jarfile=@$JAR_FILE" http://localhost:8081/jars/upload)
if [ $? -ne 0 ]; then
    print_error "Failed to upload JAR"
    exit 1
fi

JAR_ID=$(echo "$UPLOAD_RESPONSE" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(data.get('filename', '').split('/')[-1])
except Exception:
    print('', file=sys.stderr)
    exit(1)
")

if [ -z "$JAR_ID" ]; then
    print_error "Failed to extract JAR ID from upload response"
    echo "Upload response: $UPLOAD_RESPONSE"
    exit 1
fi

print_success "JAR uploaded. ID: $JAR_ID"

# Job 실행
print_step "Starting CTR Calculator job..."
JOB_RESPONSE=$(curl -s -X POST "http://localhost:8081/jars/$JAR_ID/run" \
    -H "Content-Type: application/json" \
    -d '{
        "entryClass": "com.example.ctr.CtrApplication",
        "parallelism": 2,
        "programArgs": "",
        "savepointPath": null
    }')

if [ $? -ne 0 ]; then
    print_error "Failed to start job"
    exit 1
fi

# Job ID 추출
JOB_ID=$(echo $JOB_RESPONSE | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(data['jobid'])
except:
    print('', file=sys.stderr)
    exit(1)
")

if [ -z "$JOB_ID" ]; then
    print_error "Failed to extract job ID from response"
    echo "Job response: $JOB_RESPONSE"
    exit 1
fi

print_success "CTR Calculator job started successfully!"
echo ""
echo "Job Information:"
echo "================"
echo "Job ID: $JOB_ID"
echo "JAR ID: $JAR_ID"
echo ""
echo "Monitoring:"
echo "- Flink Web UI: http://localhost:8081"
echo "- Job Details: http://localhost:8081/#/job/$JOB_ID/overview"
echo "- ClickHouse HTTP: http://localhost:8123"
echo "- Superset Dashboard: http://localhost:8088"
echo ""

# Job 상태 확인
print_step "Checking job status..."
sleep 5

JOB_STATUS=$(curl -s "http://localhost:8081/jobs/$JOB_ID" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(data['state'])
except:
    print('UNKNOWN')
")

echo "Current job status: $JOB_STATUS"

if [ "$JOB_STATUS" = "RUNNING" ]; then
    print_success "Job is running successfully!"
    echo ""
    echo "Next steps:"
    echo "1. Make sure producers are running: ./scripts/start-producers.sh"
    echo "2. Monitor CTR results via Superset: http://localhost:8088 or ClickHouse HTTP: http://localhost:8123"
    echo "3. View job metrics in Flink UI: http://localhost:8081"
elif [ "$JOB_STATUS" = "CREATED" ] || [ "$JOB_STATUS" = "INITIALIZING" ]; then
    print_warning "Job is initializing. Check status in a few moments."
else
    print_warning "Job status is: $JOB_STATUS. Check Flink UI for details."
fi

echo ""
echo "To monitor job logs:"
echo "docker logs flink-taskmanager"
echo ""
echo "To cancel the job:"
echo "curl -X PATCH http://localhost:8081/jobs/$JOB_ID"
