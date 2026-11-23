package com.example.servired.data

import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Phone
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object UserRepository {

    private val _currentUser = MutableStateFlow<DbUsuario?>(null)
    val currentUser: StateFlow<DbUsuario?> = _currentUser.asStateFlow()

    private val _isAnonymous = MutableStateFlow(false)
    val isAnonymous: StateFlow<Boolean> = _isAnonymous.asStateFlow()

    fun setAnonymousMode(enabled: Boolean) {
        _isAnonymous.value = enabled
        if (enabled) _currentUser.value = null
    }

    // "3001234567" -> "+573001234567"
    private fun normalizarCelular(texto: String): String? {
        val d = texto.filter { it.isDigit() }
        return when {
            d.length == 10 -> "+57$d"
            else -> null
        }
    }

    private suspend fun cargarPerfil(): DbUsuario {
        val client = SupabaseClientConfig.client
        val uid = client.auth.currentUserOrNull()?.id ?: error("Sin sesión")
        return client.from("Usuario")
            .select { filter { eq("auth_id", uid) } }
            .decodeSingle<DbUsuario>()
    }

    suspend fun registerUser(
        nombre: String,
        email: String?,
        celular: String,
        contrasenia: String
    ): Result<DbUsuario> = withContext(Dispatchers.IO) {
        val tel = normalizarCelular(celular)
            ?: return@withContext Result.failure(Exception("Celular no válido. Ingresa 10 dígitos."))
        try {
            SupabaseClientConfig.client.auth.signUpWith(Phone) {
                phone = tel
                password = contrasenia.trim()
                data = buildJsonObject {
                    put("nombre", nombre.trim())
                    if (!email.isNullOrBlank()) put("email", email.trim())
                }
            }
            val perfil = cargarPerfil()
            _currentUser.value = perfil
            _isAnonymous.value = false
            Result.success(perfil)
        } catch (e: Exception) {
            Log.e("UserRepository", "Error al registrar", e)
            val msg = e.message ?: ""
            if (msg.contains("user_already_exists", ignoreCase = true)) {
                Result.failure(Exception("Este número de celular ya está registrado. Por favor inicia sesión."))
            } else {
                Result.failure(Exception("No se pudo crear la cuenta: ${e.localizedMessage ?: e.message}"))
            }
        }
    }

    suspend fun loginUser(celular: String, contrasenia: String): Result<DbUsuario> =
        withContext(Dispatchers.IO) {
            val tel = normalizarCelular(celular)
                ?: return@withContext Result.failure(Exception("Celular no válido"))
            try {
                SupabaseClientConfig.client.auth.signInWith(Phone) {
                    phone = tel
                    password = contrasenia.trim()
                }
                val perfil = cargarPerfil()
                _currentUser.value = perfil
                _isAnonymous.value = false
                Result.success(perfil)
            } catch (e: Exception) {
                Log.e("UserRepository", "Error al iniciar sesión", e)
                Result.failure(Exception("Celular o contraseña incorrectos"))
            }
        }

    suspend fun logout() {
        try {
            SupabaseClientConfig.client.auth.signOut()
        } catch (e: Exception) {
            Log.e("UserRepository", "Error al cerrar sesión", e)
        }
        _currentUser.value = null
        _isAnonymous.value = false
    }
}
