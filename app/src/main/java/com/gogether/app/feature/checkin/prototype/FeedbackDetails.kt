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
) {
    fun requireValid() {
        require(listOf(organization, venue, atmosphere).all { it in 0..5 }) { "分项评分应为 1 至 5 分，也可以不填写。" }
        require(tags.size == tags.distinct().size && tags.all { id -> feedbackTags.any { it.id == id } }) { "评价标签无效，请重新选择。" }
        require(photos.size <= 3 && photos.distinct().size == photos.size && photos.all { photoIdPattern.matches(it) }) {
            "最多添加 3 张图片，请重新选择。"
        }
    }
}

enum class FeedbackVisibility { PUBLIC, ORGANIZER_ONLY }
internal val photoIdPattern = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")

/** A read DTO intentionally contains no author ID or avatar when anonymous. */
data class VisibleFeedback(val authorName: String, val anonymous: Boolean, val own: Boolean,
    val rating: Int, val comment: String, val createdAt: Long, val details: FeedbackDetails)

fun PrototypeFeedback.visibleTo(viewer: String, organizer: String): VisibleFeedback? {
    if (viewer.isBlank() || (details.visibility == FeedbackVisibility.ORGANIZER_ONLY && viewer != userId && viewer != organizer)) return null
    val name = if (details.anonymous) "匿名参与者" else prototypeParticipants.find { it.userId == userId }?.name ?: "参与者"
    return VisibleFeedback(name, details.anonymous, viewer == userId, rating, comment, createdAt, details)
}

data class FeedbackTag(val id: String, val label: String, val improvement: Boolean = false)
val feedbackTags = listOf(
    FeedbackTag("clear_meeting", "集合清楚"), FeedbackTag("well_organized", "安排有序"),
    FeedbackTag("welcoming", "氛围友好"), FeedbackTag("beginner_friendly", "新手友好"),
    FeedbackTag("helpful_guidance", "指导耐心"), FeedbackTag("comfortable_venue", "场地舒适"),
    FeedbackTag("good_pace", "节奏合适"), FeedbackTag("join_again", "还想参加"),
    FeedbackTag("clearer_meeting", "集合指引", true), FeedbackTag("better_timing", "时间安排", true),
    FeedbackTag("more_guidance", "入门讲解", true), FeedbackTag("better_venue", "场地条件", true),
    FeedbackTag("more_equipment", "器材准备", true), FeedbackTag("more_interaction", "互动机会", true),
)
