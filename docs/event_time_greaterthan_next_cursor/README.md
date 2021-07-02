# Event time greater than next cursor

When a permit has been requested, and the clock has passed the interval end without moving the nextCursor to future interval, this means we have not reached our limits. We will then set up the interval at the current point in time and the nextCursor at the end of this interval.
