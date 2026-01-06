package com.lasya.txengine.model;

public record TxResult(boolean success, boolean retryable, String message) {
    public static TxResult ok(String msg) { return new TxResult(true, false, msg); }
    public static TxResult retryableFail(String msg) { return new TxResult(false, true, msg); }
    public static TxResult finalFail(String msg) { return new TxResult(false, false, msg); }
}
