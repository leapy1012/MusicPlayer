pluginManagement {
    repositories {
        maven {
            name = "offlineMavenRepo"
            url = uri("file:///D:/Tools/Android/maven_repo")
        }
        // Declared for Gradle cache identity. With --offline these are never contacted
        // if the local Gradle cache (~/.gradle) was warmed once online.
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            name = "offlineMavenRepo"
            url = uri("file:///D:/Tools/Android/maven_repo")
        }
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "MusicPlayer"
include(":app")
include(":lib")
