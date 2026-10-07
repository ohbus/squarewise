@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}

rootProject.name = "squarewise"
include(":app:accounts", ":app:expense-core", ":app:notifications", ":app:bff")
include(":libs:db", ":libs:security", ":libs:observability", ":libs:test-support", ":libs:errors", ":libs:ids")
include(":tools:benchmarks:jmh")
