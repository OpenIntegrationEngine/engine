# 170-content-removal

`removeContentOnCompletion`: a channel that stores its content normally while the message is in
flight and deletes it once every connector has completed. Both channels below are the
DEVELOPMENT-storage channel of 160 with that flag turned on, so 160's `01-development` is the
control showing what is there to be removed.

- `01-unqueued` - the destination is not queued, so the engine removes content in the same
  transaction that marks the message processed.
- `02-queued-destination` - the destination is queued, which forces the removal into a separate
  transaction committed after the destination is already SENT. The content ends up equally gone;
  this is the path that reaches the delete from the queue thread rather than the process thread.

In both cases the statuses survive the removal, which is what separates removed content from a
channel that never stored any.
