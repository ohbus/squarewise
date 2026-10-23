@file:Suppress("UnstableApiUsage")

pluginManagement {
    val develocityPluginVersion = providers.gradleProperty("develocityPluginVersion").get()
    plugins {
        id("com.gradle.develocity") version develocityPluginVersion
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("com.gradle.develocity")
}

val isCi = System.getenv("CI")?.equals("true", ignoreCase = true) == true

develocity {
    buildScan {
        publishing.onlyIf { isCi }
        uploadInBackground.set(!isCi)
        if (isCi) {
            termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
            termsOfUseAgree.set("yes")
            tag("CI")
        }
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
