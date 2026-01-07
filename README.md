# AI Workflow

Aiming to have AI act as the navigator rather than the driver of pair programming. A root directory is monitored for file events. The file is pushed into the configured prompt pipeline and a response is provided. The responses are displayed in via web ui.

I imagine working away, being creative, and the machine churning away in the background validating all of my work. I'm the expert, not the AI.

## Prompt chains

Building up to an agent,

- (done) pipe a request through a configured LLM prompt and save to a database.

- (done) create a second request triggered off an initial request.

- (done) Move to dynamic configuration of prompt chain

- (done) Split prompt after persisting.

- (Cancelled - its simpler to adjust the prompt to provide just the filtered answers) Add filter after the split to stop optimise usage of GPU when no further processing is required.

- (done) Response should include input pre-pended in a single string. (simplest approach, otherwise could enrich using message headers at beginning of prompt)

- Messages, use spring message objects to pass content along the chain

- (done) Builder to accept PromptRequest and return PromptResponse. Requires mapping stages but is clearer on the intent of each stage.

### Prompt Response Splitter

- (done) Prompt provides 0,1,n of responses - always returns a list. Easy api really.

### Map Reduce Prompt

 - (done) List of outputs aggregated to single prompt, a map().reduce() operation. Requires local state and feedback to ensure the prompt output is ready for the next response. For the api, the stage to select this is in the enrich phase, setting a flag for reduce. The domain model would need to carry this so that the prompt stage will cache the previous response for input. Surely this is ok.

## Prompt Response View

1. (done) Display prompt responses.

2. (done) Show source file

3. (done) Show prompt input for a response. Prompt is now packaged into the response. Have to re-parse but this feels clear.

- Trigger a chain to re-run manually. Anywhere along the chain should run it downstream.

## Prompt Content Configuration

Need to work specific prompts to get them consistent across various models. 
That can be achieved by adding additional prompt chains that trigger from
specific folder root where the test files can be placed in those roots and
the output of the chain viewed via the UI.

- (done) prompt configuration into file-system (allows easy design with .md)

- (cancelled - simpler to start the program at the root directory, inline with OpenCode's startup method) dynamic root folder monitor configuration.

- (done) multiple chains in configuration

## Architectural Pivot

The database add's unnecessary complexity as prompt outputs/thoughts could themselves be files. Outputting results as files improves the git workflow, allowing thought state to be progressively captured. It removes the database interface. The prompt config needs are also simplified as the user only needs to consider what files are present and in what location. Accessing the files is then simply a matter of pulling up the directory and flicking through to see results.

The database is still required for file-system hash monitoring so that the entire prompt chain isn't reloaded on startup.

- (done) Create PromptResponseFileSystemAdapter to receive and store prompt responses.
- (done) Create FileOutput stage for prompt chain builder and enable configuration to define target file type.
- (done) Update file scanner to listen to all event
- Update pipeline configuration to filter on file regex.
- Update pipeline configuration to listen to new response file events/thoughts.
- Create PromptResponsePort that bridges the PromptResponseDatabaseAdapter.
- Update View to utilise new port.
- Create PromptResponseFileSystemAdapter to implement PromptResponsePort
- Remove PromptResponseDatabaseAdapter from program.
- Remove event based prompt chain configuration as new events occur only on file change.


## TODO List

- When should a prompt chain state be invalidated? Change of file (yes), change of prompt (yes), 
  but only have to redo the invalidated steps, easier to redo the whole step initially.
- outputs for conditional logic, how to define, parse and execute branches.
- need a way to clear memeory of any prompts to reset the system. Involves clearing file-system hash cache.
- set a flag for autocopy of prepackaged prompts.
