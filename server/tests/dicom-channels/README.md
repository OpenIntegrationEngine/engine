# DICOM Manual Test Channels

Channels and transformer script for validating the DICOM connector end-to-end
against a running Open Integration Engine server. Used to confirm the
`dicom.library` toggle (dcm4che2 vs dcm4che5) produces identical behaviour.

## Files

| File | Purpose |
|---|---|
| `DICOM_Forward.xml` | DICOM Listener on `:11112` that forwards to `:11113` |
| `DICOM_Receiver.xml` | DICOM Listener on `:11113` with a JavaScript transformer that exercises the DICOM user API |
| `dicom-api-test.js` | Transformer script that asserts every `OieDicomObject`, `OieDicomElement`, and `DICOMUtil` method. Emits `[DICOM_TEST]` log lines |

Both channels are configured with `<inboundDataType>DICOM</inboundDataType>` /
`<outboundDataType>DICOM</outboundDataType>`. The admin UI defaults new
channels to HL7v2; importing these XMLs avoids having to set the data type
manually.

## Usage

Start the server, then import via REST API:

    curl -sk -u admin:admin -H 'X-Requested-With: XMLHttpRequest' \
         -H 'Content-Type: application/xml' \
         -X POST --data-binary @DICOM_Forward.xml \
         https://localhost:8443/api/channels
    curl -sk -u admin:admin -H 'X-Requested-With: XMLHttpRequest' \
         -H 'Content-Type: application/xml' \
         -X POST --data-binary @DICOM_Receiver.xml \
         https://localhost:8443/api/channels
    curl -sk -u admin:admin -H 'X-Requested-With: XMLHttpRequest' \
         -X POST https://localhost:8443/api/channels/_redeployAll

Send the DICOM fixtures through the pipeline with DCMTK:

    storescu localhost 11112 ../test-dicom-input-1.dcm
    storescu localhost 11112 ../test-dicom-input-2.dcm
    storescu localhost 11112 ../test-dicom-input-3.dcm

Watch the server log for `[DICOM_TEST]` lines. Each message produces:

    [DICOM_TEST] === START message=N ===
    [DICOM_TEST] PASS ...            (one per assertion)
    [DICOM_TEST] backend=dcm4che2 (impl=org.dcm4che2.data.BasicDicomObject)
    [DICOM_TEST] === END message=N backend=dcm4che2 failures=0 result=ALL_PASS ===

To validate the dcm4che5 backend, set `dicom.library = dcm4che5` in
`server/conf/mirth.properties`, restart the server, redeploy the channels,
and rerun `storescu`. The `backend=` field in the END line should report
`dcm4che5` and every assertion should still PASS.
