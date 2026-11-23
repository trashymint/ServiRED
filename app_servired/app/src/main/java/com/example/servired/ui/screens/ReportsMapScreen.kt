package com.example.servired.ui.screens

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.servired.data.Report
import com.example.servired.data.ReportRepository
import com.example.servired.ui.components.BottomNavBar
import com.example.servired.ui.components.OsmMapView
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

private fun fetchCurrentGpsLocation(
    context: Context,
    onLocationFetched: (GeoPoint) -> Unit
) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
    try {
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        var loc: Location? = null
        if (isGpsEnabled) {
            loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }
        if (loc == null && isNetworkEnabled) {
            loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }
        if (loc == null) {
            loc = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        }

        if (loc != null) {
            onLocationFetched(GeoPoint(loc.latitude, loc.longitude))
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    onLocationFetched(GeoPoint(location.latitude, location.longitude))
                    locationManager.removeUpdates(this)
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            val provider = if (isGpsEnabled) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            locationManager.requestLocationUpdates(provider, 0L, 0f, listener)
        }
    } catch (e: SecurityException) {
        e.printStackTrace()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsMapScreen(navController: NavController) {
    val context = LocalContext.current
    val reports by ReportRepository.reports.collectAsState()
    val scope = rememberCoroutineScope()

    var focusedPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var selectedReportForValidation by remember { mutableStateOf<Report?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val granted = permissions.values.any { it }
            if (granted) {
                fetchCurrentGpsLocation(context) { geoPoint ->
                    focusedPoint = geoPoint
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        ReportRepository.fetchReportsFromSupabase(context)
        fetchCurrentGpsLocation(context) { geoPoint ->
            focusedPoint = geoPoint
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Mapa de Reportes",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    com.example.servired.ui.components.AccountTopBarAction(navController = navController)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            BottomNavBar(navController = navController)
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OsmMapView(
                modifier = Modifier.fillMaxSize(),
                selectedPoint = focusedPoint,
                reports = reports,
                isInteractiveSelection = false,
                onReportClick = { report ->
                    selectedReportForValidation = report
                }
            )

            // Botón "Mi Ubicación"
            ExtendedFloatingActionButton(
                onClick = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Mi Ubicación"
                    )
                },
                text = {
                    Text("Mi ubicación", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 20.dp)
            )
        }

        // Diálogo / Panel para validar reporte seleccionado en el mapa
        selectedReportForValidation?.let { report ->
            ReportValidationDialog(
                report = report,
                onDismiss = { selectedReportForValidation = null },
                onValidate = {
                    scope.launch {
                        val result = ReportRepository.validateReportInSupabase(report.id)
                        if (result.isSuccess) {
                            android.widget.Toast.makeText(context, "¡Reporte validado con éxito!", android.widget.Toast.LENGTH_LONG).show()
                            selectedReportForValidation = null
                        } else {
                            val errorMsg = result.exceptionOrNull()?.message ?: "No puedes validar tu propio reporte"
                            android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun ReportValidationDialog(
    report: Report,
    onDismiss: () -> Unit,
    onValidate: () -> Unit
) {
    var mediaAttached by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = report.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    text = "Tipo: ${report.serviceType}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (report.validado) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = "Este reporte ha sido validado.",
                            color = Color(0xFF1B5E20),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Text(
                    text = report.description,
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "📍 ${report.municipality} - 📅 ${report.dateTime}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Validación de Evidencia:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                OutlinedButton(
                    onClick = { mediaAttached = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(if (mediaAttached) "📎 Evidencia adjuntada (1 archivo)" else "Adjuntar foto o video de validación")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onValidate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp),
                    tint = Color.White
                )
                Text("Validar", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
