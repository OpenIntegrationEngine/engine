# 200-custom-metadata-columns

Custom metadata columns: a channel declares them, the engine adds a real database column per
declared column on deploy, and each connector message gets its own row of values. One RAW channel
with four columns, one per `MetaDataColumnType`, each mapped to a lower-case source map key:

| column | type | mapping name |
| --- | --- | --- |
| `MDSTRING` | STRING | `mdstring` |
| `MDNUMBER` | NUMBER | `mdnumber` |
| `MDBOOLEAN` | BOOLEAN | `mdboolean` |
| `MDTIMESTAMP` | TIMESTAMP | `mdtimestamp` |

Values arrive in the source map rather than from a transformer script, so each message is nothing
but the values in and the stored values out. `source_metadata.yml` asserts what came back out of
the database, keyed by column name — never by mapping name, which would also match the connector
map and prove nothing about storage. A NUMBER column is `DECIMAL(31, 15)` in every dialect, so its
values come back with fifteen decimal places.

- `01-all-types` - one value per type survives the round trip. Destination 1's transformer puts its
  own `mdstring` in the connector map, which the replacer prefers over the source map, so
  `dest01_metadata.yml` shows the destination's row holding a different string from the source's
  while its number is unchanged. Two rows, not one copied.
- `02-value-casting` - the same four columns fed values that are not already of the column's type:
  `YES` becomes the boolean `true`, an HL7-style `20100102130102` becomes the same instant as
  `01-all-types`' `2010-01-02 13:01:02`, and an over-long string is truncated to 255 characters,
  which is where the `|255|` marker in the expected value sits.
- `03-absent-and-uncastable` - a column with nothing mapped to it, and a NUMBER column fed `not a
  number`, both end up with no value. Casting is best-effort: the failure is logged and the message
  still reaches TRANSFORMED, which `source_status` asserts.

Adding a column is all a fixture can reach, because it deploys each channel exactly once. Removing
a column, and the drop-then-add that changing a column's type compiles to, need a channel that is
edited and redeployed, which is `CustomMetaDataColumnRedeployTest`.
