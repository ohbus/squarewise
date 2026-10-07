package com.subhrodip.squarewise.benchmarks

import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

/** Compares ordinary stack capture with the governed catalogued exception. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class ExceptionCreationBenchmark {
    private class BenchException : SquarewiseException(PlatformErrors.UNEXPECTED_INTERNAL_ERROR)

    /** Creates a standard exception with JVM stack capture. */
    @Benchmark
    fun standard(blackhole: Blackhole) {
        blackhole.consume(IllegalStateException("failure"))
    }

    /** Creates a governed exception with stable public classification. */
    @Benchmark
    fun governed(blackhole: Blackhole) {
        blackhole.consume(BenchException())
    }
}
