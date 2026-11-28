package com.gogether.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.core.data.*
import com.gogether.app.core.model.*
import com.gogether.app.feature.social.*
import com.gogether.app.feature.checkin.cloud.CloudFeedbackDraftStore
import com.gogether.app.feature.checkin.prototype.FeedbackVisibility
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Upgrade checks use isolated stores and never rewrite the installed user's data. */
class PhaseFiveCompatibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val base = instrumentation.targetContext
    private val prefix = "phase-five-${UUID.randomUUID()}"
    private val directory = File(base.cacheDir, prefix).apply { mkdirs() }
    private val preferenceNames = mutableSetOf<String>()
    private val context = object : ContextWrapper(base) {
        override fun getSharedPreferences(name: String, mode: Int) =
            base.getSharedPreferences("$prefix-$name".also(preferenceNames::add), mode)
    }
    @After fun clean() { directory.deleteRecursively(); preferenceNames.forEach(base::deleteSharedPreferences) }
    private fun absent() = object : SnapshotStore {
        override fun load(): AppSnapshot? = null
        override fun save(snapshot: AppSnapshot) = error("Source stores must not be written")
    }

    @Test fun everySupportedSnapshotVersionUpgradesAndRetainsExistingRecords() {
        val now = System.currentTimeMillis()
        val seed = DemoData.create(now).copy(currentUserId = "bob",
            questions = listOf(EventQuestion("q", "event-1", "bob", "Original question", now-3000,
                "Latest answer", "alice", now-1000, listOf(QuestionReply("answer-q", "alice", "First answer", now-2000), QuestionReply("second", "alice", "Latest answer", now-1000)))),
            eventRatings = mapOf("event-history" to EventRatingSummary(8, 2)))
        for (version in 1..SnapshotJson.CURRENT_VERSION) {
            val raw = if (version == 1) instrumentation.context.assets.open("ab-snapshot-v1.json").bufferedReader().use { it.readText() }
            else JSONObject(SnapshotJson.encode(seed)).apply {
                put("version", version)
                if (version < 7) remove("eventRatings")
                if (version < 5) getJSONArray("questions").getJSONObject(0).remove("replyHistory")
                if (version < 3) remove("questions")
            }.toString()
            val file = File(directory, "v$version.json").apply { writeText(raw) }
            val store = FileSnapshotStore(file)
            val original = store.load()!!
            assertEquals(if (version == 1) 6 else seed.events.size, original.events.size)
            store.save(original)
            assertEquals(original, FileSnapshotStore(file).load())
            assertEquals(SnapshotJson.CURRENT_VERSION, JSONObject(file.readText()).getInt("version"))
            if (version >= 3) assertEquals("Latest answer", original.questions.single().answer)
            if (version in 3..4) assertTrue(original.questions.single().replyHistory.isEmpty())
            if (version < 7) assertTrue(original.eventRatings.isEmpty())
            if (version >= 2) assertEquals(original.copy(currentUserId = null), LocalSnapshotStore.decode(file.readText()))
        }
    }

    @Test fun unsupportedOrCorruptDestinationIsPreservedInsteadOfBeingReseeded() {
        for (raw in listOf("{\"version\":999}", "{broken")) {
            val file = File(directory, "damaged.json").apply { writeText(raw) }
            val store = IntegratedSnapshotStore(FileSnapshotStore(file), absent()) { DemoData.create() }
            assertThrows(Exception::class.java) { store.load() }
            assertEquals(raw, file.readText())
        }
    }

    @Test fun divergentLegacyStoresArePreservedAndRequireAnExplicitChoice() {
        val old = DemoData.create().copy(currentUserId = "bob")
        val sourceFile = File(directory, "abd.json")
        FileSnapshotStore(sourceFile).save(old)
        val sourceBytes = sourceFile.readBytes()
        val destination = File(directory, "integrated.json")
        val other = old.copy(events = old.events.drop(1))
        val migration = IntegratedSnapshotStore(FileSnapshotStore(destination), FileSnapshotStore(sourceFile)) { other }
        assertThrows(IllegalArgumentException::class.java) { migration.load() }
        assertFalse(destination.exists())
        assertArrayEquals(sourceBytes, sourceFile.readBytes())
    }

    @Test fun matchingLegacyStoresMigrateOnceWithoutChangingSourceOrIdentity() {
        val old = DemoData.create().copy(currentUserId = "bob")
        val sourceFile = File(directory, "abd.json")
        FileSnapshotStore(sourceFile).save(old)
        val sourceBytes = sourceFile.readBytes()
        val destination = FileSnapshotStore(File(directory, "integrated.json"))
        val migration = IntegratedSnapshotStore(destination, FileSnapshotStore(sourceFile)) { old.copy(currentUserId = null) }
        assertEquals(old, migration.load())
        val updated = old.copy(currentUserId = "carol")
        migration.save(updated)
        assertEquals(updated, migration.load())
        assertArrayEquals(sourceBytes, sourceFile.readBytes())
    }

    @Test fun atomicFileRecoversInterruptedLegacyAndCurrentWrites() {
        val old = DemoData.create().copy(currentUserId = "bob")
        val file = File(directory, "atomic.json")
        FileSnapshotStore(file).save(old)
        File(file.path + ".bak").writeBytes(file.readBytes())
        file.writeText("interrupted old write")
        assertEquals(old, FileSnapshotStore(file).load())
        File(file.path + ".new").writeText("interrupted current write")
        assertEquals(old, FileSnapshotStore(file).load())
        assertEquals(old, SnapshotJson.decode(file.readText()))
    }

    @Test fun legacyQuestionMigrationDoesNotDuplicateRepliesOrCreateUnreadNotifications() = runBlocking {
        val now = System.currentTimeMillis()
        val q = EventQuestion("legacy", "event-1", "bob", "Old question", now-3000, "Old answer", "alice", now-2000)
        val n = Notification("read-notice", "bob", "event-1", NotificationType.QUESTION_ANSWERED, "Old answer notice", now-2000, true, questionId = q.id)
        val repo = FakeGogetherRepository(DemoData.create(now).copy(currentUserId = "bob", questions = listOf(q), notifications = listOf(n)))
        val gateway = RepositorySocialGateway(context, repo)
        repeat(3) {
            val thread = gateway.call("read", mapOf("rootId" to q.id))["items"] as List<*>
            assertEquals(2, thread.size)
            val notices = gateway.projectNotifications(repo.state.value).notifications
            assertEquals(1, notices.size); assertTrue(notices.single().isRead)
            assertEquals("answer-legacy", notices.single().contentId)
        }
        assertEquals(1, LocalSocialStore(context).load().replies.size)
        assertTrue(LocalSocialStore(context).load().notices.isEmpty())
    }

    @Test fun phaseTwoSocialStateRetainsPrivateRepliesAliasesAndModerationDuringUpgrade() {
        val raw = """{"replies":[{"id":"r","eventId":"e","authorId":"bob","text":"Private reply","createdAt":1,"rootId":"review","targetId":"review","private":true,"anonymous":true}],
            "deleted":["removed"],"likes":[{"id":"r","user":"alice"}],"blocks":[{"id":"b","owner":"alice","target":"bob","kind":"REGISTRATION","label":"Bob"}],
            "reports":[{"id":"rep","reporter":"alice","contentId":"r","reason":"Check this","createdAt":2,"status":"OPEN","outcome":"","moderator":"","resolvedAt":0}],
            "aliases":[{"event":"e","user":"bob","name":"Anonymous participant 01"}]}"""
        context.getSharedPreferences("social_v1", 0).edit().putString("state", raw).commit()
        val store = LocalSocialStore(context)
        val original = store.load()
        assertTrue(original.notices.isEmpty()); assertTrue(original.readNoticeIds.isEmpty())
        assertTrue(original.replies.single().private); assertTrue(original.replies.single().anonymous)
        assertEquals("Anonymous participant 01", store.anonymousName("e", "bob"))
        assertEquals("Anonymous participant 02", store.anonymousName("e", "carol"))
        val upgraded = LocalSocialStore(context).load()
        assertEquals(original.copy(aliases = upgraded.aliases), upgraded)
        assertEquals(original.aliases["e" to "bob"], upgraded.aliases["e" to "bob"])
        assertTrue(upgraded.notices.isEmpty())
    }
    @Test fun legacyCloudDraftPreservesTextAndNeverBroadensUnknownPrivacy() {
        val prefs=context.getSharedPreferences("cloud_feedback_drafts_v1",0)
        val raw="""{"rating":4,"comment":"Keep this draft","visibility":"FUTURE_PRIVATE","anonymous":true}"""
        prefs.edit().putString("bob:e",raw).commit()
        val store=CloudFeedbackDraftStore(context)
        val draft=store.load("bob","e")
        assertEquals("Keep this draft",draft.comment);assertEquals(4,draft.rating)
        assertTrue(draft.details.anonymous);assertEquals(FeedbackVisibility.ORGANIZER_ONLY,draft.details.visibility)
        assertEquals(raw,prefs.getString("bob:e",null))
        store.save("bob","e",draft)
        assertEquals(draft,CloudFeedbackDraftStore(context).load("bob","e"))
        assertEquals("",store.load("carol","e").comment)
    }

}
