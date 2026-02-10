package com.lilyai.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lilyai.app.domain.model.DailyStatus
import com.lilyai.app.domain.model.ExpenseSummary
import com.lilyai.app.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardState(
    val dailySummary: ExpenseSummary? = null,
    val weeklySummary: ExpenseSummary? = null,
    val monthlySummary: ExpenseSummary? = null,
    val dailyStatus: DailyStatus? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val daily = expenseRepository.getDailySummary()
                val weekly = expenseRepository.getWeeklySummary()
                val monthly = expenseRepository.getMonthlySummary()
                val status = expenseRepository.getDailyStatus()
                _state.value = DashboardState(daily, weekly, monthly, status, false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun submitZeroExpense() {
        viewModelScope.launch {
            try {
                expenseRepository.submitDay()
                refresh()
            } catch (_: Exception) {}
        }
    }
}
