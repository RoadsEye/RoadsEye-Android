package com.roadseye.dashcam

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.roadseye.dashcam.ui.theme.LocalAppColors

// Set when the user picks "Don't show again" on the launch notice.
val HIDE_OPEN_SOURCE_NOTICE_KEY = booleanPreferencesKey("hide_open_source_notice")

// Open source details
object OpenSourceInfo {
    const val REPOSITORY_URL = "https://github.com/RoadsEye/RoadsEye-Android"
    const val REPOSITORY_DISPLAY_TEXT = "github.com/RoadsEye/RoadsEye-Android"

    /** "OK" only hides the launch notice until the app process restarts. */
    var dismissedThisSession = false

    fun openRepository(context: Context) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, REPOSITORY_URL.toUri()))
        } catch (_: Exception) {
            // No browser installed; nothing else to fall back to.
        }
    }
}

// Open source announcement
/** @param isLaunchNotice true for the automatic launch showing, which offers "Don't show again". */
@Composable
fun OpenSourceNoticeDialog(
    onDismiss: () -> Unit,
    onDontShowAgain: () -> Unit = {},
    isLaunchNotice: Boolean = true
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current

    Dialog(
        onDismissRequest = { /* Don't let a stray tap dismiss the note */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .border(1.5.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(top = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Code,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(34.dp)
                )

                Text(
                    text = "Road's Eye Is Now Open Source",
                    color = colors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "The full source code for Road's Eye is now public on GitHub. Anyone can read it, learn from it, build it themselves, or keep it going.",
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Text(
                    text = "From now on, there will most likely be no more updates. Check GitHub for more info.",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.accent.copy(alpha = 0.12f))
                        .clickable { OpenSourceInfo.openRepository(context) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = OpenSourceInfo.REPOSITORY_DISPLAY_TEXT,
                        color = colors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open in browser",
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(color = colors.divider, thickness = 1.dp)

            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = if (isLaunchNotice) "OK" else "Close",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (isLaunchNotice) {
                    TextButton(
                        onClick = onDontShowAgain,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Don't show again",
                            color = colors.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
