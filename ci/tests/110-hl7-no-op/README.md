# 110-hl7-no-op

The same no-op channel as 101 with the HL7 v2 data type at both ends, responding with
`Auto-generate (Destinations completed)`.

- `dest01_metadata.yml` - the custom metadata columns the HL7 serializer fills in from MSH-4 and
  MSH-9 on its own, with no transformer having run.
- `source_response` - the generated ACK, echoing the inbound MSH fields and MSA-2.
