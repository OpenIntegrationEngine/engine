import java.text.SimpleDateFormat
import java.util.Date

plugins {
    java
    `java-library`
    id("com.netflix.nebula.ospackage") version "11.10.1" apply false
}

// Project-wide properties
val mirthVersion by extra("4.5.2")
val javaVersion by extra(JavaVersion.VERSION_17)

allprojects {
    group = "com.mirth.connect"
    version = mirthVersion

    repositories {
        mavenCentral()
        // Local repository for non-Maven-Central JARs
        maven {
            name = "libs-local"
            url = uri("${rootProject.projectDir}/libs-local")
        }
        flatDir {
            dirs("${rootProject.projectDir}/libs-local/flat")
        }
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")

    java {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    // Force resolution to use local JAR versions for dependencies not in Maven Central
    configurations.all {
        resolutionStrategy {
            force("org.swinglabs:swingx-core:1.6.2")
            force("com.jgoodies:looks:2.3.1")
            force("com.sun.xml.fastinfoset:FastInfoset:1.2.13")
            force("com.sun.istack:istack-commons-runtime:3.0.6")
            force("javax.jws:jsr181-api:1.0")
            force("org.glassfish.external:management-api:3.2.1.b001")
            force("org.glassfish.gmbal:gmbal-api-only:3.1.0.b001")
            force("org.glassfish.ha:ha-api:3.1.9")
            force("com.sun.xml.ws:policy:2.7.2")
            force("org.jvnet.mimepull:mimepull:1.9.7")
            force("com.sun.xml.messaging.saaj:saaj-impl:1.0")
            force("org.jvnet.staxex:stax-ex:1.8")
            force("com.sun.xml.stream.buffer:streambuffer:1.5.4")
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.isDeprecation = true
        options.compilerArgs.addAll(listOf(
            "-Xlint:unchecked"
        ))
    }

    tasks.withType<Jar>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    tasks.withType<Test>().configureEach {
        useJUnit()
        maxHeapSize = "2g"
        jvmArgs(
            "--add-exports=java.base/com.sun.crypto.provider=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens=java.base/java.text=ALL-UNNAMED",
            "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
            "--add-opens=java.sql/java.sql=ALL-UNNAMED",
            "--add-opens=java.sql.rowset/com.sun.rowset=ALL-UNNAMED",
            "--add-opens=java.sql.rowset/com.sun.rowset.internal=ALL-UNNAMED",
            "--add-opens=java.sql.rowset/com.sun.rowset.providers=ALL-UNNAMED",
            "--add-opens=java.sql.rowset/javax.sql.rowset=ALL-UNNAMED",
            "--add-opens=java.xml/com.sun.org.apache.xalan.internal.xsltc.trax=ALL-UNNAMED"
        )
    }

    // Common test dependencies for all modules
    dependencies {
        testImplementation(rootProject.libs.junit)
        testImplementation(rootProject.libs.mockito.core)
        testImplementation(rootProject.libs.mockito.inline)
        testImplementation(rootProject.libs.hamcrest)
        testRuntimeOnly(rootProject.libs.byte.buddy)
        testRuntimeOnly(rootProject.libs.byte.buddy.agent)
        testRuntimeOnly(rootProject.libs.objenesis)
    }
}

// Create version.properties for server
tasks.register("generateVersionProperties") {
    val outputDir = file("${project(":server").projectDir}/build/resources/main")
    val outputFile = file("$outputDir/version.properties")

    outputs.file(outputFile)

    doLast {
        outputDir.mkdirs()
        val dateFormat = SimpleDateFormat("MMMM d, yyyy")
        outputFile.writeText("""
            mirth.version=$mirthVersion
            mirth.date=${dateFormat.format(Date())}
        """.trimIndent())
    }
}

// Task to assemble the complete setup directory
tasks.register("assembleSetup") {
    group = "build"
    description = "Assembles the complete setup directory with all modules"

    dependsOn(
        ":donkey:build",
        ":server:build",
        ":client:build",
        ":command:build",
        ":manager:build",
        ":generator:build",
        ":webadmin:build",
        ":server:assembleSetup"
    )
}

// Clean all build outputs
tasks.register("cleanAll") {
    group = "build"
    description = "Cleans all build directories"

    dependsOn(subprojects.map { "${it.path}:clean" })

    doLast {
        delete(file("${project(":server").projectDir}/setup"))
        delete(file("${project(":server").projectDir}/dist"))
    }
}

// Wrapper configuration
tasks.wrapper {
    gradleVersion = "8.5"
    distributionType = Wrapper.DistributionType.BIN
}
