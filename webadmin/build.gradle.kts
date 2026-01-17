plugins {
    `java-library`
    war
}

description = "Mirth Connect WebAdmin - Web-based admin console"

// WebAdmin uses traditional src/ layout with WebContent
sourceSets {
    main {
        java {
            srcDir("src")
        }
        resources {
            srcDir("src")
        }
    }
}

dependencies {
    // Project dependencies
    api(project(":donkey"))
    api(project(":server"))

    // Web dependencies (provided by container)
    providedCompile(libs.javax.servlet.api)
    providedCompile(libs.mortbay.apache.jsp)

    // Web frameworks
    api(libs.stripes)
    api(libs.displaytag)
    api(libs.json.simple)

    // Logging
    api(libs.commons.logging)
}

// Configure WAR task
tasks.war {
    archiveBaseName.set("webadmin")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Include WebContent directory (except web.xml which is handled by webXml property)
    from("WebContent") {
        into("")
        exclude("WEB-INF/web.xml")
    }

    // Set web.xml location
    webXml = file("WebContent/WEB-INF/web.xml")

    // Include compiled classes
    from(sourceSets.main.get().output) {
        into("WEB-INF/classes")
    }

    // Exclude libraries that should come from WEB-INF/lib in WebContent
    rootSpec.exclude("WEB-INF/lib/*.jar")

    // Copy libs from WebContent
    from("WebContent/WEB-INF/lib") {
        into("WEB-INF/lib")
    }
}

// Task to copy WAR to setup directory
val copyWarToSetup by tasks.registering(Copy::class) {
    dependsOn(tasks.war)
    from(tasks.war)
    into(file("${project(":server").projectDir}/setup/webapps"))
}

tasks.named("assemble") {
    dependsOn(tasks.war)
}
