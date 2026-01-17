import java.text.SimpleDateFormat
import java.util.Date

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
    // Dynamically build classpath from resolved dependencies
    // These are the minimal dependencies needed by the launcher to start
    val launcherDeps = listOf(
        "commons-io", "commons-configuration2", "commons-lang3",
        "commons-logging", "commons-beanutils", "commons-text", "commons-collections"
    )
    doFirst {
        val classpathEntries = configurations.runtimeClasspath.get().files
            .filter { file -> launcherDeps.any { dep -> file.name.startsWith(dep) } }
            .map { "server-lib/${it.name}" }
            .sorted()
            .toMutableList()
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
// Setup Directory Assembly
// =============================================================================

val setupDir = file("$projectDir/setup")
val extBuildDir = file("$buildDir/extensionConfigs")

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
        file("$setupDir/lib/donkey").mkdirs()

        // Copy donkey JARs to lib/donkey (without version numbers)
        val donkeyProject = project(":donkey")
        copy {
            from(donkeyProject.tasks.named("donkeyModelJar"))
            into("$setupDir/lib/donkey")
            rename { "donkey-model.jar" }
        }
        copy {
            from(donkeyProject.tasks.named("donkeyServerJar"))
            into("$setupDir/lib/donkey")
            rename { "donkey-server.jar" }
        }
        copy {
            from(donkeyProject.tasks.named("donkeyDbconfJar"))
            into("$setupDir/lib/donkey")
            rename { "donkey-dbconf.jar" }
        }

        // Copy server-lib dependencies (excluding log4j which goes in subdirectory)
        copy {
            from(configurations.runtimeClasspath)
            into("$setupDir/server-lib")
            exclude("**/ant/**")
            exclude("**/log4j-*.jar")
        }

        // Copy log4j JARs to server-lib/log4j subdirectory
        file("$setupDir/server-lib/log4j").mkdirs()
        copy {
            from(configurations.runtimeClasspath)
            into("$setupDir/server-lib/log4j")
            include("**/log4j-*.jar")
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

    // Server JAR task (if has server classes)
    if (ext.serverClasses.isNotEmpty() || ext.type == "connector") {
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
        if (ext.serverClasses.isNotEmpty() || ext.type == "connector") {
            dependsOn("${baseName}ServerJar")
        }

        from("$extBuildDir/$baseName")
        from("src/$srcBase") {
            include("*.xml")
        }
        if (ext.hasLib) {
            from("lib/extensionConfigs/${ext.srcPackage}") {
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
        if (ext.serverClasses.isNotEmpty() || ext.type == "connector") {
            dependsOn("${ext.name}ServerJar")
        }
        dependsOn("${ext.name}ExtensionZip")
    }
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
