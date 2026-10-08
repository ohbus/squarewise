plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}
kotlin { jvmToolchain(25) }
dependencies {
    // Shared causal-header/value contract; database auto-configuration remains disabled in the BFF.
    implementation(project(":libs:db"))
    implementation(project(":libs:security"))
    implementation(project(":libs:ids"))
    implementation(project(":libs:errors")) {
        // The BFF uses only the shared error catalog; it has no persistence layer.
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-data-jpa")
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-web")
    }
    implementation(libs.boot.actuator)
    implementation(libs.boot.validation)
    implementation(libs.boot.security)
    implementation(libs.boot.resource.server)
    implementation(libs.boot.graphql)
    implementation(libs.boot.amqp)
    implementation(libs.boot.webflux)
    implementation(libs.boot.webclient)
    implementation(libs.jackson.databind)
    implementation(libs.kotlin.reflect)

    runtimeOnly(libs.micrometer.prometheus)

    testImplementation(libs.boot.test)
    testImplementation(kotlin("test"))
}
configurations.configureEach {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-web")
}
tasks.withType<Test> { useJUnitPlatform() }
sourceSets {
    main {
        resources {
            srcDir(rootProject.file("contracts"))
        }
    }
}
tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
base { archivesName.set("squarewise-bff") }
springBoot { mainClass.set("com.subhrodip.squarewise.bff.BffApplicationKt") }
