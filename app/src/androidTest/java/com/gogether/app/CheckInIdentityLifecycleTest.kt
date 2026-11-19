package com.gogether.app

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.gogether.app.feature.checkin.prototype.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CheckInIdentityLifecycleTest {
    @Test fun accountChangeWhileCheckInActivityIsCoveredClosesItsOldInstance() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as GogetherApplication
        val previous = app.repository.state.value.currentUserId
        runBlocking { app.repository.login("bob").getOrThrow() }
        try {
            ActivityScenario.launch<PrototypeCheckInActivity>(CheckInEntryIntent.general(app)).use { scenario ->
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
                // Model the stopped state while an external camera screen is on top.
                scenario.moveToState(Lifecycle.State.CREATED)
                runBlocking { app.repository.login("carol").getOrThrow() }
                val deadline = SystemClock.uptimeMillis() + 10_000
                while (scenario.state != Lifecycle.State.DESTROYED && SystemClock.uptimeMillis() < deadline) {
                    instrumentation.waitForIdleSync()
                    SystemClock.sleep(50)
                }
                assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            }
        } finally {
            runBlocking { if (previous == null) app.repository.logout() else app.repository.login(previous) }
        }
    }
}
