package com.example.servired.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.servired.data.UserRepository
import kotlinx.coroutines.launch

@Composable
fun AccountTopBarAction(navController: NavController) {
    val currentUser by UserRepository.currentUser.collectAsState()
    val isAnonymous by UserRepository.isAnonymous.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val userName = if (isAnonymous) {
        "Usuario Anónimo"
    } else {
        currentUser?.nombre ?: "Mi Cuenta"
    }

    val userSubtitle = currentUser?.email ?: currentUser?.celular ?: ""

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = "Mi Cuenta"
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = userName,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (userSubtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = userSubtitle,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        DropdownMenuItem(
            text = {
                Text(
                    if (isAnonymous) "Volver al Login" else "Cerrar Sesión",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            onClick = {
                expanded = false
                coroutineScope.launch {
                    UserRepository.logout()
                    navController.navigate("login") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            }
        )
    }
}
