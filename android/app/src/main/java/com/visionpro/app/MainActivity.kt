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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val API_BASE = "https://vision-pro-api-lexp.onrender.com"
private val Void = Color(0xFF08090D)
private val SurfaceDark = Color(0xFF14151D)
private val SurfaceRaised = Color(0xFF1D1E28)
private val NeonRed = Color(0xFFFF3048)
private val Muted = Color(0xFF9A9BA8)

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
    var catalogType by remember { mutableStateOf<String?>("movie") }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<VideoItem?>(null) }

    LaunchedEffect(catalogType) {
        val type = catalogType
        if (type == null) {
            videos = emptyList()
            loading = false
            error = null
        } else {
            loading = true
            error = null
            val result = loadCatalog(type)
            result.onSuccess { videos = it }.onFailure {
                videos = emptyList()
                error = it.message ?: "No se pudo conectar con el servidor"
            }
            loading = false
        }
    }

    fun choose(item: String) {
        section = item
        catalogType = when (item) {
            "TV EN VIVO" -> "live"
            "SERIES" -> "series"
            "PELÍCULAS", "INICIO" -> "movie"
            else -> null
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = NeonRed, secondary = Color(0xFFFF7180),
        background = Void, surface = SurfaceDark, onBackground = Color.White
    )) {
        if (playing != null) {
            VideoPlayerScreen(item = playing!!, onBack = { playing = null })
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().background(Void)) {
                val compact = maxWidth < 650.dp
                if (compact) {
                    Column(Modifier.fillMaxSize()) {
                        Header(compact = true)
                        NavigationBar(section, ::choose, compact = true)
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
                        ) {
                            HomeContent(section, videos, loading, error, compact = true,
                                onOpen = { video ->
                                    if (video.streamUrl.startsWith("https://")) playing = video
                                    else error = "Este contenido todavía no tiene un enlace HTTPS válido."
                                })
                        }
                    }
                } else {
                    Row(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.width(238.dp).fillMaxHeight()
                                .background(Color(0xFF0D0E14)).padding(20.dp)
                        ) {
                            Header(compact = false)
                            Spacer(Modifier.height(38.dp))
                            NavigationBar(section, ::choose, compact = false)
                            Spacer(Modifier.weight(1f))
                            Text("VISION PRO  •  BETA", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
                        }
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            HomeContent(section, videos, loading, error, compact = false,
                                onOpen = { video ->
                                    if (video.streamUrl.startsWith("https://")) playing = video
                                    else error = "Este contenido todavía no tiene un enlace HTTPS válido."
                                })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(compact: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = if (compact) 18.dp else 0.dp, vertical = 18.dp)) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(listOf(NeonRed, Color(0xFF8A1025)))),
            contentAlignment = Alignment.Center
        ) {
            Text("V", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text("VISION", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("PRO  /  STREAMING", color = NeonRed, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        }
        if (compact) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(34.dp).clip(CircleShape).background(SurfaceRaised), contentAlignment = Alignment.Center) {
                Text("⌕", color = Color.White, fontSize = 23.sp)
            }
        }
    }
}

@Composable
private fun NavigationBar(section: String, onSelect: (String) -> Unit, compact: Boolean) {
    val entries = listOf("INICIO", "TV EN VIVO", "PELÍCULAS", "SERIES", "MI ESPACIO")
    if (compact) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            entries.forEach { item -> NavPill(item, section == item, onSelect) }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            entries.forEach { item -> NavPill(item, section == item, onSelect, wide = true) }
        }
    }
}

@Composable
private fun NavPill(item: String, selected: Boolean, onSelect: (String) -> Unit, wide: Boolean = false) {
    Row(
        Modifier.then(if (wide) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) NeonRed.copy(alpha = .15f) else Color.Transparent)
            .clickable { onSelect(item) }.focusable()
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) Box(Modifier.width(3.dp).height(18.dp).clip(CircleShape).background(NeonRed))
        if (selected) Spacer(Modifier.width(10.dp))
        Text(item, color = if (selected) Color.White else Muted,
            fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 1.sp)
    }
}

@Composable
private fun HomeContent(
    section: String,
    videos: List<VideoItem>,
    loading: Boolean,
    error: String?,
    compact: Boolean,
    onOpen: (VideoItem) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = if (compact) 17.dp else 34.dp, vertical = 24.dp)) {
        Text("TU UNIVERSO DE ENTRETENIMIENTO", color = NeonRed, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Text(if (section == "INICIO") "Explora. Disfruta. Repite." else section,
            color = Color.White, fontSize = if (compact) 27.sp else 35.sp,
            fontWeight = FontWeight.ExtraBold, letterSpacing = (-.7).sp)
        Spacer(Modifier.height(20.dp))

        if (section == "MI ESPACIO") {
            EmptyPanel("TU ESPACIO PERSONAL", "Tus favoritos y tu historial aparecerán aquí cuando estas funciones estén habilitadas.")
            return@Column
        }

        if (section == "INICIO") {
            HeroPanel(compact)
            Spacer(Modifier.height(28.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (section == "INICIO") "Recomendado para ti" else "Catálogo",
                    color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Contenido disponible en VISION PRO", color = Muted, fontSize = 11.sp)
            }
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(NeonRed.copy(alpha = .13f)).padding(horizontal = 11.dp, vertical = 7.dp)) {
                Text(if (loading) "CARGANDO" else "${videos.size} TÍTULOS",
                    color = NeonRed, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
        Spacer(Modifier.height(15.dp))
        when {
            loading -> Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonRed)
            }
            error != null -> EmptyPanel("NO SE PUDO CARGAR", error)
            videos.isEmpty() -> EmptyPanel(
                "LA PANTALLA ESTÁ LISTA",
                "Todavía no hay contenido publicado. Cuando agregues canales o vídeos propios, de dominio público o con licencia, aparecerán aquí."
            )
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                items(videos, key = { it.id }) { video -> VideoCard(video, onOpen) }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("DISEÑADO PARA TU PANTALLA", color = Muted, fontSize = 9.sp, letterSpacing = 1.8.sp)
    }
}

@Composable
private fun HeroPanel(compact: Boolean) {
    Box(
        Modifier.fillMaxWidth().height(if (compact) 205.dp else 270.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF42101B), Color(0xFF1B1018), Color(0xFF11131C))))
    ) {
        Box(Modifier.fillMaxSize().background(
            Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .05f), Color.Black.copy(alpha = .72f)))
        ))
        Column(
            Modifier.align(Alignment.CenterStart).padding(if (compact) 19.dp else 30.dp)
                .fillMaxWidth(.82f),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(NeonRed))
                Spacer(Modifier.width(7.dp))
                Text("EXPERIENCIA PREMIUM", color = Color(0xFFFFA1AA), fontSize = 9.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
            }
            Text("Tu próxima historia
comienza aquí.", color = Color.White,
                fontSize = if (compact) 23.sp else 31.sp, fontWeight = FontWeight.ExtraBold,
                lineHeight = if (compact) 27.sp else 36.sp)
            Text("Películas, series y televisión en una sola experiencia.",
                color = Color(0xFFD5D2D5), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(
                Modifier.clip(RoundedCornerShape(9.dp))
                    .background(NeonRed).padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("DESCUBRIR  ›", color = Color.White, fontSize = 9.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
        Text("VP", Modifier.align(Alignment.BottomEnd).padding(15.dp),
            color = Color.White.copy(alpha = .07f), fontSize = if (compact) 52.sp else 76.sp,
            fontWeight = FontWeight.Black, letterSpacing = (-5).sp)
    }
}

@Composable
private fun VideoCard(video: VideoItem, onOpen: (VideoItem) -> Unit) {
    Column(
        Modifier.width(154.dp).clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark).clickable { onOpen(video) }.focusable()
    ) {
        Box(
            Modifier.fillMaxWidth().height(112.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF40131D), Color(0xFF20212C)))),
            contentAlignment = Alignment.Center
        ) {
            Text("▶", color = Color.White.copy(alpha = .75f), fontSize = 28.sp)
            Box(Modifier.align(Alignment.TopStart).padding(8.dp)
                .clip(RoundedCornerShape(5.dp)).background(NeonRed).padding(horizontal = 6.dp, vertical = 3.dp)) {
                Text("VISION", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black)
            }
        }
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(video.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (video.year.isNotBlank()) video.year else "VER DETALLES",
                color = Muted, fontSize = 9.sp, letterSpacing = .8.sp)
        }
    }
}

@Composable
private fun EmptyPanel(title: String, message: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .background(SurfaceDark).padding(19.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(message, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
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
        Text("‹  VOLVER     ${item.title}", color = Color.White,
            modifier = Modifier.clickable(onClick = onBack).focusable().padding(16.dp))
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
            update = { it.player = player },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}
