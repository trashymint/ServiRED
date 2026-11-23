package com.example.servired.data

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale
import kotlin.coroutines.resume

data class DaneLocation(
    val municipality: String,
    val daneCode: String,
    val department: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

object DaneRepository {

    private val defaultLocations = listOf(
        DaneLocation("Bogotá, D.C.", "11001", "Bogotá D.C.", 4.60971, -74.08175),
        DaneLocation("Medellín", "05001", "Antioquia", 6.2442, -75.5812),
        DaneLocation("Cali", "76001", "Valle del Cauca", 3.4516, -76.5320),
        DaneLocation("Barranquilla", "08001", "Atlántico", 10.9685, -74.7813),
        DaneLocation("Cartagena", "13001", "Bolívar", 10.3910, -75.4794),
        DaneLocation("Bucaramanga", "68001", "Santander", 7.1254, -73.1198),
        DaneLocation("Pereira", "66001", "Risaralda", 4.8133, -75.6961),
        DaneLocation("Manizales", "17001", "Caldas", 5.0689, -75.5174),
        DaneLocation("Ibagué", "73001", "Tolima", 4.4389, -75.2322),
        DaneLocation("Cúcuta", "54001", "Norte de Santander", 7.8939, -72.5078),
        DaneLocation("Santa Marta", "47001", "Magdalena", 11.2408, -74.1990),
        DaneLocation("Villavicencio", "50001", "Meta", 4.1420, -73.6266),
        DaneLocation("Pasto", "52001", "Nariño", 1.2136, -77.2811),
        DaneLocation("Montería", "23001", "Córdoba", 8.7480, -75.8814),
        DaneLocation("Neiva", "41001", "Huila", 2.9273, -75.2819),
        DaneLocation("Armenia", "63001", "Quindío", 4.5339, -75.6811),
        DaneLocation("Popayán", "19001", "Cauca", 2.4448, -76.6147),
        DaneLocation("Sincelejo", "70001", "Sucre", 9.3047, -75.3978),
        DaneLocation("Riohacha", "44001", "La Guajira", 11.5444, -72.9072),
        DaneLocation("Tunja", "15001", "Boyacá", 5.5353, -73.3678),
        DaneLocation("Florencia", "18001", "Caquetá", 1.6144, -75.6062),
        DaneLocation("Yopal", "85001", "Casanare", 5.3378, -72.3959),
        DaneLocation("Quibdó", "27001", "Chocó", 5.6947, -76.6611)
    )

    private val _daneLocations = MutableStateFlow<List<DaneLocation>>(defaultLocations)
    val daneLocations: StateFlow<List<DaneLocation>> = _daneLocations.asStateFlow()

    private fun normalizeString(input: String): String {
        val unaccented = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return unaccented.lowercase(Locale.ROOT).trim()
    }

    /**
     * Carga la lista completa de municipios de Colombia desde los assets locales y actualiza vía API
     */
    suspend fun loadDaneLocations(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val deptsMap = mutableMapOf<Int, String>()
                try {
                    val deptsJsonStr = context.assets.open("departments.json").bufferedReader().use { it.readText() }
                    val deptsArray = JSONArray(deptsJsonStr)
                    for (i in 0 until deptsArray.length()) {
                        val obj = deptsArray.getJSONObject(i)
                        deptsMap[obj.optInt("id")] = obj.optString("name")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val citiesJsonStr = context.assets.open("cities.json").bufferedReader().use { it.readText() }
                val citiesArray = JSONArray(citiesJsonStr)
                val fullList = mutableListOf<DaneLocation>()

                for (i in 0 until citiesArray.length()) {
                    val obj = citiesArray.getJSONObject(i)
                    val name = obj.optString("name", "")
                    val deptId = obj.optInt("departmentId", -1)
                    val postalCode = obj.optString("postalCode", "")
                    val id = obj.optInt("id")

                    val deptName = deptsMap[deptId] ?: "Colombia"
                    val daneCode = if (!postalCode.isNullOrEmpty() && postalCode != "null") postalCode else "$id"

                    if (name.isNotBlank()) {
                        val defaultMatch = defaultLocations.find { 
                            normalizeString(it.municipality) == normalizeString(name) 
                        }
                        fullList.add(
                            DaneLocation(
                                municipality = name,
                                daneCode = daneCode,
                                department = deptName,
                                latitude = defaultMatch?.latitude ?: 0.0,
                                longitude = defaultMatch?.longitude ?: 0.0
                            )
                        )
                    }
                }

                if (fullList.isNotEmpty()) {
                    _daneLocations.value = fullList.sortedBy { it.municipality }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Obtiene el nombre del municipio y departamento usando Android Geocoder (con soporte asíncrono para Android 13+)
     */
    private suspend fun getAddressFromGeocoder(context: Context, latitude: Double, longitude: Double): Pair<String?, String?> =
        withContext(Dispatchers.IO) {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return@withContext suspendCancellableCoroutine { continuation ->
                    try {
                        geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                            val addr = addresses.firstOrNull()
                            val city = addr?.locality ?: addr?.subAdminArea ?: addr?.subLocality
                            val dept = addr?.adminArea
                            continuation.resume(Pair(city, dept))
                        }
                    } catch (e: Exception) {
                        continuation.resume(Pair(null, null))
                    }
                }
            } else {
                try {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    val addr = addresses?.firstOrNull()
                    val city = addr?.locality ?: addr?.subAdminArea ?: addr?.subLocality
                    val dept = addr?.adminArea
                    return@withContext Pair(city, dept)
                } catch (e: Exception) {
                    return@withContext Pair(null, null)
                }
            }
        }

    /**
     * Fallback de Geocodificación inversa vía OSM Nominatim en caso de que Geocoder de Android falle
     */
    private suspend fun getAddressFromOsmNominatim(latitude: Double, longitude: Double): Pair<String?, String?> =
        withContext(Dispatchers.IO) {
            try {
                val urlStr = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude"
                val connection = URL(urlStr).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "ServiRED_Android_App/1.0")
                connection.connectTimeout = 4000
                connection.readTimeout = 4000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObj = JSONObject(response)
                    val address = jsonObj.optJSONObject("address")
                    if (address != null) {
                        val city = address.optString("city",
                            address.optString("town",
                            address.optString("village",
                            address.optString("municipality",
                            address.optString("county", "")))))
                        val state = address.optString("state", address.optString("region", ""))
                        return@withContext Pair(
                            if (city.isNotBlank()) city else null,
                            if (state.isNotBlank()) state else null
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext Pair(null, null)
        }

    /**
     * Encuentra de forma exacta el municipio más cercano a las coordenadas geográficas del usuario
     */
    suspend fun findNearestMunicipality(
        context: Context,
        latitude: Double,
        longitude: Double
    ): DaneLocation {
        return withContext(Dispatchers.IO) {
            val currentList = _daneLocations.value

            // 1. Obtener municipio desde Android Geocoder (esperando la respuesta asíncrona)
            var (detectedCity, detectedDepartment) = getAddressFromGeocoder(context, latitude, longitude)

            // 2. Si Geocoder no devolvió respuesta, intentar OSM Nominatim
            if (detectedCity.isNullOrEmpty()) {
                val osmResult = getAddressFromOsmNominatim(latitude, longitude)
                detectedCity = osmResult.first
                if (detectedDepartment.isNullOrEmpty()) {
                    detectedDepartment = osmResult.second
                }
            }

            // 3. Coincidencia exacta o parcial por nombre normalizado (sin tildes)
            if (!detectedCity.isNullOrEmpty()) {
                val normCity = normalizeString(detectedCity)
                
                // Búsqueda exacta primero
                val exactMatch = currentList.find { normalizeString(it.municipality) == normCity }
                if (exactMatch != null) return@withContext exactMatch

                // Búsqueda por contención
                val partialMatch = currentList.find {
                    val normMuni = normalizeString(it.municipality)
                    normMuni.contains(normCity) || normCity.contains(normMuni)
                }
                if (partialMatch != null) return@withContext partialMatch
            }

            // 4. Búsqueda por menor distancia física entre los municipios que poseen coordenadas GPS
            val withCoords = currentList.filter { it.latitude != 0.0 && it.longitude != 0.0 }
            if (withCoords.isNotEmpty()) {
                var closest: DaneLocation? = null
                var minDistance = Float.MAX_VALUE

                val distResult = FloatArray(1)
                for (loc in withCoords) {
                    Location.distanceBetween(latitude, longitude, loc.latitude, loc.longitude, distResult)
                    val distance = distResult[0]
                    if (distance < minDistance) {
                        minDistance = distance
                        closest = loc
                    }
                }

                if (closest != null) return@withContext closest
            }

            // 5. Fallback por departamento
            if (!detectedDepartment.isNullOrEmpty()) {
                val normDept = normalizeString(detectedDepartment!!)
                val matchDept = currentList.find { normalizeString(it.department).contains(normDept) || normDept.contains(normalizeString(it.department)) }
                if (matchDept != null) return@withContext matchDept
            }

            return@withContext currentList.firstOrNull() ?: defaultLocations.first()
        }
    }
}
