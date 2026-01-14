package com.hdekker.ai_workflow.pipeline.llmadapter;

import com.hdekker.ai_workflow.llm.Prompter;
import com.hdekker.ai_workflow.pipeline.LLMAdapter;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.prompt.PromptResponse;
import reactor.core.publisher.Flux;

public class MapAgentLLMAdapter implements LLMAdapter {

    private final Prompter prompter;
    private final AgentDefinition agentDefinition;

    public MapAgentLLMAdapter(Prompter prompter, AgentDefinition agentDefinition) {
        this.prompter = prompter;
        this.agentDefinition = agentDefinition;
    }

    @Override
    public Flux<PromptResponse> call(Flux<com.hdekker.ai_workflow.prompt.PromptRequest> request) {
        return request.flatMap(fpe ->
                prompter.call(agentDefinition.body() + "\n\r" + "`" + "``code" + fpe.file() + "\n\r" + "`" + "``" + "\n\r" + agentDefinition.outputStructure())
                        .reduce((a, b) -> a + b)
                        .map(s -> new PromptResponse(agentDefinition, fpe.fileURL(), fpe.file(), s))
        );
    }
}
