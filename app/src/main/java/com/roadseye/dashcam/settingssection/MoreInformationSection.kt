package com.roadseye.dashcam.settingssection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.roadseye.dashcam.BuildConfig
import com.roadseye.dashcam.LegalDocument
import com.roadseye.dashcam.LegalDocumentsDialog
import com.roadseye.dashcam.OpenSourceInfo
import com.roadseye.dashcam.OpenSourceNoticeDialog
import com.roadseye.dashcam.ui.theme.LocalAppColors

@Composable
fun MoreInformationSection() {
    val context = LocalContext.current
    val colors = LocalAppColors.current

    var legalDocumentToShow by remember { mutableStateOf<LegalDocument?>(null) }
    var showOpenSourceNotice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape = RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        InfoRow(
            title = "Terms of Service",
            subtitle = "Read the full terms inside the app",
            onClick = { legalDocumentToShow = LegalDocument.TERMS }
        )
        HorizontalDivider(color = colors.divider, thickness = 1.dp)

        InfoRow(
            title = "Privacy Policy",
            subtitle = "Read the full policy inside the app",
            onClick = { legalDocumentToShow = LegalDocument.PRIVACY }
        )
        HorizontalDivider(color = colors.divider, thickness = 1.dp)

        InfoRow(
            title = "Source Code on GitHub",
            subtitle = "No more updates are planned. Check GitHub for news.",
            onClick = { OpenSourceInfo.openRepository(context) }
        )
        HorizontalDivider(color = colors.divider, thickness = 1.dp)

        InfoRow(
            title = "Open Source Announcement",
            subtitle = "Road's Eye is now open source",
            onClick = { showOpenSourceNotice = true }
        )
        HorizontalDivider(color = colors.divider, thickness = 1.dp)

        Text(
            text = "App Version",
            color = colors.textPrimary,
            fontSize = 16.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            text = BuildConfig.VERSION_NAME,
            color = colors.textSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 16.dp)
        )
    }

    legalDocumentToShow?.let { document ->
        LegalDocumentsDialog(
            onDismiss = { legalDocumentToShow = null },
            initialDocument = document
        )
    }

    if (showOpenSourceNotice) {
        OpenSourceNoticeDialog(
            onDismiss = { showOpenSourceNotice = false },
            isLaunchNotice = false
        )
    }
}

@Composable
private fun InfoRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = subtitle,
            color = colors.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
