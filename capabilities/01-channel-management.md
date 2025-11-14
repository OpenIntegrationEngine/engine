# Capability: Channel Management

**Category:** Core Integration Capability
**Primary Users:** Integration Engineers, Administrators
**Related Components:** ChannelController, EngineController, ChannelServlet

---

## Overview

Channel Management provides the ability to create, configure, deploy, monitor, and control integration channels. A channel is the fundamental unit of integration in OIE, defining how messages flow from a source system through transformations to one or more destination systems.

---

## Features

### Feature 1.1: Channel Creation and Configuration

**Description:**
Create new integration channels with complete configuration including source connector, multiple destination connectors, preprocessing/postprocessing scripts, and channel metadata.

**How to Use:**
1. **Via Desktop Client:**
   - Launch Mirth Administrator client
   - Click "New Channel" button
   - Configure channel name, description, and data type
   - Select source connector type (HTTP, TCP, File, Database, etc.)
   - Add one or more destination connectors
   - Configure transformations and filters
   - Save channel

2. **Via REST API:**
   ```http
   POST /api/channels
   Content-Type: application/xml

   <channel>
     <id>unique-channel-id</id>
     <name>ADT Feed</name>
     <description>Receive ADT messages from EHR</description>
     <sourceConnector>...</sourceConnector>
     <destinationConnectors>...</destinationConnectors>
   </channel>
   ```

3. **Via CLI:**
   ```bash
   ./mirth-cli import channel.xml
   ```

**How to Test:**
- Create a test channel with minimal configuration
- Verify channel appears in channel list
- Check channel properties are saved correctly
- Attempt to create channel with invalid configuration (should fail with validation error)
- Create channel with duplicate ID (should fail)

**Expected Behavior:**
- Channel is created with unique ID
- All configuration properties are persisted to database
- Channel appears in admin interfaces immediately
- Channel is in "stopped" state by default
- Validation errors prevent invalid configurations
- Channel revision number is set to 1

**Code Location:** `ChannelServlet.java:createChannel()`, `ChannelController.java`

---

### Feature 1.2: Channel Deployment

**Description:**
Deploy channels to activate them for message processing. Deployment compiles scripts, initializes connectors, and prepares the channel for receiving messages.

**How to Use:**
1. **Via Desktop Client:**
   - Select one or more channels
   - Click "Deploy Channels" button
   - Wait for deployment confirmation

2. **Via REST API:**
   ```http
   POST /api/engine/deploy
   Content-Type: application/xml

   <set>
     <channelId>channel-id-1</channelId>
     <channelId>channel-id-2</channelId>
   </set>
   ```

3. **Automatic Deployment:**
   - Configure channel with `initialState="STARTED"`
   - Channel deploys automatically on server startup

**How to Test:**
- Deploy a simple channel
- Verify channel status changes to "deployed"
- Check server logs for deployment messages
- Deploy channel with script errors (should fail with error details)
- Deploy multiple channels simultaneously
- Undeploy and redeploy same channel
- Test deployment with debug mode enabled

**Expected Behavior:**
- Channel transitions from "stopped" to "deploying" to "deployed"
- All JavaScript scripts are compiled and validated
- Connectors are initialized (ports opened, connections established)
- Deployment failures roll back completely (channel remains undeployed)
- Error messages clearly indicate deployment failure reasons
- Statistics are reset on deployment
- Previous message queues are preserved (unless configured otherwise)

**Code Location:** `EngineController.java:deployChannels()`, `Channel.java:deploy()`

---

### Feature 1.3: Channel Lifecycle Control

**Description:**
Control channel runtime state with start, stop, pause, resume, and halt operations. Each operation provides different levels of control over message processing.

**How to Use:**

**Start Channel:**
```http
POST /api/channels/{channelId}/start
```
- Begins message processing
- Source connector starts listening/polling
- Queued messages begin processing

**Stop Channel:**
```http
POST /api/channels/{channelId}/stop
```
- Gracefully stops message processing
- Waits for in-flight messages to complete
- Source connector stops accepting new messages

**Pause Channel:**
```http
POST /api/channels/{channelId}/pause
```
- Temporarily suspends message processing
- Messages remain queued
- Can be quickly resumed

**Resume Channel:**
```http
POST /api/channels/{channelId}/resume
```
- Resumes paused channel
- Processing continues from where it paused

**Halt Channel:**
```http
POST /api/channels/{channelId}/halt
```
- Immediately stops all processing
- Does not wait for in-flight messages
- Use only in emergency situations

**How to Test:**
- Start channel and verify it processes messages
- Stop channel and verify no new messages are accepted
- Pause channel, send messages, verify they queue, then resume
- Halt channel while processing messages (verify immediate stop)
- Test state transitions (e.g., pause from stopped state should fail)
- Verify connector-level controls (start/stop individual destinations)

**Expected Behavior:**

| Operation | In-Flight Messages | Queued Messages | Source Accepts New | Restart Speed |
|-----------|-------------------|-----------------|-------------------|---------------|
| Stop | Completes | Preserved | No | Slow (full restart) |
| Pause | Completes | Preserved | No | Fast (resume) |
| Halt | Aborted | Preserved | No | Slow (full restart) |
| Start | N/A | Begin processing | Yes | N/A |
| Resume | N/A | Resume processing | Yes | Fast |

**Code Location:** `ChannelStatusServlet.java`, `Channel.java:start/stop/pause/resume/halt()`

---

### Feature 1.4: Channel Status Monitoring

**Description:**
Monitor real-time channel and connector status including state, message counts, errors, and health indicators.

**How to Use:**

1. **Get All Channel Statuses:**
   ```http
   GET /api/channels/status
   ```

2. **Get Specific Channel Status:**
   ```http
   GET /api/channels/{channelId}/status
   ```

3. **Dashboard View:**
   - View real-time status in desktop client dashboard
   - Color-coded status indicators (green=running, yellow=paused, red=error)

**Status Information Includes:**
- Channel state (STARTED, STOPPED, PAUSED, DEPLOYING, UNDEPLOYING, etc.)
- Connector states (per source and destination)
- Queued message count
- Last message received timestamp
- Error indicators
- Deployment warnings

**How to Test:**
- Start channel and verify status shows "STARTED"
- Send messages and verify timestamp updates
- Stop source connector only, verify destination continues
- Create error condition, verify error status appears
- Monitor status during deployment
- Check status after server restart

**Expected Behavior:**
- Status updates in real-time (within 1-2 seconds)
- Accurate message counts and timestamps
- Clear error messages when problems occur
- Per-connector granularity
- Historical state changes logged
- Status survives server restart (for deployed channels)

**Code Location:** `ChannelStatusServlet.java:getChannelStatus()`, `DashboardConnectorStatus.java`

---

### Feature 1.5: Channel Import/Export

**Description:**
Export channel configurations to XML files for backup, migration, or version control. Import channels from XML files.

**How to Use:**

**Export Single Channel:**
```http
GET /api/channels/{channelId}
Accept: application/xml
```

**Export Multiple Channels:**
```http
POST /api/channels/_export
Content-Type: application/xml

<channelIds>
  <channelId>id1</channelId>
  <channelId>id2</channelId>
</channelIds>
```

**Import Channel:**
```http
POST /api/channels
Content-Type: application/xml

<channel>
  <!-- channel XML content -->
</channel>
```

**Export Options:**
- Include/exclude channel statistics
- Include/exclude code templates
- Include/exclude dependencies

**How to Test:**
- Export channel and verify XML is valid
- Import exported channel with new ID
- Import channel that already exists (test overwrite behavior)
- Export channel with dependencies (code templates)
- Import malformed XML (should fail gracefully)
- Export all channels, delete them, reimport

**Expected Behavior:**
- Exported XML is valid and complete
- Import creates exact replica of original channel
- Channel IDs can be preserved or regenerated
- Code templates and dependencies are optionally included
- Import validates XML before applying changes
- Existing channels can be overwritten (with warning)
- Import failures provide detailed error messages

**Code Location:** `ChannelServlet.java:getChannel/createChannel()`, serialization in model classes

---

### Feature 1.6: Channel Metadata and Organization

**Description:**
Organize channels using tags, groups, and custom metadata. Filter and search channels based on metadata.

**How to Use:**

**Channel Tags:**
- Assign tags to channels: `production`, `test`, `adt`, `lab`, `radiology`
- Filter channels by tag in UI and API

**Channel Groups:**
```http
GET /api/channelgroups
PUT /api/channelgroups
```

**Custom Metadata:**
- Store custom properties with channels
- Use in searches and reports

**How to Test:**
- Create channels with different tags
- Filter channel list by tag
- Create channel groups and verify grouping in UI
- Search for channels by metadata
- Update tags and verify changes persist

**Expected Behavior:**
- Tags are searchable and filterable
- Groups organize channels logically in UI
- Metadata is preserved during export/import
- Tags support autocomplete
- Multiple tags per channel supported

**Code Location:** `ChannelServlet.java`, `ChannelMetadata.java`

---

### Feature 1.7: Channel Dependencies

**Description:**
Define and manage dependencies between channels to ensure proper deployment order and prevent dependency issues.

**How to Use:**

**Define Dependency:**
```xml
<channel>
  <dependencies>
    <dependency channelId="master-patient-index"/>
  </dependencies>
</channel>
```

**Get Dependency Graph:**
```http
GET /api/configuration/channelDependencies
```

**How to Test:**
- Create Channel A that depends on Channel B
- Deploy Channel A before Channel B (should fail or warn)
- Deploy both in correct order
- Create circular dependency (should be detected)
- Undeploy Channel B while A is running (should warn)

**Expected Behavior:**
- System calculates correct deployment order
- Circular dependencies are detected and prevented
- Warnings appear when dependencies are violated
- Dependencies are validated during deployment
- Dependency graph is visualized in UI

**Code Location:** `ConfigurationController.java:getChannelDependencies()`

---

### Feature 1.8: Channel Cloning

**Description:**
Create a copy of an existing channel with a new ID for rapid development and testing.

**How to Use:**
1. Export channel
2. Modify channel ID in XML
3. Import modified XML

**Alternative (Desktop Client):**
- Right-click channel → "Clone Channel"
- Specify new channel name
- Channel is duplicated with new ID

**How to Test:**
- Clone simple channel and verify independence
- Clone channel with complex transformations
- Modify cloned channel and verify original unchanged
- Clone channel in different environment

**Expected Behavior:**
- Cloned channel has unique ID
- All configuration is identical except ID and name
- Statistics are reset (not copied)
- Cloned channel is in stopped state
- Both channels can run simultaneously

**Code Location:** Client-side cloning logic, channel import/export

---

### Feature 1.9: Channel Revision Control

**Description:**
Track channel configuration changes with revision numbers. View and compare channel revisions.

**How to Use:**
- Each channel save increments revision number
- View revision history in channel summary
- Compare revisions to see changes

**How to Test:**
- Create channel (revision 1)
- Modify and save (revision 2)
- View revision history
- Export different revisions
- Deploy specific revision

**Expected Behavior:**
- Revision increments on each save
- Revision number visible in UI and API
- Old revisions can be retrieved (if configured)
- Deployment uses latest revision
- Revision number in exported XML

**Code Location:** `Channel.java` (revision field), `ChannelController.java`

---

### Feature 1.10: Channel Statistics

**Description:**
View message throughput and error statistics per channel including received, sent, filtered, queued, and error counts.

**How to Use:**

**Get Channel Statistics:**
```http
GET /api/channels/{channelId}/statistics
```

**Clear Statistics:**
```http
DELETE /api/channels/{channelId}/statistics
```

**Statistics Include:**
- Messages received
- Messages sent (per destination)
- Messages filtered
- Messages queued
- Messages errored
- Average processing time
- Throughput (messages/second)

**How to Test:**
- Send messages and verify statistics increment
- Clear statistics and verify reset to zero
- Check per-destination statistics
- Verify statistics persist across channel restart
- Test statistics after deployment (should reset)

**Expected Behavior:**
- Statistics update in real-time
- Accurate counts for all message states
- Per-destination granularity
- Statistics survive channel stop/start
- Statistics reset on deployment (optional)
- Historical statistics archived (optional)

**Code Location:** `ChannelStatisticsServlet.java`, `Statistics.java` in Donkey

---

## Integration Points

- **Message Processing:** Channels execute message processing pipeline
- **Connector Framework:** Channels use connectors for communication
- **Data Type Handling:** Channels specify message data types
- **Security:** Channel access controlled by RBAC
- **API:** Full channel management via REST API
- **Monitoring:** Channel status and statistics monitored

---

## Configuration Files

- `/server/src/com/mirth/connect/model/Channel.java` - Channel model
- `/server/dbconf/*/channel-*.xml` - Database mappings
- Channel exports: XML format

---

## Performance Considerations

- **Deployment Time:** Complex channels take longer to deploy (script compilation)
- **Channel Count:** Tested with 100+ channels per server
- **Memory:** Each channel consumes heap memory (typically 10-50 MB)
- **Thread Pools:** Each channel has dedicated thread pool (configurable)

---

## Best Practices

1. **Naming:** Use clear, descriptive channel names indicating purpose
2. **Tags:** Tag channels by environment (dev/test/prod) and system
3. **Dependencies:** Document channel dependencies clearly
4. **Testing:** Test channels in development environment before production
5. **Export:** Regularly export channels for backup
6. **Monitoring:** Set up alerts for channel errors
7. **Documentation:** Use channel description field extensively
8. **Versioning:** Track channel versions in external version control

---

## Troubleshooting

**Channel Won't Deploy:**
- Check server logs for script compilation errors
- Verify connector configuration (ports available, credentials correct)
- Check for dependency issues

**Channel Stops Unexpectedly:**
- Review error logs
- Check destination system availability
- Verify resource limits (memory, connections)

**Poor Performance:**
- Increase thread pool size
- Enable message queuing
- Optimize transformation scripts
- Check database performance

---

## Security Considerations

- Channels contain sensitive connection credentials
- Export files may contain PHI (protect accordingly)
- Use RBAC to restrict channel access
- Audit all channel modifications
- Encrypt database containing channel configuration

---

## Related Documentation

- [Message Processing](02-message-processing.md)
- [Connector Framework](03-connector-framework.md)
- [Security & Authorization](05-security-authorization.md)
- [ARCHITECTURE.md](../ARCHITECTURE.md) - Channel architecture details
