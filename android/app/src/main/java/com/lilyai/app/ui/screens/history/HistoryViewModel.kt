package com.lilyai.app.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lilyai.app.domain.model.Expense
import com.lilyai.app.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryState(
    val expenses: List<Expense> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryState())
    val state = _state.asStateFlow()

    init { loadExpenses() }

    fun loadExpenses() {
        viewModelScope.launch {
            try {
                val expenses = expenseRepository.getExpenses()
                _state.value = HistoryState(expenses, false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun deleteExpense(id: String) {
        viewModelScope.launch {
            try {
                expenseRepository.deleteExpense(id)
                // Reload from server to ensure consistency
                loadExpenses()
            } catch (e: Exception) {
                android.util.Log.e("HistoryVM", "Delete failed", e)
            }
        }
    }
}
