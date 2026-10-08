plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}
kotlin { jvmToolchain(25) }
dependencies {
    implementation(project(":libs:db"))
    implementation(project(":libs:security"))
    implementation(project(":libs:errors"))
    implementation(project(":libs:ids"))
    implementation(libs.boot.actuator)
    implementation(libs.boot.web)
    implementation(libs.boot.validation)
    implementation(libs.boot.security)
    implementation(libs.boot.resource.server)
    implementation(libs.boot.data.jpa)
    implementation(libs.boot.amqp)
    implementation(libs.boot.flyway)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)

    runtimeOnly(libs.micrometer.prometheus)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.boot.test)

    testRuntimeOnly(libs.h2)
}
tasks.withType<Test> { useJUnitPlatform() }
base { archivesName.set("squarewise-expense-core") }
springBoot { mainClass.set("com.subhrodip.squarewise.expensecore.ExpenseCoreApplicationKt") }
