plugins {
    `java-library`
    application
}

description = "Mirth Connect CLI - Command Line Interface"

// Command uses traditional src/ layout
sourceSets {
    main {
        java {
            srcDir("src")
        }
        resources {
            srcDir("conf")
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
    api(libs.commons.cli)
    api(libs.commons.codec)
    api(libs.commons.collections4)
    api(libs.commons.configuration2)
    api(libs.commons.io)
    api(libs.commons.lang3)
    api(libs.commons.logging)
    api(libs.commons.pool2)
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
    api(libs.xstream)
    api(libs.xpp3)

    // Jetty (for HTTP utilities)
    api(libs.jetty.util)

    // Utilities
    api(libs.velocity.engine.core)
    api(libs.velocity.tools.generic)
    api(libs.rhino)

    // Test
    testImplementation(libs.junit)
}

// Create mirth-cli.jar
val cliJar by tasks.registering(Jar::class) {
    archiveBaseName.set("mirth-cli")
    from(sourceSets.main.get().output)
}

// Create mirth-cli-launcher.jar with manifest
val cliLauncherJar by tasks.registering(Jar::class) {
    archiveBaseName.set("mirth-cli-launcher")
    from(sourceSets.main.get().output) {
        include("com/mirth/connect/cli/launcher/**")
    }
    manifest {
        attributes(
            "Main-Class" to "com.mirth.connect.cli.launcher.CommandLineLauncher",
            "Class-Path" to "cli-lib/"
        )
    }
}

application {
    mainClass.set("com.mirth.connect.cli.CommandLineInterface")
}

tasks.named("assemble") {
    dependsOn(cliJar, cliLauncherJar)
}

artifacts {
    add("archives", cliJar)
    add("archives", cliLauncherJar)
}
