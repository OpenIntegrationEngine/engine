# 101-raw-no-op

A channel that does nothing: the RAW data type at both ends, no filter or transformer elements,
Channel Reader to a Channel Writer templated on `${message.encodedData}`.

With no filter or transformer the engine skips serialization altogether and the encoded content is
the raw content, so `dest01` asserts the payload reaches the destination unchanged.
