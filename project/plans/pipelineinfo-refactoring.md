# Refactoring Plan: PipelineInfo to Include AgentDefinition (1-to-1)

## Overview
Refactor `PipelineInfo` to include the full `AgentDefinition` object as a 1-to-1 relationship, eliminating the need for data transformation and providing direct access to complete agent configuration data.

## Current State Analysis

### Current PipelineInfo Structure
```java
public record PipelineInfo(
    String id,
    String title,           // Duplicated from AgentDefinition
    String agentType,       // Duplicated from AgentDefinition  
    LocalDateTime createdAt,
    boolean active,
    String source           // "YAML" or "DYNAMIC"
) {}
```

### Current Issues
1. **Data Duplication**: `title` and `agentType` are duplicated from `AgentDefinition`
2. **Incomplete Data**: Other `AgentDefinition` fields (`fileInputRegex`, `body`, `outputStructure`, `outputFilenameTemplate`) are not accessible via API
3. **Transformation Required**: Grid view needs to extract/transform data to get complete agent info
4. **API Limitation**: REST API doesn't expose complete agent configuration

### Existing Usage Analysis
- Used in `PipelineRestController` for all endpoints
- Created in `DynamicPipelineManager` from `PipelineRegistryEntry`
- Tested in `PipelineRestControllerTest`
- Currently only shows metadata, not full agent configuration

## Refactoring Strategy

### Option 1: Full Composition (Recommended)
Include the complete `AgentDefinition` object as a field in `PipelineInfo`.

#### Benefits:
- ✅ Single source of truth
- ✅ Complete access to all agent data
- ✅ No data duplication
- ✅ Backward compatibility can be maintained with deprecated methods
- ✅ Clearer domain model

#### New Structure:
```java
public record PipelineInfo(
    String id,
    AgentDefinition agentDefinition,
    LocalDateTime createdAt,
    boolean active,
    String source           // "YAML" or "DYNAMIC"
) {
    // Backward compatibility methods (deprecated)
    @Deprecated
    public String title() {
        return agentDefinition().title();
    }
    
    @Deprecated
    public String agentType() {
        return agentDefinition().agentType();
    }
}
```

### Option 2: Hybrid Approach
Keep both individual fields and `AgentDefinition` object during transition.

#### Benefits:
- ✅ Gradual migration path
- ✅ Immediate backward compatibility
- ❌ Data duplication during transition

## Implementation Plan

### Phase 1: Update PipelineInfo Structure
**Objective**: Refactor the PipelineInfo record to include AgentDefinition

#### Checklist:
- [ ] Update `PipelineInfo` record with new structure (Option 1)
- [ ] Add deprecated accessor methods for backward compatibility
- [ ] Update Jackson annotations if needed for proper serialization
- [ ] Add `@JsonUnwrapped` on `agentDefinition` field for flattened JSON if desired

#### Files to modify:
- `src/main/java/com/hdekker/ai_workflow/rest/dto/PipelineInfo.java`

### Phase 2: Update DynamicPipelineManager
**Objective**: Modify PipelineInfo creation to include AgentDefinition

#### Checklist:
- [ ] Update `addDynamicPipeline()` method to pass full `AgentDefinition`
- [ ] Update `listPipelines()` method to include `AgentDefinition` from registry entries
- [ ] Verify `initializeFromYAML()` method compatibility
- [ ] Ensure all PipelineInfo constructors are updated

#### Files to modify:
- `src/main/java/com/hdekker/ai_workflow/app/pipeline/management/DynamicPipelineManager.java`

### Phase 3: Update Tests
**Objective**: Fix tests to work with new PipelineInfo structure

#### Checklist:
- [ ] Update `PipelineRestControllerTest` test data creation
- [ ] Update assertions to access `AgentDefinition` through new structure
- [ ] Update mock return values in test setup
- [ ] Verify JSON serialization/deserialization works correctly
- [ ] Add tests for backward compatibility methods if kept

#### Files to modify:
- `src/test/java/com/hdekker/ai_workflow/rest/PipelineRestControllerTest.java`
- `src/test/java/com/hdekker/ai_workflow/app/pipeline/management/DynamicPipelineManagerTest.java`

### Phase 4: Update PipelineRestController (if needed)
**Objective**: Ensure controller works with new PipelineInfo structure

#### Checklist:
- [ ] Verify `createPipeline()` method works with new structure
- [ ] Verify `listPipelines()` method works with new structure
- [ ] Test JSON serialization matches expected API contract
- [ ] Update any inline documentation or API examples

#### Files to modify:
- `src/main/java/com/hdekker/ai_workflow/rest/PipelineRestController.java`

### Phase 5: Update API Documentation (Optional)
**Objective**: Document the new API structure

#### Checklist:
- [ ] Update API documentation to reflect new response structure
- [ ] Document any breaking changes (should be minimal with backward compatibility)
- [ ] Update examples in UI.md if needed

## Detailed Implementation

### New PipelineInfo Structure
```java
package com.hdekker.ai_workflow.rest.dto;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;

public record PipelineInfo(
    String id,
    @JsonUnwrapped  // Optional: flattens AgentDefinition fields in JSON
    AgentDefinition agentDefinition,
    LocalDateTime createdAt,
    boolean active,
    String source           // "YAML" or "DYNAMIC"
) {
    // Backward compatibility methods - deprecated but functional
    @Deprecated(since = "0.0.2", forRemoval = true)
    public String title() {
        return agentDefinition().title();
    }
    
    @Deprecated(since = "0.0.2", forRemoval = true)
    public String agentType() {
        return agentDefinition().agentType();
    }
}
```

### DynamicPipelineManager Changes
```java
// Before:
return new PipelineInfo(id, def.title(), def.agentType(), entry.createdAt(), true, "DYNAMIC");

// After:
return new PipelineInfo(id, def, entry.createdAt(), true, "DYNAMIC");
```

### JSON Output Examples

#### Current Structure:
```json
{
  "id": "uuid-123",
  "title": "Document Processor",
  "agentType": "Map",
  "createdAt": "2024-01-25T10:30:00",
  "active": true,
  "source": "DYNAMIC"
}
```

#### New Structure (with @JsonUnwrapped):
```json
{
  "id": "uuid-123",
  "fileInputRegex": ".*\\.md$",
  "title": "Document Processor", 
  "body": "Process markdown files...",
  "agentType": "Map",
  "outputStructure": "{\n  \"summary\": \"string\"\n}",
  "outputFilenameTemplate": "{timestamp}_processed.json",
  "createdAt": "2024-01-25T10:30:00",
  "active": true,
  "source": "DYNAMIC"
}
```

#### New Structure (without @JsonUnwrapped):
```json
{
  "id": "uuid-123",
  "agentDefinition": {
    "fileInputRegex": ".*\\.md$",
    "title": "Document Processor",
    "body": "Process markdown files...",
    "agentType": "Map", 
    "outputStructure": "{\n  \"summary\": \"string\"\n}",
    "outputFilenameTemplate": "{timestamp}_processed.json"
  },
  "createdAt": "2024-01-25T10:30:00",
  "active": true,
  "source": "DYNAMIC"
}
```

## Migration Strategy

### Backward Compatibility Approach
1. **Phase 1-4**: Add deprecated accessor methods to maintain existing API contract
2. **Phase 5** (Future): Remove deprecated methods after updating all consumers
3. **Documentation**: Clearly mark deprecated methods in JavaDoc

### API Consumer Impact
- **Minimal**: Existing code using `title()` and `agentType()` will continue to work
- **Enhanced**: New access to full `AgentDefinition` data
- **JSON Structure**: May change slightly (can be controlled with `@JsonUnwrapped`)

## Risk Assessment

### Low Risk
- Backward compatibility can be maintained with deprecated methods
- Internal refactoring only affects data structure, not business logic
- Tests can be updated systematically

### Medium Risk  
- JSON structure changes may affect external API consumers
- Need to verify serialization/deserialization works correctly

### Mitigation Strategies
- Use `@JsonUnwrapped` to maintain existing JSON structure if needed
- Comprehensive test coverage before deployment
- Gradual deprecation timeline for removed methods

## Success Criteria
- [ ] All existing tests pass with updated PipelineInfo structure
- [ ] New grid view can access full AgentDefinition data without transformation
- [ ] Backward compatibility is maintained for existing API consumers
- [ ] JSON serialization/deserialization works as expected
- [ ] No data duplication or integrity issues

## Timeline Estimate
- **Phase 1**: 2-3 hours (PipelineInfo update)
- **Phase 2**: 1-2 hours (DynamicPipelineManager update)  
- **Phase 3**: 3-4 hours (Test updates)
- **Phase 4**: 1 hour (Controller verification)
- **Phase 5**: 1 hour (Documentation updates)
- **Total**: 8-11 hours

## Next Steps After Refactoring
1. Update grid view implementation to directly access AgentDefinition
2. Remove data transformation logic from view layer
3. Add full AgentDefinition display in grid columns
4. Implement edit/delete functionality using complete agent data