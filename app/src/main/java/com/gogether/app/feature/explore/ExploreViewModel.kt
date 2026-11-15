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
import java.time.LocalDate
import java.time.ZoneId

enum class EventTimeFilter(val label: String) {
    ANY("Any time"), TODAY("Today"), TOMORROW("Tomorrow"),
    THIS_WEEKEND("This Weekend"), CUSTOM("Custom dates")
}

enum class FreeTimeFilter(val label: String) {
    ANY("Any free time"), MORNING("Morning"), AFTERNOON("Afternoon"),
    EVENING("Evening"), WEEKEND("Weekend")
}

enum class EquipmentFilter(val label: String) {
    ANY("Any equipment"), REQUIRED("Bring equipment"), NOT_REQUIRED("No equipment needed")
}

data class ExploreFilter(
    val query: String = "",
    val organizerQuery: String = "",
    val city: String = "",
    val categories: Set<Category> = emptySet(),
    val time: EventTimeFilter = EventTimeFilter.ANY,
    val customStartDate: String = "",
    val customEndDate: String = "",
    val freeTime: FreeTimeFilter = FreeTimeFilter.ANY,
    val experienceLevels: Set<ExperienceRequirement> = emptySet(),
    val guidanceOnly: Boolean = false,
    val equipment: EquipmentFilter = EquipmentFilter.ANY,
    val spareEquipmentOnly: Boolean = false,
)

fun Event.matchesExploreFilter(
    filter: ExploreFilter,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    organizerName: String = "",
): Boolean {
    if (status != EventStatus.OPEN || endAt <= now) return false
    if (filter.categories.isNotEmpty() && category !in filter.categories) return false
    val searchable = listOf(title, description, category.label, city, location, notes, requiredItems,
        spareEquipment, organizerName, experienceRequirement.label).joinToString(" ")
    if (!searchable.contains(filter.query.trim(), ignoreCase = true)) return false
    if (!organizerName.contains(filter.organizerQuery.trim(), ignoreCase = true)) return false
    if (!city.contains(filter.city.trim(), true) && !location.contains(filter.city.trim(), true)) return false
    val localToday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val eventDate = Instant.ofEpochMilli(startAt).atZone(zone).toLocalDate()
    val timeMatches = when (filter.time) {
        EventTimeFilter.ANY -> true
        EventTimeFilter.TODAY -> eventDate == localToday
        EventTimeFilter.TOMORROW -> eventDate == localToday.plusDays(1)
        EventTimeFilter.THIS_WEEKEND -> {
            val saturday = when (localToday.dayOfWeek) {
                DayOfWeek.SATURDAY -> localToday
                DayOfWeek.SUNDAY -> localToday.minusDays(1)
                else -> localToday.plusDays((DayOfWeek.SATURDAY.value - localToday.dayOfWeek.value).toLong())
            }
            eventDate in saturday..saturday.plusDays(1)
        }
        EventTimeFilter.CUSTOM -> {
            val start = filter.customStartDate.toLocalDateOrNull()
            val end = filter.customEndDate.toLocalDateOrNull()
            val validStart = filter.customStartDate.isBlank() || start != null
            val validEnd = filter.customEndDate.isBlank() || end != null
            validStart && validEnd && (start == null || !eventDate.isBefore(start)) &&
                (end == null || !eventDate.isAfter(end))
        }
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
        (filter.experienceLevels.isEmpty() || experienceRequirement in filter.experienceLevels) &&
        (!filter.guidanceOnly || beginnerGuidance) &&
        (filter.equipment == EquipmentFilter.ANY ||
            filter.equipment == EquipmentFilter.REQUIRED && requiredItems.isNotBlank() ||
            filter.equipment == EquipmentFilter.NOT_REQUIRED && requiredItems.isBlank()) &&
        (!filter.spareEquipmentOnly || spareEquipment.isNotBlank())
}

private fun String.toLocalDateOrNull(): LocalDate? =
    trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

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
