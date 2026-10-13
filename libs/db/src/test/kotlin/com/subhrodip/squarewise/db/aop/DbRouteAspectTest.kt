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

        open fun plainMethod(): String {
            return "plain"
        }
    }

    @Test
    fun `intercepts DbRouteContext and establishes thread context`() {
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
    fun `intercepts readOnly Transactional and routes to reader`() {
        val target = SampleService()
        val method = SampleService::class.java.getMethod("readOnlyTx")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(target)
        `when`(joinPoint.proceed()).thenAnswer { target.readOnlyTx() }

        val result = aspect.aroundRoutedMethod(joinPoint)
        assertEquals("ok-tx-read", result)
    }

    @Test
    fun `intercepts write Transactional and routes to writer`() {
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
}
