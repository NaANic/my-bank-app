# my-bank-app — Спринт 10 (Kubernetes + Helm)

Учебное банковское приложение из пяти микросервисов на Spring Boot 3.5 за
шлюзом Spring Cloud Gateway, с Keycloak (OAuth 2.1) для аутентификации
пользователей и сервис-сервисного взаимодействия и единым PostgreSQL по
схеме **«отдельная схема на сервис»** (schema-per-service).

В десятом спринте проект переведён с Consul (discovery + config) на
**нативную модель Kubernetes**, упакованную в зонтичный (umbrella) **Helm**-чарт.
Consul полностью удалён: сервисы находят друг друга через DNS Kubernetes и
конфигурируются через переменные окружения (ConfigMap + Secret).

Чек-лист ревьюера — в `TASK.md`.

---

## Что изменилось по сравнению со Спринтом 9

| Область | Спринт 9 | Спринт 10 |
| --- | --- | --- |
| Service discovery | Consul (`lb://service`) | DNS Kubernetes (`http://service:port`) |
| Конфигурация | Consul KV + `spring.config.import` | переменные окружения через ConfigMap / Secret |
| Балансировка на клиенте | `spring-cloud-loadbalancer` | прямой `RestClient` к DNS-имени Service |
| Сборка / деплой | только `docker-compose` | зонтичный Helm-чарт (`helm/bank`) |
| Локальная инфраструктура | Consul + Postgres + Keycloak | Postgres + Keycloak (без Consul) |

Зависимости `spring-cloud-starter-consul-discovery`, `-consul-config` и
`-loadbalancer` удалены из всех модулей.

Дополнительно в этом спринте: общий код клиентов вынесен в модуль
`infra/common` (Spring Boot auto-configuration); у всех нагрузок заданы
`requests`/`limits`; readiness/liveness переведены на `httpGet /actuator/health`;
добавлены `NetworkPolicy`, ограничивающие входящие соединения согласно матрице
доступа между сервисами.

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
                 │ JPA+Flyway│      └────┬─────┘       └────┬─────┘
                 │  +outbox │           │ client_credentials │
                 └────┬─────┘           ▼  (SCOPE bank:service)
                      │           ┌──────────────┐
                      │  outbox   │ notifications│
                      └──────────►│   :8085      │
                                  │ JPA + Flyway │
                                  └──────┬───────┘
                       ┌──────────────────┴───────┐
                       ▼                           ▼
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
| accounts | 8081 | JPA + Flyway + транзакционный outbox |
| cash | 8083 | оркестратор пополнения / снятия |
| transfer | 8084 | переводы между счетами |
| notifications | 8085 | приёмник уведомлений, пишет в БД и лог |
| front-ui | 8082 | Thymeleaf + oauth2Login (запускается на хосте) |
| postgres | 5432 | StatefulSet, схема на сервис |
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

Kubernetes в Docker Desktop использует общее локальное хранилище образов, так
что локально собранные теги доступны напрямую (`pullPolicy: IfNotPresent`).

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

`accounts` и `notifications` используют init-контейнер `wait-for-postgres`,
поэтому запуск проходит без рестартов.

### 4. Проверка встроенными Helm-тестами

```bash
helm test bank -n bank
```

- **connectivity-test** — `wget` к `/actuator/health` каждого сервиса (проверяет
  готовность приложения, а не только открытый порт) + TCP к postgres.
- **gateway-route-test** — проходит по всем трём маршрутам
  `/accounts`, `/cash`, `/transfer` через `/<svc>/actuator/health` и ждёт `200`.

### 5. Доступ к приложению

```bash
curl -i http://localhost:30080/accounts/me      # 401 без токена = маршрут работает
```

Веб-интерфейс: запустить Front-UI на хосте, направив его на NodePort шлюза:
```bash
GATEWAY_URL=http://localhost:30080 \
  java -jar services/front-ui/target/front-ui-0.0.1-SNAPSHOT.jar
# http://localhost:8082 -> вход через Keycloak (alice / alice или bob / bob)
```

### 6. Остановка и удаление

```bash
helm uninstall bank -n bank
kubectl delete namespace bank
docker compose down
```

---

## Безопасность и устойчивость в кластере

- **ConfigMap / Secret** — несекретные настройки и пароли/секреты клиентов
  отдельно на каждый сервис, формируются из values Helm.
- **NetworkPolicy** (ingress) — каждый сервис принимает соединения только от
  разрешённых вызывающих: postgres ← accounts/notifications; accounts ←
  gateway/cash/transfer; notifications ← accounts/cash/transfer; cash и transfer
  ← только gateway. Тем самым, например, `cash` не может обращаться к `transfer`.
- **requests/limits** заданы для всех нагрузок (Burstable QoS).
- **readiness/liveness** — `httpGet /actuator/health` (эндпоинт открыт в
  SecurityConfig через `permitAll` на `/actuator/**`).

---

## Модель конфигурации

Каждый сервис читает конфигурацию из переменных окружения с разумными
значениями по умолчанию `localhost`, поэтому один и тот же jar без изменений
работает на ноутбуке, в docker-compose и в Kubernetes.

| Переменная | Кто использует | Значение в кластере |
| --- | --- | --- |
| `PG_HOST` / `PG_PASSWORD` | accounts, notifications | `postgres` / из Secret |
| `OAUTH_ISSUER_URI` | все resource server | `http://localhost:8090/realms/bank` |
| `OAUTH_JWK_SET_URI` / `OAUTH_TOKEN_URI` | все | `http://keycloak:8090/...` |
| `OAUTH_CLIENT_ID` / `OAUTH_CLIENT_SECRET` | cash, transfer, accounts | по сервису |
| `ACCOUNTS_BASE_URL` / `NOTIFICATIONS_BASE_URL` | cash, transfer, accounts | `http://accounts:8081`, `http://notifications:8085` |
| `ACCOUNTS_URI` / `CASH_URI` / `TRANSFER_URI` | gateway | `http://<svc>:<port>` |

---

## Общий модуль клиентов (`infra/common`)

Общий код сервис-сервисного взаимодействия вынесен в библиотеку `infra/common`
(пакет `ru.yandex.practicum.mybank.common`): `AbstractServiceClient`,
`NotificationsClient`, `AccountSnapshot`, `AccountsServiceException` и
`CommonClientAutoConfiguration` (бины `OAuth2AuthorizedClientManager` и
`NotificationsClient`). Модуль подключается как зависимость в accounts/cash/
transfer и регистрируется через Spring Boot auto-configuration
(`AutoConfiguration.imports`), поэтому бины подхватываются без явного
component-scan. Специфичные клиенты (`AccountsClient`) остаются в своих сервисах.

---

## Структура Helm-чарта

```
helm/bank/
├── Chart.yaml                 зонтичный чарт; объявляет каждый subchart как зависимость
├── values.yaml                global.* (image, postgres, keycloak) + переключатели
├── templates/
│   ├── keycloak-service.yaml  Service ExternalName -> Keycloak на хосте
│   ├── network-policies.yaml  ingress-политики (матрица доступа между сервисами)
│   └── tests/                 connectivity-test, gateway-route-test
└── charts/
    ├── postgres/              StatefulSet + headless Service + ConfigMap/Secret
    ├── accounts/              Deployment + Service + ConfigMap + Secret (+ init)
    ├── notifications/         Deployment + Service + ConfigMap (+ init)
    ├── cash/                  Deployment + Service + ConfigMap + Secret
    ├── transfer/              Deployment + Service + ConfigMap + Secret
    └── gateway/               Deployment + NodePort Service + ConfigMap
```

---

## Запуск локально без Kubernetes

```bash
docker compose up -d postgres keycloak
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
mvn install        # полный прогон: unit, slice, интеграционные (Testcontainers), контрактные
```

| Сервис | Unit / интеграционные | Контрактные |
| --- | --- | --- |
| accounts | `AccountServiceTest` (Mockito) · `AccountsIntegrationTest` (Testcontainers) | контракты-продюсеры генерируют `AccountsTest` + stubs-jar |
| cash | `CashControllerTest` (slice) | `AccountsClientContractTest` берёт стабы accounts из classpath |
| transfer | `TransferControllerTest` | — |
| notifications | `NotificationsIntegrationTest` (Testcontainers) | — |
| front-ui | `MainControllerTest` (`@WebMvcTest`) | — |
| gateway | `GatewayApplicationTests` (контекст + ID) · `GatewayRoutingTest` (маршрутизация через `WebTestClient`) | — |

Модуль accounts устанавливает свой stubs-jar на фазе test; cash использует его в
режиме stub-runner **CLASSPATH**, поэтому один `mvn install` разрешает всё в
рамках реактора. Работоспособность развёртывания проверяется отдельно командой
`helm test bank -n bank`.

---

## Структура репозитория

```
my-bank-app/
├── pom.xml                     родительский POM: Spring Boot 3.5 + Spring Cloud 2025.0.0
├── docker-compose.yml          Postgres + Keycloak + 6 сервисов (без Consul)
├── helm/bank/                  зонтичный Helm-чарт + subcharts
├── infra/
│   ├── common/                 общая библиотека клиентов (auto-configuration)
│   ├── gateway/                Spring Cloud Gateway
│   └── keycloak/import/        realm, импортируемый при старте Keycloak
└── services/
    ├── accounts/  cash/  transfer/  notifications/  front-ui/
```

---

## Лицензия

Учебный проект (Яндекс Практикум, Спринт 10).