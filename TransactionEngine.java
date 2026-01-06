package com.lasya.txengine.engine;

import com.lasya.txengine.model.Transaction;
import com.lasya.txengine.model.TxResult;
import com.lasya.txengine.model.TxState;
import com.lasya.txengine.store.StateStore;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

public final class TransactionEngine implements AutoCloseable {

    private final PriorityBlockingQueue<Transaction> queue = new PriorityBlockingQueue<>();
    private final ExecutorService workers;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final RetryPolicy retryPolicy;
    private final StateStore stateStore = new StateStore();

    // ordering locks (per accountId)
    private final ConcurrentHashMap<String, ReentrantLock> accountLocks = new ConcurrentHashMap<>();

    // “processor” simulates payment-like operation (injectable for testing)
    private final Function<Transaction, TxResult> processor;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public TransactionEngine(int workerCount, RetryPolicy retryPolicy, Function<Transaction, TxResult> processor) {
        if (workerCount <= 0) throw new IllegalArgumentException("workerCount must be > 0");
        this.retryPolicy = Objects.requireNonNull(retryPolicy);
        this.processor = Objects.requireNonNull(processor);
        this.workers = Executors.newFixedThreadPool(workerCount);
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        // run workers
        for (int i = 0; i < ((ThreadPoolExecutor)workers).getCorePoolSize(); i++) {
            workers.submit(new Worker());
        }
    }

    public void submit(Transaction tx) {
        stateStore.upsert(tx.id(), TxState.RECEIVED, tx.attempt(), "Received");
        queue.put(tx);
    }

    public StateStore stateStore() { return stateStore; }

    @Override
    public void close() {
        running.set(false);
        workers.shutdownNow();
        scheduler.shutdownNow();
    }

    private ReentrantLock lockForAccount(String accountId) {
        return accountLocks.computeIfAbsent(accountId, k -> new ReentrantLock());
    }

    private final class Worker implements Runnable {
        @Override
        public void run() {
            while (running.get()) {
                try {
                    Transaction tx = queue.take();

                    // If retry delay not reached yet, re-schedule
                    Instant now = Instant.now();
                    if (tx.nextEligibleAt().isAfter(now)) {
                        long delayMs = Math.max(1, tx.nextEligibleAt().toEpochMilli() - now.toEpochMilli());
                        scheduler.schedule(() -> queue.put(tx), delayMs, TimeUnit.MILLISECONDS);
                        continue;
                    }

                    ReentrantLock lock = lockForAccount(tx.accountId());
                    lock.lock();
                    try {
                        stateStore.upsert(tx.id(), TxState.PROCESSING, tx.attempt(), "Processing");
                        TxResult result = processor.apply(tx);

                        if (result.success()) {
                            stateStore.upsert(tx.id(), TxState.SUCCEEDED, tx.attempt(), result.message());
                        } else if (result.retryable() && tx.attempt() < retryPolicy.maxRetries()) {
                            tx.incrementAttempt();
                            tx.setNextEligibleAt(Instant.now().plus(retryPolicy.backoffForAttempt(tx.attempt())));
                            stateStore.upsert(tx.id(), TxState.FAILED_RETRYABLE, tx.attempt(), result.message());
                            queue.put(tx);
                        } else {
                            stateStore.upsert(tx.id(), TxState.FAILED_FINAL, tx.attempt(), result.message());
                        }
                    } finally {
                        lock.unlock();
                    }

                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    // defensive: keep worker alive
                }
            }
        }
    }
}
