/*
 * Root build file
 *
 * For more detailed information on multi-project builds, please refer to
 * https://docs.gradle.org/9.2.1/userguide/multi_project_builds.html
 */

allprojects {
    group = "com.mirth.connect"
    version = providers.gradleProperty("version").get()
}

subprojects {
    repositories {
        mavenCentral()
    }
}

// Configure Ant to have access to JUnit task

ant.lifecycleLogLevel = AntBuilder.AntMessagePriority.INFO

configurations {
    create("antJUnit")
}

dependencies {
    // ant-junit4 is required for JUnit 4 annotation support (@Test, etc.)
    "antJUnit"("org.apache.ant:ant-junit:1.10.15") {
        exclude(group = "junit", module = "junit")
    }
    "antJUnit"("org.apache.ant:ant-junit4:1.10.15") {
        exclude(group = "junit", module = "junit")
    }
}

repositories {
    mavenCentral()
}

// Make JUnit available to Ant
afterEvaluate {
    ant.withGroovyBuilder {
        "taskdef"(
            "name" to "junit",
            "classname" to "org.apache.tools.ant.taskdefs.optional.junit.JUnitTask",
            "classpath" to configurations["antJUnit"].asPath
        )
        "taskdef"(
            "name" to "junitreport",
            "classname" to "org.apache.tools.ant.taskdefs.optional.junit.XMLResultAggregator",
            "classpath" to configurations["antJUnit"].asPath
        )
    }
}

// Pass Gradle properties to Ant
project.findProperty("disableSigning")?.let {
    ant.properties["disableSigning"] = it.toString()
}

project.findProperty("disableTests")?.let {
    ant.properties["disableTests"] = it.toString()
}

project.findProperty("coverage")?.let {
    ant.properties["coverage"] = it.toString()
}

// Import existing Ant build for gradual migration
ant.importBuild("server/mirth-build.xml") { antTargetName ->
    // Rename conflicting Ant targets to avoid collision with Gradle built-in tasks
    when (antTargetName) {
        "init" -> "ant-init"
        "build" -> "ant-build"
        else -> antTargetName
    }
}

// Make the default Gradle build task delegate to Ant's build target
tasks.register("build") {
    dependsOn("ant-build")
    group = "build"
    description = "Builds the project using Ant (delegated)"
}