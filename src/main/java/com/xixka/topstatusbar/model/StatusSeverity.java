package com.xixka.topstatusbar.model;

/**
 * Presentation severity of a status item. Only non-normal states are
 * visually highlighted (New UI convention: color is reserved for anomalies).
 */
public enum StatusSeverity {
    NORMAL,
    INFO,
    WARNING,
    ERROR
}
