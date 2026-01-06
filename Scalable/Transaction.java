package com.lasya.txengine.model;

import java.time.Instant;
import java.util.Objects;

public final class Transaction implements Comparable<Transaction> {
    private final String id;
    private final String accountId; // ordering scope (guarantee per account)
    private final long sequence;    // ordering within account
    private final long amountCents;

    private int attempt;
    private Instant nextEligibleAt;

    public Transaction(String id, String accountId, long sequence, long amountCents) {
        this.id = Objects.requireNonNull(id);
        this.accountId = Objects.requireNonNull(accountId);
        this.sequence = sequence;
        this.amountCents = amountCents;
        this.attempt = 0;
        this.nextEligibleAt = Instant.EPOCH;
    }

    public String id() { return id; }
    public String accountId() { return accountId; }
    public long sequence() { return sequence; }
    public long amountCents() { return amountCents; }

    public int attempt() { return attempt; }
    public void incrementAttempt() { this.attempt++; }

    public Instant nextEligibleAt() { return nextEligibleAt; }
    public void setNextEligibleAt(Instant t) { this.nextEligibleAt = t; }

    @Override
    public int compareTo(Transaction other) {
        // primary: earliest eligible time
        int cmp = this.nextEligibleAt.compareTo(other.nextEligibleAt);
        if (cmp != 0) return cmp;

        // secondary: account ordering
        cmp = this.accountId.compareTo(other.accountId);
        if (cmp != 0) return cmp;

        // tertiary: sequence ordering
        cmp = Long.compare(this.sequence, other.sequence);
        if (cmp != 0) return cmp;

        // stable tie-breaker
        return this.id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return "Tx{id=%s, acct=%s, seq=%d, amt=%d, attempt=%d, eligible=%s}"
                .formatted(id, accountId, sequence, amountCents, attempt, nextEligibleAt);
    }
}
