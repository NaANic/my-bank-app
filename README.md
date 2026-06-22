# my-bank-app — Спринт 11 (Apache Kafka, ревью исправлено)

Учебное банковское приложение из пяти микросервисов на Spring Boot 3.5 за
шлюзом Spring Cloud Gateway, с Keycloak (OAuth 2.1) для аутентификации
пользователей и сервис-сервисного взаимодействия, единым PostgreSQL по схеме
«отдельная схема на сервис», развёрнутое в Kubernetes зонтичным Helm-чартом.

В одиннадцатом спринте уведомления доставляются в сервис Notifications через
Apache Kafka. Микросервис Accounts публикует события через **транзакционный
outbox** с **атомарным claim** (`FOR UPDATE SKIP LOCKED`), что гарантирует
надёжную доставку при любом числе реплик и исключает дублирование в Kafka. Cash
и Transfer вызывают только Accounts и к Kafka не обращаются. Consümer
Notifications реализует **идемпотентность** по `eventId` и валидацию полей.
Топик создаётся Helm Job'ом, а не внутри приложения.

---

## Что изменилось по сравнению со Спринтом 10

| Область | Спринт 10 | Спринт 11 |
|---|---|---|
| Доставка уведомлений | REST (Client Credentials) | Apache Kafka, топик `notifications` |
| Надёжность | best-effort в cash/transfer | транзакционный outbox в accounts (`FOR UPDATE SKIP LOCKED`) |
| Payload | `{login, kind, message}` — строка | `{eventId, login, kind, amount, currency, createdAt}` — структура |
| Idempotency | нет | `eventId` + уникальный индекс в notifications |
| Notifications | resource-server + REST | только Kafka-консьюмер (HTTP только для actuator) |
| Accounts | OAuth-клиент для notifications | не OAuth-клиент; пишет outbox в одной транзакции |
| Cash / Transfer | OAuth-клиент для accounts + Kafka-продюсер | OAuth-клиент только для accounts |
| Создание топика | `NewTopic` бин в приложении | Helm Job (`kafka-topics.sh`, post-install/post-upgrade hook) |

---

## Apache Kafka

- **KRaft, одна нода** (без ZooKeeper). Kafka запускается в docker-compose
  (именованный том) и в Kubernetes (StatefulSet + PVC) — содержимое топиков
  переживает перезапуск.
- **Топик `notifications`** создаётся Helm Job'ом (`kafka-topic-init`) с
  `--if-not-exists`. Это инфраструктурная ответственность, отделённая от
  жизненного цикла consumer-сервиса.
- **Продюсер** (`NotificationsClient` в `infra/common`): `acks=all` +
  идемпотентность, блокирующее ожидание подтверждения — **at-least-once**.
  Продюсер вызывается только из `OutboxPoller` сервиса Accounts.
- **Транзакционный outbox** в Accounts: событие записывается в таблицу `outbox`
  в **той же транзакции**, что и изменение баланса. `OutboxPoller` атомарно
  забирает записи (`FOR UPDATE SKIP LOCKED`, статусы `NEW→PROCESSING→SENT`)
  и публикует их в Kafka. При нескольких репликах каждую запись обработает
  ровно одна реплика.
- **Консьюмер** (`NotificationListener`): группа `notifications`, `ack-mode=RECORD`
  — оффсет коммитится после записи в БД. Поле `eventId` с уникальным индексом
  обеспечивает идемпотентность: повторная доставка одного события игнорируется.
  Валидация обязательных полей (`eventId`, `login`, `kind`) после десериализации
  — некорректные сообщения логируются и пропускаются.

---

## Архитектура

```
                          (вне кластера)
   браузер ─────► Front-UI :8082 ─► Gateway NodePort :30080
                   (Thymeleaf,            │
                    oauth2Login)          │ внутри кластера
   ───────────────────────────────────────┼──────────────────────────────
                                          ▼  (DNS Service, ClusterIP)
                                  ┌──────────────┐
                                  │   gateway    │  Spring Cloud Gateway
                                  │   :8080      │  /accounts/** /cash/**
                                  └──────┬───────┘  /transfer/**
                      ┌──────────────────┼──────────────────┐
                      ▼                  ▼                  ▼
                ┌──────────┐       ┌──────────┐       ┌──────────┐
                │ accounts │◄──────│   cash   │       │ transfer │
                │  :8081   │       │  :8083   │──────►│  :8084   │
                │ +outbox  │       └──────────┘       └──────────┘
                └────┬─────┘    (только вызывают accounts,
                     │           в Kafka не пишут)
                     │ outbox poller (FOR UPDATE SKIP LOCKED)
                     ▼
               ┌───────────────┐
               │     kafka     │  топик "notifications"
               │  :9092 KRaft  │  (StatefulSet + PVC)
               └───────┬───────┘  топик создаётся Helm Job'ом
                       ▼  @KafkaListener (ack-mode=RECORD, idempotent by eventId)
               ┌───────────────┐
               │ notifications │  пишет в БД + лог
               │   :8085       │
               └───────┬───────┘
              ┌─────────┴──────────┐
              ▼                    ▼
        ┌──────────────┐   ┌───────────────────┐
        │  postgres    │   │ keycloak (ExternalName)
        │  StatefulSet │   │ realm: bank        │──► хост :8090
        │  схемы:      │   └───────────────────┘    (вне кластера)
        │  accounts,   │
        │  notifications│
        └──────────────┘
```

| Сервис | Порт | Примечание |
|---|---|---|
| gateway | 8080 (NodePort 30080) | маршрутизация, `StripPrefix=1` |
| accounts | 8081 | JPA + Flyway + transactional outbox → Kafka |
| cash | 8083 | пополнение/снятие, только вызывает accounts |
| transfer | 8084 | переводы, только вызывает accounts |
| notifications | 8085 | Kafka-консьюмер, HTTP только actuator |
| front-ui | 8082 | Thymeleaf + oauth2Login (на хосте) |
| postgres | 5432 | StatefulSet, схема на сервис |
| kafka | 9092 | StatefulSet, KRaft, топик создаёт Helm Job |
| keycloak | 8090 | на хосте, realm импортируется |

---

## Требования

- Docker Desktop с **включённым Kubernetes** (контекст `docker-desktop`)
- `kubectl` и `helm` v3
- JDK 21 и Maven (только для сборки / локального запуска)

---

## Развёртывание в Kubernetes через Helm

### 1. Сборка образов

```bash
docker build -t accounts:0.0.1      -f services/accounts/Dockerfile .
docker build -t cash:0.0.1          -f services/cash/Dockerfile .
docker build -t transfer:0.0.1      -f services/transfer/Dockerfile .
docker build -t notifications:0.0.1 -f services/notifications/Dockerfile .
docker build -t gateway:0.0.1       -f infra/gateway/Dockerfile .
```

### 2. Запуск Keycloak на хосте

```bash
docker compose up -d keycloak
curl -s -o /dev/null -w "%{http_code}\n" \
  http://localhost:8090/realms/bank/.well-known/openid-configuration
```
Ожидается `200`.

### 3. Установка чарта

```bash
kubectl config use-context docker-desktop
kubectl create namespace bank
helm install bank helm/bank -n bank
kubectl get pods -n bank -w
```

После `helm install` автоматически запускается Helm post-install Job
`kafka-topic-init`, который создаёт топик `notifications` через
`kafka-topics.sh --if-not-exists`.

### 4. Проверка Helm-тестами

```bash
helm test bank -n bank
```

- **connectivity-test** — TCP к `postgres:5432` и `kafka:9092` + `/actuator/health` каждого сервиса.
- **e2e-notification-test** — проверяет, что notifications `UP` (consumer запущен).
- **gateway-route-test** — проходит по маршрутам через `/<svc>/actuator/health`.

### 5. Проверка e2e через NodePort

```bash
TOKEN=$(curl -s -X POST http://localhost:8090/realms/bank/protocol/openid-connect/token \
  -d grant_type=password -d client_id=front-ui -d client_secret=front-ui-secret \
  -d username=alice -d password=alice | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

curl -s -i -X POST http://localhost:30080/cash/deposit \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"amount":100.00}'

sleep 6
kubectl logs -n bank deploy/notifications --tail 5
```

Ожидаемо: HTTP `200`, в логе notifications — `kind=BALANCE_CREDIT
message=Пополнение наличными: alice внёс 100.00 RUB` (событие прошло через
outbox accounts → Kafka → консьюмер notifications).

### 6. Остановка

```bash
helm uninstall bank -n bank
kubectl delete namespace bank
docker compose down
```

---

## Безопасность и устойчивость

- **ConfigMap / Secret** — несекретные настройки и секреты отдельно на каждый сервис.
- **NetworkPolicy** (ingress): postgres ← accounts/notifications; accounts ← gateway/cash/transfer; kafka ← accounts/cash/transfer/notifications; notifications ← только health-check (HTTP к нему не ходит никто — общение через Kafka).
- **Transactional outbox** + `FOR UPDATE SKIP LOCKED` — надёжная доставка при любом числе реплик без дублирования в Kafka.
- **Idempotent consumer** — `eventId` с уникальным индексом: повторная доставка игнорируется.
- **requests/limits** для всех нагрузок (Burstable QoS).
- **readiness/liveness** — `httpGet /actuator/health` для сервисов, TCP-проба для Kafka.

---

## Модель конфигурации

| Переменная | Кто использует | Значение в кластере |
|---|---|---|
| `PG_HOST` / `PG_PASSWORD` | accounts, notifications | `postgres` / из Secret |
| `KAFKA_BOOTSTRAP_SERVERS` | accounts, cash, transfer, notifications | `kafka:9092` |
| `BANK_NOTIFICATIONS_TOPIC` | accounts, cash, transfer, notifications | `notifications` |
| `OAUTH_ISSUER_URI` / `OAUTH_JWK_SET_URI` | accounts, cash, transfer | Keycloak |
| `OAUTH_TOKEN_URI` / `OAUTH_CLIENT_ID` / `OAUTH_CLIENT_SECRET` | cash, transfer | по сервису |
| `ACCOUNTS_BASE_URL` | cash, transfer | `http://accounts:8081` |
| `ACCOUNTS_URI` / `CASH_URI` / `TRANSFER_URI` | gateway | `http://<svc>:<port>` |

---

## Структура Helm-чарта

```
helm/bank/
├── Chart.yaml                        зонтичный чарт (Sprint 11)
├── values.yaml                       global.* + переключатели
├── templates/
│   ├── keycloak-service.yaml         ExternalName → host.docker.internal:8090
│   ├── kafka-topic-init.yaml         post-install Job: kafka-topics.sh --if-not-exists
│   ├── network-policies.yaml         ingress-матрица (+ kafka)
│   └── tests/
│       ├── connectivity-test.yaml    TCP postgres/kafka + actuator health
│       ├── e2e-notification-test.yaml notifications UP (consumer работает)
│       └── gateway-route-test.yaml   маршруты gateway
└── charts/
    ├── postgres/   kafka/   accounts/   notifications/   cash/   transfer/   gateway/
```

---

## Запуск локально без Kubernetes

```bash
docker compose up -d postgres keycloak kafka
mvn -s ~/central-settings.xml package -DskipTests
java -jar services/accounts/target/accounts-0.0.1-SNAPSHOT.jar &
java -jar infra/gateway/target/gateway-0.0.1-SNAPSHOT.jar &
java -jar services/cash/target/cash-0.0.1-SNAPSHOT.jar &
java -jar services/transfer/target/transfer-0.0.1-SNAPSHOT.jar &
java -jar services/notifications/target/notifications-0.0.1-SNAPSHOT.jar &
java -jar services/front-ui/target/front-ui-0.0.1-SNAPSHOT.jar
# Front-UI: http://localhost:8082
```

---

## Тесты

```bash
mvn install   # unit, slice, интеграционные (Testcontainers + EmbeddedKafka), контрактные
```

| Сервис | Тесты |
|---|---|
| accounts | `AccountServiceTest` (Mockito) · `AccountsIntegrationTest` (Testcontainers) · `InternalSecurityIntegrationTest` (scope allow/deny) · контракт-продюсер |
| cash | `CashControllerTest` (slice) · `AccountsClientContractTest` (stub-runner) |
| transfer | `TransferControllerTest` (slice) |
| notifications | `NotificationsKafkaIntegrationTest` (`@EmbeddedKafka` + Testcontainers): happy path, idempotency, malformed JSON, missing fields |
| front-ui | `MainControllerTest` (`@WebMvcTest`) |
| gateway | `GatewayApplicationTests` · `GatewayRoutingTest` |

---

## Общий модуль `infra/common`

`NotificationsClient` — Kafka-продюсер (`acks=all`, блокирующий), публикует
`NotificationEvent {eventId, login, kind, amount, currency, createdAt}` в JSON.
`NotificationKind` — enum (`BALANCE_CREDIT`, `BALANCE_DEBIT`,
`BALANCE_TRANSFER_OUT`, `BALANCE_TRANSFER_IN`). Текст уведомления формирует
consumer по полям структуры, что упрощает локализацию и анализ событий.

---

## Лицензия

Учебный проект (Яндекс Практикум, Спринт 11).