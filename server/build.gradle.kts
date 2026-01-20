import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future

plugins {
    `java-library`
    id("com.netflix.nebula.ospackage")
    distribution
}

description = "Mirth Connect Server - Main server component"

val mirthVersion: String by rootProject.extra

// Server uses traditional src/ layout (not Maven-style)
sourceSets {
    main {
        java {
            srcDir("src")
        }
        resources {
            srcDirs("conf", "dbconf")
        }
    }
    test {
        java {
            srcDir("test")
        }
    }
}

dependencies {
    // Project dependencies
    api(project(":donkey"))

    // Apache Commons
    api(libs.commons.beanutils)
    api(libs.commons.cli)
    api(libs.commons.codec)
    api(libs.commons.collections)
    api(libs.commons.collections4)
    api(libs.commons.compress)
    api(libs.commons.configuration2)
    api(libs.commons.dbcp2)
    api(libs.commons.dbutils)
    api(libs.commons.digester3)
    api(libs.commons.el)
    api(libs.commons.email)
    api(libs.commons.fileupload)
    api(libs.commons.httpclient.legacy)
    api(libs.commons.io)
    api(libs.commons.jxpath)
    api(libs.commons.lang3)
    api(libs.commons.logging)
    api(libs.commons.math3)
    api(libs.commons.net)
    api(libs.commons.pool2)
    api(libs.commons.text)
    api(libs.commons.vfs2)

    // HTTP Client
    api(libs.httpclient)
    api(libs.httpcore)
    api(libs.httpmime)

    // Logging
    api(libs.log4j.api)
    api(libs.log4j.core)
    api(libs.log4j.bridge)
    api(libs.slf4j.api)
    implementation(libs.slf4j.log4j12)

    // Security
    api(libs.bcprov.jdk18on)
    api(libs.bcpkix.jdk18on)
    api(libs.bcutil.jdk18on)
    api(libs.not.yet.commons.ssl)

    // JSON/XML
    api(libs.jackson.annotations)
    api(libs.jackson.core)
    api(libs.jackson.databind)
    api(libs.jackson.dataformat.cbor)
    api(libs.jackson.dataformat.yaml)
    api(libs.jackson.datatype.jsr310)
    api(libs.xstream)
    api(libs.xpp3)
    api(libs.staxon)
    api(libs.jdom2)

    // Database
    api(libs.hikaricp)
    api(libs.mybatis)
    implementation(libs.derby)
    implementation(libs.derbytools)
    implementation(libs.jtds)
    implementation(libs.sqlite.jdbc)
    implementation(libs.mysql.connector)
    implementation(libs.mssql.jdbc)
    implementation(libs.postgresql)
    implementation(libs.ojdbc8)

    // Jetty
    api(libs.jetty.annotations)
    api(libs.jetty.continuation)
    api(libs.jetty.http)
    api(libs.jetty.io)
    api(libs.jetty.jndi)
    api(libs.jetty.plus)
    api(libs.jetty.rewrite)
    api(libs.jetty.security)
    api(libs.jetty.server)
    api(libs.jetty.servlet)
    api(libs.jetty.util)
    api(libs.jetty.util.ajax)
    api(libs.jetty.webapp)
    api(libs.jetty.xml)
    api(libs.jetty.schemas)
    api(libs.apache.jsp)
    api(libs.taglibs.standard.impl)
    api(libs.taglibs.standard.spec)
    api(libs.mortbay.apache.el)
    api(libs.mortbay.apache.jsp)
    api(libs.ecj)

    // Jersey/JAX-RS
    api(libs.jersey.client)
    api(libs.jersey.common)
    api(libs.jersey.server)
    api(libs.jersey.container.servlet)
    api(libs.jersey.container.servlet.core)
    api(libs.jersey.container.jetty.http)
    api(libs.jersey.container.jetty.servlet)
    api(libs.jersey.media.jaxb)
    api(libs.jersey.media.multipart)
    api(libs.jersey.proxy.client)
    api(libs.jersey.guava)
    api(libs.hk2.api)
    api(libs.hk2.locator)
    api(libs.hk2.utils)
    api(libs.aopalliance.repackaged)

    // javax APIs
    api(libs.javax.servlet.api)
    api(libs.javax.inject)
    api(libs.javax.json)
    api(libs.javax.json.api)
    api(libs.javax.mail)
    api(libs.javax.ws.rs.api)
    api(libs.javax.activation)
    api(libs.javax.activation.api)
    api(libs.javax.annotation.api)
    api(libs.validation.api)
    api(libs.persistence.api)

    // JAXB
    api(libs.jaxb.api)
    api(libs.jaxb.runtime)
    api(libs.istack.commons.runtime)
    api(libs.txw2)

    // JAX-WS
    api(libs.jaxws.api)
    api(libs.jaxws.rt)
    api(libs.jaxws.tools)
    api(libs.javax.xml.soap.api)
    api(libs.fastinfoset)
    api(libs.gmbal.api.only)
    api(libs.ha.api)
    api(libs.jsr181.api)
    api(libs.management.api)
    api(libs.mimepull)
    api(libs.policy)
    api(libs.saaj.impl)
    api(libs.stax.ex)
    api(libs.streambuffer)

    // Swagger
    api(libs.swagger.annotations)
    api(libs.swagger.core)
    api(libs.swagger.jaxrs2)
    api(libs.swagger.models)
    api(libs.swagger.integration)
    api(libs.swagger.jaxrs2.servlet.initializer)
    api(libs.reflections)
    api(libs.classgraph)

    // Google Guava
    api(libs.guava)
    implementation(libs.failureaccess)
    implementation(libs.checker.qual)
    implementation(libs.error.prone.annotations)
    implementation(libs.j2objc.annotations)
    implementation(libs.jsr305)
    implementation(libs.listenablefuture)

    // Guice
    api(libs.guice)

    // ASM
    api(libs.asm)
    api(libs.asm.analysis)
    api(libs.asm.commons)
    api(libs.asm.tree)
    api(libs.asm.util)

    // Other utilities
    api(libs.javassist)
    api(libs.joda.time)
    api(libs.java.semver)
    api(libs.quartz)
    api(libs.velocity.engine.core)
    api(libs.velocity.tools.generic)
    api(libs.rhino)
    api(libs.jsch)
    api(libs.jna)
    api(libs.jna.platform)
    api(libs.oshi.core)
    api(libs.backport.util.concurrent)
    api(libs.zip4j)

    // JMS
    api(libs.geronimo.jms)
    api(libs.geronimo.j2ee.management)

    // OSGi
    api(libs.osgi.core)
    api(libs.osgi.resource.locator)

    // HL7/HAPI
    api(libs.hapi.base)
    api(libs.hapi.structures.v21)
    api(libs.hapi.structures.v22)
    api(libs.hapi.structures.v23)
    api(libs.hapi.structures.v231)
    api(libs.hapi.structures.v24)
    api(libs.hapi.structures.v25)
    api(libs.hapi.structures.v251)
    api(libs.hapi.structures.v26)
    api(libs.hapi.structures.v27)
    api(libs.hapi.structures.v28)
    api(libs.hapi.structures.v281)

    // AWS SDK
    api(libs.aws.annotations)
    api(libs.aws.apache.client)
    api(libs.aws.auth)
    api(libs.aws.core)
    api(libs.aws.json.protocol)
    api(libs.aws.query.protocol)
    api(libs.aws.xml.protocol)
    api(libs.aws.http.client.spi)
    api(libs.aws.kms)
    api(libs.aws.profiles)
    api(libs.aws.protocol.core)
    api(libs.aws.regions)
    api(libs.aws.s3)
    api(libs.aws.sdk.core)
    api(libs.aws.sts)
    api(libs.aws.utils)
    api(libs.aws.metrics.spi)
    api(libs.aws.eventstream)

    // Netty (for AWS)
    api(libs.netty.buffer)
    api(libs.netty.codec)
    api(libs.netty.codec.http)
    api(libs.netty.codec.http2)
    api(libs.netty.common)
    api(libs.netty.handler)
    api(libs.netty.resolver)
    api(libs.netty.transport)
    api(libs.netty.transport.native.epoll)
    api(libs.netty.transport.native.unix.common)
    api(libs.netty.nio.client)
    api(libs.netty.reactive.streams)
    api(libs.netty.reactive.streams.http)
    api(libs.reactive.streams)

    // Extension-specific dependencies (conditionally included)
    // DICOM
    api(libs.dcm4che.core)
    api(libs.dcm4che.filecache)
    api(libs.dcm4che.net)
    api(libs.dcm4che.tool.dcmrcv)
    api(libs.dcm4che.tool.dcmsnd)
    api(libs.jai.imageio)

    // Document processing
    api(libs.flying.saucer.core)
    api(libs.flying.saucer.pdf) {
        exclude(group = "bouncycastle")
        exclude(group = "org.bouncycastle", module = "bctsp-jdk14")
    }
    api(libs.itext) {
        exclude(group = "bouncycastle")
        exclude(group = "org.bouncycastle", module = "bctsp-jdk14")
    }
    api(libs.itext.rtf) {
        exclude(group = "bouncycastle")
        exclude(group = "org.bouncycastle", module = "bctsp-jdk14")
    }
    api(libs.openhtmltopdf.core)
    api(libs.openhtmltopdf.pdfbox)
    api(libs.pdfbox)
    api(libs.fontbox)
    api(libs.xmpbox)
    api(libs.graphics2d)

    // File connectors
    api(libs.jcifs.ng)
    api(libs.webdavclient4j.core)

    // Web services
    api(libs.wsdl4j.fixed)

    // Viewers
    api(libs.imagej.ij)
    api(libs.pdfrenderer)

    // Local dependencies
    api(libs.mirth.vocab)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.hamcrest)
    testRuntimeOnly(libs.byte.buddy)
    testRuntimeOnly(libs.byte.buddy.agent)
    testRuntimeOnly(libs.objenesis)
}

// Generate version.properties
val generateVersionProperties by tasks.registering {
    val outputFile = file("$buildDir/resources/main/version.properties")
    outputs.file(outputFile)

    doLast {
        outputFile.parentFile.mkdirs()
        val dateFormat = SimpleDateFormat("MMMM d, yyyy")
        outputFile.writeText("""
            mirth.version=$mirthVersion
            mirth.date=${dateFormat.format(Date())}
        """.trimIndent())
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(generateVersionProperties)
}

// =============================================================================
// Core JAR Tasks
// =============================================================================

// Create mirth-crypto.jar
val cryptoJar by tasks.registering(Jar::class) {
    archiveBaseName.set("mirth-crypto")
    from(sourceSets.main.get().output) {
        include("com/mirth/commons/encryption/**")
    }
}

// Create mirth-client-core.jar
val clientCoreJar by tasks.registering(Jar::class) {
    archiveBaseName.set("mirth-client-core")
    dependsOn(generateVersionProperties)
    from(sourceSets.main.get().output) {
        include("com/mirth/connect/client/core/**")
        include("com/mirth/connect/model/**")
        include("com/mirth/connect/userutil/**")
        include("com/mirth/connect/util/**")
        include("com/mirth/connect/server/util/ResourceUtil.class")
        include("com/mirth/connect/server/util/DebuggerUtil.class")
        include("org/mozilla/**")
        include("org/glassfish/jersey/**")
        include("de/**")
        include("net/lingala/zip4j/unzip/**")
        include("version.properties")
    }
}

// Create mirth-server.jar
val serverJar by tasks.registering(Jar::class) {
    archiveBaseName.set("mirth-server")
    from(sourceSets.main.get().output) {
        include("com/mirth/connect/server/**")
        include("com/mirth/connect/model/**")
        include("com/mirth/connect/util/**")
        include("com/mirth/connect/plugins/**")
        include("com/mirth/connect/connectors/**")
        include("org/**")
        include("net/sourceforge/jtds/ssl/**")
        exclude("com/mirth/connect/server/launcher/**")
        exclude("org/dcm4che2/**")
    }
    // Include JNLP file from project directory
    from(projectDir) {
        include("mirth-client.jnlp")
    }
}

// Configuration for launcher classpath dependencies
val launcherClasspath by configurations.creating {
    extendsFrom(configurations.runtimeClasspath.get())
}

// Create mirth-server-launcher.jar with manifest
val serverLauncherJar by tasks.registering(Jar::class) {
    archiveBaseName.set("mirth-server-launcher")
    from(sourceSets.main.get().output) {
        include("com/mirth/connect/server/launcher/**")
        include("com/mirth/connect/server/extprops/**")
    }
    // Dynamically build classpath from resolved dependencies with subdirectory paths
    // These are the minimal dependencies needed by the launcher to start
    val launcherDeps = listOf(
        "commons-io", "commons-configuration2", "commons-lang3",
        "commons-logging", "commons-beanutils", "commons-text", "commons-collections"
    )
    doFirst {
        // Build classpath entries with subdirectory paths
        val classpathEntries = mutableListOf<String>()
        val manifestCopied = mutableSetOf<String>()

        // Process launcher dependencies using categorization
        serverLibCategories.entries.sortedByDescending { it.key.count { c -> c == '/' } }.forEach { (subdir, patterns) ->
            configurations.runtimeClasspath.get().files
                .filter { jar ->
                    jar.name !in manifestCopied &&
                    launcherDeps.any { dep -> jar.name.startsWith(dep) } &&
                    patterns.any { p -> jar.name.matches(Regex(p.replace("*", ".*"))) }
                }
                .forEach { jar ->
                    classpathEntries.add("server-lib/$subdir/${jar.name}")
                    manifestCopied.add(jar.name)
                }
        }

        // Add remaining launcher deps that didn't match any category (root level)
        configurations.runtimeClasspath.get().files
            .filter { jar ->
                jar.name !in manifestCopied &&
                launcherDeps.any { dep -> jar.name.startsWith(dep) }
            }
            .forEach { jar ->
                classpathEntries.add("server-lib/${jar.name}")
                manifestCopied.add(jar.name)
            }

        // Add log4j jars with subdirectory path
        configurations.runtimeClasspath.get().files
            .filter { it.name.startsWith("log4j-") }
            .forEach { classpathEntries.add("server-lib/log4j/${it.name}") }

        manifest {
            attributes(
                "Main-Class" to "com.mirth.connect.server.launcher.MirthLauncher",
                "Class-Path" to "${classpathEntries.sorted().joinToString(" ")} conf/"
            )
        }
    }
}

// Create mirth-dbconf.jar
val dbconfJar by tasks.registering(Jar::class) {
    archiveBaseName.set("mirth-dbconf")
    from("dbconf")
}

// Create userutil-sources.jar
val userutilSourcesJar by tasks.registering(Jar::class) {
    archiveBaseName.set("userutil-sources")
    from("src") {
        include("com/mirth/connect/userutil/**.java")
        include("com/mirth/connect/server/userutil/**.java")
        exclude("**/package-info.java")
    }
}

// Create httpauth-userutil-sources.jar
val httpauthUserutilSourcesJar by tasks.registering(Jar::class) {
    archiveBaseName.set("httpauth-userutil-sources")
    destinationDirectory.set(file("$buildDir/userutil-sources"))
    from("src") {
        include("com/mirth/connect/plugins/httpauth/userutil/**.java")
        exclude("**/package-info.java")
    }
}

// =============================================================================
// Extension Definition
// =============================================================================

// Data class for extension configuration
data class ExtensionConfig(
    val name: String,
    val type: String,  // "connector", "datatype", or "plugin"
    val srcPackage: String,
    val sharedClasses: List<String> = emptyList(),
    val serverClasses: List<String> = emptyList(),
    val hasLib: Boolean = true
)

// Define all extensionConfigs
val extensionConfigs = listOf(
    // Connectors
    ExtensionConfig("dicom", "connector", "dimse",
        sharedClasses = listOf("DICOMReceiverProperties", "DICOMDispatcherProperties")),
    ExtensionConfig("doc", "connector", "doc",
        sharedClasses = listOf("DocumentDispatcherProperties", "DocumentConnectorServletInterface", "PageSize", "Unit")),
    ExtensionConfig("file", "connector", "file",
        sharedClasses = listOf("SchemeProperties", "FTPSchemeProperties", "SmbDialectVersion", "SmbSchemeProperties",
            "SftpSchemeProperties", "S3SchemeProperties", "FileReceiverProperties", "FileDispatcherProperties",
            "FileScheme", "FileAction", "FileConnectorServletInterface")),
    ExtensionConfig("http", "connector", "http",
        sharedClasses = listOf("HttpReceiverProperties", "HttpDispatcherProperties", "HttpStaticResource",
            "HttpStaticResource\$ResourceType", "HttpConnectorServletInterface")),
    ExtensionConfig("jdbc", "connector", "jdbc",
        sharedClasses = listOf("DatabaseReceiverProperties", "DatabaseDispatcherProperties", "DatabaseConnectionInfo",
            "Table", "Column", "DatabaseConnectorServletInterface")),
    ExtensionConfig("jms", "connector", "jms",
        sharedClasses = listOf("JmsConnectorProperties", "JmsReceiverProperties", "JmsDispatcherProperties",
            "JmsConnectorServletInterface")),
    ExtensionConfig("js", "connector", "js",
        sharedClasses = listOf("JavaScriptReceiverProperties", "JavaScriptDispatcherProperties")),
    ExtensionConfig("smtp", "connector", "smtp",
        sharedClasses = listOf("SmtpDispatcherProperties", "SmtpConnectorServletInterface", "Attachment")),
    ExtensionConfig("tcp", "connector", "tcp",
        sharedClasses = listOf("TcpReceiverProperties", "TcpDispatcherProperties", "TcpConnectorServletInterface")),
    ExtensionConfig("vm", "connector", "vm",
        sharedClasses = listOf("VmReceiverProperties", "VmDispatcherProperties")),
    ExtensionConfig("ws", "connector", "ws",
        sharedClasses = listOf("Binding", "WebServiceReceiverProperties", "WebServiceDispatcherProperties",
            "DefinitionServiceMap", "DefinitionServiceMap\$DefinitionPortMap", "DefinitionServiceMap\$PortInformation",
            "WebServiceConnectorServletInterface")),

    // Datatypes
    ExtensionConfig("datatype-delimited", "datatype", "datatypes/delimited",
        serverClasses = listOf("DelimitedDataTypeServerPlugin", "DelimitedBatchAdaptor", "DelimitedBatchReader")),
    ExtensionConfig("datatype-dicom", "datatype", "datatypes/dicom",
        serverClasses = listOf("DICOMDataTypeServerPlugin")),
    ExtensionConfig("datatype-edi", "datatype", "datatypes/edi",
        serverClasses = listOf("EDIDataTypeServerPlugin")),
    ExtensionConfig("datatype-hl7v2", "datatype", "datatypes/hl7v2",
        serverClasses = listOf("HL7v2DataTypeServerPlugin", "HL7v2BatchAdaptor")),
    ExtensionConfig("datatype-hl7v3", "datatype", "datatypes/hl7v3",
        serverClasses = listOf("HL7V3DataTypeServerPlugin")),
    ExtensionConfig("datatype-ncpdp", "datatype", "datatypes/ncpdp",
        serverClasses = listOf("NCPDPDataTypeServerPlugin")),
    ExtensionConfig("datatype-xml", "datatype", "datatypes/xml",
        serverClasses = listOf("XMLDataTypeServerPlugin")),
    ExtensionConfig("datatype-raw", "datatype", "datatypes/raw",
        serverClasses = listOf("RawDataTypeServerPlugin")),
    ExtensionConfig("datatype-json", "datatype", "datatypes/json",
        serverClasses = listOf("JSONDataTypeServerPlugin")),

    // Plugins
    ExtensionConfig("directoryresource", "plugin", "directoryresource",
        sharedClasses = listOf("DirectoryResourceProperties", "DirectoryResourceServletInterface")),
    ExtensionConfig("dashboardstatus", "plugin", "dashboardstatus",
        sharedClasses = listOf("ConnectionLogItem", "DashboardConnectorStatusServletInterface")),
    ExtensionConfig("destinationsetfilter", "plugin", "destinationsetfilter",
        sharedClasses = listOf("DestinationSetFilterStep", "DestinationSetFilterStep\$Behavior",
            "DestinationSetFilterStep\$Condition")),
    ExtensionConfig("dicomviewer", "plugin", "dicomviewer", hasLib = true),
    ExtensionConfig("globalmapviewer", "plugin", "globalmapviewer",
        sharedClasses = listOf("GlobalMapServletInterface")),
    ExtensionConfig("httpauth", "plugin", "httpauth",
        sharedClasses = listOf("HttpAuthConnectorPluginProperties", "HttpAuthConnectorPluginProperties\$AuthType",
            "NoneHttpAuthProperties", "basic/BasicHttpAuthProperties", "digest/DigestHttpAuthProperties",
            "digest/DigestHttpAuthProperties\$Algorithm", "digest/DigestHttpAuthProperties\$QOPMode",
            "custom/CustomHttpAuthProperties", "javascript/JavaScriptHttpAuthProperties",
            "oauth2/OAuth2HttpAuthProperties", "oauth2/OAuth2HttpAuthProperties\$TokenLocation")),
    ExtensionConfig("imageviewer", "plugin", "imageviewer", hasLib = false),
    ExtensionConfig("javascriptrule", "plugin", "javascriptrule",
        sharedClasses = listOf("JavaScriptRule")),
    ExtensionConfig("javascriptstep", "plugin", "javascriptstep",
        sharedClasses = listOf("JavaScriptStep")),
    ExtensionConfig("mapper", "plugin", "mapper",
        sharedClasses = listOf("MapperStep", "MapperStep\$Scope")),
    ExtensionConfig("messagebuilder", "plugin", "messagebuilder",
        sharedClasses = listOf("MessageBuilderStep")),
    ExtensionConfig("datapruner", "plugin", "datapruner",
        sharedClasses = listOf("DataPrunerServletInterface")),
    ExtensionConfig("mllpmode", "plugin", "mllpmode",
        sharedClasses = listOf("MLLPModeProperties")),
    ExtensionConfig("pdfviewer", "plugin", "pdfviewer", hasLib = true),
    ExtensionConfig("textviewer", "plugin", "textviewer", hasLib = false),
    ExtensionConfig("rulebuilder", "plugin", "rulebuilder",
        sharedClasses = listOf("RuleBuilderRule", "RuleBuilderRule\$Condition")),
    ExtensionConfig("serverlog", "plugin", "serverlog",
        sharedClasses = listOf("ServerLogItem", "ServerLogServletInterface")),
    ExtensionConfig("scriptfilerule", "plugin", "scriptfilerule",
        sharedClasses = listOf("ExternalScriptRule")),
    ExtensionConfig("scriptfilestep", "plugin", "scriptfilestep",
        sharedClasses = listOf("ExternalScriptStep")),
    ExtensionConfig("xsltstep", "plugin", "xsltstep",
        sharedClasses = listOf("XsltStep"))
)

// =============================================================================
// JAR Categorization for server-lib Subdirectories
// =============================================================================

// JAR categorization for server-lib subdirectories to match official distribution structure
val serverLibCategories = mapOf(
    "aws" to listOf("annotations-2.*", "apache-client-2.*", "auth-2.*", "aws-*", "eventstream-*", "http-client-spi-*", "kms-*", "metrics-spi-*", "profiles-*", "protocol-core-*", "regions-*", "s3-*", "sdk-core-*", "sts-*", "arns-*", "utils-2.*"),
    "aws/ext/netty" to listOf("netty-*"),
    "aws/ext" to listOf("reactive-streams-*"),
    "commons" to listOf("commons-*", "httpclient-4.*", "httpcore-4.*", "httpmime-*"),
    "database" to listOf("derby*", "jtds-*", "mssql-jdbc-*", "mysql-connector-*", "ojdbc*", "postgresql-*", "sqlite-jdbc-*", "ucp-*", "oraclepki-*", "osdt_*", "simplefan-*", "ons-*"),
    "donkey" to listOf("HikariCP-*", "guice-*", "quartz-*", "slf4j-*"),
    "donkey/guava" to listOf("guava-*", "checker-qual-*", "error_prone_*", "failureaccess-*", "j2objc-*", "jsr305-*", "listenablefuture-*"),
    "hapi" to listOf("hapi-*"),
    "jackson" to listOf("jackson-*"),
    "javax" to listOf("javax.activation-*", "javax.annotation-*", "javax.inject-*", "javax.json*", "javax.mail*", "javax.servlet-*", "javax.ws.rs-*", "jakarta.*"),
    "javax/jaxb" to listOf("jaxb-api-*", "jaxb-runtime-*"),
    "javax/jaxb/ext" to listOf("istack-commons-*", "txw2-*"),
    "javax/jaxws" to listOf("jaxws-*", "javax.xml.soap-*"),
    "javax/jaxws/ext" to listOf("FastInfoset-*", "gmbal-*", "ha-api-*", "jsr181-*", "management-api-*", "mimepull-*", "policy-*", "saaj-*", "stax-ex-*", "streambuffer-*"),
    "jersey" to listOf("jersey-*"),
    "jersey/ext" to listOf("aopalliance*", "asm-*", "hk2-*", "org.osgi.*", "osgi-resource-*", "persistence-api-*", "validation-api-*"),
    "jetty" to listOf("jetty-*"),
    "jetty/jsp" to listOf("apache-jsp-*", "apache-el-*", "taglibs-*", "ecj-*"),
    "jms" to listOf("geronimo-*"),
    "log4j" to listOf("log4j-*"),
    "swagger" to listOf("swagger-*"),
    "swagger/ext" to listOf("reflections-*")
)

// =============================================================================
// Setup Directory Assembly
// =============================================================================

val setupDir = file("$projectDir/setup")
val extBuildDir = file("$buildDir/extensionConfigs")

// Collect extension shared JAR task names
val extensionSharedJarTasks = extensionConfigs
    .filter { it.sharedClasses.isNotEmpty() || it.type == "datatype" }
    .map { "${it.name}SharedJar" }

// Main setup assembly task
val assembleSetup by tasks.registering {
    group = "build"
    description = "Assembles the complete setup directory"

    dependsOn(
        "classes",
        cryptoJar,
        clientCoreJar,
        serverJar,
        serverLauncherJar,
        dbconfJar,
        userutilSourcesJar
    )
    // Depend on extension shared JARs for client-lib
    dependsOn(extensionSharedJarTasks)

    doLast {
        // Create setup directory structure
        setupDir.mkdirs()
        file("$setupDir/conf").mkdirs()
        file("$setupDir/extensionConfigs").mkdirs()
        file("$setupDir/server-lib").mkdirs()
        file("$setupDir/client-lib").mkdirs()
        file("$setupDir/manager-lib").mkdirs()
        file("$setupDir/cli-lib").mkdirs()
        file("$setupDir/server-launcher-lib").mkdirs()
        file("$setupDir/logs").mkdirs()
        file("$setupDir/docs").mkdirs()
        file("$setupDir/public_html").mkdirs()
        file("$setupDir/public_api_html").mkdirs()
        file("$setupDir/server-lib/donkey").mkdirs()

        // Copy donkey JARs to server-lib/donkey (without version numbers)
        val donkeyProject = project(":donkey")
        copy {
            from(donkeyProject.tasks.named("donkeyModelJar"))
            into("$setupDir/server-lib/donkey")
            rename { "donkey-model.jar" }
        }
        copy {
            from(donkeyProject.tasks.named("donkeyServerJar"))
            into("$setupDir/server-lib/donkey")
            rename { "donkey-server.jar" }
        }
        copy {
            from(donkeyProject.tasks.named("donkeyDbconfJar"))
            into("$setupDir/server-lib/donkey")
            rename { "donkey-dbconf.jar" }
        }

        // Copy server-lib dependencies with subdirectory organization
        val copiedJars = mutableSetOf<String>()

        // First pass: copy to categorized subdirectories (process deepest paths first)
        serverLibCategories.entries.sortedByDescending { it.key.count { c -> c == '/' } }.forEach { (subdir, patterns) ->
            val targetDir = file("$setupDir/server-lib/$subdir")
            targetDir.mkdirs()

            configurations.runtimeClasspath.get().files
                .filter { jar ->
                    jar.name !in copiedJars &&
                    patterns.any { pattern ->
                        jar.name.matches(Regex(pattern.replace("*", ".*")))
                    }
                }
                .forEach { jar ->
                    copy {
                        from(jar)
                        into(targetDir)
                    }
                    copiedJars.add(jar.name)
                }
        }

        // Second pass: copy remaining JARs to server-lib root
        copy {
            from(configurations.runtimeClasspath)
            into("$setupDir/server-lib")
            exclude { it.file.name in copiedJars }
            exclude("**/ant/**")
        }

        // Copy core JARs to server-lib (without version numbers for launcher compatibility)
        copy {
            from(cryptoJar)
            into("$setupDir/server-lib")
            rename { "mirth-crypto.jar" }
        }
        copy {
            from(clientCoreJar)
            into("$setupDir/server-lib")
            rename { "mirth-client-core.jar" }
        }
        copy {
            from(serverJar)
            into("$setupDir/server-lib")
            rename { "mirth-server.jar" }
        }
        copy {
            from(dbconfJar)
            into("$setupDir/server-lib")
            rename { "mirth-dbconf.jar" }
        }

        // Copy launcher JAR to setup root (without version number for script compatibility)
        copy {
            from(serverLauncherJar)
            into(setupDir)
            rename { "mirth-server-launcher.jar" }
        }

        // Copy userutil-sources to client-lib
        copy {
            from(userutilSourcesJar)
            into("$setupDir/client-lib")
        }

        // Copy mirth-client.jar from client project (without version number)
        val clientProject = project(":client")
        copy {
            from(clientProject.tasks.named("clientJar"))
            into("$setupDir/client-lib")
            rename { "mirth-client.jar" }
        }

        // Copy client lib dependencies
        copy {
            from("${clientProject.projectDir}/lib") {
                exclude("*-shared.jar")
                exclude("extensions/**")
            }
            into("$setupDir/client-lib")
        }

        // Copy mirth-client-core.jar to client-lib (required by WebStartServlet)
        copy {
            from(clientCoreJar)
            into("$setupDir/client-lib")
            rename { "mirth-client-core.jar" }
        }

        // Copy mirth-crypto.jar to client-lib (required by WebStartServlet)
        copy {
            from(cryptoJar)
            into("$setupDir/client-lib")
            rename { "mirth-crypto.jar" }
        }

        // Copy mirth-vocab.jar to client-lib (required by WebStartServlet)
        copy {
            from("$projectDir/lib/mirth-vocab.jar")
            into("$setupDir/client-lib")
        }

        // Copy donkey-model.jar to client-lib (required by WebStartServlet)
        copy {
            from(donkeyProject.tasks.named("donkeyModelJar"))
            into("$setupDir/client-lib")
            rename { "donkey-model.jar" }
        }

        // Copy extension shared JARs to client-lib (required for client deserialization)
        extensionConfigs.filter { it.sharedClasses.isNotEmpty() || it.type == "datatype" }.forEach { ext ->
            copy {
                from("$extBuildDir/${ext.name}") {
                    include("*-shared-*.jar")
                }
                into("$setupDir/client-lib")
                // Rename to remove version number: foo-shared-4.5.2.jar -> foo-shared.jar
                rename { fileName ->
                    fileName.replace(Regex("-shared-[0-9.]+\\.jar$"), "-shared.jar")
                }
            }
        }

        // Copy conf files
        copy {
            from("conf")
            into("$setupDir/conf")
        }

        // Copy public html files
        copy {
            from("public_html")
            into("$setupDir/public_html")
            exclude("Thumbs.db")
        }

        // Copy public API html files
        copy {
            from("public_api_html")
            into("$setupDir/public_api_html")
            exclude("Thumbs.db")
        }

        // Copy docs
        copy {
            from("docs")
            into("$setupDir/docs")
        }

        // Copy basedir includes
        copy {
            from("basedir-includes")
            into(setupDir)
        }

        // Make server script executable
        file("$setupDir/oieserver").setExecutable(true)
    }
}

// =============================================================================
// JAR Signing Configuration
// =============================================================================

val signingEnabled = project.findProperty("disableSigning")?.toString()?.toBoolean() != true

fun loadKeystoreProperties(): Map<String, String> {
    val props = Properties()
    val propsFile = file("keystore.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { stream -> props.load(stream) }
    }
    val result = mutableMapOf<String, String>()
    props.forEach { key, value ->
        result[key.toString()] = value.toString().replace("\${basedir}", projectDir.absolutePath)
    }
    return result
}

val modifyJarManifests by tasks.registering {
    group = "signing"
    description = "Adds Web Start security attributes to JAR manifests"

    dependsOn(assembleSetup)
    onlyIf { signingEnabled }

    doLast {
        val clientLibDir = file("$setupDir/client-lib")
        val extensionsDir = file("$setupDir/extensions")
        val manifestFile = file("custom_manifest.mf")

        if (!manifestFile.exists()) {
            logger.warn("custom_manifest.mf not found, skipping manifest modification")
            return@doLast
        }

        // Collect JARs from client-lib (skip BouncyCastle)
        val clientLibJars = clientLibDir.listFiles()?.filter {
            it.extension == "jar" &&
            !it.name.startsWith("bcp") &&
            !it.name.startsWith("bcutil")
        } ?: emptyList()

        // Collect JARs from extensions (client, shared, and lib JARs - not server JARs)
        val extensionJars = extensionsDir.walkTopDown()
            .filter { it.extension == "jar" && !it.name.contains("-server") }
            .toList()

        val jarsToModify = clientLibJars + extensionJars

        logger.lifecycle("Modifying manifests for ${jarsToModify.size} JARs (${clientLibJars.size} in client-lib, ${extensionJars.size} in extensions)")

        jarsToModify.parallelStream().forEach { jarFile ->
            exec {
                commandLine("jar", "umf", manifestFile.absolutePath, jarFile.absolutePath)
                isIgnoreExitValue = true
            }
        }
    }
}

val signClientJars by tasks.registering {
    group = "signing"
    description = "Signs all client and extension JARs for Java Web Start"

    dependsOn(modifyJarManifests)
    onlyIf { signingEnabled }

    doLast {
        val keystoreProps = loadKeystoreProperties()
        val keystore = keystoreProps["key.keystore"] ?: error("key.keystore not configured in keystore.properties")
        val storepass = keystoreProps["key.storepass"] ?: error("key.storepass not configured in keystore.properties")
        val alias = keystoreProps["key.alias"] ?: error("key.alias not configured in keystore.properties")
        val keypass = keystoreProps["key.keypass"] ?: storepass

        val clientLibDir = file("$setupDir/client-lib")
        val extensionsDir = file("$setupDir/extensions")

        // Collect JARs from client-lib
        val clientLibJars = clientLibDir.listFiles()?.filter { it.extension == "jar" } ?: emptyList()

        // Collect JARs from extensions (client, shared, and lib JARs - not server JARs)
        val extensionJars = extensionsDir.walkTopDown()
            .filter { it.extension == "jar" && !it.name.contains("-server") }
            .toList()

        val jarsToSign = clientLibJars + extensionJars

        logger.lifecycle("Signing ${jarsToSign.size} JARs with keystore: $keystore (${clientLibJars.size} in client-lib, ${extensionJars.size} in extensions)")

        val failedJars = ConcurrentHashMap<String, String>()

        // Use a fixed thread pool with limited concurrency to avoid resource exhaustion
        val executor = Executors.newFixedThreadPool(4)
        val futures = mutableListOf<Future<*>>()

        for (jarFile in jarsToSign) {
            futures.add(executor.submit {
                var success = false
                var lastError = ""

                repeat(5) { _ ->
                    if (!success) {
                        try {
                            val result = exec {
                                commandLine(
                                    "jarsigner",
                                    "-keystore", keystore,
                                    "-storepass", storepass,
                                    "-keypass", keypass,
                                    "-digestalg", "SHA-256",
                                    "-sigalg", "SHA256withRSA",
                                    jarFile.absolutePath,
                                    alias
                                )
                                isIgnoreExitValue = true
                            }
                            if (result.exitValue == 0) {
                                success = true
                            } else {
                                lastError = "Exit code: ${result.exitValue}"
                                Thread.sleep(1000)
                            }
                        } catch (e: Exception) {
                            lastError = e.message ?: "Unknown error"
                            Thread.sleep(1000)
                        }
                    }
                }

                if (!success) {
                    failedJars[jarFile.name] = lastError
                }
            })
        }

        // Wait for all signing tasks to complete
        for (future in futures) {
            future.get()
        }
        executor.shutdown()

        if (failedJars.isNotEmpty()) {
            for ((name, error) in failedJars) {
                logger.error("Failed to sign $name: $error")
            }
            throw GradleException("JAR signing failed for ${failedJars.size} files")
        }

        logger.lifecycle("Successfully signed ${jarsToSign.size} JARs")
    }
}

// Note: Signing is wired via installExtensionClients -> signClientJars chain below

// Create extension build tasks dynamically
extensionConfigs.forEach { ext ->
    val baseName = ext.name
    val srcBase = when (ext.type) {
        "connector" -> "com/mirth/connect/connectors/${ext.srcPackage}"
        "datatype" -> "com/mirth/connect/plugins/${ext.srcPackage}"
        else -> "com/mirth/connect/plugins/${ext.srcPackage}"
    }

    // Shared JAR task
    if (ext.sharedClasses.isNotEmpty() || ext.type == "datatype") {
        tasks.register<Jar>("${baseName}SharedJar") {
            archiveBaseName.set("$baseName-shared")
            destinationDirectory.set(file("$extBuildDir/$baseName"))

            from(sourceSets.main.get().output) {
                if (ext.sharedClasses.isNotEmpty()) {
                    ext.sharedClasses.forEach { className ->
                        include("$srcBase/$className.class")
                    }
                } else if (ext.type == "datatype") {
                    // For datatypes, shared includes everything except server classes
                    include("$srcBase/**")
                    ext.serverClasses.forEach { className ->
                        exclude("$srcBase/$className.class")
                    }
                }
            }
        }
    }

    // Server JAR task - build for all extensions (connectors, datatypes, and plugins)
    // Include all classes except the explicitly listed shared classes
    tasks.register<Jar>("${baseName}ServerJar") {
        archiveBaseName.set("$baseName-server")
        destinationDirectory.set(file("$extBuildDir/$baseName"))

        from(sourceSets.main.get().output) {
            include("$srcBase/**")
            ext.sharedClasses.forEach { className ->
                exclude("$srcBase/$className.class")
            }
        }
    }

    // Extension ZIP task
    tasks.register<Zip>("${baseName}ExtensionZip") {
        archiveBaseName.set(baseName)
        archiveVersion.set(mirthVersion)
        destinationDirectory.set(file("$buildDir/dist/extensionConfigs"))

        // Explicit dependencies on JAR tasks
        if (ext.sharedClasses.isNotEmpty() || ext.type == "datatype") {
            dependsOn("${baseName}SharedJar")
        }
        dependsOn("${baseName}ServerJar")  // Always include server JAR

        from("$extBuildDir/$baseName")
        from("src/$srcBase") {
            include("*.xml")
        }
        // Include lib dependencies if they exist (lib directories use srcPackage names)
        val extLibDir = file("lib/extensions/${ext.srcPackage}")
        if (extLibDir.exists()) {
            from(extLibDir) {
                into("lib")
            }
        }
    }
}

// Task to build all extensionConfigs
val buildExtensions by tasks.registering {
    group = "build"
    description = "Builds all extension JARs and ZIPs"

    extensionConfigs.forEach { ext ->
        if (ext.sharedClasses.isNotEmpty() || ext.type == "datatype") {
            dependsOn("${ext.name}SharedJar")
        }
        dependsOn("${ext.name}ServerJar")  // Always build server JAR
        dependsOn("${ext.name}ExtensionZip")
    }
}

// Task to install extensions to setup/extensions/
val installExtensions by tasks.registering {
    group = "build"
    description = "Installs extensions to setup/extensions directory"

    dependsOn(buildExtensions)
    dependsOn(httpauthUserutilSourcesJar)

    doLast {
        val extensionsDir = file("$setupDir/extensions")
        extensionsDir.mkdirs()

        extensionConfigs.forEach { ext ->
            val extDir = file("$extensionsDir/${ext.name}")
            extDir.mkdirs()

            val srcBase = when (ext.type) {
                "connector" -> "com/mirth/connect/connectors/${ext.srcPackage}"
                "datatype" -> "com/mirth/connect/plugins/${ext.srcPackage}"
                else -> "com/mirth/connect/plugins/${ext.srcPackage}"
            }

            // Copy plugin.xml
            copy {
                from("src/$srcBase") {
                    include("*.xml")
                }
                into(extDir)
            }

            // Copy shared JAR (renamed to remove version)
            if (ext.sharedClasses.isNotEmpty() || ext.type == "datatype") {
                copy {
                    from("$extBuildDir/${ext.name}") {
                        include("*-shared-*.jar")
                    }
                    into(extDir)
                    rename { "${ext.name}-shared.jar" }
                }
            }

            // Copy server JAR (renamed to remove version) - always present
            copy {
                from("$extBuildDir/${ext.name}") {
                    include("*-server-*.jar")
                }
                into(extDir)
                rename { "${ext.name}-server.jar" }
            }

            // Copy lib dependencies if they exist (lib directories use srcPackage names)
            val libSrcDir = file("lib/extensions/${ext.srcPackage}")
            if (libSrcDir.exists() && libSrcDir.isDirectory()) {
                copy {
                    from(libSrcDir)
                    into("$extDir/lib")
                }
            }
        }

        // Copy httpauth userutil sources JAR
        val httpauthExtDir = file("$extensionsDir/httpauth")
        file("$httpauthExtDir/src").mkdirs()
        copy {
            from("$buildDir/userutil-sources") {
                include("httpauth-userutil-sources*.jar")
            }
            into("$httpauthExtDir/src")
            rename { "httpauth-userutil-sources.jar" }
        }

        logger.lifecycle("Installed ${extensionConfigs.size} extensions to $extensionsDir")
    }
}

// Wire extension installation and signing to assembleSetup
// Order: assembleSetup -> installExtensions -> installExtensionClients -> signClientJars
assembleSetup.configure {
    finalizedBy(installExtensions)
}

// Install client extension JARs after server extensions are installed
installExtensions.configure {
    finalizedBy(project(":client").tasks.named("installExtensionClients"))
}

// Signing happens after all JARs are installed (including extension client JARs)
project(":client").tasks.named("installExtensionClients").configure {
    finalizedBy(signClientJars)
}

// modifyJarManifests needs extensions to be fully installed
modifyJarManifests.configure {
    mustRunAfter(project(":client").tasks.named("installExtensionClients"))
}

tasks.named("assemble") {
    dependsOn(assembleSetup, buildExtensions)
}

// Artifacts for other modules
artifacts {
    add("archives", cryptoJar)
    add("archives", clientCoreJar)
    add("archives", serverJar)
}

// =============================================================================
// Linux Package Building (RPM, DEB, tar.gz)
// =============================================================================

val packagingDir = rootProject.file("packaging")

// Common package configuration
val packageName = "oie"
val packageDescription = "Open Integration Engine - Healthcare integration platform"
val packageUrl = "https://github.com/nextgenhealthcare/connect"
val packageLicense = "MPL-2.0"
val packageVendor = "NextGen Healthcare"
val packageMaintainer = "OIE Development Team"

// RPM Package Task
val oieRpm by tasks.registering(com.netflix.gradle.plugins.rpm.Rpm::class) {
    dependsOn(assembleSetup)

    packageName = "oie"
    release = "1"
    version = mirthVersion
    archStr = "x86_64"
    os = org.redline_rpm.header.Os.LINUX

    summary = packageDescription
    packageDescription = "Open Integration Engine is a cross-platform healthcare integration engine " +
            "designed to facilitate interoperability between healthcare systems."
    url = packageUrl
    license = packageLicense
    vendor = packageVendor
    packager = packageMaintainer
    packageGroup = "Applications/Healthcare"

    // Package dependencies
    requires("java-17-openjdk-headless")
    requires("systemd")

    // Pre/post install scripts
    preInstall(file("${packagingDir}/scripts/rpm/pre-install.sh"))
    postInstall(file("${packagingDir}/scripts/rpm/post-install.sh"))
    preUninstall(file("${packagingDir}/scripts/rpm/pre-uninstall.sh"))
    postUninstall(file("${packagingDir}/scripts/rpm/post-uninstall.sh"))

    // Application files -> /opt/oie/
    from("$projectDir/setup") {
        into("/opt/oie")
        user = "oie"
        permissionGroup = "oie"
        fileMode = 0x1A4  // 0644
        dirMode = 0x1ED   // 0755
    }

    // Make oieserver script executable
    from("$projectDir/setup") {
        into("/opt/oie")
        include("oieserver")
        user = "oie"
        permissionGroup = "oie"
        fileMode = 0x1ED  // 0755
    }

    // Systemd service file
    from("${packagingDir}/systemd/oie.service") {
        into("/usr/lib/systemd/system")
        user = "root"
        permissionGroup = "root"
        fileMode = 0x1A4  // 0644
    }

    // Tmpfiles configuration
    from("${packagingDir}/systemd/oie.tmpfiles.conf") {
        into("/usr/lib/tmpfiles.d")
        rename { "oie.conf" }
        user = "root"
        permissionGroup = "root"
        fileMode = 0x1A4  // 0644
    }

    // Create empty directories
    directory("/var/log/oie", 0x1ED)  // 0755
    directory("/var/lib/oie", 0x1ED)  // 0755
    directory("/etc/oie", 0x1ED)      // 0755
}

// DEB Package Task
val oieDeb by tasks.registering(com.netflix.gradle.plugins.deb.Deb::class) {
    dependsOn(assembleSetup)

    packageName = "oie"
    release = "1"
    version = mirthVersion
    archStr = "amd64"

    summary = packageDescription
    packageDescription = "Open Integration Engine is a cross-platform healthcare integration engine " +
            "designed to facilitate interoperability between healthcare systems."
    url = packageUrl
    license = packageLicense
    vendor = packageVendor
    maintainer = packageMaintainer
    packageGroup = "misc"  // Debian section

    // Package dependencies
    requires("default-jre-headless").or("openjdk-17-jre-headless")
    requires("systemd")

    // Pre/post install scripts
    preInstall(file("${packagingDir}/scripts/deb/preinst"))
    postInstall(file("${packagingDir}/scripts/deb/postinst"))
    preUninstall(file("${packagingDir}/scripts/deb/prerm"))
    postUninstall(file("${packagingDir}/scripts/deb/postrm"))

    // Application files -> /opt/oie/
    from("$projectDir/setup") {
        into("/opt/oie")
        user = "oie"
        permissionGroup = "oie"
        fileMode = 0x1A4  // 0644
        dirMode = 0x1ED   // 0755
    }

    // Make oieserver script executable
    from("$projectDir/setup") {
        into("/opt/oie")
        include("oieserver")
        user = "oie"
        permissionGroup = "oie"
        fileMode = 0x1ED  // 0755
    }

    // Systemd service file
    from("${packagingDir}/systemd/oie.service") {
        into("/lib/systemd/system")
        user = "root"
        permissionGroup = "root"
        fileMode = 0x1A4  // 0644
    }

    // Tmpfiles configuration
    from("${packagingDir}/systemd/oie.tmpfiles.conf") {
        into("/usr/lib/tmpfiles.d")
        rename { "oie.conf" }
        user = "root"
        permissionGroup = "root"
        fileMode = 0x1A4  // 0644
    }

    // Create empty directories
    directory("/var/log/oie", 0x1ED)  // 0755
    directory("/var/lib/oie", 0x1ED)  // 0755
    directory("/etc/oie", 0x1ED)      // 0755
}

// Distribution (tar.gz) configuration
distributions {
    main {
        distributionBaseName.set("oie")
        contents {
            from("$projectDir/setup")
        }
    }
}

// Configure distTar to use gzip compression and depend on assembleSetup
tasks.named<Tar>("distTar") {
    dependsOn(assembleSetup)
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
}

tasks.named("distZip") {
    dependsOn(assembleSetup)
}

// Combined task to build all Linux packages
val buildLinuxPackages by tasks.registering {
    group = "distribution"
    description = "Builds all Linux packages (RPM, DEB, tar.gz)"
    dependsOn(oieRpm, oieDeb, "distTar")
}
