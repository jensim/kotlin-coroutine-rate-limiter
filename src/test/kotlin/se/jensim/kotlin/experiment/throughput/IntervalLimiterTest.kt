package se.jensim.kotlin.experiment.throughput

import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import se.jensim.kotlin.experiment.time.Delayer
import se.jensim.kotlin.experiment.time.TestLongTimeSource

@RunWith(Parameterized::class)
@OptIn(ExperimentalTime::class)
class IntervalLimiterTest(
    private val eventsPerInterval: Int
) {

    companion object {

        @JvmStatic
        @Parameterized.Parameters
        fun data(): Collection<Array<Any>> = listOf(1, 3, 10, 100, 1000).map { arrayOf(it) }
    }

    @Test
    fun run_for_one_second(): Unit = runBlocking {
        val timeSource = TestLongTimeSource()
        val delayer = Delayer()
        val intervalLimiter: IntervalLimiter = IntervalLimiterImpl(
            eventsPerInterval = eventsPerInterval,
            interval = Duration.seconds(1),
            timeSource = timeSource::markNow,
            delay = delayer::delay
        )
        (1..eventsPerInterval).forEach {
            intervalLimiter.acquire()
            assertEquals(0, delayer.getDelay(), "Permit #$it for $eventsPerInterval events should not be delayed")
        }
        intervalLimiter.acquire()
        assertEquals(1000, delayer.getDelay(),"Last permit (${eventsPerInterval+1}) should be delayed")
    }
}
