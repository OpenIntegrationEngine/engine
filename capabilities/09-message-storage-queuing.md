# Capability: Message Storage & Queuing

**Category:** Data Management
**Primary Users:** System Administrators, Integration Engineers
**Related Components:** Donkey message queuing engine, message database

---

## Overview

Message Storage & Queuing provides persistent message storage, reliable message queuing with store-and-forward capability, message history tracking, and comprehensive message retrieval and searching. All messages are stored in the database for audit trails, troubleshooting, and reprocessing.

---

## Features

### Feature 9.1: Persistent Message Queuing

**Description:**
Store messages in database-backed queues ensuring no message loss during system failures, with automatic retry and recovery capabilities.

**How to Use:**

1. **Enable Store-and-Forward:**
   ```xml
   <channel>
     <properties>
       <storeMessages>true</storeMessages>
       <encryptData>false</encryptData>
       <removeContentOnCompletion>false</removeContentOnCompletion>
       <clearGlobalChannelMap>true</clearGlobalChannelMap>
     </properties>
   </channel>
   ```

2. **Queue Behavior:**
   - Messages received by source connector stored immediately
   - Messages queued for each destination independently
   - Failed messages retry based on retry configuration
   - Messages persist across server restarts

3. **Queue States:**
   - **PENDING:** Message queued, waiting to process
   - **QUEUED:** Message waiting in destination queue
   - **SENDING:** Currently being sent
   - **SENT:** Successfully delivered
   - **ERROR:** Failed delivery (will retry or move to error)

4. **Configuration:**
   ```properties
   # Channel Properties
   maxRetries=3
   retryInterval=10000  # milliseconds
   queueEnabled=true
   ```

**How to Test:**
- Enable store-and-forward on channel
- Send messages to channel
- Stop destination system
- Verify messages queued (not lost)
- Start destination system
- Verify messages delivered from queue
- Test server restart with queued messages
- Verify queued messages persist

**Expected Behavior:**
- **No Message Loss:** Messages survive server crash/restart
- **Ordered Delivery:** Messages delivered in order received (configurable)
- **Retry Logic:** Automatic retry with exponential backoff
- **Queue Limits:** Configurable max queue size
- **Overflow Handling:** Block or error when queue full
- **Performance:** Fast queue operations with database optimization

**Code Location:** Donkey message queuing, `QueueHandler.java`, `ConnectorMessageQueue.java`

---

### Feature 9.2: Message Content Storage

**Description:**
Store complete message content at each processing stage for full audit trail and debugging capability.

**Message Content Types:**

1. **RAW:** Original message as received
2. **TRANSFORMED:** After source transformation
3. **ENCODED:** After serialization (ready to send)
4. **SENT:** Actual message sent to destination
5. **RESPONSE:** Response from destination
6. **RESPONSE_TRANSFORMED:** Transformed response
7. **SOURCE_MAP:** Source connector variables
8. **CONNECTOR_MAP:** Destination connector variables
9. **CHANNEL_MAP:** Channel-level variables
10. **PROCESSING_ERROR:** Error messages during processing
11. **POSTPROCESSOR_ERROR:** Error from postprocessor
12. **RESPONSE_ERROR:** Error from response handling

**How to Use:**

1. **Configure Content Storage:**
   ```xml
   <channel>
     <properties>
       <storeMessages>true</storeMessages>
       <messageStorageMode>DEVELOPMENT</messageStorageMode>
     </properties>
   </channel>
   ```

2. **Storage Modes:**
   - **DEVELOPMENT:** Store all content types (maximum debugging)
   - **PRODUCTION:** Store minimal content (performance)
   - **RAW:** Store raw content only
   - **METADATA:** Store metadata only, no content
   - **DISABLED:** No message storage

3. **Retrieve Message Content:**
   ```http
   GET /api/channels/{channelId}/messages/{messageId}/content
   ```

   **Response:**
   ```xml
   <messageContent>
     <messageId>123456</messageId>
     <contentType>RAW</contentType>
     <content>MSH|^~\&|SENDING_APP|...</content>
     <dataType>HL7V2</dataType>
     <encrypted>false</encrypted>
   </messageContent>
   ```

**How to Test:**
- Configure DEVELOPMENT mode
- Send message through channel with transformation
- Retrieve message content via API
- Verify all content types stored
- Compare RAW vs TRANSFORMED vs ENCODED
- Test with encryption enabled
- Test content retrieval for errored messages

**Expected Behavior:**
- **Complete History:** All content stages stored
- **Debugging:** Full visibility into transformations
- **Audit Trail:** Complete message history
- **Encryption:** Optional content encryption
- **Compression:** Optional content compression
- **Retention:** Content retention based on policy

**Code Location:** Message content storage in Donkey, database message tables

---

### Feature 9.3: Message Metadata and Custom Columns

**Description:**
Store custom metadata with messages for enhanced searching, filtering, and reporting.

**How to Use:**

1. **Define Custom Metadata Columns:**
   ```http
   POST /api/channels/{channelId}/metadata
   Content-Type: application/xml

   <metadataColumn>
     <name>Patient ID</name>
     <type>STRING</type>
     <columnName>patient_id</columnName>
   </metadataColumn>
   ```

2. **Set Metadata in Transformer:**
   ```javascript
   // Set custom metadata for this message
   channelMap.put('patient_id', msg['PID']['PID.3']['PID.3.1'].toString());
   channelMap.put('facility', msg['PID']['PID.3']['PID.3.4'].toString());
   channelMap.put('message_type', msg['MSH']['MSH.9']['MSH.9.1'].toString());
   ```

3. **Metadata Types:**
   - **STRING:** Text values
   - **NUMBER:** Numeric values
   - **TIMESTAMP:** Date/time values
   - **BOOLEAN:** True/false values

4. **Search by Metadata:**
   ```http
   POST /api/channels/{channelId}/messages
   Content-Type: application/xml

   <messageFilter>
     <metaDataSearch>
       <entry>
         <string>patient_id</string>
         <string>12345</string>
         <string>EQUAL</string>
       </entry>
     </metaDataSearch>
   </messageFilter>
   ```

**How to Test:**
- Define custom metadata column
- Set metadata in transformer
- Send test messages
- Search messages by metadata
- Verify filtering works
- Test different operators (EQUAL, LIKE, GREATER_THAN, etc.)
- Test indexing for performance

**Expected Behavior:**
- **Custom Columns:** Up to 20+ custom metadata columns per channel
- **Indexed:** Database indexes for fast searching
- **Operators:** Support for various comparison operators
- **Data Types:** Typed columns for proper sorting/filtering
- **Performance:** Fast searches even with millions of messages

**Code Location:** Metadata column management, message filtering

---

### Feature 9.4: Message Searching and Filtering

**Description:**
Powerful message search with filtering by date, status, content, metadata, and more.

**How to Use:**

1. **Search by Date Range:**
   ```http
   POST /api/channels/{channelId}/messages
   Content-Type: application/xml

   <messageFilter>
     <startDate>2025-01-01T00:00:00Z</startDate>
     <endDate>2025-01-31T23:59:59Z</endDate>
     <limit>100</limit>
     <offset>0</offset>
   </messageFilter>
   ```

2. **Search by Status:**
   ```xml
   <messageFilter>
     <status>ERROR</status>
   </messageFilter>
   ```

   **Statuses:**
   - RECEIVED, FILTERED, TRANSFORMED, PENDING, QUEUED, SENDING, SENT, ERROR

3. **Content Search (Text/Regex):**
   ```xml
   <messageFilter>
     <textSearch>Patient.*12345</textSearch>
     <textSearchRegex>true</textSearchRegex>
   </messageFilter>
   ```

4. **Search by Message ID:**
   ```xml
   <messageFilter>
     <messageIdLower>1000</messageIdLower>
     <messageIdUpper>2000</messageIdUpper>
   </messageFilter>
   ```

5. **Complex Filter:**
   ```xml
   <messageFilter>
     <startDate>2025-01-14T00:00:00Z</startDate>
     <endDate>2025-01-14T23:59:59Z</endDate>
     <status>ERROR</status>
     <textSearch>Connection refused</textSearch>
     <metaDataSearch>
       <entry>
         <string>patient_id</string>
         <string>12345</string>
         <string>EQUAL</string>
       </entry>
     </metaDataSearch>
     <includeContent>true</includeContent>
     <limit>50</limit>
   </messageFilter>
   ```

**How to Test:**
- Search messages by date range only
- Filter by ERROR status
- Search message content with regex
- Combine multiple filters
- Test pagination (large result sets)
- Verify search performance
- Test case-sensitive/insensitive search
- Search with metadata filters

**Expected Behavior:**
- **Fast Search:** Indexed database queries
- **Regex Support:** Full regex pattern matching
- **Pagination:** Handle millions of messages
- **Content Search:** Search across all content types
- **Combined Filters:** AND logic across filters
- **Performance:** Sub-second queries on indexed fields

**Code Location:** `MessageServlet.java`, message filtering in Donkey

---

### Feature 9.5: Message Reprocessing

**Description:**
Reprocess messages that previously failed or need to be re-sent through the channel.

**How to Use:**

1. **Reprocess Single Message:**
   ```http
   POST /api/channels/{channelId}/messages/{messageId}/reprocess
   ```

2. **Reprocess Filtered Messages:**
   ```http
   POST /api/channels/{channelId}/messages/reprocess
   Content-Type: application/xml

   <messageFilter>
     <status>ERROR</status>
     <startDate>2025-01-14T00:00:00Z</startDate>
   </messageFilter>
   ```

3. **Reprocess Options:**
   - **Replace:** Replace original message
   - **Destination:** Reprocess specific destinations only
   - **All Destinations:** Reprocess all destinations

4. **Batch Reprocess:**
   - Select multiple messages
   - Reprocess as batch
   - Monitor progress

**How to Test:**
- Send message that causes error
- Verify message in ERROR status
- Fix error condition (e.g., start destination)
- Reprocess message
- Verify message successfully sent
- Test reprocessing filtered messages (all errors in date range)
- Test reprocessing specific destination only
- Monitor reprocess progress

**Expected Behavior:**
- **Same Message ID:** Reprocessing uses original message ID
- **Update Status:** Status changes from ERROR to SENT
- **Original Preserved:** Original message content preserved
- **Bulk Reprocess:** Efficient batch reprocessing
- **Monitoring:** Progress tracking for large batches
- **Selective:** Reprocess specific destinations

**Code Location:** `MessageServlet.java:reprocessMessages()`

---

### Feature 9.6: Message Removal and Pruning

**Description:**
Remove messages manually or automatically prune old messages based on retention policies.

**How to Use:**

1. **Remove Single Message:**
   ```http
   DELETE /api/channels/{channelId}/messages/{messageId}
   ```

2. **Remove Filtered Messages:**
   ```http
   DELETE /api/channels/{channelId}/messages
   Content-Type: application/xml

   <messageFilter>
     <startDate>2024-01-01T00:00:00Z</startDate>
     <endDate>2024-12-31T23:59:59Z</endDate>
   </messageFilter>
   ```

3. **Automatic Pruning:**
   ```properties
   # mirth.properties
   database.prune.enabled=true
   database.prune.retention.days=365
   database.prune.schedule=0 0 2 * * ?  # Daily at 2 AM
   ```

4. **Prune Task:**
   ```http
   POST /api/database-tasks/prune-messages/run
   Content-Type: application/xml

   <pruneRequest>
     <channelId>channel-001</channelId>
     <olderThanDays>365</olderThanDays>
     <pruneMetadata>true</pruneMetadata>
     <pruneContent>true</pruneContent>
     <pruneAttachments>true</pruneAttachments>
   </pruneRequest>
   ```

**How to Test:**
- Delete single message via API
- Verify message removed from database
- Delete filtered messages (e.g., all messages older than 1 year)
- Verify batch deletion
- Configure automatic pruning
- Verify old messages deleted on schedule
- Test pruning performance with large datasets
- Verify statistics updated after deletion

**Expected Behavior:**
- **Permanent:** Deleted messages cannot be recovered
- **Batch Deletion:** Efficient bulk removal
- **Scheduled:** Automatic pruning on schedule
- **Selective:** Prune specific channels, date ranges
- **Statistics:** Statistics updated after pruning
- **Space Reclaimed:** Database size reduced

**Code Location:** Message deletion, database pruning tasks

---

### Feature 9.7: Message Import and Export

**Description:**
Import and export messages for backup, migration, or analysis purposes.

**How to Use:**

1. **Export Messages:**
   ```http
   POST /api/channels/{channelId}/messages/export
   Content-Type: application/xml

   <messageFilter>
     <startDate>2025-01-01T00:00:00Z</startDate>
     <endDate>2025-01-31T23:59:59Z</endDate>
     <includeContent>true</includeContent>
     <includeAttachments>false</includeAttachments>
   </messageFilter>
   ```

   **Export Formats:**
   - XML (complete message data)
   - CSV (summary data)
   - HL7 (HL7 messages only)
   - Custom (via transformer)

2. **Import Messages:**
   ```http
   POST /api/channels/{channelId}/messages/import
   Content-Type: multipart/form-data

   [Upload exported message file]
   ```

3. **Export Options:**
   - Include/exclude content
   - Include/exclude attachments
   - Include/exclude metadata
   - Date range filter
   - Status filter

**How to Test:**
- Export messages from channel
- Verify export file contains messages
- Import messages to same/different channel
- Verify messages imported correctly
- Test with large export (10,000+ messages)
- Test different export formats
- Test import with attachments

**Expected Behavior:**
- **Complete Export:** All message data exported
- **Portable:** Export from one server, import to another
- **Format Options:** Multiple export formats
- **Large Exports:** Handle large datasets
- **Attachments:** Optional attachment export/import
- **Validation:** Import validates message format

**Code Location:** `MessageServlet.java:exportMessages/importMessages()`

---

### Feature 9.8: Message Attachments

**Description:**
Store and retrieve file attachments associated with messages.

**How to Use:**

1. **Attachment Handlers:**
   - **None:** No attachment processing
   - **JavaScript:** Custom JavaScript extraction
   - **Regex:** Regex pattern extraction
   - **DICOM:** DICOM-specific extraction
   - **PassThru:** Forward attachments unchanged

2. **Set Attachment in Transformer:**
   ```javascript
   // Add attachment
   var attachmentId = addAttachment('filename.pdf', pdfData, 'application/pdf');

   // Store attachment ID for later retrieval
   channelMap.put('attachmentId', attachmentId);
   ```

3. **Retrieve Attachment:**
   ```http
   GET /api/channels/{channelId}/messages/{messageId}/attachments/{attachmentId}
   ```

4. **List Attachments:**
   ```http
   GET /api/channels/{channelId}/messages/{messageId}/attachments
   ```

   **Response:**
   ```xml
   <list>
     <attachment>
       <id>att-001</id>
       <type>application/pdf</type>
       <size>125840</size>
     </attachment>
   </list>
   ```

**How to Test:**
- Configure attachment handler
- Send message with attachment
- Verify attachment stored
- Retrieve attachment via API
- Verify attachment content correct
- Test different MIME types
- Test large attachments
- Test attachment with message export/import

**Expected Behavior:**
- **Binary Storage:** Attachments stored as-is
- **Metadata:** MIME type, size tracked
- **Extraction:** Automatic extraction based on handler
- **Retrieval:** Fast attachment retrieval
- **Large Files:** Support for large attachments
- **Retention:** Attachments pruned with messages

**Code Location:** Attachment handlers, attachment storage

---

### Feature 9.9: Message Encryption

**Description:**
Encrypt message content at rest for additional security of PHI and sensitive data.

**How to Use:**

1. **Enable Encryption:**
   ```xml
   <channel>
     <properties>
       <encryptData>true</encryptData>
     </properties>
   </channel>
   ```

2. **Encryption Configuration:**
   ```properties
   # mirth.properties
   encryption.key=base64encodedkey
   encryption.algorithm=AES/CBC/PKCS5Padding
   ```

3. **Transparent Decryption:**
   - Messages automatically decrypted when retrieved
   - No code changes required
   - Encryption transparent to transformers

**How to Test:**
- Enable encryption on channel
- Send messages
- Query database directly, verify content encrypted
- Retrieve message via API, verify content decrypted
- Test performance impact
- Test with different encryption algorithms

**Expected Behavior:**
- **Transparent:** Automatic encrypt/decrypt
- **Secure:** AES-256 encryption
- **Key Management:** Encryption key configurable
- **Performance:** Minimal performance impact
- **Compliant:** HIPAA encryption requirements

**Code Location:** Message encryption utilities, Encryptor class

---

### Feature 9.10: Queue Monitoring and Management

**Description:**
Monitor queue depth, manage queued messages, and prevent queue overflow.

**How to Use:**

1. **Get Queue Statistics:**
   ```http
   GET /api/channels/{channelId}/statistics
   ```

   **Includes:**
   - Queued message count
   - Queue depth per destination
   - Oldest queued message timestamp
   - Queue overflow status

2. **Queue Limits:**
   ```xml
   <channel>
     <properties>
       <queueBufferSize>1000</queueBufferSize>
       <queueRotate>true</queueRotate>
     </properties>
   </channel>
   ```

3. **Queue Overflow Behavior:**
   - **Block:** Block source until queue drains
   - **Error:** Return error to source
   - **Drop:** Drop oldest messages (FIFO)
   - **Rotate:** Write to disk queue

4. **Clear Queue:**
   ```http
   POST /api/channels/{channelId}/queue/clear
   ```

**How to Test:**
- Send messages faster than destination can process
- Monitor queue depth
- Verify queue grows
- Test queue limit (block or error)
- Clear queue via API
- Verify queue emptied
- Test queue persistence across restart

**Expected Behavior:**
- **Monitoring:** Real-time queue depth
- **Limits:** Prevent unbounded growth
- **Overflow:** Configurable overflow behavior
- **Performance:** Fast queue operations
- **Persistence:** Queue survives restart

**Code Location:** Queue statistics, queue management

---

## Integration Points

- **Channel Management:** Queuing configured per channel
- **Message Processing:** All processed messages stored
- **Administration:** Message browser for searching
- **API:** Programmatic message access

---

## Performance Considerations

- **Database:** Message storage requires database performance
- **Indexes:** Critical for fast message searching
- **Pruning:** Regular pruning prevents database growth
- **Compression:** Optional content compression
- **Queue Depth:** Monitor queue depth, tune destination performance

---

## Best Practices

1. **Retention Policy:** Define appropriate retention based on compliance
2. **Pruning:** Schedule automatic pruning
3. **Indexing:** Index custom metadata columns
4. **Monitoring:** Monitor queue depth
5. **Backup:** Regular database backups
6. **Encryption:** Encrypt PHI at rest
7. **Compression:** Consider compression for large messages
8. **Archiving:** Archive old messages to separate storage

---

## Troubleshooting

**Queue Growing:**
- Check destination system performance
- Increase destination thread count
- Verify no errors preventing delivery
- Consider increasing queue limit

**Slow Message Search:**
- Add indexes on search columns
- Reduce search date range
- Optimize database
- Prune old messages

**Database Growth:**
- Enable automatic pruning
- Reduce retention period
- Remove old messages
- Consider RAW storage mode

---

## Related Documentation

- [Channel Management](01-channel-management.md)
- [Message Processing](02-message-processing.md)
- [Administration & Monitoring](06-administration-monitoring.md)
- [ARCHITECTURE.md](../ARCHITECTURE.md)
