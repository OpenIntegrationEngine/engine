plugins {
    `java-library`
    application
}

description = "Mirth Connect Client - GUI application"

// Client uses traditional src/ layout
sourceSets {
    main {
        java {
            srcDir("src")
        }
        resources {
            srcDir("src")
            include("**/*.png", "**/*.gif", "**/*.jpg", "**/*.properties",
                    "**/*.html", "**/*.css", "**/*.js")
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
    api(project(":server"))

    // Logging
    api(libs.log4j.api)
    api(libs.log4j.core)
    api(libs.log4j.bridge)
    api(libs.slf4j.api)
    implementation(libs.slf4j.log4j12)

    // Apache Commons
    api(libs.commons.beanutils)
    api(libs.commons.codec)
    api(libs.commons.collections4)
    api(libs.commons.compress)
    api(libs.commons.configuration2)
    api(libs.commons.io)
    api(libs.commons.lang3)
    api(libs.commons.logging)
    api(libs.commons.pool2)
    api(libs.commons.text)
    api(libs.commons.vfs2)

    // HTTP Client
    api(libs.httpclient)
    api(libs.httpcore)
    api(libs.httpmime)

    // Security
    api(libs.bcprov.jdk18on)
    api(libs.bcpkix.jdk18on)
    api(libs.bcutil.jdk18on)

    // JSON/XML
    api(libs.jackson.annotations)
    api(libs.jackson.core)
    api(libs.jackson.databind)
    api(libs.xstream)
    api(libs.xpp3)
    api(libs.staxon)

    // Jersey client
    api(libs.jersey.client)
    api(libs.jersey.common)
    api(libs.jersey.proxy.client)
    api(libs.jersey.media.multipart)
    api(libs.jersey.guava)
    api(libs.hk2.api)
    api(libs.hk2.locator)
    api(libs.hk2.utils)
    api(libs.javax.inject)
    api(libs.javax.ws.rs.api)

    // JAXB
    api(libs.jaxb.api)
    api(libs.jaxb.runtime)
    api(libs.istack.commons.runtime)
    api(libs.javax.activation)
    api(libs.javax.activation.api)
    api(libs.javax.annotation.api)

    // UI Libraries
    api(libs.miglayout.core)
    api(libs.miglayout.swing)
    api(libs.swingx.core)
    api(libs.rsyntaxtextarea)
    api(libs.autocomplete)
    api(libs.looks)
    api(libs.javaparser)
    api(libs.libphonenumber)

    // Utilities
    api(libs.guava)
    api(libs.javassist)
    api(libs.joda.time)
    api(libs.java.semver)
    api(libs.quartz)
    api(libs.velocity.engine.core)
    api(libs.velocity.tools.generic)
    api(libs.rhino)
    api(libs.jetty.util)
    api(libs.mimepull)
    api(libs.reflections)
    api(libs.swagger.annotations)

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

    // AWS (for S3 file connector UI)
    api(libs.aws.regions)
    api(libs.aws.utils)

    // DICOM support
    api(libs.dcm4che.core)

    // File connector support
    api(libs.jcifs.ng)

    // Local dependencies
    api(libs.wizard)
    api(libs.language.support)
    api(libs.openjfx.extensions)
    api(libs.jai.imageio.client)
    api(libs.zip4j)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
}

// Create mirth-client.jar
val clientJar by tasks.registering(Jar::class) {
    archiveBaseName.set("mirth-client")
    from(sourceSets.main.get().output)
}

application {
    mainClass.set("com.mirth.connect.client.ui.Mirth")
}

// =============================================================================
// Extension Client JARs
// =============================================================================

val extClientBuildDir = file("$buildDir/extensionClients")

// Define extension client configurations: name to source package path
val extensionClientConfigs = mapOf(
    // Connectors
    "dicom" to "com/mirth/connect/connectors/dimse",
    "doc" to "com/mirth/connect/connectors/doc",
    "file" to "com/mirth/connect/connectors/file",
    "http" to "com/mirth/connect/connectors/http",
    "jdbc" to "com/mirth/connect/connectors/jdbc",
    "jms" to "com/mirth/connect/connectors/jms",
    "js" to "com/mirth/connect/connectors/js",
    "smtp" to "com/mirth/connect/connectors/smtp",
    "tcp" to "com/mirth/connect/connectors/tcp",
    "vm" to "com/mirth/connect/connectors/vm",
    "ws" to "com/mirth/connect/connectors/ws",
    // Datatypes
    "datatype-delimited" to "com/mirth/connect/plugins/datatypes/delimited",
    "datatype-dicom" to "com/mirth/connect/plugins/datatypes/dicom",
    "datatype-edi" to "com/mirth/connect/plugins/datatypes/edi",
    "datatype-hl7v2" to "com/mirth/connect/plugins/datatypes/hl7v2",
    "datatype-hl7v3" to "com/mirth/connect/plugins/datatypes/hl7v3",
    "datatype-json" to "com/mirth/connect/plugins/datatypes/json",
    "datatype-ncpdp" to "com/mirth/connect/plugins/datatypes/ncpdp",
    "datatype-raw" to "com/mirth/connect/plugins/datatypes/raw",
    "datatype-xml" to "com/mirth/connect/plugins/datatypes/xml",
    // Plugins
    "directoryresource" to "com/mirth/connect/plugins/directoryresource",
    "dashboardstatus" to "com/mirth/connect/plugins/dashboardstatus",
    "destinationsetfilter" to "com/mirth/connect/plugins/destinationsetfilter",
    "dicomviewer" to "com/mirth/connect/plugins/dicomviewer",
    "globalmapviewer" to "com/mirth/connect/plugins/globalmapviewer",
    "httpauth" to "com/mirth/connect/plugins/httpauth",
    "imageviewer" to "com/mirth/connect/plugins/imageviewer",
    "javascriptrule" to "com/mirth/connect/plugins/javascriptrule",
    "javascriptstep" to "com/mirth/connect/plugins/javascriptstep",
    "mapper" to "com/mirth/connect/plugins/mapper",
    "messagebuilder" to "com/mirth/connect/plugins/messagebuilder",
    "datapruner" to "com/mirth/connect/plugins/datapruner",
    "mllpmode" to "com/mirth/connect/plugins/mllpmode",
    "pdfviewer" to "com/mirth/connect/plugins/pdfviewer",
    "textviewer" to "com/mirth/connect/plugins/textviewer",
    "rulebuilder" to "com/mirth/connect/plugins/rulebuilder",
    "serverlog" to "com/mirth/connect/plugins/serverlog",
    "scriptfilerule" to "com/mirth/connect/plugins/scriptfilerule",
    "scriptfilestep" to "com/mirth/connect/plugins/scriptfilestep",
    "xsltstep" to "com/mirth/connect/plugins/xsltstep"
)

// Create client JAR tasks for each extension
extensionClientConfigs.forEach { (extName, srcPath) ->
    tasks.register<Jar>("${extName}ClientJar") {
        archiveBaseName.set("$extName-client")
        destinationDirectory.set(file("$extClientBuildDir/$extName"))

        from(sourceSets.main.get().output) {
            include("$srcPath/**")
        }
    }
}

// Task to build all extension client JARs
val buildExtensionClients by tasks.registering {
    group = "build"
    description = "Builds all extension client JARs"

    extensionClientConfigs.keys.forEach { extName ->
        dependsOn("${extName}ClientJar")
    }
}

// Task to copy client JARs to server's extension directories
val installExtensionClients by tasks.registering {
    group = "build"
    description = "Installs extension client JARs to server extensions directory"

    dependsOn(buildExtensionClients)

    doLast {
        val serverExtensionsDir = project(":server").file("setup/extensions")

        extensionClientConfigs.keys.forEach { extName ->
            val extDir = file("$serverExtensionsDir/$extName")
            if (extDir.exists()) {
                copy {
                    from("$extClientBuildDir/$extName") {
                        include("*-client-*.jar")
                    }
                    into(extDir)
                    rename { "$extName-client.jar" }
                }
            }
        }

        logger.lifecycle("Installed ${extensionClientConfigs.size} extension client JARs")
    }
}

tasks.named("assemble") {
    dependsOn(clientJar, buildExtensionClients)
}

artifacts {
    add("archives", clientJar)
}
