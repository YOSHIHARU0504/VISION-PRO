package com.visionpro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF090F1D)
private val Panel = Color(0xFF151F32)
private val Accent = Color(0xFF35D0B0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VisionApp() }
    }
}

@Composable
fun VisionApp() {
    var selected by remember { mutableStateOf("Inicio") }
    val sections = listOf("Inicio", "TV en vivo", "Películas", "Series", "Mi espacio")
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Navy, surface = Panel)) {
        Row(Modifier.fillMaxSize().background(Navy)) {
            Column(Modifier.width(190.dp).fillMaxHeight().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("VISION PRO", color = Accent, fontSize = 24.sp)
                Text("TU ENTRETENIMIENTO", color = Color.LightGray, fontSize = 10.sp)
                Spacer(Modifier.height(18.dp))
                sections.forEach { item ->
                    Surface(color = if (selected == item) Accent.copy(alpha = .18f) else Color.Transparent,
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().clickable { selected = item }) {
                        Text(item, modifier = Modifier.padding(14.dp), color = if (selected == item) Accent else Color.White)
                    }
                }
            }
            Column(Modifier.fillMaxSize().padding(28.dp)) {
                Text(selected, color = Color.White, fontSize = 30.sp)
                Spacer(Modifier.height(8.dp))
                Text("Explora contenido disponible y tus listas autorizadas.", color = Color.LightGray)
                Spacer(Modifier.height(28.dp))
                Text("Destacados", color = Color.White, fontSize = 20.sp)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(listOf("Canales", "Cine", "Series", "Mi lista")) { title ->
                        Surface(color = Panel, shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.width(210.dp).height(130.dp).clickable { selected = title }) {
                            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomStart) {
                                Text(title, color = Color.White, fontSize = 18.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text("Conecta tu cuenta para sincronizar favoritos e historial.", color = Color.Gray)
            }
        }
    }
}
