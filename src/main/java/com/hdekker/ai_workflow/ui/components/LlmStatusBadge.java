package com.hdekker.ai_workflow.ui.components;

import com.hdekker.ai_workflow.rest.dto.AdapterStatus;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * Compact status badge displaying "llms" with a color indicator.
 *
 * Minimal component for inline/status-bar LLM interface state display.
 *
 * Usage:
 * <pre>
 *     LlmStatusBadge badge = new LlmStatusBadge(AdapterStatus.UP);
 *     // or with auto-refresh from LLMStatusService:
 *     LlmStatusBadge badge = new LlmStatusBadge(llmStatusService);
 * </pre>
 */
public class LlmStatusBadge extends HorizontalLayout {

    private Span label;
    private Span statusDot;
    private AdapterStatus currentStatus;

    /**
     * Creates a badge with the given status.
     *
     * @param status the current LLM interface state
     */
    public LlmStatusBadge(AdapterStatus status) {
        this.currentStatus = status;
        initLayout();
        applyStatusStyles(status);
    }

    /**
     * Creates a badge and initializes with UNKNOWN status.
     * Call {@link #updateStatus(AdapterStatus)} to set the actual state.
     */
    public LlmStatusBadge() {
        this(AdapterStatus.UNKNOWN);
    }

    private void initLayout() {
        setPadding(false);
        setSpacing(true);
        setAlignItems(Alignment.BASELINE);

        // "llms" label
        label = new Span("llms");
        label.addClassName("llm-status-badge-label");
        label.getStyle().set("font-weight", "600");
        label.getStyle().set("font-size", "var(--lumo-font-size-m)");

        // Colored status dot
        statusDot = new Span();
        statusDot.addClassName("llm-status-badge-dot");
        statusDot.getStyle().set("width", "10px");
        statusDot.getStyle().set("height", "10px");
        statusDot.getStyle().set("border-radius", "50%");
        statusDot.getStyle().set("display", "inline-block");

        add(label, statusDot);
    }

    /**
     * Update the badge to reflect a new status.
     *
     * @param status the new LLM interface state
     */
    public void updateStatus(AdapterStatus status) {
        this.currentStatus = status;
        applyStatusStyles(status);
    }

    /**
     * Returns the current status.
     *
     * @return current AdapterStatus
     */
    public AdapterStatus getStatus() {
        return currentStatus;
    }

    private void applyStatusStyles(AdapterStatus status) {
        switch (status) {
            case UP:
                statusDot.getStyle().set("background-color", "var(--lumo-success-color)");
                break;
            case WARN:
                statusDot.getStyle().set("background-color", "var(--lumo-warning-color)");
                break;
            case DOWN:
                statusDot.getStyle().set("background-color", "var(--lumo-error-color)");
                break;
            case CONNECTING:
                statusDot.getStyle().set("background-color", "var(--lumo-primary-color)");
                break;
            default:
                statusDot.getStyle().set("background-color", "var(--lumo-contrast-50pct)");
                break;
        }
    }
}
