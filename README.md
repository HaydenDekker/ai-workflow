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

### Prompt Response Splitter

- (done) Prompt provides 0,1,n of responses - always returns a list. Easy api really.

## Prompt Response View

1. (done) Display prompt responses.

2. (done) Show source file

3. (done) Show prompt input for a response. Prompt is now packaged into the response. Have to re-parse but this feels clear.

## Prompt Content Configuration

Need to work specific prompts to get them conistent accross various models. 
That can be acheived by adding additional prompt chains that trigger from
specific folder root where the test files can be placed in those roots and
the output of the chain viewed via the UI.

- (done) prompt configuration into filesystem (allows easy design with .md)

- dynamic root folder monitor configuration

- multiple chains in configuration

## TODO List

- List of outputs aggregated to single prompt. / but better being a map().reduce() operation.
- When should a prompt chain state be invalidated? Change of file (yes), change of prompt (yes), 
  but only have to redo the invalidated steps, easier to redo the whole step initially.
- outputs for conditional logic, how to define, parse and execute branches.
