# DICOM test samples

All CC-BY licensed (commercial reuse OK with attribution), sourced from NCI Imaging Data Commons,
by way of https://saga-it.com/dicom/samples

Each `<name>.dcm` is paired with `<name>.xml`, the expected serialization of its header.
`DICOMSerializerTest` discovers the pairs by scanning this directory, so a new sample needs no code
change. The XML covers the header only - `DICOMSerializer.removePixelData` strips pixel data before
serializing, since including it would dwarf the rest of the document.

| Sample | Modality | Collection |
| --- | --- | --- |
| `ct-abdomen-c4kc-kits-instance` | CT | C4KC-KiTS |
| `ct-chest-lidc-idri-instance` | CT | LIDC-IDRI |
| `ct-lung-screening-nlst-instance` | CT | NLST |
| `mg-mammography-cbis-ddsm-instance` | MG | CBIS-DDSM |
| `mr-prostate-prostatex-instance` | MR | PROSTATEx |
| `pt-breast-qin-01-instance` | PT | QIN-BREAST |
| `us-lymph-node-cmb-lca-instance` | US | CMB-LCA |

The patient identifiers in these files are the collections' own pseudonyms (for example
`LIDC-IDRI-0580`), not real ones.
