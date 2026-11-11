package com.gogether.app.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gogether.app.core.data.GogetherRepository
import com.gogether.app.core.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*

data class ExploreFilter(val query: String = "", val city: String = "", val category: Category? = null, val nextSevenDays: Boolean = false)

class ExploreViewModel(repository: GogetherRepository) : ViewModel() {
    private val mutableFilter = MutableStateFlow(ExploreFilter())
    val filter = mutableFilter.asStateFlow()
    private val mutableNow = MutableStateFlow(System.currentTimeMillis())
    val now = mutableNow.asStateFlow()
    val events = combine(repository.state, filter, now) { state, filter, time ->
        state.events.filter { event ->
            event.status == EventStatus.OPEN && event.endAt > time &&
                (filter.category == null || event.category == filter.category) &&
                (event.title.contains(filter.query, true) || event.description.contains(filter.query, true)) &&
                (event.city.contains(filter.city, true) || event.location.contains(filter.city, true)) &&
                (!filter.nextSevenDays || event.startAt < time + 7 * 24 * 60 * 60 * 1000L)
        }.sortedBy { it.startAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    init { viewModelScope.launch { while (true) { mutableNow.value = System.currentTimeMillis(); delay(1_000) } } }
    fun filter(value: ExploreFilter) { mutableFilter.value = value }
}
