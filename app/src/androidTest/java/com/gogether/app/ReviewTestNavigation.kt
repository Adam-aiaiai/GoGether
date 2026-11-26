package com.gogether.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Follow the same optional-detail and privacy entry points as a user. */
internal fun ComposeTestRule.reviewNode(tag: String): SemanticsNodeInteraction {
    fun exists(value: String) = onAllNodesWithTag(value).fetchSemanticsNodes().isNotEmpty()
    fun scrollTo(value: String) {
        // Prefetched lazy nodes can exist without being placed; scroll the list first.
        onNodeWithTag("history_detail").performScrollToNode(hasTestTag(value))
        onNodeWithTag(value).performScrollTo()
    }
    val visibility = tag.startsWith("feedback_visibility_") && tag != "feedback_visibility_done"
    val optional = tag.startsWith("feedback_organization_") || tag.startsWith("feedback_venue_") ||
        tag.startsWith("feedback_atmosphere_") || tag.startsWith("feedback_tag_") || tag in setOf("feedback_clear_dimensions", "feedback_positive_comment", "feedback_improvement_comment")
    if (exists("feedback_visibility_done") && !visibility) onNodeWithTag("feedback_visibility_done").performClick()
    if (exists("feedback_details_done") && !optional) onNodeWithTag("feedback_details_done").performClick()
    if (optional && !exists("feedback_details_done") && exists("feedback_editor")) {
        scrollTo("feedback_open_details")
        onNodeWithTag("feedback_open_details").performClick()
    }
    if (tag == "feedback_clear_dimensions" && !exists(tag)) onNodeWithTag("feedback_more").performClick()
    if (visibility && !exists("feedback_visibility_done")) {
        scrollTo("feedback_visibility")
        onNodeWithTag("feedback_visibility").assertIsDisplayed().performClick()
    }
    if (!visibility && tag != "feedback_clear_dimensions" && exists("history_detail") &&
        tag !in setOf("feedback_submit", "cloud_submit_feedback", "feedback_draft_status")) {
        scrollTo(tag)
    }
    return onNodeWithTag(tag)
}
