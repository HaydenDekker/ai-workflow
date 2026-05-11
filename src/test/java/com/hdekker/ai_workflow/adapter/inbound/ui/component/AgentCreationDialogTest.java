package com.hdekker.ai_workflow.adapter.inbound.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.function.Consumer;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.adapter.inbound.rest.dto.AgentInfoDTO;
import com.hdekker.ai_workflow.adapter.inbound.ui.service.AgentInfoService;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.agent.AgentType;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

/**
 * Browserless test for {@link AgentCreationDialog}.
 * 
 * <p>Runs entirely on the server side — no browser, no servlet container,
 * no client-server bridge. Tests are typically 100× faster than E2E and
 * fail immediately if the component is misconfigured.</p>
 */
@ViewPackages
class AgentCreationDialogTest extends BrowserlessTest {

    private AgentCreationDialog dialog;
    private AgentInfoService mockService;
    private static final AgentDefinition STUB_DEF = new AgentDefinition(
            ".*", "stub", "stub", AgentType.MAP, "stub", "output/${name}.md", "/tmp/stub");
    private static final AgentInfoDTO STUB_INFO = new AgentInfoDTO(
            "test-id", STUB_DEF, LocalDateTime.now(), true, "TEST");

    @BeforeEach
    void setup() {
        // Create a mock service that returns a successful Mono
        mockService = Mockito.mock(AgentInfoService.class);
        Mockito.when(mockService.createAgent(Mockito.any()))
                .thenReturn(Mono.just(STUB_INFO));

        dialog = new AgentCreationDialog(mockService);

        // Attach dialog to UI and open it so children are visible for querying
        UI.getCurrent().add(dialog);
        dialog.open();
    }

    @Test
    void dialogIsModal() {
        assertThat(dialog.isModal()).isTrue();
    }

    @Test
    void dialogClosesOnEsc() {
        assertThat(dialog.isCloseOnEsc()).isTrue();
    }

    @Test
    void dialogClosesOnOutsideClick() {
        assertThat(dialog.isCloseOnOutsideClick()).isTrue();
    }

    @Test
    void dialogHasExpectedDimensions() {
        assertThat(dialog.getWidth()).isEqualTo("650px");
        assertThat(dialog.getHeight()).isEqualTo("750px");
    }

    @Test
    void dialogContainsFormLayout() {
        FormLayout formLayout = $(FormLayout.class).single();
        assertThat(formLayout).isNotNull();
        assertThat(formLayout.getElement().getClassList().contains("agent-creation-form")).isTrue();
    }

    @Test
    void dialogContainsButtonBar() {
        HorizontalLayout buttonBar = $(HorizontalLayout.class).single();
        assertThat(buttonBar).isNotNull();
        assertThat(buttonBar.getElement().getClassList().contains("dialog-button-bar")).isTrue();
    }

    @Test
    void dialogContainsExpectedFieldCount() {
        // 4 TextFields: Title, Target Directory, File Input Regex, Output Filename Template
        assertThat($(TextField.class).all()).hasSize(4);

        // 2 TextAreas: Body, Output Structure
        assertThat($(TextArea.class).all()).hasSize(2);

        // 1 ComboBox: Agent Type
        assertThat($(ComboBox.class).all()).hasSize(1);
    }

    @Test
    void dialogContainsCancelAndCreateButtons() {
        assertThat($(Button.class).all()).hasSize(2);

        Button cancelButton = $(Button.class).withText("Cancel").single();
        assertThat(cancelButton).isNotNull();
        assertThat(cancelButton.getElement().getClassList().contains("dialog-cancel-btn")).isTrue();

        Button createButton = $(Button.class).withText("Create Agent").single();
        assertThat(createButton).isNotNull();
        assertThat(createButton.getElement().getClassList().contains("dialog-create-btn")).isTrue();
        assertThat(createButton.getElement().getThemeList().contains("primary")).isTrue();
    }

    @Test
    void dialogOpensAndResetsForm() {
        dialog.open();
        assertThat(dialog.isOpened()).isTrue();

        // Open again with null — should reset form
        dialog.open(null);
        assertThat(dialog.isOpened()).isTrue();
    }

    @Test
    void dialogPrepopulatesFormWithExistingAgent() {
        AgentDefinition existing = new AgentDefinition(
                "(?:(.*/)?)(.*)",
                "Test Agent Title",
                "Test body content",
                AgentType.MAP,
                "Test output structure",
                "output/${name}-custom.md",
                "/tmp/test-dir"
        );

        dialog.open(existing);
        assertThat(dialog.isOpened()).isTrue();

        // Verify pre-populated agent type
        @SuppressWarnings("unchecked")
		ComboBox<String> combo = $(ComboBox.class).single();
        assertThat(combo.getValue()).isEqualTo("Map");
    }

    @Test
    void resetFormClearsFields() {
        AgentDefinition existing = new AgentDefinition(
                "(?:(.*/)?)(.*)",
                "Existing Title",
                "Existing body",
                AgentType.REDUCTION,
                "Existing structure",
                "output/${name}-existing.md",
                "/tmp/existing-dir"
        );

        dialog.open(existing);
        dialog.resetForm();

        // After reset, agent type combo should be null
        @SuppressWarnings("unchecked")
		ComboBox<String> combo = $(ComboBox.class).single();
        assertThat(combo.getValue()).isNull();

        // File input regex should default to ".*"
        TextField regexField = $(TextField.class)
                .withCondition(f -> "File Input Regex".equals(f.getLabel()))
                .single();
        assertThat(regexField.getValue()).isEqualTo(".*");

        // Output filename template should default to original
        TextField templateField = $(TextField.class)
                .withCondition(f -> "Output Filename Template".equals(f.getLabel()))
                .single();
        assertThat(templateField.getValue()).isEqualTo("output/${name}.md");
    }

    @Test
    void customConfirmCallbackIsAccepted() {
        java.util.concurrent.atomic.AtomicBoolean invoked = new java.util.concurrent.atomic.AtomicBoolean();

        Consumer<AgentDefinition> customCallback = definition -> invoked.set(true);

        AgentCreationDialog customDialog = new AgentCreationDialog(mockService, customCallback);
        UI.getCurrent().add(customDialog);

        // Dialog is constructed with the callback — verified by no exception
        assertThat(customDialog).isNotNull();
        assertThat(invoked.get()).isFalse(); // Not invoked yet
    }

    @Test
    void allFormFieldsAreRequired() {
        $(TextField.class).all().forEach(field ->
                assertThat(field.isRequired()).as("Field '%s' should be required", field.getLabel()).isTrue());

        $(TextArea.class).all().forEach(area ->
                assertThat(area.isRequired()).as("Field '%s' should be required", area.getLabel()).isTrue());

        @SuppressWarnings("unchecked")
		ComboBox<String> combo = $(ComboBox.class).single();
        assertThat(combo.isRequired()).isTrue();
    }

    @Test
    void titleFieldHasMaxLength100() {
        TextField titleField = $(TextField.class)
                .withCondition(f -> "Title".equals(f.getLabel()))
                .single();
        assertThat(titleField.getMaxLength()).isEqualTo(100);
    }

    @Test
    void fileInputRegexFieldHasDefaultPattern() {
        TextField regexField = $(TextField.class)
                .withCondition(f -> "File Input Regex".equals(f.getLabel()))
                .single();
        assertThat(regexField.getValue()).isEqualTo(".*");
        assertThat(regexField.getMaxLength()).isEqualTo(100);
    }

    @Test
    void outputFilenameTemplateFieldHasDefaultTemplate() {
        TextField templateField = $(TextField.class)
                .withCondition(f -> "Output Filename Template".equals(f.getLabel()))
                .single();
        assertThat(templateField.getValue()).isEqualTo("output/${name}.md");
        assertThat(templateField.getMaxLength()).isEqualTo(100);
    }

    @Test
    void textAreasHaveExpectedHeight() {
        $(TextArea.class).all().forEach(area -> {
            assertThat(area.getHeight()).isEqualTo("120px");
            assertThat(area.getMinHeight()).isEqualTo("60px");
            assertThat(area.getMaxHeight()).isEqualTo("300px");
        });
    }

    @Test
    void cancelAndCreateButtonsHaveCorrectClassNames() {
        Button cancelButton = $(Button.class).withText("Cancel").single();
        assertThat(cancelButton.getElement().getClassList().contains("dialog-cancel-btn")).isTrue();

        Button createButton = $(Button.class).withText("Create Agent").single();
        assertThat(createButton.getElement().getClassList().contains("dialog-create-btn")).isTrue();
        assertThat(createButton.getElement().getThemeList().contains("primary")).isTrue();
    }
}
