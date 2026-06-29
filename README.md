# my-bank-app — Спринт 12 (наблюдаемость: Zipkin, Prometheus, Grafana, ELK)

Микросервисное банковское приложение из пяти микросервисов на Spring Boot 3.5 за
шлюзом Spring Cloud Gateway, с Keycloak (OAuth 2.1), PostgreSQL (схема на сервис)
и Apache Kafka (уведомления). Всё приложение и весь стек наблюдаемости
развёрнуты в Kubernetes одним зонтичным Helm-чартом.

В двенадцатом спринте добавлена полная наблюдаемость: распределённый трейсинг
(**Zipkin** + Micrometer Tracing), метрики и алерты (**Prometheus** +
**Grafana**), сбор и анализ логов (**ELK**: Elasticsearch, Logstash, Kibana).
Единый формат логов с `traceId`/`spanId` (паттерн Microservice Chassis) связывает
логи в Kibana с трейсами в Zipkin.

---

## Что добавлено в Спринте 12

| Область | Реализация |
|---|---|
| Трейсинг | Micrometer Tracing + Brave → Zipkin (HTTP вход/выход, JDBC, Kafka) |
| Метрики | Micrometer + Actuator → Prometheus (`/actuator/prometheus`) |
| Дашборды | Grafana с провижинингом datasource + дашборды HTTP/JVM/бизнес |
| Алерты | Prometheus alert rules (downtime, 5xx, всплески ошибок операций) |
| Логи | Logback (`logstash-logback-encoder`) → Logstash → Elasticsearch → Kibana |
| Chassis | общий `logback-spring.xml` в `infra/common`, единый паттерn с trace/span |
| front-ui | внесён в кластер (NodePort) — инструментирован наравне с сервисами |

---

## Доступ к UI (NodePort)

| Компонент | URL | Назначение |
|---|---|---|
| Front-UI | http://localhost:30082 | веб-интерфейс банка (вход через Keycloak) |
| Gateway | http://localhost:30080 | API-шлюз |
| Zipkin | http://localhost:30411 | трейсы (UI редиректит на /zipkin/) |
| Prometheus | http://localhost:30090 | метрики, targets, alerts |
| Grafana | http://localhost:30030 | дашборды (анонимный вход, Admin) |
| Kibana | http://localhost:30056 | поиск и визуализация логов |
| Keycloak | http://localhost:8090 | сервер авторизации (на хосте) |

Учётные данные пользователей: `alice / alice`, `bob / bob`.

---

## Трейсинг (Zipkin)

- Зависимости (chassis, в родительском pom для всех модулей):
  `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`; для accounts и
  notifications дополнительно `datasource-micrometer-spring-boot` (трейсинг JDBC).
- Сэмплирование 100% (`management.tracing.sampling.probability=1.0`),
  endpoint — `ZIPKIN_ENDPOINT` (в кластере `http://zipkin:9411/api/v2/spans`).
- Трейсятся: входящие/исходящие HTTP, запросы в БД (`connection`, `query`,
  `result-set`), Kafka (`notifications send` у accounts, `notifications receive`
  у notifications). Front-UI генерирует корневой trace при входе пользователя.
- В Zipkin UI: Run Query → видны цепочки `front-ui → gateway → cash → accounts → kafka → notifications`.

## Метрики (Prometheus)

- Каждый сервис и front-ui отдают `/actuator/prometheus`; Prometheus скрейпит их
  по ClusterIP DNS (job `bank-services`, 6 targets).
- Стандартные метрики: HTTP (RPS, 4xx, 5xx, гистограммы для персентилей), JVM
  (память, CPU), метрики Spring Boot.
- Кастомные бизнес-метрики:
  - `bank_cash_withdraw_failed_total{login}` — неуспешные снятия;
  - `bank_transfer_failed_total{from,to}` — неуспешные переводы;
  - `bank_notification_failed_total{login}` — сбой сохранения уведомления.
- Алерты (`/etc/prometheus/rules/alerts.yml`): `ServiceDown`,
  `HighServerErrorRate`, `FailedWithdrawalsSpike`, `FailedTransfersSpike`.

## Дашборды (Grafana)

Провижининг при старте (datasource Prometheus + дашборды из ConfigMap):
- **Bank — HTTP & JVM**: RPS, 4xx, 5xx, p95-латентность, JVM heap, CPU.
- **Bank — Business metrics**: неуспешные снятия / переводы / уведомления.

## Логи (ELK)

- Все сервисы и front-ui шлют логи в Logstash в JSON (`logstash-logback-encoder`),
  включается переменной `LOGSTASH_DESTINATION=logstash:5000` (chassis-logback).
- Logstash (TCP `5000`, codec `json_lines`) маскирует номера счетов и пишет в
  Elasticsearch индексом `bank-logs-YYYY.MM.dd`.
- В каждом документе есть `appName`, `traceId`, `spanId`, `message`, `level` —
  поиск по `traceId` в Kibana связывает логи с трейсом из Zipkin.
- Единый паттерн консоли: `%d %5p [appName,traceId,spanId] logger : msg`.

---

## Развёртывание в Kubernetes

### 1. Сборка образов приложений

```bash
docker build -t accounts:0.0.1      -f services/accounts/Dockerfile .
docker build -t cash:0.0.1          -f services/cash/Dockerfile .
docker build -t transfer:0.0.1      -f services/transfer/Dockerfile .
docker build -t notifications:0.0.1 -f services/notifications/Dockerfile .
docker build -t gateway:0.0.1       -f infra/gateway/Dockerfile .
docker build -t front-ui:0.0.1      -f services/front-ui/Dockerfile .
```

Образы инфраструктуры (Zipkin, Prometheus, Grafana, Elasticsearch, Logstash,
Kibana, Kafka, Postgres) тянутся из публичных реестров.

> Docker Desktop: выделите Kubernetes ≥ 8 ГБ памяти (ELK прожорлив).

### 2. Keycloak на хосте

```bash
docker compose up -d keycloak
curl -s -o /dev/null -w "%{http_code}\n" \
  http://localhost:8090/realms/bank/.well-known/openid-configuration   # 200
```

### 3. Установка чарта

```bash
kubectl config use-context docker-desktop
kubectl create namespace bank
helm install bank helm/bank -n bank
kubectl get pods -n bank -w
```

Post-install Job `kafka-topic-init` создаёт топик `notifications`.
Elasticsearch/Kibana стартуют дольше остальных (1–2 мин).

### 4. Проверка Helm-тестами

```bash
helm test bank -n bank
```

- **connectivity-test** — TCP/health всех компонентов, включая Zipkin,
  Prometheus, Grafana, Elasticsearch, Logstash, Kibana.
- **e2e-notification-test** — consumer notifications запущен.
- **gateway-route-test** — маршруты gateway.

### 5. Демонстрация наблюдаемости

```bash
# трафик
TOKEN=$(curl -s -X POST http://localhost:8090/realms/bank/protocol/openid-connect/token \
  -d grant_type=password -d client_id=front-ui -d client_secret=front-ui-secret \
  -d username=alice -d password=alice | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
curl -s -X POST http://localhost:30080/cash/deposit \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"amount":100.00}'
```

- Zipkin → http://localhost:30411 → Run Query (видны цепочки между сервисами).
- Grafana → http://localhost:30030 → папка **Bank**.
- Prometheus → http://localhost:30090 → Status → Targets / Alerts.
- Kibana → http://localhost:30056 → Discover → data view `bank-logs-*`.

### 6. Остановка

```bash
helm uninstall bank -n bank
kubectl delete namespace bank
docker compose down
```

---

## Структура Helm-чарта

```
helm/bank/
├── Chart.yaml                  зонтичный чарт (Sprint 12)
├── values.yaml                 global.* + переключатели всех подсистем
├── templates/
│   ├── keycloak-service.yaml   ExternalName → host.docker.internal:8090
│   ├── kafka-topic-init.yaml   post-install Job создания топика
│   ├── network-policies.yaml   ingress-матрица сервисов (+ metrics, zipkin)
│   ├── network-policies-elk.yaml  logstash ← сервисы; es ← logstash/kibana
│   └── tests/                  connectivity / e2e-notification / gateway-route
└── charts/
    ├── postgres/  kafka/  accounts/  notifications/  cash/  transfer/  gateway/
    ├── front-ui/               веб-интерфейс (NodePort 30082)
    ├── zipkin/                 трейсинг (in-memory)
    ├── prometheus/             метрики + alert rules (NodePort 30090)
    ├── grafana/                дашборды, провижининг (NodePort 30030)
    ├── elasticsearch/          хранилище логов (single-node, ephemeral)
    ├── logstash/               пайплайн логов TCP→ES
    └── kibana/                 визуализация логов (NodePort 30056)
```

---

## Микросервисы и взаимодействие

- Front-UI (Authorization Code Flow) → Gateway (проброс JWT) → микросервисы.
- Микросервисы между собой — Client Credentials Flow (cash/transfer → accounts).
- Уведомления: accounts пишет событие в транзакционный outbox
  (`FOR UPDATE SKIP LOCKED`, retry с backoff, recovery «зависших») → Kafka →
  notifications (идемпотентно по `eventId`).
- Микросервис может не иметь доступа к другому (cash не ходит в transfer).

| Сервис | Порт | Наблюдаемость |
|---|---|---|
| gateway | 8080 (NodePort 30080) | metrics, traces, logs |
| accounts | 8081 | metrics, traces (+JDBC, Kafka), logs |
| cash | 8083 | metrics (+бизнес), traces, logs |
| transfer | 8084 | metrics (+бизнес), traces, logs |
| notifications | 8085 | metrics (+бизнес), traces (+JDBC, Kafka), logs |
| front-ui | 8082 (NodePort 30082) | metrics, traces, logs |

---

## Сборка и тесты

```bash
mvn -s ~/central-settings.xml install   # unit, slice, Testcontainers, EmbeddedKafka, контрактные
```

В тестовом профиле трейсинг выключен (`sampling.probability=0.0`), Logstash не
подключается (без `LOGSTASH_DESTINATION` chassis-logback пишет только в консоль).

---

## Лицензия

Учебный проект (Яндекс Практикум, Спринт 12).