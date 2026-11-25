pluginManagement {
    repositories {
        maven { url = uri("file:///D:/Tools/Android/maven_repo") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("file:///D:/Tools/Android/maven_repo") }
    }
}

rootProject.name = "MusicPlayer"
include(":app")
include(":lib")

