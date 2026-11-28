package com.gogether.app.feature.checkin

/** Shared by E's legacy demo form and the QR/history flow. */
object FeedbackInput {
    const val MAX_COMMENT_LENGTH = 100
    fun valid(rating: Int, comment: String) = rating in 1..5 && comment.length <= MAX_COMMENT_LENGTH
}
