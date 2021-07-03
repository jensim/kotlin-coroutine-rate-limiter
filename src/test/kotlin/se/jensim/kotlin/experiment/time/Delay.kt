package se.jensim.kotlin.experiment.time

import java.util.concurrent.atomic.AtomicLong

internal class Delayer {
    private val delayCounter = AtomicLong(0)
    fun delay(duration: Long){
        delayCounter.addAndGet(duration)
    }
    public fun getDelay():Long = delayCounter.get()
    public fun reset():Unit = delayCounter.set(0)
}
