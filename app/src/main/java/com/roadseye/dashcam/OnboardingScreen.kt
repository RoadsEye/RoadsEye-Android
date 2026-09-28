package com.roadseye.dashcam

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.roadseye.dashcam.R

data class OnboardingPage(
    val imageName: String?,
    val title: String,
    val description: String,
    val titleFontSize: Float = 30f
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pages = listOf(
        OnboardingPage(
            imageName = null,
            title = "Welcome to Road’s Eye",
            description = "Your personal dashcam that records continuously and protects every drive.\n\n" +
                    "• Automatic video recording\n" +
                    "• Instant clip saving\n" +
                    "• Crash detection ready",
            titleFontSize = 32f
        ),
        OnboardingPage(
            imageName = "onboarding_start_2",
            title = "Easy Recording",
            description = "• Tap the big red button to start/stop recording\n" +
                    "• Videos save automatically to your Gallery (Movies/RoadsEye)\n" +
                    "• Gear icon opens settings",
            titleFontSize = 28f
        ),
        OnboardingPage(
            imageName = "onboarding_stop_and_clip",
            title = "Save Important Moments",
            description = "• Blue CLIP button instantly saves the last 1–3 minutes\n" +
                    "• Crash Detection auto-saves on impact\n" +
                    "• Oldest clips are deleted when storage is full",
            titleFontSize = 28f
        ),
        OnboardingPage(
            imageName = null,
            title = "Keep the App Running",
            description = "For reliable recording:\n\n" +
                    "• Road’s Eye must stay in the foreground\n" +
                    "• Picture-in-Picture mode is supported\n" +
                    "• Keep your screen on while driving",
            titleFontSize = 28f
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Skip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, end = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onComplete,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF00B0FF))
                ) {
                    Text(
                        "Skip",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp)
            ) { pageIndex ->
                PageView(page = pages[pageIndex])
            }

            // Page indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pages.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 10.dp else 8.dp)
                            .padding(horizontal = 4.dp)
                            .background(
                                color = if (isSelected) Color.White else Color.Gray,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                }
            }

            // Next / Get Started button
            Button(
                onClick = {
                    if (pagerState.currentPage < pages.size - 1) {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onComplete()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0, 168, 255)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = if (pagerState.currentPage == pages.size - 1) "Get Started" else "Next",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun PageView(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        if (page.imageName != null) {
            val imageResId = when (page.imageName) {
                "onboarding_start_2" -> R.drawable.onboarding_start_2
                "onboarding_stop_and_clip" -> R.drawable.onboarding_stop_and_clip
                else -> R.drawable.ic_launcher_foreground
            }

            Image(
                painter = painterResource(id = imageResId),
                contentDescription = page.title,
                modifier = Modifier
                    .size(220.dp)
                    .padding(8.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(80.dp))
        }

        Text(
            text = page.title,
            fontSize = page.titleFontSize.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = page.description,
            fontSize = 16.5.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Preview(showBackground = true)
@Composable
fun OnboardingScreenPreview() {
    MaterialTheme {
        OnboardingScreen(onComplete = {})
    }
}
