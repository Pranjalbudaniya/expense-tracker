package com.example.expensetracker.core.common.validation

sealed interface ValidationResult {
    data object Valid : ValidationResult

    data class Invalid(val errors: List<String>) : ValidationResult {
        constructor(error: String) : this(listOf(error))
    }

    val isValid: Boolean
        get() = this is Valid

    val isInvalid: Boolean
        get() = this is Invalid

    val errorMessages: List<String>
        get() = when (this) {
            is Valid -> emptyList()
            is Invalid -> errors
        }

    companion object {
        fun valid(): ValidationResult = Valid
        fun invalid(error: String): ValidationResult = Invalid(error)
        fun invalid(errors: List<String>): ValidationResult = Invalid(errors)
    }
}
