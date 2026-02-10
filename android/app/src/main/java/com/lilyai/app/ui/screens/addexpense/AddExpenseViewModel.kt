package com.lilyai.app.ui.screens.addexpense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lilyai.app.domain.model.Category
import com.lilyai.app.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AddExpenseState(
    val amount: String = "",
    val category: Category = Category.FOOD,
    val note: String = "",
    val date: String = LocalDate.now().toString(),
    val isLoading: Boolean = false,
    val success: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddExpenseState())
    val state = _state.asStateFlow()

    fun updateAmount(value: String) { _state.value = _state.value.copy(amount = value) }
    fun updateCategory(value: Category) { _state.value = _state.value.copy(category = value) }
    fun updateNote(value: String) { _state.value = _state.value.copy(note = value) }
    fun updateDate(value: String) { _state.value = _state.value.copy(date = value) }

    fun submit() {
        val amount = _state.value.amount.toDoubleOrNull()
        if (amount == null || amount < 0) {
            _state.value = _state.value.copy(error = "Invalid amount")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                expenseRepository.createExpense(
                    amount, _state.value.category,
                    _state.value.note.ifBlank { null }, _state.value.date,
                )
                _state.value = _state.value.copy(isLoading = false, success = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}
