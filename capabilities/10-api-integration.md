# Capability: API & Integration

**Category:** Integration & Automation
**Primary Users:** Developers, DevOps Engineers, Automation Scripts
**Related Components:** REST API servlets, JAX-RS framework, Swagger documentation

---

## Overview

API & Integration provides comprehensive REST APIs for all OIE functionality, enabling programmatic access for automation, external integrations, custom applications, and CI/CD pipelines. The API supports JSON and XML formats with complete CRUD operations for all resources.

---

## Features

### Feature 10.1: REST API Architecture

**Description:**
RESTful API built on JAX-RS (Jersey) with standard HTTP methods, status codes, and content negotiation.

**API Characteristics:**

1. **Base URL:**
   ```
   http://server:8080/api
   https://server:8443/api
   ```

2. **HTTP Methods:**
   - **GET:** Retrieve resources
   - **POST:** Create resources
   - **PUT:** Update resources
   - **DELETE:** Remove resources

3. **Content Types:**
   - `application/xml` (default)
   - `application/json`
   - `text/plain`

4. **Authentication:**
   - HTTP Basic Auth
   - Session-based authentication
   - API tokens (via session)

5. **Standard HTTP Status Codes:**
   - **200 OK:** Successful GET
   - **201 Created:** Successful POST
   - **204 No Content:** Successful DELETE
   - **400 Bad Request:** Invalid input
   - **401 Unauthorized:** Authentication required
   - **403 Forbidden:** Insufficient permissions
   - **404 Not Found:** Resource not found
   - **500 Internal Server Error:** Server error

**How to Use:**

1. **Authenticate:**
   ```bash
   curl -X POST http://localhost:8080/api/users/login \
     -d "username=admin&password=admin"
   ```

2. **Use Session Token:**
   ```bash
   curl -X GET http://localhost:8080/api/channels \
     -H "X-Requested-With: OpenAPI" \
     -b "JSESSIONID=sessiontoken"
   ```

3. **Basic Auth:**
   ```bash
   curl -X GET http://localhost:8080/api/channels \
     -u admin:admin
   ```

**How to Test:**
- Test all HTTP methods
- Test with JSON and XML
- Test authentication (valid/invalid)
- Test authorization (permitted/forbidden)
- Test error responses
- Test content negotiation

**Expected Behavior:**
- **RESTful:** Follows REST principles
- **Standard:** HTTP/1.1 compliant
- **Authenticated:** All endpoints require authentication
- **Authorized:** RBAC enforced
- **Consistent:** Predictable responses
- **Documented:** Swagger/OpenAPI documentation

**Code Location:** `/server/src/com/mirth/connect/server/api/servlets/`, JAX-RS servlets

---

### Feature 10.2: Channel API

**Description:**
Complete channel lifecycle management via API including create, read, update, delete, deploy, and control operations.

**Endpoints:**

1. **List Channels:**
   ```http
   GET /api/channels
   ```

2. **Get Channel:**
   ```http
   GET /api/channels/{channelId}
   ```

3. **Create Channel:**
   ```http
   POST /api/channels
   Content-Type: application/xml

   <channel>
     <name>My Channel</name>
     <description>Channel description</description>
     ...
   </channel>
   ```

4. **Update Channel:**
   ```http
   PUT /api/channels/{channelId}
   Content-Type: application/xml

   <channel>...</channel>
   ```

5. **Delete Channel:**
   ```http
   DELETE /api/channels/{channelId}
   ```

6. **Enable/Disable Channel:**
   ```http
   PUT /api/channels/{channelId}/_enable
   PUT /api/channels/{channelId}/_disable
   ```

7. **Get Channel Status:**
   ```http
   GET /api/channels/{channelId}/status
   ```

8. **Start Channel:**
   ```http
   POST /api/channels/{channelId}/_start
   ```

9. **Stop Channel:**
   ```http
   POST /api/channels/{channelId}/_stop
   ```

10. **Pause/Resume:**
    ```http
    POST /api/channels/{channelId}/_pause
    POST /api/channels/{channelId}/_resume
    ```

**How to Test:**
- Create channel via API
- Verify channel created
- Update channel properties
- Deploy and start channel
- Get channel status
- Stop channel
- Delete channel
- Test error cases (invalid XML, missing fields)

**Expected Behavior:**
- **CRUD Complete:** All operations supported
- **Validation:** Invalid channels rejected
- **Atomic:** Operations are atomic
- **Idempotent:** PUT/DELETE idempotent
- **Consistent:** State changes tracked

**Code Location:** `ChannelServlet.java`, `ChannelStatusServlet.java`

---

### Feature 10.3: Message API

**Description:**
Query, process, reprocess, export, and manage messages programmatically.

**Endpoints:**

1. **Process Message (Send Raw):**
   ```http
   POST /api/channels/{channelId}/messages
   Content-Type: text/plain

   MSH|^~\&|SENDING_APP|...
   ```

2. **Query Messages:**
   ```http
   POST /api/channels/{channelId}/messages/_search
   Content-Type: application/xml

   <messageFilter>
     <startDate>2025-01-01T00:00:00Z</startDate>
     <endDate>2025-01-31T23:59:59Z</endDate>
     <status>ERROR</status>
     <limit>100</limit>
   </messageFilter>
   ```

3. **Get Message Content:**
   ```http
   GET /api/channels/{channelId}/messages/{messageId}/content
   ```

4. **Reprocess Message:**
   ```http
   POST /api/channels/{channelId}/messages/{messageId}/_reprocess
   ```

5. **Remove Messages:**
   ```http
   DELETE /api/channels/{channelId}/messages
   Content-Type: application/xml

   <messageFilter>
     <startDate>2024-01-01T00:00:00Z</startDate>
     <endDate>2024-12-31T23:59:59Z</endDate>
   </messageFilter>
   ```

6. **Export Messages:**
   ```http
   POST /api/channels/{channelId}/messages/_export
   Content-Type: application/xml

   <messageFilter>...</messageFilter>
   ```

7. **Get Message Statistics:**
   ```http
   GET /api/channels/{channelId}/statistics
   ```

**How to Test:**
- Send message via API
- Verify message processed
- Query messages with filters
- Retrieve message content
- Reprocess errored message
- Export messages
- Delete old messages

**Expected Behavior:**
- **Synchronous Send:** POST returns after processing
- **Async Available:** Optional async message processing
- **Rich Filtering:** Complex message queries
- **Bulk Operations:** Batch reprocess/delete
- **Content Access:** All message content accessible

**Code Location:** `MessageServlet.java`, `ChannelStatisticsServlet.java`

---

### Feature 10.4: User and Security API

**Description:**
Manage users, authentication, sessions, and permissions via API.

**Endpoints:**

1. **Login:**
   ```http
   POST /api/users/login
   Content-Type: application/x-www-form-urlencoded

   username=admin&password=admin
   ```

2. **Logout:**
   ```http
   POST /api/users/logout
   ```

3. **List Users:**
   ```http
   GET /api/users
   ```

4. **Get User:**
   ```http
   GET /api/users/{userId}
   ```

5. **Create User:**
   ```http
   POST /api/users
   Content-Type: application/xml

   <user>
     <username>john.doe</username>
     <password>SecureP@ss123</password>
     <firstName>John</firstName>
     <lastName>Doe</lastName>
     <email>john.doe@example.com</email>
   </user>
   ```

6. **Update User:**
   ```http
   PUT /api/users/{userId}
   ```

7. **Delete User:**
   ```http
   DELETE /api/users/{userId}
   ```

8. **Change Password:**
   ```http
   POST /api/users/{userId}/_changePassword
   Content-Type: application/x-www-form-urlencoded

   oldPassword=old&newPassword=new
   ```

**How to Test:**
- Login with valid credentials
- Create new user via API
- Update user properties
- Change user password
- Delete user
- Test permission enforcement
- Test invalid credentials

**Expected Behavior:**
- **Secure:** Passwords never returned in responses
- **Session Management:** Login returns session token
- **RBAC:** User operations check permissions
- **Validation:** Password policy enforced

**Code Location:** `UserServlet.java`, `UserController.java`

---

### Feature 10.5: System and Configuration API

**Description:**
Access server information, configuration, and system statistics.

**Endpoints:**

1. **Get Server Info:**
   ```http
   GET /api/system/info
   ```

   **Response:**
   ```xml
   <serverInfo>
     <version>4.0.0</version>
     <buildDate>2025-01-01</buildDate>
     <javaVersion>1.8.0_352</javaVersion>
     <osName>Linux</osName>
     <serverId>oie-prod-01</serverId>
   </serverInfo>
   ```

2. **Get System Stats:**
   ```http
   GET /api/system/stats
   ```

3. **Get Server Settings:**
   ```http
   GET /api/server/configuration
   ```

4. **Update Server Settings:**
   ```http
   PUT /api/server/configuration
   Content-Type: application/xml

   <serverConfiguration>...</serverConfiguration>
   ```

5. **Get Server ID:**
   ```http
   GET /api/server/id
   ```

6. **Get Server Version:**
   ```http
   GET /api/server/version
   ```

**How to Test:**
- Query server info
- Query system stats
- Get configuration
- Update configuration setting
- Verify changes applied

**Expected Behavior:**
- **Read-Only:** Most endpoints read-only
- **Informational:** Useful for monitoring
- **Version Info:** Support version checking

**Code Location:** `SystemServlet.java`, `ConfigurationServlet.java`

---

### Feature 10.6: Alert API

**Description:**
Create, manage, enable/disable, and test alerts programmatically.

**Endpoints:**

1. **List Alerts:**
   ```http
   GET /api/alerts
   ```

2. **Get Alert:**
   ```http
   GET /api/alerts/{alertId}
   ```

3. **Create Alert:**
   ```http
   POST /api/alerts
   Content-Type: application/xml

   <alert>
     <name>Channel Error Alert</name>
     <enabled>true</enabled>
     <expression>channelEvent == 'ERROR'</expression>
     <template>Channel ${channelName} error: ${error}</template>
     <emailAddresses>
       <string>admin@example.com</string>
     </emailAddresses>
   </alert>
   ```

4. **Update Alert:**
   ```http
   PUT /api/alerts/{alertId}
   ```

5. **Delete Alert:**
   ```http
   DELETE /api/alerts/{alertId}
   ```

6. **Enable/Disable Alert:**
   ```http
   POST /api/alerts/{alertId}/_enable
   POST /api/alerts/{alertId}/_disable
   ```

**How to Test:**
- Create alert via API
- Trigger alert condition
- Verify alert fires
- Disable alert
- Trigger condition again (should not fire)
- Delete alert

**Expected Behavior:**
- **Immediate:** Changes take effect immediately
- **Validation:** Alert expressions validated
- **Testing:** Test alert before saving

**Code Location:** `AlertServlet.java`, `AlertController.java`

---

### Feature 10.7: Event and Audit API

**Description:**
Query audit logs and system events for compliance and troubleshooting.

**Endpoints:**

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

2. **Get Event:**
   ```http
   GET /api/events/{eventId}
   ```

3. **Get Max Event ID:**
   ```http
   GET /api/events/maxId
   ```

4. **Export Events:**
   ```http
   POST /api/events/_export
   Content-Type: application/xml

   <eventFilter>
     <startDate>2025-01-01</startDate>
     <endDate>2025-12-31</endDate>
     <format>CSV</format>
   </eventFilter>
   ```

**How to Test:**
- Perform actions (login, create channel, etc.)
- Query events
- Verify events logged
- Filter by various criteria
- Export events to CSV

**Expected Behavior:**
- **Comprehensive:** All actions logged
- **Searchable:** Rich filtering
- **Immutable:** Events cannot be deleted via API
- **Performance:** Fast queries

**Code Location:** `EventServlet.java`, `EventController.java`

---

### Feature 10.8: Extension Management API

**Description:**
Install, uninstall, and manage extensions programmatically.

**Endpoints:**

1. **List Extensions:**
   ```http
   GET /api/extensions
   ```

2. **Get Extension Metadata:**
   ```http
   GET /api/extensions/{extensionName}/metadata
   ```

3. **Install Extension:**
   ```http
   POST /api/extensions/_install
   Content-Type: multipart/form-data

   [Upload extension JAR file]
   ```

4. **Uninstall Extension:**
   ```http
   POST /api/extensions/{extensionPath}/_uninstall
   ```

**How to Test:**
- List installed extensions
- Upload extension JAR
- Verify extension installed
- Uninstall extension
- Verify cleanup complete

**Expected Behavior:**
- **Validation:** Extension validated before install
- **Dependencies:** Dependency checking
- **Cleanup:** Complete uninstall

**Code Location:** `ExtensionServlet.java`

---

### Feature 10.9: Code Template API

**Description:**
Manage reusable code template libraries via API.

**Endpoints:**

1. **List Libraries:**
   ```http
   GET /api/codeTemplateLibraries
   ```

2. **Get Library:**
   ```http
   GET /api/codeTemplateLibraries/{libraryId}
   ```

3. **Create Library:**
   ```http
   POST /api/codeTemplateLibraries
   Content-Type: application/xml

   <codeTemplateLibrary>
     <name>Utility Functions</name>
     <codeTemplates>
       <codeTemplate>
         <name>Format Date</name>
         <code>function formatDate(d) { ... }</code>
       </codeTemplate>
     </codeTemplates>
   </codeTemplateLibrary>
   ```

4. **Update Library:**
   ```http
   PUT /api/codeTemplateLibraries/{libraryId}
   ```

5. **Delete Library:**
   ```http
   DELETE /api/codeTemplateLibraries/{libraryId}
   ```

**How to Test:**
- Create library via API
- Add code templates
- Assign to channel
- Verify code available in channel
- Update library
- Delete library

**Expected Behavior:**
- **Versioned:** Library versions tracked
- **Propagation:** Changes propagate to channels
- **Validation:** Code syntax validated

**Code Location:** Code template servlets

---

### Feature 10.10: API Documentation and Client Libraries

**Description:**
Swagger/OpenAPI documentation and code generation for client libraries.

**How to Use:**

1. **Swagger UI:**
   ```
   http://server:8080/api-docs
   ```

2. **OpenAPI Specification:**
   ```http
   GET /api/swagger.json
   GET /api/swagger.yaml
   ```

3. **Generate Client Libraries:**
   ```bash
   # Using OpenAPI Generator
   openapi-generator generate -i http://server:8080/api/swagger.json \
     -g python -o ./oie-python-client

   # Available generators: python, java, javascript, php, ruby, etc.
   ```

4. **Client Library Example (Python):**
   ```python
   import oie_client

   # Configure API client
   config = oie_client.Configuration(
       host="http://localhost:8080",
       username="admin",
       password="admin"
   )

   # Create API client
   with oie_client.ApiClient(config) as api_client:
       # Create channel API instance
       channel_api = oie_client.ChannelApi(api_client)

       # List channels
       channels = channel_api.get_channels()

       # Start channel
       channel_api.start_channel(channel_id="channel-001")
   ```

**How to Test:**
- Access Swagger UI
- Try API endpoints from Swagger
- Download OpenAPI spec
- Generate client library
- Test generated client

**Expected Behavior:**
- **Complete:** All endpoints documented
- **Interactive:** Try endpoints from browser
- **Standards:** OpenAPI 3.0 compliant
- **Code Generation:** Generate clients in any language

**Code Location:** Swagger annotations in servlets

---

## Integration Points

- **CI/CD:** Automate channel deployment
- **Monitoring:** External monitoring tools
- **Custom Applications:** Build custom UIs or integrations
- **Automation:** Script routine operations

---

## Performance Considerations

- **Authentication:** Cache session tokens
- **Pagination:** Use limit/offset for large datasets
- **Bulk Operations:** Batch operations where possible
- **Keep-Alive:** Reuse HTTP connections

---

## Best Practices

1. **Authentication:** Use session tokens, not basic auth for each request
2. **Error Handling:** Check HTTP status codes, parse error responses
3. **Pagination:** Always paginate large result sets
4. **Idempotency:** Use PUT/DELETE for idempotent operations
5. **Versioning:** Track API version compatibility
6. **Rate Limiting:** Implement client-side rate limiting
7. **Logging:** Log all API calls for audit

---

## Troubleshooting

**401 Unauthorized:**
- Verify credentials correct
- Check session token valid
- Ensure user has permissions

**403 Forbidden:**
- Check user permissions
- Verify RBAC configuration

**500 Internal Server Error:**
- Check server logs
- Verify request format
- Check for application errors

---

## API Examples

### Example 1: Automated Channel Deployment

```bash
#!/bin/bash

# Login
SESSION=$(curl -s -c cookies.txt -X POST \
  http://localhost:8080/api/users/login \
  -d "username=admin&password=admin" | grep -o 'JSESSIONID=[^;]*')

# Create channel from file
curl -b cookies.txt -X POST \
  http://localhost:8080/api/channels \
  -H "Content-Type: application/xml" \
  --data-binary @channel.xml

# Deploy channel
curl -b cookies.txt -X POST \
  http://localhost:8080/api/engine/_deploy \
  -H "Content-Type: application/xml" \
  -d '<set><string>channel-001</string></set>'

# Start channel
curl -b cookies.txt -X POST \
  http://localhost:8080/api/channels/channel-001/_start

# Logout
curl -b cookies.txt -X POST \
  http://localhost:8080/api/users/logout
```

### Example 2: Message Monitoring

```python
import requests
from datetime import datetime, timedelta

# Configuration
BASE_URL = "http://localhost:8080/api"
USERNAME = "admin"
PASSWORD = "admin"

# Login
session = requests.Session()
session.post(f"{BASE_URL}/users/login",
             data={"username": USERNAME, "password": PASSWORD})

# Query error messages from last hour
end_date = datetime.now()
start_date = end_date - timedelta(hours=1)

filter_xml = f"""
<messageFilter>
  <startDate>{start_date.isoformat()}Z</startDate>
  <endDate>{end_date.isoformat()}Z</endDate>
  <status>ERROR</status>
</messageFilter>
"""

response = session.post(
    f"{BASE_URL}/channels/channel-001/messages/_search",
    headers={"Content-Type": "application/xml"},
    data=filter_xml
)

errors = response.json()
print(f"Found {len(errors)} errors in last hour")

# Reprocess errors
for error in errors:
    session.post(f"{BASE_URL}/channels/channel-001/messages/{error['messageId']}/_reprocess")

# Logout
session.post(f"{BASE_URL}/users/logout")
```

### Example 3: Health Check

```javascript
const axios = require('axios');

async function healthCheck() {
  try {
    // Get server info
    const serverInfo = await axios.get('http://localhost:8080/api/system/info');
    console.log(`Server version: ${serverInfo.data.version}`);

    // Get system stats
    const stats = await axios.get('http://localhost:8080/api/system/stats');
    const memoryUsed = stats.data.jvm.totalMemoryBytes - stats.data.jvm.freeMemoryBytes;
    const memoryPercent = (memoryUsed / stats.data.jvm.totalMemoryBytes) * 100;

    console.log(`Memory usage: ${memoryPercent.toFixed(2)}%`);

    // Get channel statuses
    const channels = await axios.get('http://localhost:8080/api/channels/status');
    const errorChannels = channels.data.filter(ch => ch.state === 'ERROR');

    if (errorChannels.length > 0) {
      console.log(`WARNING: ${errorChannels.length} channels in error state`);
      return false;
    }

    console.log('All systems operational');
    return true;
  } catch (error) {
    console.error('Health check failed:', error.message);
    return false;
  }
}

healthCheck();
```

---

## Related Documentation

- [Channel Management](01-channel-management.md)
- [Message Processing](02-message-processing.md)
- [Security & Authorization](05-security-authorization.md)
- [Administration & Monitoring](06-administration-monitoring.md)
- [ARCHITECTURE.md](../ARCHITECTURE.md)
