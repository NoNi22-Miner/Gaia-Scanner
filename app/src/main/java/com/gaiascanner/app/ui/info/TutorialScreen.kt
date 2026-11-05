package com.gaiascanner.app.ui.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gaiascanner.app.R
import com.gaiascanner.app.appContainer
import kotlinx.coroutines.launch

/** A few swipe screens. The text is a placeholder for the owner to review (phase 3). */
private data class TutorialPage(val icon: Int, val title: Int, val body: Int)

private val PAGES = listOf(
    TutorialPage(R.drawable.ic_camera, R.string.tutorial_scan_title, R.string.tutorial_scan_body),
    TutorialPage(R.drawable.ic_gallery, R.string.tutorial_edit_title, R.string.tutorial_edit_body),
    TutorialPage(R.drawable.ic_sort, R.string.tutorial_convert_title, R.string.tutorial_convert_body),
    TutorialPage(R.drawable.ic_folder, R.string.tutorial_library_title, R.string.tutorial_library_body),
)

@Composable
fun TutorialScreen(onDone: () -> Unit) {
    val container = LocalContext.current.appContainer
    // Shown once at first launch: remember that it was seen as soon as it opens.
    LaunchedEffect(Unit) { container.settings.update { it.copy(tutorialSeen = true) } }
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDone) { Text(stringResource(R.string.action_skip)) }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                val page = PAGES[index]
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    Icon(
                        painterResource(page.icon),
                        contentDescription = null,
                        modifier = Modifier.size(96.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(page.title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(stringResource(page.body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                repeat(PAGES.size) { i ->
                    val color = if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                }
            }
            val last = pager.currentPage == PAGES.lastIndex
            Button(
                onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (last) R.string.action_get_started else R.string.action_next))
            }
        }
    }
}
