plugins {
    `java-library`
    application
}

description = "Mirth Connect Generator - Data model generator"

// Generator uses traditional src/ layout with embedded test folder
sourceSets {
    main {
        java {
            srcDir("src")
            exclude("**/test/**")
        }
    }
    test {
        java {
            srcDir("src/com/mirth/connect/model/generator/test")
        }
    }
}

dependencies {
    // Logging
    api(libs.log4j.api)
    api(libs.log4j.core)
    api(libs.log4j.bridge)
    api(libs.slf4j.api)
    implementation(libs.slf4j.log4j12)

    // Apache Commons
    api(libs.commons.collections4)
    api(libs.commons.io)
    api(libs.commons.lang3)

    // Template engine
    api(libs.velocity.engine.core)

    // Test
    testImplementation(libs.junit)
    testRuntimeOnly(libs.mirth.vocab)
}

// Create model-generator.jar
val modelGeneratorJar by tasks.registering(Jar::class) {
    archiveBaseName.set("model-generator")
    from(sourceSets.main.get().output)
}

// Create mirth-vocab jar (if generated)
val mirthVocabJar by tasks.registering(Jar::class) {
    archiveBaseName.set("mirth-vocab")
    archiveVersion.set("1.2")
    // This would include generated vocabulary classes
    from(sourceSets.main.get().output) {
        include("com/mirth/connect/model/vocab/**")
    }
}

application {
    mainClass.set("com.mirth.connect.model.generator.ModelGenerator")
}

tasks.named("assemble") {
    dependsOn(modelGeneratorJar)
}

artifacts {
    add("archives", modelGeneratorJar)
}
