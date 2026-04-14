package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
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
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// ── Estados de la pantalla ───────────────────────────────────
sealed class EstadoEscaner {
    object Inicio : EstadoEscaner()
    object Camara : EstadoEscaner()
    object Analizando : EstadoEscaner()
    data class Resultado(val analisis: AnalisisSuelo) : EstadoEscaner()
    data class Error(val mensaje: String) : EstadoEscaner()
}

data class AnalisisSuelo(
    val ph: Double,
    val nitrogeno: Double,
    val fosforo: Double,
    val potasio: Double,
    val hectareas: Double,
    val cultivo: String
)
// ── Pantalla principal del escáner ───────────────────────────
@Composable
fun PantallaCamaraIA() {
    var estado by remember { mutableStateOf<EstadoEscaner>(EstadoEscaner.Inicio) }
    var bitmapCapturado by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tienePermiso by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val lanzadorPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { tienePermiso = it }

    // Lanzador para galería
    val lanzadorGaleria = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = uriToBitmap(context, it)
            bitmap?.let { bmp ->
                bitmapCapturado = bmp
                estado = EstadoEscaner.Analizando
                scope.launch {
                    estado = analizarConBackend(bmp)
                }
            }
        }
    }

    when (val estadoActual = estado) {
        is EstadoEscaner.Inicio -> {
            PantallaInicioEscaner(
                onAbrirCamara = {
                    if (tienePermiso) estado = EstadoEscaner.Camara
                    else lanzadorPermiso.launch(Manifest.permission.CAMERA)
                },
                onAbrirGaleria = { lanzadorGaleria.launch("image/*") }
            )
        }
        is EstadoEscaner.Camara -> {
            PantallaCamaraCaptura(
                onFotoTomada = { bitmap ->
                    bitmapCapturado = bitmap
                    estado = EstadoEscaner.Analizando
                    scope.launch {
                        estado = analizarConBackend(bitmap)
                    }
                },
                onCancelar = { estado = EstadoEscaner.Inicio }
            )
        }
        is EstadoEscaner.Analizando -> PantallaAnalizando()
        is EstadoEscaner.Resultado -> {
            PantallaResultado(
                bitmap = bitmapCapturado,
                analisis = estadoActual.analisis,
                onNuevoAnalisis = {
                    bitmapCapturado = null
                    estado = EstadoEscaner.Inicio
                }
            )
        }
        is EstadoEscaner.Error -> {
            PantallaError(
                mensaje = estadoActual.mensaje,
                onReintentar = { estado = EstadoEscaner.Inicio }
            )
        }
    }
}

// ── Pantalla de inicio del escáner ───────────────────────────
@Composable
fun PantallaInicioEscaner(onAbrirCamara: () -> Unit, onAbrirGaleria: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("Escáner de Suelo", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = VerdeOscuro)
        Text("Diagnóstico visual y plan de curación.", fontSize = 13.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth().height(300.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8)),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(72.dp).clip(CircleShape).background(VerdeClaro),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null,
                            tint = VerdeOscuro, modifier = Modifier.size(36.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Tomar o subir foto", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Enfoca bien la textura del suelo", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onAbrirCamara,
            modifier = Modifier.fillMaxWidth().height(52.dp).offset(y = offsetY.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VerdeOscuro)
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Abrir Cámara", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onAbrirGaleria,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = VerdeOscuro)
        ) {
            Icon(Icons.Default.Image, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Subir desde Galería", fontWeight = FontWeight.Bold)
        }
    }
}

// ── Cámara en vivo con captura ───────────────────────────────
@Composable
fun PantallaCamaraCaptura(onFotoTomada: (Bitmap) -> Unit, onCancelar: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture
                        )
                    } catch (e: Exception) { e.printStackTrace() }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 32.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingActionButton(
                    onClick = onCancelar,
                    containerColor = Color.White.copy(alpha = 0.8f),
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = Color.DarkGray)
                }

                FloatingActionButton(
                    onClick = {
                        val archivo = File(context.cacheDir, "foto_suelo.jpg")
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(archivo).build()
                        imageCapture?.takePicture(
                            outputOptions, executor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    val bitmap = BitmapFactory.decodeFile(archivo.absolutePath)
                                    bitmap?.let { onFotoTomada(it) }
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    exception.printStackTrace()
                                }
                            }
                        )
                    },
                    containerColor = VerdeOscuro,
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(Icons.Default.Camera, contentDescription = "Capturar",
                        tint = Color.White, modifier = Modifier.size(32.dp))
                }

                Spacer(modifier = Modifier.size(52.dp))
            }
        }
    }
}

// ── Pantalla de carga con GIF ─────────────────────────────────
@Composable
fun PantallaAnalizando() {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(GifDecoder.Factory()) }
            .build()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(FondoClaro),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(context).data(R.raw.loading2).build(),
                    imageLoader = imageLoader
                ),
                contentDescription = "Analizando",
                modifier = Modifier.size(180.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text("Analizando tu suelo...", fontSize = 18.sp,
                fontWeight = FontWeight.Bold, color = VerdeOscuro)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Conectando con el servidor...", fontSize = 13.sp, color = Color.Gray)
        }
    }
}

// ── Pantalla de resultados ────────────────────────────────────
@Composable
fun PantallaResultado(bitmap: Bitmap?, analisis: AnalisisSuelo, onNuevoAnalisis: () -> Unit) {

    // Calcular color según pH
    val colorPh = when {
        analisis.ph in 6.0..7.0 -> Color(0xFF40916C)  // ideal
        analisis.ph in 5.0..7.5 -> Color(0xFFE07B39)  // aceptable
        else                    -> Color(0xFFB5172C)   // crítico
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        // Imagen capturada
        bitmap?.let {
            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                androidx.compose.foundation.Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Foto del suelo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    FloatingActionButton(
                        onClick = onNuevoAnalisis,
                        containerColor = Color.White.copy(alpha = 0.9f),
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retomar",
                            tint = VerdeOscuro, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // Título
            Text("Resultado del Análisis", fontSize = 20.sp,
                fontWeight = FontWeight.Bold, color = VerdeOscuro)
            Text("Cultivo detectado: ${analisis.cultivo}", fontSize = 13.sp, color = Color.Gray)

            // Tarjeta pH
            TarjetaMetrica(
                emoji   = "🧪",
                titulo  = "pH del Suelo",
                valor   = analisis.ph.toString(),
                detalle = "Ideal entre 6.0 y 7.0",
                color   = colorPh
            )

            // Fila Nitrógeno + Fósforo
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TarjetaMetricaChica(
                    modifier = Modifier.weight(1f),
                    emoji    = "🌿",
                    titulo   = "Nitrógeno",
                    valor    = "${analisis.nitrogeno} mg/kg"
                )
                TarjetaMetricaChica(
                    modifier = Modifier.weight(1f),
                    emoji    = "💧",
                    titulo   = "Fósforo",
                    valor    = "${analisis.fosforo} mg/kg"
                )
            }

            // Fila Potasio + Hectáreas
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TarjetaMetricaChica(
                    modifier = Modifier.weight(1f),
                    emoji    = "⚡",
                    titulo   = "Potasio",
                    valor    = "${analisis.potasio} mg/kg"
                )
                TarjetaMetricaChica(
                    modifier = Modifier.weight(1f),
                    emoji    = "📐",
                    titulo   = "Hectáreas",
                    valor    = "${analisis.hectareas} ha"
                )
            }

            // Botón nuevo análisis
            Button(
                onClick = onNuevoAnalisis,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VerdeOscuro)
            ) {
                Text("Nuevo Análisis", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Tarjeta grande para pH ────────────────────────────────────
@Composable
fun TarjetaMetrica(emoji: String, titulo: String, valor: String, detalle: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 28.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(titulo, fontSize = 12.sp, color = Color.Gray)
                Text(valor, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
                Text(detalle, fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

// ── Tarjeta chica para los demás valores ─────────────────────
@Composable
fun TarjetaMetricaChica(modifier: Modifier, emoji: String, titulo: String, valor: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(emoji, fontSize = 22.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(titulo, fontSize = 11.sp, color = Color.Gray)
            Text(valor, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = VerdeOscuro)
        }
    }
}
// ── Pantalla de error ─────────────────────────────────────────
@Composable
fun PantallaError(mensaje: String, onReintentar: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text("❌", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Algo salió mal", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(mensaje, textAlign = TextAlign.Center, color = Color.Gray, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onReintentar,
                colors = ButtonDefaults.buttonColors(containerColor = VerdeOscuro)
            ) {
                Text("Reintentar", color = Color.White)
            }
        }
    }
}

// ── Llamada al Backend ────────────────────────────────────────
suspend fun analizarConBackend(bitmap: Bitmap): EstadoEscaner {
    return withContext(Dispatchers.IO) {
        try {
            val backendUrl = "https://sinapa-backend.onrender.com/extraer-datos-suelo"

            val bitmapReducido = Bitmap.createScaledBitmap(bitmap, 800, 600, true)
            val stream = ByteArrayOutputStream()
            bitmapReducido.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val imageBytes = stream.toByteArray()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "archivo",
                    "suelo.jpg",
                    imageBytes.toRequestBody("image/jpeg".toMediaType())
                )
                .build()

            val client = OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(backendUrl)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext EstadoEscaner.Error("Error del servidor: ${response.code}")
            }

            val responseText = response.body?.string()
                ?: return@withContext EstadoEscaner.Error("Sin respuesta del servidor")

            android.util.Log.d("BACKEND", "Respuesta: $responseText")

            val json = JSONObject(responseText)

            EstadoEscaner.Resultado(
                AnalisisSuelo(
                    ph        = json.getDouble("ph"),
                    nitrogeno = json.getDouble("nitrogeno"),
                    fosforo   = json.getDouble("fosforo"),
                    potasio   = json.getDouble("potasio"),
                    hectareas = json.getDouble("hectareas"),
                    cultivo   = json.getString("cultivo")
                )
            )

        } catch (e: Exception) {
            android.util.Log.e("BACKEND", "Error: ${e.message}")
            EstadoEscaner.Error("Error al conectar: ${e.message}")
        }
    }
}
// ── Utilidad: Uri a Bitmap ────────────────────────────────────
fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val stream = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(stream)
    } catch (e: Exception) { null }
}