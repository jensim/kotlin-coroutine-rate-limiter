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
class IntervalLimiterTest(
    private val eventsPerInterval: Int
) {

    companion object {

        @JvmStatic
        @Parameters(name="{0} events per interval")
        fun data(): Collection<Array<Any>> = listOf(1, 3, 10, 100, 1000).map { arrayOf(it) }
    }

    @Test
    fun run_for_several_intervals(): Unit = runBlocking {
        val timeSource = TestLongTimeSource()
        val delayer = Delayer()
        val intervalLimiter: IntervalLimiter = IntervalLimiterImpl(
            eventsPerInterval = eventsPerInterval,
            interval = Duration.seconds(1),
            timeSource = timeSource::markNow,
            delay = delayer::delay
        )
        val laps = 10
        var pokes = 0
        (0 until eventsPerInterval* laps).forEach { idx ->
            pokes++
            delayer.reset()
            intervalLimiter.acquire()
            val delay:Long = (idx / eventsPerInterval) * 1000L
            assertEquals(delay, delayer.getDelay(), "Permit #${idx} for $eventsPerInterval events/interval should be delayed $delay ms")
        }
        assertEquals(eventsPerInterval* laps, pokes, "The test is wrong, wrong number of iterations")
    }
}
