package com.learnai.property;

/** State of a paid-service order. */
public enum ServiceStatus {
    /** Payment captured; the service result is included in the order response. */
    PAID,
    /** Order registered, waiting for an external provider (e.g. a real payment gateway). */
    REQUESTED
}
