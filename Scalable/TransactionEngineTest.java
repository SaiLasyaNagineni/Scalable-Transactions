package com.lasya.txengine;

import com.lasya.txengine.engine.RetryPolicy;
import com.lasya.txengine.engine.TransactionEngine;
import com.lasya.txengine.model.Transaction;
import com.lasya.txengine.model.TxResult;
import com.lasya.txengine.model.TxState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionEngineTest {

    @Test
    void retries_then_success() throws Exception {
        RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(10));
        AtomicInteger calls = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        try (TransactionEngine engine = new TransactionEngine(
                2,
                policy,
                tx -> {
                    int n = calls.incrementAndGet();
                    if (n < 3) return TxResult.retryableFail("Transient");
                    latch.countDown();
                    return TxResult.ok("OK");
                })) {

            engine.start();
            engine.submit(new Transaction("tx-1", "acct-1", 1, 100));

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            var meta = engine.stateStore().get("tx-1").orElseThrow();
            assertEquals(TxState.SUCCEEDED, meta.state());
        }
    }

    @Test
    void per_account_ordering_is_preserved() throws Exception {
        RetryPolicy policy = new RetryPolicy(0, Duration.ofMillis(1));

        ConcurrentHashMap<Long, Long> completionOrder = new ConcurrentHashMap<>();
        AtomicInteger done = new AtomicInteger(0);

        try (TransactionEngine engine = new TransactionEngine(
                4,
                policy,
                tx -> {
                    // record completion timestamp by sequence
                    completionOrder.put(tx.sequence(), System.nanoTime());
                    done.incrementAndGet();
                    return TxResult.ok("OK");
                })) {

            engine.start();
            String acct = "acct-ORDER";

            for (int i = 1; i <= 50; i++) {
                engine.submit(new Transaction("tx-" + i, acct, i, 100));
            }

            long deadline = System.currentTimeMillis() + 2000;
            while (done.get() < 50 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }

            assertEquals(50, done.get());

            // verify increasing timestamps with increasing sequence
            for (int i = 2; i <= 50; i++) {
                assertTrue(completionOrder.get((long)i) >= completionOrder.get((long)i - 1),
                        "Sequence " + i + " completed before " + (i - 1));
            }
        }
    }
}
