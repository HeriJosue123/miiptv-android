@file:OptIn(ExperimentalMaterial3Api::class)

package com.miiptv.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException

private sealed interface LoadState {
    data object Idle : LoadState
    data object Loading : LoadState
    data class Error(val message: String) : LoadState
    data class Ready(val channels: List<Channel>) : LoadState
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("miiptv", Context.MODE_PRIVATE) }

    var playlistUrl by remember { mutableStateOf(prefs.getString("playlist_url", "") ?: "") }
    var reloadKey by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var playing by remember { mutableStateOf<Channel?>(null) }
    var showSettings by remember { mutableStateOf(playlistUrl.isBlank()) }

    LaunchedEffect(playlistUrl, reloadKey) {
        if (playlistUrl.isBlank()) {
            state = LoadState.Idle
            return@LaunchedEffect
        }
        state = LoadState.Loading
        state = try {
            val text = fetchText(playlistUrl)
            val list = M3uParser.parse(text, playlistUrl)
            if (list.isEmpty()) LoadState.Error("La lista no contiene canales")
            else LoadState.Ready(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadState.Error(e.message ?: "Error de red")
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val current = playing
        when {
            current != null -> PlayerScreen(channel = current, onBack = { playing = null })

            showSettings -> SettingsScreen(
                initialUrl = playlistUrl,
                canCancel = playlistUrl.isNotBlank(),
                onSave = { newUrl ->
                    val clean = newUrl.trim()
                    prefs.edit().putString("playlist_url", clean).apply()
                    playlistUrl = clean
                    reloadKey++
                    showSettings = false
                },
                onCancel = { showSettings = false },
            )

            else -> ChannelsScreen(
                state = state,
                onPlay = { playing = it },
                onReload = { reloadKey++ },
                onSettings = { showSettings = true },
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    initialUrl: String,
    canCancel: Boolean,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf(initialUrl) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Mi IPTV", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Pega la URL de tu lista (M3U / M3U8).",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("URL de la lista") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = { onSave(text) }, enabled = text.isNotBlank()) {
                Text("Guardar y cargar")
            }
            if (canCancel) {
                TextButton(onClick = onCancel) { Text("Cancelar") }
            }
        }
    }
}

@Composable
private fun ChannelsScreen(
    state: LoadState,
    onPlay: (Channel) -> Unit,
    onReload: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Canales",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReload) { Text("Recargar") }
            TextButton(onClick = onSettings) { Text("Ajustes") }
        }

        when (state) {
            LoadState.Idle, LoadState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is LoadState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No se pudo cargar la lista", style = MaterialTheme.typography.titleMedium)
                Text(state.message, modifier = Modifier.padding(vertical = 8.dp))
                Button(onClick = onReload) { Text("Reintentar") }
            }

            is LoadState.Ready -> ChannelList(state.channels, onPlay)
        }
    }
}

@Composable
private fun ChannelList(channels: List<Channel>, onPlay: (Channel) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<String?>(null) }

    val groups = remember(channels) { channels.map { it.group }.distinct() }
    val filtered = remember(channels, query, selectedGroup) {
        channels.filter { ch ->
            (selectedGroup == null || ch.group == selectedGroup) &&
                (query.isBlank() || ch.name.contains(query, ignoreCase = true))
        }
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Buscar canal (${channels.size})") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    )

    if (groups.size > 1) {
        LazyRow(
            modifier = Modifier.padding(vertical = 8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = selectedGroup == null,
                    onClick = { selectedGroup = null },
                    label = { Text("Todos") },
                )
            }
            items(groups) { g ->
                FilterChip(
                    selected = selectedGroup == g,
                    onClick = { selectedGroup = if (selectedGroup == g) null else g },
                    label = { Text(g) },
                )
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(filtered) { ch ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlay(ch) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ch.logo != null) {
                    AsyncImage(
                        model = ch.logo,
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(MaterialTheme.shapes.small),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        ch.group,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
