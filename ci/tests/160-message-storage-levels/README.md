# 160-message-storage-levels

Which content rows a channel's message storage setting keeps. Every channel here is the same
RAW-to-RAW channel with one JavaScript transformer step — so raw, transformed, encoded and sent
all exist to be stored — and differs only in `<messageStorageMode>`.

- `01-development` - DEVELOPMENT keeps everything: raw, transformed, encoded and the sent content.
- `02-production` - PRODUCTION drops transformed (and processed raw), keeping raw, encoded and
  sent. This is the one level where content is partly kept and partly dropped.
- `03-raw` - RAW keeps only the raw content. Transformed, encoded and sent are gone, but the
  statuses show the message still ran all the way to SENT.
- `04-metadata` - METADATA keeps no content at all, only the message and connector message rows,
  so the statuses are all that is left to assert.

DISABLED, the fifth level, stores nothing whatsoever — not even the message row a fixture's
assertions need a message to run against — so it lives in `MessageStorageDisabledTest` instead.
