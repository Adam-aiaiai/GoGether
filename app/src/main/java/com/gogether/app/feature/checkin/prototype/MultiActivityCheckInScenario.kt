package com.gogether.app.feature.checkin.prototype

/** Reusable E integration fixtures. Explicitly injected; never seeded into B/C's real data. */
class MultiActivityCheckInScenario(startAt: Long) {
    val aliceFirst = PrototypeSession("759190b8-4368-4cb9-9069-f13012941df1", MeetingPoint(31.27, 120.74), startAt, startAt + 7_200_000)
    val aliceSecond = PrototypeSession("759190b8-4368-4cb9-9069-f13012941df2", MeetingPoint(31.28, 120.74), startAt, startAt + 3_600_000)
    val carolEvent = PrototypeSession("759190b8-4368-4cb9-9069-f13012941df3", MeetingPoint(31.29, 120.74), startAt, startAt + 5_400_000)
    val sessions = listOf(aliceFirst, aliceSecond, carolEvent)
    val activities = MockActivityCatalog(listOf(
        CheckInActivityInfo(aliceFirst, "alice", "Badminton Meetup", "Sports Hall East Entrance"),
        CheckInActivityInfo(aliceSecond, "alice", "Board Game Evening", "Student Activity Room", "Learn the rules together, then split into groups to play."),
        CheckInActivityInfo(carolEvent, "carol", "Easy Jog", "Sports Field Entrance", "Meet up, warm up and jog together at your own pace."),
    ))
    private fun people(vararg ids: String) = ids.map { id -> prototypeParticipants.single { it.userId == id } }
    val registrations = MockActivityRegistrations(mapOf(
        aliceFirst.id to people("alice", "bob", "carol"),
        aliceSecond.id to people("alice", "david"),
        carolEvent.id to people("carol", "bob", "david"),
    ), mapOf(aliceSecond.id to setOf("bob"), aliceFirst.id to setOf("emma")))

    fun seed(store: PrototypeStore) {
        sessions.forEach { session ->
            store.rememberActivityInfo(activities.activity(session))
            store.rememberRegistrations(session.id, CheckInRegistrationSnapshot(registrations.participants(session.id),
                listOf("alice", "bob", "carol", "david", "emma").filter {
                    registrations.status(session.id, it) == CheckInRegistration.WAITLISTED
                }.toSet()))
            registrations.participants(session.id).forEach { store.rememberSession(it.userId, session) }
        }
    }
}
