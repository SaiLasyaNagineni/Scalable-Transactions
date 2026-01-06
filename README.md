# Scalable-Transactions
# Scalable Transaction Processing Engine (Java)

High-throughput, concurrent transaction engine that supports:
- Parallel processing with thread safety
- Per-account ordering guarantees
- Retry with exponential backoff
- Unit tests + stress tests

## Architecture
Producer -> PriorityBlockingQueue -> Worker Pool -> Processor -> StateStore

## Guarantees
- Per-account ordering via account-level locking
- Retry for transient failures (configurable max retries + backoff)
- Thread-safe state tracking (ConcurrentHashMap)

## Run
mvn test
mvn -q exec:java -Dexec.mainClass="com.lasya.txengine.App"

## Complexity
- Enqueue/Dequeue: O(log N) due to priority queue ordering
- State updates: O(1) average

## Next Improvements
- Persistent state (DB) + idempotency keys
- Dead-letter queue
- Metrics (latency/throughput) + dashboards
- Kafka integration for ingestion
