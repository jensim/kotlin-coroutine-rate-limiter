package se.jensim.kotlin.experiment.throughput

import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test

@OptIn(ExperimentalTime::class)
class IntervalLimiterTest {

    @Test
    fun run_for_one_second(): Unit = runBlocking {
        listOf(1, 10, 100, 1000, 1200, 1990, 100_000).map { eventsPerInterval ->
            launch {
                val intervalLimiter: IntervalLimiter = intervalLimiter(eventsPerInterval, Duration.seconds(1))
                val start = System.currentTimeMillis()
                (0..eventsPerInterval).forEach {
                    intervalLimiter.acquire()
                }
                val end = System.currentTimeMillis()
                val diff = end - start
                assertTrue(
                    diff > 990,
                    "Suspended for too short time. Only $diff ms has passed and 1000 is expected. Ran at a rate of $eventsPerInterval events per second."
                )
                assertTrue(
                    diff < 1200,
                    "Suspended for too long time. $diff ms has passed and at most 1200 is expected. Ran at a rate of $eventsPerInterval events per second."
                )
            }
        }
    }
}
