package com.example.servired.data

import android.content.Context
import android.util.Log
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportRepository {

    private val _reports = MutableStateFlow<List<Report>>(emptyList())
    val reports: StateFlow<List<Report>> = _reports.asStateFlow()

    private val _serviciosList = MutableStateFlow<List<DbServicio>>(emptyList())
    val serviciosList: StateFlow<List<DbServicio>> = _serviciosList.asStateFlow()

    private val _dismissedResolvedIds = MutableStateFlow<Set<String>>(emptySet())
    val dismissedResolvedIds: StateFlow<Set<String>> = _dismissedResolvedIds.asStateFlow()

    private val notifiedResolvedIds = mutableSetOf<String>()

    private const val PREF_NAME = "servired_prefs"
    private const val KEY_DISMISSED_RESOLVED = "dismissed_resolved_ids"

    private fun loadDismissed(context: Context?) {
        if (context == null) return
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val set = prefs.getStringSet(KEY_DISMISSED_RESOLVED, emptySet()) ?: emptySet()
            _dismissedResolvedIds.value = set
        } catch (e: Exception) {
            Log.e("ReportRepository", "Error loading dismissed resolved reports", e)
        }
    }

    fun dismissResolvedReport(reportId: String, context: Context? = null) {
        val updated = _dismissedResolvedIds.value + reportId
        _dismissedResolvedIds.value = updated
        if (context != null) {
            try {
                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                prefs.edit().putStringSet(KEY_DISMISSED_RESOLVED, updated).apply()
            } catch (e: Exception) {
                Log.e("ReportRepository", "Error saving dismissed resolved report", e)
            }
        }
    }

    /**
     * Formatea fechas provenientes de la columna timestamptz de Supabase a un formato limpio "yyyy-MM-dd HH:mm:ss"
     */
    private fun formatDateTimeForDisplay(rawDate: String): String {
        if (rawDate.isBlank()) return ""
        return try {
            if (rawDate.length == 19 && !rawDate.contains("T")) {
                return rawDate
            }
            val cleaned = rawDate.replace("Z", "+00:00")
            val parsedDate = if (cleaned.contains("T")) {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                isoFormat.parse(cleaned.substringBefore("."))
            } else {
                val simpleFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                simpleFormat.parse(cleaned)
            }
            if (parsedDate != null) {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(parsedDate)
            } else {
                rawDate.take(19).replace("T", " ")
            }
        } catch (e: Exception) {
            rawDate.take(19).replace("T", " ")
        }
    }

    /**
     * Consulta la tabla "Servicio" en Supabase
     */
    suspend fun fetchServiciosFromSupabase() {
        if (SupabaseClientConfig.SUPABASE_URL.contains("AQUI_TU")) return

        withContext(Dispatchers.IO) {
            try {
                val client = SupabaseClientConfig.client
                val list = client.from("Servicio").select().decodeList<DbServicio>()
                if (list.isNotEmpty()) {
                    _serviciosList.value = list
                }
            } catch (e: Exception) {
                Log.e("ReportRepository", "Error al consultar la tabla Servicio en Supabase", e)
            }
        }
    }

    /**
     * Consulta las fallas reportadas desde Supabase y las fusiona con los reportes locales de la sesión
     */
    suspend fun fetchReportsFromSupabase(context: Context? = null) {
        if (context != null) {
            loadDismissed(context)
        }
        if (SupabaseClientConfig.SUPABASE_URL.contains("AQUI_TU")) return

        withContext(Dispatchers.IO) {
            try {
                val client = SupabaseClientConfig.client

                val fallas = client.from("tipo_falla").select().decodeList<DbTipoFalla>()
                val ubicaciones = client.from("Ubicacion").select().decodeList<DbUbicacion>().associateBy { it.idUbicacion }
                val servicios = client.from("Servicio").select().decodeList<DbServicio>().associateBy { it.idServicio }

                Log.d("ReportRepositoryDebug", "Fetched ${fallas.size} fallas, ${ubicaciones.size} ubicaciones, ${servicios.size} servicios from Supabase")

                val supabaseReports = fallas.map { falla ->
                    val u = ubicaciones[falla.idUbicacion]
                    val s = servicios[falla.idServicio]

                    val lat = u?.latitud ?: 4.60971
                    val lng = u?.longitud ?: -74.08175

                    Report(
                        id = falla.idFalla?.toString() ?: java.util.UUID.randomUUID().toString(),
                        userId = falla.idUsuario, // Preserva el id_usuario asociado en la base de datos
                        name = falla.tipo,
                        serviceType = s?.tipo ?: "Servicio General",
                        description = falla.descripcion ?: "",
                        dateTime = formatDateTimeForDisplay(falla.fecha),
                        latitude = lat,
                        longitude = lng,
                        daneCode = u?.codigoDane?.toString() ?: "11001",
                        municipality = u?.municipio ?: "Bogotá, D.C.",
                        department = u?.departamento ?: "Bogotá D.C.",
                        estado = falla.estado,
                        validado = falla.validado ?: false
                    )
                }

                Log.d("ReportRepositoryDebug", "Generated ${supabaseReports.size} supabaseReports")

                // Preservar reportes creados localmente que aún están pendientes en la cola de RabbitMQ
                val currentLocalList = _reports.value
                val supabaseIds = supabaseReports.map { it.id }.toSet()
                val supabaseSignatures = supabaseReports.map { "${it.name}_${it.dateTime}" }.toSet()

                val unsyncedLocal = currentLocalList.filter { local ->
                    local.id !in supabaseIds && "${local.name}_${local.dateTime}" !in supabaseSignatures
                }

                val finalReports = unsyncedLocal + supabaseReports
                _reports.value = finalReports

                if (context != null) {
                    for (report in finalReports) {
                        if (report.estado.equals("Resuelto", ignoreCase = true)) {
                            if (report.id !in notifiedResolvedIds) {
                                notifiedResolvedIds.add(report.id)
                                NotificationHelper.showReportResolvedNotification(context, report.name)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ReportRepository", "Error al consultar reportes desde Supabase", e)
            }
        }
    }

    /**
     * Agrega un reporte publicándolo en la API de ingesta y manteniéndolo en la sesión local
     */
    suspend fun addReport(report: Report) {
        val updatedLocal = listOf(report) + _reports.value.filter { it.id != report.id }
        _reports.value = updatedLocal

        withContext(Dispatchers.IO) {
            val publishResult = ReportApi.publishReportToQueue(report)

            if (publishResult.isSuccess) {
                Log.d("ReportRepository", "Reporte publicado en la API de ingesta exitosamente")
            } else {
                Log.w("ReportRepository", "No se pudo enviar a la API de ingesta, se mantiene guardado en la sesión local")
            }
        }
    }

    /**
     * Valida un reporte enviando la solicitud a la API de Ingesta (Google Cloud Run)
     */
    suspend fun validateReportInSupabase(reportId: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            val apiResult = ReportApi.validateReportOnApi(reportId)
            if (apiResult.isSuccess) {
                val updated = _reports.value.map { report ->
                    if (report.id == reportId) report.copy(validado = true) else report
                }
                _reports.value = updated
                Result.success(true)
            } else {
                apiResult
            }
        }
    }

    fun getCurrentFormattedDateTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }
}
