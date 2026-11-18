# 선착순 쿠폰 발급 API

> 한정된 수량의 쿠폰을 여러 사용자가 동시에 요청할 때 발생하는 초과 발급 문제를 재현하고, 
> 처리 방식에 따른 성능과 데이터 일관성의 차이를 확인.

## 프로젝트 소개

**1000VUs 환경에서 쿠폰 수량을 500장**으로 설정하고 다음 세 가지 방식을 비교했습니다.

- 동시성 제어를 적용하지 않은 MySQL 처리
- MySQL 비관적 락을 적용한 처리
- Redis Lua Script와 Kafka를 결합한 비동기 처리

구현 이후에는 Redis, Kafka Consumer, MySQL을 각각 중단하며
응답 지연, 메시지 적재, 복구 이후의 DB 반영 상태를 확인했습니다.

## 기술 스택

| 영역 | 사용 기술 |
| --- | --- |
| **언어** | Java 17 |
| **프레임 워크** | Spring Boot 3.3.5, Spring Data JPA |
| **DB** | MySQL 8.0 |
| **In-Memory 저장소** | Redis 호환 Memurai |
| **Messaging** | Apache Kafka 4.3.1 |
| **테스트 도구** | k6 v2.2.0 |

## 시스템 구성

![시스템 아키텍처](https://github.com/user-attachments/assets/606b3a4d-bb09-4da0-86ea-f7da3ab961c6)

### MySQL 기반 처리

HTTP 요청 안에서 쿠폰 조회, 중복 확인, 수량 확인과 발급 내역 저장을 처리합니다.

비관적 락을 사용하는 경우 쿠폰 레코드에 락을 획득한 뒤
발급 가능 여부를 확인하고 데이터를 저장합니다.

### Redis + Kafka 기반 처리

1. **Redis Lua Script**에서 사용자 중복과 재고를 확인합니다.
2. 발급 가능한 경우 재고를 차감하고 사용자를 등록합니다.
3. 발급 정보를 **Kafka**에 비동기로 전송합니다.
4. **Consumer**가 메시지를 소비해 MySQL에 발급 내역을 저장합니다.

DB 저장을 HTTP 요청 처리 경로에서 분리한 구조입니다.

> 아래 성능 수치는 Kafka 발행 완료를 기다리지 않고 응답하는
> 기존 구현에서 측정한 결과입니다.
> HTTP 200만으로 Kafka 발행 성공이나 MySQL 저장 완료가 보장되지는 않습니다.
> 이후 코드를 변경한 경우에는 해당 구현의 성능을 별도로 측정해야 합니다.

## 주요 구현

### 1. MySQL 비관적 락

쿠폰 조회에 `PESSIMISTIC_WRITE`를 적용했습니다.

동일 쿠폰의 발급 가능 여부를 판단하는 요청들이
쿠폰 레코드의 락을 획득한 뒤 처리되도록 구성했습니다.

트랜잭션 내부에서 사용자 중복과 현재 발급 수량을 확인하고
발급 내역을 저장합니다.

**관련 코드**:

- [RDBMS 발급 서비스](src/main/java/com/example/api/service/RdbmsCouponService.java)
- [쿠폰 Repository](src/main/java/com/example/api/repository/CouponRepository.java)

### 2. Redis Lua Script

중복 확인과 재고 차감을 개별 명령으로 호출하면,
명령 사이에 다른 요청이 실행될 수 있습니다.

다음 작업을 **Lua Script** 하나로 묶어 처리했습니다.

1. 사용자 발급 이력 확인
2. 현재 재고 확인
3. 재고 차감
4. 발급 사용자 등록

| 반환값 | 의미 |
| --- | --- |
| `-1` | 이미 등록된 사용자 |
| `0` | 재고가 없거나 재고 키가 없음 |
| `1` | 재고 차감 및 사용자 등록 완료 |

Lua Script가 보호하는 범위는 Redis 내부의 작업이고,
Kafka 발행과 MySQL 저장까지 하나의 원자적 작업으로 처리하는 것은 아님.

**관련 코드**:

- [Redis Repository 및 Lua Script](src/main/java/com/example/api/repository/RedisCouponRepository.java)
- [Redis 발급 서비스](src/main/java/com/example/api/service/RedisCouponService.java)

### 3. Kafka 비동기 DB 저장

Redis에서 처리한 발급 정보를 `coupon-issue` 토픽으로 전달하고,
Consumer가 MySQL에 저장하도록 구성했습니다.

이 구조에서는 HTTP 응답과 최종 DB 반영 시점이 다릅니다.

Consumer 또는 MySQL이 중단되었을 때 메시지가 대기하는 상태를
Consumer Lag으로 확인하고, 복구 이후 Lag와 DB 저장 건수를 비교했습니다.

**관련 코드**:

- [Kafka Producer](src/main/java/com/example/api/kafka/CouponKafkaProducer.java)
- [Kafka Consumer](src/main/java/com/example/kafka/CouponKafkaConsumer.java)

### 4. DB 중복 제약

발급 테이블에 다음 조합의 **unique** 제약을 적용했습니다.

```text
(coupon_id, user_id)
```

동일 사용자가 같은 쿠폰을 중복 저장하는 것을 DB에서도 제한합니다.

이 제약은 사용자별 중복을 막는 장치이며,
서로 다른 사용자에게 발급되는 총수량을 제한하는 장치는 아닙니다.

**관련 코드**:

- [발급 엔티티](src/main/java/com/example/api/domain/CouponIssue.java)

## 부하 테스트

### 측정 환경

| 항목 | 환경 |
| --- | --- |
| OS | Windows 11 |
| CPU | Intel i3-9100F |
| RAM | 16GB |
| Redis 환경 | Memurai |
| Kafka | 4.3.1 |
| k6 | v2.2.0 |
| Tomcat Max Threads | 1,000 |

아래 수치는 해당 로컬 환경에서 기록한 실험 결과입니다.
실제 사용자 운영 환경이나 다른 장비에서의 처리량은 다를 수 있습니다.

### 비교 시나리오

| 항목 | 설정 |
| --- | --- |
| Executor | `shared-iterations` |
| **VUs** | 1,000 |
| 총 Iterations | 1,000 |
| 초기 쿠폰 수량 | 500 |

요청 대상:

```text
POST /coupons/1/issue?userId={userId}
POST /coupons/1/issue/redis?userId={userId}
```

**VUs는 가상 사용자 수**이며, 초당 요청 수와는 다른 값이므로,
위 설정만으로 1,000개의 요청이 정확히 같은 시각에 서버에 도착한다고 보장되지는 않음.

### 측정 결과

| 지표 | MySQL No Lock | MySQL 비관적 락 | Redis + Kafka |
| --- | ---: | ---: | ---: |
| **총 요청 수** | 1,000 | 1,000 | 1,000 |
| **HTTP 요청 처리량** | 243.19 req/s | 170.91 req/s | 441.66 req/s |
| HTTP 200 | 549건 | 500건 | 500건 |
| HTTP 400 | 451건 | 500건 | 500건 |
| **p95 응답 시간** | 3.89초 | 5.61초 | 2.11초 |
| **최대 응답 시간** | 3.97초 | 5.72초 | 2.12초 |
| **기록된 최종 발급 건수** | 549건 | 500건 | 500건 |
| **초과 여부** | 49건 초과 | 초과 없음 | 초과 없음 |

- 요청 처리량은 성공과 거절 응답을 포함한 HTTP 요청 처리량입니다.
- p95는 측정된 요청의 95%가 해당 시간 이내에 완료되었다는 의미입니다.
- Redis + Kafka의 최종 발급 건수는 HTTP 응답과 별도로 DB를 확인한 결과입니다.

<details>
<summary>원본 측정 화면</summary>

#### MySQL No Lock

![No Lock 측정 결과](https://github.com/user-attachments/assets/61c43217-2a70-489b-8274-1a3e8a2fd989)

#### MySQL 비관적 락

![비관적 락 측정 결과](https://github.com/user-attachments/assets/12a7a8c6-2646-416b-a4ec-2a0f1223a15e)

#### Redis + Kafka

![Redis Kafka 측정 결과](https://github.com/user-attachments/assets/46569dee-715c-41b8-819b-2f516ad7dab0)

</details>

### 결과 해석

#### No Lock: 초과 발급 재현

동시성 제어가 없는 방식에서는 여러 요청이 발급 가능한 상태를 확인한 뒤
저장을 진행하면서 목표 수량 500장보다 **49건이 초과 발급**되었습니다.

단순한 조회와 조건 검사만으로는 동시 요청에서 수량 제한을 지키기 어렵다는 점을 확인했습니다.

#### 비관적 락: 수량 제한과 응답 지연

비관적 락 방식에서는 최종 발급 건수가 500건으로 기록되었습니다.

반면 No Lock 대비 요청 처리량은 **약 29.7% 감소**하고,
p95 응답 시간은 **약 44.2% 증가**했습니다.

락을 획득한 요청이 처리되는 동안 다른 요청이 대기하는 구조와 함께
응답 지연이 증가하는 결과를 관측했습니다.

#### Redis + Kafka: HTTP 응답 경로의 DB 저장 분리

Redis + Kafka 방식에서는 비관적 락 대비
HTTP 요청 처리량이 **약 2.58배로 증가**하고,
p95 응답 시간은 **약 62.4% 감소**했습니다.

다만 두 방식은 HTTP 응답 전에 완료하는 작업이 다릅니다.

- MySQL 방식은 DB 저장과 트랜잭션 완료 후 응답합니다.
- 측정 당시 Redis + Kafka 방식은 Kafka 비동기 전송을 호출한 뒤,
  발행 완료와 DB 저장을 기다리지 않고 응답합니다.

따라서 이 수치는 HTTP 응답 지표의 비교이며,
최종 DB 저장까지의 처리 성능이 같은 비율로 개선됐다는 의미는 아닙니다.

## 장애 실험

부하테스트 환경

```javascript
{
  executor: 'constant-arrival-rate',
  rate: 500,
  timeUnit: '1s',
  duration: '30s',
  preAllocatedVUs: 100,
  maxVUs: 1000
}
```

`rate: 500`은 초당 500회 iteration 시작을 목표로 하는 설정입니다.
실제 시작·완료된 요청 수는 dropped iterations와 결과 지표를 함께 확인해야 합니다.

### 1. Redis 중단

실험 순서:

```text
정상 동작 → 10초 시점 Redis 중단 → 20초 시점 Redis 재시작
```

기존 기록에서 확인한 결과:

| 항목 | 기록값 |
| --- | ---: |
| **최대 응답 시간** | 18.54초 |
| **dropped iterations** | 8,378건 |
| HTTP 200 | 500건 |
| DB 저장 건수 | 500건 |

Redis 중단 구간에서 **긴 응답 지연**과 **요청 발생 누락**이 관측되었습니다.
Redis 연결 대기와 서버 자원 점유가 영향을 주었을 가능성이 있으며,
정확한 원인을 구분하려면 연결·스레드·자원 지표를 함께 수집해야 합니다.

현재 일반 예외 처리 코드는 HTTP 500을 반환합니다.

<details>
<summary>원본 측정 화면</summary>

![Redis 장애 측정 1](https://github.com/user-attachments/assets/cd520926-0165-4895-8e98-7dcfa3a17b6e)

![Redis 장애 측정 2](https://github.com/user-attachments/assets/923fdd17-b686-4dd3-97f5-19c39a5fabdc)

</details>

### 2. Kafka Consumer 중단

실험 순서:

```text
Consumer 중단 → 발급 요청 → Lag 누적 → Consumer 재실행 → DB 반영 확인
```

기존 기록에서는 Consumer 중단 중 Partition별 Lag가 누적되고,
재실행 이후 Lag가 0으로 감소하는 것을 확인했습니다.

| 비교 항목 | 기록값 |
| --- | ---: |
| **Redis에서 처리한 발급 수량** | 263건 |
| **Consumer 복구 후 DB 저장 건수** | 263건 |

해당 실험에서는 Redis 처리 수량과 복구 후 DB 저장 건수가 일치했습니다.

이는 Kafka에 전달된 메시지가 Consumer 중단 동안 대기하고
복구 이후 처리되는 흐름을 확인한 결과입니다.

<details>
<summary>원본 측정 화면</summary>

![Consumer 중단 시 Lag](https://github.com/user-attachments/assets/eff120a6-9854-4849-8d89-bebbf706dbed)

![Consumer 복구 후 Lag](https://github.com/user-attachments/assets/700a0500-74be-4269-a715-c75c08bdaf79)

![DB 반영 확인](https://github.com/user-attachments/assets/96d1b858-2ec4-4011-80af-7e74243ac505)

</details>

### 3. MySQL 중단

실험 순서:

```text
MySQL 중단 → 발급 요청 및 저장 지연 → MySQL 복구 → Lag와 DB 확인
```

기존 기록에서는 DB 저장이 지연되는 동안 Lag가 누적되고,
MySQL 복구 이후 DB 반영과 Lag 감소를 확인했습니다.

| 비교 항목 | 기록값 |
| --- | ---: |
| 초기 쿠폰 수량 | 500개 |
| Redis 잔여 재고 | 245개 |
| 복구 후 DB 저장 건수 | 255건 |
| 최종 Consumer Lag | 0 |

잔여 재고 245개와 DB 저장 255건의 합이 초기 수량 500개와 일치했습니다.

이를 통해 해당 실험에서 지연된 발급 정보가
복구 이후 DB에 반영되는 흐름을 확인했습니다.

<details>
<summary>원본 측정 화면</summary>

![MySQL 장애 시 Lag](https://github.com/user-attachments/assets/27e43041-c2c6-4e06-969d-bfb110a970f0)

![MySQL 복구 후 Lag](https://github.com/user-attachments/assets/f5026b6c-747a-4f8a-92f4-08a7888cc060)

![DB 수량 확인](https://github.com/user-attachments/assets/1f6c167a-4974-4615-8438-b99854551752)

</details>

## VU 증가에 따른 부하 실험

### 조건

```javascript
{
  executor: 'per-vu-iterations',
  iterations: 1,
  vus: 10000,
  maxDuration: '60s'
}
```

VUs를 500, 1,000, 2,000, 5,000, 10,000으로 변경하며 실행했습니다.
각 VU는 한 번씩 요청하므로 지속적인 초당 요청률을 지정한 실험은 아닙니다.

### 기존 측정 결과

| VUs | HTTP 요청 처리량 | p95 응답 시간 | HTTP 200 | 연결 실패 |
| --- | ---: | ---: | ---: | ---: |
| 500 | 257 req/s | 1.90초 | 500건 | 0건 |
| 1,000 | 424 req/s | 2.23초 | 500건 | 0건 |
| 2,000 | 721 req/s | 2.45초 | 500건 | 0건 |
| 5,000 | 1,024 req/s | 3.86초 | 500건 | 836건 |
| 10,000 | 1,918 req/s | 3.54초 | 500건 | 3,711건 |

5,000 VU 이상에서 다음 연결 오류가 관측되었습니다.

```text
dial tcp 127.0.0.1:8080: connectex:
No connection could be made because the target machine actively refused it.
```


## 한계와 개선 방향

### Redis 변경과 Kafka 발행 사이의 실패 구간

Redis에서 재고 차감과 사용자 등록을 마친 후
Kafka 발행에 실패하면 DB에 발급 정보가 남지 않을 수 있습니다.

기존 Producer는 비동기 전송 결과를 로그로 처리하며,
실패 결과가 이미 반환한 HTTP 응답에 반영되지는 않습니다.

발행 결과 확인과 함께,
실패 상태를 보관하고 재처리·대조하는 복구 경로를 검토할 필요가 있습니다.

### 중복 소비와 재시도

DB unique 제약은 중복 행의 저장을 제한하지만,
중복 이벤트를 정상적으로 처리 완료하는 정책을 대신하지 않습니다.

중복 메시지, 일시적 DB 장애, 반복 실패 메시지를 구분하고
재시도 및 최종 실패 처리 정책을 정리할 계획입니다.

### 발급 경로 간 재고 기준

MySQL 경로와 Redis 경로는 발급 가능 수량을 판단하는 기준이 다릅니다.

현재 비교 실험은 상태를 초기화한 뒤 각 방식을 따로 실행한 결과입니다.
같은 쿠폰을 두 API에서 동시에 발급할 때의 수량 일관성은 별도 검증이 필요합니다.


## 실행 안내

### 사전 준비

- Java 17
- MySQL 및 `coupon_db`
- Redis 호환 서버
- Kafka 및 `coupon-issue` 토픽
- k6

DB 계정과 비밀번호, Redis 및 Kafka 접속 정보를
`application.properties`에서 실행 환경에 맞게 설정합니다.

**실제 비밀번호가 포함된 설정은 공개 저장소에 올리지 않습니다.**

### API 서버

```powershell
.\gradlew.bat bootRun
```

### 테스트 데이터 초기화

초기화 기능을 사용하는 경우 다음 옵션으로 실행합니다.

```powershell
.\gradlew.bat bootRun --args="--app.initialize-data=true"
```

> 초기화는 기존 발급 내역을 삭제하고 Redis 재고·사용자 정보를 재설정합니다.
> 요청 발생을 멈추고 이전 Kafka 메시지의 처리 상태를 확인한 뒤 실행해야 합니다.
> 재시작·장애 복구 실험에서는 초기화 옵션을 사용하지 않습니다.

### Consumer

IDE에서 다음 클래스를 별도 실행합니다.

```text
com.example.kafka.KafkaConsumerApplication
```

API 서버와 Consumer는 별도 프로세스로 실행합니다.

### 부하 테스트

```powershell
k6 run k6-test.js
```

실행 전에 대상 API, VUs, iteration 수,
사용자 ID와 초기 재고를 확인해야 합니다.
