package com.gogether.app.core.data

import com.gogether.app.core.model.*
import com.gogether.app.feature.registration.RegistrationEngine
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId

object DemoData {
    fun create(now: Long = System.currentTimeMillis()): AppSnapshot {
        val users = listOf(
            User("alice", "Alice", setOf(Category.SPORTS, Category.OUTDOOR), now),
            User("bob", "Bob", setOf(Category.PHOTOGRAPHY, Category.SPORTS), now),
            User("carol", "Carol", setOf(Category.BOARD_GAMES, Category.READING), now),
            User("david", "David", setOf(Category.OUTDOOR, Category.SPORTS), now),
            User("emma", "Emma", setOf(Category.READING, Category.PHOTOGRAPHY), now),
            User("finn", "Finn", setOf(Category.SPORTS, Category.BOARD_GAMES), now),
        )
        val tomorrow = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1)
            .atTime(14, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val titles = listOf("Badminton, anyone?", "Board game night", "Golden hour photo walk", "One more chapter", "Take the scenic route")
        val categories = listOf(Category.SPORTS, Category.BOARD_GAMES, Category.PHOTOGRAPHY, Category.READING, Category.OUTDOOR)
        val locations = listOf("Suzhou Sports Centre · Court 3", "Moonlight Cafe · Upstairs", "Jinji Lake · East gate", "Page One Bookshop · Reading room", "Lingyan Hill · Visitor centre")
        val descriptions = listOf(
            "A friendly doubles session for all levels. Bring a racket, water and your best team spirit.",
            "Learn a new game and meet a few new faces. We will bring the games; you bring the curiosity.",
            "An easy lakeside walk chasing light and small details. Phone cameras are very welcome.",
            "Bring the book you are reading. Share a favourite passage over coffee with a small group.",
            "A relaxed two-hour hike with breaks for the view. Wear comfortable shoes and bring water.",
        )
        val events = titles.indices.map { i ->
            val start = tomorrow + i * 24 * 60 * 60 * 1000L
            Event("event-${i + 1}", if (i == 1) "carol" else "alice", titles[i], categories[i],
                descriptions[i], start, start + RegistrationEngine.TWO_HOURS, locations[i], "Suzhou",
                if (i == 0) 4 else 6, start - 60 * 60 * 1000L,
                "Meet 10 minutes early. Please let the organizer know if your plans change.", now, now)
        }
        var order = 0L
        fun registration(eventId: String, user: String, status: RegistrationStatus) =
            Registration("reg-${eventId}-${user}", eventId, user, status, now, now, ++order)
        val registrations = events.map { registration(it.id, it.organizerId, RegistrationStatus.CONFIRMED) } +
            listOf("bob", "carol", "david").map { registration("event-1", it, RegistrationStatus.CONFIRMED) } +
            registration("event-1", "emma", RegistrationStatus.WAITLISTED)
        val past = events[3].copy(id = "event-history", title = "Last week's reading circle",
            startAt = now - 3 * 24 * 60 * 60 * 1000L, endAt = now - 3 * 24 * 60 * 60 * 1000L + RegistrationEngine.TWO_HOURS,
            registrationDeadline = now - 4 * 24 * 60 * 60 * 1000L, status = EventStatus.COMPLETED)
        return AppSnapshot(users = users, events = events + past,
            registrations = registrations + listOf("alice", "bob", "emma").map {
                registration(past.id, it, RegistrationStatus.CONFIRMED)
            })
    }
}
