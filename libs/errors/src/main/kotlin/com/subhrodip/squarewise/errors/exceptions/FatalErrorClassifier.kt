package com.subhrodip.squarewise.errors.exceptions

import java.util.concurrent.CancellationException

/** Classifies JVM and coroutine termination conditions that must never be recovered. */
object FatalErrorClassifier {
    /** Return true for process/thread termination and interruption conditions. */
    fun isFatal(throwable: Throwable): Boolean = when (throwable) {
        is VirtualMachineError,
        is LinkageError,
        is InterruptedException,
        is CancellationException -> true
        else -> throwable.javaClass.name == "java.lang.ThreadDeath" ||
            throwable.javaClass.name == "kotlinx.coroutines.CancellationException"
    }
}
