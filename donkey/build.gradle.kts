plugins {
    `java-library`
}

description = "Donkey - Message routing and transformation engine"

// Donkey uses Maven-style source layout
sourceSets {
    main {
        java {
            srcDir("src/main/java")
        }
        resources {
            srcDirs("conf", "donkeydbconf")
        }
    }
    test {
        java {
            srcDir("src/test/java")
        }
        resources {
            // Tests need access to donkey-testing.properties and database configs
            srcDirs("conf", "donkeydbconf")
        }
    }
}

dependencies {
    // Apache Commons
    api(libs.commons.beanutils)
    api(libs.commons.codec)
    api(libs.commons.collections4)
    api(libs.commons.dbcp2)
    api(libs.commons.dbutils)
    api(libs.commons.io)
    api(libs.commons.lang3)
    api(libs.commons.logging)
    api(libs.commons.math3)
    api(libs.commons.pool2)
    api(libs.commons.text)

    // Logging
    api(libs.log4j.api)
    api(libs.log4j.core)
    api(libs.log4j.bridge)
    api(libs.slf4j.api)
    implementation(libs.slf4j.log4j12)

    // Database
    api(libs.hikaricp)
    api(libs.quartz)
    implementation(libs.derby)
    implementation(libs.jtds)
    implementation(libs.mysql.connector)
    implementation(libs.mssql.jdbc)
    implementation(libs.postgresql)
    implementation(libs.ojdbc8)

    // DI/IoC
    api(libs.guice)
    implementation(libs.aopalliance.repackaged)
    implementation(libs.javax.inject)

    // XML/Serialization
    api(libs.xstream)
    api(libs.xpp3)

    // Utilities
    api(libs.javassist)
    api(libs.guava)
    implementation(libs.failureaccess)
    implementation(libs.checker.qual)
    implementation(libs.error.prone.annotations)
    implementation(libs.j2objc.annotations)
    implementation(libs.jsr305)
    implementation(libs.listenablefuture)

    // Test dependencies
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.byte.buddy)
    testRuntimeOnly(libs.byte.buddy.agent)
    testRuntimeOnly(libs.objenesis)
}

// Create the donkey-model.jar with model classes
val donkeyModelJar by tasks.registering(Jar::class) {
    archiveBaseName.set("donkey-model")
    from(sourceSets.main.get().output) {
        include("com/mirth/connect/donkey/model/**")
        include("com/mirth/connect/donkey/util/**")
    }
}

// Create the donkey-server.jar with server classes
val donkeyServerJar by tasks.registering(Jar::class) {
    archiveBaseName.set("donkey-server")
    from(sourceSets.main.get().output) {
        include("com/mirth/connect/donkey/server/**")
    }
}

// Create the donkey dbconf jar
val donkeyDbconfJar by tasks.registering(Jar::class) {
    archiveBaseName.set("donkey-dbconf")
    from("donkeydbconf")
}

// Make sure custom JARs are built
tasks.named("assemble") {
    dependsOn(donkeyModelJar, donkeyServerJar, donkeyDbconfJar)
}

// Publish artifacts for other modules to consume
artifacts {
    add("archives", donkeyModelJar)
    add("archives", donkeyServerJar)
    add("archives", donkeyDbconfJar)
}

// Configuration for other modules to depend on specific JARs
configurations {
    create("model") {
        isCanBeConsumed = true
        isCanBeResolved = false
    }
    create("server") {
        isCanBeConsumed = true
        isCanBeResolved = false
    }
}

artifacts {
    add("model", donkeyModelJar)
    add("server", donkeyServerJar)
}
