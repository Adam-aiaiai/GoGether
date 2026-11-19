package com.gogether.app.core.data

/** Serializes local C eligibility changes and E check-in/feedback writes. Not a server transaction. */
object LocalDataLock { val monitor = Any() }
