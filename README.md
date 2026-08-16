# Calendly-Lite 🗓️

> **A production-hardened scheduling API** built for the Google Hackathon 2026.  
> Implements 7 enterprise-grade backend patterns from scratch — no shortcuts.

**Stack:** Java 17 · Spring Boot 3.3 · PostgreSQL · Redis · Redisson · Bucket4j · Flyway · Swagger/OpenAPI

---

## Table of Contents

1. [What It Does](#what-it-does)
2. [Live API Demo](#live-api-demo)
3. [Architecture Overview](#architecture-overview)
4. [Patterns Deep-Dive](#patterns-deep-dive)
   - [1. Idempotency](#1-idempotency)
   - [2. Distributed Lock](#2-distributed-lock)
   - [3. Outbox Pattern](#3-outbox-pattern)
   - [4. Retry Logic](#4-retry-logic)
   - [5. Dead Letter Queue](#5-dead-letter-queue)
   - [6. Redis Caching](#6-redis-caching)
   - [7. Rate Limiting](#7-rate-limiting)
5. [Project Structure](#project-structure)
6. [Database Schema](#database-schema)
7. [API Reference](#api-reference)
8. [Setup & Running Locally](#setup--running-locally)
9. [Design Decisions](#design-decisions)
10. [Tech Stack](#tech-stack)

---

## What It Does

Calendly-Lite is a **slot-based booking system** where:

- 🧑‍⚕️ **Hosts** (doctors, consultants, teachers) register and add available time slots
- 🙋 **Guests** browse open slots and book one with their name and email
- ✉️ **Notifications** (simulated) are reliably dispatched via an outbox worker
- 🛡️ The system is protected against **double-bookings, duplicate requests, race conditions, and abuse**

---

## Live API Demo

Once running, the full interactive API is at **http://localhost:8080/swagger-ui.html**

```
Hosts & Slots ──── GET  /api/hosts
                   POST /api/hosts
                   GET  /api/hosts/{id}
                   POST /api/hosts/{id}/availability
                   GET  /api/hosts/{id}/slots          ← Redis cached

Bookings ────────  POST   /api/slots/{id}/book         ← 4 patterns applied here
                   GET    /api/bookings/{id}
                   DELETE /api/bookings/{id}

Admin / Ops ──────  GET  /api/admin/outbox-events
                    GET  /api/admin/dead-letter-events
```

Every response uses a consistent envelope:
```json
{
  "success": true,
  "message": "Slot booked",
  "data": { ... },
  "timestamp": "2026-08-16T10:00:00"
}
```

---

## Architecture Overview

```
┌────────────────────────────────────────────────────────────────────┐
│                          CLIENT                                     │
│                  (Swagger UI / cURL / Postman)                      │
└───────────────────────────────┬────────────────────────────────────┘
                                │ HTTP
                                ▼
┌────────────────────────────────────────────────────────────────────┐
│                       SPRING BOOT API                               │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  LAYER 1 — Rate Limiter (Bucket4j, per-IP token bucket)     │   │
│  │           → 429 Too Many Requests after 5 req/min           │   │
│  └───────────────────────────┬─────────────────────────────────┘   │
│                              │                                      │
│  ┌───────────────────────────▼─────────────────────────────────┐   │
│  │  LAYER 2 — Idempotency Key Check (PostgreSQL lookup)        │   │
│  │           → returns existing booking if key seen before     │   │
│  └───────────────────────────┬─────────────────────────────────┘   │
│                              │                                      │
│  ┌───────────────────────────▼─────────────────────────────────┐   │
│  │  LAYER 3 — Distributed Lock (Redisson / Redis)              │   │
│  │           → only 1 thread can book a given slot at a time   │   │
│  └───────────────────────────┬─────────────────────────────────┘   │
│                              │                                      │
│  ┌───────────────────────────▼─────────────────────────────────┐   │
│  │  LAYER 4 — Database Transaction (atomic)                    │   │
│  │            ├─ slot.status = BOOKED                          │   │
│  │            ├─ INSERT bookings row                           │   │
│  │            └─ INSERT outbox_events row (PENDING)            │   │
│  └─────────────────────────────────────────────────────────────┘   │
└──────────────────┬─────────────────────────┬───────────────────────┘
                   │                         │
                   ▼                         ▼
           ┌──────────────┐         ┌────────────────────┐
           │  PostgreSQL  │         │       Redis         │
           │  (5 tables)  │         │  ┌──────────────┐  │
           │              │         │  │  Slot Cache  │  │
           └──────────────┘         │  │  (5 min TTL) │  │
                                    │  ├──────────────┤  │
                                    │  │  Dist Locks  │  │
                                    │  └──────────────┘  │
                                    └────────────────────┘

Background Worker (every 5 seconds, @Scheduled):
  ┌──────────────────────────────────────────────────────────┐
  │  OutboxWorkerService                                      │
  │                                                           │
  │  SELECT * FROM outbox_events WHERE status = 'PENDING'    │
  │      │                                                    │
  │      ├── SUCCESS → status = SENT                          │
  │      │                                                    │
  │      └── FAILURE → retry_count++                          │
  │              ├── retry_count < 3  → stay PENDING          │
  │              └── retry_count >= 3 → move to DLQ, FAILED   │
  └──────────────────────────────────────────────────────────┘
```

---

## Patterns Deep-Dive

### 1. Idempotency

**Problem:** A guest's network drops after the server saves the booking but before the client gets the response. The client retries — and creates a **duplicate booking**.

**Solution:** Every booking request must include a client-generated `Idempotency-Key: <UUID>` header. Before doing any work, the server looks it up in the database:

```java
// BookingService.java
Optional<Booking> existing = bookingRepository.findByIdempotencyKey(idempotencyKey);
if (existing.isPresent()) {
    return existing.get(); // ← returns original booking, does nothing new
}
```

The `idempotency_key` column has a `UNIQUE` constraint at the database level — it is the final safety net even if two requests race through simultaneously.

**Result:** The client can retry indefinitely with the same key and always gets the same response. Safe, predictable, and RFC-compliant.

---

### 2. Distributed Lock

**Problem:** Two guests hit "Book" on the same slot at the exact same millisecond. Both pass the idempotency check (different keys). Both read the slot as `OPEN`. Both try to save a booking. One will hit a DB constraint error — but only *after* a bad user experience.

**Solution:** Before reading the slot, acquire a **per-slot Redis lock** via Redisson. Only one thread can hold the lock for a given slot at a time:

```java
// BookingService.java
String lockKey = "lock:slot:" + slotId;
RLock lock = redissonClient.getLock(lockKey);
boolean acquired = lock.tryLock(MAX_LOCK_WAIT_SECONDS=3, LOCK_LEASE_SECONDS=10, TimeUnit.SECONDS);

if (!acquired) {
    throw new ApiException("Slot is currently being booked. Try again.", CONFLICT);
}
// ← only one thread reaches here per slot
```

The lock has:
- **3s wait timeout** — concurrent requesters get a clean 409, not an internal error
- **10s lease** — lock auto-expires if the app crashes mid-transaction (no stuck locks)
- **`lock.isHeldByCurrentThread()` check** in `finally` — safe unlock even after exceptions

**Result:** Concurrent double-bookings are impossible. The second request gets a clean 409 Conflict.

---

### 3. Outbox Pattern

**Problem:** After saving a booking, the app needs to "send a notification" (email, webhook, etc.). The naive approach:

```java
bookingRepository.save(booking);       // ← step A
notificationService.sendEmail(...);    // ← step B — what if the app crashes here?
```

If the app crashes between A and B, the booking exists but the notification is **silently lost forever**.

**Solution:** Instead of calling an external service directly, insert a notification task into the same database transaction as the booking:

```java
// BookingService.java — inside @Transactional
bookingRepository.save(booking);        // ← step A

OutboxEvent event = OutboxEvent.builder()
    .eventType("BOOKING_CONFIRMED")
    .payload(buildPayload(booking))     // ← JSON with booking details
    .status(OutboxStatus.PENDING)
    .build();
outboxEventRepository.save(event);      // ← step B — same transaction, atomic!
```

Both rows are committed together or rolled back together. A background worker (`OutboxWorkerService`) polls the table every 5 seconds and "sends" pending events. If the app crashes, the event is still in the DB when it restarts.

**Result:** Zero notification loss. Every booking is guaranteed to eventually trigger a notification attempt.

---

### 4. Retry Logic

**Problem:** The notification service (email provider, webhook endpoint) is temporarily unavailable.

**Solution:** The outbox worker retries failed events automatically, up to `MAX_RETRIES = 3` times:

```java
// OutboxWorkerService.java
} catch (Exception ex) {
    int newRetry = event.getRetryCount() + 1;
    event.setRetryCount(newRetry);

    if (newRetry >= MAX_RETRIES) {
        // → Dead Letter Queue (see below)
    } else {
        outboxEventRepository.save(event); // stays PENDING, retried next cycle
    }
}
```

The worker runs every 5 seconds (`@Scheduled(fixedDelay = 5000)`), so the retry cadence is roughly: 5s → 10s → 15s. For production, this would be replaced with exponential backoff.

> ℹ️ A **30% simulated failure rate** (`ThreadLocalRandom.current().nextDouble() < 0.30`) is intentionally left in so you can observe retries and DLQ behaviour in the Swagger demo. Remove it for production.

---

### 5. Dead Letter Queue

**Problem:** After 3 retries the event still fails. What happens to it?

**Solution:** The worker moves the exhausted event to a `dead_letter_events` table and marks the original event as `FAILED`. Nothing is silently dropped:

```java
if (newRetry >= MAX_RETRIES) {
    DeadLetterEvent dlq = DeadLetterEvent.builder()
        .originalEventId(event.getId())
        .eventType(event.getEventType())
        .payload(event.getPayload())
        .failureReason(ex.getMessage())
        .build();
    deadLetterEventRepository.save(dlq);

    event.setStatus(OutboxStatus.FAILED);
    outboxEventRepository.save(event);
}
```

Operations can inspect failures at `GET /api/admin/dead-letter-events` and replay or alert on them.

**Result:** Full observability. Every notification failure is visible, traceable, and recoverable.

---

### 6. Redis Caching

**Problem:** `GET /api/hosts/{id}/slots` is called constantly — by every client refreshing the slot picker UI. Each call hits PostgreSQL unnecessarily.

**Solution:** Spring's `@Cacheable` caches the slot list in Redis for 5 minutes:

```java
// HostService.java
@Cacheable(value = "slots", key = "#hostId")
public List<AvailabilitySlot> getOpenSlots(Long hostId) {
    return slotRepository.findByHost_IdAndStatus(hostId, SlotStatus.OPEN);
}
```

The cache is invalidated (evicted) whenever the slot state changes:

```java
// BookingService.java — on book or cancel
cacheManager.getCache("slots").evict(hostId);
```

> ⚠️ **Design note:** `@CacheEvict` annotations on methods called within the same bean (self-invocation) are silently bypassed by Spring AOP. We deliberately use `CacheManager.evict()` directly to avoid this known trap.

**Config (application.properties):**
```properties
spring.cache.type=redis
spring.cache.redis.time-to-live=300000  # 5 minutes
```

**Result:** Near-zero DB load on the most-read endpoint; cache is always consistent with the DB.

---

### 7. Rate Limiting

**Problem:** A bot or malicious user spams the booking endpoint, exhausting server resources or gaming the system.

**Solution:** Bucket4j implements a **token bucket** algorithm, one bucket per client IP:

```java
// RateLimiterConfig.java
Bandwidth limit = Bandwidth.builder()
    .capacity(5)                           // 5 tokens max
    .refillGreedy(5, Duration.ofMinutes(1)) // refill 5 per minute
    .build();
```

```java
// BookingController.java
Bucket bucket = rateLimiterConfig.resolveBucketForIp(clientIp);
if (!bucket.tryConsume(1)) {
    return ResponseEntity.status(429)
        .body(ApiResponse.error("Rate limit exceeded: max 5 booking attempts per minute."));
}
```

IP detection respects `X-Forwarded-For` for proxy/load-balancer deployments.

**Result:** Each IP can make at most 5 booking attempts per minute. Exceeding returns HTTP 429 immediately — before any DB or Redis work is done.

---

## Project Structure

```
calendly-lite/
│
├── 📄 pom.xml                          Maven dependencies
├── 📄 docker-compose.yml               PostgreSQL + Redis containers
├── 📄 .gitignore
│
└── src/main/
    ├── java/com/hackathon/calendlylite/
    │   │
    │   ├── 🚀 CalendlyLiteApplication.java    Entry point (@EnableCaching, @EnableScheduling)
    │   ├── 🌱 DataSeeder.java                  Seeds 2 hosts + 5 slots on first boot
    │   │
    │   ├── config/
    │   │   ├── JacksonConfig.java          Global ObjectMapper bean (JavaTimeModule)
    │   │   ├── OpenApiConfig.java          Swagger/OpenAPI metadata
    │   │   └── RateLimiterConfig.java      Bucket4j per-IP token bucket
    │   │
    │   ├── controller/
    │   │   ├── HostController.java         GET/POST /api/hosts, POST /availability, GET /slots
    │   │   ├── BookingController.java      POST /book, GET /bookings/{id}, DELETE /bookings/{id}
    │   │   └── AdminController.java        GET /admin/outbox-events, /dead-letter-events
    │   │
    │   ├── service/
    │   │   ├── BookingService.java         Core booking logic: idempotency + lock + outbox
    │   │   ├── HostService.java            Host CRUD + slot management + caching
    │   │   └── OutboxWorkerService.java    @Scheduled worker: retry + DLQ
    │   │
    │   ├── entity/
    │   │   ├── Host.java                   JPA entity for hosts table
    │   │   ├── AvailabilitySlot.java       JPA entity for availability_slots (Serializable for Redis)
    │   │   ├── Booking.java                JPA entity for bookings
    │   │   ├── OutboxEvent.java            JPA entity for outbox_events
    │   │   └── DeadLetterEvent.java        JPA entity for dead_letter_events
    │   │
    │   ├── repository/
    │   │   ├── HostRepository.java
    │   │   ├── AvailabilitySlotRepository.java   (custom JPQL overlap check)
    │   │   ├── BookingRepository.java             (idempotency key lookup)
    │   │   ├── OutboxEventRepository.java         (find by PENDING status)
    │   │   └── DeadLetterEventRepository.java     (ordered by moved_at)
    │   │
    │   ├── dto/
    │   │   ├── ApiResponse.java            Generic response wrapper {success, message, data, timestamp}
    │   │   ├── BookingRequest.java         {guestName, guestEmail} + validation
    │   │   ├── CreateHostRequest.java      {name, email} + validation
    │   │   └── CreateSlotRequest.java      {startTime, endTime} + @Future validation
    │   │
    │   ├── enums/
    │   │   ├── SlotStatus.java             OPEN | BOOKED | CANCELLED
    │   │   ├── BookingStatus.java          CONFIRMED | CANCELLED
    │   │   └── OutboxStatus.java           PENDING | SENT | FAILED
    │   │
    │   └── exception/
    │       ├── ApiException.java           RuntimeException with HttpStatus
    │       └── GlobalExceptionHandler.java @RestControllerAdvice for consistent error JSON
    │
    └── resources/
        ├── application.properties
        └── db/migration/
            ├── V1__create_hosts.sql
            ├── V2__create_availability_slots.sql
            ├── V3__create_bookings.sql
            ├── V4__create_outbox_events.sql
            └── V5__create_dead_letter_events.sql
```

---

## Database Schema

5 tables, fully managed by **Flyway** (auto-migrates on startup):

```
┌──────────────────┐         ┌──────────────────────────┐         ┌──────────────────────────┐
│      hosts       │         │    availability_slots     │         │        bookings           │
├──────────────────┤    1:N  ├──────────────────────────┤   1:1   ├──────────────────────────┤
│ id          PK   │◄────────│ id              PK        │◄────────│ id              PK        │
│ name        NN   │         │ host_id         FK,NN     │         │ slot_id         FK,UQ,NN  │
│ email       UQ   │         │ start_time      NN        │         │ guest_name      NN        │
│ created_at  NN   │         │ end_time        NN        │         │ guest_email     NN        │
└──────────────────┘         │ status          NN        │         │ status          NN        │
                             │   (OPEN/BOOKED/CANCELLED) │         │ idempotency_key UQ,NN     │
                             │ created_at      NN        │         │ created_at      NN        │
                             └──────────────────────────┘         └──────────────────────────┘

┌──────────────────────────┐         ┌──────────────────────────┐
│      outbox_events       │         │    dead_letter_events     │
├──────────────────────────┤         ├──────────────────────────┤
│ id               PK      │         │ id               PK      │
│ event_type       NN      │         │ original_event_id NN     │
│ payload (TEXT)   NN      │         │ event_type       NN      │
│ status           NN      │         │ payload (TEXT)   NN      │
│  (PENDING/SENT/FAILED)   │         │ failure_reason   TEXT    │
│ retry_count      NN      │         │ moved_at         NN      │
│ created_at       NN      │         └──────────────────────────┘
│ last_attempted_at        │
└──────────────────────────┘
```

**Key constraints:**
- `hosts.email` — UNIQUE (no duplicate host accounts)
- `bookings.slot_id` — UNIQUE (one booking per slot, enforced at DB level)
- `bookings.idempotency_key` — UNIQUE (idempotency guaranteed even under race)
- `availability_slots.end_time > start_time` — CHECK constraint
- `idx_outbox_status` — index on `status` for fast PENDING queries
- `idx_slots_host_status` — composite index for slot list lookups

---

## API Reference

### Host Endpoints

#### `GET /api/hosts`
List all registered hosts.

```bash
curl http://localhost:8080/api/hosts
```

#### `POST /api/hosts`
Register a new host.

```bash
curl -X POST http://localhost:8080/api/hosts \
  -H "Content-Type: application/json" \
  -d '{"name": "Dr. Priya Sharma", "email": "priya@clinic.com"}'
```
Response: `201 Created`

#### `GET /api/hosts/{id}`
Get a host by ID.

```bash
curl http://localhost:8080/api/hosts/1
```

#### `POST /api/hosts/{id}/availability`
Add a bookable time slot. `startTime` and `endTime` must be **in the future**. Overlap with existing slots is rejected.

```bash
curl -X POST http://localhost:8080/api/hosts/1/availability \
  -H "Content-Type: application/json" \
  -d '{"startTime": "2026-08-20T10:00:00", "endTime": "2026-08-20T11:00:00"}'
```
Response: `201 Created`

#### `GET /api/hosts/{id}/slots`
List all OPEN slots for a host. **Redis cached for 5 minutes.**

```bash
curl http://localhost:8080/api/hosts/1/slots
```

---

### Booking Endpoints

#### `POST /api/slots/{id}/book` ⭐ *Core endpoint — all 7 patterns*

Book a slot. Requires the `Idempotency-Key` header (any unique string, e.g. a UUID).

```bash
curl -X POST http://localhost:8080/api/slots/1/book \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{
    "guestName": "Mukesh Mali",
    "guestEmail": "mukesh@example.com"
  }'
```

**Success `201 Created`:**
```json
{
  "success": true,
  "message": "Slot booked",
  "data": {
    "id": 1,
    "slotId": 1,
    "guestName": "Mukesh Mali",
    "guestEmail": "mukesh@example.com",
    "status": "CONFIRMED",
    "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
    "createdAt": "2026-08-16T10:00:00"
  },
  "timestamp": "2026-08-16T10:00:00"
}
```

**Possible error responses:**

| HTTP | When |
|------|------|
| `400` | Missing `Idempotency-Key` header, or invalid request body |
| `404` | Slot ID does not exist |
| `409` | Slot already booked, or lock timeout (another booking in progress) |
| `429` | Rate limit exceeded (>5 requests/min from this IP) |

#### `GET /api/bookings/{id}`
Get a booking by ID.

```bash
curl http://localhost:8080/api/bookings/1
```

#### `DELETE /api/bookings/{id}`
Cancel a booking. Frees the slot back to OPEN and triggers a `BOOKING_CANCELLED` outbox event.

```bash
curl -X DELETE http://localhost:8080/api/bookings/1
```

---

### Admin / Observability Endpoints

#### `GET /api/admin/outbox-events`
View all outbox events and their lifecycle status (PENDING → SENT or FAILED).

```bash
curl http://localhost:8080/api/admin/outbox-events
```

#### `GET /api/admin/dead-letter-events`
View events that failed all 3 retry attempts. Ordered by most recent first.

```bash
curl http://localhost:8080/api/admin/dead-letter-events
```

---

## Setup & Running Locally

### Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Java JDK | 17+ | Project targets `--release 17` |
| PostgreSQL | 15+ | Running on port 5432 |
| Redis | 6+ | Running on port 6379 |
| Maven | 3.8+ | Or use `mvnw` wrapper if available |
| Docker | Optional | Easiest way to get Postgres + Redis |

---

### Option A — Docker (Recommended, 1 command)

```bash
# Start PostgreSQL 16 + Redis 7 in containers
docker compose up -d

# Verify both are healthy
docker compose ps
```

Containers expose:
- PostgreSQL → `localhost:5432` (db: `calendly_lite`, user: `postgres`, password: `password`)
- Redis → `localhost:6379`

---

### Option B — Native (Windows)

**PostgreSQL:**
```powershell
# Install via EDB installer (https://www.enterprisedb.com/downloads/postgres-postgresql-downloads)
# Set password to "password" during setup, keep port 5432

# Create the database (after install)
$env:PGPASSWORD = "password"
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -c "CREATE DATABASE calendly_lite;"
```

**Redis:**
```powershell
winget install Redis.Redis
redis-server   # Start Redis (leave this terminal open)
```

---

### Run the Application

```bash
# Navigate to the project root
cd calendly-lite

# Start with Maven
mvn spring-boot:run

# Or using Maven Wrapper (if mvnw is present)
./mvnw spring-boot:run        # Linux/macOS
.\mvnw.cmd spring-boot:run    # Windows
```

**First startup sequence:**
1. Flyway runs 5 migrations → creates all tables
2. `DataSeeder` inserts 2 demo hosts and 5 open slots
3. App is ready on **http://localhost:8080**

**Open Swagger UI:** http://localhost:8080/swagger-ui.html

---

### Environment Configuration

All config is in [`src/main/resources/application.properties`](src/main/resources/application.properties).

Override any value with environment variables (standard Spring Boot convention):

```bash
# Example overrides
SPRING_DATASOURCE_URL=jdbc:postgresql://myhost:5432/calendly_lite
SPRING_DATASOURCE_PASSWORD=mysecurepassword
SPRING_DATA_REDIS_HOST=myredishost
```

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8080` | HTTP port |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/calendly_lite` | PostgreSQL JDBC URL |
| `spring.datasource.username` | `postgres` | DB username |
| `spring.datasource.password` | `password` | DB password |
| `spring.data.redis.host` | `localhost` | Redis host |
| `spring.data.redis.port` | `6379` | Redis port |
| `spring.cache.redis.time-to-live` | `300000` | Cache TTL in ms (5 min) |
| `spring.task.scheduling.pool.size` | `5` | Outbox worker thread pool |
| `logging.level.com.hackathon` | `DEBUG` | App log level |

---

### Quick Demo Flow (Step-by-step)

```bash
BASE=http://localhost:8080

# 1. List demo hosts (seeded on startup)
curl $BASE/api/hosts

# 2. See Dr. Priya's open slots (3 slots, Redis cached)
curl $BASE/api/hosts/1/slots

# 3. Book slot #1
curl -X POST $BASE/api/slots/1/book \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-key-001" \
  -d '{"guestName":"Mukesh Mali","guestEmail":"mukesh@example.com"}'

# 4. Retry the same request — same booking returned, no duplicate!
curl -X POST $BASE/api/slots/1/book \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-key-001" \
  -d '{"guestName":"Mukesh Mali","guestEmail":"mukesh@example.com"}'

# 5. Try to steal the slot — 409 Conflict
curl -X POST $BASE/api/slots/1/book \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: different-key-999" \
  -d '{"guestName":"Hacker","guestEmail":"hack@evil.com"}'

# 6. Watch the outbox worker process the notification (check every ~5s)
curl $BASE/api/admin/outbox-events

# 7. Cancel the booking — slot goes back to OPEN, cache evicted
curl -X DELETE $BASE/api/bookings/1

# 8. Slot 1 is open again
curl $BASE/api/hosts/1/slots
```

---

## Design Decisions

### Why Redisson for distributed locking, not `@Lock` on the DB query?

Database-level pessimistic locks (`SELECT FOR UPDATE`) are simpler but tie locking to the DB connection pool. Under high concurrency, this can exhaust connections and cascade into failures. Redisson's `RLock` uses Redis, which is orders of magnitude faster for this use case and scales independently of the DB.

### Why manual `CacheManager.evict()` instead of `@CacheEvict`?

Spring's `@CacheEvict` is powered by AOP proxies — the proxy wraps the bean. When `bookSlot()` calls `evictSlotsCache()` on `this` within the same class, the call goes directly to the method, **bypassing the proxy**. The `@CacheEvict` annotation is silently ignored. Using `CacheManager.evict(hostId)` directly is more explicit and guaranteed to work.

### Why a polling Outbox Worker instead of Kafka/RabbitMQ?

Message brokers (Kafka, RabbitMQ) are excellent but add significant infrastructure complexity. For a hackathon system, the **Outbox + Polling** pattern achieves the same reliability guarantee (at-least-once delivery, no lost notifications) with only PostgreSQL and a `@Scheduled` bean. It is also significantly easier to reason about, debug, and operate.

### Why `@Enumerated(EnumType.STRING)` for all status fields?

`STRING` stores `"OPEN"`, `"BOOKED"` etc. as readable text in the DB. The alternative `ORDINAL` stores `0`, `1`, `2` — if the enum order ever changes, all existing data silently breaks. STRING is slightly less space-efficient but is far safer for long-lived data.

### Why `AvailabilitySlot implements Serializable`?

Spring's Redis cache serializes objects to store them. Without `Serializable`, caching `List<AvailabilitySlot>` would fail at runtime with a `SerializationException`. The entity implements `Serializable` as a minimal requirement.

### Why Bucket4j (in-memory) instead of Redis-backed rate limiting?

For this demo, in-memory rate limiting with `ConcurrentHashMap` per IP is sufficient and has zero added latency. A production system with multiple server instances would need Redis-backed rate limiting (Bucket4j supports this via `ProxyManager`) to share state across nodes.

---

## Tech Stack

| Layer | Technology | Version | Role |
|-------|-----------|---------|------|
| Language | Java | 17 | Core language |
| Framework | Spring Boot | 3.3.2 | Web, DI, Scheduling, Validation |
| ORM | Spring Data JPA / Hibernate | 6.x | Database access |
| Database | PostgreSQL | 16 | Persistent storage |
| Migrations | Flyway | 10.x | Schema version control |
| Cache | Spring Cache + Redis | 7 | Slot list caching |
| Distributed Lock | Redisson | 3.35.0 | Redis-based distributed lock |
| Rate Limiting | Bucket4j | 8.10.1 | Token bucket per IP |
| API Docs | Springdoc OpenAPI | 2.6.0 | Swagger UI auto-generation |
| Serialization | Jackson | 2.x | JSON (with JavaTimeModule) |
| Containerization | Docker Compose | — | Local dev environment |

---

## Hackathon Context

**Event:** Google Hackathon 2026  
**Theme:** SaaS Tools & Business Platforms  
**Category:** Backend systems / API design

This project demonstrates production-level backend engineering judgment:
- Not just "make it work" but "make it correct under adversity"
- Each of the 7 patterns solves a specific failure mode that real systems encounter
- The code is structured to be readable by a judge in under 5 minutes per file

> Built by **Mukesh Mali**
