package com.gogether.app.feature.checkin.prototype

/** E-owned optional feedback fields. IDs are stable for the future backend adapter. */
data class FeedbackDetails(
    val organization: Int = 0,
    val venue: Int = 0,
    val atmosphere: Int = 0,
    val tags: List<String> = emptyList(),
    val anonymous: Boolean = false,
    val visibility: FeedbackVisibility = FeedbackVisibility.PUBLIC,
    val photos: List<String> = emptyList(),
    val positiveComment: String = "",
    val improvementComment: String = "",
) {
    fun requireValid() {
        require(positiveComment.length <= 500 && improvementComment.length <= 500) { "Keep each optional comment within 500 characters." }
        require(listOf(organization, venue, atmosphere).all { it in 0..5 }) { "Rate each category from 1 to 5, or leave it blank." }
        require(tags.size == tags.distinct().size && tags.all { id -> feedbackTags.any { it.id == id } }) { "Invalid review tags. Choose again." }
        require(photos.size <= 3 && photos.distinct().size == photos.size && photos.all { photoIdPattern.matches(it) }) {
            "You can add up to 3 photos. Choose again."
        }
    }
}

enum class FeedbackVisibility { PUBLIC, ORGANIZER_ONLY }
internal val photoIdPattern = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")

/** A read DTO intentionally contains no author ID or avatar when anonymous. */
data class VisibleFeedback(val authorName: String, val anonymous: Boolean, val own: Boolean,
    val rating: Int, val comment: String, val createdAt: Long, val details: FeedbackDetails)

fun PrototypeFeedback.visibleTo(viewer: String, organizer: String,
    authorName: String = prototypeParticipants.find { it.userId == userId }?.name ?: "Participant"): VisibleFeedback? {
    if (viewer.isBlank() || (details.visibility == FeedbackVisibility.ORGANIZER_ONLY && viewer != userId && viewer != organizer)) return null
    val name = if (details.anonymous) "Anonymous participant" else authorName
    return VisibleFeedback(name, details.anonymous, viewer == userId, rating, comment, createdAt, details)
}

data class FeedbackTag(val id: String, val label: String, val improvement: Boolean = false)
val feedbackTags = listOf(
    FeedbackTag("clear_meeting", "Clear meeting point"), FeedbackTag("well_organized", "Well organized"),
    FeedbackTag("welcoming", "Welcoming"), FeedbackTag("beginner_friendly", "Beginner friendly"),
    FeedbackTag("helpful_guidance", "Helpful guidance"), FeedbackTag("comfortable_venue", "Comfortable venue"),
    FeedbackTag("good_pace", "Good pace"), FeedbackTag("join_again", "Would join again"),
    FeedbackTag("clearer_meeting", "Meeting directions", true), FeedbackTag("better_timing", "Timing", true),
    FeedbackTag("more_guidance", "Beginner guidance", true), FeedbackTag("better_venue", "Venue facilities", true),
    FeedbackTag("more_equipment", "Equipment", true), FeedbackTag("more_interaction", "More interaction", true),
)
