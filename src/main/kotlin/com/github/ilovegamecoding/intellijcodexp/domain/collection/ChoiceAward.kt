package com.github.ilovegamecoding.intellijcodexp.domain.collection

/** A committed range of newly earned post-cap rights, independent of the spendable balance. */
data class ChoiceAward(
    val generation: String,
    val first: Long,
    val last: Long,
) {
    val count: Long get() = last - first + 1
    val id: String get() = "$generation:$last"
}
