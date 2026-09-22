# 190-response-handling

Where the response a channel hands back comes from, and what a destination's response transformer
can do to it. `Auto-generate (Destinations completed)` is covered by 110 and `Postprocessor` by 140,
so the channels here take the remaining "Respond from" choices plus the response transformer.

- `01-respond-before-processing` - the 110 HL7 channel responding with
  `Auto-generate (Before processing)` and a source filter that rejects ADT^A08. The one message is
  an A08, so `source_status` is FILTERED while `source_response` is still an `MSA|AA` ACK: this
  setting answers with an assumed RECEIVED status before the message is processed at all.
- `02-respond-after-source-transformer` - the same channel and filter responding with
  `Auto-generate (After source transformer)`, which takes the source connector message's own status.
  `01-accepted` is TRANSFORMED and gets `MSA|AA`; `02-filtered` is the same A08 as channel 01 and
  gets `MSA|AR|Message Rejected.` from the rejected-ACK settings. The contrast with channel 01 is
  the point of both channels.
- `03-respond-from-named-destination` - a RAW channel with two JavaScript Writers that answer
  `first destination answered` and `second destination answered`, responding from `d2`. A named
  destination's "Respond from" value is the response map key `d<metaDataId>`, not the connector
  name. `source_response` is destination 2's answer and `dest01_response` is destination 1's, so the
  fixture would fail if the engine took the first, the last, or a merged response.
- `04-response-transformer` - a RAW channel whose one JavaScript Writer echoes the payload back,
  behind a response transformer that rewrites the content and sets `responseStatus`.
  - `01-rewrites-content-and-status` - `dest01_response` is the connector's original `rewrite-me`,
    `dest01_processed_response` the rewritten `rewritten<rewrite-me>`, and `dest01_status` ERROR.
    The send itself succeeded, so the status comes from the transformer alone.
  - `02-transformer-error-fails-the-destination` - the payload `boom` makes the transformer throw.
    The connector's response is still stored, no processed response is (`((NONE))`), the destination
    is ERROR, and `dest01_processing_error` records the thrown message.
  - `03-queued-status-without-a-queue` - the transformer asks for QUEUED on a destination whose
    queue is off. The content is still rewritten, but the status is forced to ERROR and
    `dest01_response_error` carries the engine's explanation.
- `05-respond-from-unknown-name` - the same RAW channel with one JavaScript Writer, responding from
  `d9`, which no destination owns. The destination answers (`dest01_response`) but nothing is
  selected, so `source_response` is `((NONE))`: an unresolvable name yields no response rather than
  falling back to another one.

The sixth case in this theme is a Java test, `smoketest` `BlockingResponseTransformerTest`: a
destination whose response transformer has not returned yet sits at PENDING with its sent content
already stored. The fixture harness retries until the message is terminal, so it cannot see that
status. Its channel lives in `smoketest/src/test/resources/channels/`.
