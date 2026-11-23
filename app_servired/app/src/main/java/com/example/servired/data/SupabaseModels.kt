package com.example.servired.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DbUbicacion(
    @SerialName("id_ubicacion") val idUbicacion: Long? = null,
    @SerialName("latitud") val latitud: Double? = null,
    @SerialName("longitud") val longitud: Double? = null,
    @SerialName("municipio") val municipio: String? = null,
    @SerialName("codigo_dane") val codigoDane: Long? = null,
    @SerialName("departamento") val departamento: String? = null
)

@Serializable
data class DbServicio(
    @SerialName("id_servicio") val idServicio: Long? = null,
    @SerialName("tipo") val tipo: String
)

@Serializable
data class DbOperador(
    @SerialName("id_operador") val idOperador: Long? = null,
    @SerialName("nombre") val nombre: String,
    @SerialName("contrasenia") val contrasenia: String? = null
)

@Serializable
data class DbUsuario(
    @SerialName("id_usuario") val idUsuario: Long? = null,
    @SerialName("auth_id") val authId: String? = null,
    @SerialName("nombre") val nombre: String,
    @SerialName("email") val email: String? = null,
    @SerialName("celular") val celular: String? = null,
    @SerialName("contrasenia") val contrasenia: String? = null
)

@Serializable
data class DbTipoFalla(
    @SerialName("id_falla") val idFalla: Long? = null,
    @SerialName("id_usuario") val idUsuario: Long? = null,
    @SerialName("id_ubicacion") val idUbicacion: Long? = null,
    @SerialName("id_servicio") val idServicio: Long? = null,
    @SerialName("id_operador") val idOperador: Long? = null,
    @SerialName("tipo") val tipo: String,
    @SerialName("fecha") val fecha: String,
    @SerialName("estado") val estado: String = "Pendiente",
    @SerialName("validado") val validado: Boolean? = false,
    @SerialName("descripcion") val descripcion: String? = null
)
