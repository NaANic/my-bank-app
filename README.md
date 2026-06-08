# my-bank-app — Спринт 10 (Kubernetes + Helm)

Учебное банковское приложение из пяти микросервисов на Spring Boot 3.5 за
шлюзом Spring Cloud Gateway, с Keycloak (OAuth 2.1) для аутентификации
пользователей и сервис-сервисного взаимодействия и единым PostgreSQL по
схеме **«отдельная схема на сервис»** (schema-per-service).

В десятом спринте проект переведён с Consul (discovery + конфигурация) на
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
`-loadbalancer` удалены из всех модулей. Бизнес-логика не менялась — изменился
только способ обнаружения и конфигурирования сервисов.

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
`keycloak` сопоставляет внутрикластерное имя `keycloak` с хостом
(`host.docker.internal`), поэтому поды обращаются к нему по адресу
`http://keycloak:8090`, а claim `iss` остаётся равным
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

Kubernetes в Docker Desktop использует общее локальное хранилище образов,
поэтому локально собранные теги доступны напрямую (`pullPolicy: IfNotPresent`).

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
# дождитесь статуса healthy, затем:
curl -s -o /dev/null -w "%{http_code}\n" \
  http://localhost:8090/realms/bank/.well-known/openid-configuration   # -> 200
```

### 3. Установка чарта

```bash
kubectl config use-context docker-desktop      # ВАЖНО: разворачивать только здесь
kubectl create namespace bank
helm install bank helm/bank -n bank
kubectl get pods -n bank -w                     # дождитесь всех 1/1, затем Ctrl-C
```

`accounts` и `notifications` используют init-контейнер `wait-for-postgres`,
поэтому запуск проходит без рестартов.

### 4. Проверка встроенными Helm-тестами

```bash
helm test bank -n bank
```

Как Helm test-хуки запускаются два набора:

- **connectivity-test** — проверяет по TCP каждый включённый Service по DNS-имени.
- **gateway-route-test** — обращается к `http://gateway:8080/accounts/me`; любой
  ответ не из класса 5xx (например, `401` при отсутствии токена) подтверждает,
  что шлюз направил запрос в живой бэкенд.

### 5. Доступ к приложению

Шлюз опубликован через NodePort `30080`:

```bash
curl -i http://localhost:30080/accounts/me      # 401 без токена = маршрутизация работает
```

Чтобы пользоваться веб-интерфейсом, запустите Front-UI на хосте, направив его
на шлюз через NodePort:

```bash
GATEWAY_URL=http://localhost:30080 \
  java -jar services/front-ui/target/front-ui-0.0.1-SNAPSHOT.jar
# откройте http://localhost:8082 -> вход через Keycloak (alice / alice или bob / bob)
```

### 6. Остановка и удаление

```bash
helm uninstall bank -n bank
kubectl delete namespace bank        # также удаляет PVC PostgreSQL
docker compose down                  # остановить Keycloak
```

---

## Модель конфигурации

Каждый сервис читает конфигурацию из переменных окружения с разумными
значениями по умолчанию `localhost`, зашитыми в `application.yml`, поэтому один
и тот же jar без изменений работает на ноутбуке, в docker-compose и в Kubernetes.

| Переменная | Кто использует | Значение в кластере |
| --- | --- | --- |
| `PG_HOST` / `PG_PASSWORD` | accounts, notifications | `postgres` / из Secret |
| `OAUTH_ISSUER_URI` | все resource server | `http://localhost:8090/realms/bank` |
| `OAUTH_JWK_SET_URI` / `OAUTH_TOKEN_URI` | все | `http://keycloak:8090/...` |
| `OAUTH_CLIENT_ID` / `OAUTH_CLIENT_SECRET` | cash, transfer, accounts | по сервису |
| `ACCOUNTS_BASE_URL` / `NOTIFICATIONS_BASE_URL` | cash, transfer, accounts | `http://accounts:8081`, `http://notifications:8085` |
| `ACCOUNTS_URI` / `CASH_URI` / `TRANSFER_URI` | gateway | `http://<svc>:<port>` |

В Kubernetes эти значения задаются через ConfigMap (несекретные) и Secret
(пароли, секреты клиентов) для каждого сервиса, формируемые из values Helm.

---

## Структура Helm-чарта

```
helm/bank/
├── Chart.yaml                 зонтичный чарт; объявляет каждый subchart как зависимость
├── values.yaml                global.* (image, postgres, keycloak) + переключатели
├── templates/
│   ├── keycloak-service.yaml  Service ExternalName -> Keycloak на хосте
│   └── tests/                 connectivity-test, gateway-route-test
└── charts/
    ├── postgres/              StatefulSet + headless Service + ConfigMap/Secret
    ├── accounts/              Deployment + Service + ConfigMap + Secret (+ init)
    ├── notifications/         Deployment + Service + ConfigMap (+ init)
    ├── cash/                  Deployment + Service + ConfigMap + Secret
    ├── transfer/              Deployment + Service + ConfigMap + Secret
    └── gateway/               Deployment + NodePort Service + ConfigMap
```

Каждый subchart можно установить отдельно (в его `values.yaml` есть резервные
значения `global.*`); зонтичный чарт задаёт общие значения `global.*` и
переключатель `enabled` для каждого subchart.

---

## Запуск локально без Kubernetes

Consul больше не нужен. Поднимите только Postgres + Keycloak и запустите jar-ы
(значения по умолчанию уже указывают на `localhost`).

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

Либо поднять весь стек в контейнерах:

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
| gateway | `GatewayApplicationTests` (контекст + ID маршрутов) | — |

Модуль accounts устанавливает свой stubs-jar (`accounts:…:stubs`) на фазе
test; cash использует его в режиме stub-runner **CLASSPATH**, поэтому один
`mvn install` разрешает все зависимости в рамках реактора.

Работоспособность развёртывания в Kubernetes проверяется отдельно командой
`helm test bank -n bank`.

---

## Структура репозитория

```
my-bank-app/
├── pom.xml                     родительский POM: Spring Boot 3.5 + Spring Cloud 2025.0.0
├── docker-compose.yml          Postgres + Keycloak + 6 сервисов (без Consul)
├── helm/bank/                  зонтичный Helm-чарт + subcharts
├── infra/
│   ├── gateway/                Spring Cloud Gateway
│   └── keycloak/import/        realm, импортируемый при старте Keycloak
└── services/
    ├── accounts/  cash/  transfer/  notifications/  front-ui/
```

---

## Лицензия

Учебный проект (Яндекс Практикум, Спринт 10).