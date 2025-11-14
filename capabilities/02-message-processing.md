# Capability: Message Processing

**Category:** Core Integration Capability
**Primary Users:** Integration Engineers
**Related Components:** Donkey Engine, FilterTransformerExecutor, ScriptController

---

## Overview

Message Processing provides transformation, filtering, routing, and validation capabilities for healthcare messages. Using JavaScript, XSLT, visual mapping, and rule-based filtering, messages can be converted between formats, enriched with additional data, validated against business rules, and routed to appropriate destinations.

---

## Features

### Feature 2.1: JavaScript Transformation

**Description:**
Transform message content using JavaScript (ES6) with access to message data, global variables, and Java utilities. JavaScript transformers can modify message structure, calculate values, call external systems, and implement complex business logic.

**How to Use:**

1. **In Channel Source Transformer:**
   ```javascript
   // Access incoming message
   var inbound = msg;

   // Transform HL7 to JSON
   var patient = {
     id: msg['PID']['PID.3']['PID.3.1'].toString(),
     name: msg['PID']['PID.5']['PID.5.1'].toString(),
     dob: msg['PID']['PID.7']['PID.7.1'].toString()
   };

   // Set output
   msg = JSON.stringify(patient);
   ```

2. **In Destination Transformer:**
   ```javascript
   // Access connector map
   var patientId = connectorMap.get('patientId');

   // Build HL7 message
   var hl7 = createHL7Message('ADT', 'A01');
   hl7['PID']['PID.3']['PID.3.1'] = patientId;

   msg = hl7.toXML();
   ```

3. **Available Objects:**
   - `msg` - Message content (read/write)
   - `channelMap` - Channel-level variables (shared across all messages)
   - `connectorMap` - Connector-level variables (per destination)
   - `sourceMap` - Source connector variables
   - `globalMap` - Global server variables (shared across all channels)
   - `logger` - Logging utility
   - `alerts` - Alert sending
   - `router` - Message routing control

**How to Test:**
- Create simple transformation (e.g., convert field to uppercase)
- Verify transformed output matches expected result
- Test with invalid input data
- Test error handling (try/catch blocks)
- Verify access to maps (channelMap, connectorMap, etc.)
- Test JavaScript syntax errors (should show clear error message)
- Performance test with large messages
- Test calling Java classes from JavaScript

**Expected Behavior:**
- JavaScript executes in Mozilla Rhino engine (ES6 support)
- Scripts compile once and cache for performance
- Script errors halt message processing and log to error log
- Timeout protection prevents infinite loops (configurable)
- Access to full Java classpath
- Variable scoping: local to script, connector, channel, or global
- Return value becomes transformed message
- Console.log() writes to server log
- Exceptions are caught and logged with stack trace

**Code Location:** `FilterTransformerExecutor.java`, Rhino engine integration

---

### Feature 2.2: Message Filtering

**Description:**
Filter messages based on content, metadata, or business rules. Filtered messages are not processed by destinations and are marked as "FILTERED" in message history.

**How to Use:**

1. **JavaScript Rule:**
   ```javascript
   // Filter out test patients
   if (msg['PID']['PID.3']['PID.3.1'].toString().startsWith('TEST')) {
     return false; // Filter out
   }
   return true; // Process message
   ```

2. **Rule Builder (Visual):**
   - Define rules using dropdown menus
   - Conditions: equals, contains, starts with, regex, etc.
   - Combine rules with AND/OR logic

3. **Destination Set Filter:**
   ```javascript
   // Route to specific destinations only
   destinationSet.removeAll();
   destinationSet.add('1'); // Include destination 1
   destinationSet.add('3'); // Include destination 3
   // Destinations 2, 4+ are filtered
   ```

**Filter Locations:**
- **Source Filter:** Filters before any processing (most efficient)
- **Source Transformer Filter:** Filters after source transformation
- **Destination Filter:** Filters per destination (independent filtering)

**How to Test:**
- Create filter rule and send matching message (verify filtered)
- Send non-matching message (verify processed)
- Test complex boolean logic (AND/OR combinations)
- Test filter performance with high message volume
- Verify filtered messages appear in message browser with FILTERED status
- Test destination set filtering (verify only selected destinations receive)

**Expected Behavior:**
- Filtered messages do not reach destinations
- Filtered status recorded in database
- Filter statistics increment filtered count
- Filtered messages visible in message browser for troubleshooting
- Filter rules evaluated in order defined
- Source filters prevent unnecessary processing (performance benefit)
- Destination filters allow per-destination routing decisions

**Code Location:** `FilterTransformerExecutor.java`, rule plugin implementations

---

### Feature 2.3: XSLT Transformation

**Description:**
Transform XML messages using XSLT stylesheets for complex XML-to-XML transformations.

**How to Use:**

1. **XSLT Transformer Step:**
   ```xml
   <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
     <xsl:template match="/">
       <output>
         <patientId>
           <xsl:value-of select="//PID.3.1"/>
         </patientId>
       </output>
     </xsl:template>
   </xsl:stylesheet>
   ```

2. **In Channel Transformer:**
   - Add "XSLT Step" transformer
   - Paste XSLT stylesheet
   - Configure XSLT factory (Saxon, Xalan)
   - Map input message to XSLT

**XSLT Features:**
- XSLT 1.0 and 2.0 support (depending on processor)
- XPath expressions for data extraction
- XSLT parameters (pass variables to stylesheet)
- Output serialization (XML, HTML, text)

**How to Test:**
- Transform simple XML document
- Verify output structure matches XSLT template
- Test with invalid XML input
- Test with XSLT syntax errors
- Test XSLT parameters
- Performance test with large XML documents
- Test namespace handling

**Expected Behavior:**
- XSLT compiles and caches for performance
- XSLT errors reported with line numbers
- Namespace-aware transformation
- Supports XSLT functions and extensions
- Output encoding configurable
- Thread-safe XSLT execution

**Code Location:** XSLT step plugin, transformer framework

---

### Feature 2.4: Message Builder (Template-Based)

**Description:**
Build new messages using templates with variable substitution. Useful for creating outbound messages from scratch or from extracted data.

**How to Use:**

1. **HL7 Message Builder:**
   ```
   MSH|^~\&|SENDING_APP|SENDING_FAC|RECEIVING_APP|RECEIVING_FAC|${DATE}||ADT^A01|${MESSAGEID}|P|2.5
   PID|||${patientId}||${lastName}^${firstName}||${dob}|${gender}
   ```

2. **XML Message Builder:**
   ```xml
   <Patient>
     <Id>${patientId}</Id>
     <Name>${firstName} ${lastName}</Name>
     <DOB>${dob}</DOB>
   </Patient>
   ```

3. **Variable Substitution:**
   - `${variableName}` replaced with value from connector/channel map
   - JavaScript expressions: `${= new Date().getTime()}`
   - Velocity template syntax for loops and conditionals

**How to Test:**
- Create template with variables
- Set variables in transformer before message builder
- Verify output contains substituted values
- Test with missing variables (default behavior)
- Test JavaScript expressions in templates
- Test loops and conditionals (Velocity syntax)

**Expected Behavior:**
- Variables substituted at runtime
- Missing variables: blank string or error (configurable)
- JavaScript expressions evaluated
- Velocity syntax supported (if enabled)
- HTML/XML encoding options
- Template caching for performance

**Code Location:** Message Builder plugin, Velocity integration

---

### Feature 2.5: Data Type Conversion

**Description:**
Convert messages between different healthcare data formats: HL7 v2 ↔ HL7 v3 ↔ XML ↔ JSON ↔ DICOM ↔ EDI ↔ delimited.

**How to Use:**

1. **Set Channel Data Type:**
   - Source data type: Format of incoming message
   - Destination data type: Format of outgoing message
   - System automatically converts between formats

2. **Example Conversions:**
   - HL7v2 → XML (normalized HL7 XML format)
   - HL7v2 → JSON (for FHIR compatibility)
   - CSV → HL7v2 (using field mappings)
   - XML → JSON (structure-preserving)

3. **Manual Conversion:**
   ```javascript
   // In transformer
   var xml = SerializerFactory.getSerializer('HL7V2').toXML(msg);
   var json = SerializerFactory.getSerializer('JSON').fromXML(xml);
   ```

**Supported Conversions:**

| From → To | XML | JSON | HL7v2 | HL7v3 | DICOM | EDI | Delimited | Raw |
|-----------|-----|------|-------|-------|-------|-----|-----------|-----|
| **XML** | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **JSON** | ✓ | ✓ | ✓ | ✓ | ○ | ○ | ✓ | ✓ |
| **HL7v2** | ✓ | ✓ | ✓ | ○ | ○ | ○ | ○ | ✓ |
| **HL7v3** | ✓ | ✓ | ○ | ✓ | ○ | ○ | ○ | ✓ |
| **DICOM** | ✓ | ○ | ○ | ○ | ✓ | ○ | ○ | ✓ |
| **EDI** | ✓ | ○ | ○ | ○ | ○ | ✓ | ○ | ✓ |
| **Delimited** | ✓ | ✓ | ○ | ○ | ○ | ○ | ✓ | ✓ |
| **Raw** | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ✓ |

✓ = Automatic conversion
○ = Custom scripting required

**How to Test:**
- Send HL7v2 message to channel with JSON data type
- Verify conversion maintains data fidelity
- Test bidirectional conversion (A→B→A should equal original)
- Test with complex nested structures
- Test with special characters and encoding
- Measure conversion performance

**Expected Behavior:**
- Lossless conversion (where possible)
- Automatic encoding handling (UTF-8, etc.)
- Namespace preservation (XML/HL7v3)
- Segment order maintained (HL7v2)
- Validation during conversion (optional)
- Clear error messages for unsupported conversions

**Code Location:** Data type plugins, SerializerFactory

---

### Feature 2.6: Message Validation

**Description:**
Validate messages against schemas, business rules, or custom validation logic to ensure data quality and compliance.

**How to Use:**

1. **HL7v2 Validation:**
   ```javascript
   // Enable validation in channel settings
   // Or manual validation:
   try {
     validate(msg, '2.5', 'ADT_A01');
   } catch (e) {
     logger.error('Validation failed: ' + e.message);
     return false;
   }
   ```

2. **XML Schema Validation:**
   ```javascript
   var schema = FileUtil.read('schemas/patient.xsd');
   validateXMLSchema(msg, schema);
   ```

3. **Custom Validation Rules:**
   ```javascript
   // Check required fields
   if (!msg['PID']['PID.3']['PID.3.1'] || msg['PID']['PID.3']['PID.3.1'].toString() === '') {
     throw new Error('Patient ID is required');
   }

   // Validate format
   if (!/^\d{9}$/.test(msg['PID']['PID.3']['PID.3.1'].toString())) {
     throw new Error('Invalid Patient ID format');
   }
   ```

**Validation Types:**
- **Structural:** Message structure matches specification
- **Data Type:** Field values match expected types (date, number, etc.)
- **Business Rules:** Custom validation logic
- **Schema:** XML/JSON schema validation
- **Vocabulary:** Codes match allowed value sets

**How to Test:**
- Send valid message (should pass)
- Send message with missing required field (should fail)
- Send message with invalid field format (should fail)
- Send message with invalid code values (should fail)
- Test validation error messages are clear
- Test validation performance impact

**Expected Behavior:**
- Validation errors halt processing (unless configured to continue)
- Clear error messages with field paths
- Validation results logged
- Invalid messages marked as ERROR in message browser
- Validation can be warning-only (log but continue)
- Performance impact minimal (compiled validators cached)

**Code Location:** Data type validators, validation utilities

---

### Feature 2.7: Message Routing and Broadcasting

**Description:**
Route messages to multiple destinations simultaneously (broadcast) or conditionally based on content or rules.

**How to Use:**

1. **Broadcast (Default):**
   - Message sent to all configured destinations in parallel
   - Each destination processes independently

2. **Conditional Routing:**
   ```javascript
   // In source transformer
   if (msg['PID']['PID.3']['PID.3.1'].toString().startsWith('A')) {
     destinationSet.removeAll();
     destinationSet.add('1'); // Route to destination 1 only
   } else {
     destinationSet.removeAll();
     destinationSet.add('2'); // Route to destination 2 only
   }
   ```

3. **Sequential Routing:**
   - Configure destination order
   - Each destination waits for previous to complete
   - Use for dependent operations

**Routing Patterns:**

- **Broadcast:** 1 → N (parallel)
- **Conditional:** 1 → 1 of N (based on rules)
- **Sequential:** 1 → N (ordered)
- **Round-robin:** Distribute across destinations
- **Priority:** Primary destination with fallback

**How to Test:**
- Configure multiple destinations
- Send message and verify all destinations receive (broadcast)
- Implement conditional routing and test both paths
- Test sequential routing order
- Test destination failure scenarios (one fails, others continue)
- Measure routing performance

**Expected Behavior:**
- Parallel destinations process simultaneously
- Destination independence (one failure doesn't affect others)
- Sequential destinations wait for previous completion
- destinationSet changes apply immediately
- Routing decisions logged
- Statistics track per-destination metrics

**Code Location:** `Channel.java`, routing logic in Donkey engine

---

### Feature 2.8: Batch Message Processing

**Description:**
Process batch messages (multiple messages in single payload) by splitting into individual messages or processing as a group.

**How to Use:**

1. **HL7 Batch:**
   ```
   FHS|...
   BHS|...
   MSH|...
   PID|...
   MSH|...
   PID|...
   BTS|...
   FTS|...
   ```
   - Enable batch processing in channel
   - Each message processed individually

2. **Custom Batch Splitting:**
   ```javascript
   // In preprocessor
   var messages = msg.split('\n');
   for each (var message in messages) {
     router.routeMessage(channelId, message);
   }
   return false; // Don't process original
   ```

3. **Batch Properties:**
   - Batch separator (character/regex)
   - Batch headers/footers
   - Processing mode (individual vs. batch)

**How to Test:**
- Send HL7 batch file with multiple messages
- Verify each message processed individually
- Test batch statistics (count, errors)
- Test batch with some invalid messages
- Test batch commit/rollback
- Performance test with large batches

**Expected Behavior:**
- Batch automatically split into individual messages
- Each message has unique message ID
- Batch headers/footers removed
- Individual message statistics tracked
- Errors in one message don't affect others
- Original batch preserved in message content

**Code Location:** Batch adaptor plugins, BatchMessageProcessor

---

### Feature 2.9: Response Handling and Transformation

**Description:**
Process responses from destination systems, transform them, and return to source system.

**How to Use:**

1. **Response Transformer:**
   ```javascript
   // In destination response transformer
   var response = msg; // Response from destination

   // Build HL7 ACK
   var ack = createHL7Message('ACK');
   ack['MSA']['MSA.1'] = 'AA'; // Application Accept
   ack['MSA']['MSA.2'] = sourceMap.get('messageControlId');

   msg = ack.toXML();
   ```

2. **Response Routing:**
   - Configure response destination
   - Route response back to source
   - Route response to different destination
   - Transform response before returning

3. **Response Types:**
   - **HTTP:** HTTP status codes and body
   - **TCP/MLLP:** HL7 ACK/NAK
   - **Async:** Store response for later retrieval
   - **None:** Fire-and-forget

**How to Test:**
- Send message and verify response received
- Test response transformation
- Test HL7 ACK generation
- Test HTTP status codes
- Test timeout scenarios (no response)
- Test error responses

**Expected Behavior:**
- Responses processed in dedicated transformer
- Response timeout configurable
- Multiple destination responses can be aggregated
- Source receives final response
- Response content stored in message
- Response errors logged separately

**Code Location:** Response transformer logic, connector response handling

---

### Feature 2.10: Global Variables and Maps

**Description:**
Share data across messages, channels, and server runtime using global maps and variables.

**How to Use:**

1. **Channel Map (channel scope):**
   ```javascript
   // Set variable visible to all messages in channel
   channelMap.put('lastPatientId', patientId);

   // Get variable
   var lastId = channelMap.get('lastPatientId');
   ```

2. **Global Map (server scope):**
   ```javascript
   // Set variable visible to all channels
   globalMap.put('sharedCounter', counter);

   // Get variable
   var counter = globalMap.get('sharedCounter') || 0;
   ```

3. **Connector Map (connector scope):**
   ```javascript
   // Set variable visible within connector processing
   connectorMap.put('destinationResponse', response);
   ```

4. **Source Map (source scope):**
   ```javascript
   // Set in source transformer
   sourceMap.put('originalMessageId', messageId);

   // Access in destination transformer
   var originalId = sourceMap.get('originalMessageId');
   ```

**Map Characteristics:**

| Map | Scope | Persistence | Thread-Safe | Use Case |
|-----|-------|-------------|-------------|----------|
| `sourceMap` | Message | No | N/A | Pass data source → destinations |
| `connectorMap` | Message+Connector | No | N/A | Destination-specific data |
| `channelMap` | Channel | Yes (optional) | Yes | Channel-level caching, counters |
| `globalMap` | Server | Yes | Yes | Server-wide shared data |

**How to Test:**
- Set variable in source, access in destination
- Test channel map persistence across messages
- Test global map visibility across channels
- Test concurrent access (thread safety)
- Test map viewer in UI
- Clear maps and verify reset

**Expected Behavior:**
- Maps are key-value stores (string keys, any value)
- Thread-safe concurrent access
- Optional persistence (survives restart)
- Map viewer shows contents for debugging
- Large maps may impact performance
- Maps cleared on deployment (configurable)

**Code Location:** Map implementations in Donkey, GlobalMapPlugin

---

## Integration Points

- **Channel Management:** Messages processed within channels
- **Connector Framework:** Source provides input, destinations receive output
- **Data Type Handling:** Serialization/deserialization during processing
- **Message Storage:** Transformed messages stored at each step
- **API:** Messages can be injected via API

---

## Performance Considerations

- **JavaScript Performance:** Compiled scripts cached, avoid regex in loops
- **Large Messages:** Memory usage increases with message size
- **Batch Processing:** Memory overhead for splitting batches
- **Map Size:** Large global maps consume heap memory
- **XSLT Performance:** Compile and cache stylesheets

---

## Best Practices

1. **Error Handling:** Always use try/catch in JavaScript
2. **Logging:** Log important transformation decisions
3. **Testing:** Test transformations with real message samples
4. **Performance:** Profile transformations with large messages
5. **Reusability:** Use code templates for common logic
6. **Validation:** Validate early in pipeline
7. **Documentation:** Comment complex transformation logic
8. **Maps:** Use appropriate scope for variables

---

## Troubleshooting

**Transformation Errors:**
- Check server logs for JavaScript errors
- Use logger.info() to debug values
- Verify data type and structure
- Test transformation with sample data

**Performance Issues:**
- Profile JavaScript execution time
- Reduce regex complexity
- Cache computed values
- Optimize XSLT stylesheets

**Unexpected Results:**
- Use message browser to inspect transformed content
- Check variable scoping (sourceMap vs channelMap)
- Verify data type conversions
- Review filter logic

---

## Related Documentation

- [Channel Management](01-channel-management.md)
- [Data Type Handling](04-data-type-handling.md)
- [Message Storage & Queuing](09-message-storage-queuing.md)
- [ARCHITECTURE.md](../ARCHITECTURE.md) - Message processing pipeline
