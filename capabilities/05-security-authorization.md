# Capability: Security & Authorization

**Category:** Security & Compliance
**Primary Users:** Administrators, Compliance Officers, Security Teams
**Related Components:** UserController, Authentication plugins, Authorization framework

---

## Overview

Security & Authorization provides comprehensive protection for healthcare data through authentication, role-based access control, encryption, audit logging, and HIPAA compliance features. OIE implements defense-in-depth security to protect PHI (Protected Health Information) and ensure regulatory compliance.

---

## Features

### Feature 5.1: User Authentication

**Description:**
Authenticate users with username/password credentials, LDAP/Active Directory integration, or custom authentication providers. Supports session management and multi-factor authentication (via plugins).

**How to Use:**

1. **Database Authentication (Default):**
   ```http
   POST /api/users/login
   Content-Type: application/x-www-form-urlencoded

   username=admin&password=admin
   ```

   **Response:**
   ```xml
   <loginStatus>
     <status>SUCCESS</status>
     <message>User logged in successfully</message>
   </loginStatus>
   ```

2. **Create User:**
   ```http
   POST /api/users
   Content-Type: application/xml

   <user>
     <username>john.doe</username>
     <password>SecureP@ssw0rd!</password>
     <firstName>John</firstName>
     <lastName>Doe</lastName>
     <email>john.doe@hospital.org</email>
   </user>
   ```

3. **Password Requirements:**
   - Configurable minimum length
   - Complexity requirements (uppercase, lowercase, numbers, special chars)
   - Password history (prevent reuse)
   - Expiration policy
   - Account lockout after failed attempts

4. **Session Management:**
   - Session timeout (default: 72 hours)
   - Concurrent session limits
   - Force logout capability
   - Session invalidation on password change

**How to Test:**
- **Valid Login:**
  ```bash
  curl -X POST http://localhost:8080/api/users/login \
    -d "username=admin&password=admin"
  ```
- **Invalid Credentials:** Verify error message, no sensitive information leaked
- **Account Lockout:** Try multiple failed logins, verify account locks
- **Session Timeout:** Wait for timeout, verify session invalidated
- **Password Change:** Change password, verify old sessions invalidated
- **Concurrent Logins:** Login from multiple locations

**Expected Behavior:**
- **Password Hashing:** Passwords hashed with salt (PBKDF2)
- **Timing Attack Prevention:** Constant-time password comparison
- **Session Security:** Secure session tokens, HTTP-only cookies
- **Failed Login Tracking:** Log failed attempts with IP address
- **Account Lockout:** Temporary lock after configurable failed attempts
- **Password Complexity:** Enforce configured password policies
- **Session Persistence:** Sessions survive server restart (optional)

**Code Location:** `UserController.java`, `DefaultAuthenticationProvider.java`

---

### Feature 5.2: Role-Based Access Control (RBAC)

**Description:**
Control access to channels, messages, and administrative functions through role-based permissions. Users assigned to roles, roles granted permissions.

**How to Use:**

1. **Permission Model:**
   - **Channel Permissions:**
     - View channels
     - Edit channels
     - Deploy/undeploy channels
     - View messages
     - Process messages (send raw)
     - Export messages
     - Remove messages

   - **Administrative Permissions:**
     - Manage users
     - Manage alerts
     - Manage code templates
     - View server settings
     - Modify server settings
     - View logs
     - View dashboard

   - **API Permissions:**
     - Access REST API endpoints
     - Execute specific operations

2. **Assign Permissions:**
   ```xml
   <user>
     <username>integration.engineer</username>
     <channelPermissions>
       <channelId>channel-001</channelId>
       <channelId>channel-002</channelId>
     </channelPermissions>
   </user>
   ```

3. **Default vs. Explicit Authorization:**
   - **Default Authorization:** User has access to all channels
   - **Explicit Authorization:** User restricted to specific channels only

**How to Test:**
- Create user with limited permissions
- Attempt to view channel (should succeed if permitted)
- Attempt to edit channel (should fail if not permitted)
- Attempt to deploy channel (verify permission check)
- Access API endpoint without permission (should return 403)
- Verify channel filtering (users only see permitted channels)
- Test admin vs. non-admin permissions

**Expected Behavior:**
- **Permission Enforcement:** All operations check permissions
- **Fail-Secure:** Default deny, explicit allow
- **Audit Trail:** Permission denials logged
- **API Protection:** REST API enforces same permissions
- **Channel Filtering:** UI shows only permitted channels
- **Granular Control:** Per-channel, per-operation permissions
- **Inheritance:** Permissions can be role-based (via plugins)

**Code Location:** `ChannelAuthorizer.java`, `AuthorizationController.java`, servlet filters

---

### Feature 5.3: Encryption

**Description:**
Encrypt sensitive data at rest and in transit using TLS/SSL, password encryption, and configurable encryption for messages and configuration.

**How to Use:**

1. **TLS/SSL Configuration:**

   **HTTPS for Web Server:**
   ```properties
   # mirth.properties
   https.port=8443
   https.server.keystore=/path/to/keystore.jks
   https.server.keystore.password=changeit
   https.server.keystore.type=JKS
   ```

   **Connector SSL/TLS:**
   - HTTP Connector: Enable HTTPS, configure client certificates
   - TCP Connector: Enable TLS, configure keystore/truststore
   - SMTP Connector: Enable SMTPS or STARTTLS
   - File Connector: SFTP with SSH keys

2. **Password Encryption:**
   ```java
   // Passwords encrypted in database
   // Using PBKDF2 with SHA-256
   // Random salt per password
   ```

3. **Message Encryption (via Configuration):**
   ```javascript
   // Encrypt sensitive fields
   var Encryptor = Packages.com.mirth.connect.server.util.Encryptor;
   var encrypted = Encryptor.getInstance().encrypt(sensitiveData);

   // Decrypt
   var decrypted = Encryptor.getInstance().decrypt(encrypted);
   ```

4. **Configuration Encryption:**
   - Database connection strings encrypted
   - Connector passwords encrypted
   - Custom encryption key supported

**How to Test:**

**HTTPS:**
```bash
curl -k https://localhost:8443/api/server/version
```

**TLS Connectors:**
- Configure TCP connector with TLS
- Send message over TLS connection
- Verify encryption (packet capture shows encrypted data)
- Test certificate validation
- Test client certificate authentication

**Password Encryption:**
- Create user with password
- Inspect database, verify password hashed
- Verify salt is unique per password
- Test password verification works

**Expected Behavior:**
- **TLS 1.2/1.3:** Modern TLS versions supported
- **Strong Ciphers:** Configurable cipher suites, defaults to strong ciphers
- **Certificate Validation:** Validates server certificates (optional client certs)
- **Password Security:** PBKDF2 with high iteration count
- **Salt Randomness:** Cryptographically random salts
- **Key Management:** Encryption keys protected
- **No Plaintext:** Sensitive data never stored in plaintext

**Code Location:** `Encryptor.java`, `Digester.java`, Bouncy Castle library, Jetty SSL configuration

---

### Feature 5.4: Audit Logging

**Description:**
Comprehensive audit trail of all user actions, system events, message access, and configuration changes for compliance and troubleshooting.

**How to Use:**

1. **Query Audit Events:**
   ```http
   GET /api/events?level=INFO&outcome=SUCCESS&userId=123
   ```

   **Response:**
   ```xml
   <list>
     <event>
       <id>1001</id>
       <dateCreated>2025-01-14T10:30:00Z</dateCreated>
       <level>INFO</level>
       <outcome>SUCCESS</outcome>
       <name>USER_LOGIN</name>
       <userId>123</userId>
       <ipAddress>192.168.1.100</ipAddress>
       <attributes>
         <entry>
           <key>username</key>
           <value>john.doe</value>
         </entry>
       </attributes>
     </event>
   </list>
   ```

2. **Event Types:**

   **User Events:**
   - USER_LOGIN, USER_LOGOUT
   - USER_CREATED, USER_UPDATED, USER_DELETED
   - PASSWORD_CHANGED, ACCOUNT_LOCKED

   **Channel Events:**
   - CHANNEL_CREATED, CHANNEL_UPDATED, CHANNEL_DELETED
   - CHANNEL_DEPLOYED, CHANNEL_UNDEPLOYED
   - CHANNEL_STARTED, CHANNEL_STOPPED, CHANNEL_PAUSED

   **Message Events:**
   - MESSAGE_RECEIVED, MESSAGE_SENT, MESSAGE_FILTERED, MESSAGE_ERROR
   - MESSAGE_VIEWED (PHI access)
   - MESSAGE_EXPORTED (bulk PHI export)
   - MESSAGE_REMOVED

   **Configuration Events:**
   - CONFIGURATION_CHANGED
   - EXTENSION_INSTALLED, EXTENSION_UNINSTALLED
   - ALERT_CREATED, ALERT_TRIGGERED

3. **Event Levels:**
   - DEBUG: Detailed diagnostic information
   - INFO: General informational events
   - WARNING: Warning conditions
   - ERROR: Error conditions

4. **Event Outcomes:**
   - SUCCESS: Operation completed successfully
   - FAILURE: Operation failed

5. **PHI Access Auditing:**
   ```http
   GET /api/events?name=MESSAGE_VIEWED&userId=123
   ```
   - Tracks who accessed which messages
   - HIPAA compliance requirement

**How to Test:**
- Perform user login, verify USER_LOGIN event created
- Create channel, verify CHANNEL_CREATED event
- View message, verify MESSAGE_VIEWED event (PHI access)
- Export messages, verify MESSAGE_EXPORTED event
- Failed login, verify event with FAILURE outcome
- Filter events by date range
- Filter events by user ID
- Filter events by outcome
- Verify IP address captured
- Test event retention (old events pruned based on policy)

**Expected Behavior:**
- **All Actions Logged:** Every user action creates audit event
- **Immutable:** Events cannot be modified or deleted by users
- **Detailed Context:** Events include user, IP, timestamp, affected resources
- **Performance:** Async logging, minimal performance impact
- **Retention:** Configurable retention period
- **Export:** Events can be exported for external analysis
- **Real-Time:** Events visible immediately after action
- **Searchable:** Rich filtering and search capabilities

**Code Location:** `EventController.java`, `Event.java` model, database event logging

---

### Feature 5.5: HIPAA Compliance Features

**Description:**
Built-in features to support HIPAA compliance including PHI access tracking, encryption, audit logs, user authentication, and access controls.

**HIPAA Requirements Supported:**

1. **Access Control (§164.312(a)):**
   - Unique user identification (usernames)
   - Emergency access procedures (admin override)
   - Automatic logoff (session timeout)
   - Encryption and decryption (TLS, password encryption)

2. **Audit Controls (§164.312(b)):**
   - Complete audit trail of PHI access
   - User action logging
   - System event logging
   - Audit log protection (immutable events)

3. **Integrity (§164.312(c)):**
   - Message checksums (detect tampering)
   - Transaction integrity (database ACID)
   - Error detection and correction

4. **Person or Entity Authentication (§164.312(d)):**
   - User authentication (password-based)
   - Optional multi-factor authentication
   - Session management

5. **Transmission Security (§164.312(e)):**
   - TLS/SSL for all network communication
   - Encryption of PHI in transit
   - VPN support (network-level)

**How to Use:**

1. **Enable HIPAA Mode:**
   ```properties
   # mirth.properties
   hipaa.enabled=true
   ```

2. **Configure PHI Retention:**
   ```properties
   # Message retention policy
   message.retention.days=2555  # 7 years for HIPAA
   ```

3. **Audit Trail Export:**
   ```http
   GET /api/events?startDate=2024-01-01&endDate=2024-12-31
   ```
   - Export annual audit logs
   - Required for HIPAA compliance audits

4. **User Access Report:**
   ```http
   GET /api/events?name=MESSAGE_VIEWED&startDate=2025-01-01
   ```
   - Who accessed which messages
   - When and from where (IP address)

**How to Test:**
- Access message, verify audit event created with user/IP/timestamp
- Export audit logs for date range
- Verify encryption enabled for all PHI transmission
- Test session timeout enforcement
- Verify password complexity requirements
- Test account lockout after failed logins
- Review audit trail completeness
- Test message retention policy (old messages pruned)

**Expected Behavior:**
- **PHI Protection:** All PHI encrypted in transit, access logged
- **Audit Trail:** Complete, tamper-proof audit logs
- **Access Control:** Granular, enforced at all layers
- **Retention:** Configurable retention with automatic pruning
- **Compliance Reports:** Generate reports for auditors
- **Documentation:** HIPAA compliance guide included

**Note:** OIE provides technical controls to support HIPAA compliance. Organizations must also implement administrative and physical safeguards, policies, and procedures.

**Code Location:** Event logging, encryption utilities, access control framework

---

### Feature 5.6: IP Address Filtering and Whitelisting

**Description:**
Restrict access to OIE server and specific channels based on source IP address or IP range.

**How to Use:**

1. **Web Server IP Filtering:**
   ```properties
   # mirth.properties
   http.allow.ips=192.168.1.0/24,10.0.0.50
   ```

2. **Channel-Level IP Filtering:**
   ```javascript
   // In channel preprocessor
   var sourceIp = sourceMap.get('remoteAddress');
   if (sourceIp !== '192.168.1.100') {
     throw new Error('Access denied from IP: ' + sourceIp);
   }
   ```

3. **Connector IP Binding:**
   ```xml
   <properties>
     <listenerConnectorProperties>
       <host>192.168.1.10</host>  <!-- Bind to specific interface -->
     </listenerConnectorProperties>
   </properties>
   ```

**How to Test:**
- Configure IP whitelist
- Access from allowed IP (should succeed)
- Access from disallowed IP (should fail)
- Test IP range (CIDR notation)
- Test multiple allowed IPs

**Expected Behavior:**
- **Whitelist Enforcement:** Only allowed IPs can connect
- **Logged Denials:** Blocked attempts logged
- **CIDR Support:** IP ranges supported
- **Per-Channel:** Different restrictions per channel

**Code Location:** Jetty connector configuration, channel authorization

---

### Feature 5.7: Secure Password Management

**Description:**
Comprehensive password security including hashing, salting, complexity requirements, expiration, and history.

**How to Use:**

1. **Password Policy Configuration:**
   ```properties
   # mirth.properties
   password.minlength=10
   password.requireuppercase=true
   password.requirelowercase=true
   password.requirenumber=true
   password.requirespecial=true
   password.expirationdays=90
   password.historylength=5
   ```

2. **Password Change:**
   ```http
   POST /api/users/{userId}/password
   Content-Type: application/x-www-form-urlencoded

   oldPassword=OldP@ss123&newPassword=NewP@ss456
   ```

3. **Force Password Change:**
   ```xml
   <user>
     <username>john.doe</username>
     <forcePasswordChange>true</forcePasswordChange>
   </user>
   ```

**How to Test:**
- Set password policy
- Attempt weak password (should fail)
- Attempt password without special character (should fail)
- Attempt to reuse recent password (should fail)
- Change password successfully
- Verify old password no longer works
- Test password expiration (set short expiration, wait, verify forced change)
- Test force password change on next login

**Expected Behavior:**
- **Hashing:** PBKDF2-SHA256 with high iteration count
- **Salting:** Unique random salt per password
- **Complexity:** Configurable requirements enforced
- **History:** Prevent password reuse
- **Expiration:** Force periodic password changes
- **Secure Storage:** Never store plaintext passwords
- **Audit:** Password changes logged

**Code Location:** `UserController.java`, `Digester.java`, password validation

---

### Feature 5.8: Session Security

**Description:**
Secure session management with timeout, token protection, and concurrent session control.

**How to Use:**

1. **Session Timeout:**
   ```properties
   # mirth.properties
   session.timeout.minutes=4320  # 72 hours default
   ```

2. **Session Token:**
   - Returned on successful login
   - Included in Authorization header or session cookie
   - Invalidated on logout

3. **Force Logout:**
   ```http
   POST /api/users/logout
   Authorization: {sessionToken}
   ```

4. **Session Management:**
   - View active sessions
   - Terminate specific session
   - Terminate all sessions for user

**How to Test:**
- Login, verify session token returned
- Make API call with session token (should succeed)
- Logout, attempt API call with same token (should fail)
- Wait for session timeout, verify session invalidated
- Login from multiple locations, verify concurrent sessions
- Force logout one session, verify others unaffected
- Change password, verify all sessions invalidated

**Expected Behavior:**
- **Token Security:** Cryptographically random session tokens
- **HTTP-Only Cookies:** Prevent JavaScript access
- **Secure Flag:** Cookies only sent over HTTPS
- **Session Fixation Protection:** New token on authentication
- **Concurrent Session Limits:** Configurable max sessions per user
- **Timeout:** Automatic invalidation after inactivity
- **Logout:** Immediate session invalidation

**Code Location:** Session management in servlet filters, `UserController.java`

---

### Feature 5.9: Multi-Factor Authentication (MFA)

**Description:**
Optional multi-factor authentication via authentication plugins for enhanced security.

**How to Use:**

1. **Install MFA Plugin:**
   - TOTP (Time-based One-Time Password)
   - SMS-based codes
   - Hardware tokens
   - Custom MFA provider

2. **Enable for User:**
   ```xml
   <user>
     <username>john.doe</username>
     <mfaEnabled>true</mfaEnabled>
   </user>
   ```

3. **MFA Login Flow:**
   - User provides username/password
   - System requests MFA code
   - User provides code from authenticator app
   - System validates and grants access

**How to Test:**
- Enable MFA for user
- Login with password only (should request MFA code)
- Provide invalid MFA code (should fail)
- Provide valid MFA code (should succeed)
- Test backup codes
- Test MFA reset procedure

**Expected Behavior:**
- **TOTP Support:** RFC 6238 compliant
- **QR Code Enrollment:** Easy setup with authenticator apps
- **Backup Codes:** Single-use recovery codes
- **MFA Bypass:** Emergency admin access
- **Audit:** MFA events logged

**Code Location:** Authentication plugin framework, MFA plugin implementations

---

### Feature 5.10: Secure Communication

**Description:**
Ensure all communication channels use encryption and secure protocols.

**Protocols Supported:**
- **HTTPS:** Web UI, REST API
- **TLS:** TCP connectors, DICOM, SMTP
- **SFTP:** File connector
- **VPN:** Network-level encryption (external)

**How to Use:**

1. **Force HTTPS:**
   ```properties
   # mirth.properties
   http.port=0  # Disable HTTP
   https.port=8443  # Enable HTTPS only
   ```

2. **Connector TLS:**
   - Configure keystore/truststore
   - Enable TLS in connector properties
   - Validate certificates

3. **Certificate Management:**
   ```bash
   # Import certificate
   keytool -import -alias server-cert -file server.crt \
     -keystore keystore.jks
   ```

**How to Test:**
- Access server via HTTP (should redirect to HTTPS or fail)
- Verify certificate presented
- Test with self-signed certificate
- Test with expired certificate (should fail if validation enabled)
- Test client certificate authentication
- Test cipher suite configuration

**Expected Behavior:**
- **Strong Encryption:** TLS 1.2/1.3, AES-256
- **Certificate Validation:** Validates server certs
- **Client Certificates:** Optional mutual TLS
- **Cipher Suites:** Configurable, defaults secure
- **Perfect Forward Secrecy:** Supported cipher suites

**Code Location:** Jetty SSL configuration, connector SSL/TLS implementations

---

## Integration Points

- **Channel Management:** Channels enforce access control
- **Message Processing:** Messages encrypted in transit
- **Administration:** Admin functions require permissions
- **API:** All API calls authenticated and authorized

---

## Performance Considerations

- **Authentication:** Password hashing CPU-intensive (by design)
- **Encryption:** TLS adds latency (minimal with hardware acceleration)
- **Audit Logging:** Async logging minimizes performance impact
- **Session Storage:** In-memory or database-backed sessions

---

## Best Practices

1. **Passwords:** Enforce strong password policy
2. **MFA:** Enable for administrative users
3. **TLS:** Use TLS everywhere
4. **Audit:** Regularly review audit logs
5. **Least Privilege:** Grant minimum necessary permissions
6. **Certificates:** Use valid certificates from trusted CA
7. **Updates:** Keep security patches current
8. **Network:** Use firewalls, VPNs for network security

---

## Troubleshooting

**Login Failures:**
- Check username/password correct
- Verify account not locked
- Check audit log for failure reason

**Permission Denied:**
- Review user permissions
- Check channel authorization
- Verify API endpoint permissions

**TLS Issues:**
- Verify certificate validity
- Check keystore configuration
- Review cipher suite compatibility
- Check certificate chain

---

## Related Documentation

- [Administration & Monitoring](06-administration-monitoring.md)
- [API & Integration](10-api-integration.md)
- [ARCHITECTURE.md](../ARCHITECTURE.md)
