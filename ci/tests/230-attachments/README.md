# 230-attachments

Attachment storage and retrieval alongside the message. All three channels use the same Regex
attachment handler, whose two patterns pull the value out of `PHOTO:...;` as `image/jpeg` and out
of `REPORT:...;` as `text/plain`, leaving a `${ATTACH:id}` token in the message where each one was.
The payload carries a non-ASCII character so that the attachment content is not the same bytes as
its characters, which is what makes `attachmentNN`'s byte-for-byte comparison worth having.

- `01-regex-extraction` - extraction and storage: the message keeps the tokens, the attachments keep
  the content and the mime type of the pattern that matched.
  - `01-two-attachments` - both patterns fire. `source_raw` carries a token in place of each
    extracted value, `attachment01`/`attachment02` are what was taken out, in the order their tokens
    appear, and `attachment03` is `((NONE))`: exactly two attachments were stored, not three.
  - `02-blank-attachment-not-extracted` - `PHOTO:;` matches the pattern with an empty value. The
    handler leaves a blank match in the message rather than storing an empty attachment, so
    `source_raw` still reads `PHOTO:;` and the report is the only attachment.
- `02-reattachment` - retrieval: the same channel with a source transformer step calling
  `AttachmentUtil.reAttachMessage(connectorMessage)`. `source_raw` is the tokenised message and
  `source_transformed` is the original payload, so the engine read both attachments back out of
  storage by ID and put them where their tokens were. This is the read a destination connector makes
  when `reattachAttachments` is set.
- `03-remove-on-completion` - the same channel with `removeAttachmentsOnCompletion`. Once the message
  completes, `attachment01` is `((NONE))` while `source_raw` still holds the tokens: the attachments
  are dropped and the message is not, which is what makes the setting lossy. Content removal is off,
  so this is the attachment half of 170-content-removal on its own.
