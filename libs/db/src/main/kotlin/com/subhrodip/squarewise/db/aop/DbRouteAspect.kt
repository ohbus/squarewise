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

        val rawContext = resolveRawContext(method, targetClass)
        val activeContext = DbContextHolder.getActiveContext()
        val executionContext = applyContextInheritance(rawContext, activeContext)

        return if (executionContext != null) {
            DbContextHolder.withContext(executionContext) {
                joinPoint.proceed()
            }
        } else {
            joinPoint.proceed()
        }
    }

    private fun resolveRawContext(method: Method?, targetClass: Class<*>?): DbExecutionContext? {
        val dbWriterAnnotation = findAnnotation(method, targetClass, DbWriter::class.java)
        if (dbWriterAnnotation != null) {
            val opName = resolveOperationName(dbWriterAnnotation.operation, method, targetClass)
            return DbExecutionContext(
                operationName = opName,
                kind = DbOperationKind.COMMAND,
                consistency = ReadConsistency.STRONG,
                readerEligible = false,
                targetReader = null
            )
        }

        val dbReaderAnnotation = findAnnotation(method, targetClass, DbReader::class.java)
        if (dbReaderAnnotation != null) {
            val opName = resolveOperationName(dbReaderAnnotation.operation, method, targetClass)
            return DbExecutionContext(
                operationName = opName,
                kind = DbOperationKind.QUERY,
                consistency = dbReaderAnnotation.consistency,
                readerEligible = true,
                targetReader = dbReaderAnnotation.target.takeIf { it.isNotBlank() }
            )
        }

        val routeAnnotation = findAnnotation(method, targetClass, DbRouteContext::class.java)
        if (routeAnnotation != null) {
            val opName = resolveOperationName(routeAnnotation.operation, method, targetClass)
            return DbExecutionContext(
                operationName = opName,
                kind = routeAnnotation.kind,
                consistency = routeAnnotation.consistency,
                readerEligible = routeAnnotation.readerEligible,
                targetReader = routeAnnotation.targetReader.takeIf { it.isNotBlank() }
            )
        }

        val txAnnotation = findAnnotation(method, targetClass, Transactional::class.java)
        if (txAnnotation != null) {
            return resolveTxContext(txAnnotation, method, targetClass)
        }

        return null
    }

    private fun resolveTxContext(tx: Transactional, method: Method?, targetClass: Class<*>?): DbExecutionContext {
        val opName = resolveOperationName("", method, targetClass)
        return if (tx.readOnly) {
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

    private fun applyContextInheritance(
        rawContext: DbExecutionContext?,
        activeContext: DbExecutionContext?
    ): DbExecutionContext? {
        if (rawContext == null) return null
        val isInsideWriterTx = activeContext != null &&
            (!activeContext.readerEligible || activeContext.kind == DbOperationKind.COMMAND)

        if (isInsideWriterTx) {
            return rawContext.copy(
                kind = DbOperationKind.COMMAND,
                readerEligible = false,
                targetReader = null
            )
        }
        if (rawContext.targetReader == null && activeContext?.targetReader != null) {
            return rawContext.copy(targetReader = activeContext.targetReader)
        }
        return rawContext
    }

    private fun <A : Annotation> findAnnotation(method: Method?, targetClass: Class<*>?, annotationClass: Class<A>): A? =
        method?.let { AnnotatedElementUtils.findMergedAnnotation(it, annotationClass) }
            ?: targetClass?.let { AnnotatedElementUtils.findMergedAnnotation(it, annotationClass) }

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
