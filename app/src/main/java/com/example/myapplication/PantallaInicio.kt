package com.example.myapplication

import android.annotation.SuppressLint
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale

// ── Colores ──────────────────────────────────────────────────
// Quita el "private" de todos los colores
val VerdeOscuro = Color(0xFF1B4332)   // ← sin private
val VerdeBoton  = Color(0xFF40916C)
val VerdeClaro  = Color(0xFFD8F3DC)
val FondoClaro  = Color(0xFFF1F0EB)
val BeigeCard   = Color(0xFFFFFFFF)
// ── Modelo de ítem de navegación ─────────────────────────────
private data class NavItem(val label: String, val icon: ImageVector, val index: Int)

private val NAV_ITEMS = listOf(
    NavItem("Muestras",  Icons.Default.Home,      0),
    NavItem("Dashboard", Icons.Default.Science,   1),
    NavItem("Mapa",      Icons.Default.Map,       2),
    NavItem("Consejos",  Icons.Default.Lightbulb, 3)
)

// ── Navegación principal ─────────────────────────────────────
@Composable
fun PantallaInicio() {
    // rememberSaveable sobrevive rotaciones de pantalla
    var tabSeleccionado by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        containerColor = FondoClaro,
        topBar    = { BarraSuperior() },
        bottomBar = { BarraInferior(tabSeleccionado) { tabSeleccionado = it } },
        floatingActionButton = {
            if (tabSeleccionado == 0) {
                FloatingActionButton(
                    onClick        = { tabSeleccionado = 1 },
                    containerColor = VerdeOscuro,
                    shape          = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (tabSeleccionado) {
                0 -> PantallaCamaraIA()
                1 -> PantallaHome()
                2 -> PantallaMapa()
                3 -> PantallaRecomendaciones()
            }
        }
    }
}

// ── Barra superior ───────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraSuperior() {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = VerdeOscuro),
        title  = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier           = Modifier.size(36.dp).clip(CircleShape).background(VerdeBoton),
                    contentAlignment   = Alignment.Center
                ) { Text("🌾", fontSize = 18.sp) }
                Spacer(modifier = Modifier.width(10.dp))
                Text("SINAPA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        actions = {
            IconButton(onClick = { /* TODO: acción de ubicación */ }) {
                Icon(Icons.Default.LocationOn, contentDescription = "Ubicación", tint = Color.White)
            }
        }
    )
}

// ── Barra inferior ───────────────────────────────────────────
@Composable
fun BarraInferior(seleccionado: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = Color.White) {
        NAV_ITEMS.forEach { item ->
            NavigationBarItem(
                selected = seleccionado == item.index,
                onClick  = { onSelect(item.index) },
                icon     = { Icon(item.icon, contentDescription = item.label) },
                label    = { Text(item.label, fontSize = 11.sp) },
                colors   = NavigationBarItemDefaults.colors(
                    selectedIconColor = VerdeOscuro,
                    selectedTextColor = VerdeOscuro,
                    indicatorColor    = VerdeClaro
                )
            )
        }
    }
}

// ── TTS reutilizable ─────────────────────────────────────────
/**
 * Crea y gestiona el ciclo de vida de un TextToSpeech.
 * Retorna null mientras se inicializa o si falla.
 */
@Composable
fun rememberTTS(locale: Locale = Locale("es", "MX")): TextToSpeech? {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(locale) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(locale)
                // Fallback si el idioma no está disponible
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale("es")
                }
            }
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            tts = null
        }
    }
    return tts
}

// ── HOME / DASHBOARD ─────────────────────────────────────────
@Composable
fun PantallaHome() {
    val climaViewModel: ClimaViewModel = viewModel()
    val humedad by climaViewModel.humedad.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) climaViewModel.cargarClima()
    }

    LaunchedEffect(Unit) {
        launcher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Dashboard de Campo", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = VerdeOscuro)
        Text("Visión general del ecosistema del suelo.", fontSize = 13.sp, color = Color.Gray)

        TarjetaIndiceGlobal()

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TarjetaMiniMetrica(
                modifier  = Modifier.weight(1f),
                titulo    = "Equilibrio pH",
                valor     = "6.4",
                rango     = "Ideal: 6.0 – 7.0",
                progreso  = 0.64f
            )
            TarjetaMiniMetrica(
                modifier  = Modifier.weight(1f),
                titulo    = "Humedad",
                valor     = humedad?.let { "$it%" } ?: "…",
                rango     = "Clima actual",
                progreso  = (humedad ?: 42) / 100f
            )
        }

        Text("Lotes Activos",       fontSize = 17.sp, fontWeight = FontWeight.Bold, color = VerdeOscuro)
        Text("Monitoreo en tiempo real", fontSize = 12.sp, color = Color.Gray)

        // Datos de lotes extraídos para facilitar cambios futuros
        LotesActivos()

        TarjetaRecomendacion()

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ── Datos de lotes ───────────────────────────────────────────
private data class Lote(val nombre: String, val estado: String, val colorTexto: Color, val colorFondo: Color)

private val LOTES = listOf(
    Lote("Norte",       "Saludable",         Color(0xFF40916C), Color(0xFFD8F3DC)),
    Lote("Área Viñedo", "Necesita Atención", Color(0xFFE07B39), Color(0xFFFFEDD5)),
    Lote("Pastura Sur", "Muestra Atrasada",  Color(0xFFB5172C), Color(0xFFFFE0E4))
)

@Composable
private fun LotesActivos() {
    LOTES.forEach { lote ->
        TarjetaLote(lote.nombre, lote.estado, lote.colorTexto, lote.colorFondo)
    }
}

// ── Componentes de tarjetas ──────────────────────────────────
@Composable
fun TarjetaIndiceGlobal() {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = BeigeCard),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier         = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("ÍNDICE GLOBAL", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = VerdeOscuro) {
                    Text(
                        "  EXCELENTE  ",
                        fontSize = 11.sp,
                        color    = Color.White,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("88",   fontSize = 42.sp, fontWeight = FontWeight.Bold, color = VerdeOscuro)
                    Text("/100", fontSize = 16.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                CircularProgressIndicator(
                    progress    = { 0.88f },
                    modifier    = Modifier.size(80.dp),
                    color       = VerdeBoton,
                    strokeWidth = 7.dp,
                    trackColor  = VerdeClaro,
                    strokeCap   = StrokeCap.Round
                )
                Text("🌿", fontSize = 24.sp)
            }
        }
    }
}

@Composable
fun TarjetaMiniMetrica(
    modifier : Modifier,
    titulo   : String,
    valor    : String,
    rango    : String,
    progreso : Float
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = BeigeCard),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(titulo, fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Text(valor, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = VerdeOscuro)
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress   = { progreso.coerceIn(0f, 1f) },
                modifier   = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color      = VerdeBoton,
                trackColor = VerdeClaro
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(rango, fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
fun TarjetaLote(nombre: String, estado: String, colorTexto: Color, colorFondo: Color) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = BeigeCard),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(VerdeClaro),
                contentAlignment = Alignment.Center
            ) { Text("🌱", fontSize = 22.sp) }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(nombre, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = colorFondo) {
                    Text(
                        estado,
                        fontSize = 11.sp,
                        color    = colorTexto,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
        }
    }
}

@Composable
fun TarjetaRecomendacion() {
    val tts    = rememberTTS()                          // reutiliza el composable centralizado
    var leyendo by remember { mutableStateOf(false) }
    val texto  = "Considera anticipar la rotación de cultivos para fijación de Nitrógeno en el lote Norte."

    // Listener declarado una sola vez
    val listener = remember {
        object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?)  { leyendo = false }
            override fun onError(id: String?) { leyendo = false }
        }
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = VerdeOscuro)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("💡", fontSize = 24.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Recomendación del Día", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(6.dp))
            Text(texto, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    val engine = tts ?: return@Button
                    if (leyendo) {
                        engine.stop()
                        leyendo = false
                    } else {
                        engine.setOnUtteranceProgressListener(listener)
                        engine.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "rec")
                        leyendo = true
                    }
                },
                colors   = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                shape    = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector        = if (leyendo) Icons.Default.Stop else Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint               = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text     = if (leyendo) "Detener" else "Leer en voz alta",
                    color    = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ── Mapa ─────────────────────────────────────────────────────
@SuppressLint("MissingPermission")
@Composable
fun PantallaMapa() {
    val context   = LocalContext.current
    var ubicacion by remember { mutableStateOf<GeoPoint?>(null) }
    var muestras  by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }

    // Verificar permiso antes de pedir ubicación
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName

        val tienePermiso = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (tienePermiso) {
            LocationServices
                .getFusedLocationProviderClient(context)
                .lastLocation
                .addOnSuccessListener { location ->
                    location?.let {
                        val pos = GeoPoint(it.latitude, it.longitude)
                        ubicacion = pos
                        muestras  = listOf(pos)      // evita duplicar al recomponer
                    }
                }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(16.0)
                    controller.setCenter(GeoPoint(-17.4, -63.5))
                }
            },
            update = { mapView ->
                mapView.overlays.clear()
                ubicacion?.let { mapView.controller.setCenter(it) }
                muestras.forEachIndexed { i, punto ->
                    mapView.overlays.add(
                        Marker(mapView).apply {
                            position = punto
                            title    = "Muestra #${i + 1}"
                            snippet  = "Toca para ver detalles"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                    )
                }
                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        FloatingActionButton(
            onClick        = { ubicacion?.let { muestras = muestras + it } },
            containerColor = VerdeOscuro,
            shape          = CircleShape,
            modifier       = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Agregar muestra", tint = Color.White)
        }

        if (ubicacion == null) {
            Card(
                modifier = Modifier.align(Alignment.Center),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier             = Modifier.padding(16.dp),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        color       = VerdeOscuro,
                        strokeWidth = 2.dp
                    )
                    Text("Obteniendo ubicación…", fontSize = 14.sp)
                }
            }
        }
    }
}

// ── Recomendaciones ──────────────────────────────────────────
@Composable
fun PantallaRecomendaciones() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("💡 Recomendaciones\n(próximamente)", textAlign = TextAlign.Center, color = Color.Gray)
    }
}