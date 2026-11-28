package com.gogether.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Plain message-list tab, with the same indicator and touch target across screens. */
@Composable
fun AppTextTab(title: String, active: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, unread: Boolean = false) {
    Column(modifier.heightIn(min = 56.dp).semantics { selected = active }
        .clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = if (active) 16.sp else 14.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (unread) {
                Spacer(Modifier.width(5.dp))
                Box(Modifier.size(5.dp).background(Color(0xFFEF534B), CircleShape))
            }
        }
        Spacer(Modifier.height(7.dp))
        Box(Modifier.size(25.dp, 4.dp).background(if (active) AppUi.Primary else Color.Transparent, CircleShape))
    }
}
