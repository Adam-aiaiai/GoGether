package com.gogether.app

import com.gogether.app.feature.checkin.prototype.*
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrototypeViewModelTest {
    private val time = 1_800_000_000_000L


    @Test fun scanningAnotherEventKeepsItsContextWhenSubmissionFails() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val host = LocalPrototypeBackend(MemoryPrototypeStore(), { time })
            val first = host.createMeeting("alice")
            val store = MemoryPrototypeStore()
            val local = LocalPrototypeBackend(store, { time })
            var fail = false
            val backend = object : PrototypeCheckInBackend by local {
                override fun submit(actor: String, raw: String, registrationKey: String?): PrototypeReceipt {
                    require(!fail) { "Request rejected" }
                    return local.submit(actor, raw, registrationKey)
                }
            }
            val model = PrototypeViewModel("bob", backend, now = { time })
            model.receiveScan(PrototypeCodeCodec.encode(host.issue("alice", first))); runCurrent()
            val second = host.createMeeting("alice")
            fail = true
            model.receiveScan(PrototypeCodeCodec.encode(host.issue("alice", second))); runCurrent()
            assertEquals(second, model.state.value.participantSession)
            assertNull(model.state.value.receipt)
            assertNotNull(store.receipt(first.id, "bob"))
            assertEquals(ParticipantScanStage.ATTENTION, model.state.value.scanStage)
            model.leaveForeground()
            assertNull(model.state.value.notice)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun participantNoticeRequiresSavedNewReceiptAndIsNotRestoredOrRepeated() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {

            val host = LocalPrototypeBackend(MemoryPrototypeStore(), { time })
            val session = host.createMeeting("alice")
            val raw = PrototypeCodeCodec.encode(host.issue("alice", session))
            val store = MemoryPrototypeStore()
            val backend = LocalPrototypeBackend(store, { time })
            val model = PrototypeViewModel("bob", backend, now = { time })
            model.receiveScan("invalid"); runCurrent()
            assertNull(model.state.value.notice)
            assertEquals(ParticipantScanStage.ATTENTION, model.state.value.scanStage)
            model.receiveScan(raw); runCurrent()
            val notice = model.state.value.notice!!
            assertEquals("Checked in successfully", notice.text)
            assertEquals(ParticipantScanStage.SUCCESS, model.state.value.scanStage)
            assertNotNull(store.receipt(session.id, "bob"))
            assertEquals(session, model.state.value.participantSession)
            model.dismissNotice(notice.id - 1)
            assertEquals(notice, model.state.value.notice)
            model.dismissNotice(notice.id)
            assertNull(model.state.value.notice)
            model.receiveScan(raw); runCurrent()
            assertNull(model.state.value.notice)
            assertEquals(ParticipantScanStage.ATTENTION, model.state.value.scanStage)
            assertNotNull(model.state.value.receipt)
            val restored = PrototypeViewModel("bob", backend, store.receipt(session.id, "bob"))
            assertNotNull(restored.state.value.receipt)
            assertNull(restored.state.value.notice)
            assertEquals(ParticipantScanStage.READY, restored.state.value.scanStage)
            assertEquals(session, restored.state.value.participantSession)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun organizerOnlyAnnouncesNewObservedArrivalsAndResetsForNewSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = MemoryPrototypeStore()
            val backend = LocalPrototypeBackend(store, { time + testScheduler.currentTime })

            val session = backend.createMeeting("alice")
            store.save(PrototypeReceipt(session.id, "alice", time))
            val model = PrototypeViewModel("alice", backend, now = { time + testScheduler.currentTime })
            model.refreshAttendance(session); runCurrent()
            assertNull(model.state.value.notice)
            store.save(PrototypeReceipt(session.id, "bob", time + 1))
            model.refreshAttendance(session); runCurrent()
            assertEquals("Bob just checked in", model.state.value.notice!!.text)
            model.dismissNotice(model.state.value.notice!!.id)
            model.refreshAttendance(session); runCurrent()
            assertNull(model.state.value.notice)
            store.save(PrototypeReceipt(session.id, "carol", time + 2))
            store.save(PrototypeReceipt(session.id, "david", time + 3))
            model.refreshAttendance(session); runCurrent()
            assertEquals("2 people just checked in, including Carol", model.state.value.notice!!.text)
            model.leaveForeground(); runCurrent()
            assertNull(model.state.value.notice)
            model.prepareMeeting(); runCurrent(); model.confirmMeeting(); runCurrent()
            val next = model.state.value.session!!
            assertNotEquals(session.id, next.id)
            model.refreshAttendance(next); runCurrent()
            store.save(PrototypeReceipt(next.id, "bob", time + 30_000))
            model.refreshAttendance(next); runCurrent()
            assertEquals("Bob just checked in", model.state.value.notice!!.text)
            model.leaveForeground(); runCurrent()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun historyConnectsScanToFeedbackWithoutRescanningAnExpiredQr() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var clock = time

            val host = LocalPrototypeBackend(MemoryPrototypeStore(), { clock })
            val session = host.createMeeting("alice")
            val store = MemoryPrototypeStore()
            val backend = LocalPrototypeBackend(store, { clock })
            val model = PrototypeViewModel("bob", backend, now = { clock })
            model.receiveScan(PrototypeCodeCodec.encode(host.issue("alice", session))); runCurrent()
            assertNotNull(model.state.value.history.single().receipt)
            clock = session.endAt
            model.openHistory(); runCurrent()
            model.submitFeedback(session.id, 4, "I had a great time"); runCurrent()
            assertEquals("I had a great time", model.state.value.history.single().feedback!!.comment)
            model.submitFeedback(session.id, 2, "I would join again."); runCurrent()
            assertEquals(4, model.state.value.history.single().feedback!!.rating)
            assertTrue(model.state.value.message.contains("already reviewed"))
            assertTrue(PrototypeViewModel("carol", backend).state.value.history.isEmpty())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun hostCreatesFixedMeetingThenRefreshesCodeAndHidesItOnLeaving() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val backend = LocalPrototypeBackend(MemoryPrototypeStore(), { time + testScheduler.currentTime })
            var positionCalls = 0

            val model = PrototypeViewModel("alice", backend, now = { time + testScheduler.currentTime })
            model.prepareMeeting(); runCurrent()
            assertTrue(model.state.value.creatingMeeting)
            model.confirmMeeting(); runCurrent()
            assertNotNull(model.state.value.session)
            model.showCode(); runCurrent()
            val first = model.state.value.code!!
            advanceTimeBy(10_000); runCurrent()
            assertNotEquals(first, model.state.value.code)
            assertNotNull(model.state.value.receipt)
            model.leaveForeground(); runCurrent()
            assertNull(model.state.value.code)
            advanceTimeBy(60_000); runCurrent()
            assertNotNull(model.state.value.receipt)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun cancellationDuringRefreshHidesCodeAndStopsRenewal() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = MemoryPrototypeStore()
            val backend = LocalPrototypeBackend(store, { time + testScheduler.currentTime })
            val session = backend.createMeeting("alice")
            val model = PrototypeViewModel("alice", backend, now = { time + testScheduler.currentTime })
            model.showCode(); runCurrent()
            assertNotNull(model.state.value.code)
            store.cancel(session.id)
            advanceTimeBy(10_000); runCurrent()
            assertNull(model.state.value.code)
            assertTrue(model.state.value.message.contains("cancelled"))
            model.leaveForeground()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun invalidScanDoesNotRecordAttendanceAndFreshScanCanSucceed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val host = LocalPrototypeBackend(MemoryPrototypeStore(), { time })
            val session = host.createMeeting("alice")
            val raw = PrototypeCodeCodec.encode(host.issue("alice", session))
            val store = MemoryPrototypeStore()
            val model = PrototypeViewModel("bob", LocalPrototypeBackend(store, { time }), now = { time })
            model.receiveScan("invalid"); runCurrent()
            assertTrue(store.receipts.isEmpty())
            model.receiveScan(raw); runCurrent()
            assertEquals("bob", model.state.value.receipt!!.userId)
            model.receiveScan(raw); runCurrent()
            assertEquals(1, store.receipts.size)
            assertTrue(model.state.value.message.contains("already checked in"))
        } finally { Dispatchers.resetMain() }
    }

    @Test fun expiredCodeCannotSubmit() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val host = LocalPrototypeBackend(MemoryPrototypeStore(), { time })
            val raw = PrototypeCodeCodec.encode(host.issue("alice", host.createMeeting("alice")))
            val store = MemoryPrototypeStore()
            val model = PrototypeViewModel("bob", LocalPrototypeBackend(store, { time + 30_000 }), now = { time + 30_000 })
            model.receiveScan(raw); runCurrent()
            assertTrue(store.receipts.isEmpty())
            assertTrue(model.state.value.message.contains("expired"))
        } finally { Dispatchers.resetMain() }
    }

    @Test fun dismissingEventPreparationDoesNotConfirmArrival() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = MemoryPrototypeStore()
            val model = PrototypeViewModel("alice", LocalPrototypeBackend(store, { time }))
            model.prepareMeeting(); runCurrent()
            assertTrue(model.state.value.creatingMeeting)
            model.dismissMeeting(); runCurrent()
            assertFalse(model.state.value.creatingMeeting)
            assertTrue(store.receipts.isEmpty())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun generatedQrIsDecodableByTheActualScannerLibrary() {
        val host = LocalPrototypeBackend(MemoryPrototypeStore(), { time })

        val code = host.issue("alice", host.createMeeting("alice"))
        val raw = PrototypeCodeCodec.encode(code)
        val matrix = com.gogether.app.core.ui.CheckInQr.matrix(raw)
        val pixels = IntArray(720 * 720) { i -> if (matrix[i % 720, i / 720]) -0x1000000 else -1 }
        val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(720, 720, pixels))))
        assertEquals(code, PrototypeCodeCodec.decode(decoded.text))
    }
}
