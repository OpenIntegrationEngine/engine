# 120-filter-transformer

Filter and transformer outcomes, on the RAW data type so that the transformed and encoded content
are exactly what the script left in `msg`.

- `01-source-filter` - a JavaScript rule rejecting messages containing `REJECT`. An accepted
  message is TRANSFORMED and reaches `dest01`; a rejected one is FILTERED, and its transformed
  content is stored all the same, because the filter runs after the content is handed to the script.
- `02-source-transformer` - a JavaScript step prefixing the message. The transformed content is the
  script's output, and RAW encodes to it unchanged, so the destination receives the same thing.
- `03-transformer-error` - a step that throws, giving ERROR.

The case of no filter and no transformer at all is 101 and 110.
