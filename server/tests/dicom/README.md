# DICOM test samples

Each sample is one instance from a public collection of The Cancer Imaging Archive (TCIA), as
distributed by the NCI Imaging Data Commons (IDC) and obtained by way of
https://saga-it.com/dicom/samples. The files are stored as received: OIE has not modified them. Each
collection's license and DOI are in the table below (looked up in IDC by the sample's
SeriesInstanceUID); see the TCIA data usage policy at
https://www.cancerimagingarchive.net/data-usage-policies-and-restrictions/ for citation requirements.

Each `<name>.dcm` is paired with `<name>.xml`, the expected serialization of its header.
`DICOMSerializerTest` discovers the pairs by scanning this directory, so a new sample needs no code
change. The XML covers the header only - `DICOMSerializer.removePixelData` strips pixel data before
serializing, since including it would dwarf the rest of the document.

| Sample | Modality | Collection | License | Collection DOI |
| --- | --- | --- | --- | --- |
| `ct-abdomen-c4kc-kits-instance` | CT | C4KC-KiTS | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) | [10.7937/TCIA.2019.IX49E8NX](https://doi.org/10.7937/TCIA.2019.IX49E8NX) |
| `ct-chest-lidc-idri-instance` | CT | LIDC-IDRI | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) | [10.7937/K9/TCIA.2015.LO9QL9SX](https://doi.org/10.7937/K9/TCIA.2015.LO9QL9SX) |
| `ct-lung-screening-nlst-instance` | CT | NLST | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | [10.7937/TCIA.HMQ8-J677](https://doi.org/10.7937/TCIA.HMQ8-J677) |
| `mg-mammography-cbis-ddsm-instance` | MG | CBIS-DDSM | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) | [10.7937/K9/TCIA.2016.7O02S9CY](https://doi.org/10.7937/K9/TCIA.2016.7O02S9CY) |
| `mr-prostate-prostatex-instance` | MR | PROSTATEx | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) | [10.7937/K9TCIA.2017.MURS5CL](https://doi.org/10.7937/K9TCIA.2017.MURS5CL) |
| `pt-breast-qin-01-instance` | PT | QIN-BREAST | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) | [10.7937/K9/TCIA.2016.21JUEBH0](https://doi.org/10.7937/K9/TCIA.2016.21JUEBH0) |
| `us-lymph-node-cmb-lca-instance` | US | CMB-LCA | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | [10.7937/3CX3-S132](https://doi.org/10.7937/3CX3-S132) |

The patient identifiers in these files are the collections' own pseudonyms (for example
`LIDC-IDRI-0580`), not real ones.
