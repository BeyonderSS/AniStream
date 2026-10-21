package com.anistream.feature.movies

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anistream.core.common.Result
import com.anistream.core.ui.components.EmptyState
import com.anistream.core.ui.components.PrimaryButton
import com.anistream.core.ui.components.RowSkeleton
import com.anistream.core.ui.components.TextAction
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.core.ui.theme.Space
import com.anistream.domain.model.Addon
import com.anistream.domain.repository.AddonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddState(val busy: Boolean = false, val error: String? = null, val done: Boolean = false)

@HiltViewModel
class AddonManagerViewModel @Inject constructor(private val repo: AddonRepository) : ViewModel() {
    /** null until the first emission (skeleton). */
    val addons: StateFlow<List<Addon>?> = repo.addons().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _add = MutableStateFlow(AddState())
    val add: StateFlow<AddState> = _add.asStateFlow()

    fun add(url: String) {
        if (url.isBlank() || _add.value.busy) return
        viewModelScope.launch {
            _add.value = AddState(busy = true)
            _add.value = when (val r = repo.add(url.trim())) {
                is Result.Success -> AddState(done = true)
                is Result.Error -> AddState(error = r.message ?: "Couldn't add this addon")
                Result.Loading -> AddState()
            }
        }
    }

    fun resetAdd() { _add.value = AddState() }
    fun setEnabled(id: String, enabled: Boolean) = viewModelScope.launch { repo.setEnabled(id, enabled) }
    fun remove(id: String) = viewModelScope.launch { repo.remove(id) }
}

@Composable
fun AddonManagerScreen(onBack: () -> Unit, viewModel: AddonManagerViewModel = hiltViewModel()) {
    val addons by viewModel.addons.collectAsStateWithLifecycle()
    val add by viewModel.add.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(add.done) { if (add.done) { dialog = false; viewModel.resetAdd() } }

    Column(Modifier.fillMaxSize().background(Bg.base)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(56.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Ink.high) }
            Text("Addons", style = AppType.heading)
        }
        Text(
            "Stremio addons add extra stream sources to Movies & TV.",
            style = AppType.caption,
            modifier = Modifier.padding(horizontal = Space.lg)
        )
        val list = addons
        Box(Modifier.weight(1f)) {
            when {
                list == null -> Column(Modifier.padding(top = Space.md)) { repeat(3) { RowSkeleton() } }
                list.isEmpty() -> EmptyState(message = "No addons yet", subtitle = "Add a Stremio manifest URL", icon = Icons.Outlined.Extension)
                else -> LazyColumn(
                    contentPadding = PaddingValues(Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    items(list, key = { it.id }) { a ->
                        AddonRow(a, onToggle = { viewModel.setEnabled(a.id, it) }, onRemove = { viewModel.remove(a.id) })
                    }
                }
            }
        }
        PrimaryButton(
            text = "Add addon",
            icon = Icons.Rounded.Add,
            onClick = { viewModel.resetAdd(); dialog = true },
            modifier = Modifier.fillMaxWidth().padding(Space.lg)
        )
    }

    if (dialog) AddUrlDialog(
        title = "Add addon",
        busy = add.busy,
        error = add.error,
        onDismiss = { dialog = false; viewModel.resetAdd() },
        onConfirm = viewModel::add
    )
}

@Composable
private fun AddonRow(a: Addon, onToggle: (Boolean) -> Unit, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.lg)
            .background(Bg.raised)
            .padding(start = Space.lg, top = Space.md, bottom = Space.md, end = Space.sm)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.name, style = AppType.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (a.builtIn) {
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        "Built-in",
                        style = AppType.label.copy(color = Ink.muted),
                        modifier = Modifier.background(Bg.overlay, Radius.pill).padding(horizontal = Space.sm, vertical = 2.dp)
                    )
                }
            }
            val desc = a.description
            if (!desc.isNullOrBlank()) Text(desc, style = AppType.caption.copy(color = Ink.body), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(a.manifestUrl, style = AppType.caption.copy(color = Ink.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Switch(
            checked = a.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Accent.on,
                checkedTrackColor = Accent.main,
                checkedBorderColor = Accent.main,
                uncheckedThumbColor = Ink.muted,
                uncheckedTrackColor = Bg.overlay,
                uncheckedBorderColor = Color.Transparent
            )
        )
        if (!a.builtIn) {
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, contentDescription = "Remove ${a.name}", tint = Ink.muted) }
        }
    }
}

/** Manifest URL entry: URL field + paste, inline error. */
@Composable
fun AddUrlDialog(
    title: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initialUrl: String = ""
) {
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Radius.xl,
        containerColor = Bg.overlay,
        title = { Text(title, style = AppType.heading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    shape = Radius.sm,
                    placeholder = { Text("https://…/manifest.json", style = AppType.body, color = Ink.faint) },
                    textStyle = AppType.body.copy(color = Ink.high),
                    trailingIcon = {
                        IconButton(onClick = { clipboard.getText()?.text?.let { url = it.trim() } }) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste", tint = Ink.muted)
                        }
                    },
                    colors = urlFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) Text(error, style = AppType.caption.copy(color = Accent.danger))
            }
        },
        confirmButton = {
            TextAction(text = if (busy) "Adding…" else "Add", onClick = { if (!busy) onConfirm(url) }, color = Accent.text)
        },
        dismissButton = { TextAction(text = "Cancel", onClick = onDismiss) }
    )
}

@Composable
fun urlFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent.main,
    unfocusedBorderColor = Ink.hairline,
    cursorColor = Accent.main,
    focusedContainerColor = Bg.raised,
    unfocusedContainerColor = Bg.raised
)
