plugins {
    `java-library`
    application
}

description = "Mirth Connect Manager - Server management utility"

// Manager uses traditional src/ layout
sourceSets {
    main {
        java {
            srcDir("src")
        }
        resources {
            srcDir("src")
            include("**/*.png", "**/*.gif", "**/*.properties")
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

    // Apache Commons
    api(libs.commons.beanutils)
    api(libs.commons.codec)
    api(libs.commons.collections4)
    api(libs.commons.configuration2)
    api(libs.commons.io)
    api(libs.commons.lang3)
    api(libs.commons.logging)
    api(libs.commons.text)

    // HTTP Client
    api(libs.httpclient)
    api(libs.httpcore)
    api(libs.httpmime)

    // JSON/XML
    api(libs.xstream)
    api(libs.xpp3)

    // UI Libraries
    api(libs.miglayout.core)
    api(libs.miglayout.swing)
    api(libs.swingx.core)
    api(libs.looks)

    // Utilities
    api(libs.rhino)
}

// Create mirth-manager-launcher.jar with manifest
val managerLauncherJar by tasks.registering(Jar::class) {
    archiveBaseName.set("mirth-manager-launcher")
    from(sourceSets.main.get().output)
    manifest {
        attributes(
            "Main-Class" to "com.mirth.connect.manager.ManagerLauncher",
            "Class-Path" to "manager-lib/"
        )
    }
}

application {
    mainClass.set("com.mirth.connect.manager.ManagerLauncher")
}

tasks.named("assemble") {
    dependsOn(managerLauncherJar)
}

artifacts {
    add("archives", managerLauncherJar)
}
