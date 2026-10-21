package com.anistream.core.common

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable, val message: String? = exception.message) : Result<Nothing>()
    data object Loading : Result<Nothing>()

    fun isSuccess(): Boolean = this is Success
    fun isFailure(): Boolean = this is Error
    fun isLoading(): Boolean = this is Loading

    fun getOrNull(): T? = (this as? Success)?.data
    fun exceptionOrNull(): Throwable? = (this as? Error)?.exception

    fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> Error(exception, message)
        is Loading -> Loading
    }

    fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    fun onFailure(action: (Throwable) -> Unit): Result<T> {
        if (this is Error) action(exception)
        return this
    }

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun <T> error(exception: Throwable, message: String? = exception.message ?: exception.javaClass.simpleName): Result<T> = Error(exception, message)
        fun <T> loading(): Result<T> = Loading
    }
}
