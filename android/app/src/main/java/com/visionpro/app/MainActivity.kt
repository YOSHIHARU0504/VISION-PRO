package com.visionpro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val API_BASE = "https://vision-pro-api-lexp.onrender.com"
private val Ink = Color(0xFF08090D)
private val Panel = Color(0xFF12141B)
private val Panel2 = Color(0xFF1B1E28)
private val Red = Color(0xFFFF3048)
private val White = Color(0xFFF7F7FA)
private val Gray = Color(0xFF9A9EAA)

data class VideoItem(
    val id: String, val title: String, val description: String,
    val posterUrl: String, val backdropUrl: String, val streamUrl: String, val year: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VisionApp() }
    }
}

private suspend fun loadCatalog(type: String): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
    runCatching {
        val connection = (URL("$API_BASE/api/catalog/$type").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 15000; readTimeout = 20000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) error("Servidor: HTTP ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONObject(body).optJSONArray("items") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val v = array.optJSONObject(i) ?: continue
                    add(VideoItem(v.optString("id"), v.optString("title", "Sin título"),
                        v.optString("description"), v.optString("poster_url"),
                        v.optString("backdrop_url"), v.optString("stream_url"), v.optString("release_year")))
                }
            }
        } finally { connection.disconnect() }
    }
}

@Composable
fun VisionApp() {
    var section by remember { mutableStateOf("INICIO") }
    var catalogType by remember { mutableStateOf("movie") }
    var videos by remember { mutableStateOf(emptyList<VideoItem>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<VideoItem?>(null) }

    LaunchedEffect(catalogType) {
        loading = true; error = null
        val result = loadCatalog(catalogType)
        result.onSuccess { videos = it }.onFailure { videos = emptyList(); error = it.message ?: "No se pudo conectar" }
        loading = false
    }

    fun navigate(label: String) {
        section = label
        catalogType = when (label) { "TV EN VIVO" -> "live"; "SERIES" -> "series"; else -> "movie" }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Red, secondary = Color(0xFFFF7584), background = Ink, surface = Panel)) {
        if (playing != null) PlayerScreen(playing!!, { playing = null })
        else BoxWithConstraints(Modifier.fillMaxSize().background(Ink)) {
            val compact = maxWidth < 700.dp
            if (compact) {
                Column(Modifier.fillMaxSize()) {
                    TopBar()
                    Navigation(section, ::navigate, true)
                    CatalogBody(section, videos, loading, error, true, { playing = it }, { 
                        loading = true
                    }, Modifier.weight(1f))
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(230.dp).fillMaxHeight().background(Color(0xFF0D0F15)).padding(22.dp)) {
                        Brand()
                        Spacer(Modifier.height(44.dp))
                        Navigation(section, ::navigate, false)
                        Spacer(Modifier.weight(1f))
                        Text("CINE • SERIES • LIVE", color = Gray, fontSize = 9.sp, letterSpacing = 1.5.sp)
                        Text("VERSION 1.1 BETA", color = Red, fontSize = 9.sp)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        TopBar(showBrand = false)
                        CatalogBody(section, videos, loading, error, false, { playing = it }, { loading = true }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Red, Color(0xFF8D1028)))), contentAlignment = Alignment.Center) {
            Text("VP", color = White, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("VISION PRO", color = White, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Text("ENTERTAINMENT", color = Red, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp)
        }
    }
}

@Composable
private fun TopBar(showBrand: Boolean = true) {
    Row(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showBrand) Brand()
        else Text("DESCUBRE", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Gray, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(34.dp).clip(CircleShape).background(Panel2), contentAlignment = Alignment.Center) {
            Text("Y", color = White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Navigation(section: String, onSelect: (String) -> Unit, compact: Boolean) {
    val items = listOf("INICIO", "TV EN VIVO", "PELÍCULAS", "SERIES", "MI ESPACIO")
    if (compact) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { label -> NavItem(label, section == label, onSelect, false) }
    } else Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        items.forEach { label -> NavItem(label, section == label, onSelect, true) }
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onSelect: (String) -> Unit, wide: Boolean) {
    Row(Modifier.then(if (wide) Modifier.fillMaxWidth() else Modifier).clip(RoundedCornerShape(11.dp))
        .background(if (selected) Red.copy(alpha = .16f) else Color.Transparent)
        .clickable { onSelect(label) }.focusable().padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (selected) { Box(Modifier.width(3.dp).height(18.dp).clip(CircleShape).background(Red)); Spacer(Modifier.width(10.dp)) }
        Text(label, color = if (selected) White else Gray, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, letterSpacing = 1.sp)
    }
}

@Composable
private fun CatalogBody(
    section: String, videos: List<VideoItem>, loading: Boolean, error: String?, compact: Boolean,
    onOpen: (VideoItem) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = if (compact) 16.dp else 34.dp, vertical = 16.dp)) {
        Hero(section, compact, videos.firstOrNull(), onOpen)
        Spacer(Modifier.height(25.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(when (section) { "TV EN VIVO" -> "Canales en vivo"; "SERIES" -> "Series"; "MI ESPACIO" -> "Tu biblioteca"; else -> "Películas para descubrir" },
                    color = White, fontSize = if (compact) 19.sp else 23.sp, fontWeight = FontWeight.ExtraBold)
                Text("Historias seleccionadas para ti", color = Gray, fontSize = 11.sp)
            }
            Text(if (loading) "CARGANDO" else "${videos.size} TÍTULOS", color = Red, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(14.dp))
        when {
            loading -> Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Red) }
            error != null -> InfoPanel("No pudimos conectar", error, "REINTENTAR", onRetry)
            videos.isEmpty() -> InfoPanel("Estamos preparando tu catálogo", "Los títulos publicados aparecerán aquí.", null, {})
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(videos, key = { it.id }) { item -> PosterCard(item, onOpen) }
            }
        }
        Spacer(Modifier.height(25.dp))
        Text("VISION PRO  •  CONTENIDO CON LICENCIA", color = Gray, fontSize = 9.sp, letterSpacing = 1.4.sp)
    }
}

@Composable
private fun Hero(section: String, compact: Boolean, featured: VideoItem?, onOpen: (VideoItem) -> Unit) {
    Box(Modifier.fillMaxWidth().height(if (compact) 235.dp else 330.dp).clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF65182A), Color(0xFF25121C), Color(0xFF11141D))))) {
        if (featured?.backdropUrl?.startsWith("https://") == true) {
            AsyncImage(model = featured.backdropUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF090A0F).copy(alpha=.08f), Color(0xFF090A0F).copy(alpha=.94f)))))
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(.88f).padding(if (compact) 20.dp else 34.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Red))
                Spacer(Modifier.width(8.dp))
                Text("VISION PRO ORIGINAL EXPERIENCE", color = Color(0xFFFFA1AC), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            }
            Text(if (featured != null) featured.title else "Tu entretenimiento.
A tu manera.", color = White,
                fontSize = if (compact) 27.sp else 39.sp, fontWeight = FontWeight.Black, lineHeight = if (compact) 31.sp else 44.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (featured != null) featured.description.ifBlank { "Descubre esta película en VISION PRO." } else "Películas, series y televisión en una experiencia creada para ti.",
                color = Color(0xFFD4D5DC), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Red).clickable(enabled = featured != null) { featured?.let(onOpen) }.focusable()
                .padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = White, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (featured != null) "REPRODUCIR" else "EXPLORAR CATÁLOGO", color = White, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
            }
        }
        Text("VP", Modifier.align(Alignment.BottomEnd).padding(16.dp), color = White.copy(alpha=.08f), fontSize = 72.sp, fontWeight = FontWeight.Black, letterSpacing = (-7).sp)
    }
}

@Composable
private fun PosterCard(item: VideoItem, onOpen: (VideoItem) -> Unit) {
    Column(Modifier.width(160.dp).clip(RoundedCornerShape(15.dp)).background(Panel).clickable { onOpen(item) }.focusable()) {
        Box(Modifier.fillMaxWidth().height(205.dp).background(Brush.linearGradient(listOf(Color(0xFF551728), Panel2))), contentAlignment = Alignment.Center) {
            if (item.posterUrl.startsWith("https://")) AsyncImage(model = item.posterUrl, contentDescription = item.title, modifier = Modifier.fillMaxSize())
            else {
                Text("VP", color = White.copy(alpha=.16f), fontSize = 48.sp, fontWeight = FontWeight.Black)
                Box(Modifier.size(42.dp).clip(CircleShape).background(Red.copy(alpha=.95f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir", tint = White, modifier = Modifier.size(26.dp))
                }
            }
            Box(Modifier.align(Alignment.TopStart).padding(9.dp).clip(RoundedCornerShape(5.dp)).background(Red).padding(horizontal=7.dp, vertical=4.dp)) {
                Text("PELÍCULA", color = White, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing=.8.sp)
            }
        }
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (item.year.isNotBlank()) item.year else "DISPONIBLE AHORA", color = Gray, fontSize = 9.sp)
        }
    }
}

@Composable
private fun InfoPanel(title: String, message: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Panel).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(message, color = Gray, fontSize = 12.sp, lineHeight = 18.sp)
        if (action != null) Text(action, color = Red, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable(onClick = onAction).focusable().padding(top=5.dp))
    }
}

@Composable
private fun PlayerScreen(item: VideoItem, onBack: () -> Unit) {
    val context = LocalContext.current
    var playerError by remember { mutableStateOf<String?>(null) }
    var playbackState by remember { mutableStateOf("Conectando con el servidor…") }
    val player = remember(item.streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    playbackState = when (state) {
                        Player.STATE_BUFFERING -> "Cargando vídeo…"
                        Player.STATE_READY -> "Reproduciendo"
                        Player.STATE_ENDED -> "La película terminó"
                        else -> "Preparando reproducción…"
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    playerError = "No se pudo reproducir: ${error.errorCodeName}. Comprueba tu conexión o el enlace del vídeo."
                }
            })
            setMediaItem(MediaItem.fromUri(item.streamUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().background(Panel).clickable(onClick = onBack).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = White)
            Spacer(Modifier.width(12.dp))
            Text(item.title, color = White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true; keepScreenOn = true } },
                update = { it.player = player }, modifier = Modifier.fillMaxSize())
            if (playerError != null) InfoPanel("Error de reproducción", playerError!!, "VOLVER AL CATÁLOGO", onBack)
        }
        if (playerError == null) Text(playbackState, color = Gray, fontSize = 11.sp, modifier = Modifier.padding(12.dp))
    }
}
