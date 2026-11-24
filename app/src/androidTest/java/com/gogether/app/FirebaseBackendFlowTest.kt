package com.gogether.app

import android.content.Context
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ApplicationProvider
import com.gogether.app.core.data.cloud.FirebaseGogetherRepository
import com.gogether.app.core.data.cloud.FirebaseRuntime
import com.gogether.app.core.data.cloud.FirebaseServices
import com.gogether.app.core.data.cloud.invokeCallable
import org.json.JSONObject
import org.json.JSONArray
import java.security.MessageDigest
import com.gogether.app.core.model.*
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Real Auth -> HTTP callable -> Firestore flow. Requires -Pbackend=emulator and Emulator Suite. */
class FirebaseBackendFlowTest {
    @Test fun twoClientsShareActivityQuestionsAnnouncementsAndReadState(): Unit = runBlocking {
        assumeTrue("Run with -Pbackend=emulator", BuildConfig.BACKEND_MODE == "emulator")
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (Build.VERSION.SDK_INT >= 37) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, "android.permission.ACCESS_LOCAL_NETWORK")
        val suffix = UUID.randomUUID().toString()
        val aliceServices = FirebaseRuntime.services(context, "android-alice-$suffix")
        val bobServices = FirebaseRuntime.services(context, "android-bob-$suffix")
        val alice = FirebaseGogetherRepository(context, aliceServices)
        val bob = FirebaseGogetherRepository(context, bobServices)
        try {
            val a = alice.signUp("alice-$suffix@example.test", "ExamplePass2026!", "Alice Android", setOf(Category.SPORTS)).getOrThrow()
            val b = bob.signUp("bob-$suffix@example.test", "ExamplePass2026!", "Bob Android", setOf(Category.SPORTS)).getOrThrow()
            val now = System.currentTimeMillis()
            val event = alice.createEvent(EventDraft("Android shared activity", Category.SPORTS, "Created through the real Android cloud repository",
                now + 7_200_000, "Campus sports centre", "Suzhou", 4, now + 3_600_000)).getOrThrow()
            withTimeout(20_000) { while (bob.state.value.event(event.id) == null) delay(200) }
            assertEquals(a.id, bob.state.value.event(event.id)!!.organizerId)
            assertNull(bob.state.value.users.single { it.id == a.id }.email)
            assertEquals(RegistrationStatus.CONFIRMED, bob.join(event.id).getOrThrow().status)
            alice.postAnnouncement(event.id, "Meet at the east entrance").getOrThrow()
            withTimeout(20_000) { while (bob.state.value.notifications.none { it.eventId == event.id && it.type == NotificationType.ANNOUNCEMENT }) delay(200) }
            bob.askQuestion(event.id, "Do I need to bring equipment?").getOrThrow()
            withTimeout(20_000) { while (alice.state.value.questions.none { it.eventId == event.id }) delay(200) }
            val question = alice.state.value.questions.single { it.eventId == event.id }
            alice.answerQuestion(question.id, "Spare equipment is available").getOrThrow()
            withTimeout(20_000) { while (bob.state.value.questions.find { it.id == question.id }?.answer == null) delay(200) }
            bob.markAllRead().getOrThrow()
            assertTrue(bob.state.value.notifications.filter { it.eventId == event.id }.all { it.isRead })
            assertTrue(alice.state.value.notifications.any { it.eventId == event.id && !it.isRead })
            // Simulate a server commit whose success reply never reached the repository.
            bob.cancelRegistration(event.id).getOrThrow()
            lostResponse(context, bobServices, "join", mapOf("eventId" to event.id), "registration")
            bob.refresh()
            bob.cancelRegistration(event.id).getOrThrow()
            assertEquals(RegistrationStatus.CONFIRMED, bob.join(event.id).getOrThrow().status)
            bob.refresh()
            assertEquals(RegistrationStatus.CONFIRMED, bob.state.value.registrations.single { it.eventId == event.id && it.userId == b.id }.status)
            alice.postAnnouncement(event.id, "Before lost read acknowledgement").getOrThrow()
            bob.refresh()
            val readContext = mapOf("unreadIds" to bob.state.value.notifications.filter { !it.isRead }.map { it.id }.sorted())
            lostResponse(context, bobServices, "markAllRead", emptyMap(), "markAllRead", readContext)
            bob.refresh()
            alice.postAnnouncement(event.id, "New notice after the first read").getOrThrow()
            bob.refresh()
            assertTrue(bob.state.value.notifications.any { !it.isRead })
            bob.markAllRead().getOrThrow()
            assertTrue(bob.state.value.notifications.all { it.isRead })
            val announcement = mapOf("eventId" to event.id, "content" to "Exactly once after a lost response")
            lostResponse(context, aliceServices, "postAnnouncement", announcement, "postAnnouncement")
            alice.refresh()
            val countBeforeRetry = alice.state.value.announcements.count { it.eventId == event.id }
            alice.postAnnouncement(event.id, "Exactly once after a lost response").getOrThrow()
            assertEquals(countBeforeRetry, alice.state.value.announcements.count { it.eventId == event.id })
            bob.refresh(); bob.markAllRead().getOrThrow()
            bob.logout().getOrThrow()
            assertNull(bob.state.value.currentUserId)
            assertTrue(bob.state.value.notifications.isEmpty())
            bob.signIn("bob-$suffix@example.test", "ExamplePass2026!").getOrThrow()
            assertEquals(b.id, bob.state.value.currentUserId)
            assertTrue(bob.state.value.notifications.filter { it.eventId == event.id }.all { it.isRead })
            assertEquals("Spare equipment is available", bob.state.value.questions.single { it.id == question.id }.answer)
            // A missing emulator region simulates an unreachable backend while Auth identity survives restart.
            val unavailableFunctions = FirebaseFunctions.getInstance(bobServices.app, "unavailable-test-region")
            unavailableFunctions.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 5001)
            val cached = FirebaseGogetherRepository(context, bobServices.copy(functions = unavailableFunctions,
                emulatorCallableUrl = bobServices.emulatorCallableUrl!!.replace("/us-central1/", "/unavailable-test-region/")))
            try {
                assertTrue(cached.restoreAccountSession().isFailure)
                assertEquals(b.id, cached.state.value.currentUserId)
                assertEquals(event.id, cached.state.value.event(event.id)?.id)
                assertNotNull(cached.sync.value.message)
                val before = cached.state.value.questions
                assertTrue(cached.askQuestion(event.id, "Must not become a local success").isFailure)
                assertEquals(before, cached.state.value.questions)
            } finally { cached.close() }

        } finally {
            alice.close(); bob.close(); aliceServices.app.delete(); bobServices.app.delete()
        }
    }
    private suspend fun lostResponse(context: Context, services: FirebaseServices, action: String,
        payload: Map<String, Any?>, group: String, journalPayload: Map<String, Any?> = payload) {
        val uid = requireNotNull(services.auth.currentUser).uid
        val id = UUID.randomUUID().toString()
        services.invokeCallable(payload + mapOf("action" to action, "requestId" to id))
        fun canonical(value: Any?): Any = when(value) {
            null -> JSONObject.NULL
            is Map<*, *> -> JSONObject().apply { value.keys.map { it.toString() }.sorted().forEach { put(it, canonical(value[it])) } }
            is Iterable<*> -> JSONArray().apply { value.forEach { put(canonical(it)) } }
            else -> value
        }
        fun fingerprint(name: String, data: Map<String, Any?>) = MessageDigest.getInstance("SHA-256")
            .digest("$uid:$name:${canonical(data)}".toByteArray()).joinToString("") { "%02x".format(it) }
        val key = "request:${fingerprint(action, journalPayload)}"
        val resource = if (action in setOf("createEvent", "postAnnouncement", "askQuestion")) payload
            else payload["eventId"]?.let { mapOf("eventId" to it) } ?: emptyMap()
        val preferences = context.getSharedPreferences("cloud-requests-${services.app.name}", Context.MODE_PRIVATE)
        val sequence = maxOf(System.currentTimeMillis(), preferences.getLong("requestSequence", 0) + 1)
        check(preferences.edit().putString(key, id).putString("actor:$key", uid).putString("action:$key", action)
            .putString("scope:$key", "intent:${fingerprint(group, resource)}").putLong("sequence:$key", sequence)
            .putLong("requestSequence", sequence).commit())
    }

}
