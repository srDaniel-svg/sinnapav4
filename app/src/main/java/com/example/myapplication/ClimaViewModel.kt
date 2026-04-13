package com.example.myapplication

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ── Modelo de respuesta ──────────────────────────────────────
data class ClimaRespuesta(
    val current_weather: ClimaActual?,
    val hourly: HourlyData?
)
data class ClimaActual(val temperature: Float, val windspeed: Float)
data class HourlyData(
    val time: List<String>,
    val relativehumidity_2m: List<Int>
)

// ── Interfaz API ─────────────────────────────────────────────
interface ClimaApi {
    @GET("v1/forecast")
    suspend fun getClima(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current_weather") currentWeather: Boolean = true,
        @Query("hourly") hourly: String = "relativehumidity_2m",
        @Query("forecast_days") days: Int = 1
    ): ClimaRespuesta
}

// ── ViewModel ────────────────────────────────────────────────
class ClimaViewModel(app: Application) : AndroidViewModel(app) {

    private val _humedad = MutableStateFlow<Int?>(null)
    val humedad: StateFlow<Int?> = _humedad

    private val _temperatura = MutableStateFlow<Float?>(null)
    val temperatura: StateFlow<Float?> = _temperatura

    private val api = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ClimaApi::class.java)

    private val fusedClient = LocationServices.getFusedLocationProviderClient(app)

    @SuppressLint("MissingPermission")
    fun cargarClima() {
        fusedClient.lastLocation.addOnSuccessListener { location: Location? ->
            val lat = location?.latitude ?: -17.4
            val lon = location?.longitude ?: -63.5
            viewModelScope.launch {
                try {
                    val resp = api.getClima(lat, lon)
                    _temperatura.value = resp.current_weather?.temperature
                    // Tomar humedad de la hora actual
                    _humedad.value = resp.hourly?.relativehumidity_2m?.firstOrNull()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}