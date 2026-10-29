# 140-pre-post-processor

A no-op RAW channel carrying a pre-processor and a post-processor, responding with `Postprocessor`.

- `dest01` - the pre-processor's return value. With no filter or transformer the encoded content is
  the processed raw content rather than the raw content, so this is also what proves the
  pre-processor ran.
- `source_response` - the post-processor's return value.
