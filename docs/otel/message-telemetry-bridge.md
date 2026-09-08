# Optional message telemetry bridge

The engine exposes one optional `MessageTelemetry.Provider`. It owns no SDK, telemetry registry,
queue carrier, exporter, configuration storage, or background thread. A plugin can use standard
OpenTelemetry Context/Scope and propagators behind this neutral Java contract.

`SOURCE` surrounds `Channel.process`, including preprocessing, source transformation, destination
chains and synchronous postprocessing. `TRANSFORM`, `SEND`, and `RESPONSE` surround the existing
shared filter/transformer, validated send, and response-transformer execution methods. The original
method bodies, transaction boundaries and queue operations are retained. A source span's final
source-message status is distinct from a destination's final status. Send/response observations
also receive the actual returned response status before the caller applies queue status rules.

The provider receives the existing connector message, including its maps. It must not change
message processing. The intended plugin-owned exception is publication of documented scalar
propagation values in the channel map; that plugin behavior is not implemented by this bridge.

Capture occurs before submission to the destination-chain and JavaScript executors. The provider
returns a resource-free context activator, not a replacement business task. The engine activates
on the worker and invokes the original callable exactly once, restoring context in a lexical
finally. Capture does not start a span or open a scope. Rejection and cancellation before start
therefore require no telemetry cleanup. Cancelled running work closes its scope on its own thread.

Ordinary callback failures, including linkage/assertion errors, are isolated and yield one fixed
warning per installation, without exception/message content. A failed start/activation must clean
up its own partial work before throwing; the engine cannot recover resources a provider never
returned. VM errors and ThreadDeath propagate. An original engine fatal remains primary if its
failure-notification callback also throws a fatal; the callback fatal is suppressed when possible.
Java try-with-resources governs suppression when
business execution and scope cleanup both throw fatal errors. Closing a registration is idempotent
and cannot detach a newer installation. Already captured context activates through its original
provider; new stage observations inside that work select the current installation. The token
neither drains nor shuts down provider resources.

## Behavior matrix and evidence

| Case | Required behavior | Evidence / remaining scope |
| --- | --- | --- |
| No provider | Same no-op observation and original callable; no per-message bridge allocation | `MessageTelemetryTest` identity checks plus direct fast-path inspection |
| Duplicate install / repeated close / stale token | Reject overlap; old token never detaches replacement | Registration regression |
| Ordinary start, capture, activation, status, failure, close errors | Business task/result/exception preserved; cleanup attempted | Bridge fault matrix and real-channel callback-failure fixture |
| Fatal callbacks | VM error / ThreadDeath identity retained; original engine fatal wins a second failure-callback fatal; standard suppression on cleanup | Full six-surface fatal matrix and actual transformer regression |
| Synchronous processing | Balanced source/transform/send/response scopes; unchanged stored outcomes | Private Derby channel fixture |
| Parallel destinations | Source context crosses worker submission; destination maps are separate | Two real destination chains, one worker and one inline |
| Filter or source transformation error | Correct durable FILTERED/ERROR; no destination scopes | Private Derby negative paths |
| Preprocessor/destination filter, transform, validator or response errors | Original handled failure and correct source/destination stored status | Actual channel negatives; checked response/transform failure identity |
| Synchronous and queued destination retries | One scope per actual send; raw response and final status distinguished | Actual two-attempt retry cases with durable final SENT |
| Source queue | Source scope starts on actual queue worker; normal completion | Private Derby queued-source fixture; parent continuity is a separate adapter concern |
| Script worker success / failure / interrupt | Actual JavaScript executor transfers and restores context; original exception/cancellation semantics | Executor tests plus real Rhino execution, exception and infinite-loop cancellation |
| Rejected / cancelled before start | No task execution or open telemetry scope | Controlled executor regressions |
| Cancelled after start | Worker restores its own prior context | Bridge and JavaScript executor interruption tests |
| Detach with captured task | Previously captured immutable context remains usable; new installation independent | Bridge detach/worker tests |
| Partial provider start | Provider must restore any partial attachment before throwing | Explicit provider responsibility; adapter requires its own fault tests |
| Channel-map propagation, incoming HTTP, unsampled parents | Standard W3C extraction/injection; runtime context independent of editable maps | Plugin adapter pending |
| Destination attempt parent; retries/refill/restart/nested channel/batch | Real queue/transaction behavior unchanged; documented parent policy | Further reduced-design integration pending; no durable carrier in this bridge |
| Actual instrumented HTTP/JDBC | Dependency spans share the channel context without duplicates | Agent/library interoperability proof pending |
| UI action-time config / retries / ambiguous writes | Explicit plugin-owned persistence and ownership guarantees | Configuration adaptation pending; old plugin cannot yet start on this engine |

This is the first reduced bridge slice, not a compatible plugin release or complete OTel acceptance.
Performance must be measured on the final integrated reduced implementation; previous benchmarks
of the large lifecycle SPI do not establish this bridge's overhead.
