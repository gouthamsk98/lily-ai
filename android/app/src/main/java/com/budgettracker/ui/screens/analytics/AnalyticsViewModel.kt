package com.budgettracker.ui.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.domain.model.ExpenseSummary
import com.budgettracker.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Period { DAILY, WEEKLY, MONTHLY }

data class AnalyticsState(
    val period: Period = Period.MONTHLY,
    val summary: ExpenseSummary? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AnalyticsState())
    val state = _state.asStateFlow()

    init { load() }

    fun setPeriod(period: Period) {
        _state.value = _state.value.copy(period = period)
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val summary = when (_state.value.period) {
                    Period.DAILY -> expenseRepository.getDailySummary()
                    Period.WEEKLY -> expenseRepository.getWeeklySummary()
                    Period.MONTHLY -> expenseRepository.getMonthlySummary()
                }
                _state.value = _state.value.copy(summary = summary, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }
}
