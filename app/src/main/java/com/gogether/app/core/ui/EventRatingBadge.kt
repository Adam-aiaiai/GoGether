package com.gogether.app.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gogether.app.core.model.EventRatingSummary

val LocalEventRatings=staticCompositionLocalOf<Map<String,EventRatingSummary>> {emptyMap()}

@Composable
fun EventRatingBadge(summary:EventRatingSummary?,eventId:String,modifier:Modifier=Modifier) {
    val average=summary?.displayAverage
    val count=summary?.reviewCount
    val description=when {
        summary==null -> "Rating unavailable"
        average==null -> "No reviews"
        else -> "$average out of 5, $count ${if(count==1) "review" else "reviews"}"
    }
    Column(modifier.widthIn(max=112.dp).testTag("event_rating_$eventId").clearAndSetSemantics {contentDescription=description},
        horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)) {
            Icon(if(average==null) Icons.Outlined.Star else Icons.Filled.Star,contentDescription=null,
                modifier=Modifier.size(16.dp),tint=MaterialTheme.colorScheme.primary)
            Text(average ?: "—",fontSize=13.sp,fontWeight=FontWeight.SemiBold)
        }
        Text(if(count==null) "Unavailable" else if(count==0) "No reviews" else "$count ${if(count==1) "review" else "reviews"}",
            fontSize=10.sp,lineHeight=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EventRatingOverview(eventId:String,summary:EventRatingSummary?=LocalEventRatings.current[eventId]) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Public participant reviews",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        EventRatingBadge(summary,eventId)
    }
}
