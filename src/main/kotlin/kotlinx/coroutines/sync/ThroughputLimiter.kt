package kotlinx.coroutines.sync

import kotlin.math.max
import kotlinx.coroutines.delay

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
