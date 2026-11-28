package com.gogether.app.core.model

/** Public aggregate only: no reviewer identities or private scores cross this boundary. */
data class EventRatingSummary(val ratingSum: Int = 0, val reviewCount: Int = 0) {
    init { require(reviewCount >= 0 && ratingSum.toLong() in reviewCount.toLong()..reviewCount.toLong()*5) }
    val average: Double? get() = if(reviewCount == 0) null else ratingSum.toDouble()/reviewCount
    val displayAverage: String? get() = average?.let {
        java.math.BigDecimal.valueOf(it).setScale(1,java.math.RoundingMode.HALF_UP).toPlainString()
    }
}

/** Internal inputs; callers supply current reviews, never drafts, reply ratings or archived predecessors. */
data class RatingReview(val id:String, val eventId:String, val authorId:String, val rating:Int,
    val createdAt:Long, val public:Boolean=true, val registrationKey:String?=null, val submitted:Boolean=true)

fun eventRatingSummary(state:AppSnapshot,event:Event,reviews:List<RatingReview>,deleted:Set<String>,now:Long):EventRatingSummary {
    if(event.statusAt(now)!=EventStatus.COMPLETED) return EventRatingSummary()
    val eligible=reviews.filter {it.eventId==event.id}.sortedWith(compareByDescending<RatingReview> {it.createdAt}.thenBy {it.id})
        .distinctBy {it.authorId}.filter {r ->
            val registration=state.registration(event.id,r.authorId)
            val arrival=state.activeCheckIn(event.id,r.authorId)
            r.public && r.submitted && r.id !in deleted && r.authorId!=event.organizerId && r.rating in 1..5 &&
                registration?.status==RegistrationStatus.CONFIRMED && arrival!=null &&
                arrival.checkedInAt>=registration.joinedAt && r.createdAt>=arrival.checkedInAt && r.createdAt<=now &&
                (event.status==EventStatus.COMPLETED || r.createdAt>=event.endAt) &&
                (r.registrationKey==null || r.registrationKey==registration.attendanceKey)
        }
    return EventRatingSummary(eligible.sumOf {it.rating},eligible.size)
}
