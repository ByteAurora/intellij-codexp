package com.github.ilovegamecoding.intellijcodexp.model

data class CodeXPChallenge(
    val id: String,
    val name: String,
    val category: Type,
    val description: String,
    val xp: Long,
    val goal: Long,
    var isCompleted: Boolean,
    val onCompletion: () -> Unit
) {
    enum class Type {
        XP,
        ACTION
    }
}