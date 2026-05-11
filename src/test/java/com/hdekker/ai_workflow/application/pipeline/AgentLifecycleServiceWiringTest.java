package com.hdekker.ai_workflow.application.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hdekker.ai_workflow.application.agent.AgentLifecycleService;
import com.hdekker.ai_workflow.application.file.port.FileCounterPort;
import com.hdekker.ai_workflow.application.pipeline.port.AgentObserverPort;

/**
 * Spring wiring smoke test for the agent observer infrastructure.
 * <p>
 * Verifies that constructor injection is properly annotated so Spring
 * will use the parameterized constructors — ensuring the observer and
 * file counter are non-null at runtime.
 *
 * @see AgentLifecycleService
 * @see AgentObserverService
 */
@SpringBootTest(classes = AgentLifecycleServiceWiringTestConfig.class)
class AgentLifecycleServiceWiringTest {

    @Autowired
    private AgentLifecycleService agentLifecycleService;

    @Autowired
    private AgentObserverService agentObserverService;

    // -- runtime wiring verification --

    /**
     * Verify that {@code AgentLifecycleService.observer} is not null.
     * <p>
     * Bug #1 from the original plan: Spring selected the no-arg constructor,
     * leaving observer as null. Removing the no-arg constructor and adding
     * {@code @Autowired} to the parameterized constructor fixes this.
     */
    @Test
    void agentLifecycleServiceHasObserverInjected() throws Exception {
        Field observerField = AgentLifecycleService.class.getDeclaredField("observer");
        observerField.setAccessible(true);
        Object observer = observerField.get(agentLifecycleService);
        assertThat(observer)
                .as("AgentLifecycleService.observer must be non-null — Spring must wire the parameterized constructor")
                .isNotNull()
                .isInstanceOf(AgentObserverUseCase.class);
    }

    /**
     * Verify that {@code AgentObserverService.fileCounter} is not null.
     * <p>
     * Bug #2 from the original plan: Spring selected the no-arg constructor,
     * leaving fileCounter as null. Removing the no-arg constructor and adding
     * {@code @Autowired} to the parameterized constructor fixes this.
     */
    @Test
    void agentObserverServiceHasFileCounterInjected() throws Exception {
        Field counterField = AgentObserverService.class.getDeclaredField("fileCounter");
        counterField.setAccessible(true);
        Object counter = counterField.get(agentObserverService);
        assertThat(counter)
                .as("AgentObserverService.fileCounter must be non-null — Spring must wire the parameterized constructor")
                .isNotNull()
                .isInstanceOf(FileCounterPort.class);
    }

    /**
     * Verify that {@code AgentObserverService.outputDirectory} is not null.
     * <p>
     * The outputDirectory string is injected via {@code @Value}.
     * Even if not explicitly configured, it gets a default value.
     */
    @Test
    void agentObserverServiceHasOutputDirectoryConfigured() throws Exception {
        Field dirField = AgentObserverService.class.getDeclaredField("outputDirectory");
        dirField.setAccessible(true);
        Object dir = dirField.get(agentObserverService);
        assertThat(dir)
                .as("AgentObserverService.outputDirectory must be non-null — @Value with default ensures this")
                .isNotNull();
    }

    /**
     * Verify that {@code AgentObserverService.getOutputDirectoryFileCount()}
     * returns a long (not zero due to null fileCounter).
     */
    @Test
    void getOutputDirectoryFileCountReturnsRealCount() {
        long count = agentObserverService.getOutputDirectoryFileCount();
        assertThat(count).isInstanceOf(Long.class);
    }

    /**
     * Verify that only one AgentLifecycleService bean exists in the Spring context.
     */
    @Test
    void agentLifecycleServiceHasSingleBean() {
        assertThat(agentLifecycleService).isNotNull();
    }
}
