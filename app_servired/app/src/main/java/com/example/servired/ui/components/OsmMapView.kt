package com.example.servired.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.servired.data.Report
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

// Fuente de mosaicos pública basada en OpenStreetMap (OSM Francia)
val OsmFranceTileSource = object : OnlineTileSourceBase(
    "OSM_France",
    0, 19, 256, ".png",
    arrayOf(
        "https://a.tile.openstreetmap.fr/osmfr/",
        "https://a.tile.openstreetmap.fr/osmfr/",
        "https://a.tile.openstreetmap.fr/osmfr/"
    )
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + mImageFilenameEnding
    }
}

enum class MarkerIconType {
    LOCATION_PIN,
    WRENCH,
    USER_PERSON
}

/**
 * Dibuja un pin de mapa personalizado con un icono vectorial estilizado (Llave inglesa, Usuario o Pin)
 */
fun createCustomMapMarker(
    context: Context,
    iconType: MarkerIconType,
    colorArgb: Int,
    sizePx: Int = 110
): Drawable {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx + 24, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val center = sizePx / 2f
    val radius = sizePx / 2f - 6f

    // 1. Sombra suave en la base
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#33000000")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, sizePx + 10f, 12f, shadowPaint)

    // 2. Cuerpo/Punta del Pin
    val pinPath = Path().apply {
        moveTo(center - 18f, center + radius - 4f)
        lineTo(center + 18f, center + radius - 4f)
        lineTo(center, sizePx + 16f)
        close()
    }

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        style = Paint.Style.FILL
    }

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    canvas.drawPath(pinPath, fillPaint)
    canvas.drawPath(pinPath, borderPaint)

    // 3. Círculo principal
    canvas.drawCircle(center, center, radius, fillPaint)
    canvas.drawCircle(center, center, radius, borderPaint)

    // 4. Dibujar el icono interno en blanco según el tipo de marcador
    val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }

    when (iconType) {
        MarkerIconType.WRENCH -> {
            // Icono de Llave Inglesa (Reportes/Fallas)
            canvas.save()
            canvas.rotate(-45f, center, center)
            // Mango
            canvas.drawRoundRect(
                center - 5f, center - 18f,
                center + 5f, center + 18f,
                4f, 4f, whitePaint
            )
            // Cabeza de la llave
            canvas.drawCircle(center, center - 16f, 13f, whitePaint)
            val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorArgb
                style = Paint.Style.FILL
            }
            canvas.drawRect(center - 4f, center - 24f, center + 4f, center - 12f, cutPaint)
            canvas.restore()
        }

        MarkerIconType.USER_PERSON -> {
            // Icono de Usuario / Persona (Mi Ubicación)
            // Cabeza
            canvas.drawCircle(center, center - 9f, 11f, whitePaint)
            // Hombros / Cuerpo
            val rectF = RectF(center - 19f, center + 3f, center + 19f, center + 27f)
            canvas.drawArc(rectF, 180f, 180f, true, whitePaint)
        }

        MarkerIconType.LOCATION_PIN -> {
            // Punto de Ubicación Seleccionada
            canvas.drawCircle(center, center, 12f, whitePaint)
        }
    }

    return BitmapDrawable(context.resources, bitmap)
}

@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    selectedPoint: GeoPoint? = null,
    reports: List<Report> = emptyList(),
    isInteractiveSelection: Boolean = false,
    onPointSelected: (GeoPoint) -> Unit = {},
    onReportClick: (Report) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val redColor = android.graphics.Color.parseColor("#E53935")
    val blueColor = android.graphics.Color.parseColor("#0288D1")

    // Crear marcadores con iconos vectoriales personalizados (Llave inglesa, Usuario, Pin)
    val locationPinDrawable = remember(context, redColor) {
        createCustomMapMarker(context, MarkerIconType.LOCATION_PIN, redColor)
    }

    val wrenchDrawable = remember(context, primaryColor) {
        createCustomMapMarker(context, MarkerIconType.WRENCH, primaryColor)
    }

    val userPersonDrawable = remember(context, blueColor) {
        createCustomMapMarker(context, MarkerIconType.USER_PERSON, blueColor)
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(OsmFranceTileSource)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            controller.setZoom(14.0)
            val initialCenter = selectedPoint ?: GeoPoint(4.60971, -74.08175) // Bogotá default
            controller.setCenter(initialCenter)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize(),
        update = { map ->
            map.overlays.clear()

            selectedPoint?.let { point ->
                map.controller.animateTo(point)
            }

            if (isInteractiveSelection) {
                // Modo 1: Ubicación seleccionada en el formulario (Pin de ubicación rojo)
                val currentPoint = selectedPoint ?: GeoPoint(4.60971, -74.08175)
                val marker = Marker(map).apply {
                    position = currentPoint
                    title = "Ubicación seleccionada"
                    icon = locationPinDrawable
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(marker)

                val mapEventsReceiver = object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        p?.let {
                            onPointSelected(it)
                            marker.position = it
                            map.invalidate()
                        }
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean {
                        p?.let {
                            onPointSelected(it)
                            marker.position = it
                            map.invalidate()
                        }
                        return true
                    }
                }
                map.overlays.add(MapEventsOverlay(mapEventsReceiver))
            } else {
                // Modo 2: Reportes de fallas en el mapa (Icono de Llave Inglesa)
                android.util.Log.d("OsmMapViewDebug", "Rendering ${reports.size} reports on map")
                reports.forEach { report ->
                    android.util.Log.d("OsmMapViewDebug", "Report ID: ${report.id}, Name: ${report.name}, Lat: ${report.latitude}, Lng: ${report.longitude}")
                    val point = GeoPoint(report.latitude, report.longitude)
                    val marker = Marker(map).apply {
                        position = point
                        title = "${report.serviceType}: ${report.name}"
                        snippet = "${report.municipality} - ${report.dateTime}"
                        icon = wrenchDrawable
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ ->
                            onReportClick(report)
                            true
                        }
                    }
                    map.overlays.add(marker)
                }

                // Marcador para "Mi Ubicación Actual" (Icono de Usuario)
                selectedPoint?.let { userPoint ->
                    val userMarker = Marker(map).apply {
                        position = userPoint
                        title = "Mi Ubicación Actual"
                        snippet = "Estás aquí"
                        icon = userPersonDrawable
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    map.overlays.add(userMarker)
                }
            }

            map.invalidate()
        }
    )
}
