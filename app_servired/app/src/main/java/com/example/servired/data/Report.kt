package com.example.servired.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Modelo de datos para un Reporte de servicio público.
 * Diseñado para ser totalmente compatible con el esquema de base de datos en Supabase y RabbitMQ.
 */
@Serializable
data class Report(
    @SerialName("id")
    val id: String = UUID.randomUUID().toString(),

    @SerialName("id_usuario")
    val userId: Long? = null,

    @SerialName("name")
    val name: String,

    @SerialName("service_type")
    val serviceType: String,

    @SerialName("description")
    val description: String,

    @SerialName("date_time")
    val dateTime: String,

    @SerialName("latitude")
    val latitude: Double,

    @SerialName("longitude")
    val longitude: Double,

    @SerialName("dane_code")
    val daneCode: String,

    @SerialName("municipality")
    val municipality: String,

    @SerialName("department")
    val department: String,

    @SerialName("media_uris")
    val mediaUris: List<String> = emptyList(),

    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @SerialName("estado")
    val estado: String = "Pendiente",

    @SerialName("validado")
    val validado: Boolean = false
)
