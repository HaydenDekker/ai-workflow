package com.hdekker.ai_workflow.test.pipeline.mock;

/**
 * Provider for mock LLM responses tailored to each adapter type.
 * Generates realistic responses that test the specific behavior of each adapter.
 */
public class MockResponseProvider {

    /**
     * Mock response for MapAgentLLMAdapter testing.
     * Single structured response for 1:1 transformation.
     */
    public static String getMapAgentResponse() {
        return """
            ```json
            {
                "functionName": "processData",
                "functionDetail": "Processes input data and returns transformed result",
                "functionCategory": "Data Processing"
            }
            ```
            """;
    }

    /**
     * Mock response for SplitterLLMAdapter testing.
     * Contains multiple sections split by --- ItemKey --- tokens.
     */
    public static String getSplitterResponse() {
        return """
            Analysis of SOLID principle violations in the provided code:
            
            --- Single Responsibility Principle ---
            ```json
            {
                "principle": "Single Responsibility Principle",
                "violation": "Class handles multiple responsibilities",
                "severity": "Medium",
                "recommendation": "Separate concerns into different classes"
            }
            ```
            
            --- Open/Closed Principle ---
            ```json
            {
                "principle": "Open/Closed Principle", 
                "violation": "Class requires modification for new features",
                "severity": "High",
                "recommendation": "Use strategy pattern or interfaces"
            }
            ```
            
            --- Dependency Inversion Principle ---
            ```json
            {
                "principle": "Dependency Inversion Principle",
                "violation": "Direct dependency on concrete classes",
                "severity": "Medium", 
                "recommendation": "Depend on abstractions instead"
            }
            ```
            """;
    }

    /**
     * Mock response for ReducerLLMAdapter testing (first input).
     * Initial response when no previous state exists.
     */
    public static String getReducerInitialResponse() {
        return """
            Function Analysis Summary:
            
            Currently analyzed functions:
            - processData: Data Processing function that transforms input data
            
            System Overview:
            The system contains data processing capabilities with transformation logic.
            """;
    }

    /**
     * Mock response for ReducerLLMAdapter testing (subsequent inputs).
     * Shows accumulation of previous responses.
     */
    public static String getReducerAccumulatedResponse() {
        return """
            Function Analysis Summary:
            
            Currently analyzed functions:
            - processData: Data Processing function that transforms input data
            - validateInput: Validation function that checks input parameters
            - generateReport: Reporting function that creates output reports
            
            System Overview:
            The system contains data processing capabilities with:
            - Input validation mechanisms
            - Data transformation logic  
            - Report generation functionality
            
            Total functions analyzed: 3
            """;
    }
    
    /**
     * Get multiple reducer responses for testing state accumulation.
     * Returns a list with initial and accumulated responses.
     */
    public static String[] getReducerResponses() {
        return new String[] {
            getReducerInitialResponse(),
            getReducerAccumulatedResponse()
        };
    }

    /**
     * Mock response for default Map adapter testing.
     */
    public static String getDefaultMapResponse() {
        return """
            ```json
            {
                "status": "processed",
                "inputType": "java",
                "result": "File processed successfully"
            }
            ```
            """;
    }

    /**
     * Get the expected split keys from the splitter response.
     * Used to verify filename generation in SplitterLLMAdapter.
     */
    public static String[] getSplitterKeys() {
        return new String[]{
            "Single_Responsibility_Principle",
            "Open/Closed_Principle",
            "Dependency_Inversion_Principle"
        };
    }

    /**
     * Get empty response for testing edge cases.
     */
    public static String getEmptyResponse() {
        return "";
    }

    /**
     * Get malformed response for testing error handling.
     */
    public static String getMalformedSplitterResponse() {
        return """
            This response has no proper splits.
            --- Missing Key ---
        Some content here.
        Another section without proper formatting.
        """;
    }
}