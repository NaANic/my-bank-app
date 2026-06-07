# my-bank-app — Sprint 9

A walking-skeleton bank built from five Spring Boot 3.5 microservices behind a
Spring Cloud Gateway, with Consul for discovery + config, Keycloak (OAuth 2.1)
for both user auth and service auth, and a single PostgreSQL backing every
service with the **schema-per-service** pattern.

Built as Yandex Practicum sprint 9 — see `TASK.md` for the reviewer checklist.

---

## What's in the box

```
                              ┌──────────────┐
   user's browser ───────────►│   Front-UI   │ Thymeleaf · oauth2Login
                              │  :8082       │ session cookie + CSRF
                              └──────┬───────┘
                                     │ Bearer (user JWT)
                                     ▼
                              ┌──────────────┐
                              │   Gateway    │ Spring Cloud Gateway
                              │  :8080       │ routes /accounts/** /cash/**
                              └──────┬───────┘        /transfer/**
                       ┌─────────────┼─────────────┐
                       ▼             ▼             ▼
                 ┌──────────┐  ┌──────────┐  ┌──────────┐
                 │ Accounts │  │   Cash   │  │ Transfer │   ─┐
                 │  :8081   │◄─│  :8083   │  │  :8084   │    │ lb://accounts
                 │          │  │          │──┼─────────►│    │ client_credentials
                 │  JPA     │  └──────────┘  └──────────┘    │ SCOPE_bank:service
                 │  Flyway  │
                 │  outbox  │  ─────────────────────────────►┌──────────────┐
                 └────┬─────┘  @Scheduled 5s · client_creds  │Notifications │
                      │                                      │  :8085       │
                      │ writes outbox row in same TX         │  JPA + log   │
                      ▼                                      └──────────────┘
                 ┌──────────────────────────────────┐
                 │  PostgreSQL (schemas: accounts,  │
                 │  notifications)                  │
                 └──────────────────────────────────┘

   ┌─────────┐    ┌───────────┐
   │ Consul  │    │ Keycloak  │  realm: bank
   │ :8500   │    │  :8090    │  clients: front-ui (auth-code),
   │ disc.+  │    │  :9000mgt │           cash/transfer/accounts/notifications
   │  KV cfg │    │           │           (client_credentials)
   └─────────┘    └───────────┘
```

| Pattern | Where |
| --- | --- |
| API Gateway | `infra/gateway` |
| Service Discovery + Externalized Config | Consul (one container does both) |
| Database per Service | one Postgres, `accounts` + `notifications` schemas, Flyway-managed |
| RPI (Remote Procedure Invocation) | Spring `RestClient` everywhere |
| Access Token | Keycloak-issued JWT: auth-code for user, client-credentials for services |
| Transactional Outbox | `accounts.outbox` written atomically with the balance change; `@Scheduled` poller drains every 5s |
| Circuit Breaker | Resilience4j `@CircuitBreaker` on every outbound REST call |
| Contract Testing | Spring Cloud Contract: Accounts → Cash one producer / one consumer pair |
| UI Composition | Front-UI is the only thing the browser sees; talks to backends via the gateway |
| Single Service per Host | one container per service in `docker-compose.yml` |

---

## Prerequisites

- **Java 21** (Eclipse Temurin or any JDK 21)
- **Maven 3.9+**
- **Docker** (engine 20+; Docker Compose v2)

Tested against Docker Engine 29.x. The root `pom.xml` pins
`-Dapi.version=1.45` on Surefire so Testcontainers' bundled `docker-java`
client speaks the right API version — leave that setting in place.

---

## Run the whole stack (one command)

```bash
docker compose up --build
```

That command:
1. Pulls Postgres 16, Consul 1.20, Keycloak 26.
2. Builds six Spring Boot images via multi-stage Dockerfiles
   (`maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre`).
3. Imports `infra/keycloak/import/bank-realm.json` on Keycloak startup
   (front-ui + cash + transfer + accounts + notifications clients, alice / bob
   users).
4. Brings everything up in dependency order via `depends_on`
   healthchecks.

First build takes 2–3 minutes (Maven downloads). Subsequent runs are
seconds because the local Maven cache is mounted into BuildKit.

| Service | Host port | Notes |
| --- | --- | --- |
| Front-UI | http://localhost:8082 | the only page a user opens |
| Gateway | http://localhost:8080 | `/accounts/**`, `/cash/**`, `/transfer/**` |
| Accounts | http://localhost:8081 | REST + outbox |
| Cash | http://localhost:8083 | deposit / withdraw |
| Transfer | http://localhost:8084 | transfer orchestrator |
| Notifications | http://localhost:8085 | sink |
| Keycloak (admin UI) | http://localhost:8090 | `admin` / `admin` |
| Keycloak (mgmt) | http://localhost:9000 | `/health/ready` etc. |
| Consul UI | http://localhost:8500 | service catalog + KV |
| Postgres | localhost:5432 | `bank` / `bank` / db `bank` |

### Try it
Open http://localhost:8082 in a browser → redirects to Keycloak → log in as
**alice / alice** (or **bob / bob**) → land on `main.html` with the
seeded profile, deposit/withdraw form, and a recipient dropdown.

---

## Run from IDE or local `mvn`

```bash
# 1. Bring up just the infra (Consul, Postgres, Keycloak)
docker compose up -d consul postgres keycloak

# 2. Build the world
mvn package -DskipTests

# 3. Run each service in a separate terminal (or via your IDE)
java -jar services/accounts/target/accounts-0.0.1-SNAPSHOT.jar
java -jar infra/gateway/target/gateway-0.0.1-SNAPSHOT.jar
java -jar services/cash/target/cash-0.0.1-SNAPSHOT.jar
java -jar services/transfer/target/transfer-0.0.1-SNAPSHOT.jar
java -jar services/notifications/target/notifications-0.0.1-SNAPSHOT.jar
java -jar services/front-ui/target/front-ui-0.0.1-SNAPSHOT.jar
```

Defaults in `application.yml` for every service point at `localhost`, so no
extra environment variables are needed when running on the host.

---

## Tests

```bash
mvn test          # 44 tests across 8 classes, ~70s
```

| Service | Unit + Integration | Contract |
| --- | --- | --- |
| accounts | `AccountServiceTest` (Mockito) · `AccountsIntegrationTest` (Testcontainers Postgres) | `AccountsTest` auto-generated by Spring Cloud Contract from `src/test/resources/contracts/` |
| cash | `CashControllerTest` (slice, mocked `AccountsClient`) | `AccountsClientContractTest` runs against stubs from the accounts module |
| transfer | `TransferControllerTest` | — |
| notifications | `NotificationsIntegrationTest` (Testcontainers Postgres) | — |
| front-ui | `MainControllerTest` (`@WebMvcTest`, clients mocked) | — |
| gateway | `GatewayApplicationTests` (context loads, route IDs registered) | — |

The accounts module installs its stubs jar (`accounts:stubs:…`) to the local
Maven repository in the **test** phase, so cash's stub-runner can resolve it
in a single `mvn test` invocation.

---

## OAuth flow at a glance

```
                       (1) /                        (2) 302 → /oauth2/authorization/keycloak
   browser  ─────────────────────────────────►  front-ui  ─────────────────────────────────►
                       (3) 302 → KC /auth                       (4) login form
            ◄─────────────────────────────────                  ◄────────────────────
            ──────────────────────────────────►                 ─────────────────────►
                       (5) POST creds                  (6) 302 → /login/oauth2/code/keycloak
            ◄─────────────────────────────────  KC               ◄────────────────────
                       (7) GET callback                          (8) server-side: code → token
   browser  ─────────────────────────────────►  front-ui  ──── HTTP ───►  http://keycloak:8090/token
                       (9) session cookie                        (access token in session)
            ◄─────────────────────────────────
```

For each REST call to a backend, front-ui pulls the user's access token out of
`OAuth2AuthorizedClientService` and sets `Authorization: Bearer …`. Cash,
Transfer, and the outbox poller in Accounts use **client_credentials** with
scope `bank:service`; backend resource servers reject user JWTs on
`/internal/**` and reject service JWTs on `/me`.

Inside the compose network the OAuth endpoint URIs are split so the browser
sees `http://localhost:8090/…` (real, port-forwarded) and backend pods talk to
Keycloak directly at `http://keycloak:8090/…`. The iss claim on every JWT
remains `http://localhost:8090/realms/bank` thanks to `KC_HOSTNAME=localhost`.

---

## Repository layout

```
my-bank-app/
├── pom.xml                                    Spring Boot 3.5 + Spring Cloud 2025.0.0 parent
├── docker-compose.yml                         9-container stack
├── infra/
│   ├── gateway/                               Spring Cloud Gateway
│   └── keycloak/import/bank-realm.json        Realm imported on Keycloak startup
└── services/
    ├── accounts/                              JPA + Flyway + outbox + REST
    ├── cash/                                  deposit/withdraw orchestrator
    ├── transfer/                              account-to-account orchestrator
    ├── notifications/                         POST /notifications sink (logs + persists)
    └── front-ui/                              Thymeleaf + Spring Security oauth2Login
```

Each service follows the same layout: `api/` controllers and DTOs, `domain/`
JPA entities + repositories (where applicable), `client/` outbound REST
clients, `config/` Spring `@Configuration` classes.

---

## Useful commands

```bash
# tail logs from one service
docker compose logs -f accounts

# inspect Consul-registered services
curl -s http://localhost:8500/v1/agent/services | jq

# inspect a JWT (no signature verification)
echo "$TOK" | cut -d. -f2 | base64 -d | jq

# poke an internal endpoint with a service token
SVC=$(curl -s -X POST http://localhost:8090/realms/bank/protocol/openid-connect/token \
  -d 'grant_type=client_credentials&client_id=cash&client_secret=cash-service-secret' | jq -r .access_token)
curl -s -H "Authorization: Bearer $SVC" http://localhost:8081/internal/accounts/alice/credit \
  -H 'Content-Type: application/json' -d '{"amount":1.00}'

# reset balances
docker exec bank-postgres psql -U bank -d bank \
  -c "update accounts.account set balance=1000 where login='alice'; update accounts.account set balance=500 where login='bob';"
```

---

## License

Educational project (Yandex Practicum sprint).
