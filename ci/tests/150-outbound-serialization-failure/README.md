# 150-outbound-serialization-failure

What the engine leaves behind when the transformed content is something the *outbound* data type
cannot serialize. The channel reads RAW and writes HL7 v2, so `msg` is the submitted payload
verbatim and the payload alone decides whether the outbound serializer succeeds — the one
arrangement in which a message the harness can submit reaches that failure.

- `01-serializable` - an `<HL7Message>` XML document, as the control: `source_encoded` is the ER7
  the outbound serializer produced from it, so the channel is known to work for content it accepts.
- `02-not-xml` - a payload the outbound serializer cannot parse. The source is ERROR, the
  transformed content is stored all the same, and no encoded content exists at all — `((NONE))`,
  which is not the same as an empty string.
