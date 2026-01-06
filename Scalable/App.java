package com.lasya.txengine;

import com.lasya.txengine.engine.RetryPolicy;
import com.lasya.txengine.engine.TransactionEngine;
import com.lasya.txengine.model.Transaction;
import com.lasya.txengine.model.TxResult;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class App {
    public static void main(String[] args) throws Exception {
        Random rnd = new Random();
        AtomicInteger counter = new AtomicInteger(0);

        RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(50));

        try (TransactionEngine engine = new TransactionEngine(
                4,
                policy,
                tx -> {
                    // simulate 15% transient fail, 3% final fail
                    int x = rnd.nextInt(100);
                    if (x < 3) return TxResult.finalFail("Permanent failure");
                    if (x < 18) return TxResult.retryableFail("Transient failure");
                    return TxResult.ok("Processed");
                })) {

            engine.start();

            for (int i = 0; i < 100; i++) {
                String account = "acct-" + (i % 10);           // per-account ordering
                long seq = counter.incrementAndGet();
                engine.submit(new Transaction("tx-" + i, account, seq, 1000 + i));
            }

            Thread.sleep(2000);
            System.out.println("Done. Stored tx states = " + engine.stateStore().size());
        }
    }
}
