package com.hdekker.ai_workflow.ui.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.TreeOnFailureExtension;
import com.vaadin.browserless.internal.MockVaadin;

/**
 * Browserless prototype tests demonstrating the Vaadin push pattern.
 * 
 * <h3>How Vaadin Push Works (from framework source)</h3>
 * <p>When {@code UI.access(command)} is called from a background thread:</p>
 * <ol>
 *   <li>The command is queued in {@code VaadinSession.pendingAccessQueue}</li>
 *   <li>When {@code VaadinSession.unlock()} is called (hold count == 1):</li>
 *   <ul>
 *     <li>{@code runPendingAccessTasks()} executes all queued callbacks</li>
 *     <li>For AUTOMATIC push mode, {@code ui.push()} is called automatically</li>
 *   </ul>
 * </ol>
 * 
 * <p>In browserless tests, {@link MockVaadin#runUIQueue()} simulates the
 * {@code unlock()} → {@code lock()} cycle, which triggers the same
 * {@code runPendingAccessTasks()} + {@code ui.push()} flow.</p>
 * 
 * <p>However, {@code roundTrip()} is still needed to flush the state tree
 * changes to the "client" (the test's simulated browser).</p>
 * 
 * <h3>AgentListView Context</h3>
 * <p>In {@code AgentListView}, the LLM status badge refreshes via:</p>
 * <pre>
 * {@code
 * getUI().ifPresent(ui -> ui.access(() -> updateLlmStatus()));
 * }
 * </pre>
 * <p>In Playwright tests, the push from {@code UI.access()} must reach the browser.
 * The {@code roundTrip()} in browserless tests corresponds to waiting for the
 * server push to complete in Playwright.</p>
 * 
 * @see PushDemoView
 * @see com.hdekker.ai_workflow.ui.views.AgentListView
 */
@ExtendWith(TreeOnFailureExtension.class)
class PushDemoViewTest extends SpringBrowserlessTest {

    private PushDemoView view;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        view = navigate(PushDemoView.class);
        view.counter = 0;
        view.counterDisplay.setText("0");
    }

    /**
     * Test 1: Complete push pattern — value IS updated.
     * 
     * <p>This is the CORRECT pattern for testing async UI updates in browserless tests:</p>
     * <ol>
     *   <li>{@code view.updateWithAccess()} — simulates async method queuing via {@code UI.access()}</li>
     *   <li>{@link MockVaadin#runUIQueue()} — simulates {@code session.unlock()} → processes
     *       {@code UI.access()} callbacks AND calls {@code ui.push()} automatically</li>
     *   <li>{@code roundTrip()} — flushes state tree to client (simulates browser receiving push)</li>
     * </ol>
     * 
     * <p>In Playwright tests, step 2+3 happen automatically when Vaadin push reaches the browser.
     * The test waits for the push to complete before asserting.</p>
     * 
     * <p>Key: {@code runUIQueue()} already calls {@code ui.push()} internally
     * (via {@code session.unlock()} → {@code runPendingAccessTasks()} → {@code ui.push()}).
     * {@code roundTrip()} flushes the state tree to make changes visible to the "client".</p>
     */
    @Test
    void completePushPattern_valueIsUpdated() {
        assertEquals(0, view.getCounter());
        assertEquals("0", view.getCounterText());

        // Simulate async update from background thread (like AgentListView LLM status refresh)
        view.updateWithAccess();

        // Browserless test: simulate session.unlock() which:
        // 1. Runs pending access tasks (executes the UI.access callback)
        // 2. Calls ui.push() for AUTOMATIC push mode (automatic, no explicit call needed)
        MockVaadin.runUIQueue();

        // Browserless test: flush state tree changes to "client"
        // (equivalent to waiting for push to complete in Playwright)
        roundTrip();

        // Client sees the updated value
        assertEquals(1, view.getCounter());
        assertNotEquals("0", view.getCounterText());
    }

    /**
     * Test 2: Missing runUIQueue — callback never processed, value NOT updated.
     * 
     * <p>This demonstrates what happens when {@code UI.access()} callbacks are
     * NOT processed. Without calling {@code MockVaadin.runUIQueue()} (which simulates
     * {@code session.unlock()}), the queued callback is never executed.</p>
     * 
     * <p>In Playwright tests, this manifests as the browser not receiving the push update.
     * The server-side callback was queued but never executed, so the component value
     * remains unchanged.</p>
     * 
     * <p>Note: {@code UI.push()} is called automatically by {@code VaadinSession.unlock()},
     * so there's no need to call it explicitly in AUTOMATIC push mode. The issue is
     * that {@code unlock()} (and thus push) never happens without {@code runUIQueue()}.</p>
     */
    @Test
    void missingRunUIQueue_callbackNeverProcessed() {
        assertEquals(0, view.getCounter());
        assertEquals("0", view.getCounterText());

        // Async update queues callback via UI.access()
        view.updateWithAccess();

        // BUG: Missing runUIQueue() — the callback is NEVER executed
        // In Playwright: the push never reaches the browser, so the component never updates
        // Note: UI.push() is called automatically by session.unlock(), but without
        // runUIQueue() the unlock never happens, so push never happens either

        // The component value was never updated because the callback wasn't processed
        assertEquals(0, view.getCounter(),
                "Server-side counter was never incremented — callback not processed");
        assertEquals("0", view.getCounterText(),
                "Client display NOT updated — missing runUIQueue() = missing session.unlock()");
    }
}
