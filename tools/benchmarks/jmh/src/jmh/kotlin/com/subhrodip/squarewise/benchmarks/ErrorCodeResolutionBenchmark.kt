package com.subhrodip.squarewise.benchmarks

import com.subhrodip.squarewise.errors.catalog.ErrorCatalog
import com.subhrodip.squarewise.errors.code.ErrorCode
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

/** Measures direct six-digit decomposition and static catalog resolution. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class ErrorCodeResolutionBenchmark {
    private val code = ErrorCode("213201")

    /** Resolves decomposition properties without reflective lookup. */
    @Benchmark
    fun decompose(blackhole: Blackhole) {
        blackhole.consume(code.domainDigit + code.moduleDigit + code.layerDigit + code.categoryDigit + code.sequence)
    }

    /** Resolves one immutable definition from the compiled catalog. */
    @Benchmark
    fun catalogLookup(blackhole: Blackhole) {
        blackhole.consume(ErrorCatalog.find(code.value))
    }
}
