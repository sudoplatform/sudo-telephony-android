package com.sudoplatform.sudotelephony

/**
 * Models the result of an operation
 */
sealed class Result<out T> {

    /**
     * Models the success case with a number. Guaranteed to not be null.
     */
    data class Success<T>(val value: T): Result<T>()

    /**
     * Models the case where no number was returned.
     */
    object Absent: Result<Nothing>()

    /**
     * Models an error case
     */
    data class Error(val throwable: Throwable, val message: String? = null): Result<Nothing>()

    /**
     * Gets the number contained in the result, or throws an exception if there is no number
     * @return the contain number
     * @throws NoSuchElementException if the result is [Absent]
     * @throws throwable, if [Result] is of type [Error]
     */
    fun get(): T {
        return when (this) {
            is Success -> value
            is Absent -> throw NoSuchElementException("No number present")
            is Error -> throw throwable
        }
    }

    /**
     * Gets the number contained in the result, or returns null if there is no result
     * @return the contained number, or null
     */
    fun orNull(): T? {
        return when (this) {
            is Success -> value
            else -> null
        }
    }

    /**
     * Map the number contained in this result to another type, using the supplied mapper function f
     * @return new [Result] containing the mapped number
     */
    fun <E> map(f: (T) -> E): Result<E> {
        return when (this) {
            is Success -> Success(f(value))
            is Absent -> Absent
            is Error -> Error(throwable)
        }
    }

    /**
     * Map the number contained in this result to another type using the supplied mapper function, and flatten
     * @return new [Result] containing the mapped number
     */
    fun <E> flatMap(f: (T) -> Result<E>): Result<E> {
        return when(this) {
            is Success -> f(value)
            is Absent -> Absent
            is Error -> Error(throwable)
        }
    }

    companion object {
        /**
         * Creates a result from a nullable number.
         * @return Success(number) | Absent
         */
        fun <T> fromNullable(value: T?): Result<T> {
            return if (value != null) Success(value) else Absent
        }
    }
}