package com.gogether.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.feature.checkin.prototype.*
import com.gogether.app.ui.theme.GogetherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.io.File

class PrototypeHistoryTest {
    @org.junit.After fun cleanupDraft() {
        val edit = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("checkin_prototype_v1", 0).edit()
        listOf("bob", "carol").forEach { edit.remove(FeedbackDraftStore.key(it, session.id)) }
        edit.commit()
    }
    @get:Rule val compose = createComposeRule()
    private val start = 1_800_000_000_000L
    private val session = PrototypeSession(UUID.randomUUID().toString(), MeetingPoint(31.27, 120.74), start, start + 60_000)
    private fun entry(receipt: Boolean = true, cancelled: Boolean = false) = PrototypeHistoryEntry(session,
        if (receipt) PrototypeReceipt(session.id, "bob", start) else null, null, cancelled)
    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun pendingFilterRatingAndSavedReviewFormACompleteFlow() {
        var records by mutableStateOf(listOf(entry(), entry().copy(session = session.copy(id = UUID.randomUUID().toString(),
            startAt = start + 120_000, endAt = start + 180_000))))
        var calls = 0
        compose.setContent { GogetherTheme {
            PrototypeHistoryScreen("bob", records, session.endAt, false, "", {}, { id, rating, comment, details ->
                calls++
                records = records.map { if (it.session.id == id) it.copy(feedback = PrototypeFeedback(id, "bob", rating, comment, session.endAt, details)) else it }
            })
        } }
        compose.onNodeWithTag("history_pending").performClick()
        compose.onNodeWithTag("history_${records[1].session.id}").assertDoesNotExist()
        capture("prototype-history.png")
        compose.onNodeWithTag("history_${session.id}").performClick()
        compose.onNodeWithTag("feedback_submit").assertIsNotEnabled()
        capture("prototype-feedback-form.png")
        compose.onNodeWithTag("feedback_rating_4").performScrollTo().performClick()
        compose.onNodeWithTag("history_detail").performScrollToNode(hasTestTag("feedback_comment"))
        compose.onNodeWithTag("feedback_comment").performScrollTo().performTextInput("集合点说明很清楚")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("feedback_submit").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("Submission must actually reach the callback", 1, calls) }
        // The long form keeps its scroll position; locate the saved summary before checking visibility.
        compose.onNodeWithTag("history_detail").performScrollToNode(hasTestTag("feedback_saved"))
        compose.onNodeWithTag("feedback_saved").assertIsDisplayed()
        compose.onNodeWithTag("history_detail").performScrollToNode(hasText("集合点说明很清楚"))
        compose.onNodeWithText("集合点说明很清楚").assertIsDisplayed()
        compose.onNodeWithTag("feedback_submit").assertDoesNotExist()
        capture("prototype-feedback-saved.png")
        assertEquals(1, calls)
    }

    @Test fun failedSubmissionKeepsInputAndAccountSwitchClearsTheDetail() {
        var actor by mutableStateOf("bob")
        var message by mutableStateOf("")
        var records by mutableStateOf(listOf(entry(false)))
        compose.setContent { GogetherTheme {
            PrototypeHistoryScreen(actor, records, session.endAt, false, message, {}, { _, _, _, _ -> message = "保存失败，请重试。" })
        } }
        compose.onNodeWithTag("history_${session.id}").performClick()
        compose.onNodeWithTag("feedback_rating_3").performScrollTo().performClick()
        compose.onNodeWithTag("history_detail").performScrollToNode(hasTestTag("feedback_comment"))
        compose.onNodeWithTag("feedback_comment").performScrollTo().performTextInput("保留这条评论")
        compose.onNodeWithTag("feedback_submit").performClick()
        compose.onNodeWithText("保存失败，请重试。").assertIsDisplayed()
        compose.onNodeWithTag("feedback_comment").assertTextContains("保留这条评论")
        compose.onNodeWithTag("history_detail").performScrollToNode(hasTestTag("feedback_rating_3"))
        compose.onNodeWithTag("feedback_rating_3").assertIsSelected()
        compose.runOnIdle { actor = "carol"; records = emptyList() }
        compose.onNodeWithTag("feedback_comment").assertDoesNotExist()
        compose.onNodeWithText("参与活动后，签到和评价会保存在这里。").assertExists()
    }

    @Test fun crossingEndTimeEnablesReviewButCancellationRemovesIt() {
        var now by mutableStateOf(session.endAt - 1)
        var records by mutableStateOf(listOf(entry(false)))
        compose.setContent { GogetherTheme { PrototypeHistoryScreen("bob", records, now, false, "", {}, { _, _, _, _ -> }) } }
        compose.onNodeWithTag("history_${session.id}").performClick()
        compose.onNodeWithTag("feedback_submit").assertDoesNotExist()
        compose.runOnIdle { now++ }
        compose.onNodeWithTag("feedback_submit").assertExists()
        compose.runOnIdle { records = listOf(entry(false, cancelled = true)) }
        compose.onNodeWithTag("feedback_submit").assertDoesNotExist()
        compose.onNodeWithText("活动已取消，不能评价。").assertExists()
    }

    @Test fun actualDiskStoreKeepsHistoryAndBlocksDuplicatesAcrossServiceInstances() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("checkin_prototype_v1", 0)
        var clock = start
        val fix = PositionFix(session.meeting, 10.0, 0)
        val code = PrototypeCodeCodec.encode(PrototypeCode(session, clock, clock + 30_000))
        fun service() = LocalPrototypeBackend(PreferencesPrototypeStore(context), { clock }, { 0 })
        val pool = Executors.newFixedThreadPool(4)
        try {
            val attempts = pool.invokeAll((1..8).map { Callable { runCatching { service().submit("bob", code, fix) }.isSuccess } })
            assertEquals(1, attempts.count { it.get() })
            val first = service().history("bob").single { it.session.id == session.id }
            assertNotNull(first.receipt)
            assertTrue(service().history("carol").none { it.session.id == session.id })
            assertThrows(IllegalArgumentException::class.java) { service().submit("bob", code, fix) }
            clock = session.endAt
            val details = FeedbackDetails(5, 0, 4, listOf("welcoming", "more_guidance"))
            val reviews = pool.invokeAll((1..8).map { Callable { runCatching { service().submitFeedback("bob", session.id, 4, "一起打球\n\"很开心\" 🏸", details) }.isSuccess } })
            assertEquals(1, reviews.count { it.get() })
            val restored = service().history("bob").single { it.session.id == session.id }
            assertEquals(4, restored.feedback!!.rating)
            assertEquals(details, restored.feedback!!.details)
            assertEquals("一起打球\n\"很开心\" 🏸", restored.feedback!!.comment)
            assertThrows(IllegalArgumentException::class.java) { service().submitFeedback("bob", session.id, 5, "重复") }
        } finally {
            pool.shutdownNow()
            prefs.edit().also { edit -> prefs.all.keys.filter { it.contains(session.id) }.forEach(edit::remove) }.commit()
        }
    }

    @Test fun olderStoredReviewWithoutDetailsRemainsReadable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("checkin_prototype_v1", 0)
        val key = "feedback.${session.id}.bob"
        try {
            assertTrue(prefs.edit().putString(key, """{"rating":4,"comment":"旧评价","createdAt":$start}""").commit())
            val saved = PreferencesPrototypeStore(context).feedback(session.id, "bob")!!
            assertEquals("旧评价", saved.comment)
            assertEquals(FeedbackDetails(), saved.details)
        } finally { prefs.edit().remove(key).commit() }
    }
}
