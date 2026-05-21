package com.example.cinecam

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

// Class representing Scenic Preset Simulation (for graceful camera-less environments)
data class ScenicPreset(
    val id: String,
    val title: String,
    val description: String,
    val baseBrightness: Float, // natural light factor
    val baseNoise: Float, // sensor grain factor
    val defaultKelvin: Float,
    val imageUrlPlaceholder: String, // visual scene style descriptor
    val dominantColors: List<Long>
)

// Class representing a captured or imported masterpiece
data class CapturedMedia(
    val id: String,
    val timestamp: String,
    val capturedDate: Date,
    val lutName: String,
    val iso: Int,
    val shutterSpeed: String,
    val kelvin: Int,
    val tint: Int,
    val focusDistance: Float,
    val cameraModel: String,
    val lensModel: String,
    val aperture: String,
    val frameStyle: String, // Modern, Polaroid, Matte, Vintage Film Strip
    val grainIntensity: Float,
    val vignetteIntensity: Float,
    val flareIntensity: Float,
    val aspectName: String,
    val baseScenicPresetId: String,
    val customImageUri: String? = null // if imported from disk
)

class CineCamViewModel : ViewModel() {

    // --- Tab / Mode Navigation ---
    private val _activeTab = MutableStateFlow("camera") // camera, luts, exif, creative, gallery
    val activeTab: StateFlow<String> = _activeTab.asStateFlow()

    fun setActiveTab(tab: String) {
        _activeTab.value = tab
    }

    // --- Camera HUD Manual Controls (Blackmagic style) ---
    private val _iso = MutableStateFlow(400) // 50 to 3200
    val iso: StateFlow<Int> = _iso.asStateFlow()

    private val _shutterSpeed = MutableStateFlow("1/48") // "1/24", "1/48", "1/60", "1/120", "1/240", "1/500" etc
    val shutterSpeed: StateFlow<String> = _shutterSpeed.asStateFlow()

    private val _kelvin = MutableStateFlow(5600f) // 2000K to 12000K
    val kelvin: StateFlow<Float> = _kelvin.asStateFlow()

    private val _tint = MutableStateFlow(0f) // -50f to 50f
    val tint: StateFlow<Float> = _tint.asStateFlow()

    private val _focusDistance = MutableStateFlow(0.7f) // 0f (Macro) to 1.0f (Infinity)
    val focusDistance: StateFlow<Float> = _focusDistance.asStateFlow()

    private val _exposureComp = MutableStateFlow(0f) // -3.0f to +3.0f EV
    val exposureComp: StateFlow<Float> = _exposureComp.asStateFlow()

    private val _lutIntensity = MutableStateFlow(1.0f) // 0f to 1f dial
    val lutIntensity: StateFlow<Float> = _lutIntensity.asStateFlow()

    // --- Advanced Live Utility Toggles ---
    private val _focusPeakingEnabled = MutableStateFlow(true)
    val focusPeakingEnabled: StateFlow<Boolean> = _focusPeakingEnabled.asStateFlow()

    private val _zebraEnabled = MutableStateFlow(false)
    val zebraEnabled: StateFlow<Boolean> = _zebraEnabled.asStateFlow()

    private val _zebraThreshold = MutableStateFlow(0.85f)
    val zebraThreshold: StateFlow<Float> = _zebraThreshold.asStateFlow()

    private val _autoAdjustLuts = MutableStateFlow(true) // Dynamic camera spec adaptation
    val autoAdjustLuts: StateFlow<Boolean> = _autoAdjustLuts.asStateFlow()

    // Safe Guides
    private val _guideStyle = MutableStateFlow("grid") // none, thirds, grid, crosshair
    val guideStyle: StateFlow<String> = _guideStyle.asStateFlow()

    // Aspect Ratio Masks
    private val _aspectRatio = MutableStateFlow("2.39:1") // "16:9", "4:3", "1.85:1", "2.39:1", "1:1"
    val aspectRatio: StateFlow<String> = _aspectRatio.asStateFlow()

    // --- Creative grading suite ---
    private val _grainIntensity = MutableStateFlow(0.20f) // 0f to 1f
    val grainIntensity: StateFlow<Float> = _grainIntensity.asStateFlow()

    private val _vignetteIntensity = MutableStateFlow(0.35f) // 0f to 1f
    val vignetteIntensity: StateFlow<Float> = _vignetteIntensity.asStateFlow()

    private val _anamorphicFlareIntensity = MutableStateFlow(0.40f) // 0f to 1f
    val anamorphicFlareIntensity: StateFlow<Float> = _anamorphicFlareIntensity.asStateFlow()

    // --- Active LUT Spec Selection ---
    private val _selectedLut = MutableStateFlow(LUTRegistry.luts.first())
    val selectedLut: StateFlow<LutSpec> = _selectedLut.asStateFlow()

    fun setSelectedLut(lut: LutSpec) {
        _selectedLut.value = lut
    }

    // --- EXIF Film Frame custom labels ---
    private val _cameraModel = MutableStateFlow("CINE-X SENSOR II")
    val cameraModel: StateFlow<String> = _cameraModel.asStateFlow()

    private val _lensModel = MutableStateFlow("ANAMORPHIC CINE-PRIME")
    val lensModel: StateFlow<String> = _lensModel.asStateFlow()

    private val _apertureValue = MutableStateFlow("f/1.3 t/1.5")
    val apertureValue: StateFlow<String> = _apertureValue.asStateFlow()

    private val _exifFrameStyle = MutableStateFlow("Polaroid Matte") // Polaroid Matte, Sleek Black, Minimalist White, Film Negative
    val exifFrameStyle: StateFlow<String> = _exifFrameStyle.asStateFlow()

    private val _customLocation = MutableStateFlow("RED ROCK CANYON DESERT, USA")
    val customLocation: StateFlow<String> = _customLocation.asStateFlow()

    // Setters for EXIF
    fun setCameraModel(value: String) { _cameraModel.value = value }
    fun setLensModel(value: String) { _lensModel.value = value }
    fun setApertureValue(value: String) { _apertureValue.value = value }
    fun setExifFrameStyle(value: String) { _exifFrameStyle.value = value }
    fun setCustomLocation(value: String) { _customLocation.value = value }

    fun setIso(value: Int) { _iso.value = value.coerceIn(50, 3200) }
    fun setShutterSpeed(value: String) { _shutterSpeed.value = value }
    fun setKelvin(value: Float) { _kelvin.value = value.coerceIn(2000f, 12000f) }
    fun setTint(value: Float) { _tint.value = value.coerceIn(-50f, 50f) }
    fun setFocusDistance(value: Float) { _focusDistance.value = value.coerceIn(0f, 1f) }
    fun setExposureComp(value: Float) { _exposureComp.value = value.coerceIn(-3f, 3f) }
    fun setLutIntensity(value: Float) { _lutIntensity.value = value.coerceIn(0f, 1f) }
    fun setFocusPeakingEnabled(value: Boolean) { _focusPeakingEnabled.value = value }
    fun setZebraEnabled(value: Boolean) { _zebraEnabled.value = value }
    fun setZebraThreshold(value: Float) { _zebraThreshold.value = value.coerceIn(0f, 1f) }
    fun setAutoAdjustLuts(value: Boolean) { _autoAdjustLuts.value = value }
    fun setGuideStyle(value: String) { _guideStyle.value = value }
    fun setAspectRatio(value: String) { _aspectRatio.value = value }
    fun setGrainIntensity(value: Float) { _grainIntensity.value = value.coerceIn(0f, 1f) }
    fun setVignetteIntensity(value: Float) { _vignetteIntensity.value = value.coerceIn(0f, 1f) }
    fun setAnamorphicFlareIntensity(value: Float) { _anamorphicFlareIntensity.value = value.coerceIn(0f, 1f) }

    // --- Scientific Scenic Preset Simulations ---
    val scenicPresets = listOf(
        ScenicPreset("desert_dune", "Sahara Golden Dunes", "Intense warm highlights, golden skylines, deep contrasted sand contours", 0.5f, 0.05f, 6200f, "golden_dunes", listOf(0xFFE5A85A, 0xFF7C4B27, 0xFFFFA235)),
        ScenicPreset("cyber_tokyo", "Shibuya Neon Twilight", "Saturated dark alleys, magenta glowing billboards, slate wet streets", -0.2f, 0.40f, 8500f, "tokyo_neon", listOf(0xFFFF007F, 0xFF00E5FF, 0xFF120338)),
        ScenicPreset("misty_forest", "Moody Pacific Pines", "Grave, diffuse foliage shadows, pale emerald fog, muted highlights", -0.1f, 0.15f, 5000f, "misty_woods", listOf(0xFF1E3A27, 0xFF7C8D80, 0xFF14241A)),
        ScenicPreset("vintage_cozy", "Cozy Jazz Cafe", "Dim tungsten lamps, antique warm wood panelling, high black matte shadows", -0.3f, 0.25f, 3200f, "vintage_cafe", listOf(0xFF8B4513, 0xFFFFCC33, 0xFF351F0D)),
        ScenicPreset("glacier_fjord", "Arctic Glacier Ice", "Low latitude cold steel reflections, turquoise alpine pools, crisp air", 0.2f, 0.10f, 7500f, "glacier_lake", listOf(0xFFADD8E6, 0xFF008080, 0xFF4A687A)),
        ScenicPreset("noir_broadway", "Sin City Broadway Noir", "Harsh retro spotlights, deep absolute black ink drops, slick city rain", -0.4f, 0.35f, 5500f, "noir_alley", listOf(0xFF0F0F0F, 0xFFE0E0E0, 0xFF444444))
    )

    private val _activeScenicPreset = MutableStateFlow(scenicPresets[0])
    val activeScenicPreset: StateFlow<ScenicPreset> = _activeScenicPreset.asStateFlow()

    fun setActiveScenicPreset(preset: ScenicPreset) {
        _activeScenicPreset.value = preset
        // Match base kelvin temp representation for realistic camera specs
        _kelvin.value = preset.defaultKelvin
    }

    // Custom image selection (for mock import and processing)
    private val _importedImageBitmap = MutableStateFlow<Bitmap?>(null)
    val importedImageBitmap: StateFlow<Bitmap?> = _importedImageBitmap.asStateFlow()

    fun setImportedImage(bitmap: Bitmap?) {
        _importedImageBitmap.value = bitmap
        if (bitmap != null) {
            _activeScenicPreset.value = ScenicPreset("imported_photo", "User Custom File", "User selected photo processed in CineCam Studio", 0.0f, 0.1f, 5600f, "uploaded", listOf(0xFF444444, 0xFF888888, 0xFFCCCCCC))
        }
    }

    // --- Masterpiece Gallery Store ---
    val capturedGallery = mutableStateListOf<CapturedMedia>()

    init {
        // Populating some initial masterpiece histories to make the app look stunning on fresh boot
        capturedGallery.addAll(
            listOf(
                CapturedMedia(
                    "cap_1",
                    "Captured on CineCam pro. Preset: Sahara Dunes",
                    Date(System.currentTimeMillis() - 86400000 * 2),
                    "Teal & Orange",
                    100,
                    "1/48",
                    6000,
                    5,
                    0.8f,
                    "CINE-X SENSOR II",
                    "ANAMORPHIC CINE-PRIME",
                    "f/1.4",
                    "Polaroid Matte",
                    0.20f,
                    0.35f,
                    0.40f,
                    "2.39:1",
                    "desert_dune"
                ),
                CapturedMedia(
                    "cap_2",
                    "Captured on CineCam pro. Preset: Shibuya Neon Tail",
                    Date(System.currentTimeMillis() - 86400000),
                    "Cyberwave City",
                    800,
                    "1/60",
                    8500,
                    -12,
                    0.4f,
                    "ALEXA LF CINE",
                    "COOKE ANAMORPHIC 50mm",
                    "f/2.0",
                    "Sleek Black",
                    0.45f,
                    0.50f,
                    0.80f,
                    "2.39:1",
                    "cyber_tokyo"
                ),
                CapturedMedia(
                    "cap_3",
                    "Captured on CineCam pro. Preset: Moody Pacific Pines",
                    Date(System.currentTimeMillis() - 40000000),
                    "Decay Forest",
                    400,
                    "1/48",
                    5200,
                    8,
                    0.9f,
                    "RED V-RAPTOR 8K",
                    "ZEISS DISTAGON t* 35mm",
                    "f/1.4",
                    "Minimalist White",
                    0.15f,
                    0.25f,
                    0.10f,
                    "1.85:1",
                    "misty_forest"
                )
            )
        )

        // Launch simulated sound levels crawler (audio VU meter in real time)
        viewModelScope.launch {
            while (true) {
                _audioLevelLeft.value = Random.nextFloat() * 0.45f + 0.15f + (if (Random.nextFloat() > 0.85) 0.3f else 0f)
                _audioLevelRight.value = _audioLevelLeft.value + (Random.nextFloat() * 0.08f - 0.04f)
                delay(120)
            }
        }
    }

    // --- Live audio meters ---
    private val _audioLevelLeft = MutableStateFlow(0.3f)
    val audioLevelLeft: StateFlow<Float> = _audioLevelLeft.asStateFlow()

    private val _audioLevelRight = MutableStateFlow(0.35f)
    val audioLevelRight: StateFlow<Float> = _audioLevelRight.asStateFlow()

    // --- Action: Cinematic Capture triggers ---
    fun captureCinematicPhoto(): CapturedMedia {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val timestampText = "Captured on CineCam Studio. LUT: ${selectedLut.value.name}"
        
        val media = CapturedMedia(
            id = "cap_${System.currentTimeMillis()}",
            timestamp = timestampText,
            capturedDate = Date(),
            lutName = _selectedLut.value.name,
            iso = _iso.value,
            shutterSpeed = _shutterSpeed.value,
            kelvin = _kelvin.value.toInt(),
            tint = _tint.value.toInt(),
            focusDistance = _focusDistance.value,
            cameraModel = _cameraModel.value,
            lensModel = _lensModel.value,
            aperture = _apertureValue.value,
            frameStyle = _exifFrameStyle.value,
            grainIntensity = _grainIntensity.value,
            vignetteIntensity = _vignetteIntensity.value,
            flareIntensity = _anamorphicFlareIntensity.value,
            aspectName = _aspectRatio.value,
            baseScenicPresetId = _activeScenicPreset.value.id
        )

        capturedGallery.add(0, media)
        return media
    }

    fun deleteItem(media: CapturedMedia) {
        capturedGallery.remove(media)
    }

    // Generate simulated camera state tags
    fun getDynamicRangeLabel(): String {
        return if (_iso.value < 200) "15.2 STOPS DR"
        else if (_iso.value < 800) "14.1 STOPS DR"
        else "12.8 STOPS DR"
    }

    fun getSensorTemperatureLabel(): String {
        val temp = 34.2f + (_iso.value / 3200f) * 8.5f + (Random.nextFloat() * 0.4f - 0.2f)
        return String.format(Locale.US, "%.1f°C", temp)
    }

    fun getCodecLabel(): String {
        return "CINE-RAW 12-BIT"
    }

    fun getEstimatedStorageMinutes(): String {
        val gigabytesRemaining = 124.6f
        val bitrateMbps = 450.0f // RAW proxy dynamic bitrates
        val minutesRemaining = (gigabytesRemaining * 8192) / bitrateMbps
        return "${minutesRemaining.toInt()} MIN LEFT"
    }
}
