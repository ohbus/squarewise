plugins {
    alias(libs.plugins.spotless)
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.cyclonedx) apply false
    alias(libs.plugins.sonarqube)
}

sonar {
    properties {
        property("sonar.projectKey", "ohbus_squarewise")
        property("sonar.organization", "ohbus")
    }
}

spotless {
    format("approvedKotlinBaseline") {
        target("app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/categories/ExpenseCategory.kt")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

group = "com.subhrodip.squarewise"
version = "0.1.0-SNAPSHOT"

subprojects {
    group = rootProject.group
    version = rootProject.version
    apply(plugin = "org.cyclonedx.bom")
    apply(plugin = "jacoco")

    dependencyLocking {
        lockAllConfigurations()
    }

    tasks.withType<JacocoReport>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}

dependencyLocking {
    lockAllConfigurations()
}

