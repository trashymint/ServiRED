package com.example.servired.data

import android.util.Log
import com.example.servired.BuildConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Formato exacto que espera la API (nombres en snake_case)
@Serializable
private data class ReporteRequest(
    val name: String,
    val description: String,
    @SerialName("service_type") val serviceType: String,
    val latitude: Double,
    val longitude: Double,
    val municipality: String? = null,
    val department: String? = null,
    @SerialName("dane_code") val daneCode: String? = null,
    @SerialName("date_time") val dateTime: String,
    @SerialName("id_usuario") val userId: Long? = null
)

object ReportApi {

    private val http = HttpClient(Android) {
        install(HttpTimeout) {
            connectTimeoutMillis = 8_000
            requestTimeoutMillis = 15_000
        }
    }

    // Convierte tu Report al formato de la API, resolviendo id_usuario si fuera necesario.
    private suspend fun Report.toRequest(): ReporteRequest {
        var resolvedUserId = userId
        if (resolvedUserId == null) {
            try {
                val authId = SupabaseClientConfig.client.auth.currentUserOrNull()?.id
                if (authId != null) {
                    val userRecord = SupabaseClientConfig.client.from("Usuario")
                        .select { filter { eq("auth_id", authId) } }
                        .decodeSingleOrNull<DbUsuario>()
                    resolvedUserId = userRecord?.idUsuario
                }
            } catch (e: Exception) {
                Log.e("ReportApi", "No se pudo resolver el id_usuario desde Supabase", e)
            }
        }

        return ReporteRequest(
            name = name,
            description = description,
            serviceType = serviceType,
            latitude = latitude,
            longitude = longitude,
            municipality = municipality,
            department = department,
            daneCode = daneCode,
            dateTime = dateTime,
            userId = resolvedUserId
        )
    }

    /** Envía el reporte a la API de ingesta, que lo valida y lo publica en la cola. */
    suspend fun publishReportToQueue(report: Report): Result<Boolean> = withContext(Dispatchers.IO) {
        val token = SupabaseClientConfig.client.auth.currentAccessTokenOrNull()
            ?: return@withContext Result.failure(Exception("Debes iniciar sesión para enviar un reporte"))
        try {
            val requestPayload = report.toRequest()
            val resp = withContext(NonCancellable) {
                http.post("${BuildConfig.INGESTA_URL}/reportes") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(requestPayload))
                }
            }
            if (resp.status == HttpStatusCode.Accepted || resp.status == HttpStatusCode.OK || resp.status == HttpStatusCode.Created) {
                Log.d("ReportApi", "Reporte aceptado por la API: ${resp.bodyAsText()}")
                Result.success(true)
            } else {
                Log.e("ReportApi", "La API respondió ${resp.status.value}: ${resp.bodyAsText()}")
                Result.failure(Exception("La API rechazó el reporte (${resp.status.value})"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ReportApi", "Error al enviar el reporte: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** Envía una solicitud para validar un reporte a la API de ingesta. */
    suspend fun validateReportOnApi(reportId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val token = SupabaseClientConfig.client.auth.currentAccessTokenOrNull()
            ?: return@withContext Result.failure(Exception("Debes iniciar sesión para validar un reporte"))
        try {
            val resp = withContext(NonCancellable) {
                http.post("${BuildConfig.INGESTA_URL}/reportes/$reportId/validar") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
            val responseText = resp.bodyAsText()
            if (resp.status == HttpStatusCode.OK || resp.status == HttpStatusCode.Accepted) {
                Log.d("ReportApi", "Reporte validado exitosamente en la API: $responseText")
                Result.success(true)
            } else {
                Log.e("ReportApi", "La API respondió al validar ${resp.status.value}: $responseText")
                Result.failure(Exception("No puedes validar tu propio reporte"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ReportApi", "Error al validar el reporte: ${e.message}", e)
            Result.failure(e)
        }
    }
}
