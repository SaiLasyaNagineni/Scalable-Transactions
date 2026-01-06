package com.lasya.txengine;

import com.lasya.txengine.engine.RetryPolicy;
import com.lasya.txengine.engine.TransactionEngine;
import com.lasya.txengine.model.Transaction;
import com.lasya.txengine.model.TxResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class StressTest {

    @Test
    void processes_10000_transactions_under_time_budget() throws Exception {
        int total = 10_000;
        CountDownLatch latch = new CountDownLatch(total);

        RetryPolicy policy = new RetryPolicy(0, Duration.ofMillis(1));

        long start = System.currentTimeMillis();
        try (TransactionEngine engine = new TransactionEngine(
                8,
                policy,
                tx -> {
                    latch.countDown();
                    return TxResult.ok("OK");
                })) {

            engine.start();
            for (int i = 0; i < total; i++) {
                engine.submit(new Transaction("tx-" + i, "acct-" + (i % 200), i, 100));
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Processed " + total + " in " + elapsed + " ms");
    }
}
