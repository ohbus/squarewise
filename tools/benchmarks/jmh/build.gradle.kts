plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jmh)
}

kotlin { jvmToolchain(25) }

dependencies {
    implementation(project(":libs:errors"))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jmh.core)
    annotationProcessor(libs.jmh.generator.annprocess)
}

jmh {
    warmupIterations.set(2)
    iterations.set(3)
    fork.set(1)
    resultFormat.set("JSON")
}

// Keep the repository validation command stable across JMH Gradle plugin versions.
tasks.register("run") { dependsOn("jmh") }
