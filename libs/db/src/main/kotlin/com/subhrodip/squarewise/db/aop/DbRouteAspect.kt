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
import java.lang.reflect.Method

/**
 * Aspect that intercepts [@DbRouteContext] and [@Transactional] methods/classes
 * to establish thread-bound [DbExecutionContext] prior to database connection acquisition.
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
        "@annotation(org.springframework.transaction.annotation.Transactional) || " +
        "@within(org.springframework.transaction.annotation.Transactional)"
    )
    fun aroundRoutedMethod(joinPoint: ProceedingJoinPoint): Any? {
        val signature = joinPoint.signature as? MethodSignature
        val method = signature?.method
        val targetClass = joinPoint.target?.javaClass ?: method?.declaringClass

        val routeAnnotation = method?.getAnnotation(DbRouteContext::class.java)
            ?: targetClass?.getAnnotation(DbRouteContext::class.java)

        val txAnnotation = method?.getAnnotation(Transactional::class.java)
            ?: targetClass?.getAnnotation(Transactional::class.java)

        val executionContext = when {
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
                        readerEligible = true
                    )
                } else {
                    DbExecutionContext(
                        operationName = opName,
                        kind = DbOperationKind.COMMAND,
                        consistency = ReadConsistency.STRONG,
                        readerEligible = false
                    )
                }
            }
            else -> null
        }

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
