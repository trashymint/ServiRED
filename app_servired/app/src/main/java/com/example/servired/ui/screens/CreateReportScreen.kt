package com.example.servired.ui.screens

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.servired.data.DaneLocation
import com.example.servired.data.DaneRepository
import com.example.servired.data.Report
import com.example.servired.data.ReportRepository
import com.example.servired.data.UserRepository
import com.example.servired.ui.components.OsmMapView
import com.example.servired.ui.media.CameraXOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

private fun fetchCurrentDeviceLocation(
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
fun CreateReportScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Cargar municipios y DANE desde API pública
    val daneLocations by DaneRepository.daneLocations.collectAsState()
    val serviciosList by ReportRepository.serviciosList.collectAsState()

    LaunchedEffect(Unit) {
        DaneRepository.loadDaneLocations(context)
        ReportRepository.fetchServiciosFromSupabase()
    }

    var faultType by remember { mutableStateOf("") }
    var serviceType by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    
    // Auto-filled date & time
    val dateTime = remember { ReportRepository.getCurrentFormattedDateTime() }

    // Map selection (default: Bogotá center)
    var selectedPoint by remember { mutableStateOf(GeoPoint(4.60971, -74.08175)) }

    // DANE & Municipality selection
    var selectedLocation by remember { mutableStateOf<DaneLocation?>(null) }
    var expandedDaneMenu by remember { mutableStateOf(false) }

    LaunchedEffect(daneLocations) {
        if (selectedLocation == null && daneLocations.isNotEmpty()) {
            selectedLocation = daneLocations.first()
        }
    }

    LaunchedEffect(serviciosList) {
        if (serviciosList.isNotEmpty() && serviceType.isBlank()) {
            serviceType = serviciosList.first().tipo
        }
    }

    // Service Type dropdown
    var expandedServiceMenu by remember { mutableStateOf(false) }

    // Attached Media URIs
    val mediaUris = remember { mutableStateListOf<Uri>() }

    // Camera overlay active state
    var showCameraX by remember { mutableStateOf(false) }

    // Confirmation Pop-Up State
    var showSuccessDialog by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(3) }
    var formError by remember { mutableStateOf<String?>(null) }

    // Gallery Picker Launcher
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                mediaUris.addAll(uris)
            }
        }
    )

    // Permission Launcher for Camera
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                showCameraX = true
            }
        }
    )

    // Permission Launcher for Location ("Mi Ubicación")
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val granted = permissions.values.any { it }
            if (granted) {
                fetchCurrentDeviceLocation(context) { geoPoint ->
                    selectedPoint = geoPoint
                    coroutineScope.launch {
                        val nearestMunicipality = DaneRepository.findNearestMunicipality(
                            context,
                            geoPoint.latitude,
                            geoPoint.longitude
                        )
                        selectedLocation = nearestMunicipality
                    }
                }
            }
        }
    )

    val isAnonymousUser by UserRepository.isAnonymous.collectAsState()

    fun handleFinishReport() {
        showSuccessDialog = false
        if (isAnonymousUser) {
            UserRepository.setAnonymousMode(false)
            navController.navigate("login") {
                popUpTo("login") { inclusive = true }
            }
        } else {
            val popped = navController.popBackStack()
            if (!popped) {
                navController.navigate("my_reports") {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                }
            }
        }
    }

    // Timer countdown effect for confirmation pop-up
    LaunchedEffect(showSuccessDialog) {
        if (showSuccessDialog) {
            countdown = 3
            while (countdown > 0) {
                delay(1000L)
                countdown--
            }
            handleFinishReport()
        }
    }

    if (showCameraX) {
        CameraXOverlay(
            onImageCaptured = { uri ->
                mediaUris.add(uri)
                showCameraX = false
            },
            onClose = { showCameraX = false }
        )
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Crear Nuevo Reporte",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. SERVICIO QUE SE REPORTA
            Text("Servicio que se reporta", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))
            ExposedDropdownMenuBox(
                expanded = expandedServiceMenu,
                onExpandedChange = { expandedServiceMenu = it }
            ) {
                OutlinedTextField(
                    value = serviceType,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedServiceMenu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = expandedServiceMenu,
                    onDismissRequest = { expandedServiceMenu = false }
                ) {
                    serviciosList.forEach { service ->
                        DropdownMenuItem(
                            text = { Text(service.tipo) },
                            onClick = {
                                serviceType = service.tipo
                                expandedServiceMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. TIPO DE FALLA
            Text("Tipo de falla", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = faultType,
                onValueChange = { faultType = it; formError = null },
                placeholder = { Text("Ej. Fuga de agua, Corte no programado, etc.") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. DESCRIPCIÓN DEL PROBLEMA
            Text("Descripción del problema (opcional)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it; formError = null },
                placeholder = { Text("Describe detalladamente el daño o incidente...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. FECHA Y HORA (Auto-llenado)
            Text("Fecha y Hora (Auto-registrada)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Box(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = dateTime,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. UBICACIÓN CON MAPA EMBEDIDO OPENSTREETMAPS Y BOTÓN "MI UBICACIÓN"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Ubicación del reporte",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )

                OutlinedButton(
                    onClick = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Mi Ubicación",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Mi ubicación", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                OsmMapView(
                    modifier = Modifier.fillMaxSize(),
                    selectedPoint = selectedPoint,
                    isInteractiveSelection = true,
                    onPointSelected = { newPoint ->
                        selectedPoint = newPoint
                        coroutineScope.launch {
                            val nearestMunicipality = DaneRepository.findNearestMunicipality(
                                context,
                                newPoint.latitude,
                                newPoint.longitude
                            )
                            selectedLocation = nearestMunicipality
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Coordenadas: Lat ${"%.5f".format(selectedPoint.latitude)}, Lng ${"%.5f".format(selectedPoint.longitude)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // MUNICIPIO Y CÓDIGO DANE
            Text("Municipio, Código DANE y Departamento", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val currentLoc = selectedLocation
            ExposedDropdownMenuBox(
                expanded = expandedDaneMenu,
                onExpandedChange = { expandedDaneMenu = it }
            ) {
                OutlinedTextField(
                    value = if (currentLoc != null) "${currentLoc.municipality} - DANE: ${currentLoc.daneCode} (${currentLoc.department})" else "Cargando municipios...",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDaneMenu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = expandedDaneMenu,
                    onDismissRequest = { expandedDaneMenu = false }
                ) {
                    daneLocations.forEach { loc ->
                        DropdownMenuItem(
                            text = { Text("${loc.municipality} (DANE: ${loc.daneCode}) - ${loc.department}") },
                            onClick = {
                                selectedLocation = loc
                                expandedDaneMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 6. ADJUNTAR PRUEBAS (FOTOS Y VIDEOS)
            Text("Adjuntar pruebas (Fotos o Videos)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(mediaUris) { uri ->
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Prueba adjunta",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        IconButton(
                            onClick = { mediaUris.remove(uri) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .background(Color.Red, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Eliminar",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                item {
                    // Tomar foto con CámaraX
                    FilledTonalIconButton(
                        onClick = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.size(60.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "CámaraX",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("Cámara", fontSize = 10.sp)
                        }
                    }
                }

                item {
                    // Cargar de Galería
                    FilledTonalIconButton(
                        onClick = {
                            pickMediaLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        modifier = Modifier.size(60.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Galería",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text("Galería", fontSize = 10.sp)
                        }
                    }
                }
            }

            if (formError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = formError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 7. BOTÓN ENVIAR REPORTE
            Button(
                onClick = {
                    if (faultType.isBlank()) {
                        formError = "Por favor ingresa el tipo de falla."
                        return@Button
                    }

                    val loc = selectedLocation ?: DaneLocation("Bogotá, D.C.", "11001", "Bogotá D.C.")
                    val currentUserId = UserRepository.currentUser.value?.idUsuario

                    val newReport = Report(
                        userId = currentUserId,
                        name = faultType.trim(),
                        serviceType = serviceType,
                        description = description.trim(),
                        dateTime = dateTime,
                        latitude = selectedPoint.latitude,
                        longitude = selectedPoint.longitude,
                        daneCode = loc.daneCode,
                        municipality = loc.municipality,
                        department = loc.department,
                        mediaUris = mediaUris.map { it.toString() }
                    )

                    coroutineScope.launch {
                        ReportRepository.addReport(newReport)
                    }
                    showSuccessDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    "Enviar Reporte",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // CONFIRMATION POP-UP DIALOG (3 segundos + Botón OK)
        if (showSuccessDialog) {
            AlertDialog(
                onDismissRequest = {
                    handleFinishReport()
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(48.dp)
                    )
                },
                title = {
                    Text(
                        "¡Envío Exitoso!",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (isAnonymousUser) "Tu reporte anónimo fue guardado exitosamente."
                            else "Tu reporte fue guardado exitosamente.",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Volviendo al inicio en $countdown segundo(s)...",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            handleFinishReport()
                        }
                    ) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}
