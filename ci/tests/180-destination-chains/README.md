# 180-destination-chains

Which maps a destination inherits, and from where. One RAW channel with four destinations; the third
has `waitForPrevious` false, so destinations 1-2 are one chain and destinations 3-4 another. The
source seeds `fromSource` in the channel and response maps, destination 1 adds `fromD1` to both,
destination 3 adds `fromD3` to the channel map, and each destination writes its own `cmN` to its
connector map. Every transformer renders what it can see *before* writing, so `destNN_transformed`
is a snapshot of what that destination inherited:

| | d1 (chain A) | d2 (chain A) | d3 (chain B) | d4 (chain B) |
| --- | --- | --- | --- | --- |
| connector map `cm1` | absent | absent | absent | absent |
| channel map `fromSource` (`chS`) | S | S | S | S |
| channel map `fromD1` (`chD1`) | absent | **C1** | absent | absent |
| channel map `fromD3` (`chD3`) | absent | absent | absent | **C3** |
| response map `fromSource` (`rsS`) | present | present | present | present |
| response map `fromD1` (`rsD1`) | absent | **present** | absent | absent |

Response map rows record only presence because the entry is stored as a serialised `Response`. The
bold cells are the only hand-off: channel and response maps pass to the next destination in the same
chain. Everything else comes from the source, because each chain starts afresh from the source
connector message, and `cm1` never propagates at all.

`destNN_metadata.yml` asserts each `cmN` landed; `destNN_status` that both chains completed.
