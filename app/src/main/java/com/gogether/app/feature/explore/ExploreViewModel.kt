package com.gogether.app.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gogether.app.core.data.GogetherRepository
import com.gogether.app.core.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.DayOfWeek
import java.time.ZoneId

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

enum class EventTimeFilter(val label: String) {
    ANY("Any time"), TODAY("Today"), NEXT_7_DAYS("Next 7 days"), NEXT_30_DAYS("Next 30 days")
}

enum class FreeTimeFilter(val label: String) {
    ANY("Any free time"), MORNING("Morning"), AFTERNOON("Afternoon"),
    EVENING("Evening"), WEEKEND("Weekend")
}

data class ExploreFilter(
    val query: String = "",
    val organizerQuery: String = "",
    val city: String = "",
    val category: Category? = null,
    val time: EventTimeFilter = EventTimeFilter.ANY,
    val freeTime: FreeTimeFilter = FreeTimeFilter.ANY,
    val beginnerFriendlyOnly: Boolean = false,
    val guidanceOnly: Boolean = false,
    val spareEquipmentOnly: Boolean = false,
)

fun Event.matchesExploreFilter(
    filter: ExploreFilter,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    organizerName: String = "",
): Boolean {
    if (status != EventStatus.OPEN || endAt <= now) return false
    if (filter.category != null && category != filter.category) return false
    val searchable = listOf(title, description, notes, requiredItems, spareEquipment, organizerName,
        experienceRequirement.label).joinToString(" ")
    if (!searchable.contains(filter.query.trim(), ignoreCase = true)) return false
    if (!organizerName.contains(filter.organizerQuery.trim(), ignoreCase = true)) return false
    if (!city.contains(filter.city.trim(), true) && !location.contains(filter.city.trim(), true)) return false
    val timeMatches = when (filter.time) {
        EventTimeFilter.ANY -> true
        EventTimeFilter.TODAY -> Instant.ofEpochMilli(startAt).atZone(zone).toLocalDate() ==
            Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        EventTimeFilter.NEXT_7_DAYS -> startAt in now until now + 7 * DAY_MILLIS
        EventTimeFilter.NEXT_30_DAYS -> startAt in now until now + 30 * DAY_MILLIS
    }
    val localStart = Instant.ofEpochMilli(startAt).atZone(zone)
    val freeTimeMatches = when (filter.freeTime) {
        FreeTimeFilter.ANY -> true
        FreeTimeFilter.MORNING -> localStart.hour in 5..11
        FreeTimeFilter.AFTERNOON -> localStart.hour in 12..17
        FreeTimeFilter.EVENING -> localStart.hour in 18..23
        FreeTimeFilter.WEEKEND -> localStart.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
    return timeMatches && freeTimeMatches &&
        (!filter.beginnerFriendlyOnly || experienceRequirement == ExperienceRequirement.NONE) &&
        (!filter.guidanceOnly || beginnerGuidance) &&
        (!filter.spareEquipmentOnly || spareEquipment.isNotBlank())
}

class ExploreViewModel(repository: GogetherRepository) : ViewModel() {
    private val mutableFilter = MutableStateFlow(ExploreFilter())
    val filter = mutableFilter.asStateFlow()
    private val mutableNow = MutableStateFlow(System.currentTimeMillis())
    val now = mutableNow.asStateFlow()
    val events = combine(repository.state, filter, now) { state, activeFilter, time ->
        state.events.filter {
            it.matchesExploreFilter(activeFilter, time, organizerName = state.userName(it.organizerId))
        }.sortedBy { it.startAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            while (true) {
                mutableNow.value = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }

    fun filter(value: ExploreFilter) { mutableFilter.value = value }
}
