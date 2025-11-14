# Capability: Connector Framework

**Category:** Core Integration Capability
**Primary Users:** Integration Engineers
**Related Components:** Connector implementations, SourceConnector, DestinationConnector

---

## Overview

The Connector Framework provides communication with external systems through multiple protocols and transports. Connectors handle the technical details of sending and receiving messages via HTTP, TCP, databases, files, email, and healthcare-specific protocols like DICOM and HL7/MLLP.

---

## Features

### Feature 3.1: HTTP Connector

**Description:**
Communicate with REST APIs, web services, and HTTP-based systems. Supports both receiving HTTP requests (listener) and sending HTTP requests (sender/dispatcher).

**How to Use:**

**HTTP Listener (Source):**
1. Configure HTTP Listener in source connector
2. Set listen port (e.g., 8081)
3. Configure context path (e.g., `/adt`)
4. Messages POST to `http://server:8081/adt` trigger channel

**Configuration:**
- **Host/Port:** IP address and port to listen on (0.0.0.0 for all interfaces)
- **Context Path:** URL path (e.g., `/api/patients`)
- **HTTP Methods:** GET, POST, PUT, DELETE, PATCH
- **Response:** Static content, JavaScript-generated, or from destination
- **Headers:** Custom response headers
- **Timeout:** Socket timeout (milliseconds)
- **Charset:** Character encoding (UTF-8, ISO-8859-1, etc.)

**HTTP Sender (Destination):**
```javascript
// URL can be dynamic
connectorMap.put('url', 'http://fhir-server.com/Patient/' + patientId);
```

**Configuration:**
- **URL:** Target URL (static or JavaScript expression)
- **Method:** GET, POST, PUT, DELETE, PATCH
- **Headers:** Static or JavaScript-generated headers
- **Query Parameters:** URL parameters
- **Request Body:** Message content or custom template
- **Authentication:** None, Basic, Digest, OAuth
- **Proxy:** HTTP proxy configuration
- **SSL/TLS:** Certificate validation, client certificates
- **Response Handling:** Parse as XML, JSON, or text

**How to Test:**

**Listener:**
```bash
curl -X POST http://localhost:8081/adt \
  -H "Content-Type: application/json" \
  -d '{"patientId":"12345"}'
```

**Sender:**
- Configure test destination with HTTP sender
- Send message through channel
- Verify HTTP request received at destination
- Test error scenarios (404, 500, timeout)
- Test SSL/TLS connections
- Test authentication methods

**Expected Behavior:**
- **Listener:** Accepts HTTP requests, returns configurable responses
- **Sender:** Sends HTTP requests, handles responses and errors
- **Thread Safety:** Multiple concurrent requests supported
- **Keep-Alive:** Connection reuse for performance
- **Timeouts:** Configurable read/write timeouts
- **Error Handling:** HTTP error codes trigger channel error handling
- **Redirects:** Automatic following (configurable)

**Code Location:** `HttpReceiver.java`, `HttpDispatcher.java`, `HttpReceiverProperties.java`

---

### Feature 3.2: TCP Connector

**Description:**
Low-level TCP socket communication with support for HL7 MLLP (Minimal Lower Layer Protocol) framing. Essential for HL7v2 integration.

**How to Use:**

**TCP Listener (Source):**
1. Configure TCP Listener on port (e.g., 6661)
2. Select transmission mode:
   - **MLLP:** HL7 framing (0x0B + message + 0x1C + 0x0D)
   - **Frame:** Custom start/end delimiters
   - **Raw:** No framing, accept everything

**Configuration:**
- **Mode:** Server (listen) or Client (connect)
- **Host/Port:** Listen address/port or remote address/port
- **Transmission Mode:** MLLP, frame, or raw
- **Start/End Bytes:** Custom frame delimiters (hex)
- **Keep Connection Open:** Reuse connection for multiple messages
- **Max Connections:** Connection pool size
- **Receive Timeout:** Socket read timeout
- **Buffer Size:** Receive buffer size

**TCP Sender (Destination):**
```javascript
// Send HL7 message over MLLP
// Automatically wrapped with 0x0B...0x1C0x0D
```

**Configuration:**
- **Remote Address/Port:** Destination server
- **Transmission Mode:** MLLP, frame, or raw
- **Keep Connection Open:** Connection pooling
- **Timeout:** Connection and socket timeout
- **Response Handling:** Wait for ACK/NAK

**How to Test:**

**Listener:**
```bash
# Send HL7 message with MLLP framing
echo -ne '\x0bMSH|^~\\&|TEST|||20250114000000||ADT^A01|123|P|2.5\x1c\x0d' | nc localhost 6661
```

**Sender:**
- Use HL7 simulator or test tool
- Send test message through channel
- Verify MLLP framing correct
- Test connection pooling (multiple messages)
- Test timeout scenarios
- Test keep-alive behavior

**Expected Behavior:**
- **MLLP Framing:** Automatic addition/removal of frame bytes
- **Connection Management:** Pool and reuse connections
- **Binary Safe:** Supports binary data
- **Blocking I/O:** Waits for complete message
- **Error Recovery:** Automatic reconnection on failure
- **ACK Handling:** HL7 ACK/NAK parsing and validation

**Code Location:** `TcpReceiver.java`, `TcpDispatcher.java`, `TcpReceiverProperties.java`

---

### Feature 3.3: Database (JDBC) Connector

**Description:**
Query databases (polling source) and execute SQL statements (destination). Supports all JDBC-compatible databases.

**How to Use:**

**Database Reader (Source):**
1. Configure database connection (driver, URL, credentials)
2. Write SELECT query
3. Set polling frequency (e.g., every 60 seconds)
4. Optionally UPDATE records after reading

**Configuration:**
- **Driver:** MySQL, PostgreSQL, Oracle, SQL Server, Derby, custom
- **URL:** JDBC connection string
- **Username/Password:** Database credentials
- **SQL Statement:** SELECT query (JavaScript-generated allowed)
- **Polling Interval:** Milliseconds between queries
- **Update Statement:** Mark records as processed (ONCE or EACH)
- **Fetch Size:** Result set fetch size
- **Keep Connection Open:** Connection pooling

**Example:**
```sql
SELECT patient_id, first_name, last_name, dob
FROM patients
WHERE processed = 0
ORDER BY created_date
LIMIT 100
```

**Database Writer (Destination):**
```javascript
// Set parameters in transformer
connectorMap.put('patientId', patientId);
connectorMap.put('firstName', firstName);
```

**SQL Template:**
```sql
INSERT INTO patient_results (patient_id, result, created_date)
VALUES (${patientId}, ${resultValue}, NOW())
```

**Configuration:**
- **SQL Statement:** INSERT, UPDATE, DELETE, or stored procedure
- **Parameters:** Bind variables from connector map
- **Transaction:** Commit/rollback on error
- **Batch:** Batch multiple statements

**How to Test:**

**Reader:**
- Insert test records into database
- Verify channel polls and processes records
- Check UPDATE statement marks records processed
- Test with empty result set
- Test with large result sets
- Test polling interval accuracy

**Writer:**
- Send message through channel
- Verify INSERT/UPDATE executed
- Check database for expected data
- Test SQL injection prevention (parameterized queries)
- Test transaction rollback on error
- Test batch performance

**Expected Behavior:**
- **Polling:** Executes query on schedule
- **Connection Pooling:** Reuses database connections
- **Parameterized Queries:** SQL injection prevention
- **Transaction Management:** Automatic commit/rollback
- **Error Handling:** Database errors logged, channel continues
- **Performance:** Fetch size optimization for large results

**Code Location:** `DatabaseReceiver.java`, `DatabaseDispatcher.java`, JDBC libraries

---

### Feature 3.4: File Connector

**Description:**
Read files from file systems (local, FTP, SFTP) and write files to destinations. Supports polling, filtering, and post-processing.

**How to Use:**

**File Reader (Source):**
1. Configure directory to monitor
2. Set file filter (glob or regex)
3. Configure polling frequency
4. Set post-processing action (delete, move, archive)

**Configuration:**
- **Scheme:** FILE (local), FTP, SFTP
- **Host/Port:** Remote server (for FTP/SFTP)
- **Username/Password:** Remote credentials
- **Directory:** Directory to monitor
- **File Filter:** Pattern (*.hl7, *.xml, regex)
- **Polling Interval:** Check frequency
- **File Age:** Minimum age before processing (avoid partial writes)
- **Recursive:** Include subdirectories
- **Sort By:** Name, size, date
- **Process Batch:** Files per poll
- **After Processing:** DELETE, MOVE, NONE
- **Move To Directory:** Destination for processed files
- **Error Directory:** Destination for failed files
- **Check File Age:** Minimum milliseconds since modification

**Example Configuration:**
- Directory: `/data/inbound/hl7/`
- Filter: `*.hl7`
- Polling: 10000ms (10 seconds)
- File Age: 5000ms (5 seconds)
- After Processing: MOVE
- Move To: `/data/archive/`

**File Writer (Destination):**
```javascript
// Set filename in transformer
connectorMap.put('filename', 'patient_' + patientId + '_' + new Date().getTime() + '.xml');
```

**Configuration:**
- **Scheme:** FILE, FTP, SFTP
- **Directory:** Destination directory
- **File Name:** Static or JavaScript template
- **Append:** Append to existing file vs. overwrite
- **Create Directory:** Create if not exists
- **Temp File:** Write to temp, then rename (atomic)
- **Binary Mode:** Binary vs. text
- **Encoding:** Character encoding

**How to Test:**

**Reader:**
```bash
# Create test file
echo "MSH|..." > /data/inbound/hl7/test001.hl7

# Verify:
# 1. File processed after polling interval
# 2. File moved to archive directory
# 3. Message appears in channel
```

**Writer:**
- Send message through channel
- Verify file created in destination directory
- Check file contents match expected output
- Test filename generation
- Test append mode
- Test FTP/SFTP connections
- Test directory creation

**Expected Behavior:**
- **Atomic Processing:** Files not processed until completely written
- **File Locking:** Avoid processing files being written
- **Error Handling:** Failed files moved to error directory
- **FTP/SFTP:** Secure file transfer with authentication
- **Recursive:** Processes subdirectories if configured
- **Sorting:** Processes files in specified order
- **Performance:** Batch processing for high volume

**Code Location:** `FileReceiver.java`, `FileDispatcher.java`, JSch library (SFTP)

---

### Feature 3.5: SMTP Connector (Email)

**Description:**
Send email notifications, alerts, or message content via SMTP. Supports HTML, attachments, and encryption.

**How to Use:**

**SMTP Sender (Destination Only):**
```javascript
// Set email properties in transformer
connectorMap.put('to', 'admin@hospital.org');
connectorMap.put('subject', 'Patient Admission: ' + patientName);
connectorMap.put('body', msg); // Message content as email body
```

**Configuration:**
- **SMTP Host:** Mail server address
- **SMTP Port:** 25 (plain), 465 (SSL), 587 (TLS)
- **Username/Password:** SMTP authentication
- **Encryption:** None, SSL, TLS, STARTTLS
- **From Address:** Sender email
- **To, CC, BCC:** Recipients (comma-separated or JavaScript)
- **Subject:** Email subject (static or JavaScript)
- **Body:** Plain text or HTML
- **Content Type:** text/plain or text/html
- **Attachments:** Static files or dynamic content
- **Reply-To:** Reply address
- **Custom Headers:** Additional email headers

**How to Test:**
- Configure test email destination
- Send message through channel
- Verify email received
- Test HTML formatting
- Test attachments
- Test authentication
- Test SSL/TLS encryption
- Test multiple recipients

**Expected Behavior:**
- **Authentication:** SMTP AUTH support
- **Encryption:** TLS/SSL for secure transmission
- **HTML Support:** Rich formatting in emails
- **Attachments:** Base64 encoding, MIME types
- **Error Handling:** SMTP errors logged
- **Template Support:** Dynamic subject/body from message content

**Code Location:** `SmtpDispatcher.java`, JavaMail library

---

### Feature 3.6: DICOM Connector

**Description:**
Send and receive medical images using DICOM protocol. Supports C-STORE operations for PACS integration.

**How to Use:**

**DICOM Listener (Source):**
1. Configure DICOM listener on port (e.g., 11112)
2. Set Application Entity (AE) Title
3. Receive C-STORE operations from modalities/PACS

**Configuration:**
- **Port:** DICOM listener port
- **AE Title:** Application entity title
- **Accepted Transfer Syntaxes:** Image compression formats
- **Storage Location:** Temporary storage for received images

**DICOM Sender (Destination):**
```javascript
// DICOM message sent to remote PACS
```

**Configuration:**
- **Remote Host/Port:** PACS server
- **Remote AE Title:** Destination application entity
- **Local AE Title:** Source application entity
- **Transfer Syntax:** Image compression preference

**How to Test:**
- Use DICOM test tool (e.g., dcm4che toolkit)
- Send C-STORE to OIE listener
- Verify DICOM image received and processed
- Send DICOM from OIE to test PACS
- Verify image stored correctly
- Test DICOM metadata extraction

**Expected Behavior:**
- **Protocol Compliance:** DICOM 3.0 standard
- **C-STORE Operations:** Send/receive images
- **Metadata Extraction:** Patient info, study details
- **Transfer Syntaxes:** Compressed/uncompressed images
- **Error Handling:** DICOM status codes

**Code Location:** `DICOMReceiver.java`, `DICOMDispatcher.java`, DCM4CHE2 library

---

### Feature 3.7: JMS Connector

**Description:**
Integrate with message-oriented middleware (ActiveMQ, IBM MQ, etc.) using Java Message Service.

**How to Use:**

**JMS Listener (Source):**
1. Configure JMS connection factory
2. Select queue or topic
3. Set message selector (optional filtering)

**Configuration:**
- **JMS Broker:** ActiveMQ, WebLogic, WebSphere, JBoss
- **Connection Factory:** JNDI lookup or direct URL
- **Destination Type:** Queue or Topic
- **Destination Name:** Queue/topic name
- **Message Selector:** SQL-like filter
- **Durable Subscription:** For topics
- **Username/Password:** JMS authentication

**JMS Sender (Destination):**
```javascript
// Send to JMS queue/topic
```

**Configuration:**
- **Connection Factory/Destination:** Same as listener
- **Message Type:** Text, Bytes, Object
- **Message Properties:** Custom JMS headers

**How to Test:**
- Start JMS broker (e.g., ActiveMQ)
- Create test queue
- Configure JMS listener
- Send message to queue from external producer
- Verify OIE receives and processes
- Send message from OIE to queue
- Verify external consumer receives

**Expected Behavior:**
- **Transactional:** JMS transactions supported
- **Acknowledgment:** AUTO, CLIENT, DUPS_OK modes
- **Selectors:** Filter messages at broker
- **Persistent:** Durable message delivery
- **Priority:** JMS message priority support

**Code Location:** `JmsReceiver.java`, `JmsDispatcher.java`, JMS libraries

---

### Feature 3.8: WebSocket Connector

**Description:**
Bidirectional real-time communication via WebSocket protocol.

**How to Use:**

**WebSocket Listener (Source):**
1. Configure WebSocket listener port
2. Messages sent to WebSocket trigger channel

**WebSocket Sender (Destination):**
```javascript
// Send message via WebSocket
```

**How to Test:**
- Use WebSocket client tool
- Connect to OIE WebSocket endpoint
- Send message, verify channel processes
- Send message from channel, verify client receives

**Expected Behavior:**
- **Bidirectional:** Send and receive
- **Real-time:** Low latency
- **Persistent Connections:** Long-lived connections

**Code Location:** WebSocket connector implementation

---

### Feature 3.9: JavaScript Connector

**Description:**
Custom connector logic implemented entirely in JavaScript for specialized integrations.

**How to Use:**

**JavaScript Writer (Destination):**
```javascript
// Complete control over destination logic
var response = router.routeMessage('another-channel', msg);

// Call external API
var http = new org.apache.http.client.methods.HttpPost(url);
// ... implement custom logic
```

**How to Test:**
- Implement custom connector logic
- Test with various message types
- Verify error handling
- Test integration with external systems

**Expected Behavior:**
- **Full Control:** Complete flexibility
- **Java Access:** Can use any Java class
- **Synchronous:** Blocking execution

**Code Location:** JavaScript connector plugin

---

### Feature 3.10: VM (Virtual Memory) Connector

**Description:**
Route messages between channels within the same OIE server without external network communication.

**How to Use:**

**VM Sender (Destination):**
```javascript
// Configure destination with VM connector
// Target channel ID
```

**Configuration:**
- **Target Channel ID:** Destination channel
- **Sync/Async:** Wait for response or fire-and-forget

**How to Test:**
- Create two channels
- Configure VM connector from Channel A to Channel B
- Send message to Channel A
- Verify Channel B receives and processes

**Expected Behavior:**
- **Internal Routing:** No network overhead
- **Fast:** In-memory message passing
- **Synchronous Option:** Wait for response
- **Asynchronous Option:** Fire-and-forget

**Code Location:** VM connector implementation

---

## Integration Points

- **Channel Management:** Connectors configured within channels
- **Message Processing:** Connectors provide input/output for transformations
- **Data Type Handling:** Connectors handle serialization
- **Security:** Connectors support authentication and encryption

---

## Performance Considerations

- **Connection Pooling:** HTTP, Database, TCP connectors pool connections
- **Thread Pools:** Each connector has dedicated thread pool
- **Timeouts:** Configure appropriate timeouts to prevent blocking
- **Buffer Sizes:** Tune for large messages

---

## Best Practices

1. **Connection Pooling:** Enable for high-volume connections
2. **Timeouts:** Set realistic timeouts for destinations
3. **Error Handling:** Implement retry logic
4. **Security:** Use SSL/TLS for sensitive data
5. **Testing:** Test connectors with actual systems
6. **Monitoring:** Monitor connection health

---

## Troubleshooting

**Connection Failures:**
- Verify network connectivity
- Check firewall rules
- Validate credentials
- Check SSL certificates

**Performance Issues:**
- Increase connection pool size
- Adjust timeouts
- Enable keep-alive
- Monitor thread usage

---

## Related Documentation

- [Channel Management](01-channel-management.md)
- [Message Processing](02-message-processing.md)
- [Security & Authorization](05-security-authorization.md)
