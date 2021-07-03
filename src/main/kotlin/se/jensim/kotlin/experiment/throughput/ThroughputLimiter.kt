package se.jensim.kotlin.experiment.throughput

import java.lang.IllegalArgumentException
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import se.jensim.kotlin.experiment.time.LongTimeMark
import se.jensim.kotlin.experiment.time.LongTimeSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
public fun intervalLimiter(eventsPerInterval: Int, interval: Duration): IntervalLimiter =
    IntervalLimiterImpl(eventsPerInterval, interval)

@OptIn(ExperimentalTime::class)
internal class IntervalLimiterImpl(
    eventsPerInterval: Int,
    interval: Duration,
    val timeSource: () -> LongTimeMark = { LongTimeSource.markNow() },
    val delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }
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
    private var intervalStartCursor: LongTimeMark = timeSource()

    @Volatile
    private var cursor: LongTimeMark = intervalStartCursor + eventSegment

    @Volatile
    private var intervalEndCursor: LongTimeMark = intervalStartCursor + _interval

    override suspend fun acquire(): Long = acquire(permits = 1)
    override suspend fun acquire(permits: Int): Long {
        if (permits < 0) throw IllegalArgumentException("You need to ask for at least zero permits")

        val now: LongTimeMark = timeSource()
        val permitDuration = if (permits == 1) eventSegment else eventSegment.times(permits)

        val wakeUpTime: LongTimeMark = mutex.withLock {
            getWakeUpTime(now, permitDuration)
        }
        val sleep: Duration = (wakeUpTime.minus(now))
        val sleepMillis = sleep.inWholeMilliseconds
        delay(sleepMillis)
        return sleepMillis
    }

    override suspend fun tryAcquire(): Boolean = tryAcquireInternal()
    override suspend fun tryAcquire(permits: Int): Boolean = tryAcquireInternal(permits = permits)
    override suspend fun tryAcquire(permits: Int, timeout: Duration): Boolean =
        tryAcquireInternal(permits = permits, timeout = timeout)

    override suspend fun tryAcquire(timeout: Duration): Boolean = tryAcquireInternal(timeout = timeout)
    private suspend fun tryAcquireInternal(permits: Int = 1, timeout: Duration? = null): Boolean {
        if (permits < 0) throw IllegalArgumentException("You need to ask for at least zero permits")
        val now: LongTimeMark = timeSource()

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
        return if (intervalEndCursor < now) {
            // Active interval is in the past
            // Align start of interval with current point in time
            intervalStartCursor = now
            intervalEndCursor = now + _interval
            cursor = intervalStartCursor + permitDuration
            // No delay
            // println("Settign up")
            now
        } else if (cursor > intervalEndCursor) {
            // Cursor has moved into new interval
            // Move cursors to match new interval
            val cursorDiff = cursor - intervalEndCursor
            var intervalSteps:Int = (cursorDiff.inWholeNanoseconds / _interval.inWholeNanoseconds).toInt()
            if(cursorDiff.inWholeNanoseconds % _interval.inWholeNanoseconds > 9) intervalSteps++
            val displacement = _interval.times(intervalSteps)

            intervalEndCursor += displacement
            intervalStartCursor += displacement
            cursor += permitDuration
            // println("Cursor beyond interval, moving interval $intervalSteps steps")
            intervalStartCursor
        } else if (intervalStartCursor > now) {
            // Active interval is in the future, and the current permit must be delayed
            cursor += permitDuration
            // println("Cursor in future interval")
            intervalStartCursor
        } else {
            // Now and Cursor are within the active interval
            // Only need to move the Cursor, nothing else
            // No delay
            cursor += permitDuration
            // println("Nothing special - cursor in first interval")
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

@OptIn(ExperimentalTime::class)
public fun rateLimiter(eventsPerInterval: Int, interval:Duration): RateLimiter = RateLimiterImpl(eventsPerInterval,interval)

@OptIn(ExperimentalTime::class)
internal class RateLimiterImpl(
    eventsPerInterval: Int,
    interval: Duration,
    val timeSource: () -> LongTimeMark = { LongTimeSource.markNow() },
    val delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }
) : RateLimiter {

    private val mutex = Mutex()
    private val _interval = Duration.nanoseconds(interval.inWholeNanoseconds)
    private val permitDuration = _interval.div(eventsPerInterval)

    @Volatile
    private var cursor: LongTimeMark = timeSource()

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

    override suspend fun acquire(): Long {
        return acquireDelay(permitDuration)
    }

    override suspend fun acquire(permits: Int): Long {
        return acquireDelay(if(permits == 1) permitDuration else permitDuration.times(permits))
    }

    private suspend fun acquireDelay(permitDuration: Duration): Long {
        val now: LongTimeMark = timeSource()
        val wakeUpTime: LongTimeMark = mutex.withLock {
            val base = if (cursor > now) cursor else now
            cursor = base + permitDuration
            base
        }
        val delayInMillis = (wakeUpTime - now).inWholeMilliseconds
        delay(delayInMillis)
        return delayInMillis
    }
}
