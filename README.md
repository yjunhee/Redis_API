#  1,000명 동시 접속 환경의 선착순 쿠폰 발급 API

> RDBMS,  Redis 동시성 제어 비교
> 
> **1,000명의 동시 요청 환경에서 500장의 선착순 쿠폰을 정확하게 발급하고,
> 동시성 제어 방식에 따른 성능과 정합성의 Trade-off를 비교 검증한다.**

---

## 개요

선착순 이벤트는 단시간에 대규모 트래픽이 집중되어 **동시성 이슈(Race Condition)** 및 **데이터베이스 I/O 병목 현상**이 발생하는 대표적인 상황이다.

본 프로젝트에서는 **RDBMS(MySQL) 기반의 동시성 제어** 방식과 **Redis 기반의 In-Memory 동시성 제어** 방식을 구현하고, 부하 테스트 도구인 **k6**를 활용해 성능(TPS, Latency) 및 정합성을 비교 검증한다.

### 핵심 목표
- **데이터 정합성 (Data Integrity)**: 1,000건 이상의 동시 요청 환경에서 초과 발급 없이 **정확히 500건만 발급** (Race Condition 차단)
- **성능향상 및 시스템 안정성**: DB Lock 병목을 제거하여 **TPS 극대화 및 응답 속도(Latency) 최소화**.

---

### 기술 스택

| 분류 | 기술 스택 |
| :--- | :--- |
| **Language / Framework** | Java 17, Spring Boot 3.3.5, Spring Data JPA |
| **Database** | MySQL 8.0, Redis v4.2.3, Apache Kafka 4.3.1 |
| **Load Test** | k6 v2.2.0 |

---

### 시스템 아키텍처

<img width="1536" height="1024" alt="Image" src="https://github.com/user-attachments/assets/606b3a4d-bb09-4da0-86ea-f7da3ab961c6" />

## K6 부하 테스트
### 테스트 환경
| 항목 | 환경 |
|---|---|
| OS | Windows 11 |
| CPU | i3-9100F |
| RAM | 16GB |
| Redis | Memurai |
| Kafka | 4.3.1 |
| k6 | v2.2.0 |
| Tomcat Max Threads | 1,000 | 

### 테스트 시나리오
> **Target Scenario**: 
> - executor: shared-iterations
> - iterations: 1000
> - vus (Virtual Users): 1000

#### **Target API**: `POST /coupons/1/issue?userId={userId}`, `POST /coupons/1/issue/redis?userId={userId}`
#### **검증 기준**: HTTP 200 및 MySQL CouponIssue 테이블 체크
---

### RDBMS(No Lock) 테스트 결과
<img width="724" height="421" alt="Image" src="https://github.com/user-attachments/assets/61c43217-2a70-489b-8274-1a3e8a2fd989" />

| 지표 (Metrics) | 측정 결과 수치 | 비고 및 상태 |
| :--- | :--- | :--- |
| **Total Requests** | 1,000 req | 총 요청 건수 |
| **Throughput (TPS)** | **243.19 req/s** | 초당 처리량 |
| **HTTP 200** | **54.9% (549건)** | 쿠폰 발급 성공 |
| **HTTP 400** | **45.1% (451건)** | 쿠폰 발급 실패 |
| **p(95) Latency** | **3.89s(3,890ms)** | 상위 5% 요청의 응답 시간 |
| **Max Latency** | **3.97s (3,970ms)** | 최대 응답 시간 |
| **데이터 정합성 (Integrity)** | **49건 초과 발급** | Race Condition 발생  |

---

### 결과 분석

> ####  Race Condition으로 인한 초과 발급
> DB에서 `조회 → 검증 → 발급`과정을 처리하는데, 
> 여러 요청이 동시에 현재 발급 수량을 읽고 발급 가능으로 판단하여 **Race Condition(49건의 초과 발급 발생)** 이 발생한다.

> ####  동시성 환경에서의 성능 한계 
> Lock을 사용하지 않았음에도 1,000개의 동시 요청으로 인한 
> **DB Connection Pool 경합 및 I/O 병목**으로 TPS가 **243.19 req/s** 수준에 머물렀다.

---

### RDBMS(Pessimistic Lock) 테스트 결과
<img width="732" height="415" alt="Image" src="https://github.com/user-attachments/assets/12a7a8c6-2646-416b-a4ec-2a0f1223a15e" />

| 지표 (Metrics) | 측정 결과 수치 | 비고 및 상태 |
| :--- | :--- | :--- |
| **Total Requests** | 1,000 req | 총 요청 건수 |
| **Throughput (TPS)** | **170.91 req/s** | 초당 처리량 |
| **HTTP 200** | **50% (500건)** | 쿠폰 발급 성공 |
| **HTTP 400** | **50% (500건)** | 쿠폰 발급 실패 |
| **p(95) Latency** | **5.61s(5,610ms)** | 상위 5% 요청의 응답 시간 |
| **Max Latency** | **5.72s (5,720ms)** | 최대 응답 시간 |
| **데이터 정합성 (Integrity)** | **초과 발급 X** | 정합성 만족  |

---

### 결과 분석

> #### 비관적 락(Pessimistic Lock)을 통한 정합성 확보
> 쿠폰 레코드에 비관적 락(Pessimistic Lock)을 적용하여 트랜잭션을 순차 처리함으로써 
> **정확히 500건**만 발급하여 정합성 문제를 해결했다.

> #### Lock Queueing으로 인한 응답 지연
> 동시 요청이 Lock 획득을 위해 대기(Queueing)하면서 
> **No Lock 대비 TPS는 29.7% 감소(170.91 req/s)했고, p(95) Latency는 44.2% 증가(5.61s)** 하는 병목이 발생했다.

---

### Redis 테스트 결과
<img width="703" height="417" alt="Image" src="https://github.com/user-attachments/assets/46569dee-715c-41b8-819b-2f516ad7dab0" />

| 지표 (Metrics) | 측정 결과 수치 | 비고 및 상태 |
| :--- | :--- | :--- |
| **Total Requests** | 1,000 req | 총 요청 건수 |
| **Throughput (TPS)** | **441.66 req/s** | 초당 처리량 |
| **HTTP 200** | **50% (500건)** | 쿠폰 발급 성공 |
| **HTTP 400** | **50% (500건)** | 쿠폰 발급 실패 |
| **p(95) Latency** | **2.11s(2,110ms)** | 상위 5% 요청의 응답 시간 |
| **Max Latency** | **2.12s (2,120ms)** | 최대 응답 시간 |
| **데이터 정합성 (Integrity)** | **초과 발급 X** | 정합성 만족  |
---

### 결과 분석

> #### 초기 Redis RTT로 인한 정합성 문제
> 싱글 스레드 기반 Redis를 도입했으나, `조회 → 검증 → 발급` 명령 간 **네트워크 RTT(Round Trip Time)**발생으로 인해 명령 사이에 다른 요청들이 침범하면서 Race Condition이 발생하여 정합성이 깨지는 현상이 발생했다. 이를 해결하기 위해 중복 검증과 `조회 → 검증 → 발급` 과정을  
> **Lua Script로 묶어 원자적(Atomic) 연산으로** 처리하게 함으로써 In-Memory 단에서 데이터 정합성을 확보했다.

> #### DB Synchronous Write 병목으로 인한 응답 지연
> Redis를 활용해 In-Memory 단에서 원자적(Atomic) 연산을 처리하더라도, HTTP 요청 주기 내에서 DB에 동기(Synchronous) 방식으로 데이터를 저장하면  **RDBMS I/O 병목 및 Connection Pool 고갈**로 인해 전체 응답 지연이 발생하였다. 이를 해결하기 위해 **Kafka 메시지 큐**를 도입해 DB 쓰기 트랜잭션을 **비동기화**했 다. Redis 검증 직후 Kafka로 이벤트를 발행하고 클라이언트에게 즉시 응답(Early Return)하여 응답 시간을 최소화했다. 또한 **Kafka Consumer가 DB 저장을 HTTP 요청 처리 경로에서 밖에서 비동기로 처리하게 하여 DB I/O 병목을 격리**하고 메시지 내구성(Durability)을 확보했다.

> #### TPS 및 Latency 개선
> DB에 직접 조회/수정 대신 Redis In-Memory 연산 및 Kafka 메시지 발행만 수행하여 응답 시간을 최소화하여, 
> Pessimistic Lock 대비 처리량은 **170.91 req/s → 441.66req/s로  약158.39% 증가**했으며, p(95) Latency는 **5.61s → 2.11s로 약 62.4%감소**했다.

## 최종 성능 비교 및 결론
| 제어 방식 (Architecture) | TPS (초당 처리량) | p(95) Latency (응답 속도) | 데이터 정합성 (Integrity) |
| :--- | :--- | :--- | :--- |
| **RDBMS (No Lock)** | **243.19 req/s** | **3.89s(3,890ms)** | 실패(49건 초과발급) |
| **RDBMS (Pessimistic Lock)** | **170.91 req/s -29.7%** | **5.61s(5,610ms) +44.2%** | 성공 |
| **Redis + Kafka (In-Memory)** | **441.66 req/s +81.6%** | **2.11s(2,110ms) -45.8%** | 성공 |

※ 모든 증감률은 No Lock 기준
### No Lock 방식의 정합성 붕괴 및 Pessimistic Lock의 성능 한계
동시성 제어가 없는 RDBMS(No Lock) 환경에서는 높은 TPS(243.19 req/s)를 기록했으나, Race Condition 발생으로 인해 500개 제한 대비 **49건이 초과 발급(실패)되며 데이터 정합성이 훼손**되었다. 이를 해결하기 위해 **비관적 락(Pessimistic Lock)**을 적용하여 500건 데이터 정합성을 확보했으나, **Lock Queueing으로 인해 No Lock 대비 TPS가 29.7% 감소(170.91 req/s)하고 p(95) 응답 시간은 44.2% 지연(5.61s)되는 성능 병목**을 확인했다.

### Redis + Kafka 도입을 통한 병목 분리 및 성능 개선
Redis 싱글 스레드 특성과 Lua Script의 원자적(Atomic) 연산을 결합하여 500건 발급 제한을 초과하지 않고 정합성을 확보했다. 동시에 Kafka 비동기 파이프라인을 통해 HTTP 요청 주기의 DB I/O 부하를 HTTP 요청 처리 경로에서 분리함으로써, **No Lock 기준 대비 TPS는 81.6% 상승(441.66 req/s), p(95) 응답 시간은 45.8% 단축(2.11s)이라는 성능 개선**을 확인했다. (Pessimistic Lock 대비 TPS +158.4%, 응답 시간 -62.4%)

### 아키텍처 인사이트
결과적으로 선착순 이벤트 시스템에서는 RDBMS Lock에 의존하는 방식보다, **Redis 싱글 스레드 기반의 In-Memory 동시성 제어와 Kafka 이벤트 드라이븐 비동기 처리(Eventual Consistency)를 결합한 아키텍처**가 본 테스트 환경과 요구사항에 가장 높은 처리량과 낮은 p(95)Latency를 보였다. 다만 DB 영속화를 비동기화하면서 Eventual Consistency라는 Trade Off와 **Redis/Kafka 장애에 대한 복구 전략**이 필요함을 확인했다.

---

## 장애 대응 및 트러블슈팅

### Redis 서버 장애 대응

**문제 상황**
> Redis 장애 발생 시 쿠폰 발급 로직 중단

**테스트**
> Redis 프로세스 강제 종료

**검증**
> scenarios: 
> - executor: 'constant-arrival-rate',
> - rate:  500,
> - timeUnit: '1s',
> - duration: '30s',
> - preAllocatedVUs: 100,
> - maxVUs: 1000,

`0~10초 Redis 정상 → 10초 Redis stop → 20초 Redis start`

### 결과
<img width="404" height="197" alt="Image" src="https://github.com/user-attachments/assets/cd520926-0165-4895-8e98-7dcfa3a17b6e" />

<img width="353" height="179" alt="Image" src="https://github.com/user-attachments/assets/923fdd17-b686-4dd3-97f5-19c39a5fabdc" />

#### 장애 구간(10~20초) 예외 처리 및 Latency 영향

> Redis stop 구간 동안 들어온 요청은 애플리케이션 예외 핸들링을 통해 503 Service Unavailable 응답을 반환하여 차단했다.

> Redis Connection Timeout 대기 시간으로 인해 Max Latency 18.54s가 발생하였으며, Threads Blocking으로 인한 k6의 dropped_iterations(8,378건)가 관측되었다.

#### 데이터 정합성 확보

> 설정된 선착순 수량 500개에 대해 k6 지표상 200 OK 500건, DB 실제 저장 500건으로 초과 발급 및 유실 없이 완벽히 일치했다.


>  Redis 재부팅 후 메모리가 비어있는 상태였지만, Kafka Consumer 및 DB 단의 수량 검증 로직이 정확히 작동하여 목표 수량 500개가 채워진 즉시 이후 들어온 요청들을 전부 400 Bad Request로 차단했다. (거짓 성공 응답 0건) 
> 또한 DB레벨에서 (coupon_id, user_id)로 unique 제약 조건을 설정하여 중복을 방지하였다.

**결론**: Redis 장애 환경에서도 Kafka 메시지 큐의 영속성과 Consumer의 순차 검증을 통해 초과 발급 없이 정합성을 확보했다.

#### 추후 개선 과제 
> Redis AOF(Append Only File) 영속화 적용
> Redis 재부팅 시 장애 직전의 재고 상태를 복원하여, Kafka Consumer 및 DB 까지 불필요한 초과 발급 요청 메시지가 유입되는 현상 자체를 Redis에서 방어함.


> Redis Client Timeout 단축 (Fast-Fail 적용)
> Redis 장애 단락 시 스레드가 최장 18초간 블로킹되는 병목을 방지하기 위해 Connection/Read Timeout을 200ms~500ms 수준으로 단축 설정.
---

### Kafka Consumer 장애 및 Lag 누적

**문제 상황**
> Consumer 장애 발생 시 Kafka 메시지가 처리되지 못하고 DB 저장 지연

**테스트**
> Consumer 프로세스 중지 후 쿠폰 발급 요청
> Consumer를 실행하여 누적된 메시지들 소모 확인

**검증**
> scenarios: 
> - executor: 'constant-arrival-rate',
> - rate:  500,
> - timeUnit: '1s',
> - duration: '30s',
> - preAllocatedVUs: 100,
> - maxVUs: 1000,

`Consumer Stop → 쿠폰 발급 요청 → Kafka Lag 누적 → Consumer Start → 메시지 재처리`

**결과**

※ k6 테스트 결과
<br>
<img width="361" height="183" alt="Image" src="https://github.com/user-attachments/assets/72d460fa-3fef-4baf-aa42-a63f6fd2a032" />

※ Lag 누적과 DB 미저장 상태 확인
<br>
<img width="624" height="142" alt="Image" src="https://github.com/user-attachments/assets/eff120a6-9854-4849-8d89-bebbf706dbed" />

<img width="570" height="381" alt="Image" src="https://github.com/user-attachments/assets/f28708cd-a858-404b-afd7-4bb6f2b5e643" />

※ Lag 소모와 DB 재처리 확인
<br>
<img width="633" height="151" alt="Image" src="https://github.com/user-attachments/assets/700a0500-74be-4269-a715-c75c08bdaf79" />

<img width="545" height="392" alt="Image" src="https://github.com/user-attachments/assets/96d1b858-2ec4-4011-80af-7e74243ac505" />

<img width="248" height="39" alt="Image" src="https://github.com/user-attachments/assets/e0b288a1-9fbe-4124-aad9-d77a70754d8c" />


#### Consumer 장애 구간 메시지 적재

> 테스트 시작 전 Kafka Consumer를 중지한 상태에서 500 req/s의  발급 요청을 발생시켰다.
> Consumer 중지 상태에서도 Redis는 쿠폰 발급을 정상 처리했으며, Producer 역시 발급 이벤트를 Kafka 토픽으로 정상 발행했다.
> 이 과정에서 Consumer가 중지되어 있어 Kafka 메시지를 DB로 전달하지 못하면서 각 Partition에 처리되지 않은 메시지가 Lag형태로 누적되는 것을 확인했다.

#### Kafka Lag 누적 관측

> Consumer 장애 상태 동안 Partition별 Lag를 확인한 결과, Kafka에 처리되지 않은 메시지가 누적되는 것을 확인했다.

#### Consumer 복구 및 메시지 재처리

> 부하 테스트 종료 후 Kafka Consumer를 재실행했다.
> Consumer가 재가동되면서 Kafka에 누적되어 있던 메시지를 순차적으로 소비하였으며, Lag가 0이 된 것을 확인했다.
> Consumer 장애 중 적재된 메시지가 유실되지 않고 정상적으로 재처리되었음을 확인했다.

#### 데이터 정합성 확보

> Redis에서 최종적으로 차감된 쿠폰 수량과 Consumer 복구 후 MySQL에 저장된 쿠폰 발급 데이터의 수량을 비교하였다.
> Redis 발급 수량 [263건], MySQL 저장 수량 [263건]으로 일치하여 Consumer 장애 및 복구 과정에서 쿠폰 발급 데이터의 유실이 발생하지 않았음을 확인했다.

**결론**: Kafka Consumer 장애 상황에서도 발급 이벤트가 Kafka에 보존되어 메시지 유실 없이 누적되었으며, Consumer 복구 후 미처리 메시지가 정상적으로 재처리되어 Kafka Lag이 0으로 감소하고 최종적으로 MySQL에 데이터가 정상 반영됨을 확인하였다.

#### 추후 개선 과제

> Kafka Consumer 장애 발생 시 Lag 증가량을 모니터링하고 임계치 초과 시 관리자에게 알림을 제공하도록 Consumer Lag 모니터링 및 Alert 시스템 구축

---

### MySQL 서버 장애 및 Eventual Consistency

문제 상황
> MySQL 장애로 인한 DB 저장 실패

테스트
> MySQL 강제 종료 상태에서 쿠폰 발급

검증
→ Redis 발급 성공
→ Kafka 메시지 적재
→ Consumer 처리 실패
→ MySQL 복구
→ 메시지 재처리
→ 최종 정합성

결과
> 실제 수치 이미지

---

## Redis 점진적 부하 한계 테스트
| Target Load | 실제 TPS (초당 처리량) | p(95) Latency (응답 속도) | 데이터 정합성 (Integrity) | CPU | Memory |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **100req/s** | **-** | **-** | **-** | - | - |
| **500req/s** | **-** | **-** | **-** | - | - |
| **1000req/s** | **-** | **-** | **-** | - | - |
| **2000req/s** | **-** | **-** | **-** | - | - |
| **5000req/s** | **-** | **-** | **-** | - | - |

### 결과
-

