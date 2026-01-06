package com.lasya.txengine.store;

import com.lasya.txengine.model.TxState;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class StateStore {
    public record TxMeta(TxState state, int attempts, Instant updatedAt, String message) {}

    private final ConcurrentHashMap<String, TxMeta> store = new ConcurrentHashMap<>();

    public void upsert(String txId, TxState state, int attempts, String message) {
        store.put(txId, new TxMeta(state, attempts, Instant.now(), message));
    }

    public Optional<TxMeta> get(String txId) {
        return Optional.ofNullable(store.get(txId));
    }

    public int size() { return store.size(); }
}
