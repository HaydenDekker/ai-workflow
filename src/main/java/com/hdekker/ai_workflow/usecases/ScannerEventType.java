package com.hdekker.ai_workflow.usecases;

/**
 * Types of file system events the scanner can observe.
 * <p>
 * {@code CREATION} and {@code MODIFICATION} increment the discovered counter.
 * {@code DELETION} and {@code UNCHANGED} do not.
 * The file count is always computed on-demand by walking the watched directory.
 */
public enum ScannerEventType {
    CREATION,
    MODIFICATION,
    DELETION,
    UNCHANGED
}
