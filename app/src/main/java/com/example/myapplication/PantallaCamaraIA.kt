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
    val estadoSalud: String,
    val porcentajeSalud: Int,
    val colorEstado: Color,
    val alertas: List<String>,
    val planAccion: List<Pair<String, String>>,
    val notaAgronomo: String
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
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Estado: ${analisis.estadoSalud}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("DIAGNÓSTICO VISUAL", fontSize = 10.sp, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${analisis.porcentajeSalud}%",
                            fontSize = 28.sp, fontWeight = FontWeight.Bold, color = analisis.colorEstado)
                        Text("SALUD", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }

            analisis.alertas.forEach { alerta ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3F3))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(alerta, fontSize = 13.sp)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFFFEDD5)),
                            contentAlignment = Alignment.Center
                        ) { Text("⚡", fontSize = 18.sp) }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Plan de Acción", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    analisis.planAccion.forEach { (titulo, descripcion) ->
                        Row(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text("🌿", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(titulo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(descripcion, fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF4FF))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("NOTA DEL AGRÓNOMO IA", fontSize = 10.sp,
                        color = Color(0xFF3B5BDB), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("\"${analisis.notaAgronomo}\"", fontSize = 13.sp, color = Color(0xFF3B5BDB))
                }
            }

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
            val backendUrl = "https://sinapa-backend.onrender.com/analizar" // ← ajusta si el endpoint es diferente

            val bitmapReducido = Bitmap.createScaledBitmap(bitmap, 800, 600, true)
            val stream = ByteArrayOutputStream()
            bitmapReducido.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val imageBytes = stream.toByteArray()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image",
                    "suelo.jpg",
                    imageBytes.toRequestBody("image/jpeg".toMediaType())
                )
                .build()

            val client = OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS) // Render free tarda ~50s en arrancar
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

            val resultado = JSONObject(responseText)
            val estadoTexto = resultado.getString("estadoSalud")
            val porcentaje  = resultado.getInt("porcentajeSalud")

            val colorEstado = when {
                porcentaje >= 70 -> Color(0xFF40916C)
                porcentaje >= 40 -> Color(0xFFE07B39)
                else             -> Color(0xFFB5172C)
            }

            val alertas = mutableListOf<String>()
            val alertasJson = resultado.getJSONArray("alertas")
            for (i in 0 until alertasJson.length()) alertas.add(alertasJson.getString(i))

            val plan = mutableListOf<Pair<String, String>>()
            val planJson = resultado.getJSONArray("planAccion")
            for (i in 0 until planJson.length()) {
                val item = planJson.getJSONObject(i)
                plan.add(Pair(item.getString("titulo"), item.getString("descripcion")))
            }

            EstadoEscaner.Resultado(
                AnalisisSuelo(
                    estadoSalud     = estadoTexto,
                    porcentajeSalud = porcentaje,
                    colorEstado     = colorEstado,
                    alertas         = alertas,
                    planAccion      = plan,
                    notaAgronomo    = resultado.getString("notaAgronomo")
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