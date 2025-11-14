# Capability: Data Type Handling

**Category:** Healthcare Standards Support
**Primary Users:** Integration Engineers
**Related Components:** Data type plugins, serializers, parsers

---

## Overview

Data Type Handling provides parsing, serialization, validation, and transformation of healthcare message formats. OIE natively supports HL7 v2/v3, FHIR, DICOM, EDI/X12, NCPDP, XML, JSON, delimited text, and raw binary data.

---

## Features

### Feature 4.1: HL7 v2.x Support

**Description:**
Parse, generate, validate, and transform HL7 version 2.x messages (pipe-and-hat format). Supports all HL7 v2.x versions from 2.1 to 2.8.

**How to Use:**

1. **Set Channel Data Type to HL7v2:**
   - Source data type: HL7 v2.x
   - Channel automatically parses incoming HL7 messages

2. **Access HL7 Data in JavaScript:**
   ```javascript
   // Access segments and fields
   var patientId = msg['PID']['PID.3']['PID.3.1'].toString();
   var patientName = msg['PID']['PID.5']['PID.5.1'].toString();
   var dob = msg['PID']['PID.7']['PID.7.1'].toString();

   // Access repeating fields
   var allergies = msg['AL1'];
   for (var i = 0; i < allergies.length(); i++) {
     var allergyType = allergies[i]['AL1.2']['AL1.2.1'].toString();
   }

   // Modify message
   msg['PID']['PID.3']['PID.3.1'] = 'NEW123';
   ```

3. **Create HL7 Message:**
   ```javascript
   var adt = createHL7Message('ADT', 'A01', '2.5');
   adt['MSH']['MSH.3']['MSH.3.1'] = 'SENDING_APP';
   adt['MSH']['MSH.4']['MSH.4.1'] = 'SENDING_FAC';
   adt['PID']['PID.3']['PID.3.1'] = patientId;
   adt['PID']['PID.5']['PID.5.1'] = lastName;
   adt['PID']['PID.5']['PID.5.2'] = firstName;

   msg = adt.toXML();
   ```

4. **Generate HL7 ACK:**
   ```javascript
   var ack = createHL7Message('ACK');
   ack['MSA']['MSA.1'] = 'AA'; // Application Accept
   ack['MSA']['MSA.2'] = sourceMap.get('messageControlId');
   ack['MSA']['MSA.3'] = 'Message processed successfully';
   ```

**Features:**
- **Parsing:** ER7 (pipe-delimited) and XML formats
- **Versions:** 2.1, 2.2, 2.3, 2.3.1, 2.4, 2.5, 2.5.1, 2.6, 2.7, 2.8
- **Message Types:** ADT, ORM, ORU, DFT, SIU, MDM, and all standard types
- **Custom Segments:** Support for Z-segments
- **Batch Messages:** FHS/BHS batch file handling
- **ACK Generation:** Automatic acknowledgment creation
- **Validation:** Structure and conformance validation
- **Encoding Characters:** Configurable field/component/repeat/escape characters

**How to Test:**
- Send standard HL7 ADT^A01 message
- Verify parsing of all segments
- Test with custom Z-segments
- Test with non-standard field separators
- Send batch file (multiple messages)
- Validate HL7 structure (missing required fields should error)
- Test ACK generation
- Test with different HL7 versions
- Send malformed HL7 (verify error handling)

**Expected Behavior:**
- **Automatic Parsing:** HL7 ER7 format converted to XML
- **Field Access:** XPath-like access to segments/fields
- **Type Safety:** Proper handling of repeating fields
- **Encoding:** Preserve special characters (escape sequences)
- **Validation:** Optional strict validation against HL7 specifications
- **ACK/NAK:** Automatic acknowledgment support
- **Performance:** Fast parsing using HAPI HL7 library

**Code Location:** `/server/src/com/mirth/connect/plugins/datatypes/hl7v2/`, HAPI HL7 library

---

### Feature 4.2: HL7 v3 Support

**Description:**
Parse and generate HL7 Version 3 XML messages including CDA (Clinical Document Architecture) documents.

**How to Use:**

1. **Set Channel Data Type to HL7v3:**
   - Source data type: HL7 v3
   - Messages automatically parsed as XML

2. **Access HL7v3 Data:**
   ```javascript
   // HL7v3 is XML-based
   var doc = new XML(msg);
   var patientId = doc..id.@extension.toString();
   var patientName = doc..name.given.toString();
   ```

3. **CDA Document Processing:**
   ```javascript
   // Process Clinical Document Architecture
   var cda = new XML(msg);
   var title = cda..title.toString();
   var sections = cda..section;
   ```

**Features:**
- **XML-Based:** Native XML format
- **CDA Support:** Clinical Document Architecture (CDA R2)
- **Schema Validation:** Validate against HL7v3 schemas
- **Namespace Handling:** Proper namespace support
- **RIM-Based:** Reference Information Model compliance

**How to Test:**
- Send HL7v3 XML message
- Verify XML parsing
- Test CDA document processing
- Validate against HL7v3 schema
- Test namespace handling

**Expected Behavior:**
- **XML Parsing:** Full XML structure preserved
- **Namespaces:** Namespace-aware processing
- **Validation:** Schema validation (optional)
- **CDA Support:** Specific CDA document handling

**Code Location:** `/server/src/com/mirth/connect/plugins/datatypes/hl7v3/`

---

### Feature 4.3: DICOM Support

**Description:**
Parse DICOM medical imaging files and extract metadata. Support for DICOM protocol communication.

**How to Use:**

1. **Set Channel Data Type to DICOM:**
   - Source data type: DICOM
   - Incoming DICOM files automatically parsed

2. **Access DICOM Data:**
   ```javascript
   // Access DICOM tags
   var patientName = msg['00100010']['00100010.1'].toString(); // Patient Name
   var patientId = msg['00100020']['00100020.1'].toString();   // Patient ID
   var studyDate = msg['00080020']['00080020.1'].toString();   // Study Date
   var modality = msg['00080060']['00080060.1'].toString();    // Modality

   // Access image data
   var pixelData = msg['7FE00010']; // Pixel Data tag
   ```

3. **DICOM Metadata Extraction:**
   ```javascript
   // Extract relevant metadata for database storage
   var metadata = {
     patientId: msg['00100020']['00100020.1'].toString(),
     studyInstanceUID: msg['0020000D']['0020000D.1'].toString(),
     seriesInstanceUID: msg['0020000E']['0020000E.1'].toString(),
     sopInstanceUID: msg['00080018']['00080018.1'].toString()
   };
   ```

**Features:**
- **Tag Parsing:** Read DICOM tag values
- **Image Data:** Access pixel data
- **Transfer Syntaxes:** Multiple compression formats
- **Metadata Extraction:** Patient, study, series information
- **Protocol Support:** DICOM C-STORE via DIMSE connector

**How to Test:**
- Send DICOM file through channel
- Verify tag extraction
- Test with different modalities (CT, MR, US, etc.)
- Test compressed vs. uncompressed
- Verify image data accessible
- Test DICOM protocol C-STORE

**Expected Behavior:**
- **Tag Access:** Dictionary-based tag access
- **Binary Data:** Proper handling of image pixel data
- **Metadata:** Complete DICOM header parsing
- **Performance:** Efficient parsing of large image files

**Code Location:** `/server/src/com/mirth/connect/plugins/datatypes/dicom/`, DCM4CHE2 library

---

### Feature 4.4: EDI/X12 Support

**Description:**
Parse and generate EDI (Electronic Data Interchange) messages including X12 transactions for insurance claims and eligibility.

**How to Use:**

1. **Set Channel Data Type to EDI/X12:**
   - Source data type: EDI
   - Segment-based parsing

2. **Access EDI Data:**
   ```javascript
   // Access EDI segments
   var claim = msg['CLM'];
   var claimId = claim['CLM01'].toString();
   var claimAmount = claim['CLM02'].toString();

   // Loop through segments
   var segments = msg['2300']; // Claim information loop
   ```

3. **Transaction Types:**
   - 270/271: Eligibility inquiry/response
   - 276/277: Claim status inquiry/response
   - 278: Health service review
   - 834: Benefit enrollment
   - 835: Payment/remittance advice
   - 837: Healthcare claim (professional, institutional, dental)

**Features:**
- **X12 Support:** HIPAA-compliant transaction sets
- **Segment Parsing:** ISA, GS, ST, SE, GE, IEA envelopes
- **Loop Handling:** Hierarchical loops (2000, 2300, etc.)
- **Element Access:** Segment element access
- **Validation:** Segment validation and requirements

**How to Test:**
- Send X12 837 claim transaction
- Verify segment parsing
- Test loop navigation
- Send 270 eligibility inquiry
- Test validation rules
- Verify envelope handling (ISA/GS/ST)

**Expected Behavior:**
- **Segment Delimiter:** Configurable delimiters
- **Loop Navigation:** Access hierarchical loops
- **Validation:** Required segment checking
- **Envelopes:** Proper ISA/GS/ST handling

**Code Location:** `/server/src/com/mirth/connect/plugins/datatypes/edi/`

---

### Feature 4.5: NCPDP Support

**Description:**
Parse and generate NCPDP SCRIPT messages for pharmacy claims and e-prescribing.

**How to Use:**

1. **Set Channel Data Type to NCPDP:**
   - Source data type: NCPDP
   - Pharmacy message parsing

2. **Access NCPDP Data:**
   ```javascript
   // Access NCPDP fields
   var prescriptionNumber = msg['prescription-number'].toString();
   var ndc = msg['product-id']['ndc'].toString();
   ```

**Features:**
- **SCRIPT Standard:** NCPDP SCRIPT 10.6, 2017071
- **Message Types:** NewRx, RefillRequest, RxChangeRequest, etc.
- **Field Access:** Structured field navigation

**How to Test:**
- Send NCPDP prescription message
- Verify field extraction
- Test different message types

**Expected Behavior:**
- **Standard Compliance:** NCPDP SCRIPT specification
- **Field Parsing:** Proper field extraction
- **Message Types:** Support for all SCRIPT message types

**Code Location:** `/server/src/com/mirth/connect/plugins/datatypes/ncpdp/`

---

### Feature 4.6: XML Support

**Description:**
Parse and transform XML documents with XPath querying, namespace support, and XSLT transformations.

**How to Use:**

1. **Set Channel Data Type to XML:**
   - Source data type: XML
   - Full XML DOM access

2. **Access XML Data:**
   ```javascript
   // Parse XML
   var doc = new XML(msg);

   // XPath queries
   var patients = doc..patient;
   for each (var patient in patients) {
     var id = patient.id.toString();
     var name = patient.name.toString();
   }

   // Attributes
   var status = doc.@status.toString();

   // Namespaces
   var ns = new Namespace("http://hl7.org/fhir");
   var resource = doc.ns::resource;
   ```

3. **Create XML:**
   ```javascript
   var xml = <patient>
     <id>{patientId}</id>
     <name>{patientName}</name>
   </patient>;

   msg = xml.toXMLString();
   ```

**Features:**
- **DOM Parsing:** Full XML document object model
- **XPath:** Query XML with XPath expressions
- **Namespaces:** Namespace-aware processing
- **Validation:** XML schema validation
- **XSLT:** Transform with XSLT stylesheets
- **Pretty Print:** Formatted XML output

**How to Test:**
- Send XML document
- Query with XPath
- Test namespace handling
- Validate against XSD schema
- Transform with XSLT
- Create new XML documents

**Expected Behavior:**
- **Standards-Compliant:** XML 1.0/1.1 support
- **Namespace Aware:** Proper namespace handling
- **Validation:** Optional schema validation
- **Performance:** Efficient XML parsing

**Code Location:** `/server/src/com/mirth/connect/plugins/datatypes/xml/`, JDOM2 library

---

### Feature 4.7: JSON Support

**Description:**
Parse and generate JSON documents with full object model access.

**How to Use:**

1. **Set Channel Data Type to JSON:**
   - Source data type: JSON
   - Automatic JSON parsing

2. **Access JSON Data:**
   ```javascript
   // Parse JSON
   var obj = JSON.parse(msg);
   var patientId = obj.patient.id;
   var name = obj.patient.name.given[0];

   // Modify JSON
   obj.patient.status = 'active';

   // Generate JSON
   msg = JSON.stringify(obj, null, 2); // Pretty print
   ```

3. **FHIR JSON:**
   ```javascript
   // Access FHIR resources
   var resource = JSON.parse(msg);
   var resourceType = resource.resourceType; // Patient, Observation, etc.
   var identifier = resource.identifier[0].value;
   ```

**Features:**
- **JSON Parsing:** Full JSON object model
- **FHIR Support:** FHIR-compatible JSON
- **Nested Objects:** Deep object navigation
- **Arrays:** Array manipulation
- **Conversion:** JSON ↔ XML conversion

**How to Test:**
- Send JSON document
- Parse and access nested objects
- Modify JSON structure
- Test array handling
- Convert JSON to XML
- Send FHIR JSON resource

**Expected Behavior:**
- **Standards-Compliant:** RFC 8259 (JSON)
- **FHIR Compatible:** FHIR R4 JSON format
- **Bidirectional:** JSON ↔ XML conversion
- **Performance:** Fast JSON parsing (Jackson library)

**Code Location:** `/server/src/com/mirth/connect/plugins/datatypes/json/`, Jackson library

---

### Feature 4.8: Delimited Text Support

**Description:**
Parse and generate delimited text files (CSV, TSV, custom delimiters).

**How to Use:**

1. **Set Channel Data Type to Delimited:**
   - Source data type: Delimited Text
   - Configure delimiter, quote character

2. **Configuration:**
   - **Column Delimiter:** Comma, tab, pipe, custom
   - **Quote Character:** Double quote, single quote, none
   - **Escape Character:** Backslash, double quote
   - **Header Row:** Use first row for column names
   - **Column Names:** Define column names manually

3. **Access Delimited Data:**
   ```javascript
   // Access by column name
   var patientId = msg['patient_id'].toString();
   var firstName = msg['first_name'].toString();
   var lastName = msg['last_name'].toString();

   // Access by index
   var column0 = msg['column0'].toString();
   ```

4. **Create Delimited Output:**
   ```javascript
   // Set delimiter in channel properties
   // Data automatically formatted
   ```

**How to Test:**
- Send CSV file with header row
- Verify column access by name
- Send TSV file
- Test custom delimiters
- Test quoted fields with delimiters inside
- Test escape characters

**Expected Behavior:**
- **RFC 4180:** CSV standard compliance
- **Flexible:** Custom delimiters and quotes
- **Header Support:** Column names from first row
- **Escape Handling:** Proper quote/escape processing
- **Large Files:** Efficient parsing

**Code Location:** `/server/src/com/mirth/connect/plugins/datatypes/delimited/`

---

### Feature 4.9: Raw Data Support

**Description:**
Handle binary or unstructured data without parsing. Pass-through mode for unknown formats.

**How to Use:**

1. **Set Channel Data Type to Raw:**
   - Source data type: Raw
   - No parsing, message treated as byte array

2. **Access Raw Data:**
   ```javascript
   // msg contains raw string/bytes
   var data = msg;

   // Convert to Base64
   var base64 = FileUtil.encode(data);

   // Binary operations
   var bytes = new java.lang.String(msg).getBytes();
   ```

**Features:**
- **Binary Safe:** Preserve binary data
- **No Parsing:** Pass-through mode
- **Encoding:** Handle any character encoding
- **Large Data:** Efficient handling of large payloads

**How to Test:**
- Send binary file
- Verify data preserved unchanged
- Test large binary files
- Test different encodings

**Expected Behavior:**
- **No Transformation:** Data unchanged
- **Binary Safe:** No corruption
- **Performance:** Minimal overhead

**Code Location:** `/server/src/com/mirth/connect/plugins/datatypes/raw/`

---

### Feature 4.10: Data Type Conversion

**Description:**
Convert between different data types automatically or programmatically.

**How to Use:**

1. **Automatic Conversion:**
   - Set source data type (e.g., HL7v2)
   - Set destination data type (e.g., JSON)
   - System converts automatically

2. **Programmatic Conversion:**
   ```javascript
   // Get serializer
   var hl7Serializer = SerializerFactory.getSerializer('HL7V2');
   var jsonSerializer = SerializerFactory.getSerializer('JSON');

   // Convert HL7 to normalized XML
   var xml = hl7Serializer.toXML(msg);

   // Convert XML to JSON
   var json = jsonSerializer.fromXML(xml);
   ```

3. **Supported Conversions:**
   - HL7v2 ↔ XML ↔ JSON
   - DICOM → XML
   - EDI → XML
   - CSV → XML
   - Any → Raw

**How to Test:**
- Create channel with HL7v2 source and JSON destination
- Send HL7 message
- Verify JSON output preserves data
- Test bidirectional conversion
- Test all data type combinations

**Expected Behavior:**
- **Lossless:** Data preserved during conversion (where possible)
- **Automatic:** Transparent conversion based on data types
- **Flexible:** Programmatic control available
- **Performance:** Efficient conversion

**Code Location:** Serializer framework, data type plugins

---

## Integration Points

- **Channel Management:** Data types configured per channel
- **Message Processing:** Transformations operate on parsed data
- **Connectors:** Connectors serialize/deserialize based on data type

---

## Performance Considerations

- **Parsing Overhead:** Complex formats (HL7, XML) have parsing cost
- **Large Messages:** Memory usage for large XML/JSON documents
- **Validation:** Validation adds processing time
- **Caching:** Parsed messages cached where possible

---

## Best Practices

1. **Choose Appropriate Type:** Use specific type (HL7v2) vs. generic (XML)
2. **Validation:** Enable for critical interfaces
3. **Error Handling:** Handle parsing errors gracefully
4. **Performance:** Profile with real message sizes
5. **Testing:** Test with actual message samples
6. **Custom Types:** Develop plugins for proprietary formats

---

## Troubleshooting

**Parsing Errors:**
- Check message format matches data type
- Verify encoding (UTF-8, etc.)
- Review parsing error messages
- Test with minimal valid message

**Conversion Issues:**
- Verify source/destination types compatible
- Check for data loss warnings
- Test bidirectional conversion

---

## Related Documentation

- [Message Processing](02-message-processing.md)
- [Connector Framework](03-connector-framework.md)
- [ARCHITECTURE.md](../ARCHITECTURE.md)
