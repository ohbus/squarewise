package com.subhrodip.squarewise.db.aop

import com.subhrodip.squarewise.db.routing.DbContextHolder
import com.subhrodip.squarewise.db.routing.DbExecutionContext
import com.subhrodip.squarewise.db.routing.DbOperationKind
import com.subhrodip.squarewise.db.routing.DbRouteContext
import com.subhrodip.squarewise.db.routing.ReadConsistency
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.transaction.annotation.Transactional
import com.subhrodip.squarewise.db.routing.DbReader
import com.subhrodip.squarewise.db.routing.DbWriter
import org.springframework.core.annotation.AnnotatedElementUtils
import java.lang.reflect.Method

/**
 * Aspect that intercepts [@DbRouteContext], [@DbReader], [@DbWriter], and [@Transactional]
 * methods/classes to establish thread-bound [DbExecutionContext] prior to database connection acquisition.
 *
 * Ordered before Spring's transaction interceptor (HIGHEST_PRECEDENCE + 50) so transactions
 * observe the established routing context.
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
class DbRouteAspect {

    @Around(
        "@annotation(com.subhrodip.squarewise.db.routing.DbRouteContext) || " +
        "@within(com.subhrodip.squarewise.db.routing.DbRouteContext) || " +
        "@annotation(com.subhrodip.squarewise.db.routing.DbReader) || " +
        "@within(com.subhrodip.squarewise.db.routing.DbReader) || " +
        "@annotation(com.subhrodip.squarewise.db.routing.DbWriter) || " +
        "@within(com.subhrodip.squarewise.db.routing.DbWriter) || " +
        "@annotation(org.springframework.transaction.annotation.Transactional) || " +
        "@within(org.springframework.transaction.annotation.Transactional)"
    )
    fun aroundRoutedMethod(joinPoint: ProceedingJoinPoint): Any? {
        val signature = joinPoint.signature as? MethodSignature
        val method = signature?.method
        val targetClass = joinPoint.target?.javaClass ?: method?.declaringClass

        val dbWriterAnnotation = method?.let { AnnotatedElementUtils.findMergedAnnotation(it, DbWriter::class.java) }
            ?: targetClass?.let { AnnotatedElementUtils.findMergedAnnotation(it, DbWriter::class.java) }

        val dbReaderAnnotation = method?.let { AnnotatedElementUtils.findMergedAnnotation(it, DbReader::class.java) }
            ?: targetClass?.let { AnnotatedElementUtils.findMergedAnnotation(it, DbReader::class.java) }

        val routeAnnotation = method?.let { AnnotatedElementUtils.findMergedAnnotation(it, DbRouteContext::class.java) }
            ?: targetClass?.let { AnnotatedElementUtils.findMergedAnnotation(it, DbRouteContext::class.java) }

        val txAnnotation = method?.let { AnnotatedElementUtils.findMergedAnnotation(it, Transactional::class.java) }
            ?: targetClass?.let { AnnotatedElementUtils.findMergedAnnotation(it, Transactional::class.java) }

        val activeContext = DbContextHolder.getActiveContext()
        val isInsideWriterTx = activeContext != null && (!activeContext.readerEligible || activeContext.kind == DbOperationKind.COMMAND)

        val rawContext: DbExecutionContext? = when {
            dbWriterAnnotation != null -> {
                val opName = resolveOperationName(dbWriterAnnotation.operation, method, targetClass)
                DbExecutionContext(
                    operationName = opName,
                    kind = DbOperationKind.COMMAND,
                    consistency = ReadConsistency.STRONG,
                    readerEligible = false,
                    targetReader = null
                )
            }
            dbReaderAnnotation != null -> {
                val opName = resolveOperationName(dbReaderAnnotation.operation, method, targetClass)
                DbExecutionContext(
                    operationName = opName,
                    kind = DbOperationKind.QUERY,
                    consistency = dbReaderAnnotation.consistency,
                    readerEligible = true,
                    targetReader = dbReaderAnnotation.target.takeIf { it.isNotBlank() }
                )
            }
            routeAnnotation != null -> {
                val opName = resolveOperationName(routeAnnotation.operation, method, targetClass)
                DbExecutionContext(
                    operationName = opName,
                    kind = routeAnnotation.kind,
                    consistency = routeAnnotation.consistency,
                    readerEligible = routeAnnotation.readerEligible,
                    targetReader = routeAnnotation.targetReader.takeIf { it.isNotBlank() }
                )
            }
            txAnnotation != null -> {
                val opName = resolveOperationName("", method, targetClass)
                if (txAnnotation.readOnly) {
                    DbExecutionContext(
                        operationName = opName,
                        kind = DbOperationKind.QUERY,
                        consistency = ReadConsistency.EVENTUAL,
                        readerEligible = true,
                        targetReader = null
                    )
                } else {
                    DbExecutionContext(
                        operationName = opName,
                        kind = DbOperationKind.COMMAND,
                        consistency = ReadConsistency.STRONG,
                        readerEligible = false,
                        targetReader = null
                    )
                }
            }
            else -> null
        }

        val executionContext = if (rawContext != null) {
            if (isInsideWriterTx) {
                // Invariant: Operations inside an active writer transaction must remain on the writer.
                rawContext.copy(
                    kind = DbOperationKind.COMMAND,
                    readerEligible = false,
                    targetReader = null
                )
            } else {
                // Inherit outer targetReader if not explicitly specified by child
                if (rawContext.targetReader == null && activeContext?.targetReader != null) {
                    rawContext.copy(targetReader = activeContext.targetReader)
                } else {
                    rawContext
                }
            }
        } else null

        return if (executionContext != null) {
            DbContextHolder.withContext(executionContext) {
                joinPoint.proceed()
            }
        } else {
            joinPoint.proceed()
        }
    }

    private fun resolveOperationName(declared: String, method: Method?, targetClass: Class<*>?): String {
        if (declared.isNotBlank()) return declared
        val rawClass = (targetClass?.simpleName ?: "unclassified")
            .lowercase()
            .removeSuffix("controller")
            .removeSuffix("service")
            .removeSuffix("impl")
            .ifBlank { "db" }
        val rawMethod = (method?.name ?: "query").lowercase()
        val sanitized = "$rawClass.$rawMethod".replace(Regex("[^a-z0-9._-]"), ".")
        return if (sanitized.matches(Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*"))) sanitized else "db.query"
    }
}
