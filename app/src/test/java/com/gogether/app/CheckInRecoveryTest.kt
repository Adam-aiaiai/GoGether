package com.gogether.app

import com.gogether.app.feature.checkin.prototype.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckInRecoveryTest {
    private val time = 1_800_000_000_000L
    private val session = PrototypeSession("f23e4af8-b3ed-49ef-a7ee-d7c1cf2bf317", time + 600_000, time + 7_800_000)
    private val raw = PrototypeCodeCodec.encode(PrototypeCode(session, time, time + 30_000))
    private fun scenario(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { block() } finally { Dispatchers.resetMain() }
    }
    private inner class Fixture {
        var key: String? = "registration:1"
        var user: String? = "bob"
        val store = MemoryPrototypeStore()
        val pending = MemoryPendingCheckInStore()
        val registrations = object : CheckInRegistrationSource {
            override fun status(activityId: String?, userId: String) = if (key != null) CheckInRegistration.CONFIRMED else CheckInRegistration.NONE
            override fun registrationKey(activityId: String?, userId: String) = key
            override fun participants(activityId: String?) = listOf(PrototypeAttendee("bob", "Bob"))
        }
        val backend = LocalPrototypeBackend(store, { time }, registrations)
        fun model(source: CheckInSubmissionSource = LocalCheckInSubmissionSource(backend)) =
            PrototypeViewModel("bob", backend, now = { time }, entry = CheckInEntry.Activity(session),
                identity = CheckInIdentitySource { user }, registrations = registrations,
                submissions = source, pendingSubmissions = pending)
    }
    @Test fun cameraResultFromPreviousRegistrationCannotCheckInNewRegistration() = scenario {
        val f = Fixture(); val model = f.model(); val ticket = model.beginScan()
        f.key = "registration:2"
        model.receiveScan(raw, ticket); runCurrent()
        assertNull(f.store.receipt(session.id, "bob"))
        assertTrue(model.state.value.message.contains("registration changed"))
        model.receiveScan(raw, model.beginScan()); runCurrent()
        assertEquals("registration:2", model.state.value.receipt?.registrationKey)
    }
    @Test fun lateSavedResponseCannotRestorePreviousRegistrationAttendance() = scenario {
        val f = Fixture(); val delayed = CompletableDeferred<PrototypeReceipt>()
        val source = object : CheckInSubmissionSource {
            override suspend fun submit(attempt: CheckInAttempt, raw: String) = delayed.await()
            override suspend fun resolve(attempt: CheckInAttempt) = CheckInResolution.Unknown
        }
        val model = f.model(source)
        model.receiveScan(raw); runCurrent()
        f.key = "registration:2"
        delayed.complete(PrototypeReceipt(session.id, "bob", time, "registration:1")); runCurrent()
        assertNull(model.state.value.receipt)
        assertNull(model.state.value.notice)
        assertNull(f.pending.get("bob", session.id))
    }
    @Test fun pendingPreviousCycleIsDiscardedWithoutQueryingOrResubmitting() = scenario {
        val f = Fixture()
        f.pending.save(CheckInAttempt("old-attempt", "bob", session, "registration:1"))
        f.key = "registration:2"
        val source = object : CheckInSubmissionSource {
            override suspend fun submit(attempt: CheckInAttempt, raw: String): PrototypeReceipt = error("Must not submit")
            override suspend fun resolve(attempt: CheckInAttempt): CheckInResolution = error("Must not resolve stale cycle")
        }
        val model = f.model(source)
        model.resolveCheckIn(); runCurrent()
        assertNull(model.state.value.receipt)
        assertNull(f.pending.get("bob", session.id))
    }
    @Test fun legacyPendingWithoutRegistrationCycleCannotGrantAttendance() = scenario {
        val f = Fixture(); f.pending.save(CheckInAttempt("legacy", "bob", session))
        val model = f.model(); model.resolveCheckIn(); runCurrent()
        assertNull(model.state.value.receipt)
        assertNull(f.pending.get("bob", session.id))
    }
    @Test fun withdrawalRefreshClearsReceiptButPreservesStoredHistory() = scenario {
        val f = Fixture(); val model = f.model()
        model.receiveScan(raw); runCurrent()
        val saved = model.state.value.receipt!!
        f.key = null; model.refreshLiveData()
        assertNull(model.state.value.receipt)
        assertEquals(saved, f.store.receipt(session.id, "bob"))
        f.key = "registration:2"; model.refreshLiveData()
        assertNull(model.state.value.receipt)
        model.receiveScan(raw, model.beginScan()); runCurrent()
        assertEquals("registration:2", model.state.value.receipt?.registrationKey)
    }
    @Test fun closingCameraKeepsSavedReceiptWithoutAnotherNotice() = scenario {
        val f = Fixture(); val model = f.model()
        model.receiveScan(raw); runCurrent()
        val saved = model.state.value.receipt
        model.receiveScan(null, model.beginScan()); runCurrent()
        assertEquals(saved, model.state.value.receipt)
        assertNull(model.state.value.notice)
        assertEquals(ParticipantScanStage.READY, model.state.value.scanStage)
    }
    @Test fun cancellationBeforeReturningCameraCannotCommitAttendance() = scenario {
        val f = Fixture(); val model = f.model(); val ticket = model.beginScan()
        f.store.cancel(session.id)
        model.receiveScan(raw, ticket); runCurrent()
        assertNull(model.state.value.receipt)
        assertNull(f.store.receipt(session.id, "bob"))
    }
}
