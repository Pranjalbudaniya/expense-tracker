package com.example.expensetracker.core.model.common

@JvmInline
value class EntityId(val value: String) {
    override fun toString(): String = value

    companion object {
        fun of(id: Long): EntityId = EntityId(id.toString())
        fun of(id: String): EntityId = EntityId(id)
    }
}
