package com.subhrodip.squarewise.benchmarks

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.subhrodip.squarewise.errors.web.ProblemDetailsDto
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.net.URI
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Measures JSON serialization of the additive public error contract. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class ProblemSerializationBenchmark {
    private val mapper = ObjectMapper().registerModule(JavaTimeModule())
    private val problem = ProblemDetailsDto(
        type = URI.create("https://squarewise.dev/problems/validation-failed"),
        title = "Validation failed",
        status = 400,
        detail = "The request could not be accepted.",
        instance = "/accounts/v1/me",
        code = "VALIDATION_FAILED",
        requestId = "0199f8ae-1c3d-7abc-8def-0123456789ab",
        source = "accounts",
        timestamp = Instant.parse("2026-01-01T00:00:00Z"),
        numericCode = "213201",
        errorName = "REQUEST_VALIDATION_FAILED",
    )

    /** Serializes one stable RFC 9457 response. */
    @Benchmark
    fun serialize(blackhole: Blackhole) {
        blackhole.consume(mapper.writeValueAsString(problem))
    }
}
