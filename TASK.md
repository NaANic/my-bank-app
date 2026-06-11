# Bank Microservices App — Sprint 10 Task

## Goal

Take the Sprint 9 "Bank" application and deploy its microservices to a local
Kubernetes cluster using Helm charts, replacing Consul-based discovery and
configuration with native Kubernetes mechanisms.

## Functionality (unchanged from Sprint 9)

A bank client logs in via the web frontend and can edit own account data,
deposit/withdraw money, and transfer money to another user. Services: Front UI,
Accounts, Cash, Transfer, Notifications. Auth via Keycloak (Authorization Code
Flow for the user; Client Credentials Flow service-to-service). Access matrix:
Accounts→Notifications; Cash→Accounts,Notifications; Transfer→Accounts,
Notifications; Cash cannot call Transfer (and vice versa).

## What's new in Sprint 10

- Microservices run in a local Kubernetes cluster (Rancher Desktop / Minikube /
  Kind / Colima / Docker Desktop).
- **Helm** is the package manager and templating engine.
- Databases are deployed as **StatefulSets**.
- Microservices are deployed as **Deployments** (one or more replicas).
- **Service Discovery** via a Kubernetes **Service** per microservice (DNS name
  resolution instead of Consul / Eureka / Zookeeper).
- **Gateway API**: keep Spring Cloud Gateway, or use **Ingress** (your choice).
- **Externalized config** via **ConfigMaps** and **Secrets** (instead of
  Consul / Zookeeper / Spring Cloud Config).
- Charts organized as **subcharts** under one **umbrella** Helm chart, stored in
  Git; deployable per service or all at once.
- **OAuth 2.0 server**: outside the cluster (provide in-cluster access) or
  inside via a Helm subchart (your choice).
- **Front UI**: outside the cluster (reach services via the Gateway through a
  NodePort / Ingress) or inside via a Helm subchart (your choice).
- Optional: per-environment deploys via **namespaces** (dev / test / prod).
- **Helm chart tests** must be implemented.

Two valid schemes:
1. Front UI + OAuth server **outside** the cluster.
2. Front UI + OAuth server **inside** the cluster.

This project uses **Scheme 1**: Spring Cloud Gateway retained; Keycloak and
Front-UI outside the cluster; gateway exposed via NodePort.

## Development steps

1. Umbrella Helm chart.
2. A subchart per microservice.
3. (Optional) subcharts for the OAuth server and Front UI.
4. Deployment, Service, StatefulSet, etc. for each service and its DB.
5. ConfigMaps and Secrets for settings and secrets.
6. Migrate to Kubernetes Service Discovery + ConfigMaps/Secrets; remove Consul /
   Zookeeper / Eureka / Spring Cloud Config.
7. (Optional) replace Spring Cloud Gateway with Ingress.
8. (Optional) in-cluster access to an external OAuth server.
9. External access via NodePort / Ingress (browser → Front UI, or Front UI →
   services).
10. Update integration / contract tests if needed.
11. Write Helm chart tests.
12. Update README (build / run / test / deploy in Kubernetes).
13. Micro-commits; push to GitHub after the final commit.

## Reviewer checks (in addition to Sprint 9)

- Correct Kubernetes manifests for the microservices.
- Correct Helm subcharts and umbrella chart.
- Helm chart tests present.
- Clear, well-formatted README.
- Micro-commits; correct rebase / merge in history.
- Green test runs.

## Out of scope (later sprints)

- Message buses / JMS / Kafka.
- Log aggregation and analysis (ELK).
- Health checks, monitoring, auditing.
- Distributed tracing.

## Submission

- Public GitHub repo, GitFlow.
- Branch off `main` (e.g. `feature/sprint-10`); full history on that branch.
- Open a Pull Request into `main`; send the reviewer the PR link.
- Address review comments as commits on the same branch; merge after approval.
