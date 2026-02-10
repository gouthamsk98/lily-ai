package com.lilyai.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class EffectiveBudgetResponse(
    @SerializedName("daily_budget") val dailyBudget: String,
    @SerializedName("effective_budget_today") val effectiveBudgetToday: String,
    @SerializedName("carried_over") val carriedOver: String,
    @SerializedName("spent_today") val spentToday: String,
    @SerializedName("remaining_today") val remainingToday: String
)

data class SetBudgetRequest(
    @SerializedName("daily_budget") val dailyBudget: String
)

data class BudgetSettingResponse(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("daily_budget") val dailyBudget: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)
