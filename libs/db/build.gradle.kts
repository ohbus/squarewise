plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(25) }
dependencies {
    api(project(":libs:errors"))
    compileOnly(libs.boot.data.jpa)
    compileOnly(libs.boot.flyway)
    compileOnly(libs.boot.web)
    api(project(":libs:observability"))
    testImplementation(kotlin("test"))
    testImplementation(libs.boot.web)
    testImplementation(libs.boot.test)
    testImplementation(libs.boot.data.jpa)
    testImplementation(libs.postgresql)
}

tasks.withType<Test> { useJUnitPlatform() }
