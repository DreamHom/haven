# Session 8 — Notifications + Kafka Outbox

## The problem the outbox solves

When a user submits an offer, two things need to happen: insert the offer row into the DB, and publish an `OFFER_SUBMITTED` Kafka event. The naive code does both directly inside `@Transactional`:

```java
offerRepository.save(offer);
kafkaTemplate.send(event);
```

But this is wrong. Two failure modes:

- DB commits, Kafka send fails → offer exists but no one knows. Downstream silently misses the event.
- Kafka send succeeds, DB rollback → an event went out about an offer that doesn't exist. Downstream reacts to a phantom.

`@Transactional` alone can't fix this because Kafka isn't a transactional resource.

## The fix: write the event to a DB table

Instead of publishing directly, write the event as a row in `outbox_events` in the same transaction as the offer:

```java
offerRepository.save(offer);
outboxRepository.save(eventAsOutboxRow);   // also a DB write, same txn
```

Both succeed together or neither does. No partial failure possible.

Then a separate background process — the **relay** — reads new rows from `outbox_events` and ships them to Kafka. The application never has to wait for or know about Kafka availability.

## The relay (`OutboxRelay`)

A Spring component running inside Haven. Its only job: drain the outbox table to Kafka. Like a post office worker — services drop letters in the outbox tray, the worker picks them up and takes them to the sorting facility (Kafka). If the facility is closed, the worker comes back later.

## Two ways the relay knows there's a new row

**The nudge** — right after a service writes an outbox row, it fires an in-process Spring event (`OutboxRowReadyEvent`). The relay wakes up immediately and drains. Low latency.

**The scheduled poll** — every few seconds, the relay sweeps the table anyway. Safety net for the case where the JVM crashed between writing the row and firing the nudge.

Fast path + safety net running together. You never have to choose.

## Idempotency via event_id

The system can deliver the same event twice — Kafka guarantees "at least once", and the relay could double-publish if it crashes mid-send. So every outbox event carries a unique `event_id` (UUID, generated at write time).

Two layers of dedup, both real and verified in code:

**DB constraint (V6 migration):**
```sql
notifications.event_id UUID UNIQUE
outbox_events.event_id UUID NOT NULL UNIQUE
```

**Service check (NotificationService.recordAsync line 60):**
```java
if (notificationRepository.existsByEventId(eventId)) {
    log.info("Skipping duplicate notification eventId={} kind={}", eventId, kind);
    return;
}
```

Belt and suspenders. Service check skips duplicates silently; DB unique constraint catches anything that races past it.

Net effect: at-least-once at the wire, effectively-once at the DB.

## Per-listing partitioning

Kafka topics have multiple partitions. Events with the same key always land on the same partition (Kafka guarantee), and Kafka processes messages within a partition in strict order with a single consumer thread.

We set the partition key to the listing ID:

```java
.partitionKey(String.valueOf(listing.id()))   // OfferService line 105
.partitionKey(String.valueOf(listing.id()))   // InspectionService line 129
```

Everything on listing #17 lands on the same partition → processed in order. Events on different listings can land on different partitions → processed in parallel by multiple consumer threads.

Trade-off: order within a listing, parallelism across listings.

## The two listeners

`InspectionRequestedListener` reads `inspection.requested.v1`. `OfferSubmittedListener` reads `offer.submitted.v1`. Both follow the same pattern:

```java
notificationApi.recordAsync(eventId, kind, ownerId, event);
ack.acknowledge();   // only AFTER the DB write returned
```

Manual ack is the key discipline. If the JVM dies between consuming and writing, no ack happens, Kafka redelivers, the idempotency check catches the duplicate.

**Known gap:** both listeners only notify the owner, not the assigned agent. This is Item 7 Gap A on the task list. Fix is one extra `recordAsync` call per listener with an `activeAgentUserId(listingId)` lookup.

## Sync vs async notification paths

Two ways a notification can be born:

**Sync** — `NotificationService.recordSync(kind, recipientId, payload)` — called directly inside a business transaction. No Kafka, no event_id, no listener. Used when the originating service knows exactly who to notify and wants the notification to commit with the same transaction. Example: applicant books → applicant gets a sync `INSPECTION_BOOKED` ack.

**Async** — `NotificationService.recordAsync(eventId, kind, recipientId, payload)` — called from inside a Kafka listener. Used when the event might have multiple consumers and the originating service shouldn't know who they all are.

Rule of thumb: one known recipient in-transaction → sync; multi-consumer fan-out → async via outbox.

## SSE for live push to the browser

`GET /api/notifications/stream` opens a Server-Sent Events connection. The browser keeps it open; the server pushes a small JSON frame whenever a new notification commits. No polling needed.

`NotificationSseEmitters` is a per-user in-memory registry — userId → list of emitters (a user can have multiple tabs/devices connected). When a notification is recorded, the service calls `push(userId, ...)` which finds the user's emitters and sends each one the event. Browser receives → Vista pops the toast / updates the bell badge.

**Honest limitation:** single-instance only. If Haven runs on two app instances behind a load balancer, an emitter on Instance A misses events that fire on Instance B. The Javadoc flags this: *"a Redis pub-sub layer would fan events across nodes — deferred until we actually scale out."*

For demo + current scale, single-instance SSE is fine.
