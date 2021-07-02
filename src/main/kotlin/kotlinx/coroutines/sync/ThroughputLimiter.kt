package kotlinx.coroutines.sync

import java.lang.IllegalArgumentException
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.jvm.LongTimeMark
import kotlin.time.jvm.LongTimeSource
import kotlin.time.jvm.compareTo
import kotlin.time.jvm.minus
import kotlinx.coroutines.delay

@OptIn(ExperimentalTime::class)
public interface IntervalLimiter {
    public suspend fun acquire(): Long
    public suspend fun acquire(permits: Int): Long
    public suspend fun tryAcquire(): Boolean
    public suspend fun tryAcquire(permits: Int): Boolean
    public suspend fun tryAcquire(permits: Int, timeout: Duration): Boolean
    public suspend fun tryAcquire(timeout: Duration): Boolean
}

@OptIn(ExperimentalTime::class)
public fun intervalLimiter(eventsPerInterval: Double, interval: Duration): IntervalLimiter =
    IntervalLimiterImpl(eventsPerInterval, interval)

@OptIn(ExperimentalTime::class)
internal class IntervalLimiterImpl(
    eventsPerInterval: Double,
    interval: Duration
) : IntervalLimiter {

    init {
        require(interval.inWholeMilliseconds > 5) {
            "Interval has to be at least 5 ms. The overhead of having locks and such in place if enough to render this moot."
        }
        require(interval.inWholeDays <= 1){
            "Interval has to be less than 1 day"
        }
        require(interval.inWholeNanoseconds / eventsPerInterval > 1){
            "Interval segment is not allowed to be less than one"
        }
    }

    private val _interval = Duration.nanoseconds(interval.inWholeNanoseconds)
    private val mutex = Mutex()
    private val eventSegment = _interval.div(eventsPerInterval)

    @Volatile
    private var cursor: LongTimeMark = LongTimeSource.markNow()

    @Volatile
    private var intervalStartCursor: LongTimeMark = cursor

    @Volatile
    private var intervalEndCursor: LongTimeMark = cursor.plus(_interval)

    override suspend fun acquire(): Long = acquire(permits = 1)
    override suspend fun acquire(permits: Int): Long {
        if (permits > 0) throw IllegalArgumentException("You need to ask for at least zero permits")

        val now: LongTimeMark = LongTimeSource.markNow()
        val permitDuration = if (permits == 1) eventSegment else eventSegment.times(permits)

        val wakeUpTime: LongTimeMark = mutex.withLock {
            getWakeUpTime(now, permitDuration)
        }
        val sleep: Duration = (wakeUpTime.minus(now))
        val sleepMillis = sleep.inWholeMilliseconds
        return if (sleepMillis > 0) {
            delay(sleepMillis)
            sleepMillis
        } else {
            0L
        }
    }

    override suspend fun tryAcquire(): Boolean = tryAcquireInternal()
    override suspend fun tryAcquire(permits: Int): Boolean = tryAcquireInternal(permits = permits)
    override suspend fun tryAcquire(permits: Int, timeout: Duration): Boolean =
        tryAcquireInternal(permits = permits, timeout = timeout)

    override suspend fun tryAcquire(timeout: Duration): Boolean = tryAcquireInternal(timeout = timeout)
    private suspend fun tryAcquireInternal(permits: Int = 1, timeout: Duration? = null): Boolean {
        if (permits > 0) throw IllegalArgumentException("You need to ask for at least zero permits")
        val now: LongTimeMark = LongTimeSource.markNow()

        val timeoutEnd = if(timeout == null) now else now + timeout
        // Early elimination without waiting for locks
        if (timeoutEnd <= intervalStartCursor) {
            // Start of current interval is in the future
            return false
        }

        val permitDuration = if (permits == 1) eventSegment else eventSegment.times(permits)
        val wakeUpTime: LongTimeMark = mutex.withLock {
            // Late elimination with locks
            // In case things changed while waiting for the lock
            if (timeoutEnd <= intervalStartCursor) {
                return false
            }
            getWakeUpTime(now, permitDuration)
        }
        val sleep: Duration = (wakeUpTime.minus(now))
        val sleepMillis = sleep.inWholeMilliseconds
        if (sleepMillis > 0) {
            delay(sleepMillis)
        }
        return true
    }

    /**
     * Must be run inside the mutex.. This is the Danger Zone.
     */
    private fun getWakeUpTime(now: LongTimeMark, permitDuration: Duration): LongTimeMark {
        return if (intervalEndCursor.hasNotPassedNow()) {
            // Active interval is in the past
            // Align start of interval with current point in time
            intervalStartCursor = now
            intervalEndCursor = now + _interval
            cursor = intervalStartCursor + permitDuration
            // No delay
            now
        } else if (cursor > intervalEndCursor) {
            // Cursor has moved into new interval
            // Move cursors to match new interval
            intervalStartCursor = intervalEndCursor
            intervalEndCursor += _interval
            cursor = intervalStartCursor + permitDuration
            intervalStartCursor
        } else if (intervalStartCursor.hasPassedNow()) {
            // Active interval is in the future, and the current permit must be delayed
            cursor += permitDuration
            intervalStartCursor
        } else {
            // Now and Cursor are within the active interval
            // Only need to move the Cursor, nothing else
            // No delay
            cursor += permitDuration
            now
        }
    }
}

/**
 * Limit throughput of events per second to be at most equal to the argument eventsPerSecond.
 * When the limit is passed, calls are suspended until the calculated point in time when it's
 * okay to pass the rate limiter.
 */
public interface RateLimiter {
    /**
     * Acquires a single permit from this RateLimiter, blocking until the request can be granted. Tells the amount of time slept, if any.
     */
    public suspend fun acquire(): Long

    /**
     * Acquires the given number of permits from this RateLimiter, blocking until the request can be granted.
     */
    public suspend fun acquire(permits: Int): Long
}

public fun rateLimiter(eventsPerSecond: Double): RateLimiter = RateLimiterImpl(eventsPerSecond)

private const val MAX_ALLOWED: Double = 1_000_000_000.0
private const val MIN_ALLOWED: Double = 0.0000001

internal class RateLimiterImpl(eventsPerSecond: Double) : RateLimiter {

    private val mutex = Mutex()
    private var next = 0L
    private val delayInNanos: Long = (1_000_000_000L / eventsPerSecond).toLong()

    init {
        require(eventsPerSecond > MIN_ALLOWED) {
            "eventsPerSecond must be a positive number"
        }
        require(MAX_ALLOWED > eventsPerSecond) {
            "The calculated delay in nanos became too small"
        }
    }

    override suspend fun acquire(): Long {
        return acquireDelay(delayInNanos)
    }

    override suspend fun acquire(permits: Int): Long {
        return acquireDelay(delayInNanos)
    }

    private suspend fun acquireDelay(delayInNanos: Long): Long {
        val now: Long = System.nanoTime()
        val until = mutex.withLock {
            max(next, now).also {
                next = it + delayInNanos
            }
        }
        return if (until != now) {
            val sleep = (until - now) / 1_000_000
            if (sleep > 0) {
                delay(sleep)
            }
            sleep
        } else {
            0L
        }
    }
}
