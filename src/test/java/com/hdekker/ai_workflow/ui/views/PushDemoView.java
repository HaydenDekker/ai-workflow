package com.hdekker.ai_workflow.ui.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Minimal mock view for prototyping Vaadin push behavior in browserless tests.
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>A counter displayed in a {@link Div} component</li>
 *   <li>{@code updateWithAccess()} — updates counter via {@link com.vaadin.flow.component.UI#access(Runnable)},
 *       which queues the change for push to the client</li>
 *   <li>{@code updateDirectly()} — updates counter directly on the UI thread,
 *       bypassing the push queue entirely</li>
 * </ul>
 * 
 * <p>In browserless tests:</p>
 * <ul>
 *   <li>Changes from {@code updateWithAccess()} become visible after calling
 *       {@code roundTrip()} to flush the state tree</li>
 *   <li>Changes from {@code updateDirectly()} are never pushed to the client,
 *       even after {@code roundTrip()}, because they bypass the state tree sync</li>
 * </ul>
 * 
 * @see com.vaadin.browserless.SpringBrowserlessTest
 */
@Route("push-demo")
@PageTitle("Push Demo")
public class PushDemoView extends VerticalLayout {

    final Div counterDisplay;  // package-private for test access
    int counter = 0;  // package-private for test access

    public PushDemoView() {
        setPadding(true);
        setSpacing(true);

        H2 header = new H2("Push Demo View");
        header.addClassName("page-title");

        counterDisplay = new Div(String.valueOf(counter));
        counterDisplay.addClassName("counter-display");

        Div instructions = new Div();
        instructions.setText("Call updateWithAccess() for push-queued updates,\nor updateDirectly() for synchronous updates.");
        instructions.addClassName("instructions");

        add(header, new Hr(), counterDisplay, instructions);
    }

    /**
     * Returns the current counter value.
     */
    public int getCounter() {
        return counter;
    }

    /**
     * Returns the text content of the counter display Div.
     */
    public String getCounterText() {
        return counterDisplay.getText();
    }

    /**
     * Updates the counter via UI.access().
     * 
     * <p>This simulates an async/background thread updating the UI.
     * The change is queued in {@code VaadinSession.pendingAccessQueue}.</p>
     * 
     * <p>In browserless tests, {@link com.vaadin.browserless.internal.MockVaadin#runUIQueue()}
     * simulates {@code session.unlock()}, which automatically triggers
     * {@code ui.push()} for AUTOMATIC push mode.</p>
     * 
     * <p>{@code UI.push()} is called automatically by {@code VaadinSession.unlock()}
     * in AUTOMATIC push mode — no explicit call needed in normal code.</p>
     */
    public void updateWithAccess() {
        getUI().ifPresent(ui -> ui.access(() -> {
            counter++;
            counterDisplay.setText(String.valueOf(counter));
        }));
    }

    /**
     * Updates the counter directly (same thread).
     * 
     * <p>This simulates a synchronous update that does NOT go through
     * the push mechanism. The value changes on the server but is NOT
     * pushed to the client in browserless tests.</p>
     * 
     * <p>After {@code roundTrip()}, the client-side view will NOT
     * reflect this update — demonstrating the difference between
     * push-queued and direct updates.</p>
     */
    public void updateDirectly() {
        counter++;
        counterDisplay.setText(String.valueOf(counter));
    }
}
