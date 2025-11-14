# Capability: Extension & Plugin System

**Category:** Extensibility
**Primary Users:** Developers, Integration Engineers
**Related Components:** ExtensionController, Plugin framework

---

## Overview

The Extension & Plugin System provides a modular architecture for extending OIE functionality with custom connectors, data types, authentication providers, transformation steps, and service plugins. Extensions are packaged as JARs with metadata and can be installed/uninstalled without modifying core code.

---

## Features

### Feature 8.1: Plugin Architecture

**Description:**
Comprehensive plugin framework supporting multiple extension types with lifecycle management, dependency resolution, and isolation.

**Plugin Types:**

1. **Connector Plugins:** New communication protocols
2. **Data Type Plugins:** Custom message format parsers/serializers
3. **Authentication Plugins:** Custom authentication providers
4. **Service Plugins:** Background services
5. **Transformer Step Plugins:** Custom transformation operations
6. **Rule Plugins:** Custom filtering rules
7. **Transmission Mode Plugins:** Custom frame encoding for TCP
8. **Code Template Plugins:** Reusable code libraries
9. **Viewer Plugins:** Custom message content viewers

**How to Use:**

1. **Plugin Structure:**
   ```
   my-plugin.jar
   ├── META-INF/
   │   └── MANIFEST.MF
   ├── plugin.xml (Plugin metadata)
   ├── com/example/myplugin/
   │   ├── MyPlugin.class
   │   ├── MyConnector.class
   │   └── resources/
   └── lib/ (Dependencies)
   ```

2. **Plugin Metadata (`plugin.xml`):**
   ```xml
   <pluginMetaData path="myplugin">
     <name>My Custom Plugin</name>
     <author>Example Corp</author>
     <version>1.0.0</version>
     <mirthVersion>4.0.0</mirthVersion>
     <pluginClass>com.example.myplugin.MyPlugin</pluginClass>

     <dependencies>
       <dependency>
         <name>Other Plugin</name>
         <version>2.0.0</version>
         <minVersion>1.5.0</minVersion>
       </dependency>
     </dependencies>

     <libraries>
       <library type="server">lib/my-library.jar</library>
     </libraries>
   </pluginMetaData>
   ```

3. **Plugin Lifecycle:**
   - **Load:** Server reads plugin.xml, loads classes
   - **Init:** Plugin.init() called
   - **Start:** Plugin.start() called
   - **Stop:** Plugin.stop() called on server shutdown
   - **Uninstall:** Plugin removed, cleanup performed

**How to Test:**
- Create minimal plugin
- Package as JAR with plugin.xml
- Install via API
- Verify plugin loaded (check extensions list)
- Verify plugin functionality
- Uninstall plugin
- Verify cleanup complete

**Expected Behavior:**
- **Isolated:** Plugins have separate classloaders
- **Dependencies:** Dependency resolution automatic
- **Lifecycle:** Well-defined initialization/cleanup
- **Versioning:** Version compatibility checking
- **Hot Deploy:** Install without server restart (some types)
- **Error Handling:** Plugin errors don't crash server

**Code Location:** `ExtensionController.java`, `/server/src/com/mirth/connect/plugins/`

---

### Feature 8.2: Connector Plugin Development

**Description:**
Develop custom connectors for proprietary protocols or specialized integrations.

**How to Use:**

1. **Connector Plugin Interface:**
   ```java
   public class MyConnector implements ConnectorInterface {

       @Override
       public void onDeploy() throws ConnectorException {
           // Initialize connector (open connections, etc.)
       }

       @Override
       public void onUndeploy() throws ConnectorException {
           // Cleanup (close connections, etc.)
       }

       @Override
       public void onStart() throws ConnectorException {
           // Start processing messages
       }

       @Override
       public void onStop() throws ConnectorException {
           // Stop processing (graceful shutdown)
       }

       @Override
       public void send(ConnectorMessage message) throws InterruptedException {
           // Send message to external system
           try {
               // Custom sending logic
               String response = sendToExternalSystem(message.getRaw());
               message.setResponse(response);
           } catch (Exception e) {
               throw new ConnectorException(e);
           }
       }
   }
   ```

2. **Source Connector (Receiver):**
   ```java
   public class MySourceConnector extends SourceConnector {

       @Override
       public void run() {
           // Poll for messages or listen for incoming
           while (isRunning()) {
               try {
                   String message = receiveFromExternalSystem();
                   dispatchMessage(message);
               } catch (Exception e) {
                   logger.error("Error receiving message", e);
               }
           }
       }
   }
   ```

3. **Connector Properties:**
   ```java
   public class MyConnectorProperties extends ConnectorProperties {
       private String host;
       private int port;
       private String apiKey;

       // Getters and setters with @XStreamAlias annotations
       @XStreamAlias("host")
       public String getHost() { return host; }

       public void setHost(String host) { this.host = host; }
   }
   ```

4. **UI Configuration Panel:**
   - Client-side connector configuration UI
   - JavaFX or Swing panels
   - Bind to ConnectorProperties

**How to Test:**
- Implement connector interface
- Package as plugin
- Install plugin
- Create channel with custom connector
- Deploy and test message flow
- Verify error handling
- Test lifecycle operations (start/stop/deploy)

**Expected Behavior:**
- **Configuration:** Properties editable in UI
- **Lifecycle:** Start/stop/deploy/undeploy work correctly
- **Error Handling:** Errors reported to channel
- **Thread-Safe:** Concurrent message handling
- **Resource Management:** Proper cleanup of connections

**Code Location:** Connector interface, example connectors in `/server/src/com/mirth/connect/connectors/`

---

### Feature 8.3: Data Type Plugin Development

**Description:**
Create parsers and serializers for custom or proprietary message formats.

**How to Use:**

1. **Data Type Plugin Interface:**
   ```java
   public class MyDataTypePlugin implements DataTypeServerPlugin {

       @Override
       public String getPluginPointName() {
           return "MY_DATA_TYPE";
       }

       @Override
       public SerializerProvider getSerializer() {
           return new MySerializer();
       }

       @Override
       public boolean isBinary() {
           return false;  // Text-based format
       }
   }
   ```

2. **Serializer Implementation:**
   ```java
   public class MySerializer implements SerializerProvider {

       @Override
       public String toXML(String message) throws SerializerException {
           // Parse custom format to normalized XML
           MyMessage parsed = parse(message);
           return convertToXML(parsed);
       }

       @Override
       public String fromXML(String xml) throws SerializerException {
           // Convert XML back to custom format
           Document doc = parseXML(xml);
           return serializeToMyFormat(doc);
       }

       @Override
       public boolean isValid(String message) {
           // Validate message format
           try {
               parse(message);
               return true;
           } catch (Exception e) {
               return false;
           }
       }
   }
   ```

3. **Batch Message Support:**
   ```java
   public class MyBatchAdaptor implements BatchAdaptorProvider {

       @Override
       public List<String> splitBatch(String batchMessage) {
           // Split batch into individual messages
           return Arrays.asList(batchMessage.split("\\n---\\n"));
       }
   }
   ```

**How to Test:**
- Implement data type plugin
- Install plugin
- Create channel with custom data type
- Send test message in custom format
- Verify parsing to XML
- Transform XML
- Verify serialization back to custom format
- Test validation

**Expected Behavior:**
- **Bidirectional:** Parse and serialize
- **Validation:** Format validation
- **Batch Support:** Optional batch handling
- **Error Messages:** Clear parsing errors
- **Performance:** Efficient parsing

**Code Location:** Data type plugin interface, examples in `/server/src/com/mirth/connect/plugins/datatypes/`

---

### Feature 8.4: Authentication Plugin Development

**Description:**
Implement custom authentication providers for LDAP, OAuth, SAML, or proprietary authentication systems.

**How to Use:**

1. **Authentication Plugin Interface:**
   ```java
   public class MyAuthPlugin implements AuthenticationPlugin {

       @Override
       public LoginStatus authenticate(String username, String password) {
           try {
               // Custom authentication logic
               if (authenticateWithExternalSystem(username, password)) {
                   User user = getUserInfo(username);
                   return new LoginStatus(Status.SUCCESS, user);
               } else {
                   return new LoginStatus(Status.FAIL, "Invalid credentials");
               }
           } catch (Exception e) {
               return new LoginStatus(Status.FAIL, "Authentication error");
           }
       }

       @Override
       public boolean isValidPassword(User user, String password) {
           // Password validation
           return authenticateWithExternalSystem(user.getUsername(), password);
       }
   }
   ```

2. **LDAP Authentication Example:**
   ```java
   public class LDAPAuthPlugin implements AuthenticationPlugin {

       @Override
       public LoginStatus authenticate(String username, String password) {
           // LDAP connection
           Hashtable<String, String> env = new Hashtable<>();
           env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
           env.put(Context.PROVIDER_URL, ldapUrl);
           env.put(Context.SECURITY_PRINCIPAL, "uid=" + username + "," + baseDN);
           env.put(Context.SECURITY_CREDENTIALS, password);

           try {
               DirContext ctx = new InitialDirContext(env);
               // Successful authentication
               User user = lookupUserAttributes(ctx, username);
               ctx.close();
               return new LoginStatus(Status.SUCCESS, user);
           } catch (AuthenticationException e) {
               return new LoginStatus(Status.FAIL, "Invalid credentials");
           }
       }
   }
   ```

3. **OAuth 2.0 Authentication:**
   ```java
   public class OAuth2AuthPlugin implements AuthenticationPlugin {

       @Override
       public LoginStatus authenticate(String token, String unused) {
           // Validate OAuth token
           try {
               UserInfo userInfo = validateTokenWithProvider(token);
               User user = mapToMirthUser(userInfo);
               return new LoginStatus(Status.SUCCESS, user);
           } catch (Exception e) {
               return new LoginStatus(Status.FAIL, "Invalid token");
           }
       }
   }
   ```

**How to Test:**
- Implement authentication plugin
- Configure authentication provider
- Install plugin
- Attempt login with valid credentials
- Verify success
- Attempt login with invalid credentials
- Verify failure
- Test user attribute mapping
- Test group/role mapping

**Expected Behavior:**
- **External Integration:** Connect to auth systems
- **User Mapping:** Map external users to OIE users
- **Role Mapping:** Map external groups to OIE permissions
- **Secure:** No credential storage in OIE
- **Error Handling:** Clear error messages

**Code Location:** Authentication plugin interface, auth plugins in `/server/src/com/mirth/connect/plugins/httpauth/`

---

### Feature 8.5: Service Plugin Development

**Description:**
Create background services that run continuously, perform scheduled tasks, or provide shared functionality.

**How to Use:**

1. **Service Plugin Interface:**
   ```java
   public class MyServicePlugin implements ServicePlugin {

       private ScheduledExecutorService scheduler;

       @Override
       public void init(Properties properties) {
           // Initialize service
           scheduler = Executors.newScheduledThreadPool(1);
       }

       @Override
       public void start() {
           // Start service
           scheduler.scheduleAtFixedRate(() -> {
               // Periodic task
               performScheduledTask();
           }, 0, 60, TimeUnit.SECONDS);
       }

       @Override
       public void stop() {
           // Stop service
           scheduler.shutdown();
       }

       private void performScheduledTask() {
           // Custom service logic
           logger.info("Performing scheduled task");
       }
   }
   ```

2. **Service Examples:**
   - **Monitoring Service:** Health checks, metric collection
   - **Cache Service:** Shared caching layer
   - **Integration Service:** Connect to external services
   - **Scheduler Service:** Custom scheduling logic

**How to Test:**
- Implement service plugin
- Install plugin
- Start server
- Verify service starts
- Verify service functionality
- Stop server
- Verify graceful shutdown

**Expected Behavior:**
- **Background:** Runs independently of channels
- **Lifecycle:** Proper start/stop behavior
- **Thread-Safe:** Handles concurrent access
- **Resource Management:** Cleanup on shutdown
- **Configuration:** Configurable via properties

**Code Location:** Service plugin interface, service plugins in `/server/src/com/mirth/connect/plugins/`

---

### Feature 8.6: Transformer Step Plugin

**Description:**
Create custom transformation steps available in the channel transformer.

**How to Use:**

1. **Step Plugin Interface:**
   ```java
   public class MyTransformerStep implements JavaScriptStep {

       @Override
       public String getScript(Map<String, String> parameters) {
           // Generate JavaScript for this step
           String inputVariable = parameters.get("input");
           String outputVariable = parameters.get("output");

           return String.format(
               "var %s = customTransform(%s);",
               outputVariable, inputVariable
           );
       }

       @Override
       public Map<String, StepPanel> getStepPanels() {
           // Return UI panels for configuration
           return panels;
       }
   }
   ```

2. **Custom Step Example (Encryption):**
   ```java
   public class EncryptStep implements JavaScriptStep {

       @Override
       public String getScript(Map<String, String> params) {
           String algorithm = params.get("algorithm");
           String key = params.get("key");

           return String.format(
               "msg = encrypt(msg, '%s', '%s');",
               algorithm, key
           );
       }
   }
   ```

**How to Test:**
- Implement step plugin
- Install plugin
- Open channel transformer
- Verify custom step appears in step list
- Add step to transformer
- Configure step parameters
- Test transformation

**Expected Behavior:**
- **UI Integration:** Appears in transformer UI
- **Configuration:** Parameters editable
- **Code Generation:** Generates JavaScript
- **Reusable:** Available in all channels

**Code Location:** Transformer step plugins

---

### Feature 8.7: Extension Installation and Management

**Description:**
Install, update, enable/disable, and uninstall extensions via API or UI.

**How to Use:**

1. **Install Extension:**
   ```http
   POST /api/extensions/install
   Content-Type: multipart/form-data

   [Upload plugin JAR file]
   ```

2. **List Installed Extensions:**
   ```http
   GET /api/extensions
   ```

   **Response:**
   ```xml
   <list>
     <extensionMetaData>
       <name>My Plugin</name>
       <author>Example Corp</author>
       <version>1.0.0</version>
       <enabled>true</enabled>
       <path>myplugin</path>
     </extensionMetaData>
   </list>
   ```

3. **Uninstall Extension:**
   ```http
   POST /api/extensions/uninstall/myplugin
   ```

4. **Get Extension Metadata:**
   ```http
   GET /api/extensions/myplugin/metadata
   ```

**How to Test:**
- Install extension via API
- Verify extension appears in list
- Verify extension functionality works
- Uninstall extension
- Verify extension removed
- Verify cleanup complete
- Test install with dependencies
- Test version conflicts

**Expected Behavior:**
- **Validation:** Extension validated before install
- **Dependencies:** Dependency checking
- **Versioning:** Version conflict detection
- **Cleanup:** Complete removal on uninstall
- **Restart:** Some extensions require restart
- **Rollback:** Failed install rolls back

**Code Location:** `ExtensionServlet.java`, `ExtensionController.java`

---

### Feature 8.8: Code Template Libraries

**Description:**
Create and manage reusable code template libraries shared across channels.

**How to Use:**

1. **Create Code Template:**
   ```http
   POST /api/codetemplates
   Content-Type: application/xml

   <codeTemplateLibrary>
     <name>Utility Functions</name>
     <revision>1</revision>
     <codeTemplates>
       <codeTemplate>
         <id>utils-001</id>
         <name>Format Date</name>
         <code>
           function formatDate(date) {
             return new Date(date).toISOString();
           }
         </code>
       </codeTemplate>
     </codeTemplates>
   </codeTemplateLibrary>
   ```

2. **Assign Library to Channel:**
   ```xml
   <channel>
     <codeTemplateLibraries>
       <codeTemplateLibrary id="utils-001"/>
     </codeTemplateLibraries>
   </channel>
   ```

3. **Use in Channel:**
   ```javascript
   // Code templates automatically available
   var formattedDate = formatDate(msg['PID']['PID.7']['PID.7.1']);
   ```

**How to Test:**
- Create code template library
- Add functions to library
- Assign library to channel
- Use functions in transformer
- Verify functions work
- Update library
- Verify channels use updated code

**Expected Behavior:**
- **Global or Scoped:** Available globally or per-channel
- **Versioning:** Track library versions
- **Automatic Loading:** Functions auto-available
- **Shared:** One definition, multiple channels
- **Update:** Changes propagate to all using channels

**Code Location:** Code template management, library assignment

---

### Feature 8.9: Custom Viewers

**Description:**
Develop custom message content viewers for specialized formats.

**How to Use:**

1. **Viewer Plugin:**
   ```java
   public class MyViewerPlugin implements ViewerPlugin {

       @Override
       public String getViewerName() {
           return "My Custom Viewer";
       }

       @Override
       public Component getViewerComponent(String content) {
           // Return Swing/JavaFX component to display content
           return new MyCustomViewer(content);
       }

       @Override
       public boolean supportsContentType(String contentType) {
           return "MY_FORMAT".equals(contentType);
       }
   }
   ```

2. **Viewer Examples:**
   - PDF Viewer
   - DICOM Image Viewer
   - HL7 Tree Viewer
   - JSON Pretty Viewer
   - Custom format visualizers

**How to Test:**
- Implement viewer plugin
- Install plugin
- Send message with compatible format
- Open message in message browser
- Verify custom viewer available
- Verify content displays correctly

**Expected Behavior:**
- **Format-Specific:** Activates for compatible formats
- **UI Integration:** Appears in message browser
- **Rich Display:** Enhanced visualization
- **Read-Only:** View only, no editing

**Code Location:** Viewer plugin interface

---

### Feature 8.10: Plugin Best Practices and Security

**Description:**
Guidelines for developing secure, performant, and maintainable plugins.

**Best Practices:**

1. **Security:**
   - Validate all inputs
   - Sanitize user data
   - Use parameterized queries
   - Encrypt sensitive data
   - Follow principle of least privilege

2. **Performance:**
   - Use connection pooling
   - Cache compiled resources
   - Avoid blocking operations
   - Use async operations where possible
   - Profile and optimize

3. **Error Handling:**
   - Catch and handle all exceptions
   - Log errors with context
   - Provide meaningful error messages
   - Fail gracefully

4. **Resource Management:**
   - Close connections properly
   - Clean up in stop() method
   - Use try-with-resources
   - Avoid memory leaks

5. **Testing:**
   - Unit test plugin code
   - Integration test with OIE
   - Test error scenarios
   - Performance test under load

6. **Documentation:**
   - Document plugin.xml thoroughly
   - Provide user documentation
   - Comment code
   - Include examples

**Code Location:** Plugin development guidelines

---

## Integration Points

- **Channel Management:** Custom connectors used in channels
- **Message Processing:** Custom transformers and data types
- **Security:** Custom authentication providers
- **Administration:** Service plugins for system management

---

## Performance Considerations

- **Class Loading:** Plugins loaded once, cached
- **Isolation:** Separate classloaders prevent conflicts
- **Dependencies:** Shared libraries optimized
- **Initialization:** Lazy loading where possible

---

## Best Practices

1. **Version Compatibility:** Test with target OIE version
2. **Dependencies:** Minimize external dependencies
3. **Documentation:** Comprehensive plugin documentation
4. **Testing:** Thorough testing before deployment
5. **Versioning:** Follow semantic versioning
6. **Security:** Security review before installation

---

## Troubleshooting

**Plugin Won't Load:**
- Verify plugin.xml syntax
- Check class names match
- Review server logs for errors
- Verify dependencies available

**Plugin Errors:**
- Check plugin code for exceptions
- Review error logs
- Verify resource availability
- Test plugin in isolation

---

## Related Documentation

- [Connector Framework](03-connector-framework.md)
- [Data Type Handling](04-data-type-handling.md)
- [Message Processing](02-message-processing.md)
