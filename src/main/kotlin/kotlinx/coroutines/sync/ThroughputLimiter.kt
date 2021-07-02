package kotlinx.coroutines.sync

import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.time.compareTo
import kotlinx.coroutines.delay

@OptIn(ExperimentalTime::class)
public interface IntervalLimiter {
    public suspend fun acquire(): Long
    public suspend fun acquire(permits: Int): Long
    public suspend fun tryAcquire():Boolean
    public suspend fun tryAcquire(permits: Int):Boolean
    public suspend fun tryAcquire(permits: Int, timeout:Duration):Boolean
    public suspend fun tryAcquire(timeout:Duration):Boolean
}

@OptIn(ExperimentalTime::class)
public fun intervalLimiter(eventsPerInterval: Double, interval: Duration):IntervalLimiter = IntervalLimiterImpl(eventsPerInterval, interval)

@OptIn(ExperimentalTime::class)
internal class IntervalLimiterImpl(
    eventsPerInterval: Double,
    interval: Duration
) : IntervalLimiter {

    private val _interval = Duration.nanoseconds(interval.inWholeNanoseconds)

    private val mutex = Mutex()

    @Volatile
    private var cursor: TimeMark = TimeSource.Monotonic.markNow()
    @Volatile
    private var intervalStartCursor: TimeMark = cursor
    @Volatile
    private var intervalEndCursor: TimeMark = cursor.plus(_interval)
    private val eventSegment = _interval.div(eventsPerInterval)

    override suspend fun acquire(): Long = acquire(permits = 1)
    override suspend fun acquire(permits: Int): Long {
        if (intervalEndCursor.hasNotPassedNow()){
            // Time is moving faster than buffer of events delayed
            // TODO Set up first interval on now
            TODO()
        }
        if (cursor > intervalEndCursor){
            // Cursor has moved into new interval
            // Move cursors to match new interval
            TODO()
        }
        if (intervalStartCursor.hasPassedNow()){
            // Active interval is in the future, and the current permit must be delayed
            TODO()
        }
        TODO()
    }
    override suspend fun tryAcquire(): Boolean = tryAcquireInternal()
    override suspend fun tryAcquire(permits: Int): Boolean = tryAcquireInternal(permits = permits)
    override suspend fun tryAcquire(permits: Int, timeout: Duration): Boolean = tryAcquireInternal(permits = permits, timeout = timeout)
    override suspend fun tryAcquire(timeout: Duration): Boolean = tryAcquireInternal(timeout = timeout)
    private suspend fun tryAcquireInternal(permits: Int = 1, timeout: Duration? = null): Boolean = TODO("not implemented")
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

public fun rateLimiter(eventsPerSecond: Double):RateLimiter = RateLimiterImpl(eventsPerSecond)

private const val MAX_ALLOWED:Double = 1_000_000_000.0
private const val MIN_ALLOWED:Double = 0.0000001

internal class RateLimiterImpl(eventsPerSecond: Double) : RateLimiter {

    private val mutex = Mutex()
    private var next = 0L
    private val delayInNanos: Long = (1_000_000_000L / eventsPerSecond).toLong()

    init {
        require(eventsPerSecond > MIN_ALLOWED) {
            "eventsPerSecond must be a positive number"
        }
        require(MAX_ALLOWED > eventsPerSecond){
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
            if (sleep > 0){
                delay(sleep)
            }
            sleep
        } else {
            0L
        }
    }
}
