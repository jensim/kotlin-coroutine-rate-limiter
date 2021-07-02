package kotlinx.coroutines.sync

import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test

@OptIn(ExperimentalTime::class)
internal class IntervalLimiterTest {

    @Test
    @Ignore
    fun `run for one second`(): Unit = runBlocking {
        listOf(0.9, 1.0, 10.0, 100.0, 1000.0, 1200.0, 1990.0, 100_000.0).map { eventsPerSec ->
            launch {
                val rateLimiter = intervalLimiter(eventsPerSec, Duration.seconds(1))
                val start = System.currentTimeMillis()
                (0..(eventsPerSec + 0.2).toInt()).forEach {
                    rateLimiter.acquire()
                }
                val end = System.currentTimeMillis()
                val diff = end - start
                assertTrue(
                    diff > 990,
                    "Suspended for too short time. Only $diff ms has passed and 1000 is expected. Ran at a rate of $eventsPerSec events per second."
                )
                assertTrue(
                    diff < 1200,
                    "Suspended for too long time. $diff ms has passed and at most 1200 is expected. Ran at a rate of $eventsPerSec events per second."
                )
            }
        }
    }
}
