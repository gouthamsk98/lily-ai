package com.budgettracker.domain.model

data class User(
    val id: String,
    val email: String,
    val name: String,
    val notificationTime: String,
    val createdAt: String,
)
