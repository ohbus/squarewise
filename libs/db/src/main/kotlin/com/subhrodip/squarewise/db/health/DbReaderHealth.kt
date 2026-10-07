package com.subhrodip.squarewise.db.health

import com.subhrodip.squarewise.db.errors.DbPlatformException
import com.subhrodip.squarewise.errors.catalog.PlatformErrors
import com.subhrodip.squarewise.db.routing.DbExecutionContext
import com.subhrodip.squarewise.db.routing.ReadConsistency
import com.subhrodip.squarewise.db.routing.DbWatermark
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** A bounded, in-memory health policy for one named reader pool. */
class DbReaderHealth(
    private val failureThreshold: Int = 3,
    private val openDuration: Duration = Duration.ofSeconds(10),
    private val clock: Clock = Clock.systemUTC()
) {
    private data class Entry(
        var state: DbReaderState = DbReaderState.HEALTHY,
        var failures: AtomicInteger = AtomicInteger(0),
        var openedAt: Instant? = null,
        var replayedWatermark: DbWatermark? = null
    )

    private val readers = ConcurrentHashMap<String, Entry>()

    init {
        if (failureThreshold <= 0) {
            throw DbPlatformException(PlatformErrors.PLATFORM_CONFIGURATION_INVALID, "failureThreshold must be positive")
        }
        if (openDuration.isNegative || openDuration.isZero) {
            throw DbPlatformException(PlatformErrors.PLATFORM_CONFIGURATION_INVALID, "openDuration must be positive")
        }
    }

    /** Registers a reader as healthy and makes it eligible for eventual reads. */
    fun register(readerName: String) {
        readers.putIfAbsent(readerName, Entry())
    }

    /** Records a successful probe or query and closes an open circuit. */
    fun markHealthy(readerName: String) {
        markHealthy(readerName, null)
    }

    /** Records a successful probe and the reader's latest replayed causal position. */
    fun markHealthy(readerName: String, replayedWatermark: DbWatermark?) {
        val entry = readers.computeIfAbsent(readerName) { Entry() }
        synchronized(entry) {
            entry.state = DbReaderState.HEALTHY
            entry.failures.set(0)
            entry.openedAt = null
            if (replayedWatermark != null) entry.replayedWatermark = replayedWatermark
        }
    }

    /** Records replay lag without opening the circuit; affected reads fail closed until caught up. */
    fun markLagging(readerName: String) {
        val entry = readers.computeIfAbsent(readerName) { Entry() }
        synchronized(entry) { if (entry.state != DbReaderState.OPEN) entry.state = DbReaderState.LAGGING }
    }

    /** Records a failed reader operation and opens the circuit after the configured threshold. */
    fun markFailure(readerName: String) {
        val entry = readers.computeIfAbsent(readerName) { Entry() }
        synchronized(entry) {
            if (entry.state == DbReaderState.OPEN) return
            if (entry.failures.incrementAndGet() >= failureThreshold) {
                entry.state = DbReaderState.OPEN
                entry.openedAt = Instant.now(clock)
            } else {
                entry.state = DbReaderState.DISCONNECTED
            }
        }
    }

    /** Marks a reader disconnected after a failed health probe. */
    fun markDisconnected(readerName: String) {
        val entry = readers.computeIfAbsent(readerName) { Entry() }
        synchronized(entry) {
            entry.state = DbReaderState.DISCONNECTED
            entry.failures.set(0)
        }
    }

    /** Returns the current state, transitioning an elapsed open circuit to disconnected for probing. */
    fun state(readerName: String): DbReaderState {
        val entry = readers[readerName] ?: return DbReaderState.DISCONNECTED
        synchronized(entry) {
            if (entry.state == DbReaderState.OPEN && entry.openedAt?.plus(openDuration)?.isBefore(Instant.now(clock)) == true) {
                entry.state = DbReaderState.DISCONNECTED
            }
            return entry.state
        }
    }

    /** Decides whether routing may use the reader or must use the writer/fail. */
    fun route(context: DbExecutionContext, readerName: String): DbReaderDecision {
        if (context.isWriterOnly()) return DbReaderDecision.Writer
        val required = context.requiredDbWatermark()
        val replayed = readers[readerName]?.replayedWatermark
        if (required != null && (replayed == null || replayed < required)) {
            return DbReaderDecision.Fail
        }
        return when (state(readerName)) {
            DbReaderState.HEALTHY -> DbReaderDecision.Reader
            DbReaderState.LAGGING, DbReaderState.OPEN, DbReaderState.DISCONNECTED -> DbReaderDecision.Fail
        }
    }
}
