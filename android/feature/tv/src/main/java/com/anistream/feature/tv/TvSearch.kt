package com.anistream.feature.tv

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.DisplayHeavy
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.domain.model.AnimeSearchResult
import com.anistream.feature.search.SearchViewModel

private val Violet = Color(0xFF5B3FA0)
private val KeyBg = Color(0xFF1A1B24)

private val animeTypes = listOf("TV", "Movie", "OVA", "ONA", "Special")
private val mediaTypes = listOf("Movie", "Series")
private val articles = listOf("the ", "a ", "an ")

/** Rest of the top result's title when it starts with [query] (case-insensitive, leading punctuation/article ignored), else null. */
internal fun autofillTitle(query: String, top: String?): String? {
    val q = query.trimStart().lowercase()
    if (top == null || q.isEmpty()) return null
    val t = top.trimStart { !it.isLetterOrDigit() }
    val noArticle = articles.firstOrNull { t.startsWith(it, ignoreCase = true) }?.let { t.substring(it.length) }
    return listOfNotNull(t, noArticle).firstOrNull { it.length > q.length && it.lowercase().startsWith(q) }
}

/** One focusable thing: [base] at rest, amber when focused (no scale or glow, the Fire Stick 2 GPU can't afford it). */
@Composable
private fun STouch(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = Radius.pill,
    base: Color = Color.White.copy(alpha = 0.07f),
    selected: Boolean = false,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Accent.soft else base,
            contentColor = if (selected) Accent.main else Ink.high,
            focusedContainerColor = Accent.main,
            focusedContentColor = Accent.on,
            pressedContainerColor = Accent.pressed,
            pressedContentColor = Accent.on
        )
    ) { content() }
}

@Composable
private fun SText(text: String, size: Int, modifier: Modifier = Modifier) =
    Text(text, style = TvType.body.copy(fontSize = size.sp, lineHeight = (size + 4).sp, color = Color.Unspecified, fontWeight = FontWeight.SemiBold), modifier = modifier, maxLines = 1)

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) =
    STouch(onClick, selected = selected) { SText(text, 12, Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }

@Composable
private fun Key(label: String?, onClick: () -> Unit, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) =
    STouch(onClick, modifier.height(25.dp), RoundedCornerShape(6.dp), KeyBg) {
        Row(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterVertically) {
            if (label != null) SText(label, if (label.length == 1) 14 else 11)
            if (icon != null) {
                if (label != null) Spacer(Modifier.width(4.dp))
                Icon(icon, null, Modifier.height(14.dp).width(14.dp))
            }
        }
    }

@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun TvSearchScreen(onOpen: (id: String) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val s by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    }
    // Fire TV and most Android TVs ship a recognizer; hide the mic where none resolves
    val canVoice = remember { voiceIntent.resolveActivity(context.packageManager) != null }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(vm::onQueryChange)
    }
    val list = if (s.query.isBlank()) (if (s.scope == 1) s.mediaPopular else s.popular) else s.visibleResults
    val suggestion = autofillTitle(s.query, s.visibleResults.firstOrNull()?.title)

    BoxWithConstraints(
        Modifier.fillMaxSize().drawBehind {
            // one soft violet glow top-right; a single static gradient, cheap on weak GPUs
            drawRect(Brush.radialGradient(listOf(Violet.copy(alpha = 0.4f), Color.Transparent), Offset(size.width * 0.7f, size.height * 0.2f), size.width * 0.5f))
        }
    ) {
        val w = maxWidth
        Row(Modifier.fillMaxSize().padding(start = Overscan, top = 16.dp)) {
            Column(Modifier.width(w * 0.34f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val big = (w.value * 0.068f).sp
                val typed = buildAnnotatedString {
                    withStyle(SpanStyle(color = Ink.high)) { append(s.query) }
                    withStyle(SpanStyle(color = Accent.main, fontWeight = FontWeight.Light)) { append("|") }
                    if (suggestion != null) {
                        withStyle(SpanStyle(color = Color.White.copy(alpha = 0.45f), drawStyle = Stroke(width = 2f))) { append(suggestion.substring(s.query.trimStart().length)) }
                    }
                }
                Box(Modifier.fillMaxWidth()) {
                    val style = TextStyle(fontFamily = DisplayHeavy, fontWeight = FontWeight.ExtraBold, fontSize = big, lineHeight = big * 1.05f)
                    if (s.query.isEmpty()) {
                        Text(buildAnnotatedString {
                            withStyle(SpanStyle(color = Accent.main, fontWeight = FontWeight.Light)) { append("|") }
                            withStyle(SpanStyle(color = Color.White.copy(alpha = 0.45f), drawStyle = Stroke(width = 2f))) { append("Search") }
                        }, style = style, maxLines = 1)
                    } else {
                        Text(typed, style = style, maxLines = 2, overflow = TextOverflow.Clip)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.background(Color.White.copy(alpha = 0.07f), Radius.pill).padding(3.dp)) {
                        STouch({ vm.onScopeChange(0) }, selected = s.scope == 0, base = Color.Transparent) { SText("Anime", 11, Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
                        STouch({ vm.onScopeChange(1) }, selected = s.scope == 1, base = Color.Transparent) { SText("Movies & TV", 11, Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
                    }
                    if (canVoice) STouch({ try { voice.launch(voiceIntent) } catch (_: ActivityNotFoundException) { } }) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Mic, null, Modifier.height(14.dp).width(14.dp))
                            Spacer(Modifier.width(6.dp))
                            SText("Voice", 11)
                        }
                    }
                }
                Column(Modifier.focusRestorer(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    keys.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { k -> Key(k, { vm.onQueryChange(s.query + k) }, Modifier.weight(1f)) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Key("Space", { vm.onQueryChange(s.query + " ") }, Modifier.weight(1.6f))
                        Key(null, { vm.onQueryChange(s.query.dropLast(1)) }, Modifier.weight(1f), Icons.AutoMirrored.Outlined.Backspace)
                        Key("Clear", vm::onClearQuery, Modifier.weight(1.3f))
                        // Right on the last key column goes to the results, so accepting lives on this key only
                        Key("Complete", { suggestion?.let(vm::onQueryChange) }, Modifier.weight(2f), Icons.AutoMirrored.Outlined.ArrowForward)
                    }
                }
            }
            Spacer(Modifier.width(w * 0.05f))
            Column(Modifier.weight(1f).padding(end = w * 0.04f)) {
                val types = if (s.scope == 1) mediaTypes else animeTypes
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            when {
                                s.query.isBlank() -> if (s.recentSearches.isEmpty()) "Popular" else "Recent"
                                s.isLoading -> "Searching…"
                                s.error != null -> s.error!!
                                list.isEmpty() -> "No results"
                                else -> if (list.size == 1) "1 result" else "${list.size} results"
                            },
                            style = TvType.title.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (s.error != null) Chip("Retry", false) { vm.onQueryChange(s.query) }
                    }
                    if (s.query.isNotBlank()) Row(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Chip("All", s.typeFilter == null) { vm.onTypeFilterChange(null) }
                        types.forEach { t -> Chip(t, s.typeFilter.equals(t, true)) { vm.onTypeFilterChange(t) } }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.focusRestorer(),
                    contentPadding = PaddingValues(top = 6.dp, start = 4.dp, end = 4.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (s.query.isBlank() && s.recentSearches.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    s.recentSearches.forEach { r ->
                                        STouch({ vm.onRecentSearchClick(r) }, Modifier.drawBehind {
                                            drawLine(Ink.ghost, Offset(1f, 0f), Offset(1f, size.height), 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
                                        }, RoundedCornerShape(6.dp), Color(0xFF1D1B26)) {
                                            SText("↺  $r", 12, Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                                        }
                                    }
                                    Chip("Clear recents", false, vm::onClearRecents)
                                }
                                Text("Popular", style = TvType.title.copy(fontSize = 16.sp), modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                    items(list, key = { it.slug }) { r ->
                        Poster(r, onClick = { onOpen(r.slug) }, onFocused = { if (r == list.lastOrNull() && s.query.isNotBlank()) vm.loadNextPage() })
                    }
                }
            }
        }
    }
}

@Composable
private fun Poster(r: AnimeSearchResult, onClick: () -> Unit, onFocused: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() },
            shape = ClickableSurfaceDefaults.shape(Radius.md),
            colors = ClickableSurfaceDefaults.colors(containerColor = Bg.raised, focusedContainerColor = Bg.raised),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(FocusRing, shape = Radius.md))
        ) {
            Box(Modifier.fillMaxSize()) {
                if (r.posterUrl != null) AsyncImage(r.posterUrl, r.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (focused) Box(Modifier.fillMaxSize().background(FocusLift))
            }
        }
        Text(r.title, style = TvType.body.copy(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        listOfNotNull(r.type, r.year?.toString()).joinToString(", ").takeIf { it.isNotEmpty() }?.let {
            Text(it, style = TvType.body.copy(fontSize = 11.sp, lineHeight = 14.sp, color = Ink.muted), maxLines = 1)
        }
    }
}
