
# Bank Microservices App — Sprint 9 Task

## Goal

Build a microservices banking application using Spring Boot + Spring Cloud, demonstrating microservice architecture patterns.

## Functionality (user-facing)

A bank client logs in via a web frontend and can:

- Edit own account data (first name, last name, date of birth)

- Deposit virtual money to own account

- Withdraw virtual money from own account

- Transfer virtual money to another user's account

## Services

1. **Front UI** — web frontend, single HTML page (Thymeleaf), available only after OAuth login

2. **Accounts** — stores users (login, name, DOB, balance)

3. **Cash** — deposit/withdraw operations

4. **Transfer** — transfers between accounts

5. **Notifications** — sends notifications (log/email/alert) about completed actions

## Infrastructure components (required)

- **API Gateway** — own Spring Cloud Gateway service

- **Service Discovery** — Consul / Eureka / Zookeeper / custom

- **Externalized/Distributed Config** — Consul / Zookeeper / Spring Cloud Config

- **OAuth 2.0 Authorization Server** — Keycloak (or alternative, or custom with Spring Security OAuth)

## Technical requirements

- Java 21

- Spring Boot + Spring Cloud

- Maven or Gradle, multi-module project (one parent + submodules per service)

- Build all services with one command

- Persistent DB (PostgreSQL); single DB with schema-per-service is acceptable (Database per Service pattern)

- Data access: Spring Data JDBC / R2DBC / JPA + Hibernate

- Frontend: Spring Web MVC or Spring WebFlux

- Executable JAR per service, runnable in Tomcat / Jetty / Netty

- Each service in its own Docker container

- Docker Compose for orchestrating everything

## Auth flows

- **Front → microservices**: Authorization Code Flow (user login, JWT with user login + privileges for Accounts/Cash/Transfer)

- **Microservice → microservice**: Client Credentials Flow (service JWT)

- **Gateway**: forwards user JWT to backend services

- **Service-level access matrix**:

 - Accounts → Notifications

 - Cash → Accounts, Notifications

 - Transfer → Accounts, Notifications

 - Cash CANNOT call Transfer (and vice versa)

## Frontend UI blocks

Single HTML page with three blocks:

1. **Account data block**

  - Editable: last name + first name, date of birth

  - Read-only: current balance

  - Validation: all fields filled, age ≥ 18

  - "Save changes" button

2. **Cash block**

  - Amount input

  - "Deposit" and "Withdraw" buttons

  - Error if withdraw amount > balance

3. **Transfer block**

  - Recipient account selector (dropdown of other users)

  - Amount input

  - "Transfer" button

  - Error if amount > sender's balance

## Testing requirements

- Unit tests (JUnit 5)

- Integration tests (Spring Boot Test, TestContext Framework, context caching)

- Contract tests (Spring Cloud Contract)

## Microservice patterns to apply

Circuit Breaker, Service Discovery, Gateway API, RPI (Remote Procedure Invocation), Transactional Outbox, Access Token, UI Composition, Contract Testing, Externalized/Distributed Configs, Single Service per Host, Database per Service.

## Frontend template

A starter Maven project for the frontend is provided in `front-template/` directory. It contains:

- `src/main/resources/templates/main.html` — UI page

- `MainController` — controller returning main.html with TODO comments on each method

- DTOs for view binding

- `AccountStub` service — **must be removed**, used only for standalone demo

## Out of scope (will be in later sprints)

- Replication, Load Balancer

- Kafka / JMS / message buses

- ELK / log aggregation

- Health checks, monitoring, distributed tracing

- Kubernetes

- CI/CD (Jenkins etc.)

## Deliverables

- Public GitHub repo (e.g. `my-bank-app`)

- GitFlow branching, micro-commits

- README.md with build/run instructions (IDE, local, Docker)

- All tests passing

- Docker Compose runs the full stack

## Reviewer checks

- Multi-module project structure (pom.xml / build.gradle)

- Microservice interaction matches the schema

- Layered architecture per service (controller, service, model, DAO; view only in Front UI)

- SOLID, YAGNI, proper naming

- OAuth flows correctly implemented

- DB schema design

- Test coverage (unit + integration + contract)

- Dockerfile + docker-compose

- README quality

- Commit history (micro-commits, proper rebase/merge)

- All tests green

