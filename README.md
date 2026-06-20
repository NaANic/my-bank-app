# my-bank-app — Спринт 11 (Apache Kafka)

Учебное банковское приложение из пяти микросервисов на Spring Boot 3.5 за
шлюзом Spring Cloud Gateway, с Keycloak (OAuth 2.1) для аутентификации
пользователей и сервис-сервисного взаимодействия и единым PostgreSQL по
схеме «отдельная схема на сервис», развёрнутое в Kubernetes зонтичным Helm-чартом.

В одиннадцатом спринте уведомления доставляются в сервис Notifications **через
Apache Kafka** вместо REST. Микросервисы Accounts, Cash и Transfer публикуют
событие уведомления в топик `notifications`, а Notifications его потребляет и
сохраняет. За счёт вынесенного в Спринте 10 модуля `infra/common` смена
транспорта затронула только общий `NotificationsClient` — код Accounts/Cash/
Transfer не менялся.

Чек-лист ревьюера — в `TASK.md`.

---

## Что изменилось по сравнению со Спринтом 10

| Область | Спринт 10 | Спринт 11 |
| --- | --- | --- |
| Доставка уведомлений | REST (Client Credentials, через Gateway) | Apache Kafka, топик `notifications` |
| Notifications | resource-server + REST `POST /notifications` | Kafka-консьюмер (`@KafkaListener`), без бизнес-REST |
| Accounts | OAuth-клиент (client_credentials) для Notifications | не OAuth-клиент; публикует в Kafka из outbox |
| Cash / Transfer | OAuth-клиент для Accounts и Notifications | OAuth-клиент только для Accounts; публикуют в Kafka |
| Инфраструктура (k8s/compose) | postgres | postgres + kafka (KRaft, StatefulSet/том) |

Notifications сохранил `spring-boot-starter-web` + actuator только ради
`httpGet /actuator/health` для проб Kubernetes из Спринта 10; бизнес-эндпоинтов
и resource-server у него больше нет.

---

## Apache Kafka

- **KRaft, одна нода** (без ZooKeeper). Развёрнута и локально в Docker Compose
  (с именованным томом), и в Kubernetes (StatefulSet + PVC) — содержимое топиков
  переживает перезапуск/пересоздание брокера.
- **Топик `notifications`** создаётся декларативно (бин `NewTopic` в сервисе
  Notifications), фактор репликации 1, 1 партиция.
- **Продюсер** (`NotificationsClient` в `infra/common`): `acks=all` +
  идемпотентность, сериализация события в JSON-строку, блокирующее ожидание
  подтверждения брокера (`KafkaTemplate.send(...).get()`) — стратегия
  **at-least-once**. Ключ сообщения — логин (очерёдность не требуется).
- **Консьюмер** (`NotificationListener`): группа `notifications`,
  `enable-auto-commit: false` + `ack-mode: RECORD` — оффсет фиксируется только
  после записи в БД, поэтому при падении сообщение переобрабатывается
  (at-least-once), а после перезапуска чтение продолжается с последнего
  закоммиченного оффсета. `auto-offset-reset: earliest`.

Транспорт между модулями — JSON-строка (Jackson на продюсере, разбор на
консьюмере), без type-заголовков, поэтому продюсер и консьюмер не связаны общими
классами.

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
                 │ +outbox  │       └────┬─────┘       └────┬─────┘
                 └────┬─────┘            │ client_credentials │
                      │                  ▼  (доступ только в accounts)
                      │            (все три публикуют событие)
                      └───────────────┐  │  ┌───────────────────┘
                                      ▼  ▼  ▼
                                ┌───────────────┐
                                │     kafka     │  топик "notifications"
                                │  :9092 KRaft  │  (StatefulSet + PVC)
                                └───────┬───────┘
                                        ▼  @KafkaListener (group=notifications)
                                ┌───────────────┐
                                │ notifications │  пишет в БД + лог
                                │   :8085       │
                                └───────┬───────┘
                       ┌────────────────┴───────────┐
                       ▼                             ▼
                 ┌──────────────┐          ┌───────────────────┐
                 │  postgres    │          │ keycloak (Service │
                 │  StatefulSet │          │ ExternalName) ───►│ хост :8090
                 │  схемы:      │          │ realm: bank       │ (вне кластера)
                 │  accounts,   │          └───────────────────┘
                 │  notifications│
                 └──────────────┘
```

Keycloak работает **вне** кластера. Service типа `ExternalName` с именем
`keycloak` сопоставляет внутрикластерное имя с хостом (`host.docker.internal`),
поэтому поды обращаются к нему по `http://keycloak:8090`, а claim `iss` остаётся
`http://localhost:8090/realms/bank` (`KC_HOSTNAME=localhost`).

| Сервис | Порт | Примечание |
| --- | --- | --- |
| gateway | 8080 (NodePort 30080) | реактивная маршрутизация, `StripPrefix=1` |
| accounts | 8081 | JPA + Flyway + транзакционный outbox → Kafka |
| cash | 8083 | пополнение / снятие, публикует в Kafka |
| transfer | 8084 | переводы между счетами, публикует в Kafka |
| notifications | 8085 | Kafka-консьюмер, пишет в БД и лог (HTTP только actuator) |
| front-ui | 8082 | Thymeleaf + oauth2Login (запускается на хосте) |
| postgres | 5432 | StatefulSet, схема на сервис |
| kafka | 9092 | StatefulSet, KRaft, топик `notifications` |
| keycloak | 8090 | на хосте, realm импортируется |

---

## Требования

- Docker Desktop с **включённым Kubernetes** (контекст `docker-desktop`)
- `kubectl` и `helm` v3
- JDK 21 и Maven (только для сборки / запуска вне кластера)

---

## Развёртывание в Kubernetes через Helm

Все команды выполняются из корня репозитория.

### 1. Сборка образов сервисов

```bash
docker build -t accounts:0.0.1      -f services/accounts/Dockerfile .
docker build -t cash:0.0.1          -f services/cash/Dockerfile .
docker build -t transfer:0.0.1      -f services/transfer/Dockerfile .
docker build -t notifications:0.0.1 -f services/notifications/Dockerfile .
docker build -t gateway:0.0.1       -f infra/gateway/Dockerfile .
```

Образ Kafka (`apache/kafka:3.9.0`) и Postgres тянутся из публичного реестра —
собирать их не нужно. Kubernetes в Docker Desktop использует общее локальное
хранилище образов, поэтому локально собранные теги доступны напрямую
(`pullPolicy: IfNotPresent`).

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

`accounts` и `notifications` используют init-контейнер `wait-for-postgres`.
Kafka запускается отдельным StatefulSet; продюсеры и консьюмер подключаются к
брокеру лениво с ретраями, поэтому порядок старта не критичен.

### 4. Проверка встроенными Helm-тестами

```bash
helm test bank -n bank
```

- **connectivity-test** — TCP к `postgres:5432` и `kafka:9092` + `/actuator/health`
  каждого сервиса (проверяет готовность приложения, а не только открытый порт).
- **gateway-route-test** — проходит по маршрутам `/accounts`, `/cash`, `/transfer`
  через `/<svc>/actuator/health` и ждёт `200`.

### 5. Проверка доставки уведомления через Kafka

```bash
TOKEN=$(curl -s -X POST http://localhost:8090/realms/bank/protocol/openid-connect/token \
  -d grant_type=password -d client_id=front-ui -d client_secret=front-ui-secret \
  -d username=alice -d password=alice | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

curl -s -i -X POST http://localhost:30080/cash/deposit \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"amount":100.00}'

kubectl logs -n bank deploy/notifications --tail 5
```
Ожидаемо: HTTP `200`, а в логе notifications — строка `[notification#…]
kind=cash_deposit …` (событие пришло через топик `notifications`).

Веб-интерфейс: запустить Front-UI на хосте на NodePort шлюза:
```bash
GATEWAY_URL=http://localhost:30080 \
  java -jar services/front-ui/target/front-ui-0.0.1-SNAPSHOT.jar
# http://localhost:8082 -> вход через Keycloak (alice / alice или bob / bob)
```

### 6. Остановка и удаление

```bash
helm uninstall bank -n bank
kubectl delete namespace bank
docker compose down            # без -v том Kafka сохраняется
```

---

## Безопасность и устойчивость в кластере

- **ConfigMap / Secret** — несекретные настройки и секреты клиентов отдельно на
  каждый сервис, формируются из values Helm. У accounts больше нет OAuth-секрета
  (он не OAuth-клиент).
- **NetworkPolicy** (ingress): postgres ← accounts/notifications;
  accounts ← gateway/cash/transfer; cash и transfer ← только gateway;
  kafka ← accounts/cash/transfer/notifications; notifications ← только health-check
  (по HTTP к нему больше никто не ходит — общение через Kafka).
- **requests/limits** заданы для всех нагрузок (Burstable QoS).
- **readiness/liveness** — `httpGet /actuator/health` для сервисов, TCP-проба для Kafka.

---

## Модель конфигурации

Каждый сервис читает конфигурацию из переменных окружения с разумными
значениями по умолчанию, поэтому один и тот же jar без изменений работает на
ноутбуке, в docker-compose и в Kubernetes.

| Переменная | Кто использует | Значение в кластере |
| --- | --- | --- |
| `PG_HOST` / `PG_PASSWORD` | accounts, notifications | `postgres` / из Secret |
| `KAFKA_BOOTSTRAP_SERVERS` | accounts, cash, transfer, notifications | `kafka:9092` |
| `BANK_NOTIFICATIONS_TOPIC` | accounts, cash, transfer, notifications | `notifications` |
| `OAUTH_ISSUER_URI` / `OAUTH_JWK_SET_URI` | accounts, cash, transfer (resource server) | Keycloak |
| `OAUTH_TOKEN_URI` / `OAUTH_CLIENT_ID` / `OAUTH_CLIENT_SECRET` | cash, transfer (клиент для accounts) | по сервису |
| `ACCOUNTS_BASE_URL` | cash, transfer | `http://accounts:8081` |
| `ACCOUNTS_URI` / `CASH_URI` / `TRANSFER_URI` | gateway | `http://<svc>:<port>` |

Локально (jar на хосте) `KAFKA_BOOTSTRAP_SERVERS` по умолчанию `localhost:9094`
(внешний листенер брокера из docker-compose); в контейнерах/подах — `kafka:9092`.

---

## Структура Helm-чарта

```
helm/bank/
├── Chart.yaml                 зонтичный чарт; объявляет каждый subchart как зависимость
├── values.yaml                global.* (image, postgres, keycloak, kafka) + переключатели
├── templates/
│   ├── keycloak-service.yaml  Service ExternalName -> Keycloak на хосте
│   ├── network-policies.yaml  ingress-политики (матрица доступа между сервисами + kafka)
│   └── tests/                 connectivity-test, gateway-route-test
└── charts/
    ├── postgres/              StatefulSet + headless Service + ConfigMap/Secret
    ├── kafka/                 StatefulSet (KRaft) + headless Service + PVC
    ├── accounts/              Deployment + Service + ConfigMap (+ init)
    ├── notifications/         Deployment + Service + ConfigMap (+ init)
    ├── cash/                  Deployment + Service + ConfigMap + Secret
    ├── transfer/              Deployment + Service + ConfigMap + Secret
    └── gateway/               Deployment + NodePort Service + ConfigMap
```

---

## Запуск локально без Kubernetes

```bash
docker compose up -d postgres keycloak kafka
mvn package -DskipTests
java -jar services/accounts/target/accounts-0.0.1-SNAPSHOT.jar
java -jar infra/gateway/target/gateway-0.0.1-SNAPSHOT.jar
java -jar services/cash/target/cash-0.0.1-SNAPSHOT.jar
java -jar services/transfer/target/transfer-0.0.1-SNAPSHOT.jar
java -jar services/notifications/target/notifications-0.0.1-SNAPSHOT.jar
java -jar services/front-ui/target/front-ui-0.0.1-SNAPSHOT.jar
```

Либо весь стек в контейнерах:
```bash
docker compose up --build
# Front-UI: http://localhost:8082   Gateway: http://localhost:8080
```

---

## Тесты

```bash
mvn install        # полный прогон: unit, slice, интеграционные (Testcontainers / EmbeddedKafka), контрактные
```

| Сервис | Unit / интеграционные | Контрактные |
| --- | --- | --- |
| accounts | `AccountServiceTest` (Mockito) · `AccountsIntegrationTest` (Testcontainers) | контракты-продюсеры генерируют `AccountsTest` + stubs-jar |
| cash | `CashControllerTest` (slice) | `AccountsClientContractTest` берёт стабы accounts из classpath |
| transfer | `TransferControllerTest` | — |
| notifications | `NotificationsKafkaIntegrationTest` (`@EmbeddedKafka` + Testcontainers Postgres): сообщение в топик → запись в БД | — |
| front-ui | `MainControllerTest` (`@WebMvcTest`) | — |
| gateway | `GatewayApplicationTests` (контекст + ID) · `GatewayRoutingTest` (маршрутизация через `WebTestClient`) | — |

Интеграция с Kafka проверяется встроенным брокером (`@EmbeddedKafka`), поэтому
тесты не требуют Docker. Работоспособность развёртывания (включая реальный
брокер `apache/kafka` и доставку события) проверяется командой `helm test bank -n bank`
и сценарием из раздела «Проверка доставки уведомления через Kafka».

---

## Общий модуль клиентов (`infra/common`)

Общий код вынесен в библиотеку `infra/common` (пакет
`ru.yandex.practicum.mybank.common`): `AbstractServiceClient`, `AccountSnapshot`,
`AccountsServiceException`, `NotificationsClient` (теперь Kafka-продюсер) и
`CommonClientAutoConfiguration`. Менеджер OAuth2 (`OAuth2AuthorizedClientManager`)
создаётся только при наличии клиентских регистраций (cash/transfer); accounts его
не получает. `NotificationsClient` публикует событие в Kafka, поэтому смена
транспорта REST → Kafka не затронула код Accounts/Cash/Transfer.

---

## Структура репозитория

```
my-bank-app/
├── pom.xml                     родительский POM: Spring Boot 3.5 + Spring Cloud 2025.0.0
├── docker-compose.yml          Postgres + Keycloak + Kafka + 6 сервисов
├── helm/bank/                  зонтичный Helm-чарт + subcharts (вкл. kafka)
├── infra/
│   ├── common/                 общая библиотека клиентов (auto-configuration)
│   ├── gateway/                Spring Cloud Gateway
│   └── keycloak/import/        realm, импортируемый при старте Keycloak
└── services/
    ├── accounts/  cash/  transfer/  notifications/  front-ui/
```

---

## Лицензия

Учебный проект (Яндекс Практикум, Спринт 11).