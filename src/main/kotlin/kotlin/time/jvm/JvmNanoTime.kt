package kotlin.time.jvm

import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
public class LongTimeMark(val nanos: Long) : TimeMark() {
    override fun elapsedNow(): Duration = LongTimeSource.markNow() - this
    override fun plus(duration: Duration): LongTimeMark = LongTimeMark(nanos + duration.inWholeNanoseconds)
}

@OptIn(ExperimentalTime::class)
public object LongTimeSource : TimeSource {
    override fun markNow(): LongTimeMark {
        return LongTimeMark(System.nanoTime())
    }
}

@OptIn(ExperimentalTime::class)
public operator fun LongTimeMark.minus(other: LongTimeMark): Duration = Duration.nanoseconds(nanos - other.nanos)
public operator fun LongTimeMark.compareTo(other: LongTimeMark): Int = nanos.compareTo(other.nanos)

