package com.gogether.app.feature.checkin.prototype

import com.gogether.app.core.ui.AppBackButton
import com.gogether.app.core.ui.AppPageHeader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gogether.app.core.data.GogetherRepository
import com.gogether.app.core.model.Event
import com.gogether.app.core.ui.RepositoryViewModel

/** Both bound and never-scanned activities share E's complete form and privacy/storage path. */
@Composable
internal fun RepositoryFeedbackScreen(repository: GogetherRepository, bridge: RegistrationAttendanceBridge,
    event: Event, actor: String, now: Long, back: () -> Unit, initialAttendance: Boolean = false) {
    val snapshot by repository.state.collectAsStateWithLifecycle()
    var setup by remember(actor, event) { mutableStateOf<Result<PrototypeSession>?>(null) }
    LaunchedEffect(actor, event) { setup = runCatching { bridge.prepareFeedback(actor, event.id) } }
    val session = setup?.getOrNull()
    if (session == null) {
        Column(Modifier.padding(16.dp)) {
            AppBackButton(back)
            Text(setup?.exceptionOrNull()?.message ?: "Loading event records…")
        }
        return
    }
    val context = LocalContext.current
    val instance = remember(actor, session.id) { java.util.UUID.randomUUID().toString() }
    val model: PrototypeViewModel = viewModel(key = "feedback:$instance", factory = RepositoryViewModel.factory {
        PrototypeViewModel(actor, bridge.backend, AndroidPositionSource(context), entry = CheckInEntry.Activity(session),
            identity = CheckInIdentitySource { repository.state.value.currentUserId }, registrations = bridge)
    })
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(snapshot) { model.refreshLiveData(); model.openHistory() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(model, lifecycle) {
        val observer = LifecycleEventObserver { _, action ->
            if (action == Lifecycle.Event.ON_STOP) model.leaveForeground()
            if (action == Lifecycle.Event.ON_RESUME) { model.refreshLiveData(); model.openHistory() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); model.leaveForeground() }
    }
    val entryPanel = if (initialAttendance) "attendance" else ""
    var panel by remember(actor, session.id, entryPanel) { mutableStateOf(entryPanel) }
    fun goBack() { if (panel != entryPanel) panel = entryPanel else back() }
    BackHandler { goBack() }
    CompositionLocalProvider(LocalCheckInActivities provides model.activitySource) {
        Scaffold { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val record = state.history.find { it.session.id == session.id }
                when {
                    state.identityExpired -> Text("Your account has changed. Go back and reopen this page.")
                    panel == "reviews" -> {
                        LaunchedEffect(session, snapshot) { model.loadReviews(session) }
                        ActivityReviewsScreen(actor, session, state.reviews, state.busy, state.message,
                            { panel = "" }, { model.loadReviews(session) })
                    }
                    panel == "attendance" -> {
                        AttendanceRefreshEffect(session, live = false, model::refreshAttendance, model::stopAttendance)
                        AttendanceRecordScreen(state, session, now, ::goBack, model::refreshAttendance)
                    }
                    else -> Column(Modifier.fillMaxSize().imePadding().then(if (record?.canReview(now) == true) Modifier else Modifier.padding(horizontal = 16.dp))) {
                        if (record?.canReview(now) != true) AppPageHeader(if (record?.feedback == null) "Event review" else "My review", horizontalInset = 16.dp, onBack = back)
                        if (record != null) HistoryDetail(actor, record, now, state.busy, state.message,
                            model::submitFeedback, Modifier.weight(1f), openCheckIn = { back() },
                            openAttendance = { panel = "attendance" }, openReviews = { panel = "reviews" }, back = back,
                            reviewSummary = ReviewEventSummary(event.title, snapshot.users.find { it.id == event.organizerId }?.displayName ?: "Organizer",
                                event.startAt, event.imageUri, event.category.symbol))
                        else Text("Loading event records…")
                    }
                }
            }
        }
    }
}
