package se.jensim.kotlin.experiment.time

import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
public abstract class LongTimeMark(val nanos:Long) : TimeMark() {

    operator fun minus(other: LongTimeMark): Duration = Duration.nanoseconds(nanos - other.nanos)
    override operator fun minus(duration: Duration): LongTimeMark = JvmNanoLongTimeMark(nanos - duration.inWholeNanoseconds)
    override operator fun plus(duration: Duration): LongTimeMark = JvmNanoLongTimeMark(nanos + duration.inWholeNanoseconds)
    override fun elapsedNow(): Duration = JvmNanoLongTimeSource.markNow() - this
    operator fun compareTo(other: LongTimeMark): Int = nanos.compareTo(other.nanos)
}

@OptIn(ExperimentalTime::class)
private class JvmNanoLongTimeMark(nanos: Long) : LongTimeMark(nanos)

@OptIn(ExperimentalTime::class)
public interface LongTimeSource : TimeSource {
    companion object {
        fun markNow(): LongTimeMark = JvmNanoLongTimeSource.markNow()
    }
    override fun markNow(): LongTimeMark
}

@OptIn(ExperimentalTime::class)
private object JvmNanoLongTimeSource : LongTimeSource {
    override fun markNow(): LongTimeMark = JvmNanoLongTimeMark(System.nanoTime())
}
