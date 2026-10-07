plugins {
    alias(libs.plugins.kotlin.jvm)
}
kotlin { jvmToolchain(25) }
dependencies {
    api(project(":libs:errors"))
    testImplementation(libs.boot.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
tasks.withType<Test> { useJUnitPlatform() }
