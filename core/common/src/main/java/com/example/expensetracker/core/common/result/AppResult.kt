package com.example.expensetracker.core.common.result

sealed interface AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : AppResult<Nothing>

    val isSuccess: Boolean
        get() = this is Success

    val isError: Boolean
        get() = this is Error

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    companion object {
        fun <T> success(data: T): AppResult<T> = Success(data)
        fun error(message: String, cause: Throwable? = null): AppResult<Nothing> = Error(message, cause)
    }
}
