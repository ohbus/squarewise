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
import org.springframework.core.annotation.AnnotationUtils
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
        // 1. Direct method-level annotations (highest precedence)
        val methodWriter = findMethodAnnotation(method, targetClass, DbWriter::class.java)
        if (methodWriter != null) {
            val opName = resolveOperationName(methodWriter.operation, method, targetClass)
            return DbExecutionContext(
                operationName = opName,
                kind = DbOperationKind.COMMAND,
                consistency = ReadConsistency.STRONG,
                readerEligible = false,
                targetReader = null,
                targetWriter = methodWriter.target.takeIf { it.isNotBlank() }
            )
        }

        val methodReader = findMethodAnnotation(method, targetClass, DbReader::class.java)
        if (methodReader != null) {
            val opName = resolveOperationName(methodReader.operation, method, targetClass)
            return DbExecutionContext(
                operationName = opName,
                kind = DbOperationKind.QUERY,
                consistency = methodReader.consistency,
                readerEligible = true,
                targetReader = methodReader.target.takeIf { it.isNotBlank() }
            )
        }

        val methodRoute = findMethodAnnotation(method, targetClass, DbRouteContext::class.java)
        if (methodRoute != null) {
            val opName = resolveOperationName(methodRoute.operation, method, targetClass)
            return DbExecutionContext(
                operationName = opName,
                kind = methodRoute.kind,
                consistency = methodRoute.consistency,
                readerEligible = methodRoute.readerEligible,
                targetReader = methodRoute.targetReader.takeIf { it.isNotBlank() },
                targetWriter = methodRoute.targetWriter.takeIf { it.isNotBlank() }
            )
        }

        val methodTx = findMethodAnnotation(method, targetClass, Transactional::class.java)
        if (methodTx != null && !methodTx.readOnly) {
            return resolveTxContext(methodTx, method, targetClass)
        }

        // 2. Class and declaring interface hierarchy annotations
        val dbWriterAnnotation = findClassAnnotation(method, targetClass, DbWriter::class.java)
        if (dbWriterAnnotation != null) {
            val opName = resolveOperationName(dbWriterAnnotation.operation, method, targetClass)
            return DbExecutionContext(
                operationName = opName,
                kind = DbOperationKind.COMMAND,
                consistency = ReadConsistency.STRONG,
                readerEligible = false,
                targetReader = null,
                targetWriter = dbWriterAnnotation.target.takeIf { it.isNotBlank() }
            )
        }

        val dbReaderAnnotation = findClassAnnotation(method, targetClass, DbReader::class.java)
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

        val routeAnnotation = findClassAnnotation(method, targetClass, DbRouteContext::class.java)
        if (routeAnnotation != null) {
            val opName = resolveOperationName(routeAnnotation.operation, method, targetClass)
            return DbExecutionContext(
                operationName = opName,
                kind = routeAnnotation.kind,
                consistency = routeAnnotation.consistency,
                readerEligible = routeAnnotation.readerEligible,
                targetReader = routeAnnotation.targetReader.takeIf { it.isNotBlank() },
                targetWriter = routeAnnotation.targetWriter.takeIf { it.isNotBlank() }
            )
        }

        val txAnnotation = findClassAnnotation(method, targetClass, Transactional::class.java)
        if (txAnnotation != null) {
            return resolveTxContext(txAnnotation, method, targetClass)
        }

        return null
    }

    private fun resolveTxContext(tx: Transactional, method: Method?, targetClass: Class<*>?): DbExecutionContext? {
        if (tx.readOnly) {
            return null
        }
        val opName = resolveOperationName("", method, targetClass)
        return DbExecutionContext(
            operationName = opName,
            kind = DbOperationKind.COMMAND,
            consistency = ReadConsistency.STRONG,
            readerEligible = false,
            targetReader = null
        )
    }

    private fun applyContextInheritance(
        rawContext: DbExecutionContext?,
        activeContext: DbExecutionContext?
    ): DbExecutionContext? {
        if (rawContext == null) return null
        val isInsideWriterTx = activeContext?.isWriterOnly() == true

        if (isInsideWriterTx) {
            return rawContext.copy(
                kind = DbOperationKind.COMMAND,
                readerEligible = false,
                targetReader = null,
                targetWriter = rawContext.targetWriter ?: activeContext.targetWriter
            )
        }
        if (rawContext.targetWriter == null && activeContext?.targetWriter != null) {
            return rawContext.copy(targetWriter = activeContext.targetWriter)
        }
        if (rawContext.targetReader == null && activeContext?.targetReader != null) {
            return rawContext.copy(targetReader = activeContext.targetReader)
        }
        return rawContext
    }

    private fun <A : Annotation> findMethodAnnotation(
        method: Method?,
        targetClass: Class<*>?,
        annotationClass: Class<A>
    ): A? {
        if (method == null) return null
        val direct = AnnotationUtils.findAnnotation(method, annotationClass)
        if (direct != null) return direct

        val classesToInspect = listOfNotNull(targetClass, method.declaringClass).distinct()
        for (rootClass in classesToInspect) {
            val fromClass = searchMethodInHierarchy(rootClass, method, annotationClass)
            if (fromClass != null) return fromClass
        }
        return null
    }

    private fun <A : Annotation> searchMethodInHierarchy(
        rootClass: Class<*>,
        method: Method,
        annotationClass: Class<A>
    ): A? {
        val visited = mutableSetOf<Class<*>>()
        val queue = ArrayDeque<Class<*>>()
        var currentClass: Class<*>? = rootClass
        while (currentClass != null && currentClass != Any::class.java) {
            try {
                val declaredMethod = currentClass.getDeclaredMethod(method.name, *method.parameterTypes)
                val annotation = AnnotationUtils.findAnnotation(declaredMethod, annotationClass)
                if (annotation != null) return annotation
            } catch (_: NoSuchMethodException) {
                // Method not declared on this class; continue hierarchy traversal
            }
            queue.addAll(currentClass.interfaces)
            currentClass = currentClass.superclass
        }
        while (queue.isNotEmpty()) {
            val iface = queue.removeFirst()
            if (visited.add(iface)) {
                try {
                    val ifaceMethod = iface.getMethod(method.name, *method.parameterTypes)
                    val annotation = AnnotationUtils.findAnnotation(ifaceMethod, annotationClass)
                    if (annotation != null) return annotation
                } catch (_: NoSuchMethodException) {
                    // Method not declared on this interface; continue hierarchy traversal
                }
                queue.addAll(iface.interfaces)
            }
        }
        return null
    }

    private fun <A : Annotation> findClassAnnotation(
        method: Method?,
        targetClass: Class<*>?,
        annotationClass: Class<A>
    ): A? {
        val classesToInspect = listOfNotNull(targetClass, method?.declaringClass).distinct()
        for (rootClass in classesToInspect) {
            val fromClass = searchClassInHierarchy(rootClass, method, annotationClass)
            if (fromClass != null) return fromClass
        }
        return null
    }

    private fun <A : Annotation> searchClassInHierarchy(
        rootClass: Class<*>,
        method: Method?,
        annotationClass: Class<A>
    ): A? {
        val queue = ArrayDeque<Class<*>>()
        val annotation = findClassAnnotationOnSuperclasses(rootClass, annotationClass, queue)
        if (annotation != null) return annotation
        return searchClassAnnotationOnInterfaces(queue, method, annotationClass)
    }

    private fun <A : Annotation> findClassAnnotationOnSuperclasses(
        rootClass: Class<*>,
        annotationClass: Class<A>,
        interfaceQueue: ArrayDeque<Class<*>>
    ): A? {
        var currentClass: Class<*>? = rootClass
        while (currentClass != null && currentClass != Any::class.java) {
            val classAnnotation = currentClass.getAnnotation(annotationClass)
            if (classAnnotation != null) return classAnnotation
            interfaceQueue.addAll(currentClass.interfaces)
            currentClass = currentClass.superclass
        }
        return null
    }

    private fun <A : Annotation> searchClassAnnotationOnInterfaces(
        queue: ArrayDeque<Class<*>>,
        method: Method?,
        annotationClass: Class<A>
    ): A? {
        val visited = mutableSetOf<Class<*>>()
        while (queue.isNotEmpty()) {
            val iface = queue.removeFirst()
            if (visited.add(iface)) {
                if (interfaceDeclaresMethod(iface, method)) {
                    val ifaceAnnotation = iface.getAnnotation(annotationClass)
                    if (ifaceAnnotation != null) return ifaceAnnotation
                }
                queue.addAll(iface.interfaces)
            }
        }
        return null
    }

    private fun interfaceDeclaresMethod(iface: Class<*>, method: Method?): Boolean {
        if (method == null) return true
        return try {
            iface.getMethod(method.name, *method.parameterTypes)
            true
        } catch (_: NoSuchMethodException) {
            // Method not declared on this interface
            false
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
