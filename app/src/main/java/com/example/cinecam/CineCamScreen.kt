package com.example.cinecam

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.ripple.rememberRipple
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin

// Main composable entry point for CineCam application
@Composable
fun CineCamScreen(
    viewModel: CineCamViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeTab by viewModel.activeTab.collectAsState()
    val scope = rememberCoroutineScope()

    // Retrieve state variables from ViewModel
    val iso by viewModel.iso.collectAsState()
    val shutterSpeed by viewModel.shutterSpeed.collectAsState()
    val kelvin by viewModel.kelvin.collectAsState()
    val tint by viewModel.tint.collectAsState()
    val focusDistance by viewModel.focusDistance.collectAsState()
    val exposureComp by viewModel.exposureComp.collectAsState()
    val selectedLut by viewModel.selectedLut.collectAsState()
    val lutIntensity by viewModel.lutIntensity.collectAsState()
    
    val grainIntensity by viewModel.grainIntensity.collectAsState()
    val vignetteIntensity by viewModel.vignetteIntensity.collectAsState()
    val anamorphicFlareIntensity by viewModel.anamorphicFlareIntensity.collectAsState()

    val customImgBitmap by viewModel.importedImageBitmap.collectAsState()
    val activeScenicPreset by viewModel.activeScenicPreset.collectAsState()

    // Activity launcher for choosing custom pictures from device gallery
    val imageChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    viewModel.setImportedImage(bitmap)
                    Toast.makeText(context, "Loaded user scene custom image!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to decode image file", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Material 3 dark scaffolding theme
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF0F1115) // Pure cinematic professional dark slate depth
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F1115))
        ) {
            // 2. Viewfinder & Canvas Processing Frame Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
            ) {
                // Main live renderer viewport combines simulated canvas details & actual camera content
                CinematicViewfinder(
                    viewModel = viewModel,
                    activeScenicPreset = activeScenicPreset,
                    customImgBitmap = customImgBitmap,
                    selectedLut = selectedLut,
                    lutIntensity = lutIntensity,
                    iso = iso,
                    exposureComp = exposureComp,
                    kelvin = kelvin,
                    tint = tint,
                    focusDistance = focusDistance,
                    vignetteIntensity = vignetteIntensity,
                    grainIntensity = grainIntensity,
                    flareIntensity = anamorphicFlareIntensity
                )

                // Render technical safe guides (overlaid on standard preview screen)
                SafeGuidesOverlay(viewModel = viewModel)

                // Simulated Focus Peaking & Zebra patterns
                InterferenceOverlays(viewModel = viewModel)

                // Top Floating Status bar
                FloatingHUDTopBar(viewModel = viewModel)
            }

            // 3. Dynamic Manual Camera Control Wheels & Subtitle Dials (Blackmagic Style)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Color(0xFF2F333D),
                        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    ),
                tonalElevation = 8.dp,
                color = Color(0xFF16191F), // High quality camera controller panel color
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    // Quick stats line (ISO, Temp, Shutter, DR, Temp status labels)
                    TechnicalStatusStrip(viewModel = viewModel)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Controls panel switching based on active bottom tab
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                    ) {
                        when (activeTab) {
                            "camera" -> CameraControlsPanel(viewModel = viewModel)
                            "luts" -> LutsStudioPanel(viewModel = viewModel, onChooseImage = { imageChooserLauncher.launch("image/*") })
                            "creative" -> CreativeLabPanel(viewModel = viewModel)
                            "exif" -> ExifFrameCustomizerPanel(viewModel = viewModel)
                            "gallery" -> ProductionGalleryPanel(viewModel = viewModel)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Custom Bottom Navigation Bar matching Material Design 3 guidelines (with active pills)
                    CineBottomNavBar(
                        activeTab = activeTab,
                        onTabSelected = { viewModel.setActiveTab(it) }
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------
// THE VIEWFINDER RENDER PIPELINE & CAMERA FALLBACKS
// ------------------------------------------------------------------------
@Composable
fun CinematicViewfinder(
    viewModel: CineCamViewModel,
    activeScenicPreset: ScenicPreset,
    customImgBitmap: Bitmap?,
    selectedLut: LutSpec,
    lutIntensity: Float,
    iso: Int,
    exposureComp: Float,
    kelvin: Float,
    tint: Float,
    focusDistance: Float,
    vignetteIntensity: Float,
    grainIntensity: Float,
    flareIntensity: Float
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Detect if we have CameraPermission
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) 
            == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    // Permission request contract
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    // Check on startup
    LaunchedEffect(Unit) {
        hasCameraPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // Custom pixel noise factors & Kelvin adjustments applied below via custom Modifier.drawWithContent
    val calculatedMatrix = remember(selectedLut, iso, exposureComp, lutIntensity, kelvin, tint) {
        val noiseVal = (iso - 50) / 3200f
        selectedLut.getComposerMatrix(exposureComp, noiseVal, lutIntensity, kelvin, tint)
    }

    // Interactive focus blur simulation based on focus distance slider:
    // If the focal point (e.g. 0.7f) is far from the object distance, simulate a shallow depth-of-field cine blur
    val simulatedBlurRadius by animateFloatAsState(
        targetValue = if (focusDistance < 0.25f) 15f else if (focusDistance > 0.85f) 8f else 0.1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "Blur focus simulation"
    )

    // Layout Aspect ratio constraint
    val aspectString by viewModel.aspectRatio.collectAsState()
    val targetAspect = when (aspectString) {
        "16:9" -> 16f / 9f
        "4:3" -> 4f / 3f
        "2.39:1" -> 2.39f // CinemaScope
        "1.85:1" -> 1.85f // VistaVision
        "1:1" -> 1f
        else -> 16f / 9f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("cine_viewfinder_container"),
        contentAlignment = Alignment.Center
    ) {
        // Enforce aspect ratio box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(targetAspect)
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFF2C2D35), RoundedCornerShape(4.dp))
        ) {
            // Dual mode:
            // Mode A: Render active scenic preset procedurally in back-end visualizer
            // Mode B: Optional overlay (Note: true CameraX can be optionally configured as background preview)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val paint = Paint().apply {
                            colorFilter = ColorFilter.colorMatrix(calculatedMatrix)
                        }
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
                            this@drawWithContent.drawContent()
                            canvas.restore()
                        }

                        // 2. Linear/Radial Anamorphic Flare Streaks (horizontal flare centered on bright spots)
                        if (flareIntensity > 0.05f) {
                            val flareY = size.height * 0.45f // simulated bright horizon line of flare
                            val flareHeight = 4.dp.toPx() * flareIntensity
                            val flareGlowWidth = size.width * 0.85f * flareIntensity

                            // Blue horizontal streak
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0x1000BFFF),
                                        Color(0xFF33B5E5).copy(alpha = flareIntensity),
                                        Color(0xFFE0F7FA).copy(alpha = flareIntensity * 1.2f),
                                        Color(0xFF33B5E5).copy(alpha = flareIntensity),
                                        Color(0x1000BFFF),
                                        Color.Transparent
                                    ),
                                    startX = size.width / 2f - flareGlowWidth / 2f,
                                    endX = size.width / 2f + flareGlowWidth / 2f
                                ),
                                topLeft = Offset(0f, flareY - flareHeight / 2f),
                                size = Size(size.width, flareHeight)
                            )

                            // Subtle secondary horizontal reflections (hallmark of anamorphic primes)
                            drawCircle(
                                color = Color(0x3300D4FF).copy(alpha = flareIntensity * 0.4f),
                                radius = 28.dp.toPx() * flareIntensity,
                                center = Offset(size.width * 0.25f, flareY)
                            )
                            drawCircle(
                                color = Color(0x229933CC).copy(alpha = flareIntensity * 0.3f),
                                radius = 45.dp.toPx() * flareIntensity,
                                center = Offset(size.width * 0.72f, flareY)
                            )
                        }

                        // 3. Film Vignette overlay
                        if (vignetteIntensity > 0.05f) {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color.Black.copy(alpha = vignetteIntensity * 0.5f),
                                        Color.Black.copy(alpha = vignetteIntensity * 0.95f)
                                    ),
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    radius = size.width / 1.4f
                                ),
                                size = size
                            )
                        }

                        // 4. Kinetic Film Grain
                        if (grainIntensity > 0.05f) {
                            // Simulated dense high-quality salt-pepper noise
                            for (i in 0..12) {
                                val noiseX = kotlin.random.Random.nextFloat() * size.width
                                val noiseY = kotlin.random.Random.nextFloat() * size.height
                                val noiseR = (kotlin.random.Random.nextFloat() * 2f + 1.2f) * grainIntensity
                                drawCircle(
                                    color = if (kotlin.random.Random.nextBoolean()) Color.White else Color.Black,
                                    radius = noiseR,
                                    center = Offset(noiseX, noiseY),
                                    alpha = grainIntensity * 0.3f
                                )
                            }
                        }
                    }
            ) {
                if (customImgBitmap != null) {
                    Image(
                        bitmap = customImgBitmap.asImageBitmap(),
                        contentDescription = "User Import Image Scene Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Procedural Scenic vector generators (renders beautiful geometric compositions)
                    ProceduralSceneBackdrop(presetId = activeScenicPreset.id, simulatedBlurRadius = simulatedBlurRadius)
                }
            }

            // Real CameraX node is rendered underneath inside an AndroidView if permission granted
            // To provide real photo integration! We place it in a small box or overlay so users can toggle it
            // if they want to capture real backgrounds!
            var isUsingRealCamera by remember { mutableStateOf(false) }

            if (hasCameraPermission && isUsingRealCamera) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                                    CameraSelector.DEFAULT_BACK_CAMERA
                                } else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                                    CameraSelector.DEFAULT_FRONT_CAMERA
                                } else {
                                    null
                                }
                                if (cameraSelector != null) {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview
                                    )
                                }
                            } catch (exc: Throwable) {
                                exc.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Quick toggle button for Real Camera feed vs High-fidelity scenic generator
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        if (!hasCameraPermission) {
                            permissionLauncher.launch(android.Manifest.permission.CAMERA)
                        } else {
                            isUsingRealCamera = !isUsingRealCamera
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isUsingRealCamera) Color(0xFF00E5FF).copy(0.18f) else Color.Black.copy(0.6f),
                        contentColor = if (isUsingRealCamera) Color(0xFF00E5FF) else Color.White
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = if (isUsingRealCamera) Icons.Default.Videocam else Icons.Default.Terrain,
                        contentDescription = "Toggle viewfinder stream source",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isUsingRealCamera) "LIVE CAM" else "SCENE SIM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// Draw gorgeous procedural landscape backgrounds to make the app look stunning without hardware Camera
@Composable
fun ProceduralSceneBackdrop(presetId: String, simulatedBlurRadius: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        when (presetId) {
            "desert_dune" -> {
                // Saharan Sky (gradient gold to deep orange)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFFB74D), Color(0xFFFF7043), Color(0xFFD84315))
                    ),
                    size = size
                )
                // Glowing Sun
                drawCircle(
                    color = Color(0xFFFFF176).copy(alpha = 0.9f),
                    radius = w * 0.12f,
                    center = Offset(w * 0.72f, h * 0.35f)
                )
                // Distant Dunes
                val path1 = Path().apply {
                    moveTo(0f, h * 0.65f)
                    quadraticTo(w * 0.3f, h * 0.58f, w * 0.62f, h * 0.68f)
                    quadraticTo(w * 0.85f, h * 0.72f, w, h * 0.62f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(path1, color = Color(0xFFE65100))

                val path2 = Path().apply {
                    moveTo(0f, h * 0.8f)
                    quadraticTo(w * 0.45f, h * 0.88f, w * 0.78f, h * 0.76f)
                    quadraticTo(w * 0.92f, h * 0.74f, w, h * 0.82f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(path2, color = Color(0xFFBF360C))
            }
            "cyber_tokyo" -> {
                // Saturated cyberpunk dark background
                drawRect(color = Color(0xFF03010D), size = size)
                // Skyline silhouettes (Tokyo towers)
                val towerW = w * 0.15f
                drawRect(Color(0xFF0E0B1F), Offset(w * 0.1f, h * 0.4f), Size(towerW, h))
                drawRect(Color(0xFF0A071A), Offset(w * 0.28f, h * 0.25f), Size(towerW * 1.2f, h))
                drawRect(Color(0xFF0E0B1F), Offset(w * 0.55f, h * 0.35f), Size(towerW, h))
                drawRect(Color(0xFF060412), Offset(w * 0.72f, h * 0.48f), Size(towerW * 1.5f, h))

                // Billboard glows
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFFFF007F), Color.Transparent)),
                    topLeft = Offset(w * 0.3f, h * 0.28f),
                    size = Size(w * 0.12f, h * 0.25f)
                )
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF00E5FF), Color.Transparent)),
                    topLeft = Offset(w * 0.58f, h * 0.38f),
                    size = Size(w * 0.1f, h * 0.2f)
                )

                // High Tech Neon glowing spheres
                drawCircle(Color(0xFFFF00FF).copy(0.2f), radius = w * 0.2f, center = Offset(w * 0.35f, h * 0.3f))
                drawCircle(Color(0xFF00FFFF).copy(0.15f), radius = w * 0.3f, center = Offset(w * 0.65f, h * 0.4f))
            }
            "misty_forest" -> {
                // Pale foggy green sky
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF5A6E63), Color(0xFF2E3D35), Color(0xFF141F1A))
                    ),
                    size = size
                )
                // Layer of tree stalks
                val trunkBrush = Brush.horizontalGradient(listOf(Color(0xFF080D0A), Color(0xFF1B2920)))
                for (i in 1..4) {
                    val x = w * (i * 0.22f) + (sin(i.toFloat()) * 30f)
                    drawRect(
                        brush = trunkBrush,
                        topLeft = Offset(x, h * 0.1f),
                        size = Size(20.dp.toPx(), h)
                    )
                }
                // Distant foggy hillside
                val path = Path().apply {
                    moveTo(0f, h * 0.52f)
                    quadraticTo(w * 0.5f, h * 0.46f, w, h * 0.58f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(path, color = Color(0xFF1B2E24).copy(0.7f))
            }
            "vintage_cozy" -> {
                // Dim warm golden study/room atmosphere
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFE5C158).copy(0.45f), Color(0xFF1F1206)),
                        center = Offset(w * 0.25f, h * 0.35f),
                        radius = w * 0.6f
                    ),
                    size = size
                )
                // Warm lamp bulb glow
                drawCircle(
                    color = Color(0xFFFFEE58),
                    radius = 24.dp.toPx(),
                    center = Offset(w * 0.25f, h * 0.35f)
                )
                // Table / Cup silhouette
                val shelfPath = Path().apply {
                    moveTo(0f, h * 0.72f)
                    lineTo(w, h * 0.8f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(shelfPath, color = Color(0xFF120902))
            }
            "glacier_fjord" -> {
                // Alpine Blue cold sky
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFFB3E5FC), Color(0xFF00ACC1))),
                    size = size
                )
                // Icy snowy peak triangles
                val wall1 = Path().apply {
                    moveTo(0f, h * 0.8f)
                    lineTo(w * 0.38f, h * 0.38f)
                    lineTo(w * 0.65f, h * 0.8f)
                    close()
                }
                drawPath(wall1, color = Color(0xFFE1F5FE))

                val wall2 = Path().apply {
                    moveTo(w * 0.4f, h * 0.8f)
                    lineTo(w * 0.75f, h * 0.45f)
                    lineTo(w, h * 0.8f)
                    close()
                }
                drawPath(wall2, color = Color(0xFFB3E5FC))

                // Fjord dark water reflection base
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF006064), Color(0xFF00363A))),
                    topLeft = Offset(0f, h * 0.76f),
                    size = Size(w, h * 0.24f)
                )
            }
            "noir_broadway" -> {
                // Pure dark theatre stage shadow contrast
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF262626), Color(0xFF000000))),
                    size = size
                )
                // Broadway spot cone
                val pathSpot = Path().apply {
                    moveTo(w * 0.5f, -10f)
                    lineTo(w * 0.2f, h)
                    lineTo(w * 0.8f, h)
                    close()
                }
                drawPath(
                    pathSpot,
                    brush = Brush.verticalGradient(listOf(Color.White.copy(0.48f), Color.Transparent))
                )
                // Rain lines on wall
                for (i in 0..15) {
                    val rx = kotlin.random.Random.nextFloat() * w
                    val ry = kotlin.random.Random.nextFloat() * h
                    drawLine(
                        color = Color.White.copy(0.2f),
                        start = Offset(rx, ry),
                        end = Offset(rx - 8f, ry + 25f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------
// LIVE INTERFERENCE OVERLAYS (PEAKING / ZEBRAS / ASPECT GUIDE MASKS)
// ------------------------------------------------------------------------
@Composable
fun InterferenceOverlays(viewModel: CineCamViewModel) {
    val peaking by viewModel.focusPeakingEnabled.collectAsState()
    val zebra by viewModel.zebraEnabled.collectAsState()
    val threshold by viewModel.zebraThreshold.collectAsState()

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Focus Peaking green outlines (Simulate outline traces of prominent contrast regions)
        if (peaking) {
            // Draw stylized highlight edges around simulated scene contours
            // Edge 1: horizon dune / mountain skyline highlight
            drawLine(Color(0xFF39FF14), Offset(0f, h * 0.65f), Offset(w * 0.4f, h * 0.62f), strokeWidth = 1.5.dp.toPx())
            drawLine(Color(0xFF39FF14), Offset(w * 0.4f, h * 0.62f), Offset(w * 0.75f, h * 0.68f), strokeWidth = 1.2.dp.toPx())
            drawLine(Color(0xFF39FF14), Offset(w * 0.75f, h * 0.68f), Offset(w, h * 0.61f), strokeWidth = 1.5.dp.toPx())

            // Edge 2: secondary focal details
            drawCircle(Color(0xFF39FF14), radius = 3.dp.toPx(), center = Offset(w * 0.25f, h * 0.56f), style = Stroke(1.1.dp.toPx()))
            drawCircle(Color(0xFF39FF14), radius = 5.dp.toPx(), center = Offset(w * 0.72f, h * 0.48f), style = Stroke(1.2.dp.toPx()))
        }

        // 2. Zebra Stripes (Overexposure Warning striped masks)
        if (zebra) {
            // Overlay black/white safety stripes in top left diagonal clusters where highlights exceed threshold
            val stripeStep = 18.dp.toPx()
            val stripeHeight = h * 0.25f
            val stripeWidth = w * 0.35f

            // Group together zebra warnings in simulated skies
            for (offset in stripeStep.toInt()..stripeWidth.toInt() step (stripeStep * 1.5f).toInt()) {
                drawLine(
                    color = Color.Yellow.copy(0.7f),
                    start = Offset(offset.toFloat(), 10f),
                    end = Offset(offset.toFloat() - 25f, stripeHeight),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }
    }
}

@Composable
fun SafeGuidesOverlay(viewModel: CineCamViewModel) {
    val guideStyle by viewModel.guideStyle.collectAsState()
    val aspectString by viewModel.aspectRatio.collectAsState()

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        when (guideStyle) {
            "thirds" -> {
                // Vertical lines
                drawLine(Color(0x73FFFFFF), Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 0.8.dp.toPx())
                drawLine(Color(0x73FFFFFF), Offset(2 * w / 3f, 0f), Offset(2 * w / 3f, h), strokeWidth = 0.8.dp.toPx())

                // Horizontal lines
                drawLine(Color(0x73FFFFFF), Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 0.8.dp.toPx())
                drawLine(Color(0x73FFFFFF), Offset(0f, 2 * h / 3f), Offset(w, 2 * h / 3f), strokeWidth = 0.8.dp.toPx())
            }
            "grid" -> {
                // High density grid guides
                for (i in 1..3) {
                    val x = w * (i / 4f)
                    drawLine(Color(0x55E0E0E0), Offset(x, 0f), Offset(x, h), strokeWidth = 0.5.dp.toPx())
                }
                for (i in 1..3) {
                    val y = h * (i / 4f)
                    drawLine(Color(0x55E0E0E0), Offset(0f, y), Offset(w, y), strokeWidth = 0.5.dp.toPx())
                }
            }
            "crosshair" -> {
                // Focus Center reticle with target ring
                drawLine(Color(0xBEFF3333), Offset(w / 2f - 24.dp.toPx(), h / 2f), Offset(w / 2f + 24.dp.toPx(), h / 2f), strokeWidth = 1.dp.toPx())
                drawLine(Color(0xBEFF3333), Offset(w / 2f, h / 2f - 24.dp.toPx()), Offset(w / 2f, h / 2f + 24.dp.toPx()), strokeWidth = 1.dp.toPx())
                drawCircle(Color(0xBEFF3333), radius = 10.dp.toPx(), center = Offset(w / 2f, h / 2f), style = Stroke(0.8.dp.toPx()))
            }
        }
    }
}

// ------------------------------------------------------------------------
// TECHNICAL STATUS COUNTERS & LIVE AUDIO VU-METERS
// ------------------------------------------------------------------------
@Composable
fun TechnicalStatusStrip(viewModel: CineCamViewModel) {
    val iso by viewModel.iso.collectAsState()
    val shutter by viewModel.shutterSpeed.collectAsState()
    val kelvin by viewModel.kelvin.collectAsState()
    val aspect by viewModel.aspectRatio.collectAsState()
    
    val leftVU by viewModel.audioLevelLeft.collectAsState()
    val rightVU by viewModel.audioLevelRight.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF090A0D), RoundedCornerShape(4.dp))
            .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Quick visual indicators
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(containerColor = Color(0xFFD32F2F), contentColor = Color.White) {
                Text(
                    text = "RAW",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(2.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${aspect}  •  ${viewModel.getCodecLabel()}",
                color = Color.White.copy(0.6f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        // Live Audio decibel meters (VU Left/Right channels)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(100.dp)
        ) {
            Text(
                text = "CH1\nCH2",
                color = Color.White.copy(0.5f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 9.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                // VU Green horizontal bar Left
                AudioVUBar(level = leftVU)
                Spacer(modifier = Modifier.height(2.dp))
                // VU Green horizontal bar Right
                AudioVUBar(level = rightVU)
            }
        }

        // Camera temp & storage metrics
        Text(
            text = "${viewModel.getSensorTemperatureLabel()}  |  ${viewModel.getEstimatedStorageMinutes()}",
            color = Color(0xFF00E5FF),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AudioVUBar(level: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Color(0xFF1E2026), RoundedCornerShape(1.dp))
    ) {
        val barColor = if (level > 0.75f) Color.Yellow else if (level > 0.9f) Color.Red else Color(0xFF39FF14)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(level)
                .background(barColor, RoundedCornerShape(1.dp))
        )
    }
}

// ------------------------------------------------------------------------
// MAIN TOP FLOATING HUD OVERLAY
// ------------------------------------------------------------------------
@Composable
fun FloatingHUDTopBar(viewModel: CineCamViewModel) {
    val activeLut by viewModel.selectedLut.collectAsState()
    val lutIntensity by viewModel.lutIntensity.collectAsState()
    var isRecordingSimulated by remember { mutableStateOf(false) }
    
    // Simple pulse animation for REC label
    val infiniteTransition = rememberInfiniteTransition(label = "pulse rec")
    val recAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Active LUT tag
        Surface(
            color = Color.Black.copy(0.6f),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(0.6.dp, Color(0xFF00E5FF).copy(0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FilterFrames,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${activeLut.name.uppercase()} (${(lutIntensity * 100).toInt()}%)",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Live battery and clock
        Surface(
            color = Color.Black.copy(0.6f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = "120 FPS  •  24.00 fps  •  92% BAT",
                color = Color.White.copy(0.8f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

// ------------------------------------------------------------------------
// CONTROLS PANELS (DYNAMIC ACCORDIONS)
// ------------------------------------------------------------------------

// A. CAMERA MANUAL HUD PANEL (Blackmagic wheels)
@Composable
fun CameraControlsPanel(viewModel: CineCamViewModel) {
    val iso by viewModel.iso.collectAsState()
    val shutterSpeed by viewModel.shutterSpeed.collectAsState()
    val kelvin by viewModel.kelvin.collectAsState()
    val tint by viewModel.tint.collectAsState()
    val focusDistance by viewModel.focusDistance.collectAsState()
    val exposureComp by viewModel.exposureComp.collectAsState()

    val peakingEnabled by viewModel.focusPeakingEnabled.collectAsState()
    val zebraEnabled by viewModel.zebraEnabled.collectAsState()
    val guideStyle by viewModel.guideStyle.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "HARDWARE MANUAL LENS ADJUSTMENTS",
            color = Color.White.copy(0.4f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Triple manual sliders row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Slider 1: SENSOR ISO
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF1D2026), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF2F333D), RoundedCornerShape(20.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ISO SENSITIVITY", color = Color(0xFFA8ABB4), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("ISO $iso", color = Color(0xFFD1E4FF), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = iso.toFloat(),
                    onValueChange = { viewModel.setIso(it.toInt()) },
                    valueRange = 50f..3200f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFD1E4FF),
                        activeTrackColor = Color(0xFFD1E4FF),
                        inactiveTrackColor = Color(0xFF2F333D)
                    ),
                    modifier = Modifier.height(28.dp)
                )
                // Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(100, 400, 800, 1600).forEach { choice ->
                        Text(
                            text = "$choice",
                            color = if (iso == choice) Color(0xFFD1E4FF) else Color.White.copy(0.4f),
                            fontSize = 9.sp,
                            fontWeight = if (iso == choice) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clickable { viewModel.setIso(choice) }
                                .padding(2.dp)
                        )
                    }
                }
            }

            // Slider 2: SHUTTER SPEED & DR LIMITER
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF1D2026), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF2F333D), RoundedCornerShape(20.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("SHUTTER ANGLE", color = Color(0xFFA8ABB4), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(shutterSpeed, color = Color(0xFFD1E4FF), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                // Custom Shutter Speed picker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val shutters = listOf("1/24", "1/48", "1/60", "1/120", "1/240")
                    shutters.forEach { item ->
                        Box(
                            modifier = Modifier
                                .background(
                                    if (shutterSpeed == item) Color(0xFFD1E4FF) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (shutterSpeed == item) Color(0xFFD1E4FF) else Color(0xFF2F333D),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.setShutterSpeed(item) }
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = item,
                                color = if (shutterSpeed == item) Color(0xFF003366) else Color(0xFFA8ABB4),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = viewModel.getDynamicRangeLabel(),
                    color = Color.White.copy(0.35f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.5.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Row 2: Kelvin Temperature and Focus Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Slider 3: KELVIN TEMPERATURE & TINT
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF1D2026), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF2F333D), RoundedCornerShape(20.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("WB TEMP", color = Color(0xFFA8ABB4), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFFFFCC33), CircleShape))
                    }
                    Text("${kelvin.toInt()}K", color = Color(0xFFFFCC33), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = kelvin,
                    onValueChange = { viewModel.setKelvin(it) },
                    valueRange = 2000f..10000f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFFCC33),
                        activeTrackColor = Color(0xFFFFCC33),
                        inactiveTrackColor = Color(0xFF2F333D)
                    ),
                    modifier = Modifier.height(28.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TINT: ${tint.toInt()}", color = Color(0xFFA8ABB4), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.width(4.dp))
                    Slider(
                        value = tint,
                        onValueChange = { viewModel.setTint(it) },
                        valueRange = -50f..50f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFFCC33),
                            activeTrackColor = Color(0xFFFFCC33),
                            inactiveTrackColor = Color(0xFF2F333D)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .height(10.dp)
                    )
                }
            }

            // Slider 4: MANUAL LENS FOCUS (Focus distance slider with peaking toggle)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF1D2026), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF2F333D), RoundedCornerShape(20.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("FOCUS", color = Color(0xFFA8ABB4), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFF64B5F6), CircleShape))
                    }
                    // Display either specific distance estimate or Infinity symbol
                    val label = if (focusDistance > 0.9f) "INFINITY (∞)" else String.format(Locale.US, "%.2f M", focusDistance * 5)
                    Text(label, color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = focusDistance,
                    onValueChange = { viewModel.setFocusDistance(it) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF64B5F6),
                        activeTrackColor = Color(0xFF64B5F6),
                        inactiveTrackColor = Color(0xFF2F333D)
                    ),
                    modifier = Modifier.height(28.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PEAKING", color = Color(0xFFA8ABB4), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Switch(
                        checked = peakingEnabled,
                        onCheckedChange = { viewModel.setFocusPeakingEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF64B5F6),
                            checkedTrackColor = Color(0xFF64B5F6).copy(0.3f),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.graphicsLayer(scaleX = 0.6f, scaleY = 0.6f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Overlay Utilities Strip (Aspect ratio, exposure compensation dial)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Exposure compensator
            Row(
                modifier = Modifier
                    .weight(1.2f)
                    .background(Color(0xFF1D2026), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF2F333D), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("EXPOSURE", color = Color(0xFFA8ABB4), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                IconButton(
                    onClick = { viewModel.setExposureComp(exposureComp - 0.5f) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
                Text(
                    text = String.format(Locale.US, "%+.1f", exposureComp),
                    color = Color(0xFFD1E4FF),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
                IconButton(
                    onClick = { viewModel.setExposureComp(exposureComp + 0.5f) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }

            // Aspect mask selector
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF1D2026), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF2F333D), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("MASK", color = Color(0xFFA8ABB4), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Text(
                        text = viewModel.aspectRatio.collectAsState().value,
                        color = Color(0xFFD1E4FF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable { expanded = true }
                            .padding(4.dp)
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(Color(0xFF1D2026))
                    ) {
                        listOf("16:9", "4:3", "1.85:1", "2.39:1", "1:1").forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt, color = Color.White) },
                                onClick = {
                                    viewModel.setAspectRatio(opt)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Guidelines style grid chooser
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF1D2026), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF2F333D), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("GUIDE", color = Color(0xFFA8ABB4), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                val opts = listOf("none", "thirds", "grid", "crosshair")
                val curr = guideStyle
                Box(
                    modifier = Modifier
                        .clickable {
                            val idx = (opts.indexOf(curr) + 1) % opts.size
                            viewModel.setGuideStyle(opts[idx])
                        }
                        .padding(4.dp)
                ) {
                    Text(
                        text = curr.uppercase(),
                        color = Color(0xFFD1E4FF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// B. LUT STUDIO PANEL (60+ Cinematic and gloomy LUT selectors)
@Composable
fun LutsStudioPanel(viewModel: CineCamViewModel, onChooseImage: () -> Unit) {
    val selectedLut by viewModel.selectedLut.collectAsState()
    val lutIntensity by viewModel.lutIntensity.collectAsState()
    val autoAdjust by viewModel.autoAdjustLuts.collectAsState()

    var activeCategoryFilter by remember { mutableStateOf<LutCategory?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CINEMATIC LUT SELECTOR (60+ FILTERS)",
                color = Color.White.copy(0.4f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            // Auto Adjust parameters label
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AUTO-ADAPT", color = Color(0xFFA8ABB4), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Switch(
                    checked = autoAdjust,
                    onCheckedChange = { viewModel.setAutoAdjustLuts(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFD1E4FF),
                        checkedTrackColor = Color(0xFFD1E4FF).copy(0.3f),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.graphicsLayer(scaleX = 0.55f, scaleY = 0.55f)
                )
            }
        }

        // Category Fast Filters scroll
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                FilterChip(
                    selected = activeCategoryFilter == null,
                    onClick = { activeCategoryFilter = null },
                    label = { Text("ALL 61", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = activeCategoryFilter == null,
                        borderColor = Color(0xFF2F333D),
                        selectedBorderColor = Color(0xFFD1E4FF),
                        disabledBorderColor = Color.Transparent,
                        disabledSelectedBorderColor = Color.Transparent,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.dp
                    ),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFD1E4FF),
                        selectedLabelColor = Color(0xFF003366),
                        containerColor = Color(0xFF1D2026),
                        labelColor = Color(0xFFA8ABB4)
                    )
                )
            }
            LutCategory.values().forEach { category ->
                item {
                    val label = category.name.replace("_", " ")
                    FilterChip(
                        selected = activeCategoryFilter == category,
                        onClick = { activeCategoryFilter = category },
                        label = { Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = activeCategoryFilter == category,
                            borderColor = Color(0xFF2F333D),
                            selectedBorderColor = Color(0xFFD1E4FF),
                            disabledBorderColor = Color.Transparent,
                            disabledSelectedBorderColor = Color.Transparent,
                            borderWidth = 1.dp,
                            selectedBorderWidth = 1.dp
                        ),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFD1E4FF),
                            selectedLabelColor = Color(0xFF003366),
                            containerColor = Color(0xFF1D2026),
                            labelColor = Color(0xFFA8ABB4)
                        )
                    )
                }
            }
        }

        // Active selected LUT description
        Text(
            text = "\"${selectedLut.description}\"",
            color = Color(0xFFD1E4FF),
            fontSize = 11.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1D2026), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF2F333D), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Horizontal Carousel scroll of individual LUT specs
        val filteredList = remember(activeCategoryFilter) {
            if (activeCategoryFilter == null) LUTRegistry.luts
            else LUTRegistry.luts.filter { it.category == activeCategoryFilter }
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(end = 12.dp)
        ) {
            items(filteredList) { item ->
                val isSelected = selectedLut.id == item.id
                Box(
                    modifier = Modifier
                        .width(115.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) Color(0xFFD1E4FF) else Color(0xFF1D2026))
                        .border(
                            1.dp,
                            if (isSelected) Color(0xFFD1E4FF) else Color(0xFF2F333D),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { viewModel.setSelectedLut(item) }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = item.name,
                            color = if (isSelected) Color(0xFF003366) else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.category.name.lowercase(),
                            color = if (isSelected) Color(0xFF003366).copy(0.7f) else Color(0xFFA8ABB4),
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Intensity slider blending LUT profile with clean camera footage
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "LUT BLENDING",
                color = Color(0xFFA8ABB4),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(95.dp)
            )
            Slider(
                value = lutIntensity,
                onValueChange = { viewModel.setLutIntensity(it) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFD1E4FF),
                    activeTrackColor = Color(0xFFD1E4FF),
                    inactiveTrackColor = Color(0xFF2F333D)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
            )
            Text(
                "${(lutIntensity * 100).toInt()}%",
                color = Color(0xFFD1E4FF),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Gallery import utility
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onChooseImage,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF1D2026),
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color(0xFF2F333D)),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFFD1E4FF)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("PROCESS PHOTO FROM DISK", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }

            // Quick reset button to remove imported image back to simulated environment
            if (viewModel.importedImageBitmap.collectAsState().value != null) {
                Button(
                    onClick = { viewModel.setImportedImage(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5449)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("CLEAR", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// C. CREATIVE GRADIN SUITE (GRAIN, FLARING, SCENIC CHOOSER)
@Composable
fun CreativeLabPanel(viewModel: CineCamViewModel) {
    val context = LocalContext.current
    val grainIntensity by viewModel.grainIntensity.collectAsState()
    val vignetteIntensity by viewModel.vignetteIntensity.collectAsState()
    val anamorphicFlareIntensity by viewModel.anamorphicFlareIntensity.collectAsState()
    val activePreset by viewModel.activeScenicPreset.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "CREATIVE GRADING MASTER CONTROLS",
            color = Color.White.copy(0.4f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // 1. Scenic backdrop chooser
        Text("SELECT SCENIC VIEWPORT SIMULATION (FALLBACK FOR WEB EMULATORS)", color = Color(0xFFA8ABB4), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(viewModel.scenicPresets) { item ->
                val isSelected = activePreset.id == item.id
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) Color(0xFFD1E4FF) else Color(0xFF1D2026))
                        .border(
                            1.dp,
                            if (isSelected) Color(0xFFD1E4FF) else Color(0xFF2F333D),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { viewModel.setActiveScenicPreset(item) }
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = item.title,
                            color = if (isSelected) Color(0xFF003366) else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.description,
                            color = if (isSelected) Color(0xFF003366).copy(0.7f) else Color.White.copy(0.4f),
                            fontSize = 8.5.sp,
                            maxLines = 2,
                            lineHeight = 10.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Sliders for dynamic properties
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Film Grain controller
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF1D2026), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF2F333D), RoundedCornerShape(20.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("FILM GRAIN", color = Color(0xFFA8ABB4), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("${(grainIntensity * 100).toInt()}%", color = Color(0xFFD1E4FF), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = grainIntensity,
                    onValueChange = { viewModel.setGrainIntensity(it) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFD1E4FF),
                        activeTrackColor = Color(0xFFD1E4FF),
                        inactiveTrackColor = Color(0xFF2F333D)
                    )
                )
            }

            // Anamorphic flare controller
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF1D2026), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF2F333D), RoundedCornerShape(20.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ANAMORPHIC FLARE", color = Color(0xFFA8ABB4), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("${(anamorphicFlareIntensity * 100).toInt()}%", color = Color(0xFFD1E4FF), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = anamorphicFlareIntensity,
                    onValueChange = { viewModel.setAnamorphicFlareIntensity(it) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFD1E4FF),
                        activeTrackColor = Color(0xFFD1E4FF),
                        inactiveTrackColor = Color(0xFF2F333D)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Vignette intensity
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1D2026), RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFF2F333D), RoundedCornerShape(20.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("VIGNETTE INTENSITY", color = Color(0xFFA8ABB4), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.width(135.dp))
            Slider(
                value = vignetteIntensity,
                onValueChange = { viewModel.setVignetteIntensity(it) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFD1E4FF),
                    activeTrackColor = Color(0xFFD1E4FF),
                    inactiveTrackColor = Color(0xFF2F333D)
                ),
                modifier = Modifier.weight(1f)
            )
            Text("${(vignetteIntensity * 100).toInt()}%", color = Color(0xFFD1E4FF), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 6.dp))
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Professional System Settings & Developer Credits Card
        var showSettingsDetails by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1D2026), RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFF2F333D), RoundedCornerShape(20.dp))
                .clickable { showSettingsDetails = !showSettingsDetails }
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "System Settings",
                        tint = Color(0xFFD1E4FF),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SYSTEM SETTINGS & CREDITS",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Icon(
                    imageVector = if (showSettingsDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (showSettingsDetails) "Collapse" else "Expand",
                    tint = Color(0xFFA8ABB4),
                    modifier = Modifier.size(16.dp)
                )
            }

            if (showSettingsDetails) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF2F333D), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Credits section
                Text(
                    text = "CINE DESIGNER & CONTRIBUTOR",
                    color = Color(0xFFA8ABB4),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "@fiozxr_",
                            color = Color(0xFFD1E4FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "github.com/fiozxr",
                            color = Color(0xFFA8ABB4),
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Button(
                        onClick = {
                            val uri = Uri.parse("https://github.com/fiozxr")
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2F333D),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("GITHUB", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Build Target
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PLATFORM DESCRIPTOR", color = Color(0xFFA8ABB4), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text("ANDROID 7.0+ (API 24+)", color = Color(0xFFFFCC33), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("BUILD SPECIFICATION", color = Color(0xFFA8ABB4), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text("v1.0.0 Pro Studio Edition", color = Color(0xFFD1E4FF), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// D. EXIF-FRAME BUILDER PANEL (Creates customizable polaroids and negative rolls)
@Composable
fun ExifFrameCustomizerPanel(viewModel: CineCamViewModel) {
    val cameraModel by viewModel.cameraModel.collectAsState()
    val lensModel by viewModel.lensModel.collectAsState()
    val apertureValue by viewModel.apertureValue.collectAsState()
    val frameStyle by viewModel.exifFrameStyle.collectAsState()
    val locationText by viewModel.customLocation.collectAsState()

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EXIF FILM CONTAINER FRAME BUILDER",
                color = Color(0xFFA8ABB4),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            // Frame style chooser
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("STYLE:", color = Color(0xFFA8ABB4), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                val styles = listOf("Polaroid Matte", "Sleek Black", "Minimalist White", "Film Negative")
                var expanded by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1D2026), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF2F333D), RoundedCornerShape(12.dp))
                        .clickable { expanded = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = frameStyle,
                        color = Color(0xFFD1E4FF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(Color(0xFF1D2026))
                    ) {
                        styles.forEach { sty ->
                            DropdownMenuItem(
                                text = { Text(sty, color = Color.White) },
                                onClick = {
                                    viewModel.setExifFrameStyle(sty)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Live EXIF Frame demonstration preview
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(Color(0xFF0F1115), RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFF2F333D), RoundedCornerShape(20.dp))
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            // Simulated EXIF Card
            FrameExifStructure(
                cameraModel = cameraModel,
                lensModel = lensModel,
                apertureVal = apertureValue,
                location = locationText,
                frameStyle = frameStyle,
                iso = viewModel.iso.collectAsState().value,
                shutter = viewModel.shutterSpeed.collectAsState().value,
                kelvin = viewModel.kelvin.collectAsState().value.toInt(),
                activeLutName = viewModel.selectedLut.collectAsState().value.name,
                aspect = viewModel.aspectRatio.collectAsState().value
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Text inputs for customization
        Text("CUSTOMIZE METADATA STRINGS FOR OUTPUT PRINT", color = Color(0xFFA8ABB4), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = cameraModel,
                onValueChange = { viewModel.setCameraModel(it) },
                label = { Text("Camera Body Label") },
                maxLines = 1,
                textStyle = TextStyle(fontSize = 12.sp, color = Color.White),
                colors = textFieldCineColors(),
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = lensModel,
                onValueChange = { viewModel.setLensModel(it) },
                label = { Text("Lens Profile / Focal") },
                maxLines = 1,
                textStyle = TextStyle(fontSize = 12.sp, color = Color.White),
                colors = textFieldCineColors(),
                modifier = Modifier.weight(1.3f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = apertureValue,
                onValueChange = { viewModel.setApertureValue(it) },
                label = { Text("Aperture / T-Stop") },
                maxLines = 1,
                textStyle = TextStyle(fontSize = 12.sp, color = Color.White),
                colors = textFieldCineColors(),
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = locationText,
                onValueChange = { viewModel.setCustomLocation(it) },
                label = { Text("Location Frame Text") },
                maxLines = 1,
                textStyle = TextStyle(fontSize = 12.sp, color = Color.White),
                colors = textFieldCineColors(),
                modifier = Modifier.weight(2f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Capture Action Trigger Button
        Button(
            onClick = {
                val media = viewModel.captureCinematicPhoto()
                Toast.makeText(context, "Captured master photo! Exif metadata successfully baked using style \"${frameStyle}\". Added to CineCam local log folder.", Toast.LENGTH_LONG).show()
                viewModel.setActiveTab("gallery")
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFD1E4FF),
                contentColor = Color(0xFF003366)
            ),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("action_capture_cinematic_photo")
        ) {
            Icon(Icons.Default.Camera, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "BAKE EXIF-FRAME & SAVE TO CAM LOG",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// Visual layout representer dynamic styles
@Composable
fun FrameExifStructure(
    cameraModel: String,
    lensModel: String,
    apertureVal: String,
    location: String,
    frameStyle: String,
    iso: Int,
    shutter: String,
    kelvin: Int,
    activeLutName: String,
    aspect: String
) {
    val bg = when (frameStyle) {
        "Polaroid Matte" -> Color(0xFFFBFBF9)
        "Minimalist White" -> Color.White
        "Sleek Black" -> Color(0xFF090A0C)
        "Film Negative" -> Color(0xFF1E100B)
        else -> Color.White
    }

    val fg = if (frameStyle == "Sleek Black" || frameStyle == "Film Negative") Color.White else Color.Black

    Surface(
        color = bg,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Simulated square preview placeholder inside the frame card
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mock thumbnail visual block expressing matching color theme style to selected preset
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(90.dp)
                        .background(Color.Gray.copy(0.4f), RoundedCornerShape(2.dp))
                        .border(0.5.dp, fg.copy(0.2f), RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraRoll,
                        contentDescription = null,
                        tint = fg.copy(0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Core tech tags right side
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = cameraModel.uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = fg,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = lensModel,
                        color = fg.copy(0.7f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$apertureVal  •  ASPECT $aspect",
                        color = fg.copy(0.6f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    // Custom latitude mock text label
                    Text(
                        text = location.uppercase(),
                        color = if (frameStyle == "Film Negative") Color(0xFFFF5252) else fg.copy(0.5f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Divider(color = fg.copy(0.12f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

            // Lower border footer carrying dynamic details (ISO, Shutter, Kelvins, LUT profiles)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ISO $iso  |   SHUTTER $shutter s   |   ${kelvin}K WB",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = fg
                )

                Text(
                    text = "CINE-LUT: ${activeLutName.uppercase()}",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = if (fg == Color.Black) Color(0xFF00838F) else Color(0xFF00E5FF)
                )
            }
        }
    }
}

// E. MASTERPIECE PRODUCTION HISTORY LOGS (GALLERY SHOWCASES)
@Composable
fun ProductionGalleryPanel(viewModel: CineCamViewModel) {
    val items = viewModel.capturedGallery
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PRODUCTION LOG MASTER MASTERS GALLERY",
                color = Color.White.copy(0.4f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${items.size} ITEMS RECORDED",
                color = Color(0xFF00E5FF),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Color(0xFF1D2026), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF2F333D), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudQueue, contentDescription = null, tint = Color(0xFFD1E4FF), modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("NO CAPTURED LOGS DETECTED", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("Design custom metadata and bake a capture above to see records.", color = Color(0xFFA8ABB4), fontSize = 9.sp)
                }
            }
        } else {
            // Horizontal card scroller for galleries
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 12.dp)
            ) {
                items(items) { item ->
                    Box(
                        modifier = Modifier
                            .width(240.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1D2026))
                            .border(1.dp, Color(0xFF2F333D), RoundedCornerShape(20.dp))
                            .padding(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Top item row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.cameraModel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, fontFamily = FontFamily.Monospace)
                                    Text(item.lensModel, color = Color(0xFFA8ABB4), fontSize = 9.sp, maxLines = 1)
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.deleteItem(item)
                                        Toast.makeText(context, "Removed capture item from log folder", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete captured clip", tint = Color(0xFFFF5449), modifier = Modifier.size(16.dp))
                                }
                            }

                            // Center technical strip representation
                            Surface(
                                color = Color(0xFF0F1115),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF2F333D), RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("ISO ${item.iso}", color = Color(0xFFFFCC33), fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Text("SHUTTER ${item.shutterSpeed}", color = Color(0xFFD1E4FF), fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                                    Text("${item.kelvin}K WB", color = Color(0xFFFFCC33), fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                                }
                            }

                            // Footer details
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("LUT: ${item.lutName}", color = Color(0xFFD1E4FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text("Style: ${item.frameStyle}", color = Color(0xFFA8ABB4), fontSize = 8.sp)
                                }

                                Badge(containerColor = Color(0xFF1B2F2A), contentColor = Color(0xFF81C784)) {
                                    Text("BAKED OK", fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------
// CUSTOM COMPONENT STYLES & INTERFACES
// ------------------------------------------------------------------------

// Standard TextField cinematically formatted
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun textFieldCineColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color(0xFF1D2026),
    unfocusedContainerColor = Color(0xFF1D2026),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White.copy(0.8f),
    focusedIndicatorColor = Color(0xFFD1E4FF),
    unfocusedIndicatorColor = Color(0xFF2F333D),
    focusedLabelColor = Color(0xFFD1E4FF),
    unfocusedLabelColor = Color(0xFFA8ABB4)
)

// Bottom bar with indicators complying with dynamic dark styling
@Composable
fun CineBottomNavBar(
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF16191F), RoundedCornerShape(24.dp))
            .border(1.dp, Color(0xFF2F333D), RoundedCornerShape(24.dp))
            .padding(vertical = 6.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val navItems = listOf(
            NavDef("camera", "HUD CAP", Icons.Default.Videocam),
            NavDef("luts", "60+ LUTs", Icons.Default.ColorLens),
            NavDef("creative", "EFFECT LAB", Icons.Default.Tune),
            NavDef("exif", "EXIF FRAME", Icons.Default.FilterFrames),
            NavDef("gallery", "CAM LOG", Icons.Default.FolderOpen)
        )

        navItems.forEach { def ->
            val isSelected = activeTab == def.tabId
            val title = def.title

            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFFD1E4FF) else Color(0xFFA8ABB4), label = "text transition animation"
            )

            val pillWidth by animateDpAsState(
                targetValue = if (isSelected) 105.dp else 45.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "pill animation"
            )

            Box(
                modifier = Modifier
                    .width(pillWidth)
                    .height(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) Color(0xFFD1E4FF).copy(0.12f) else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = androidx.compose.foundation.LocalIndication.current,
                        onClick = { onTabSelected(def.tabId) }
                    )
                    .testTag("nav_tab_${def.tabId}")
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = def.icon,
                        contentDescription = null,
                        tint = if (isSelected) Color(0xFFD1E4FF) else Color(0xFFA8ABB4),
                        modifier = Modifier.size(18.dp)
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = title,
                            color = textColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }
    }
}

data class NavDef(
    val tabId: String,
    val title: String,
    val icon: ImageVector
)
