package kotlin.time

@OptIn(ExperimentalTime::class)
internal class LongTimeMark(val nanos: Long) : TimeMark() {
    override fun elapsedNow(): Duration = LongTimeSource.markNow() - this
    override fun plus(duration: Duration): LongTimeMark = LongTimeMark(nanos + duration.inWholeNanoseconds)
    public inline operator fun LongTimeMark.compareTo(other: LongTimeMark): Int = nanos.compareTo(other.nanos)
    public inline operator fun LongTimeMark.minus(other: LongTimeMark): Duration = Duration.nanoseconds(nanos - other.nanos)
}

@OptIn(ExperimentalTime::class)
internal object LongTimeSource : TimeSource {
    override fun markNow(): LongTimeMark {
        return LongTimeMark(System.nanoTime())
    }
}
