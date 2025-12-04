# API Performance Test (k6)

k6 기반으로 `/ctr/latest` API를 호출하여 성능을 측정하고 `window_end` 변화를 추적합니다.
- 기본: 35초 동안 초당 10건(`constant-arrival-rate`) 호출
- 95% 응답 시간 1초 미만을 `threshold`로 검증
- `window_end` 변화(새 윈도우/소멸 윈도우)와 각 윈도우의 지속시간을 요약 출력

## 실행 방법

1. **k6 설치**
   ```bash
   brew install k6  # 또는 https://k6.io/docs/get-started/installation/ 참고
   ```

2. **테스트 실행**
   ```bash
   cd performance-test
   k6 run k6_api_performance_test.js \\
     -e API_URL=http://localhost:8000 \\
     -e RATE=10 \\
     -e TEST_DURATION=35s
   ```

## 주요 환경 변수
- `API_URL` (기본: `http://localhost:8000`)
- `API_ENDPOINT` (기본: `/ctr/latest`)
- `RATE` 요청/초 (기본: `10`)
- `TEST_DURATION` 부하 지속 시간 (기본: `35s`, `10s`/`1m`/`1h` 형식 지원)
- `VUS` 사전 할당 VU (기본: `2`), `MAX_VUS` (기본: `20`)

## 결과
- 표준 출력으로 `window_end` 변동 로그, 각 윈도우 지속시간, 성공률, 응답 지표(p95 포함) 제공
- k6 기본 요약도 함께 출력되어 실패율/지연 SLA를 한눈에 확인할 수 있습니다.
