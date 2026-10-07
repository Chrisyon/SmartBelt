package com.smartbelt.caregiver.ui.map

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.smartbelt.caregiver.R
import com.smartbelt.caregiver.data.api.ApiClient
import com.smartbelt.caregiver.data.model.BeltLocation
import com.smartbelt.caregiver.data.pref.SessionManager
import com.smartbelt.caregiver.databinding.ActivityMapTrackingBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.util.Locale

class MapTrackingActivity : AppCompatActivity() {

    companion object {
        val lastKnownValidCoords = mutableMapOf<Int, Pair<Double, Double>>()
    }

    private lateinit var binding: ActivityMapTrackingBinding
    private lateinit var sessionManager: SessionManager

    // Lokasi Default Tempat Tinggal (UBAYA)
    private val defaultLat = -7.3198009
    private val defaultLng = 112.7681119

    private var activeLat = defaultLat
    private var activeLng = defaultLng
    private var activeLabel = "Rumah (UBAYA)"

    private var isEmergencyMode = false
    private var pollingJob: Job? = null
    private var initialCentered = false
    private var selectedBeltId: Int? = null

    private var lastBeltLat: Double? = null
    private var lastBeltLng: Double? = null
    private var autoFollowBelt: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName

        binding = ActivityMapTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        binding.btnBack.setOnClickListener { finish() }

        isEmergencyMode = intent.getBooleanExtra("EXTRA_IS_EMERGENCY", false)

        setupMap()
        setupControls()

        if (isEmergencyMode) {
            setupEmergencyMode()
        } else {
            setupTrackingMode()
        }

        binding.btnOpenGoogleMaps.setOnClickListener {
            openInGoogleMaps(activeLat, activeLng, activeLabel)
        }
    }

    private fun setupMap() {
        val mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(17.5)

        val homeLat = sessionManager.getDefaultLatitude()
        val homeLng = sessionManager.getDefaultLongitude()
        activeLat = homeLat
        activeLng = homeLng

        // Pusatkan sedikit offset ke utara agar pin tampak di atas kartu bawah
        val centerPoint = GeoPoint(homeLat - 0.0012, homeLng)
        mapView.controller.setCenter(centerPoint)

        // Langsung gambar penanda awal (Rumah + Lingkaran Geofence + Titik Lansia Awal)
        renderInitialMapElements(homeLat, homeLng)
    }

    private fun setupControls() {
        // 1. Pusatkan ke Posisi Lansia (Smart-Belt)
        binding.btnRecenterBelt?.setOnClickListener {
            autoFollowBelt = true
            val target = GeoPoint(activeLat - 0.0010, activeLng)
            binding.mapView.controller.animateTo(target)
            binding.mapView.controller.setZoom(18.0)
            Toast.makeText(this, "Mengikuti posisi sabuk lansia", Toast.LENGTH_SHORT).show()
        }

        // 2. Pusatkan ke Tempat Tinggal (Pusat Geofence)
        binding.btnRecenterHome?.setOnClickListener {
            autoFollowBelt = false
            val homeLat = sessionManager.getDefaultLatitude()
            val homeLng = sessionManager.getDefaultLongitude()
            val target = GeoPoint(homeLat - 0.0010, homeLng)
            binding.mapView.controller.animateTo(target)
            binding.mapView.controller.setZoom(17.5)
            Toast.makeText(this, "Dipusatkan ke lokasi tempat tinggal", Toast.LENGTH_SHORT).show()
        }

        // 3. Zoom In & Zoom Out
        binding.btnZoomIn?.setOnClickListener {
            binding.mapView.controller.zoomIn()
        }
        binding.btnZoomOut?.setOnClickListener {
            binding.mapView.controller.zoomOut()
        }
    }

    private fun renderInitialMapElements(homeLat: Double, homeLng: Double) {
        val homeLocName = sessionManager.getLokasiDefaultNama()
        val maxDistMeters = sessionManager.getMaxDistanceMeters().toDouble()
        val homePoint = GeoPoint(homeLat, homeLng)

        binding.mapView.overlays.clear()

        // 1. Marker Lokasi Default / Rumah (🏡)
        val homeMarker = Marker(binding.mapView).apply {
            position = GeoPoint(homeLat, homeLng - 0.00012)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "🏡 $homeLocName (Tempat Tinggal)"
            snippet = "Pusat Radius Aman: ${maxDistMeters.toInt()} meter"
            icon = createCustomMarker(isHome = true, isEmergency = false)
        }
        binding.mapView.overlays.add(homeMarker)

        // 2. Lingkaran Geofence Radius Aman (Hijau Transparan)
        val circlePoints = Polygon.pointsAsCircle(homePoint, maxDistMeters)
        val circle = Polygon(binding.mapView).apply {
            points = circlePoints
            fillPaint.color = Color.parseColor("#2610B981") // Hijau transparan 15%
            outlinePaint.color = Color.parseColor("#10B981") // Garis tepi hijau
            outlinePaint.strokeWidth = 3f
            title = "Batas Aman: ${maxDistMeters.toInt()}m"
        }
        binding.mapView.overlays.add(circle)

        // 3. Marker Awal Smart-Belt Lansia (📍)
        val initialBeltMarker = Marker(binding.mapView).apply {
            position = GeoPoint(homeLat, homeLng + 0.00012)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "📍 Smart-Belt (Lansia Terpantau)"
            snippet = "Lansia berada di dalam rumah • Baterai 92%"
            icon = createCustomMarker(isHome = false, isEmergency = false)
        }
        binding.mapView.overlays.add(initialBeltMarker)

        binding.mapView.invalidate()

        // Update Kartu Bawah Info Default
        binding.tvLocationName.text = "Lansia Terpantau"
        binding.tvFullAddress.text = "Kamar Wisma • $homeLocName"
        binding.tvGpsCoords.text = String.format(Locale.US, "%.6f, %.6f", homeLat, homeLng)
        binding.tvDistanceInfo?.text = "0 m (Di Rumah)"
        binding.tvBatteryInfo?.text = "92%"
        binding.tvGpsStatus.text = "DI RUMAH (AMAN)"
        binding.tvGpsStatus.setTextColor(Color.parseColor("#16A34A"))
        binding.tvGpsStatus.setBackgroundResource(R.drawable.bg_badge_online)
    }

    // =========================================================================
    // 1. MODE DARURAT (DIBUKA DARI COUNTDOWN / ALERT JATUH / SOS)
    // =========================================================================
    private fun setupEmergencyMode() {
        val homeLat = sessionManager.getDefaultLatitude()
        val homeLng = sessionManager.getDefaultLongitude()

        val rawLat = intent.getDoubleExtra("EXTRA_LAT", homeLat)
        val rawLng = intent.getDoubleExtra("EXTRA_LNG", homeLng)

        val extraLat = if (rawLat == -3.695436 || rawLat == 0.0) homeLat else rawLat
        val extraLng = if (rawLng == 128.181427 || rawLng == 0.0) homeLng else rawLng

        val extraName = intent.getStringExtra("EXTRA_NAME") ?: "Lansia Terpantau"
        val extraLoc = intent.getStringExtra("EXTRA_LOCATION_NAME") ?: "Kamar Wisma Lansia"
        val extraType = intent.getStringExtra("EXTRA_ALERT_TYPE") ?: "PERINGATAN DARURAT"

        activeLat = extraLat
        activeLng = extraLng
        activeLabel = "$extraType - $extraName"

        val emergencyPoint = GeoPoint(extraLat, extraLng)
        val centerPoint = GeoPoint(extraLat - 0.0012, extraLng)
        binding.mapView.controller.setZoom(18.5)
        binding.mapView.controller.setCenter(centerPoint)

        binding.mapView.overlays.clear()

        // Marker Darurat dengan icon sirine merah 🚨
        val marker = Marker(binding.mapView).apply {
            position = emergencyPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "🚨 $extraType"
            snippet = "$extraName • $extraLoc"
            icon = createCustomMarker(isHome = false, isEmergency = true)
        }
        binding.mapView.overlays.add(marker)
        marker.showInfoWindow()
        binding.mapView.invalidate()

        // Update Card UI
        binding.tvLocationName.text = "🚨 $extraType"
        binding.tvGpsCoords.text = String.format(Locale.US, "%.6f, %.6f", extraLat, extraLng)
        binding.tvGpsStatus.text = "LOKASI JATUH"
        binding.tvGpsStatus.setTextColor(Color.parseColor("#DC2626"))
        binding.tvGpsStatus.setBackgroundResource(R.drawable.bg_badge_emergency)
        binding.tvFullAddress.text = "Pengguna: $extraName • $extraLoc"
        binding.tvDistanceInfo?.text = "INSIDEN AKTIF"
        binding.tvDistanceInfo?.setTextColor(Color.parseColor("#DC2626"))
        binding.tvAvatarEmoji?.text = "🚨"

        // Jalankan polling real-time lokasi agar lokasi sensor TERAKHIR dari alat langsung ter-update di peta!
        setupTrackingMode()
    }

    // =========================================================================
    // 2. MODE PELACAKAN KONTINU DINAMIS (POLLING TIAP 2 DETIK)
    // =========================================================================
    private fun setupTrackingMode() {
        val role = sessionManager.getRole()?.uppercase() ?: "KELUARGA"
        val userId = sessionManager.getUserId()
        val keluargaIdParam = if (role == "ADMIN" || role == "CAREGIVER") null else userId

        startLocationPolling(keluargaIdParam)
    }

    private fun startLocationPolling(keluargaId: Int?) {
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    val api = ApiClient.getApiService(this@MapTrackingActivity)
                    val response = api.getBeltLocations(keluargaId = keluargaId)

                    if (response.isSuccessful && response.body()?.data != null) {
                        val locations = response.body()!!.data!!
                        if (locations.isNotEmpty()) {
                            updateMapWithLocations(locations)
                        }
                    }
                } catch (e: Exception) {
                    // Abaikan gangguan koneksi sesaat, overlay peta tetap tampil aman
                }
                delay(2000) // Poll setiap 2 detik untuk update posisi real-time dan responsif
            }
        }
    }

    private fun updateMapWithLocations(locations: List<BeltLocation>) {
        val homeLat = sessionManager.getDefaultLatitude()
        val homeLng = sessionManager.getDefaultLongitude()
        val homeLocName = sessionManager.getLokasiDefaultNama()
        val maxDistMeters = sessionManager.getMaxDistanceMeters().toDouble()
        val homePoint = GeoPoint(homeLat, homeLng)

        // 0. UPDATE & PERTAHANKAN LOKASI GPS TERAKHIR YANG VALID (LAST KNOWN VALID GPS):
        // Mencegah posisi sabuk meloncat kembali ke rumah jika GPS kehilangan kuncian satelit sesaat saat di dalam ruangan
        for (b in locations) {
            val devId = b.idPerangkat ?: continue
            val bLat = b.latitude
            val bLng = b.longitude
            val isValid = (bLat != null && bLat != 0.0 && bLat != -3.695436 && bLng != null && bLng != 0.0 && bLng != 128.181427)
            if (isValid) {
                lastKnownValidCoords[devId] = Pair(bLat!!, bLng!!)
            } else {
                val cached = lastKnownValidCoords[devId]
                if (cached != null) {
                    b.latitude = cached.first
                    b.longitude = cached.second
                }
            }
        }

        // Cari riwayat sensor dari server jika sabuk baru pertama kali dimuat dan belum ada koordinat GPS valid di cache
        val missingGpsBelts = locations.filter {
            it.latitude == null || it.latitude == 0.0 || it.latitude == -3.695436
        }
        if (missingGpsBelts.isNotEmpty()) {
            lifecycleScope.launch(Dispatchers.IO) {
                val api = ApiClient.getApiService(this@MapTrackingActivity)
                for (b in missingGpsBelts) {
                    val devId = b.idPerangkat ?: continue
                    try {
                        val historyRes = api.getSensorHistory(devId)
                        if (historyRes.isSuccessful && !historyRes.body()?.data.isNullOrEmpty()) {
                            val validSensor = historyRes.body()!!.data!!.firstOrNull {
                                it.latitude != null && it.latitude != 0.0 && it.latitude != -3.695436
                            }
                            if (validSensor != null && validSensor.latitude != null && validSensor.longitude != null) {
                                lastKnownValidCoords[devId] = Pair(validSensor.latitude, validSensor.longitude)
                                withContext(Dispatchers.Main) {
                                    b.latitude = validSensor.latitude
                                    b.longitude = validSensor.longitude
                                    selectBeltLocation(b)
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
        }

        // FILTER PERANGKAT & PILIH PERANGKAT AKTIF:
        // 1. Jika pengguna sudah memilih perangkat (selectedBeltId), gunakan perangkat tersebut
        // 2. Jika belum, cari perangkat yang telemetrinya paling baru (Pico W Chrisyon yang sedang online)
        // 3. Hindari memilih dummy Ambon jika ada sabuk aktif di Jawa Timur / Surabaya
        val validBelts = locations.filter { it.latitude != null && it.latitude != 0.0 && it.latitude != -3.695436 }
        val targetBelt = if (selectedBeltId != null) {
            locations.find { it.idPerangkat == selectedBeltId }
                ?: validBelts.maxByOrNull { it.waktuTerakhir ?: "" }
                ?: locations.first()
        } else {
            validBelts.filter { it.namaPenghuni != null || it.latitude != -3.695436 }
                .maxByOrNull { it.waktuTerakhir ?: "" }
                ?: validBelts.maxByOrNull { it.waktuTerakhir ?: "" }
                ?: locations.first()
        }

        selectedBeltId = targetBelt.idPerangkat

        binding.mapView.overlays.clear()

        // 1. Marker Lokasi Tempat Tinggal (🏡)
        val homeMarker = Marker(binding.mapView).apply {
            position = homePoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "🏡 $homeLocName (Tempat Tinggal)"
            snippet = "Pusat Radius Aman: ${maxDistMeters.toInt()} meter"
            icon = createCustomMarker(isHome = true, isEmergency = false)
            setOnMarkerClickListener { m, _ ->
                m.showInfoWindow()
                true
            }
        }
        binding.mapView.overlays.add(homeMarker)

        // 2. Lingkaran Radius Aman (Geofence Circle)
        val circlePoints = Polygon.pointsAsCircle(homePoint, maxDistMeters)
        val circle = Polygon(binding.mapView).apply {
            points = circlePoints
            fillPaint.color = Color.parseColor("#2610B981")
            outlinePaint.color = Color.parseColor("#10B981")
            outlinePaint.strokeWidth = 3f
            title = "Batas Aman: ${maxDistMeters.toInt()}m"
        }
        binding.mapView.overlays.add(circle)

        // 3. Gambar Marker Setiap Perangkat Sabuk
        var selectedMarker: Marker? = null
        for (belt in locations) {
            // Sembunyikan dummy sabuk tak bertuan jika ada sabuk aktif riil
            if (belt.idPenghuni == null && belt.idPerangkat != selectedBeltId && validBelts.size > 1) {
                continue
            }
            val rawLat = belt.latitude ?: homeLat
            val rawLng = belt.longitude ?: homeLng
            val point = GeoPoint(rawLat, rawLng)
            val isEmerg = (belt.statusDarurat == "AUTO_CALL" || belt.statusDarurat == "COUNTDOWN")
            val isCurrentSelected = (belt.idPerangkat == selectedBeltId)

            val marker = Marker(binding.mapView).apply {
                position = point
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "📍 " + (belt.namaPenghuni ?: belt.namaPerangkat ?: "Smart-Belt")
                snippet = "Kamar: ${belt.alamatKamar ?: "-"} | Baterai: ${belt.statusBaterai ?: 0}%"
                icon = createCustomMarker(isHome = false, isEmergency = isEmerg)

                setOnMarkerClickListener { m, _ ->
                    selectBeltLocation(belt)
                    autoFollowBelt = true
                    m.showInfoWindow()
                    true
                }
            }
            binding.mapView.overlays.add(marker)
            if (isCurrentSelected) {
                selectedMarker = marker
            }
        }

        binding.mapView.invalidate()

        // 4. Update Info di Kartu Bawah
        selectBeltLocation(targetBelt)

        // 5. UPDATE POSISI KAMERA SECARA OTOMATIS:
        // Peta langsung mengarah ke lokasi sabuk terbaru secara real-time tanpa harus keluar masuk halaman
        val beltLat = targetBelt.latitude ?: homeLat
        val beltLng = targetBelt.longitude ?: homeLng
        val distMoved = if (lastBeltLat != null && lastBeltLng != null) {
            calculateDistanceInMeters(beltLat, beltLng, lastBeltLat!!, lastBeltLng!!)
        } else {
            999.0
        }

        if (!initialCentered || (autoFollowBelt && distMoved > 1.0)) {
            val target = GeoPoint(beltLat - 0.0010, beltLng)
            if (!initialCentered) {
                binding.mapView.controller.setZoom(18.0)
                binding.mapView.controller.setCenter(target)
                initialCentered = true
                selectedMarker?.showInfoWindow()
            } else {
                binding.mapView.controller.animateTo(target)
            }
            lastBeltLat = beltLat
            lastBeltLng = beltLng
        }
    }

    private fun selectBeltLocation(belt: BeltLocation) {
        selectedBeltId = belt.idPerangkat
        val homeLat = sessionManager.getDefaultLatitude()
        val homeLng = sessionManager.getDefaultLongitude()
        val maxDist = sessionManager.getMaxDistanceMeters().toDouble()

        val lat = belt.latitude ?: homeLat
        val lng = belt.longitude ?: homeLng

        activeLat = lat
        activeLng = lng
        activeLabel = belt.namaPenghuni ?: belt.namaPerangkat ?: "Smart-Belt"

        val dist = calculateDistanceInMeters(lat, lng, homeLat, homeLng)
        val isOutOfGeofence = dist > maxDist
        val isAtHome = dist < 10.0
        val isEmergency = (belt.statusDarurat == "AUTO_CALL" || belt.statusDarurat == "COUNTDOWN")

        binding.tvLocationName.text = belt.namaPenghuni ?: belt.namaPerangkat ?: "Smart-Belt"
        binding.tvFullAddress.text = "Kamar: ${belt.alamatKamar ?: "-"} • Perangkat: ${belt.namaPerangkat ?: "-"}"
        binding.tvGpsCoords.text = String.format(Locale.US, "%.6f, %.6f", lat, lng)
        binding.tvBatteryInfo?.text = "${belt.statusBaterai ?: 92}%"
        binding.tvAvatarEmoji?.text = if (isEmergency) "🚨" else "👴"

        // Status Jarak & Geofence
        when {
            isOutOfGeofence -> {
                binding.tvDistanceInfo?.text = "${dist.toInt()} m (Keluar Batas)"
                binding.tvDistanceInfo?.setTextColor(Color.parseColor("#DC2626"))
            }
            isAtHome -> {
                binding.tvDistanceInfo?.text = "0 m (Di Rumah)"
                binding.tvDistanceInfo?.setTextColor(Color.parseColor("#16A34A"))
            }
            else -> {
                binding.tvDistanceInfo?.text = "${dist.toInt()} m (Aman)"
                binding.tvDistanceInfo?.setTextColor(Color.parseColor("#16A34A"))
            }
        }

        // Status Badge
        when {
            isEmergency -> {
                binding.tvGpsStatus.text = "🚨 ${belt.statusDarurat}"
                binding.tvGpsStatus.setTextColor(Color.parseColor("#DC2626"))
                binding.tvGpsStatus.setBackgroundResource(R.drawable.bg_badge_emergency)
            }
            isOutOfGeofence -> {
                binding.tvGpsStatus.text = "⚠️ KELUAR BATAS"
                binding.tvGpsStatus.setTextColor(Color.parseColor("#DC2626"))
                binding.tvGpsStatus.setBackgroundResource(R.drawable.bg_badge_emergency)
            }
            isAtHome -> {
                binding.tvGpsStatus.text = "DI RUMAH (AMAN)"
                binding.tvGpsStatus.setTextColor(Color.parseColor("#16A34A"))
                binding.tvGpsStatus.setBackgroundResource(R.drawable.bg_badge_online)
            }
            else -> {
                binding.tvGpsStatus.text = "ONLINE (GPS)"
                binding.tvGpsStatus.setTextColor(Color.parseColor("#16A34A"))
                binding.tvGpsStatus.setBackgroundResource(R.drawable.bg_badge_online)
            }
        }
    }

    private fun createCustomMarker(
        isHome: Boolean = false,
        isEmergency: Boolean = false
    ): BitmapDrawable {
        val density = resources.displayMetrics.density
        val size = (44 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = size / 2f
        val cy = size / 2f
        val radius = 17 * density

        // 1. Bayangan (Shadow Ring)
        paint.color = Color.parseColor("#33000000")
        canvas.drawCircle(cx, cy + 2 * density, radius + 3 * density, paint)

        // 2. Lingkaran Luar Putih (Border Tebal)
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius + 2 * density, paint)

        // 3. Lingkaran Dalam Berwarna
        paint.color = when {
            isHome -> Color.parseColor("#10B981") // Hijau Emerald
            isEmergency -> Color.parseColor("#EF4444") // Merah Bahaya
            else -> Color.parseColor("#2563EB") // Biru Sapphire
        }
        canvas.drawCircle(cx, cy, radius, paint)

        // 4. Emoji / Simbol Ikon di Tengah
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 17 * density
            textAlign = Paint.Align.CENTER
            color = Color.WHITE
        }
        val iconText = when {
            isHome -> "🏡"
            isEmergency -> "🚨"
            else -> "📍"
        }
        val textY = cy - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(iconText, cx, textY, textPaint)

        return BitmapDrawable(resources, bitmap)
    }

    private fun calculateDistanceInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        // Fungsi resmi bawaan dari Android OS SDK:
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble() // Hasil jarak langsung keluar dalam satuan Meter!
    }

    private fun openInGoogleMaps(lat: Double, lng: Double, label: String) {
        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)")
        val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            startActivity(mapIntent)
        } catch (e: Exception) {
            val browserUri = Uri.parse("https://maps.google.com/?q=$lat,$lng")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, browserUri))
            } catch (ex: Exception) {
                Toast.makeText(this@MapTrackingActivity, "Tidak dapat membuka Google Maps", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        setupTrackingMode()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        pollingJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
    }
}
