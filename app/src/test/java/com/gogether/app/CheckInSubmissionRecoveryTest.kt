package com.gogether.app

import com.gogether.app.feature.checkin.prototype.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CheckInSubmissionRecoveryTest {
    private val time = 1_800_000_000_000L
    private val session = PrototypeSession("f23e4af8-b3ed-49ef-a7ee-d7c1cf2bf317",
        time + 600_000, time + 7_800_000)
    private val raw = PrototypeCodeCodec.encode(PrototypeCode(session, time, time + 30_000))
    private fun scenario(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { block() } finally { Dispatchers.resetMain() }
    }
    private inner class Fixture {
        var clock = time
        var user = "bob"
        var submits = 0
        var queries = 0
        var locations = 0
        val store = MemoryPrototypeStore()
        val journal = MemoryPendingCheckInStore()
        val backend = LocalPrototypeBackend(store, { clock })
        val receipt = PrototypeReceipt(session.id, "bob", time, "initial:${session.id}:bob")
        var send: suspend (CheckInAttempt) -> PrototypeReceipt = { receipt }
        var query: suspend (CheckInAttempt) -> CheckInResolution = { CheckInResolution.Unknown }
        val source = object : CheckInSubmissionSource {
            override suspend fun submit(attempt: CheckInAttempt, raw: String): PrototypeReceipt {
                submits++; return send(attempt)
            }
            override suspend fun resolve(attempt: CheckInAttempt): CheckInResolution { queries++; return query(attempt) }
        }
        fun model(pending: PendingCheckInStore = journal) = PrototypeViewModel("bob", backend, now = { clock },
            entry = CheckInEntry.Activity(session), identity = CheckInIdentitySource { user },
            submissions = source, pendingSubmissions = pending)
    }

    @Test fun lostReplyFindsSavedResultWithoutResubmission() = scenario {
        val f = Fixture()
        var sentId = ""
        f.send = { sentId = it.id; f.store.save(f.receipt); throw IOException("reply lost") }
        f.query = { assertEquals(sentId, it.id); CheckInResolution.Saved(f.receipt) }
        val model = f.model()
        model.receiveScan(raw); runCurrent()
        assertEquals(1, f.submits); assertEquals(1, f.queries)
        assertEquals(ParticipantScanStage.SUCCESS, model.state.value.scanStage)
        assertEquals(f.receipt, model.state.value.history.single().receipt)
        assertNull(f.journal.get("bob", session.id))
        model.resolveCheckIn(); runCurrent()
        assertEquals(1, f.queries)
    }

    @Test fun timedOutSubmissionMustBeConfirmedNotSubmittedBeforeFreshScan() = scenario {
        val f = Fixture()
        f.send = { delay(20_000); f.receipt }
        f.query = { CheckInResolution.NotSubmitted }
        val model = f.model()
        model.receiveScan(raw); runCurrent()
        assertTrue(model.state.value.busy)
        advanceTimeBy(10_000); runCurrent()
        assertFalse(model.state.value.busy)
        assertTrue(model.state.value.message.contains("previous check-in was not completed"))
        assertNull(model.state.value.pendingSubmission)
        assertEquals(1, f.submits)
        f.send = { f.store.save(f.receipt); f.receipt }
        model.receiveScan(raw); runCurrent()
        assertEquals(2, f.submits);
        assertEquals(ParticipantScanStage.SUCCESS, model.state.value.scanStage)
    }

    @Test fun unknownResultBlocksRescanAndRetryOnlyQueriesSameAttempt() = scenario {
        val f = Fixture()
        f.send = { throw IOException() }
        val model = f.model()
        model.receiveScan(raw); runCurrent()
        val attempt = model.state.value.pendingSubmission!!
        assertFalse(model.state.value.busy)
        assertNull(model.state.value.notice)
        assertNull(model.beginScan())
        model.resolveCheckIn(); model.receiveScan(raw); runCurrent()
        assertEquals(1, f.submits); assertEquals(2, f.queries)
        assertEquals(attempt, model.state.value.pendingSubmission)
        f.clock = session.endAt + 100_000
        f.query = { CheckInResolution.Saved(f.receipt) }
        model.resolveCheckIn(); runCurrent()
        assertEquals(ParticipantScanStage.SUCCESS, model.state.value.scanStage)
        assertEquals(f.receipt, model.state.value.receipt)
        assertEquals(1, f.submits)
    }

    @Test fun lookupTimeoutAndLookupFailureBothKeepUnknownInsteadOfEnablingSubmission() = scenario {
        val f = Fixture()
        f.send = { throw IOException() }
        f.query = { delay(20_000); CheckInResolution.NotSubmitted }
        val model = f.model()
        model.receiveScan(raw); runCurrent()
        assertEquals(ParticipantScanStage.RESOLVING, model.state.value.scanStage)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(ParticipantScanStage.ATTENTION, model.state.value.scanStage)
        assertNotNull(model.state.value.pendingSubmission)
        assertFalse(model.state.value.busy)
        f.query = { throw IOException() }
        model.resolveCheckIn(); runCurrent()
        assertNotNull(model.state.value.pendingSubmission)
        assertNull(model.state.value.notice)
    }

    @Test fun definiteRejectionClearsAttemptAndDoesNotQueryOrClaimSuccess() = scenario {
        val f = Fixture()
        f.send = { throw CheckInRejected("The current user no longer has registration eligibility.") }
        val model = f.model()
        model.receiveScan(raw); runCurrent()
        assertEquals(0, f.queries)
        assertNull(f.journal.get("bob", session.id))
        assertNull(model.state.value.notice)
        assertTrue(model.state.value.message.contains("registration eligibility"))
    }

    @Test fun mismatchedReplyAndLookupCannotDisplayAnotherUsersReceipt() = scenario {
        val f = Fixture()
        f.send = { f.receipt.copy(userId = "carol") }
        f.query = { CheckInResolution.Saved(f.receipt.copy(sessionId = "other")) }
        val model = f.model()
        model.receiveScan(raw); runCurrent()
        assertNull(model.state.value.receipt)
        assertNull(model.state.value.notice)
        assertNotNull(model.state.value.pendingSubmission)
        assertFalse(model.state.value.busy)
    }

    @Test fun leavingAndRecreatingModelRestoresAttemptAndQueriesWithoutLocationOrSubmission() = scenario {
        val f = Fixture()
        f.send = { awaitCancellation() }
        val model = f.model()
        model.receiveScan(raw); runCurrent()
        val pending = model.state.value.pendingSubmission
        model.leaveForeground(); runCurrent()
        val restored = f.model()
        assertEquals(pending, restored.state.value.pendingSubmission)
        f.query = { CheckInResolution.Saved(f.receipt) }
        restored.resolveCheckIn(); runCurrent()
        assertEquals(ParticipantScanStage.SUCCESS, restored.state.value.scanStage)
        assertEquals(1, f.submits)
        assertNull(f.journal.get("bob", session.id))
    }

    @Test fun switchingActivityIgnoresLateResponseAndPreservesOriginalPendingRecord() = scenario {
        val f = Fixture()
        val complete = CompletableDeferred<Unit>()
        f.send = { withContext(NonCancellable) { complete.await(); f.receipt } }
        val model = f.model()
        model.receiveScan(raw); runCurrent()
        val other = session.copy(id = "414f1652-7e75-4d90-9cd7-c42f3665623a")
        model.enterActivity(other)
        complete.complete(Unit); runCurrent()
        assertEquals(other, model.state.value.participantSession)
        assertNull(model.state.value.receipt)
        assertNull(model.state.value.pendingSubmission)
        assertNotNull(f.journal.get("bob", session.id))
        assertTrue(model.hasPendingCheckIn(session))
        assertFalse(model.hasPendingCheckIn(other))
        model.enterActivity(session)
        assertNotNull(model.state.value.pendingSubmission)
    }

    @Test fun accountSwitchDuringLookupDoesNotExposeResultOrConsumePreviousAccountsAttempt() = scenario {
        val f = Fixture()
        val complete = CompletableDeferred<Unit>()
        f.send = { throw IOException() }
        f.query = { withContext(NonCancellable) { complete.await(); CheckInResolution.Saved(f.receipt) } }
        val model = f.model()
        model.receiveScan(raw); runCurrent()
        f.user = "carol"
        model.refreshIdentity()
        complete.complete(Unit); runCurrent()
        assertTrue(model.state.value.identityExpired)
        assertNull(model.state.value.receipt)
        assertNotNull(f.journal.get("bob", session.id))
        assertNull(f.journal.get("carol", session.id))
    }

    @Test fun progressStorageFailurePreventsSendingRequest() = scenario {
        val f = Fixture()
        val broken = object : PendingCheckInStore by f.journal {
            override fun save(attempt: CheckInAttempt) { error("Could not save check-in progress") }
        }
        val model = f.model(broken)
        model.receiveScan(raw); runCurrent()
        assertEquals(0, f.submits)
        assertNull(model.state.value.notice)
        assertTrue(model.state.value.message.contains("Could not save"))
    }

    @Test fun unresolvedActivityDoesNotBlockAnotherActivityAndIsRecoveredWhenScannedAgain() = scenario {
        val f = Fixture()
        f.send = { throw IOException() }
        val model = f.model()
        model.receiveScan(raw); runCurrent()
        val pending = model.state.value.pendingSubmission
        val other = session.copy(id = "414f1652-7e75-4d90-9cd7-c42f3665623a")
        f.send = { f.receipt.copy(sessionId = it.session.id, registrationKey = it.registrationKey) }
        model.receiveScan(PrototypeCodeCodec.encode(PrototypeCode(other, time, time + 30_000)), model.beginScan(general = true))
        runCurrent()
        assertEquals(other.id, model.state.value.receipt?.sessionId)
        assertEquals(pending, f.journal.get("bob", session.id))
        val queryCount = f.queries
        model.receiveScan(raw, model.beginScan(general = true)); runCurrent()
        assertEquals(session, model.state.value.participantSession)
        assertEquals(pending, model.state.value.pendingSubmission)
        assertNull(model.state.value.receipt)
        assertEquals(queryCount + 1, f.queries)
        assertEquals(2, f.submits)
    }
}
