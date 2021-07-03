package se.jensim.kotlin.experiment.time

internal class TestLongTimeMark(nanos:Long): LongTimeMark(nanos)
internal class TestLongTimeSource(var nanos:Long = 0L) : LongTimeSource {
    override fun markNow(): LongTimeMark = TestLongTimeMark(nanos)
}
