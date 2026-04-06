package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

//Colores del tema agronomo :D
val VerdeOscuro = Color(0xFF2E7D32)
val VerdeMedio = Color(0xFF4CAF50)
val VerdeClaro = Color(0xFFE8F5E9)
val MarronSuelo = Color (0xFF785548)

@Composable
    fun PantallaInicio(){
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(VerdeClaro)
                .padding(24.dp),
                horizontalAlignment= Alignment.CenterHorizontally
            ){
            Spacer(modifier = Modifier.height(48.dp))

            //Título
            Text(
                text = "🌱",
                fontSize = 64.sp
            )
            Spacer (modifier = Modifier.height(12.dp))
            Text(
                text = "AgroSuelo",
                fontSize= 32.sp,
                fontWeight = FontWeight.Bold,
                color = VerdeOscuro
            )
            Spacer(modifier=Modifier.height (48.dp))

            //Botones del menu
            BotonMenu(
                emoji="📸",
                titulo="analizar por Cámara",
                descripcion = "Toma una foto del suelo",
                color =VerdeMedio
            )
            Spacer (modifier=Modifier.height(16.dp))
            BotonMenu(
                emoji= "📊",
                titulo= "Ver Resultados",
                descripcion= "Historial de análisis",
                color= MarronSuelo
            )
            Spacer (modifier = Modifier.height(16.dp))
            BotonMenu(
                emoji="📋",
                titulo= "Recomendaciones",
                descripcion= "como mejorar tu suelo",
                color = Color (0xFF1565C0)

            )
        }
}

@Composable
fun BotonMenu(emoji: String, titulo : String,descripcion: String, color:Color ){
    Button(
        onClick = {/*navegacion despues*/},
        modifier= Modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor=color)

    ){
        Row (
            modifier= Modifier.fillMaxWidth(),
            verticalAlignment= Alignment.CenterVertically
        ){
            Text (text = emoji, fontSize=28.sp)
            Spacer(modifier=Modifier.width(16.dp))
            Column{
                Text (text= titulo,fontWeight =FontWeight.Bold,fontSize=16.sp)
                Text (text= descripcion,fontSize= 12.sp, color = Color.White.copy(alpha=0.8f))
            }
        }
    }
}