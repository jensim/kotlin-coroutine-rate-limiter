When a permit has been requested, and the cursor moves beyond the nextCursor (into the next interval).
Then we need to delay or deny the permit.

If the permit has a timeout - compare with what would be the delay.

If the delay is within the timeout - grant the permit and do the actions for
* Moving cursor
* Setting up a new interval
