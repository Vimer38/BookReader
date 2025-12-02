package com.example.bookreader.data.model

data class Todo(
    val id: Int,
    val userId: Int,
    val title: String,
    val completed: Boolean
)

data class RemoteTodoDto(
    val userId: Int,
    val id: Int,
    val title: String,
    val completed: Boolean
)

