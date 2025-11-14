# Capability: Administration & Monitoring

**Category:** System Management
**Primary Users:** System Administrators, Operations Teams
**Related Components:** Dashboard, Statistics, Alerts, Event Logging

---

## Overview

Administration & Monitoring provides real-time visibility into system health, performance metrics, channel statistics, error tracking, and proactive alerting. Multiple interfaces (desktop client, web admin, REST API) enable comprehensive system management.

---

## Features

### Feature 6.1: Real-Time Dashboard

**Description:**
Visual dashboard showing real-time status of all channels, connectors, message statistics, and system health indicators.

**How to Use:**

1. **Desktop Client Dashboard:**
   - Launch Mirth Administrator
   - Dashboard tab shows all channel statuses
   - Color-coded status indicators:
     - **Green:** Running normally
     - **Yellow:** Paused or warning
     - **Red:** Error or stopped
     - **Gray:** Undeployed or stopped

2. **Web Dashboard:**
   ```http
   GET /api/channels/status
   ```

   **Response:**
   ```xml
   <list>
     <dashboardStatus>
       <channelId>channel-001</channelId>
       <name>ADT Feed</name>
       <state>STARTED</state>
       <deployedDate>2025-01-14T08:00:00Z</deployedDate>
       <statistics>
         <received>1523</received>
         <sent>1523</sent>
         <error>0</error>
         <filtered>0</filtered>
         <queued>0</queued>
       </statistics>
     </dashboardStatus>
   </list>
   ```

3. **Dashboard Statistics:**
   - Messages received (last hour, last 24 hours, total)
   - Messages sent per destination
   - Error count
   - Filtered message count
   - Queued message count
   - Last message timestamp
   - Uptime

4. **Auto-Refresh:**
   - Configurable refresh interval (default 10 seconds)
   - Real-time updates without page reload

**How to Test:**
- View dashboard with running channels
- Start/stop channel, verify dashboard updates
- Send messages, verify statistics increment
- Create error condition, verify red indicator
- Test auto-refresh interval
- Filter dashboard by status
- Search for specific channel

**Expected Behavior:**
- **Real-Time:** Updates within seconds
- **Accurate:** Statistics match message counts
- **Color-Coded:** Visual status indicators
- **Per-Connector:** Drill-down to connector level
- **Responsive:** Fast load times even with many channels
- **Filterable:** Search and filter capabilities

**Code Location:** `DashboardServlet.java`, `DashboardStatus.java`

---

### Feature 6.2: Channel Statistics

**Description:**
Detailed message throughput and performance statistics per channel and destination including historical trends.

**How to Use:**

1. **Get Channel Statistics:**
   ```http
   GET /api/channels/{channelId}/statistics
   ```

   **Response:**
   ```xml
   <statistics>
     <received>15234</received>
     <sent>15234</sent>
     <error>12</error>
     <filtered>45</filtered>
     <queued>0</queued>
     <connectorStatistics>
       <entry>
         <int>1</int>  <!-- Destination metadata ID -->
         <statistics>
           <received>15234</received>
           <sent>15189</sent>
           <error>12</error>
           <queued>33</queued>
         </statistics>
       </entry>
     </connectorStatistics>
   </statistics>
   ```

2. **Statistics Metrics:**
   - **Received:** Messages received by source connector
   - **Sent:** Messages successfully sent by destinations
   - **Error:** Messages that encountered errors
   - **Filtered:** Messages filtered out by rules
   - **Queued:** Messages currently in queue

3. **Clear Statistics:**
   ```http
   DELETE /api/channels/{channelId}/statistics
   ```

4. **Historical Statistics:**
   - Hourly aggregates
   - Daily aggregates
   - Custom time ranges

**How to Test:**
- Get statistics for active channel
- Send test messages, verify counts increment
- Verify per-destination statistics
- Clear statistics, verify reset to zero
- Create error, verify error count increments
- Filter messages, verify filtered count
- Test statistics persistence across restarts
- Query historical statistics

**Expected Behavior:**
- **Accurate Counts:** Match actual message counts
- **Per-Destination:** Independent statistics per destination
- **Real-Time:** Updates immediately after message processing
- **Persistent:** Statistics survive channel restart (not deployment)
- **Reset on Deploy:** Deployment clears statistics (configurable)
- **Performance:** Minimal overhead for statistics collection

**Code Location:** `ChannelStatisticsServlet.java`, `Statistics.java` in Donkey

---

### Feature 6.3: System Monitoring

**Description:**
Monitor overall system health including JVM memory usage, CPU utilization, disk space, database connections, and thread pools.

**How to Use:**

1. **Get System Stats:**
   ```http
   GET /api/system/stats
   ```

   **Response:**
   ```xml
   <systemStats>
     <timestamp>2025-01-14T10:30:00Z</timestamp>
     <jvm>
       <version>1.8.0_352</version>
       <freeMemoryBytes>536870912</freeMemoryBytes>
       <totalMemoryBytes>2147483648</totalMemoryBytes>
       <maxMemoryBytes>4294967296</maxMemoryBytes>
     </jvm>
     <operatingSystem>
       <name>Linux</name>
       <version>4.4.0</version>
       <architecture>amd64</architecture>
       <availableProcessors>8</availableProcessors>
       <systemLoadAverage>1.52</systemLoadAverage>
     </operatingSystem>
     <diskUsage>
       <totalSpaceBytes>1000000000000</totalSpaceBytes>
       <freeSpaceBytes>500000000000</freeSpaceBytes>
     </diskUsage>
   </systemStats>
   ```

2. **Key Metrics:**
   - **Memory:**
     - Heap usage (used/total/max)
     - Non-heap usage
     - Garbage collection statistics
   - **CPU:**
     - System load average
     - Process CPU usage
   - **Disk:**
     - Total/free disk space
     - Database file size
   - **Threads:**
     - Active thread count
     - Thread pool utilization
   - **Database:**
     - Connection pool size
     - Active connections
     - Idle connections

3. **Get System Info:**
   ```http
   GET /api/system/info
   ```
   - Server version
   - Build date
   - Java version
   - OS information
   - Database type and version

**How to Test:**
- Query system stats
- Verify memory usage reasonable
- Load test system, monitor CPU and memory
- Check disk space warnings
- Monitor thread pool under load
- Verify database connection pool metrics
- Test GC statistics collection

**Expected Behavior:**
- **Accurate Metrics:** Reflects actual system state
- **Low Overhead:** Minimal performance impact
- **Historical Data:** Track trends over time (optional)
- **Warnings:** Alert on high memory, low disk space
- **Cross-Platform:** Works on Linux, Windows, macOS

**Code Location:** `SystemServlet.java`, OSHI library for system metrics

---

### Feature 6.4: Alert Management

**Description:**
Create proactive alerts that trigger based on channel events, errors, or custom conditions. Alerts can send emails, trigger channels, or execute scripts.

**How to Use:**

1. **Create Alert:**
   ```http
   POST /api/alerts
   Content-Type: application/xml

   <alert>
     <name>Channel Error Alert</name>
     <enabled>true</enabled>
     <expression>
       channelEvent == 'ERROR' &amp;&amp; channelId == 'channel-001'
     </expression>
     <template>
       Channel ${channelName} encountered error: ${error}
     </template>
     <emailAddresses>
       <string>admin@hospital.org</string>
     </emailAddresses>
   </alert>
   ```

2. **Alert Triggers:**
   - Channel state change (STARTED, STOPPED, ERROR)
   - Message error
   - Custom JavaScript expression
   - Scheduled (time-based)
   - System events (low disk, high memory)

3. **Alert Actions:**
   - **Email:** Send email notification
   - **Channel:** Trigger another channel
   - **Script:** Execute JavaScript
   - **HTTP:** POST to webhook

4. **Alert Expression Examples:**
   ```javascript
   // Alert on any channel error
   channelEvent == 'ERROR'

   // Alert on specific channel stop
   channelEvent == 'STOPPED' && channelId == 'critical-channel'

   // Alert on high error rate
   errorCount > 100

   // Alert on queue buildup
   queuedCount > 1000

   // Alert on slow processing
   averageProcessingTime > 5000  // 5 seconds
   ```

5. **Alert Templates:**
   ```
   Subject: ${alertName} - ${channelName}

   Channel: ${channelName} (${channelId})
   Event: ${channelEvent}
   Time: ${timestamp}
   Server: ${serverName}

   ${customMessage}
   ```

**How to Test:**
- Create alert for channel error
- Trigger error condition, verify alert fires
- Verify email received (if email action)
- Test alert expression with JavaScript
- Disable alert, trigger condition, verify no alert
- Test alert template variable substitution
- Create scheduled alert, verify fires on schedule
- Test multiple alert actions

**Expected Behavior:**
- **Immediate:** Alerts trigger within seconds of condition
- **Reliable:** Alerts not lost even under high load
- **Customizable:** Flexible expressions and templates
- **Multiple Actions:** Multiple actions per alert
- **Enable/Disable:** Easily enable/disable without deletion
- **Audit:** Alert triggers logged in event log

**Code Location:** `AlertServlet.java`, `AlertController.java`, `Alert.java`

---

### Feature 6.5: Event Log Viewing

**Description:**
View, search, and filter comprehensive system event logs including user actions, channel events, errors, and audit trail.

**How to Use:**

1. **Query Events:**
   ```http
   GET /api/events?level=ERROR&startDate=2025-01-01&limit=100
   ```

   **Parameters:**
   - `level`: DEBUG, INFO, WARNING, ERROR
   - `outcome`: SUCCESS, FAILURE
   - `userId`: Filter by user
   - `name`: Event type
   - `startDate`, `endDate`: Date range
   - `limit`, `offset`: Pagination

2. **Event Types:**
   - USER_LOGIN, USER_LOGOUT
   - CHANNEL_DEPLOYED, CHANNEL_STARTED, CHANNEL_ERROR
   - MESSAGE_RECEIVED, MESSAGE_ERROR, MESSAGE_VIEWED
   - CONFIGURATION_CHANGED
   - ALERT_TRIGGERED
   - EXTENSION_INSTALLED

3. **Event Details:**
   ```xml
   <event>
     <id>12345</id>
     <dateCreated>2025-01-14T10:30:00Z</dateCreated>
     <level>ERROR</level>
     <outcome>FAILURE</outcome>
     <name>MESSAGE_ERROR</name>
     <userId>admin</userId>
     <ipAddress>192.168.1.100</ipAddress>
     <serverId>server-01</serverId>
     <attributes>
       <entry>
         <key>channelId</key>
         <value>channel-001</value>
       </entry>
       <entry>
         <key>error</key>
         <value>Connection refused</value>
       </entry>
     </attributes>
   </event>
   ```

4. **Export Events:**
   ```http
   POST /api/events/export
   Content-Type: application/xml

   <exportRequest>
     <startDate>2025-01-01</startDate>
     <endDate>2025-12-31</endDate>
     <format>CSV</format>
   </exportRequest>
   ```

**How to Test:**
- Query events without filters (recent events)
- Filter by level (ERROR only)
- Filter by date range
- Filter by user (specific user's actions)
- Search for specific event type
- Test pagination (large result sets)
- Export events to CSV
- Verify PHI access events logged

**Expected Behavior:**
- **Searchable:** Rich filtering and search
- **Performance:** Fast queries even with millions of events
- **Retention:** Configurable retention policy
- **Immutable:** Events cannot be modified/deleted by users
- **Detailed:** Events include full context
- **Real-Time:** Events visible immediately
- **Export:** Multiple export formats (CSV, XML, JSON)

**Code Location:** `EventServlet.java`, `EventController.java`

---

### Feature 6.6: Server Logs

**Description:**
Access server application logs for troubleshooting and debugging.

**How to Use:**

1. **View Server Log:**
   ```http
   GET /api/server/logs/mirth.log
   ```

2. **Log Files:**
   - `mirth.log`: Main server log
   - `mirth-YYYY-MM-DD.log`: Daily log rotation
   - `channel-{channelId}.log`: Per-channel logs (if configured)

3. **Log Levels:**
   ```properties
   # log4j2.properties
   logger.mirth.level=INFO
   logger.donkey.level=DEBUG
   ```

4. **Log Format:**
   ```
   2025-01-14 10:30:00,123 [Channel Reader Thread] INFO  com.mirth.connect.server.Mirth - Channel deployed: ADT Feed
   2025-01-14 10:30:01,456 [Destination Thread] ERROR c.m.c.c.http.HttpDispatcher - Connection refused: http://destination:8080
   ```

**How to Test:**
- View server log via API
- Trigger event, verify log entry created
- Change log level, verify output changes
- Test log rotation (daily, size-based)
- Search log for specific error
- Download log file

**Expected Behavior:**
- **Rolling Logs:** Automatic rotation by size/date
- **Configurable Levels:** DEBUG, INFO, WARN, ERROR
- **Per-Component:** Different log levels per package
- **Performance:** Async logging, minimal overhead
- **Structured:** Consistent log format
- **Searchable:** Grep-friendly format

**Code Location:** Log4j2 configuration, logging framework

---

### Feature 6.7: Message Browser

**Description:**
Search, view, and analyze historical messages with rich filtering and content inspection.

**How to Use:**

1. **Query Messages:**
   ```http
   POST /api/channels/{channelId}/messages
   Content-Type: application/xml

   <messageFilter>
     <startDate>2025-01-01T00:00:00Z</startDate>
     <endDate>2025-01-31T23:59:59Z</endDate>
     <status>ERROR</status>
     <textSearch>Patient.*12345</textSearch>
     <textSearchRegex>true</textSearchRegex>
   </messageFilter>
   ```

2. **Filter Options:**
   - **Date Range:** Start/end timestamps
   - **Status:** RECEIVED, SENT, ERROR, FILTERED, QUEUED, PENDING
   - **Text Search:** Content search with regex
   - **Metadata:** Custom metadata column values
   - **Message ID:** Specific message ID
   - **Attachment:** Messages with attachments
   - **Send Attempts:** Retry count filter

3. **Get Message Content:**
   ```http
   GET /api/channels/{channelId}/messages/{messageId}/content
   ```

   **Returns:**
   - Raw message content
   - Processed message content
   - Transformed message content
   - Encoded message content
   - Response content
   - Connector maps
   - Channel map
   - Processing errors

4. **Message Actions:**
   - **View:** View message content and metadata
   - **Reprocess:** Reprocess messages
   - **Remove:** Delete messages
   - **Export:** Export to file

**How to Test:**
- Send messages through channel
- Search for messages by date range
- Search for messages with errors
- Search message content (text search)
- Filter by status
- View message details
- Reprocess errored message
- Export messages to XML
- Test pagination with large result sets

**Expected Behavior:**
- **Fast Search:** Indexed database queries
- **Regex Support:** Complex content searches
- **Full Content:** Access to all message content at each step
- **Metadata:** Custom metadata column search
- **Pagination:** Handle millions of messages
- **Export:** Bulk export capabilities
- **Reprocess:** Retry failed messages

**Code Location:** `MessageServlet.java`, message querying in Donkey

---

### Feature 6.8: Performance Monitoring

**Description:**
Track channel and system performance metrics including message throughput, processing time, and resource utilization.

**How to Use:**

1. **Metrics Tracked:**
   - Messages per second (throughput)
   - Average message processing time
   - Peak processing time
   - Queue depth over time
   - Error rate
   - Connector response times

2. **Get Performance Stats:**
   ```http
   GET /api/channels/{channelId}/statistics/performance
   ```

   **Metrics:**
   ```xml
   <performance>
     <throughput>125.5</throughput>  <!-- msg/sec -->
     <avgProcessingTime>45</avgProcessingTime>  <!-- ms -->
     <maxProcessingTime>1250</maxProcessingTime>
     <queueDepth>33</queueDepth>
   </performance>
   ```

3. **Historical Metrics:**
   - 1-minute, 5-minute, 15-minute averages
   - Hourly aggregates
   - Daily summaries

**How to Test:**
- Monitor throughput during load test
- Verify processing time metrics accurate
- Check queue depth during high load
- Test historical metric aggregation
- Compare performance before/after optimization

**Expected Behavior:**
- **Real-Time:** Current metrics
- **Historical:** Trend analysis
- **Accurate:** Matches actual performance
- **Lightweight:** Minimal overhead

**Code Location:** Statistics collection in Donkey

---

### Feature 6.9: Database Administration

**Description:**
Perform database maintenance tasks including pruning old messages, optimizing tables, and viewing database statistics.

**How to Use:**

1. **List Database Tasks:**
   ```http
   GET /api/database-tasks
   ```

2. **Prune Old Messages:**
   ```http
   POST /api/database-tasks/prune-messages/run
   Content-Type: application/xml

   <pruneRequest>
     <channelId>channel-001</channelId>
     <olderThanDays>90</olderThanDays>
   </pruneRequest>
   ```

3. **Database Tasks:**
   - **Prune Messages:** Delete old messages
   - **Optimize Tables:** Defragment and optimize
   - **Rebuild Indexes:** Rebuild database indexes
   - **Vacuum:** Reclaim disk space (PostgreSQL)
   - **Analyze:** Update statistics (performance)

4. **Schedule Pruning:**
   ```properties
   # mirth.properties
   database.prune.schedule=0 0 2 * * ?  # Daily at 2 AM
   database.prune.retention.days=365
   ```

**How to Test:**
- Run message pruning task
- Verify old messages deleted
- Check database size reduced
- Optimize tables, verify performance improvement
- Schedule automatic pruning
- Monitor task progress
- Test cancel long-running task

**Expected Behavior:**
- **Safe:** Tasks preserve data integrity
- **Efficient:** Batch operations for performance
- **Monitorable:** Progress tracking
- **Cancellable:** Long tasks can be canceled
- **Scheduled:** Automated maintenance

**Code Location:** `DatabaseTaskServlet.java`, database maintenance controllers

---

### Feature 6.10: Configuration Backup and Restore

**Description:**
Backup and restore server configuration including channels, users, alerts, code templates, and server settings.

**How to Use:**

1. **Backup All Configuration:**
   ```http
   POST /api/configuration/export
   ```

   **Exports:**
   - All channels
   - Users and permissions
   - Alerts
   - Code templates
   - Server configuration
   - Channel groups
   - Global scripts

2. **Restore Configuration:**
   ```http
   POST /api/configuration/import
   Content-Type: multipart/form-data

   <configurationBackup>
     <!-- exported configuration XML -->
   </configurationBackup>
   ```

3. **Backup Options:**
   - Full backup: Everything
   - Selective: Channels only, users only, etc.
   - Include statistics: Yes/no
   - Include passwords: Yes/no (encrypted)

4. **Automated Backups:**
   - Schedule daily backups
   - Retention policy (keep last N backups)
   - Off-site storage

**How to Test:**
- Export configuration
- Verify all components included
- Restore to new server
- Verify channels, users, alerts restored
- Test selective export (channels only)
- Test password encryption in backup
- Simulate disaster recovery

**Expected Behavior:**
- **Complete:** All configuration captured
- **Portable:** Import to different server
- **Versioned:** Track backup versions
- **Secure:** Passwords encrypted
- **Atomic:** All-or-nothing restore

**Code Location:** `ConfigurationServlet.java`, import/export controllers

---

## Integration Points

- **Channel Management:** Monitor channel health and status
- **Security:** Audit logs track security events
- **Message Storage:** Message browser queries message database
- **Alerts:** Proactive notification of issues

---

## Performance Considerations

- **Dashboard Refresh:** Configurable interval to reduce load
- **Statistics Collection:** Minimal overhead (atomic counters)
- **Event Logging:** Async to avoid blocking
- **Message Queries:** Database indexes for fast searches

---

## Best Practices

1. **Monitoring:** Set up alerts for critical channels
2. **Logs:** Regular log review and rotation
3. **Statistics:** Clear statistics periodically to manage database size
4. **Backups:** Regular automated configuration backups
5. **Retention:** Configure appropriate message retention
6. **Performance:** Monitor system metrics, plan capacity
7. **Pruning:** Schedule automatic message pruning

---

## Troubleshooting

**Dashboard Not Updating:**
- Check auto-refresh enabled
- Verify network connectivity
- Review browser console for errors

**Missing Events:**
- Check event retention policy
- Verify database connectivity
- Review log level configuration

**Slow Message Queries:**
- Add indexes on custom metadata columns
- Reduce date range
- Optimize database
- Consider archiving old messages

---

## Related Documentation

- [Channel Management](01-channel-management.md)
- [Security & Authorization](05-security-authorization.md)
- [Message Storage & Queuing](09-message-storage-queuing.md)
