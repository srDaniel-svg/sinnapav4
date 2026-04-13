package com.example.myapplication
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import com.google.android.gms.location.LocationServices
// En tu archivo, agrega esto al inicio
import android.speech.tts.TextToSpeech
import java.util.Locale

import android.annotation.SuppressLint


// ── Colores ──────────────────────────────────────────────────
val VerdeOscuro = Color(0xFF1B4332)
val VerdeMedio  = Color(0xFF2D6A4F)
val VerdeBoton  = Color(0xFF40916C)
val VerdeClaro  = Color(0xFFD8F3DC)
val FondoClaro  = Color(0xFFF1F0EB)
val BeigeCard   = Color(0xFFFFFFFF)

// ── Navegación principal ─────────────────────────────────────
@Composable
fun PantallaInicio() {
    var tabSeleccionado by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = FondoClaro,
        topBar = { BarraSuperior() },
        bottomBar = { BarraInferior(tabSeleccionado) { tabSeleccionado = it } },
        floatingActionButton = {
            if (tabSeleccionado == 0) {
                FloatingActionButton(
                    onClick = { tabSeleccionado = 1 },
                    containerColor = VerdeOscuro,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (tabSeleccionado) {
                1 -> PantallaHome()
                0 -> PantallaCamara()
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
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(VerdeBoton),
                    contentAlignment = Alignment.Center
                ) { Text("🌾", fontSize = 18.sp) }
                Spacer(modifier = Modifier.width(10.dp))
                Text("SINAPA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        actions = {
            IconButton(onClick = {}) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White)
            }
        }
    )
}
@Composable
fun rememberTTS(): TextToSpeech {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "MX") // Español
                // Para voz más "Loquendo" puedes probar es_US o es_ES
            }
        }
        tts = engine
        onDispose { engine.shutdown() }
    }

    return tts ?: TextToSpeech(context) {}
}
// ── Barra inferior ───────────────────────────────────────────
@Composable
fun BarraInferior(seleccionado: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = Color.White) {
        listOf(
            Triple("Muestras",   Icons.Default.Home,       0),
            Triple("dashboard", Icons.Default.Science,    1),
            Triple("Mapa",     Icons.Default.Map,        2),
            Triple("Consejos", Icons.Default.Lightbulb,  3)
        ).forEach { (label, icon, index) ->
            NavigationBarItem(
                selected = seleccionado == index,
                onClick  = { onSelect(index) },
                icon     = { Icon(icon, contentDescription = label) },
                label    = { Text(label, fontSize = 11.sp) },
                colors   = NavigationBarItemDefaults.colors(
                    selectedIconColor = VerdeOscuro,
                    selectedTextColor = VerdeOscuro,
                    indicatorColor    = VerdeClaro
                )
            )
        }
    }
}

// ── HOME / DASHBOARD ─────────────────────────────────────────
@Composable
fun PantallaCamara() {
    PantallaCamaraIA()
}
@Composable
fun PantallaHome() {
    val climaViewModel: ClimaViewModel = viewModel()
    val humedad by climaViewModel.humedad.collectAsState()

// Pedir permiso de ubicación
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { climaViewModel.cargarClima() }

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
        // Título
        Text("Dashboard de Campo", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = VerdeOscuro)
        Text("Visión general del ecosistema del suelo.", fontSize = 13.sp, color = Color.Gray)

        // Tarjeta índice global
        TarjetaIndiceGlobal()

        // Tarjetas pH y Humedad
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TarjetaMiniMetrica(modifier = Modifier.weight(1f), titulo = "Equilibrio pH", valor = "6.4", rango = "Ideal: 6.0 - 7.0", progreso = 0.64f)
            TarjetaMiniMetrica(
                modifier = Modifier.weight(1f),
                titulo = "Humedad",
                valor = if (humedad != null) "$humedad%" else "...",
                rango = "Clima actual",
                progreso = (humedad ?: 42) / 100f
            )
        }

        // Lotes activos
        Text("Lotes Activos", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = VerdeOscuro)
        Text("Monitoreo en tiempo real", fontSize = 12.sp, color = Color.Gray)

        TarjetaLote("Norte",        "Saludable",        Color(0xFF40916C), Color(0xFFD8F3DC))
        TarjetaLote("Área Viñedo",  "Necesita Atención",Color(0xFFE07B39), Color(0xFFFFEDD5))
        TarjetaLote("Pastura Sur",  "Muestra Atrasada", Color(0xFFB5172C), Color(0xFFFFE0E4))

        // Recomendación del día
        TarjetaRecomendacion()

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun TarjetaIndiceGlobal() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BeigeCard),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("ÍNDICE GLOBAL", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = VerdeOscuro) {
                    Text("  EXCELENTE  ", fontSize = 11.sp, color = Color.White, modifier = Modifier.padding(vertical = 3.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("88", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = VerdeOscuro)
                    Text("/100", fontSize = 16.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            // Círculo de progreso
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                CircularProgressIndicator(
                    progress = { 0.88f },
                    modifier = Modifier.size(80.dp),
                    color = VerdeBoton,
                    strokeWidth = 7.dp,
                    trackColor = VerdeClaro,
                    strokeCap = StrokeCap.Round
                )
                Text("🌿", fontSize = 24.sp)
            }
        }
    }
}

@Composable
fun TarjetaMiniMetrica(modifier: Modifier, titulo: String, valor: String, rango: String, progreso: Float) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BeigeCard),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(titulo, fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Text(valor, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = VerdeOscuro)
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progreso },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = VerdeBoton,
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BeigeCard),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(VerdeClaro),
                contentAlignment = Alignment.Center
            ) { Text("🌱", fontSize = 22.sp) }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(nombre, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = colorFondo) {
                    Text(estado, fontSize = 11.sp, color = colorTexto,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
        }
    }
}

@Composable
fun TarjetaRecomendacion() {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var leyendo by remember { mutableStateOf(false) }
    val texto = "Considera anticipar la rotación de cultivos para fijación de Nitrógeno en el lote Norte."

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "MX")
            }
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VerdeOscuro)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("💡", fontSize = 24.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Recomendación del Día",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                texto,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(14.dp))

            // ── Botón de voz ──────────────────────────────
            Button(
                onClick = {
                    val t = tts ?: return@Button
                    if (leyendo) {
                        t.stop()
                        leyendo = false
                    } else {
                        t.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "rec")
                        leyendo = true
                        // Resetea el ícono cuando termina
                        t.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                            override fun onStart(id: String?) {}
                            override fun onDone(id: String?)  { leyendo = false }
                            override fun onError(id: String?) { leyendo = false }
                        })
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (leyendo) Icons.Default.Stop else Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (leyendo) "Detener" else "Leer en voz alta",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ── PLACEHOLDERS ─────────────────────────────────────────────


@SuppressLint("MissingPermission")
@Composable
fun PantallaMapa() {
    val context = LocalContext.current
    var ubicacion by remember { mutableStateOf<GeoPoint?>(null) }
    var muestras by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        fusedClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val pos = GeoPoint(it.latitude, it.longitude)
                ubicacion = pos
                muestras = muestras + pos
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
                muestras.forEachIndexed { index, punto ->
                    val marker = Marker(mapView).apply {
                        position = punto
                        title = "Muestra #${index + 1}"
                        snippet = "Toca para ver detalles"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    mapView.overlays.add(marker)
                }
                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Botón agregar muestra
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp, end = 16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButton(
                onClick = { ubicacion?.let { muestras = muestras + it } },
                containerColor = VerdeOscuro,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar muestra", tint = Color.White)
            }
        }

        if (ubicacion == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = VerdeOscuro,
                            strokeWidth = 2.dp
                        )
                        Text("Obteniendo ubicación...", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PantallaRecomendaciones() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("💡 Recomendaciones\n(próximamente)", textAlign = TextAlign.Center, color = Color.Gray)
    }
}