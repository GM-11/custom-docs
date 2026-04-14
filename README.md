# custom-docs

A production-deployed, real-time collaborative document editor — built as a polyglot distributed system across C++, Go, Java, and TypeScript.

**Live:** [custom-docs.vercel.app](https://custom-docs.vercel.app)

---

## What this is

Google Docs-style collaborative editing, built from scratch with a focus on correctness under concurrency. The interesting part isn't the editor — it's what happens when two users type at the same offset simultaneously. This system resolves that at the algorithm level, not by locking or last-write-wins.

Every architectural decision here was made with a specific systems tradeoff in mind, not cargo-culted from tutorials.

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                  React + Slate.js                    │
│              (Vercel — custom-docs.vercel.app)       │
└──────────────────────┬──────────────────────────────┘
                       │ HTTPS / WSS
            ┌──────────▼──────────┐
            │   nginx Ingress     │  Azure AKS
            │   (TLS termination) │  52.224.224.177.nip.io
            └──┬──────┬──────┬───┘
               │      │      │
      ┌────────▼─┐ ┌──▼────┐ ┌▼──────────────┐
      │   Auth   │ │  Doc  │ │   Connection  │
      │  Service │ │Manager│ │    Manager    │
      │ Java/SB  │ │Java/SB│ │      Go       │
      │ :8082    │ │ :8081 │ │    :8080      │
      └────┬─────┘ └──┬────┘ └──────┬────────┘
           │          │             │
      ┌────▼──────────▼─────────────▼────────┐
      │  PostgreSQL  │  Kafka  │  C++ OT Lib  │
      │  (Azure VM)  │  (VM)   │  (CGo FFI)   │
      └──────────────────────────────────────┘
```

### Services

| Service | Language | Responsibility |
|---|---|---|
| **Connection Manager** | Go | WebSocket hub, fan-out, OT pipeline, Kafka publish |
| **Auth Service** | Java + Spring Boot | JWT issuance/validation, refresh token rotation, JWKS |
| **Document Manager** | Java + Spring Boot | Document persistence, RBAC, operation log, gRPC recovery |
| **OT Engine** | C++ (shared lib) | Conflict resolution, convergence, Lamport clock ordering |
| **Frontend** | React + Slate.js | Real-time editor, WebSocket client, presence UI |

---

## The OT Engine (C++)

The convergence problem: if User A inserts at offset 5 and User B deletes at offset 3, and these ops are concurrent — naively applying them in either order produces different documents. OT solves this by transforming each operation against concurrent ones before application, such that all clients converge to the same state.

The C++ engine is compiled as a shared library and called from Go via CGo FFI. It handles:
- Insert/delete transformation under concurrent edits
- Lamport clock-based causal ordering
- Transformation pipeline across multiple concurrent operations

**Known intentional scope limits:** compound operations, undo/redo, cursor position transformation. These are standard OT complexities — the engine demonstrates convergence mechanics, not production editor parity.

Memory ownership is explicit: `cOperationToGo` copies data without freeing; `freeCOperation` handles C-side cleanup; deferred closures manage C memory in the Go `TransformPipeline`.

---

## Go Connection Manager

Hub-per-document model. Each document has one Hub: a goroutine managing client registration, broadcast fan-out, and operation ordering.

**Restart recovery flow:**
1. New connection arrives → `HubExists` check in `hub_manager.go`
2. If Hub is absent → `LoadExistingDocument` → `recoverDocumentState` in `models/recovery.go`
3. gRPC call to Java docmanager → `GetOperationsSince(from_clock=0)`
4. All ops replayed through C++ OT engine
5. Hub initialized with correct `Content` + `LamportClock`
6. Client receives fully reconstructed document state

**Race condition fix:** `safeSend()` with defer/recover prevents send-to-closed-channel panics when fan-out hits a client that disconnected between broadcast and send.

**Kafka:** Operations published outside mutex scope (never hold a lock across blocking I/O). Kafka acts as a durability buffer — Go continues accepting writes even if Java is down; Java replays from Kafka on recovery.

---

## Auth Service (Java + Spring Boot)

- **RS256 asymmetric JWT** — private key signs tokens; Go fetches the public key from the JWKS endpoint on startup for zero-network-cost local token verification on the WebSocket path
- Access tokens: 15-minute expiry with refresh token rotation
- RBAC via `documents_access` table — owner must explicitly grant access; no implicit read-on-creation
- WebSocket auth via query param token (validated before upgrade)
- Grant-access endpoint resolves email → userId by calling `GET /auth/id?userEmail=` internally

Rationale for asymmetric JWT: in a microservices architecture, Go should verify tokens locally without trusting Java as a runtime dependency. RS256 enables this without sharing a secret.

---

## Document Manager (Java + Spring Boot)

- gRPC server on port 9090 via `GrpcServerConfig` (`@PostConstruct`/`@PreDestroy`)
- `DocumentRecoveryService` extends `DocumentRecoveryServiceGrpc.DocumentRecoveryServiceImplBase`
- Idempotency enforced via unique constraint on `operation_id` UUID — duplicate ops from network retries are silently deduplicated at the DB layer
- `GetOperationsSince` uses `CAST(:documentId AS uuid)` native query (Spring Data limitation with UUID params in JPQL)
- Proto generated via `protoc-jar-maven-plugin` + `build-helper-maven-plugin`
- **Must run via `java -jar`** — `mvn spring-boot:run` has classloader issues with generated protobuf classes

---

## Infrastructure

- **AKS** — 3-node cluster, nginx ingress controller (helm, default namespace), single public IP
- **TLS** — cert-manager + Let's Encrypt via nip.io domain
- **Azure Container Registry** — `customdocsregistry.azurecr.io`
- **Kafka** — `confluentinc/cp-kafka:7.4.0` on Azure VM, single topic `document-ops`; type field differentiates operation vs. snapshot messages
- **PostgreSQL** — two separate databases (auth DB, docmanager DB) — separate to avoid coupling and reduce blast radius
- **Vercel** — frontend static deploy

**Deployment:**
```bash
docker build -f services/<service>/Dockerfile -t customdocsregistry.azurecr.io/<service>:latest .
az acr login --name customdocsregistry
docker push customdocsregistry.azurecr.io/<service>:latest
kubectl rollout restart deployment/<service>
```

---

## Load Testing (k6)

Tested against local docker-compose. Four scenarios:

| Scenario | Result |
|---|---|
| Fan-out under high VU | ~738 msgs/sec broadcast throughput |
| Concurrent writes through OT | ~400 ops/sec |
| Connection churn | ~1,130 churn cycles |
| Sustained throughput | 47,500+ ops |

**100% WebSocket connection success across 105 concurrent users.**

AKS is smoke-tested only — local docker-compose used for stress testing to control cloud costs.

---

## Key Design Decisions

**Why polyglot?**
Each service uses the language that fits the problem. C++ for the OT engine because it's called in the hot path of every edit — latency matters. Go for connection management because goroutines make the fan-out model natural. Java for auth/docmanager because Spring Boot's ecosystem (gRPC, JPA, security) reduces boilerplate for service concerns.

**Why Kafka over direct gRPC for operations?**
Decoupling durability from availability. Go publishes operations to Kafka regardless of whether Java is up. Java consumes and persists asynchronously. This means a Java restart doesn't drop in-flight edits — Go's Kafka buffer absorbs them.

**Why separate auth and docmanager databases?**
Security boundary. A compromise of the docmanager DB doesn't expose credential hashes. Separate DBs also prevent accidental joins and tight schema coupling between services.

**Why event sourcing for operations?**
Document state is derived from the ordered operation log, not stored as mutable state. This makes recovery deterministic: replay all ops through OT → guaranteed convergence to correct state. It also enables future features like audit logs and undo history without schema changes.

**React StrictMode removed:**
StrictMode's double-mount in development causes two WebSocket connections to open simultaneously, triggering concurrent write panics in Go. Removed intentionally for this project.

---

## What's deferred (and why)

| Feature | Reason deferred |
|---|---|
| `GetLatestSnapshot` (S3) | Snapshots are an optimization — recovery via full op replay works correctly for MVP |
| Redis caching for hot snapshots | Premature optimization before snapshot infrastructure exists |
| `gRPC CreateDocument` | Currently fire-and-forget via Kafka; correct production approach acknowledged |
| Prometheus + Grafana | Observability layer — out of scope for MVP, in scope for next iteration |
| Compound ops / undo in OT | Standard production OT complexity; beyond scope for convergence demonstration |

---

## Running Locally

```bash
# Start infrastructure
docker-compose up -d  # PostgreSQL, Kafka, Zookeeper

# Auth service
cd auth-service && java -jar target/auth-service.jar

# Document manager
cd doc-manager && java -jar target/doc-manager.jar

# Connection manager
cd connection-manager && go run .

# Frontend
cd frontend && npm run dev
```

---

## Tech Stack

`C++` `Go` `Java 21` `Spring Boot` `React` `Slate.js` `Kafka` `PostgreSQL` `gRPC` `CGo` `AKS` `nginx` `cert-manager` `Vercel` `k6` `Docker` `Azure Container Registry`
