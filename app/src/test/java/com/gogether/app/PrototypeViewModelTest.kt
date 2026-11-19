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
    private val point = MeetingPoint(31.27, 120.74)

    @Test fun scanningAnotherEventKeepsItsContextEvenWhenPositionFails() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fix = PositionFix(point, 10.0, 0)
            val host = LocalPrototypeBackend(MemoryPrototypeStore(), { time }, { 0 })
            val first = host.createMeeting("alice", fix)
            val store = MemoryPrototypeStore()
            val backend = LocalPrototypeBackend(store, { time }, { 0 })
            var failPosition = false
            val model = PrototypeViewModel("bob", backend, PositionSource {
                if (failPosition) error("无法获取位置") else fix
            }, now = { time })
            model.receiveScan(PrototypeCodeCodec.encode(host.issue("alice", first, fix))); runCurrent()
            val second = host.createMeeting("alice", fix)
            failPosition = true
            model.receiveScan(PrototypeCodeCodec.encode(host.issue("alice", second, fix))); runCurrent()
            assertEquals(second, model.state.value.participantSession)
            assertNull(model.state.value.receipt)
            assertNotNull(store.receipt(first.id, "bob"))
            assertEquals(ParticipantScanStage.ATTENTION, model.state.value.scanStage)
            model.leaveForeground()
            assertEquals(second, model.state.value.participantSession)
            assertNull(model.state.value.notice)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun participantNoticeRequiresSavedNewReceiptAndIsNotRestoredOrRepeated() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fix = PositionFix(point, 10.0, 0)
            val host = LocalPrototypeBackend(MemoryPrototypeStore(), { time }, { 0 })
            val session = host.createMeeting("alice", fix)
            val raw = PrototypeCodeCodec.encode(host.issue("alice", session, fix))
            val store = MemoryPrototypeStore()
            val backend = LocalPrototypeBackend(store, { time }, { 0 })
            val model = PrototypeViewModel("bob", backend, PositionSource { fix }, now = { time })
            model.receiveScan("invalid"); runCurrent()
            assertNull(model.state.value.notice)
            assertEquals(ParticipantScanStage.ATTENTION, model.state.value.scanStage)
            model.receiveScan(raw); runCurrent()
            val notice = model.state.value.notice!!
            assertEquals("签到成功", notice.text)
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
            val restored = PrototypeViewModel("bob", backend, PositionSource { fix }, store.receipt(session.id, "bob"))
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
            val backend = LocalPrototypeBackend(store, { time + testScheduler.currentTime }, { testScheduler.currentTime })
            val source = PositionSource { PositionFix(point, 10.0, testScheduler.currentTime) }
            val session = backend.createMeeting("alice", source.locate())
            store.save(PrototypeReceipt(session.id, "alice", time))
            val model = PrototypeViewModel("alice", backend, source, now = { time + testScheduler.currentTime })
            model.refreshAttendance(session); runCurrent()
            assertNull(model.state.value.notice)
            store.save(PrototypeReceipt(session.id, "bob", time + 1))
            model.refreshAttendance(session); runCurrent()
            assertEquals("Bob 刚刚签到", model.state.value.notice!!.text)
            model.dismissNotice(model.state.value.notice!!.id)
            model.refreshAttendance(session); runCurrent()
            assertNull(model.state.value.notice)
            store.save(PrototypeReceipt(session.id, "carol", time + 2))
            store.save(PrototypeReceipt(session.id, "david", time + 3))
            model.refreshAttendance(session); runCurrent()
            assertEquals("Carol 等 2 人刚刚签到", model.state.value.notice!!.text)
            model.leaveForeground(); runCurrent()
            assertNull(model.state.value.notice)
            model.locateMeeting(); runCurrent(); model.confirmMeeting(); runCurrent()
            val next = model.state.value.session!!
            assertNotEquals(session.id, next.id)
            model.refreshAttendance(next); runCurrent()
            store.save(PrototypeReceipt(next.id, "bob", time + 30_000))
            model.refreshAttendance(next); runCurrent()
            assertEquals("Bob 刚刚签到", model.state.value.notice!!.text)
            model.leaveForeground(); runCurrent()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun historyConnectsScanToFeedbackWithoutRescanningAnExpiredQr() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var clock = time
            val fix = PositionFix(point, 10.0, 0)
            val host = LocalPrototypeBackend(MemoryPrototypeStore(), { clock }, { 0 })
            val session = host.createMeeting("alice", fix)
            val store = MemoryPrototypeStore()
            val backend = LocalPrototypeBackend(store, { clock }, { 0 })
            val model = PrototypeViewModel("bob", backend, PositionSource { fix }, now = { clock })
            model.receiveScan(PrototypeCodeCodec.encode(host.issue("alice", session, fix))); runCurrent()
            assertNotNull(model.state.value.history.single().receipt)
            clock = session.endAt
            model.openHistory(); runCurrent()
            model.submitFeedback(session.id, 4, "很开心"); runCurrent()
            assertEquals("很开心", model.state.value.history.single().feedback!!.comment)
            model.submitFeedback(session.id, 2, "重复"); runCurrent()
            assertEquals(4, model.state.value.history.single().feedback!!.rating)
            assertTrue(model.state.value.message.contains("已评价"))
            assertTrue(PrototypeViewModel("carol", backend, PositionSource { fix }).state.value.history.isEmpty())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun hostCreatesFixedMeetingThenRefreshesCodeAndHidesItOnLeaving() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val backend = LocalPrototypeBackend(MemoryPrototypeStore(), { time + testScheduler.currentTime }, { testScheduler.currentTime })
            var positionCalls = 0
            val source = PositionSource { positionCalls++; PositionFix(point, 10.0, testScheduler.currentTime) }
            val model = PrototypeViewModel("alice", backend, source, now = { time + testScheduler.currentTime })
            model.locateMeeting(); runCurrent()
            assertNotNull(model.state.value.candidate)
            model.confirmMeeting(); runCurrent()
            assertEquals(point, model.state.value.session!!.meeting)
            model.showCode(); runCurrent()
            val first = model.state.value.code!!
            advanceTimeBy(10_000); runCurrent()
            assertNotEquals(first, model.state.value.code)
            assertEquals(3, positionCalls)
            model.leaveForeground(); runCurrent()
            assertNull(model.state.value.code)
            advanceTimeBy(60_000); runCurrent()
            assertEquals(3, positionCalls)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun movingOutsideRangeDuringRefreshHidesCodeAndStopsRenewal() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val backend = LocalPrototypeBackend(MemoryPrototypeStore(), { time + testScheduler.currentTime }, { testScheduler.currentTime })
            val session = backend.createMeeting("alice", PositionFix(point, 10.0, 0))
            var current = point
            val model = PrototypeViewModel("alice", backend,
                PositionSource { PositionFix(current, 10.0, testScheduler.currentTime) })
            model.showCode(); runCurrent()
            assertNotNull(model.state.value.code)
            current = MeetingPoint(point.latitude + 0.01, point.longitude)
            advanceTimeBy(10_000); runCurrent()
            assertNull(model.state.value.code)
            assertTrue(model.state.value.message.contains("超过 200 米"))
            assertEquals(session.meeting, model.state.value.session!!.meeting)
            model.leaveForeground()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun attendeeLocationFailureDoesNotRecordAttendanceAndNextScanCanSucceed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val host = LocalPrototypeBackend(MemoryPrototypeStore(), { time }, { 0 })
            val fix = PositionFix(point, 10.0, 0)
            val session = host.createMeeting("alice", fix)
            val raw = PrototypeCodeCodec.encode(host.issue("alice", session, fix))
            val attendeeStore = MemoryPrototypeStore()
            val backend = LocalPrototypeBackend(attendeeStore, { time }, { 0 })
            var denied = true
            val model = PrototypeViewModel("bob", backend, PositionSource {
                require(!denied) { "需要精确位置权限" }; fix
            }, now = { time })
            model.receiveScan(raw); runCurrent()
            assertNull(model.state.value.receipt)
            assertTrue(attendeeStore.receipts.isEmpty())
            assertTrue(model.state.value.message.contains("权限"))
            denied = false
            model.receiveScan(raw); runCurrent()
            assertEquals("bob", model.state.value.receipt!!.userId)
            model.receiveScan(raw); runCurrent()
            assertEquals(1, attendeeStore.receipts.size)
            assertTrue(model.state.value.message.contains("已签到"))
        } finally { Dispatchers.resetMain() }
    }

    @Test fun slowLocationCannotSubmitAnExpiredCode() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val host = LocalPrototypeBackend(MemoryPrototypeStore(), { time }, { 0 })
            val fix = PositionFix(point, 10.0, 0)
            val raw = PrototypeCodeCodec.encode(host.issue("alice", host.createMeeting("alice", fix), fix))
            val store = MemoryPrototypeStore()
            val model = PrototypeViewModel("bob", LocalPrototypeBackend(store,
                { time + testScheduler.currentTime }, { testScheduler.currentTime }), PositionSource {
                delay(30_001); fix.copy(elapsedMillis = testScheduler.currentTime)
            }, now = { time + testScheduler.currentTime })
            model.receiveScan(raw); advanceUntilIdle()
            assertTrue(store.receipts.isEmpty())
            assertTrue(model.state.value.message.contains("过期"))
        } finally { Dispatchers.resetMain() }
    }

    @Test fun leavingCancelsPendingPositionRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var cancelled = false
            val model = PrototypeViewModel("alice", LocalPrototypeBackend(MemoryPrototypeStore(), { time }, { 0 }),
                PositionSource { try { awaitCancellation() } finally { cancelled = true } })
            model.locateMeeting(); runCurrent()
            assertTrue(model.state.value.busy)
            model.leaveForeground(); runCurrent()
            assertTrue(cancelled)
            assertFalse(model.state.value.busy)
            assertNull(model.state.value.candidate)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun generatedQrIsDecodableByTheActualScannerLibrary() {
        val host = LocalPrototypeBackend(MemoryPrototypeStore(), { time }, { 0 })
        val fix = PositionFix(point, 10.0, 0)
        val code = host.issue("alice", host.createMeeting("alice", fix), fix)
        val raw = PrototypeCodeCodec.encode(code)
        val matrix = QRCodeWriter().encode(raw, BarcodeFormat.QR_CODE, 720, 720)
        val pixels = IntArray(720 * 720) { i -> if (matrix[i % 720, i / 720]) -0x1000000 else -1 }
        val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(720, 720, pixels))))
        assertEquals(code, PrototypeCodeCodec.decode(decoded.text))
    }
}
