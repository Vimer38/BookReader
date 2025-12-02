package com.example.bookreader.data.repository

import androidx.room.withTransaction
import com.example.bookreader.data.local.BookDatabase
import com.example.bookreader.data.local.TodoDao
import com.example.bookreader.data.local.TodoEntity
import com.example.bookreader.data.model.RemoteTodoDto
import com.example.bookreader.data.model.Todo
import com.example.bookreader.data.remote.JsonPlaceholderApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepository @Inject constructor(
    private val api: JsonPlaceholderApi,
    private val todoDao: TodoDao,
    private val database: BookDatabase
) {
    fun observeTodos(userId: Int): Flow<List<Todo>> {
        return todoDao.observeTodos(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun refreshTodos(userId: Int) {
        val remote = api.getTodos(userId)
        val entities = remote.map { it.toEntity() }
        database.withTransaction {
            todoDao.deleteTodosForUser(userId)
            todoDao.insertTodos(entities)
        }
    }

    private fun TodoEntity.toDomain(): Todo = Todo(
        id = id,
        userId = userId,
        title = title,
        completed = completed
    )

    private fun RemoteTodoDto.toEntity(): TodoEntity = TodoEntity(
        id = id,
        userId = userId,
        title = title,
        completed = completed,
        syncedAt = System.currentTimeMillis()
    )
}

