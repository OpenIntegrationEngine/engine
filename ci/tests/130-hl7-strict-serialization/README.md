# 130-hl7-strict-serialization

The HL7 v2 data type with `useStrictParser`, so the message goes through HAPI rather than being
read as delimited text. The channel's transformer step does nothing; it is there only because the
engine does not serialize at all without one.

- `01-valid-adt` - an ADT^A01 is TRANSFORMED, and `source_transformed` asserts the whole strict
  `urn:hl7-org:v2xml` document, which is what distinguishes strict parsing from the `<HL7Message>`
  form the non-strict reader produces.
- `02-not-hl7` - a payload HAPI cannot read gives ERROR rather than being passed through.
- `03-unknown-message-type` - HAPI reads the message but has no structure class for its
  MSH-9, so it parses into a `GenericMessage` and serialization fails. Distinct from
  `02-not-hl7`, where the parse itself fails.

`02-strict-validating` is the same channel with `useStrictValidation` on as well, running the
same two messages. Validation changes neither outcome, which is worth pinning: an operator
reaching for stricter handling does not get different behaviour here.
