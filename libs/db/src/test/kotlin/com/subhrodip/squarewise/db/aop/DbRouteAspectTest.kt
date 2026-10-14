package com.subhrodip.squarewise.db.aop

import com.subhrodip.squarewise.db.routing.DbContextHolder
import com.subhrodip.squarewise.db.routing.DbExecutionContext
import com.subhrodip.squarewise.db.routing.DbOperationKind
import com.subhrodip.squarewise.db.routing.DbReader
import com.subhrodip.squarewise.db.routing.DbRouteContext
import com.subhrodip.squarewise.db.routing.DbWriter
import com.subhrodip.squarewise.db.routing.ReadConsistency
import java.lang.reflect.Method
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.Signature
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
            assertFalse(ctx.readerEligible)
            assertEquals(DbOperationKind.COMMAND, ctx.kind)
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

        @DbReader(target = "search", operation = "search.items")
        open fun dbReaderMethod(): String {
            val ctx = DbContextHolder.current()
            assertEquals("search.items", ctx.operationName)
            assertEquals("search", ctx.targetReader)
            assertTrue(ctx.readerEligible)
            assertEquals(DbOperationKind.QUERY, ctx.kind)
            return "ok-db-reader"
        }

        @DbWriter(operation = "persist.item")
        open fun dbWriterMethod(): String {
            val ctx = DbContextHolder.current()
            assertEquals("persist.item", ctx.operationName)
            assertFalse(ctx.readerEligible)
            assertEquals(DbOperationKind.COMMAND, ctx.kind)
            return "ok-db-writer"
        }

        @DbWriter(operation = "persist.shard", target = "shard-1")
        open fun dbWriterTargetMethod(): String {
            val ctx = DbContextHolder.current()
            assertEquals("persist.shard", ctx.operationName)
            assertEquals("shard-1", ctx.targetWriter)
            assertFalse(ctx.readerEligible)
            assertEquals(DbOperationKind.COMMAND, ctx.kind)
            return "ok-db-writer-target"
        }

        @DbRouteContext(operation = "route.shard", kind = DbOperationKind.COMMAND, readerEligible = false, targetWriter = "shard-2")
        open fun dbRouteTargetWriterMethod(): String {
            val ctx = DbContextHolder.current()
            assertEquals("route.shard", ctx.operationName)
            assertEquals("shard-2", ctx.targetWriter)
            assertFalse(ctx.readerEligible)
            return "ok-db-route-target-writer"
        }

        open fun plainMethod(): String {
            return "plain"
        }
    }

    @DbReader(target = "reporting", operation = "interface.read")
    interface SampleReaderInterface {
        fun interfaceRead(): String
    }

    open class SampleReaderServiceImpl : SampleReaderInterface {
        override fun interfaceRead(): String {
            val ctx = DbContextHolder.current()
            assertEquals("interface.read", ctx.operationName)
            assertEquals("reporting", ctx.targetReader)
            assertTrue(ctx.readerEligible)
            return "ok-interface-read"
        }
    }

    @Test
    fun `intercepts DbReader on implemented interface and establishes reader context`() {
        val target = SampleReaderServiceImpl()
        val method = SampleReaderServiceImpl::class.java.getMethod("interfaceRead")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(target)
        `when`(joinPoint.proceed()).thenAnswer { target.interfaceRead() }

        val result = aspect.aroundRoutedMethod(joinPoint)
        assertEquals("ok-interface-read", result)
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
    fun `intercepts DbWriter with target and sets targetWriter`() {
        val target = SampleService()
        val method = SampleService::class.java.getMethod("dbWriterTargetMethod")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(target)
        `when`(joinPoint.proceed()).thenAnswer { target.dbWriterTargetMethod() }

        val result = aspect.aroundRoutedMethod(joinPoint)
        assertEquals("ok-db-writer-target", result)
    }

    @Test
    fun `intercepts DbRouteContext with targetWriter and sets targetWriter`() {
        val target = SampleService()
        val method = SampleService::class.java.getMethod("dbRouteTargetWriterMethod")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(target)
        `when`(joinPoint.proceed()).thenAnswer { target.dbRouteTargetWriterMethod() }

        val result = aspect.aroundRoutedMethod(joinPoint)
        assertEquals("ok-db-route-target-writer", result)
    }

    @Test
    fun `pins inner read-only method to writer when executing inside active writer transaction`() {
        val target = BlankTargetReaderService()
        val method = BlankTargetReaderService::class.java.getMethod("readBlank")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(target)
        `when`(joinPoint.proceed()).thenAnswer {
            val ctx = DbContextHolder.current()
            // Invariant: must remain on writer despite readBlank being @DbReader
            assertFalse(ctx.readerEligible)
            assertEquals(DbOperationKind.COMMAND, ctx.kind)
            "pinned-to-writer"
        }

        val writerContext = DbExecutionContext(
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
        val target = BlankTargetReaderService()
        val method = BlankTargetReaderService::class.java.getMethod("readBlank")
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

        val parentContext = DbExecutionContext(
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
    fun `plain Transactional readOnly does not create reader context and preserves default writer route`() {
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

    @DbReader
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

    @DbWriter
    open class ClassAnnotatedWriter {
        open fun command(): String {
            val ctx = DbContextHolder.current()
            assertEquals("classannotatedwriter.command", ctx.operationName)
            assertFalse(ctx.readerEligible)
            assertEquals(DbOperationKind.COMMAND, ctx.kind)
            return "from-class-writer"
        }
    }

    @DbRouteContext(operation = "custom.op", kind = DbOperationKind.QUERY, readerEligible = true, targetReader = "")
    open class ClassAnnotatedRoute {
        open fun routed(): String {
            val ctx = DbContextHolder.current()
            assertEquals("custom.op", ctx.operationName)
            assertTrue(ctx.readerEligible)
            assertEquals(null, ctx.targetReader)
            return "from-class-route"
        }
    }

    @Transactional(readOnly = false)
    open class SampleTransactionalService {
        open fun execute(): String {
            val ctx = DbContextHolder.current()
            assertFalse(ctx.readerEligible)
            assertEquals(DbOperationKind.COMMAND, ctx.kind)
            return "from-tx"
        }
    }

    open class BlankTargetReaderService {
        @DbReader(operation = "", target = "")
        open fun readBlank(): String {
            val ctx = DbContextHolder.current()
            assertEquals(null, ctx.targetReader)
            assertTrue(ctx.readerEligible)
            return "blank-read"
        }
    }

    @Test
    fun `inherits DbWriter from class level`() {
        val target = ClassAnnotatedWriter()
        val method = ClassAnnotatedWriter::class.java.getMethod("command")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(target)
        `when`(joinPoint.proceed()).thenAnswer { target.command() }

        val result = aspect.aroundRoutedMethod(joinPoint)
        assertEquals("from-class-writer", result)
    }

    @Test
    fun `inherits DbRouteContext from class level with blank targetReader resolving to null`() {
        val target = ClassAnnotatedRoute()
        val method = ClassAnnotatedRoute::class.java.getMethod("routed")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(target)
        `when`(joinPoint.proceed()).thenAnswer { target.routed() }

        val result = aspect.aroundRoutedMethod(joinPoint)
        assertEquals("from-class-route", result)
    }

    @Test
    fun `inherits Transactional read-write from class level`() {
        val target = SampleTransactionalService()
        val method = SampleTransactionalService::class.java.getMethod("execute")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(target)
        `when`(joinPoint.proceed()).thenAnswer { target.execute() }

        val result = aspect.aroundRoutedMethod(joinPoint)
        assertEquals("from-tx", result)
    }

    @Test
    fun `DbReader with blank target resolves targetReader to null`() {
        val target = BlankTargetReaderService()
        val method = BlankTargetReaderService::class.java.getMethod("readBlank")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(target)
        `when`(joinPoint.proceed()).thenAnswer { target.readBlank() }

        val result = aspect.aroundRoutedMethod(joinPoint)
        assertEquals("blank-read", result)
    }

    @Test
    fun `handles null target by falling back to method declaringClass`() {
        val method = SampleService::class.java.getMethod("customRoutedRead")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(null)
        `when`(joinPoint.proceed()).thenAnswer {
            val ctx = DbContextHolder.current()
            assertEquals("custom.read", ctx.operationName)
            assertEquals("analytics", ctx.targetReader)
            assertTrue(ctx.readerEligible)
            assertEquals(DbOperationKind.QUERY, ctx.kind)
            "fallback-ok"
        }

        val result = aspect.aroundRoutedMethod(joinPoint)
        assertEquals("fallback-ok", result)
    }

    open class Service {
        @DbReader
        open fun query(): String {
            val ctx = DbContextHolder.current()
            assertEquals("db.query", ctx.operationName)
            return "from-service"
        }

        @DbReader
        open fun `$$$$`(): String {
            val ctx = DbContextHolder.current()
            assertEquals("db.query", ctx.operationName)
            return "from-special"
        }
    }

    @Test
    fun `operation name resolution handles empty class prefix by falling back to db`() {
        val target = Service()
        val method = Service::class.java.getMethod("query")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(target)
        `when`(joinPoint.proceed()).thenAnswer { target.query() }

        val result = aspect.aroundRoutedMethod(joinPoint)
        assertEquals("from-service", result)
    }

    @Test
    fun `operation name resolution falls back to db dot query for special characters`() {
        val target = Service()
        val method = Service::class.java.getMethod("$$$$")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(target)
        `when`(joinPoint.proceed()).thenAnswer { target.`$$$$`() }

        val result = aspect.aroundRoutedMethod(joinPoint)
        assertEquals("from-special", result)
    }

    @Test
    fun `resolveOperationName handles null method and targetClass`() {
        val resolveMethod = DbRouteAspect::class.java.getDeclaredMethod(
            "resolveOperationName",
            String::class.java,
            Method::class.java,
            Class::class.java
        ).apply { isAccessible = true }

        val result = resolveMethod.invoke(aspect, "", null, null)
        assertEquals("unclassified.query", result)
    }

    @Test
    fun `inherits command context when active context has readerEligible true but kind COMMAND`() {
        val target = SampleService()
        val method = SampleService::class.java.getMethod("customRoutedRead")
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(MethodSignature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.target).thenReturn(target)
        `when`(joinPoint.proceed()).thenAnswer {
            val ctx = DbContextHolder.current()
            assertEquals(DbOperationKind.COMMAND, ctx.kind)
            assertFalse(ctx.readerEligible)
            "inside-command"
        }

        DbContextHolder.withContext(
            DbExecutionContext("outer.cmd", DbOperationKind.COMMAND, readerEligible = true)
        ) {
            val result = aspect.aroundRoutedMethod(joinPoint)
            assertEquals("inside-command", result)
        }
    }

    @Test
    fun `handles non-MethodSignature joinPoint signature by proceeding directly`() {
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val signature = mock(Signature::class.java)

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(joinPoint.proceed()).thenReturn("direct-proceed")

        val result = aspect.aroundRoutedMethod(joinPoint)
        assertEquals("direct-proceed", result)
    }
}
