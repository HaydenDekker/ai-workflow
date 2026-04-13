# Observability – Technology‑agnostic Recommendations for the *ai‑workflow* codebase

**High‑level Observability Goals**
| Goal | What it means for this project |
|------|--------------------------------|
| Visibility | Know when a pipeline is created, started, finishes, fails, or is removed. |
| Performance | Measure latency and throughput of the `PromptPipeline` (LLM request time, file‑scan time, etc.). |
| Reliability | Detect error spikes, unhandled exceptions, and resource leaks (e.g., undisposed `Disposable`). |
| Traceability | Follow a request end‑to‑end across the REST API, manager, configurator, the LLM adapters, and file I/O. |
| Operational health | Expose health checks for the core components (chat client connectivity, file‑system access, DB). |

**Cross‑cutting Instrumentation**
| Concern | Recommended Hook / Pattern | Why |
|----------|--------------------------|-----|
| Correlation / Request ID | Generate a unique ID at the entry point (`AgentRestController#createAgent` or `listAgents`) and propagate it through method arguments or thread‑local/context. | Enables linking logs, metrics, and traces belonging to the same agent execution. |
| Structured Logging | Replace raw `log.info("…")` with key‑value pairs (e.g., `log.info("pipeline.created", KV("id", id), KV("type", source))`). | Makes automated log analysis easier, regardless of the logging backend. |
| Exception handling | Centralize error handling (e.g., a `@ControllerAdvice` for REST, or an AOP interceptor for service methods) that records an `error` metric and writes a log entry with the correlation ID. | Guarantees failures are observed consistently. |
| Metrics recording | Provide a thin wrapper (e.g., `ObservabilityMetrics.recordTimer(name, tags, () -> …)`) around any potentially slow operation. | Keeps timing code tidy and works with any metric library. |
| Tracing spans | Start a top‑level span at the REST controller, then create child spans for: • `DynamicAgentManager.addDynamicAgent` (agent‑creation) • `AgentConfigurator.configure` (agent‑building) • each LLM adapter call (LLM request) • file reading/writing steps | Gives a hierarchical view of work without tying the code to a specific tracer. |
| Health checks | Expose an endpoint (e.g., `/actuator/health`) that aggregates: • Chat‑client connectivity • Database availability • File‑system read/write permission • Any external LLM service health | Allows orchestration platforms to react to component failures. |

**Class‑by‑Class Observability Recommendations**
| Class | Observability Feature(s) to Add | Suggested Implementation Detail |
|-------|------------------------------|--------------------------------|
| `DynamicAgentManager` | • Metrics: `agent.created`, `agent.removed`, `agent.active.count` \n• Tracing: Span around `addDynamicAgent` and `removeAgent` \n• Logging: Include agent **ID** and **source** (`YAML`/`DYNAMIC`) in every log entry | Use a `Timer` to measure time spent creating the `Flux` and subscribing. Increment a gauge for active agents whenever the map changes. |
| `AgentConfigurator` | • Metric: `agent.configure.duration` \n• Trace: Span named `agent.configure` wrapping the call to `LLMAdapterFactory.create` and the returned `Flux` assembly | Record the size of the `Flux` (number of elements) as a counter when the agent runs. |
| `AgentBuilder` | • Metric: `agent.build.duration` \n• Trace: Span per agent step (`trigger`, `prompt`, `persist`, `split`) \n• Logging: Log the chosen `AgentDefinition.title` when `withDefinition` is called | The builder can expose a hook (`onBuildComplete`) that the manager invokes to emit a metric/report. |
| `LLMAdapterFactory` | • Logging: Log which concrete adapter is instantiated (use the correlation ID). No metrics needed here, but helpful for debugging. |
| `MapAgentLLMAdapter` / `LLMReducerAdapter` / `SplitterLLMAdapter` | • Metric: `llm.request.count`, `llm.request.duration`, `llm.request.errors` \n• Trace: Span around each `chatClient.prompt(...)` call \n• Logging: Include the *agent title* and the *file URL* that is being processed | The `call` method is the natural place to start a child span and record a timer. Wrap the reactive chain with `doOnError` to capture failures. |
| `FileScanner` & implementations | • Metric: `file.scan.count`, `file.scan.duration` \n• Trace: Span for the whole scan operation \n• Logging: Log each file path discovered (at DEBUG) with the correlation ID | For the reactive `Flux<FileHistory>` source, attach `doOnNext` and `doFinally` hooks. |
| `FileWriter` / `FileSystemFileWriter` | • Metric: `file.write.bytes`, `file.write.duration`, `file.write.errors` \n• Trace: Span when persisting a `PromptResponse` \n• Logging: Log the output file location. |
| `AgentRestController` | • Metric: `http.request.count`, `http.request.duration` (tagged by `method` and `endpoint`) \n• Trace: Top‑level span (automatically created by most tracing libraries when an HTTP request arrives) \n• Logging: Include the generated **agent ID** in the response log. |
| `AiWorkflowApplication` (main class) | • Health: Register a *startup* health check that validates the `ChatClient` can reach the LLM service and the DB connection is alive. |
| `AgentDefinition` (DTO) | • Logging: When an `AgentDefinition` is deserialized (e.g., in the REST `POST`), log its `title`, `agentType`, and any custom fields. |
| `PromptResponse` & `PromptRequest` | • Logging: Include the *file URL* and *correlation ID* when they are created. |
| Database entities (`FileMetadataEntity`) | • Metric: `db.query.duration`, `db.error.count` (if you add a repository wrapper). \n• Trace: Span around repository calls (Spring Data can be auto‑instrumented). |

**Suggested Central Observability Helper**
Create a lightweight **`ObservabilityFacade`** (or an interface) that abstracts the concrete library:
```java
public interface ObservabilityFacade {
    Span startSpan(String name, Map<String, String> tags);
    void endSpan(Span span, Throwable error);
    Timer startTimer(String metricName, Map<String, String> tags);
    void recordTimer(Timer timer, Duration elapsed);
    Counter incrementCounter(String metricName, Map<String, String> tags);
    void gauge(String metricName, double value, Map<String, String> tags);
    void log(String level, String message, Map<String, Object> fields);
    void setCorrelationId(String id);
    String getCorrelationId();
}
```
All classes above can depend on this façade (injected via Spring) rather than a concrete library. Swapping Micrometer, Prometheus, OpenTelemetry, or any vendor‑specific SDK becomes a single‑implementation change.

**Implementation Path (step‑by‑step)**
1. Add the façade (or use an existing abstraction if the project already includes Micrometer/OTel).  
2. Instrument the REST layer – add request‑level metrics and propagate a correlation ID (e.g., via `MDC`).  
3. Wrap the `DynamicPipelineManager` methods with spans and metric recording.  
4. Enhance the LLM adapters to time each call to the chat client and capture errors.  
5. Instrument file‑scan and file‑write flows (the `Flux` pipelines) using reactive hooks (`doOnSubscribe`, `doOnNext`, `doOnError`, `doFinally`).  
6. Add health contributors for the chat client, DB, and file system.  
7. Upgrade logging to structured key‑value format (most logging frameworks support this via MDC or JSON encoders).  
8. Verify with a local observability stack (e.g., Prometheus + Grafana + Jaeger) that the new metrics, traces, and health endpoints appear and are correctly tagged.  
9. Document the new observability conventions for future contributors (e.g., “all service methods must start a span named `<class>.<method>` and record a timer”).

**Example Metric & Trace Flow (Illustrative)**
```
HTTP POST /api/agents
│
└─► Span: http.request (method=POST, path=/api/agents)
      └─► Span: agent.create (agentId=abcd‑1234, source=DYNAMIC)
            └─► Span: agent.configure
                  └─► Span: llm.request (agent=MapAgent, file=…/myFile.txt)
                        └─► Metric: llm.request.duration{agent=MapAgent}=350ms
```
All spans automatically inherit the correlation ID from the HTTP request, and each metric is tagged with `agentId`, `agent`, and `status` (success/failure). This pattern works the same whether the underlying library is Micrometer+OTel or a proprietary APM.

**Summary**
| What to add | Where | Benefit |
|-------------|-------|---------|
| Correlation IDs | REST controller → forward to all services | End‑to‑end traceability |
| Structured logs | Every class (especially manager & adapters) | Easier log parsing & alerting |
| Timers & counters | Manager, configurator, LLM adapters, file I/O, DB | Quantitative performance & reliability data |
| Spans / tracing | Top-level request → manager → configurator → each adapter | Visualize latency bottlenecks, follow a request through the whole agent |
| Health checks | Application startup (ChatClient, DB, FS) | Immediate detection of missing dependencies |
| Central façade | New utility package | Library‑agnostic implementation, future‑proof |

By applying these instrumentation points, the *ai‑workflow* service will gain full‑stack observability – regardless of the concrete monitoring stack you later choose. The result is better operational insight, faster incident resolution, and a solid foundation for scaling the system.
