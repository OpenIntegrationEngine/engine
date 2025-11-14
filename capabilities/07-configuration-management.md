# Capability: Configuration Management

**Category:** System Administration
**Primary Users:** System Administrators
**Related Components:** ConfigurationController, mirth.properties, database configuration

---

## Overview

Configuration Management provides centralized control over server settings, database connections, SSL/TLS certificates, performance tuning parameters, and system-wide defaults. Configuration can be managed through properties files, environment variables, database settings, or REST API.

---

## Features

### Feature 7.1: Server Configuration

**Description:**
Configure core server settings including ports, database connection, SSL/TLS, session timeout, and server identification.

**How to Use:**

1. **Configuration File (`mirth.properties`):**
   ```properties
   # Server Identification
   server.name=OIE Production Server
   server.id=oie-prod-01

   # HTTP/HTTPS Ports
   http.port=8080
   https.port=8443

   # SSL/TLS Configuration
   https.server.keystore=/path/to/keystore.jks
   https.server.keystore.password=changeit
   https.server.keystore.type=JKS

   # Database Configuration
   database=postgres
   database.url=jdbc:postgresql://localhost:5432/mirthdb
   database.username=mirthuser
   database.password=encrypted:ABC123...
   database.max-connections=20

   # Session Configuration
   session.timeout.minutes=4320  # 72 hours

   # Administrator Settings
   administrator.password.minlength=10
   administrator.password.requireupper=true
   ```

2. **Get Server Configuration via API:**
   ```http
   GET /api/configuration/settings
   ```

3. **Update Server Settings:**
   ```http
   PUT /api/configuration/settings
   Content-Type: application/xml

   <serverSettings>
     <settingName>session.timeout.minutes</settingName>
     <settingValue>1440</settingValue>
   </serverSettings>
   ```

4. **Key Settings:**
   - **Server Identity:** Name, ID, description
   - **Network:** HTTP/HTTPS ports, bind addresses
   - **Database:** Connection string, pool size
   - **Security:** Password policy, session timeout
   - **Performance:** Thread pools, memory limits
   - **Paths:** Data directory, temp directory

**How to Test:**
- Modify mirth.properties
- Restart server
- Verify changes applied (check server info API)
- Update setting via API
- Verify change persisted
- Test invalid configuration (should prevent startup)
- Test environment variable override

**Expected Behavior:**
- **File-Based:** Primary configuration in mirth.properties
- **API Override:** Some settings changeable via API
- **Validation:** Invalid settings rejected
- **Restart Required:** Most changes require restart
- **Environment Variables:** Can override file settings
- **Encryption:** Sensitive values (passwords) encrypted

**Code Location:** `ConfigurationController.java`, `mirth.properties` file

---

### Feature 7.2: Database Configuration

**Description:**
Configure database connection, driver selection, connection pooling, and database-specific settings.

**How to Use:**

1. **Supported Databases:**
   - Apache Derby (embedded, default)
   - MySQL / MariaDB
   - PostgreSQL
   - Oracle Database
   - Microsoft SQL Server

2. **Database Properties:**
   ```properties
   # Select database type
   database=mysql

   # Connection URL
   database.url=jdbc:mysql://localhost:3306/mirthdb

   # Credentials
   database.username=mirthuser
   database.password=changeit

   # Connection Pool
   database.max-connections=20
   database.connection-timeout=30000

   # Driver Class (optional, auto-detected)
   database.driver=com.mysql.cj.jdbc.Driver
   ```

3. **Connection Pool Settings (HikariCP):**
   ```properties
   # Pool Configuration
   hikari.maximumPoolSize=20
   hikari.minimumIdle=5
   hikari.connectionTimeout=30000
   hikari.idleTimeout=600000
   hikari.maxLifetime=1800000
   ```

4. **Database Initialization:**
   - On first startup, database schema created automatically
   - Migration scripts applied for version upgrades
   - Schema versions tracked in database

5. **Custom Drivers:**
   - Place JDBC driver JAR in `/server/custom-lib/`
   - Configure driver class in properties
   - Restart server

**How to Test:**
- Configure PostgreSQL connection
- Start server, verify connection successful
- Check connection pool metrics
- Test with invalid credentials (should fail to start)
- Test connection pool exhaustion
- Switch database types (Derby → PostgreSQL)
- Verify data migrated correctly

**Expected Behavior:**
- **Auto-Detection:** Driver detected from URL
- **Connection Pooling:** HikariCP for performance
- **Validation:** Connection tested on startup
- **Migration:** Automatic schema upgrades
- **Failover:** Startup fails if database unreachable
- **Custom Drivers:** Support for any JDBC driver

**Code Location:** `mirth.properties`, database connection initialization, HikariCP configuration

---

### Feature 7.3: SSL/TLS Configuration

**Description:**
Configure SSL/TLS certificates for HTTPS web server and connector-level encryption.

**How to Use:**

1. **HTTPS Configuration:**
   ```properties
   # Enable HTTPS
   https.port=8443

   # Keystore Configuration
   https.server.keystore=/path/to/keystore.jks
   https.server.keystore.password=changeit
   https.server.keystore.type=JKS

   # Client Certificate Authentication (optional)
   https.client.auth=WANT  # NONE, WANT, NEED

   # TLS Protocols
   https.protocols=TLSv1.2,TLSv1.3

   # Cipher Suites (comma-separated)
   https.ciphersuites=TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,...
   ```

2. **Create Keystore:**
   ```bash
   # Generate self-signed certificate (testing only)
   keytool -genkey -alias mirth -keyalg RSA -keysize 2048 \
     -keystore keystore.jks -validity 365

   # Import CA-signed certificate
   keytool -import -alias mirth -file server.crt \
     -keystore keystore.jks
   ```

3. **Connector SSL:**
   - HTTP Connector: Configure SSL context
   - TCP Connector: TLS socket factory
   - SMTP Connector: SMTPS/STARTTLS
   - File Connector: SFTP with SSH keys

4. **Truststore Configuration:**
   ```properties
   # For validating remote certificates
   https.client.truststore=/path/to/truststore.jks
   https.client.truststore.password=changeit
   ```

**How to Test:**
- Generate test keystore
- Configure HTTPS
- Access server via https://
- Verify certificate presented
- Test with invalid certificate (should show warning)
- Test cipher suite restriction
- Test client certificate authentication
- Test TLS 1.3 connection

**Expected Behavior:**
- **TLS 1.2/1.3:** Modern TLS versions
- **Strong Ciphers:** Default to secure cipher suites
- **Certificate Validation:** Validates chains
- **SNI Support:** Server Name Indication
- **Client Certs:** Optional mutual TLS

**Code Location:** Jetty SSL configuration, `mirth.properties`

---

### Feature 7.4: Performance Tuning

**Description:**
Configure JVM memory, thread pools, garbage collection, and performance optimization parameters.

**How to Use:**

1. **JVM Memory Configuration (`.vmoptions` files):**
   ```
   # mcserver.vmoptions
   -Xms2048m      # Initial heap size
   -Xmx4096m      # Maximum heap size
   -Xss256k       # Thread stack size

   # Garbage Collection (G1GC)
   -XX:+UseG1GC
   -XX:MaxGCPauseMillis=200
   -XX:G1HeapRegionSize=16m

   # GC Logging
   -Xlog:gc*:file=gc.log:time,level,tags
   ```

2. **Thread Pool Configuration:**
   ```properties
   # mirth.properties

   # Global Thread Pool
   server.threads.min=10
   server.threads.max=100

   # Channel Thread Pool (per channel)
   channel.threads.min=1
   channel.threads.max=10

   # Queue Thread Pool
   queue.threads=5
   ```

3. **Database Performance:**
   ```properties
   # Connection Pool
   database.max-connections=20

   # Statement Cache
   database.prepared-statement-cache-size=250
   ```

4. **Message Processing:**
   ```properties
   # Batch Size
   message.batch-size=100

   # Queue Buffer
   queue.buffer-size=1000
   ```

**How to Test:**
- Configure different heap sizes
- Load test with various settings
- Monitor GC pauses
- Test thread pool under load
- Measure throughput with different batch sizes
- Profile memory usage
- Test OOM scenarios

**Expected Behavior:**
- **Tunable:** Configurable for different workloads
- **Defaults:** Reasonable defaults for typical use
- **Scalable:** Support high-throughput scenarios
- **GC Options:** Support different GC algorithms
- **Monitoring:** JMX metrics available

**Code Location:** `.vmoptions` files in `/server/conf/`, `mirth.properties`

---

### Feature 7.5: Logging Configuration

**Description:**
Configure log levels, log rotation, log format, and per-component logging.

**How to Use:**

1. **Log4j2 Configuration (`log4j2.properties`):**
   ```properties
   # Root Logger
   rootLogger.level=INFO
   rootLogger.appenderRef.file.ref=FileAppender

   # File Appender
   appender.file.type=RollingFile
   appender.file.name=FileAppender
   appender.file.fileName=logs/mirth.log
   appender.file.filePattern=logs/mirth-%d{yyyy-MM-dd}.log
   appender.file.layout.type=PatternLayout
   appender.file.layout.pattern=%d{ISO8601} [%t] %-5level %logger - %msg%n

   # Rolling Policy
   appender.file.policies.type=Policies
   appender.file.policies.time.type=TimeBasedTriggeringPolicy
   appender.file.policies.size.type=SizeBasedTriggeringPolicy
   appender.file.policies.size.size=100MB

   # Retention
   appender.file.strategy.type=DefaultRolloverStrategy
   appender.file.strategy.max=30

   # Per-Component Logging
   logger.mirth.name=com.mirth.connect
   logger.mirth.level=DEBUG

   logger.donkey.name=com.mirth.connect.donkey
   logger.donkey.level=INFO

   logger.http.name=org.eclipse.jetty
   logger.http.level=WARN
   ```

2. **Log Levels:**
   - **TRACE:** Very detailed, for diagnosis
   - **DEBUG:** Detailed debugging information
   - **INFO:** Informational messages
   - **WARN:** Warning conditions
   - **ERROR:** Error conditions
   - **FATAL:** Critical errors

3. **Dynamic Log Level Change:**
   ```http
   PUT /api/system/loglevel
   Content-Type: application/x-www-form-urlencoded

   package=com.mirth.connect.server&level=DEBUG
   ```

**How to Test:**
- Set log level to DEBUG
- Verify detailed logging appears
- Change log level at runtime
- Test log rotation (by size and date)
- Verify old logs retained per policy
- Test per-component logging
- Check log format customization

**Expected Behavior:**
- **Rotation:** Automatic by size and/or date
- **Retention:** Configurable history
- **Performance:** Async logging, minimal overhead
- **Per-Component:** Fine-grained control
- **Runtime Change:** Some levels changeable without restart

**Code Location:** `log4j2.properties`, Log4j2 configuration

---

### Feature 7.6: Channel Defaults

**Description:**
Set default values for new channels including data types, encoding, queue settings, and processing parameters.

**How to Use:**

1. **Channel Defaults Configuration:**
   ```properties
   # mirth.properties

   # Default Data Type
   channel.default.datatype=HL7V2

   # Default Encoding
   channel.default.encoding=UTF-8

   # Queue Settings
   channel.default.queue.enabled=true
   channel.default.queue.rotate=true

   # Processing
   channel.default.threads=1
   channel.default.processingthreads=1
   ```

2. **Apply to New Channels:**
   - Defaults applied when creating channel
   - Can be overridden per channel
   - Template channels (clone with defaults)

**How to Test:**
- Set default data type
- Create new channel
- Verify data type matches default
- Override default in channel
- Verify override takes precedence

**Expected Behavior:**
- **New Channels Only:** Existing channels unaffected
- **Overridable:** Defaults can be changed per channel
- **Templates:** Serve as starting point

**Code Location:** `mirth.properties`, channel creation logic

---

### Feature 7.7: Resource and Library Management

**Description:**
Manage external resources (database connections, file paths, URLs) and code template libraries centrally.

**How to Use:**

1. **Resource Management:**
   ```http
   POST /api/resources
   Content-Type: application/xml

   <resourceProperties>
     <name>External DB Connection</name>
     <type>DATABASE</type>
     <properties>
       <url>jdbc:mysql://external:3306/db</url>
       <username>user</username>
       <password>pass</password>
     </properties>
   </resourceProperties>
   ```

2. **Use Resource in Channel:**
   ```javascript
   // Access configured resource
   var connection = ResourceUtil.getConnection('External DB Connection');
   var statement = connection.createStatement();
   // ... execute queries
   ```

3. **Resource Types:**
   - Database connections
   - File directories
   - HTTP endpoints
   - Custom resources

4. **Code Template Libraries:**
   ```http
   GET /api/codetemplates
   ```
   - Organize reusable code into libraries
   - Assign libraries to channels
   - Global utilities available to all channels

**How to Test:**
- Create database resource
- Use resource in channel script
- Verify connection works
- Update resource properties
- Verify channels use updated resource
- Test resource access control

**Expected Behavior:**
- **Centralized:** Single definition, multiple uses
- **Secure:** Credentials encrypted
- **Reusable:** Share across channels
- **Versioned:** Track changes to resources

**Code Location:** Resource management, code template library management

---

### Feature 7.8: Environment-Specific Configuration

**Description:**
Support different configurations for development, testing, staging, and production environments.

**How to Use:**

1. **Environment Variables:**
   ```bash
   export MIRTH_DATABASE_URL=jdbc:postgresql://prod-db:5432/mirth
   export MIRTH_HTTPS_PORT=8443
   ```

2. **Properties File Override:**
   ```properties
   # mirth.properties
   database.url=${env:MIRTH_DATABASE_URL}
   https.port=${env:MIRTH_HTTPS_PORT}
   ```

3. **External Configuration:**
   ```bash
   # Load configuration from external file
   java -Dmirth.config=/etc/mirth/production.properties ...
   ```

4. **Configuration Profiles:**
   - `mirth-dev.properties`
   - `mirth-test.properties`
   - `mirth-prod.properties`

**How to Test:**
- Set environment variables
- Verify server uses environment values
- Override with external config file
- Test different profiles
- Verify precedence (env > file > defaults)

**Expected Behavior:**
- **Environment Variables:** Override file settings
- **External Files:** Support for external configuration
- **Profiles:** Easy switching between environments
- **Precedence:** Clear override hierarchy

**Code Location:** Configuration loading, property resolution

---

### Feature 7.9: System Maintenance Configuration

**Description:**
Configure automatic maintenance tasks including message pruning, database optimization, and backup schedules.

**How to Use:**

1. **Message Pruning Schedule:**
   ```properties
   # Cron expression (daily at 2 AM)
   database.prune.schedule=0 0 2 * * ?

   # Retention period
   database.prune.retention.days=365

   # Channels to prune (all if not specified)
   database.prune.channels=channel-001,channel-002
   ```

2. **Database Optimization:**
   ```properties
   # Optimize tables weekly
   database.optimize.schedule=0 0 3 * * SUN

   # Rebuild indexes
   database.reindex.schedule=0 0 4 1 * ?  # Monthly
   ```

3. **Backup Schedule:**
   ```properties
   # Daily configuration backup
   backup.schedule=0 0 1 * * ?
   backup.retention.count=30
   backup.path=/backups/mirth/
   ```

**How to Test:**
- Configure pruning schedule
- Verify task runs at scheduled time
- Check old messages deleted
- Test database optimization
- Verify backup created
- Test backup retention (old backups deleted)

**Expected Behavior:**
- **Automated:** Runs without manual intervention
- **Scheduled:** Cron-based scheduling
- **Configurable:** Flexible retention policies
- **Monitored:** Task execution logged

**Code Location:** Scheduled task configuration, database maintenance

---

### Feature 7.10: Configuration Validation and Migration

**Description:**
Validate configuration correctness and migrate configuration between server versions.

**How to Use:**

1. **Validation on Startup:**
   - Server validates configuration on start
   - Invalid settings prevent startup
   - Detailed error messages

2. **Configuration Migration:**
   - Export from old version
   - Import to new version
   - Automatic schema upgrades

3. **Validation API:**
   ```http
   POST /api/configuration/validate
   Content-Type: application/xml

   <serverSettings>
     <!-- configuration to validate -->
   </serverSettings>
   ```

4. **Migration Path:**
   - Version detection
   - Schema conversion
   - Data migration
   - Compatibility warnings

**How to Test:**
- Configure invalid setting
- Attempt server start (should fail with clear error)
- Export config from old version
- Import to new version
- Verify all settings migrated
- Test validation API

**Expected Behavior:**
- **Validation:** Comprehensive checking
- **Clear Errors:** Specific error messages
- **Migration:** Automatic version upgrades
- **Backwards Compatible:** Import old configurations
- **Safe:** Validation prevents invalid configs

**Code Location:** Configuration validation, migration logic

---

## Integration Points

- **Channel Management:** Channel defaults and templates
- **Security:** SSL/TLS, password policies
- **Performance:** JVM tuning, thread pools
- **Database:** Connection configuration

---

## Performance Considerations

- **Connection Pooling:** Critical for database performance
- **Thread Pools:** Size appropriately for workload
- **JVM Memory:** Balance heap size vs. GC pauses
- **Logging:** Async logging reduces overhead

---

## Best Practices

1. **Documentation:** Document all configuration changes
2. **Version Control:** Track mirth.properties in Git
3. **Environment-Specific:** Use separate configs per environment
4. **Backups:** Regular configuration backups
5. **Validation:** Test configuration changes in dev first
6. **Security:** Encrypt sensitive values
7. **Monitoring:** Monitor key settings (memory, connections)

---

## Troubleshooting

**Server Won't Start:**
- Check configuration syntax
- Verify database connectivity
- Review startup logs
- Validate keystore paths/passwords

**Performance Issues:**
- Increase heap memory
- Tune GC settings
- Increase thread pools
- Optimize database connections

**Configuration Not Applied:**
- Verify restart performed
- Check property name spelling
- Review override precedence

---

## Related Documentation

- [Administration & Monitoring](06-administration-monitoring.md)
- [Security & Authorization](05-security-authorization.md)
- [ARCHITECTURE.md](../ARCHITECTURE.md)
