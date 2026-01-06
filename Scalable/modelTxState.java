package com.lasya.txengine.model;

public enum TxState {
    RECEIVED,
    PROCESSING,
    SUCCEEDED,
    FAILED_RETRYABLE,
    FAILED_FINAL
}
