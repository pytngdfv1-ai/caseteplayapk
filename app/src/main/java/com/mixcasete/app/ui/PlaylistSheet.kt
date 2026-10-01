package com.mixcasete.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mixcasete.app.data.SearchResultItem
import com.mixcasete.app.player.PlayerViewModel

/**
 * Hoja inferior (vertical) o panel lateral (horizontal) con la lista del usuario:
 * buscar, agregar, borrar y reordenar. Las listas las crea el usuario; no hay listas impuestas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSheet(
    vm: PlayerViewModel,
    visible: Boolean,
    onDismiss: () -> Unit,
    portrait: Boolean
) {
    if (!visible) return
    val ui by vm.ui.collectAsState()
    val search by vm.search.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = !portrait)
    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Selector de documentos para archivos locales (persistente)
    val openDoc = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            vm.addLocalFile(uri)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Casete — lista", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { showSearch = true }) { Icon(Icons.Default.Add, "Agregar") }
                IconButton(onClick = { openDoc.launch(arrayOf("audio/*")) }) {
                    Icon(Icons.Default.Search, "Archivo local")
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
            }

            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.heightIn(max = if (portrait) 420.dp else 520.dp)
            ) {
                items(ui.tracks, key = { it.id }) { track ->
                    val idx = ui.tracks.indexOf(track)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { vm.playAt(idx); onDismiss() },
                        onClick = { }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(track.title, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    track.artist + if (track.isPreview) " · preview 30s" else "",
                                    maxLines = 1, style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (track.favorite) Icon(Icons.Default.Favorite, "favorita", tint = MaterialTheme.colorScheme.error)
                            IconButton(onClick = { vm.moveTrack(idx, -1) }) { Text("↑") }
                            IconButton(onClick = { vm.moveTrack(idx, +1) }) { Text("↓") }
                            IconButton(onClick = { vm.removeTrack(track.id) }) { Icon(Icons.Default.Delete, "Borrar") }
                        }
                    }
                }
                if (ui.tracks.isEmpty()) {
                    item { Text("Sin pistas todavías. Usa + para buscar o el selector de archivos.", Modifier.padding(16.dp)) }
                }
            }
        }
    }

    if (showSearch) {
        Dialog(onDismissRequest = { showSearch = false }) {
            Card(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        label = { Text("Buscar título / artista") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.doSearch(query) }) { Text("Buscar") }
                        Button(onClick = { showSearch = false }) { Text("Cerrar") }
                    }
                    if (search.loading) Text("Buscando…", Modifier.padding(top = 8.dp))
                    search.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(search.results) { item ->
                            SearchResultRow(item) { vm.addSearchResult(item) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(item: SearchResultItem, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.title, maxLines = 1)
            Text(item.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onAdd) { Text("Agregar") }
    }
}

/** Panel de registro de errores visible solo en modo debug/calibración. */
@Composable
fun DebugLogPanel(vm: PlayerViewModel, log: List<String>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier.padding(8.dp)) {
        Card {
            Column(Modifier.padding(8.dp)) {
                Text("Debug (${log.size})", style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clickable { expanded = !expanded })
                if (expanded) {
                    LazyColumn(Modifier.heightIn(max = 160.dp)) {
                        items(log.reversed()) { Text(it, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
    }
}

/** Botonera discreta inferior: lista, compartir TV, login YouTube. */
@Composable
fun TopControls(
    vm: PlayerViewModel,
    sharing: Boolean,
    modifier: Modifier = Modifier,
    onToggleImmersive: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButtonRetro("LISTA") { vm.showPlaylist() }
        TextButtonRetro(if (sharing) "TV ●" else "TV") { TvShareBus.toggle(context) }
        TextButtonRetro("LOGIN") {
            YouTubeLoginBus.open(context)
        }
        TextButtonRetro("CAL") { vm.toggleCalibration() }
    }
}

@Composable
private fun TextButtonRetro(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}
