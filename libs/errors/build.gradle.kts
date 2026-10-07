plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.spring.dependency.management)
}
kotlin { jvmToolchain(25) }
dependencies {
    api(libs.boot.web)
    api(libs.uuid.creator)
    implementation(libs.boot.data.jpa)
    testImplementation(libs.boot.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
tasks.withType<Test> { useJUnitPlatform() }


