# Idempotent Background Transaction Processing Service

A Java 17 / Spring Boot implementation for the FDE L2 practical assessment.

## Features
- REST-based transaction ingestion with Bean Validation.
- H2 file-backed persistence for transaction state and account balances.
- Idempotency using unique `transactionId` and `idempotencyKey` checks.
- Sequence-based ordering per account: sequence gaps remain `RECEIVED` until earlier events arrive.
- Scheduled background processing plus a manual processing endpoint.
- Simulated transient failure, retry count, and retry-safe balance updates.
- Status lifecycle: `RECEIVED`, `PROCESSING`, `PROCESSED`, `DUPLICATE` (returned for replay), `FAILED`, `RETRY_PENDING`.
- Processing summary, transaction listing, and account balance endpoint.
- Spring Boot tests for duplicate, ordering, and retry scenarios.

## Run
Requires JDK 17+ and Maven 3.8+.

```bash
mvn clean test
mvn spring-boot:run
```

Application: `http://localhost:8080`  
H2 console: `http://localhost:8080/h2-console`  
JDBC URL: `jdbc:h2:file:./data/transactions`, user `sa`, blank password.

## Example requests

Submit sequence 2 first (it remains pending until sequence 1 is received):
```bash
curl -X POST http://localhost:8080/api/transactions -H 'Content-Type: application/json' -d '{
  "transactionId":"TXN-2002","idempotencyKey":"REQ-2","accountId":"ACC-201",
  "transactionType":"DEBIT","amount":40,"currency":"USD","sequenceNumber":2
}'
```

Then submit sequence 1:
```bash
curl -X POST http://localhost:8080/api/transactions -H 'Content-Type: application/json' -d '{
  "transactionId":"TXN-2001","idempotencyKey":"REQ-1","accountId":"ACC-201",
  "transactionType":"CREDIT","amount":100,"currency":"USD","sequenceNumber":1
}'
```

Trigger processing immediately (otherwise the scheduler runs every 2 seconds):
```bash
curl -X POST http://localhost:8080/api/transactions/process
```

Inspect:
```bash
curl http://localhost:8080/api/transactions
curl http://localhost:8080/api/accounts/ACC-201/balance
curl http://localhost:8080/api/processing-summary
```

To exercise a transient retry, set `"simulateTransientFailure": true`. The first processing attempt moves the record to `RETRY_PENDING`; the next attempt succeeds.

## Design decisions and trade-offs
1. **Idempotency:** business transaction ID and idempotency key are unique in the database. A replay returns a `DUPLICATE` response and does not create another row or change the balance.
2. **Ordering:** sequence numbers are scoped to an account and start at 1. A future sequence stays `RECEIVED`; once the missing event is submitted, the processor can process the contiguous sequence.
3. **Atomicity:** transaction state and account balance changes are made in one Spring/JPA transaction. A database rollback prevents a partial balance update.
4. **Retries:** the sample simulates a transient failure once. Retry metadata is persisted; processing the retry does not apply the balance twice.
5. **Observability:** structured application logs include transaction identifiers and sequence information without logging customer payloads. `/api/processing-summary` provides counts by status.
6. **Input isolation:** Bean Validation rejects malformed requests at the API boundary. The database remains the source of truth for state.

## Production hardening
- Add database-level unique constraints for all idempotency dimensions and use atomic insert/upsert semantics to handle concurrent duplicate submissions.
- Lock account rows (or use optimistic versioning) so concurrent workers cannot process the same account sequence simultaneously.
- Add a durable queue adapter, consumer offset strategy, dead-letter queue, exponential backoff, and poison-message handling.
- Persist an immutable ledger entry with a unique transaction reference rather than relying only on a mutable balance.
- Add authentication/authorization, TLS, secrets management, rate limits, and audit controls.
- Add Micrometer metrics, tracing, dashboards, alerts, and operational replay tooling.
- Define sequence initialization and gap timeout/reconciliation policy with the business owner.

## Important assessment note
This is a practical, lightweight reference implementation. It deliberately documents concurrency, queue, and production hardening work that would be required before deploying it for real financial transactions. Explain these trade-offs during the review rather than presenting the sample as a fully distributed ledger.
