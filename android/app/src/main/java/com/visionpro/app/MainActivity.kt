package com.visionpro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val API_BASE = "https://vision-pro-api-lexp.onrender.com"
private val Navy = Color(0xFF080E1B)
private val Panel = Color(0xFF151F32)
private val Accent = Color(0xFF35D0B0)

data class VideoItem(
    val id: String,
    val title: String,
    val description: String,
    val posterUrl: String,
    val streamUrl: String,
    val year: String
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
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) error("API respondió ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONObject(body).optJSONArray("items") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(VideoItem(
                        id = item.optString("id"),
                        title = item.optString("title", "Sin título"),
                        description = item.optString("description"),
                        posterUrl = item.optString("poster_url"),
                        streamUrl = item.optString("stream_url"),
                        year = item.optString("release_year")
                    ))
                }
            }
        } finally { connection.disconnect() }
    }
}

@Composable
fun VisionApp() {
    var section by remember { mutableStateOf("Inicio") }
    var catalogType by remember { mutableStateOf("movie") }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<VideoItem?>(null) }

    LaunchedEffect(catalogType) {
        loading = true
        error = null
        val result = loadCatalog(catalogType)
        result.onSuccess { videos = it }.onFailure {
            videos = emptyList()
            error = it.message ?: "No se pudo conectar con el servidor"
        }
        loading = false
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Navy, surface = Panel)) {
        if (playing != null) {
            VideoPlayerScreen(item = playing!!, onBack = { playing = null })
        } else {
            Row(Modifier.fillMaxSize().background(Navy)) {
                Column(
                    Modifier.width(190.dp).fillMaxHeight().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text("VISION PRO", color = Accent, fontSize = 24.sp)
                    Text("ENTRETENIMIENTO", color = Color.LightGray, fontSize = 10.sp)
                    Spacer(Modifier.height(18.dp))
                    listOf("Inicio", "TV en vivo", "Películas", "Series", "Mi espacio").forEach { item ->
                        val selected = section == item
                        Surface(
                            color = if (selected) Accent.copy(alpha = .18f) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                section = item
                                catalogType = when (item) {
                                    "TV en vivo" -> "live"
                                    "Series" -> "series"
                                    else -> "movie"
                                }
                            }.focusable()
                        ) {
                            Text(item, modifier = Modifier.padding(13.dp), color = if (selected) Accent else Color.White)
                        }
                    }
                }
                Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
                    Text("Hola, bienvenido", color = Color.Gray, fontSize = 13.sp)
                    Text(section, color = Color.White, fontSize = 32.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Tu entretenimiento, en un solo lugar.", color = Color.LightGray)
                    Spacer(Modifier.height(28.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (section == "Inicio") "Catálogo destacado" else "Contenido disponible", color = Color.White, fontSize = 20.sp)
                        Spacer(Modifier.weight(1f))
                        Text(if (loading) "Cargando…" else "${videos.size} títulos", color = Accent, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    when {
                        loading -> CircularProgressIndicator(color = Accent)
                        error != null -> Text("No se pudo cargar el catálogo: $error", color = Color(0xFFFFB4AB))
                        videos.isEmpty() -> Column(
                            Modifier.fillMaxWidth().padding(top = 22.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Todavía no hay contenido publicado.", color = Color.White, fontSize = 18.sp)
                            Text("Agrega canales o vídeos propios, de dominio público o con licencia desde tu catálogo autorizado.", color = Color.LightGray)
                        }
                        else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(videos, key = { it.id }) { video ->
                                Surface(
                                    color = Panel,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.width(220.dp).height(156.dp).clickable {
                                        if (video.streamUrl.startsWith("https://")) playing = video
                                        else error = "Este título todavía no tiene un enlace HTTPS de reproducción."
                                    }.focusable()
                                ) {
                                    Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.Bottom) {
                                        Text(video.title, color = Color.White, fontSize = 17.sp, maxLines = 2)
                                        if (video.year.isNotBlank()) Text(video.year, color = Accent, fontSize = 12.sp)
                                        Text("Abrir contenido  ›", color = Color.LightGray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("VISION PRO · Android TV y móvil", color = Color.Gray, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerScreen(item: VideoItem, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember(item.streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(item.streamUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Text(
            "‹  VOLVER     ${item.title}",
            color = Color.White,
            modifier = Modifier.clickable(onClick = onBack).focusable().padding(16.dp)
        )
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
            update = { it.player = player },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}
