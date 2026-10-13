package com.subhrodip.squarewise.db.aop

import com.subhrodip.squarewise.db.routing.DbContextHolder
import com.subhrodip.squarewise.db.routing.DbOperationKind
import com.subhrodip.squarewise.db.routing.DbRouteContext
import com.subhrodip.squarewise.db.routing.ReadConsistency
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.transaction.annotation.Transactional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests for [DbRouteAspect] annotation and transactional routing interception. */
class DbRouteAspectTest {

    private val aspect = DbRouteAspect()

    open class SampleService {
        @DbRouteContext(operation = "custom.read", targetReader = "analytics")
        open fun customRoutedRead(): String {
            val ctx = DbContextHolder.current()
            assertEquals("custom.read", ctx.operationName)
            assertEquals("analytics", ctx.targetReader)
            assertTrue(ctx.readerEligible)
            assertEquals(DbOperationKind.QUERY, ctx.kind)
            return "ok-read"
        }

        @Transactional(readOnly = true)
        open fun readOnlyTx(): String {
            val ctx = DbContextHolder.current()
            assertTrue(ctx.readerEligible)
            assertEquals(DbOperationKind.QUERY, ctx.kind)
            assertEquals(ReadConsistency.EVENTUAL, ctx.consistency)
            return "ok-tx-read"
        }

        @Transactional(readOnly = false)
        open fun writeTx(): String {
            val ctx = DbContextHolder.current()
            assertFalse(ctx.readerEligible)
            assertEquals(DbOperationKind.COMMAND, ctx.kind)
            assertEquals(ReadConsistency.STRONG, ctx.consistency)
            return "ok-tx-write"
        }

        @com.subhrodip.squarewise.db.routing.DbReader(target = "search", operation = "search.items")
        open fun dbReaderMethod(): String {
            val ctx = DbContextHolder.current()
            assertEquals("search.items", ctx.operationName)
            assertEquals("search", ctx.targetReader)
            assertTrue(ctx.readerEligible)
            assertEquals(DbOperationKind.QUERY, ctx.kind)
            return "ok-db-reader"
        }

        @com.subhrodip.squarewise.db.routing.DbWriter(operation = "persist.item")
        open fun dbWriterMethod(): String {
            val ctx = DbContextHolder.current()
            assertEquals("persist.item", ctx.operationName)
            assertFalse(ctx.readerEligible)
            assertEquals(DbOperationKind.COMMAND, ctx.kind)
            return "ok-db-writer"
        }

        open fun plainMethod(): String {
            return "plain"
        }
    }

    @Test
    fun `intercepts DbReader and establishes reader context with target`() {
        val target = SampleService()
        val method = SampleService::class.java.getMethod("dbReaderMethod")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(target)
        `when`(joinPoint.proceed()).thenAnswer { target.dbReaderMethod() }

        val result = aspect.aroundRoutedMethod(joinPoint)
        assertEquals("ok-db-reader", result)
    }

    @Test
    fun `intercepts DbWriter and establishes writer command context`() {
        val target = SampleService()
        val method = SampleService::class.java.getMethod("dbWriterMethod")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(target)
        `when`(joinPoint.proceed()).thenAnswer { target.dbWriterMethod() }

        val result = aspect.aroundRoutedMethod(joinPoint)
        assertEquals("ok-db-writer", result)
    }

    @Test
    fun `pins inner read-only method to writer when executing inside active writer transaction`() {
        val target = SampleService()
        val method = SampleService::class.java.getMethod("readOnlyTx")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(target)
        `when`(joinPoint.proceed()).thenAnswer {
            val ctx = DbContextHolder.current()
            // Invariant: must remain on writer despite readOnly = true
            assertFalse(ctx.readerEligible)
            assertEquals(DbOperationKind.COMMAND, ctx.kind)
            "pinned-to-writer"
        }

        val writerContext = com.subhrodip.squarewise.db.routing.DbExecutionContext(
            operationName = "outer.command",
            kind = DbOperationKind.COMMAND,
            readerEligible = false
        )
        val result = DbContextHolder.withContext(writerContext) {
            aspect.aroundRoutedMethod(joinPoint)
        }
        assertEquals("pinned-to-writer", result)
    }

    @Test
    fun `inherits parent targetReader when inner read context leaves target blank`() {
        val target = SampleService()
        val method = SampleService::class.java.getMethod("readOnlyTx")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(target)
        `when`(joinPoint.proceed()).thenAnswer {
            val ctx = DbContextHolder.current()
            assertEquals("analytics", ctx.targetReader)
            assertTrue(ctx.readerEligible)
            "inherited-target"
        }

        val parentContext = com.subhrodip.squarewise.db.routing.DbExecutionContext(
            operationName = "outer.read",
            kind = DbOperationKind.QUERY,
            readerEligible = true,
            targetReader = "analytics"
        )
        val result = DbContextHolder.withContext(parentContext) {
            aspect.aroundRoutedMethod(joinPoint)
        }
        assertEquals("inherited-target", result)
    }

    @Test
    fun `intercepts custom DbRouteContext and establishes custom context`() {
        val target = SampleService()
        val method = SampleService::class.java.getMethod("customRoutedRead")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(target)
        `when`(joinPoint.proceed()).thenAnswer { target.customRoutedRead() }

        val result = aspect.aroundRoutedMethod(joinPoint)
        assertEquals("ok-read", result)
    }

    @Test
    fun `intercepts writeTx and establishes write command context`() {
        val target = SampleService()
        val method = SampleService::class.java.getMethod("writeTx")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(target)
        `when`(joinPoint.proceed()).thenAnswer { target.writeTx() }

        val result = aspect.aroundRoutedMethod(joinPoint)
        assertEquals("ok-tx-write", result)
    }

    @Test
    fun `unannotated method proceeds without establishing context`() {
        val target = SampleService()
        val method = SampleService::class.java.getMethod("plainMethod")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(target)
        `when`(joinPoint.proceed()).thenReturn("plain")

        val result = aspect.aroundRoutedMethod(joinPoint)
        assertEquals("plain", result)
    }

    @com.subhrodip.squarewise.db.routing.DbReader
    open class ClassAnnotatedReader {
        open fun queryWithoutMethodAnnotation(): String {
            val ctx = DbContextHolder.current()
            assertEquals("classannotatedreader.querywithoutmethodannotation", ctx.operationName)
            assertTrue(ctx.readerEligible)
            assertEquals(DbOperationKind.QUERY, ctx.kind)
            return "from-class-reader"
        }
    }

    @Test
    fun `inherits DbReader from class level and auto-resolves operation name`() {
        val target = ClassAnnotatedReader()
        val method = ClassAnnotatedReader::class.java.getMethod("queryWithoutMethodAnnotation")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(target)
        `when`(joinPoint.proceed()).thenAnswer { target.queryWithoutMethodAnnotation() }

        val result = aspect.aroundRoutedMethod(joinPoint)
        assertEquals("from-class-reader", result)
    }
}
