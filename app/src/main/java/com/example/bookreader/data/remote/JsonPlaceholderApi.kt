package com.example.bookreader.data.remote

import com.example.bookreader.data.model.RemoteTodoDto
import retrofit2.http.GET
import retrofit2.http.Query

interface JsonPlaceholderApi {
    @GET("todos")
    suspend fun getTodos(
        @Query("userId") userId: Int
    ): List<RemoteTodoDto>
}

