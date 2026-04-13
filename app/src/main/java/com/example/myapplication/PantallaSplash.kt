package com.example.myapplication
import android.media.MediaPlayer
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext

@Composable
fun PantallaSplash(onTerminado: () -> Unit) {

    // Escala del logo (empieza pequeño y crece con rebote)
    var iniciar by remember { mutableStateOf(false) }
    val escalaLogo by animateFloatAsState(
        targetValue = if (iniciar) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "escalaLogo"
    )

    // Escala del texto SINAPA
    val escalaTexto by animateFloatAsState(
        targetValue = if (iniciar) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "escalaTexto"
    )

    // Brillo/pulso del fondo
    val infiniteTransition = rememberInfiniteTransition(label = "pulso")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        iniciar = true
        val sonido = MediaPlayer.create(context, R.raw.sinapa)
        sonido.start()
        delay(2500)
        sonido.release()
        onTerminado()
    }

    // Pantalla completa verde oscuro
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1B4332)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Logo con rebote
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(escalaLogo)
                    .clip(CircleShape)
                    .background(Color(0xFF40916C)),
                contentAlignment = Alignment.Center
            ) {
                Text("🌾", fontSize = 56.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Texto SINAPA con rebote
            Text(
                text = "SINAPA",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = alpha),
                modifier = Modifier.scale(escalaTexto)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Sistema de Análisis de Suelos",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.scale(escalaTexto)
            )
        }
    }
}