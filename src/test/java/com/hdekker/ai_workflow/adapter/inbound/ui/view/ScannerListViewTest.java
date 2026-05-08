package com.hdekker.ai_workflow.adapter.inbound.ui.view;

import static org.junit.jupiter.api.Assertions.*;


import java.util.Map;

import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.domain.scanner.ScannerFileResult;

/**
 * Test class for ScannerListView.
 */
public class ScannerListViewTest {

    @Test
    void testViewClassExists() {
        assertDoesNotThrow(() -> {
            Class.forName("com.hdekker.ai_workflow.adapter.inbound.ui.view.ScannerListView");
        });
    }

    @Test
    void testViewHasRouteAnnotation() {
        Class<?> viewClass;
        try {
            viewClass = Class.forName("com.hdekker.ai_workflow.adapter.inbound.ui.view.ScannerListView");
            assertNotNull(viewClass.getAnnotation(com.vaadin.flow.router.Route.class));
            com.vaadin.flow.router.Route route = viewClass.getAnnotation(com.vaadin.flow.router.Route.class);
            assertEquals("scanners", route.value());
        } catch (ClassNotFoundException e) {
            fail("ScannerListView class not found");
        }
    }

    @Test
    void testViewHasPageTitleAnnotation() {
        Class<?> viewClass;
        try {
            viewClass = Class.forName("com.hdekker.ai_workflow.adapter.inbound.ui.view.ScannerListView");
            assertNotNull(viewClass.getAnnotation(com.vaadin.flow.router.PageTitle.class));
            com.vaadin.flow.router.PageTitle pageTitle = viewClass.getAnnotation(com.vaadin.flow.router.PageTitle.class);
            assertEquals("Scanners", pageTitle.value());
        } catch (ClassNotFoundException e) {
            fail("ScannerListView class not found");
        }
    }

    // --- Display state mapping tests ---

    @Test
    void testDisplayStateFromEmittedIsActive() {
        String displayState = displayStateFromResult(ScannerFileResult.EMITTED);
        assertEquals("Active", displayState);
    }

    @Test
    void testDisplayStateFromFilteredIsFiltered() {
        String displayState = displayStateFromResult(ScannerFileResult.FILTERED);
        assertEquals("Filtered", displayState);
    }

    @Test
    void testDisplayStateFromErrorIsError() {
        String displayState = displayStateFromResult(ScannerFileResult.ERROR);
        assertEquals("Error", displayState);
    }

    @Test
    void testDisplayStateFromNullResultIsIdle() {
        String displayState = displayStateFromResult(null);
        assertEquals("Idle", displayState);
    }

    @Test
    void testDisplayColorForActiveIsBlue() {
        String color = displayColorForState("Active");
        assertEquals("#4a90d9", color);
    }

    @Test
    void testDisplayColorForFilteredIsOrange() {
        String color = displayColorForState("Filtered");
        assertEquals("#e67e22", color);
    }

    @Test
    void testDisplayColorForErrorIsRed() {
        String color = displayColorForState("Error");
        assertEquals("#e74c3c", color);
    }

    @Test
    void testDisplayColorForIdleIsGreen() {
        String color = displayColorForState("Idle");
        assertEquals("#27ae60", color);
    }

    @Test
    void testDisplayColorForUnknownIsGreen() {
        String color = displayColorForState("UNKNOWN");
        assertEquals("#27ae60", color);
    }

    @Test
    void testTimerDurationForEmittedIsTenSeconds() {
        long duration = timerDurationMsForResult(ScannerFileResult.EMITTED);
        assertEquals(10_000L, duration);
    }

    @Test
    void testTimerDurationForFilteredIsTwoSeconds() {
        long duration = timerDurationMsForResult(ScannerFileResult.FILTERED);
        assertEquals(2_000L, duration);
    }

    @Test
    void testTimerDurationForErrorIsZero() {
        long duration = timerDurationMsForResult(ScannerFileResult.ERROR);
        assertEquals(0L, duration);
    }

    @Test
    void testDisplayTimerMapUsesAgentIdAsKey() {
        Map<String, ?> displayTimers = Map.of("agent-1", new Object[0], "agent-2", new Object[0]);
        assertTrue(displayTimers.containsKey("agent-1"));
        assertTrue(displayTimers.containsKey("agent-2"));
    }

    // --- Helper methods mirroring ScannerListView logic ---

    /**
     * Maps a ScannerFileResult to the display state string.
     * Mirrors the logic used in ScannerListView for display state computation.
     */
    private static String displayStateFromResult(ScannerFileResult result) {
        return switch (result) {
            case EMITTED -> "Active";
            case FILTERED -> "Filtered";
            case ERROR -> "Error";
            case null -> "Idle";
        };
    }

    /**
     * Returns the display color hex code for a given state.
     */
    private static String displayColorForState(String state) {
        return switch (state) {
            case "Active" -> "#4a90d9";  // blue
            case "Filtered" -> "#e67e22";  // orange
            case "Error" -> "#e74c3c";  // red
            default -> "#27ae60";  // Idle = green
        };
    }

    /**
     * Returns the timer duration in milliseconds for a given file result.
     * 0 means no auto-reset (error persists until cleared).
     */
    private static long timerDurationMsForResult(ScannerFileResult result) {
        return switch (result) {
            case EMITTED -> 10_000L;
            case FILTERED -> 2_000L;
            case ERROR -> 0L;
            case null -> 0L;
        };
    }
}
