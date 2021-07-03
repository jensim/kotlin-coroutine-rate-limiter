package se.jensim.kotlin.experiment.throughput

import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import se.jensim.kotlin.experiment.time.Delayer
import se.jensim.kotlin.experiment.time.TestLongTimeSource

@RunWith(Parameterized::class)
@OptIn(ExperimentalTime::class)
class RateLimiterTest(private val eventsPerInterval: Int) {

    companion object {

        @JvmStatic
        @Parameters(name="{0} events per second")
        fun data(): Collection<Array<Any>> = listOf(1, 3, 10, 100, 1000).map { arrayOf(it) }
    }

    @Test
    fun acquire(): Unit = runBlocking {
        val delayer = Delayer()
        val timeSource = TestLongTimeSource()
        val rateLimiter = RateLimiterImpl(
            eventsPerInterval = eventsPerInterval,
            interval = Duration.seconds(1),
            timeSource = timeSource::markNow,
            delay = delayer::delay
        )
        (0..eventsPerInterval).forEach { delayMultiplier ->
            delayer.reset()
            rateLimiter.acquire()
            val delaySum = delayer.getDelay()
            val expectedDelay: Long = (1000L / eventsPerInterval) * delayMultiplier
            assertEquals(
                expectedDelay, delaySum,
                "Failed for eventsPerInterval: $eventsPerInterval round #$delayMultiplier took $delaySum ms"
            )
        }
    }
}
