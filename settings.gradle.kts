rootProject.name = "open-integration-engine"

// Enable version catalog
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Include all modules
include("donkey")
include("server")
include("client")
include("command")
include("manager")
include("generator")
include("webadmin")

// Configure plugin management
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// Configure dependency resolution
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        // Local repository for non-Maven-Central JARs
        maven {
            name = "libs-local"
            url = uri("${rootProject.projectDir}/libs-local")
        }
        // Fallback flat directory for any remaining local JARs
        flatDir {
            dirs("${rootProject.projectDir}/libs-local/flat")
        }
    }
    // Version catalog is automatically loaded from gradle/libs.versions.toml
}
