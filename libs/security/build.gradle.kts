plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(25) }
dependencies {
    api(project(":libs:errors"))
    api(libs.boot.security)
    api(libs.boot.resource.server)
    api(libs.boot.webflux)
    api(libs.boot.data.redis)
    api(libs.boot.actuator)
    testImplementation(libs.boot.test)
    testImplementation(libs.boot.web)
    testImplementation(libs.boot.webflux)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> { useJUnitPlatform() }
