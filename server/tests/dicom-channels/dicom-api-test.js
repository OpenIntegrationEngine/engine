// === OIE DICOM API Regression Script (v2: Rhino-safe) ===
var results = {};
var failures = [];
var LOG_PREFIX = '[DICOM_TEST] ';

function nonEmpty(x) { return x != null && String(x) !== ''; }

function check(name, cond, info) {
    var status = cond ? 'PASS' : 'FAIL';
    var line = status + ' ' + name + (info != null ? ' (' + info + ')' : '');
    results[name] = line;
    if (cond) { logger.info(LOG_PREFIX + line); }
    else      { failures.push(line); logger.warn(LOG_PREFIX + line); }
}

logger.info(LOG_PREFIX + '=== START message=' + connectorMessage.getMessageId() + ' ===');

var dicomBytes = DICOMUtil.getDICOMMessage(connectorMessage);
check('rawBytesNonEmpty', dicomBytes != null && dicomBytes.length > 0, 'len=' + (dicomBytes ? dicomBytes.length : 'null'));

var rawBytes = DICOMUtil.getDICOMRawBytes(connectorMessage);
check('rawBytesDirectAccess', rawBytes != null && rawBytes.length > 0, 'len=' + (rawBytes ? rawBytes.length : 'null'));

var rawB64 = DICOMUtil.getDICOMRawData(connectorMessage);
check('rawDataBase64', nonEmpty(rawB64), 'b64len=' + (rawB64 ? String(rawB64).length : 'null'));

var dicomObj = DICOMUtil.byteArrayToDicomObject(dicomBytes, false);
check('parseObject', dicomObj != null);

var patientName = dicomObj.getString(0x00100010);
var patientID   = dicomObj.getString(0x00100020);
var modality    = dicomObj.getString(0x00080060);
var sopClassUID = dicomObj.getString(0x00080016);
var sopInstUID  = dicomObj.getString(0x00080018);

check('getString_patientName', nonEmpty(patientName), patientName);
check('getString_patientID',   nonEmpty(patientID),   patientID);
check('getString_modality',    nonEmpty(modality),    modality);
check('getString_sopClassUID', nonEmpty(sopClassUID), sopClassUID);
check('getString_sopInstUID',  nonEmpty(sopInstUID),  sopInstUID);

var studyDate = dicomObj.getString(0x00080020, 'UNKNOWN');
var numFrames = dicomObj.getInt(0x00280008, 1);
var bogusTag  = dicomObj.getString(0x00190099, 'DEFAULT');
var bogusInt  = dicomObj.getInt(0x00190099, 42);

check('getString_default_absent_uses_default', String(bogusTag) == 'DEFAULT', String(bogusTag));
check('getInt_default_absent_uses_default',    bogusInt == 42,                String(bogusInt));
check('getInt_numFrames_fallback',             numFrames >= 1,                String(numFrames));

var sopClassElem = dicomObj.get(0x00080016);
if (sopClassElem != null) {
    check('element_tag_matches',    sopClassElem.tag() == 0x00080016, '0x' + sopClassElem.tag().toString(16));
    check('element_vr_nonempty',    nonEmpty(sopClassElem.vr()),      String(sopClassElem.vr()));
    check('element_length_gt_zero', sopClassElem.length() > 0,        'len=' + sopClassElem.length());
    check('element_valueAsString',  String(sopClassElem.getValueAsString(0)) == String(sopClassUID));
} else {
    check('element_sopClass_present', false, 'null');
}

check('absent_element_null', dicomObj.get(0x00190099) == null);

var pixelElem = dicomObj.get(0x7FE00010);
if (pixelElem != null) {
    var pixBytes = pixelElem.getBytes();
    check('pixelData_bytes_readable',     pixBytes != null, 'len=' + (pixBytes ? pixBytes.length : 'null'));
    check('pixelData_hasItems_queryable', typeof pixelElem.hasItems() == 'boolean');
} else {
    results['pixelData_present'] = 'ABSENT (expected on minimal fixtures)';
    logger.info(LOG_PREFIX + 'SKIP pixelData_present (absent on fixture)');
}

try {
    var slices = DICOMUtil.getSliceCount(connectorMessage);
    check('getSliceCount_noThrow', true, String(slices));
} catch (e) {
    check('getSliceCount_noThrow', false, String(e));
}

check('hasFileMetaInfo',   dicomObj.hasFileMetaInfo() === true);
check('bigEndian_boolean', typeof dicomObj.bigEndian() == 'boolean');

try {
    dicomObj.putString(0x00189004, 'CS', 'OIE_TEST_VALUE');
    check('putString_roundtrip',    String(dicomObj.getString(0x00189004)) == 'OIE_TEST_VALUE');
    check('remove_returnsElement',  dicomObj.remove(0x00189004) != null);
    check('remove_actuallyRemoves', dicomObj.getString(0x00189004) == null);
} catch (e) {
    check('mutation_supported', false, String(e));
}

try {
    var roundTripBytes = DICOMUtil.dicomObjectToByteArray(dicomObj);
    check('roundtrip_nonEmpty', roundTripBytes != null && roundTripBytes.length > 0, 'len=' + (roundTripBytes ? roundTripBytes.length : 'null'));
    var reparsed = DICOMUtil.byteArrayToDicomObject(roundTripBytes, false);
    check('roundtrip_reparse_patientName_stable', String(reparsed.getString(0x00100010)) == String(patientName));
    check('roundtrip_reparse_modality_stable',    String(reparsed.getString(0x00080060)) == String(modality));
} catch (e) {
    check('roundtrip_noThrow', false, String(e));
}

var backend = 'UNKNOWN';
try {
    var implClass = String(dicomObj.unwrap().getClass().getName());
    backend = implClass.indexOf('dcm4che3') >= 0 ? 'dcm4che5'
            : implClass.indexOf('dcm4che2') >= 0 ? 'dcm4che2' : 'UNKNOWN';
    channelMap.put('dicomTest.backendImplClass', implClass);
    logger.info(LOG_PREFIX + 'backend=' + backend + ' (impl=' + implClass + ')');
} catch (e) {
    logger.warn(LOG_PREFIX + 'backend detection failed: ' + e);
}
channelMap.put('dicomTest.backend', backend);

for (var key in results) {
    channelMap.put('dicomTest.' + key, String(results[key]));
}
channelMap.put('dicomTest.failureCount', String(failures.length));
channelMap.put('dicomTest.summary', failures.length == 0 ? 'ALL PASS' : ('FAILURES: ' + failures.join('; ')));

channelMap.put('patientName', String(patientName));
channelMap.put('patientID',   String(patientID));
channelMap.put('modality',    String(modality));
channelMap.put('studyDate',   String(studyDate));
channelMap.put('numberOfFrames', String(numFrames));

logger.info(LOG_PREFIX + '=== END message=' + connectorMessage.getMessageId()
    + ' backend=' + backend
    + ' failures=' + failures.length
    + (failures.length == 0 ? ' result=ALL_PASS' : (' result=FAIL fails=[' + failures.join(' | ') + ']')) + ' ===');
