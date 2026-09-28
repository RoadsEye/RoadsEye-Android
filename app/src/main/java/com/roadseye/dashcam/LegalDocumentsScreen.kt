package com.roadseye.dashcam

import android.content.Context
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.roadseye.dashcam.ui.theme.LocalAppColors

// Terms of Service and Privacy Policy bundled in the app's assets.
enum class LegalDocument(
    val title: String,
    /** Short label for the tab strip. */
    val shortTitle: String,
    private val assetName: String
) {
    TERMS("Terms of Service", "Terms", "terms_of_service.txt"),
    PRIVACY("Privacy Policy", "Privacy", "privacy_policy.txt");

    /** Full text from the assets folder, or a fallback message if it can't be read. */
    fun text(context: Context): String = try {
        context.assets.open(assetName).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        "$title could not be loaded from this copy of the app.\n\n" +
            "The full text is also in the source code on GitHub:\n${OpenSourceInfo.REPOSITORY_URL}"
    }
}

// Viewer
@Composable
fun LegalDocumentsDialog(
    onDismiss: () -> Unit,
    initialDocument: LegalDocument = LegalDocument.TERMS
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current

    var selected by remember { mutableStateOf(initialDocument) }
    // A fresh state per document, so switching tabs jumps back to the top.
    val scrollState = remember(selected) { ScrollState(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            // Title bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selected.title,
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = colors.accent
                    )
                }
            }

            // Document picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LegalDocument.entries.forEach { document ->
                    val isSelected = document == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) colors.accent else colors.surface)
                            .clickable { selected = document }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = document.shortTitle,
                            color = if (isSelected) Color.White else colors.textSecondary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Document body
            val body = remember(selected) { selected.text(context) }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = body,
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.divider, RoundedCornerShape(16.dp))
                        .padding(18.dp)
                )
            }
        }
    }
}
