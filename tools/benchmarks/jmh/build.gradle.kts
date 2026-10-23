import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import javax.inject.Inject

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
    target {
        compilations.create("jmh") {
            defaultSourceSet {
                kotlin.srcDir("src/jmh/kotlin")
            }
            associateWith(target.compilations.getByName("main"))
        }
    }
}

val jmhGenerator = configurations.create("jmhGenerator")

dependencies {
    add("jmhImplementation", project(":libs:errors"))
    add("jmhImplementation", libs.jackson.databind)
    add("jmhImplementation", libs.jackson.datatype.jsr310)
    add("jmhImplementation", libs.jmh.core)
    add("jmhAnnotationProcessor", libs.jmh.generator.annprocess)
    add(jmhGenerator.name, libs.jmh.core)
    add(jmhGenerator.name, "org.openjdk.jmh:jmh-generator-bytecode:1.37")
}

val jmhCompilation = kotlin.target.compilations.getByName("jmh")
val jmhClasses = jmhCompilation.output.classesDirs
val jmhRuntimeClasspath = jmhCompilation.runtimeDependencyFiles
val generatedSourcesDirectory = layout.buildDirectory.dir("jmh-generated-sources")
val generatedResourcesDirectory = layout.buildDirectory.dir("jmh-generated-resources")
val generatedClassesDirectory = layout.buildDirectory.dir("jmh-generated-classes")

abstract class JmhBytecodeGeneratorTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @get:Classpath
    abstract val generatorClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val classesDirs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val generatedSources: DirectoryProperty

    @get:OutputDirectory
    abstract val generatedResources: DirectoryProperty

    @TaskAction
    fun generate() {
        fileSystemOperations.delete { delete(generatedSources, generatedResources) }
        classesDirs.files.forEach { classesDir ->
            execOperations.javaexec {
                mainClass.set("org.openjdk.jmh.generators.bytecode.JmhBytecodeGenerator")
                classpath(generatorClasspath, runtimeClasspath, classesDirs)
                args(classesDir, generatedSources.get().asFile, generatedResources.get().asFile, "default")
            }
        }
    }
}

val jmhRunBytecodeGenerator = tasks.register<JmhBytecodeGeneratorTask>("jmhRunBytecodeGenerator") {
    group = "verification"
    description = "Generates JMH runners from compiled benchmark bytecode."
    dependsOn("compileJmhKotlin")
    generatorClasspath.from(jmhGenerator)
    runtimeClasspath.from(jmhRuntimeClasspath)
    classesDirs.from(jmhClasses)
    generatedSources.set(generatedSourcesDirectory)
    generatedResources.set(generatedResourcesDirectory)
}

val jmhCompileGeneratedClasses = tasks.register<JavaCompile>("jmhCompileGeneratedClasses") {
    group = "verification"
    description = "Compiles generated JMH runners."
    dependsOn(jmhRunBytecodeGenerator)
    source(generatedSourcesDirectory)
    destinationDirectory.set(generatedClassesDirectory)
    classpath = files(jmhClasses, jmhRuntimeClasspath)
    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
}

tasks.register<JavaExec>("jmh") {
    group = "verification"
    description = "Runs the JMH benchmark suite."
    dependsOn(jmhCompileGeneratedClasses)
    mainClass.set("org.openjdk.jmh.Main")
    classpath(jmhClasses, generatedClassesDirectory, generatedResourcesDirectory, jmhRuntimeClasspath)
    args("-wi", "2", "-i", "3", "-f", "1", "-rf", "json")
}

tasks.register("run") {
    group = "verification"
    description = "Runs the JMH benchmark suite."
    dependsOn("jmh")
}
