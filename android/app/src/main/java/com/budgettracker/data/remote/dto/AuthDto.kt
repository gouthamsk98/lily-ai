package com.budgettracker.data.remote.dto

import com.budgettracker.domain.model.User
import com.google.gson.annotations.SerializedName

data class UserResponse(
    val id: String,
    val email: String,
    val name: String,
    @SerializedName("notification_time") val notificationTime: String,
    @SerializedName("created_at") val createdAt: String,
) {
    fun toDomain() = User(id, email, name, notificationTime, createdAt)
}
