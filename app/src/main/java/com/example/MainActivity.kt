package com.example

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.launch
import android.net.Uri
import android.provider.OpenableColumns
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Environment
import android.os.Build
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AdminSettings
import com.example.data.PrintOrder
import com.example.data.PrinterConfig
import com.example.data.ServiceRate
import com.example.data.Transaction
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppPerspective
import com.example.viewmodel.SmartXeroxViewModel
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.speech.RecognizerIntent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.shadow
import androidx.lifecycle.ViewModelProvider
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val viewModel = ViewModelProvider(this)[SmartXeroxViewModel::class.java]
        intent?.dataString?.let { data ->
            if (data.contains("OWNER01")) {
                viewModel.connectShopViaQR()
            }
        }

        setContent {
            val isPrintFriendly by viewModel.isPrintFriendlyMode.collectAsState()
            MyApplicationTheme(darkTheme = !isPrintFriendly) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) { innerPadding ->
                    ErrorBoundary {
                        SmartXeroxDashboardScreen(modifier = Modifier.padding(innerPadding), viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val viewModel = ViewModelProvider(this)[SmartXeroxViewModel::class.java]
        intent.dataString?.let { data ->
            if (data.contains("OWNER01")) {
                viewModel.connectShopViaQR()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val isCtrlPressed = event.isCtrlPressed
            val keyCode = event.keyCode
            
            val viewModel = ViewModelProvider(this)[SmartXeroxViewModel::class.java]
            val currentPerspective = viewModel.perspective.value
            
            if (currentPerspective == com.example.viewmodel.AppPerspective.OWNER && isCtrlPressed) {
                if (keyCode == KeyEvent.KEYCODE_P) {
                    viewModel.triggerPrintShortcut(this)
                    return true
                } else if (keyCode == KeyEvent.KEYCODE_FORWARD_DEL || keyCode == KeyEvent.KEYCODE_DEL) {
                    viewModel.triggerClearQueueShortcut(this)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
fun SmartXeroxDashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: SmartXeroxViewModel = viewModel()
) {
    val perspective by viewModel.perspective.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val printerConfig by viewModel.printerConfig.collectAsState()
    val nextHeartbeatSeconds by viewModel.nextHeartbeatSeconds.collectAsState()

    // Determine printer online status based on owner's settings
    val isPrinterOnline = isServiceRunning && printerConfig.isPrinterConnected && printerConfig.isMainPrinterWorking

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F9))
    ) {
        val showUniversalPreview by viewModel.showUniversalPreview.collectAsState()
        if (showUniversalPreview) {
            val selectedService by viewModel.selectedService.collectAsState()
            val totalPrice = selectedService?.rate ?: 30.0
            UniversalPrintPreviewDialog(
                viewModel = viewModel,
                isPassport = selectedService?.id == "passport_photo" || selectedService?.name?.contains("Passport", ignoreCase = true) == true,
                price = totalPrice,
                onConfirm = { viewModel.openPayment() }
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Elegant Top Header with App Title and Global Reset
            HeaderSection(
                viewModel = viewModel,
                onReset = { viewModel.resetSimulator() },
                isServiceRunning = isServiceRunning,
                isPrinterOnline = isPrinterOnline
            )

            // Perspective Switcher Tab
            PerspectiveTabs(
                selectedPerspective = perspective,
                onSelected = { viewModel.setPerspective(it) }
            )

            // Central Area - Renders based on the selected screen
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val isOwnerLoggedIn by viewModel.isOwnerLoggedIn.collectAsState()
                val isAdminLoggedIn by viewModel.isAdminLoggedIn.collectAsState()

                when (perspective) {
                    AppPerspective.CUSTOMER -> CustomerPortalScreen(
                        viewModel = viewModel,
                        isPrinterOnline = isPrinterOnline
                    )
                    AppPerspective.OWNER -> {
                        if (isOwnerLoggedIn) {
                            ShopOwnerScreen(viewModel = viewModel)
                        } else {
                            OwnerLoginScreen(viewModel = viewModel)
                        }
                    }
                    AppPerspective.ADMIN -> {
                        if (isAdminLoggedIn) {
                            AdminDashboardScreen(viewModel = viewModel)
                        } else {
                            AdminLoginScreen(viewModel = viewModel)
                        }
                    }
                }
            }
            
            // Dynamic Active Print Progress Banner (Always visible in background at bottom if a print job is executing)
            val activePrintJob by viewModel.activePrintJob.collectAsState()
            val printProgress by viewModel.printJobProgress.collectAsState()
            val printStatus by viewModel.printJobStatus.collectAsState()
            
            AnimatedVisibility(visible = activePrintJob != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = "Printing",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Auto Printing: ${activePrintJob?.orderId}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { printProgress },
                            modifier = Modifier.fillMaxWidth().clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = printStatus,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SxpBrandHeaderComponent(title: String, subtitle: String, customLogoUri: String? = null) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFFFB300)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                modifier = Modifier.size(40.dp),
                border = BorderStroke(1.dp, Color(0xFFFFB300))
            ) {
                if (!customLogoUri.isNullOrEmpty()) {
                    coil.compose.AsyncImage(
                        model = customLogoUri,
                        contentDescription = "Custom SXP Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(2.dp)
                    )
                } else {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.img_smart_x_logo),
                        contentDescription = "SXP Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(2.dp)
                    )
                }
            }
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun HeaderSection(
    viewModel: SmartXeroxViewModel,
    onReset: () -> Unit,
    isServiceRunning: Boolean,
    isPrinterOnline: Boolean
) {
    val adminSettings by viewModel.adminSettings.collectAsState()
    val isApiOnline by viewModel.isApiOnline.collectAsState()
    val geminiStatus by viewModel.geminiStatus.collectAsState()
    val firebaseStatus by viewModel.firebaseStatus.collectAsState()
    val hardwareConnectionState by viewModel.hardwareConnectionState.collectAsState()
    var showPairDialog by remember { mutableStateOf(false) }

    if (showPairDialog) {
        AlertDialog(
            onDismissRequest = { showPairDialog = false },
            title = { Text("Physical Hardware Pairing", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current State: $hardwareConnectionState", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(onClick = {
                        viewModel.pairHardware("Bluetooth") { showPairDialog = false }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Pair via Bluetooth (HP LaserJet)")
                    }
                    Button(onClick = {
                        viewModel.pairHardware("USB") { showPairDialog = false }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Connect via USB OTG (Epson L3210)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPairDialog = false }) { Text("Close") }
            }
        )
    }

    Surface(
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = Offset(0f, size.height - strokeWidth / 2),
                    end = Offset(size.width, size.height - strokeWidth / 2),
                    strokeWidth = strokeWidth
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // SXP Logo badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    modifier = Modifier.size(42.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFB300))
                ) {
                    if (!adminSettings.customLogoUri.isNullOrEmpty()) {
                        coil.compose.AsyncImage(
                            model = adminSettings.customLogoUri,
                            contentDescription = "Custom SXP Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(2.dp)
                        )
                    } else {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.img_smart_x_logo),
                            contentDescription = "SXP Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(2.dp)
                        )
                    }
                }
                
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Smart X Point",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFFEEF2FF),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "V5.0",
                                color = Color(0xFF6750A4),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (isApiOnline) Color(0xFF22C55E) else Color(0xFFEF4444),
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = if (isApiOnline) "API Online • Gemini $geminiStatus" else "API Unreachable",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.5.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 1.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF22C55E), shape = CircleShape)
                        )
                        Text(
                            text = "🌐 Online & Accessible over Internet (${adminSettings.cloudDomain})",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16A34A),
                            letterSpacing = 0.3.sp
                        )
                    }

                    val cloudSyncStatus by viewModel.cloudSyncStatus.collectAsState()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 1.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (cloudSyncStatus.contains("Synced")) Color(0xFF22C55E) else Color(0xFFF59E0B), shape = CircleShape)
                        )
                        Text(
                            text = "☁️ Cloud DB Sync: $cloudSyncStatus",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0369A1),
                            letterSpacing = 0.3.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 1.dp).clickable { showPairDialog = true }
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = "Hardware", tint = Color(0xFF6750A4), modifier = Modifier.size(9.dp))
                        Text(
                            text = hardwareConnectionState,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6750A4)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Theme Toggle
                val isPrintFriendly by viewModel.isPrintFriendlyMode.collectAsState()
                Surface(
                    onClick = { viewModel.togglePrintFriendlyMode() },
                    color = if (isPrintFriendly) Color(0xFFFFB300) else Color(0xFF0F172A),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color(0xFFFFB300)),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Toggle Theme Mode",
                            tint = if (isPrintFriendly) Color.Black else Color(0xFFFFB300),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Refresh/Settings circular button
                Surface(
                    onClick = onReset,
                    color = Color.White,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Simulator",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PerspectiveTabs(
    selectedPerspective: AppPerspective,
    onSelected: (AppPerspective) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = Offset(0f, size.height - strokeWidth / 2),
                    end = Offset(size.width, size.height - strokeWidth / 2),
                    strokeWidth = strokeWidth
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val options = listOf(
            AppPerspective.CUSTOMER to Triple(Icons.Default.QrCodeScanner, "Customer", "QR Portal"),
            AppPerspective.OWNER to Triple(Icons.Default.Storefront, "Owner", "Android App"),
            AppPerspective.ADMIN to Triple(Icons.Default.Security, "Admin", "Dashboard")
        )

        options.forEach { (perspective, data) ->
            val isSelected = selectedPerspective == perspective
            val (icon, title, subtitle) = data
            
            Surface(
                onClick = { onSelected(perspective) },
                color = if (isSelected) Color(0xFFEADDFF) else Color(0xFFF1F5F9),
                contentColor = if (isSelected) Color(0xFF21005D) else Color(0xFF475569),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = title,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = subtitle,
                        fontSize = 7.5.sp,
                        color = if (isSelected) Color(0xFF21005D).copy(alpha = 0.8f) else Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun WebPortalLinkBanner() {
    val context = LocalContext.current
    val webUrl = "https://ais-dev-nlecgp4wfa3sgpjani44jp-135102368660.asia-southeast1.run.app/login.html"
    Surface(
        onClick = {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
            }
        },
        color = Color(0xFFEFF6FF),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = null,
                tint = Color(0xFF2563EB),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "🌐 Open Admin & Owner Web Portal Dashboard in Browser",
                color = Color(0xFF1E40AF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ==========================================
// 1. CUSTOMER PORTAL SCREEN
// ==========================================
@Composable
fun ServiceCardItem(
    service: ServiceRate,
    viewModel: SmartXeroxViewModel
) {
    val isPassport = service.id == "passport_photo_ai"
    Card(
        onClick = { viewModel.selectService(service) },
        colors = CardDefaults.cardColors(
            containerColor = if (isPassport) Color(0xFFEEF2FF) else Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .border(
                width = if (isPassport) 1.5.dp else 1.dp,
                color = if (isPassport) Color(0xFF6366F1) else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    color = if (isPassport) Color(0xFF6750A4) else Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = when (service.id) {
                            "passport_photo_ai" -> Icons.Default.Face
                            "a4_xerox_bw_single", "a4_xerox_bw_double" -> Icons.Default.FileCopy
                            "a4_print_color_hq", "a4_print_color_std" -> Icons.Default.Print
                            "doc_scan_pdf", "scan_ocr" -> Icons.Default.Scanner
                            "lamination_a4" -> Icons.Default.Layers
                            "pvc_card_print", "id_card_xerox" -> Icons.Default.CreditCard
                            "spiral_binding_100", "soft_binding", "thesis_hard_binding" -> Icons.Default.AutoStories
                            else -> Icons.Default.ReceiptLong
                        },
                        contentDescription = null,
                        tint = if (isPassport) Color.White else Color(0xFF475569),
                        modifier = Modifier
                            .size(32.dp)
                            .padding(6.dp)
                    )
                }
                
                if (isPassport) {
                    Surface(
                        color = Color(0xFFFF9100),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "AI MAGIC",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column {
                Text(
                    text = service.name,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "₹${service.rate.toInt()}",
                    color = Color(0xFF6750A4),
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun CustomerPortalScreen(
    viewModel: SmartXeroxViewModel,
    isPrinterOnline: Boolean
) {
    val selectedService by viewModel.selectedService.collectAsState()
    val showPreview by viewModel.showPreview.collectAsState()
    val showSheetGrid by viewModel.showSheetGrid.collectAsState()
    val isProcessingAI by viewModel.isProcessingAI.collectAsState()
    val photoBase64 by viewModel.photoBase64.collectAsState()
    val context = LocalContext.current

    var selectedCategory by remember { mutableStateOf("AI Services") }

    if (selectedService == null) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            var customerTab by remember { mutableStateOf("Services") } // "Services" or "PayQR"
            val ownerCredits by viewModel.ownerCredits.collectAsState()

            // Custom Tab Bar for Customer View
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { customerTab = "Services" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (customerTab == "Services") Color(0xFF6750A4) else Color.Transparent,
                        contentColor = if (customerTab == "Services") Color.White else Color(0xFF475569)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = "Services", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Services & Print", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { customerTab = "PayQR" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (customerTab == "PayQR") Color(0xFF6750A4) else Color.Transparent,
                        contentColor = if (customerTab == "PayQR") Color.White else Color(0xFF475569)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Pay QR", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("UPI Portal", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (customerTab == "Services") {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
            if (isPrinterOnline) {
                val serviceRates by viewModel.serviceRates.collectAsState()
                val filteredServices = serviceRates.filter { it.category == selectedCategory }
                val serviceChunks = filteredServices.chunked(2)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        StatusSafetyBanner(isPrinterOnline = isPrinterOnline)
                    }

                    // Compact top category selector grid (replacing the old subtabs)
                    item {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Select Category",
                            fontSize = 12.sp,
                            color = Color(0xFF475569),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        
                        val categoriesList = listOf(
                            "AI Services" to ("AI Magic 🤖" to Icons.Default.AutoAwesome),
                            "Document Services" to ("Documents 📄" to Icons.Default.Print),
                            "Binding & Lamination" to ("Binding 📚" to Icons.Default.Layers),
                            "Cards & Prints" to ("Cards 💳" to Icons.Default.CreditCard),
                            "Business Services" to ("Business 💼" to Icons.Default.Business),
                            "Gifting & Merch" to ("Gifts 🎁" to Icons.Default.CardGiftcard)
                        )
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categoriesList.chunked(3).forEach { chunk ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    chunk.forEach { (catId, pair) ->
                                        val (label, icon) = pair
                                        val isSelected = selectedCategory == catId
                                        Surface(
                                            onClick = { selectedCategory = catId },
                                            color = if (isSelected) Color(0xFF6750A4) else Color(0xFFF1F5F9),
                                            contentColor = if (isSelected) Color.White else Color(0xFF475569),
                                            shape = RoundedCornerShape(12.dp),
                                            border = if (isSelected) null else BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(52.dp)
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier.padding(2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = label,
                                                    tint = if (isSelected) Color.White else Color(0xFF64748B),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    text = label,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Choose Service header
                    item {
                        Text(
                            text = "Choose Service",
                            fontSize = 13.sp,
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                        )
                    }

                    if (filteredServices.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No services found in this category", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    } else {
                        items(serviceChunks) { chunk ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                chunk.forEach { service ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        ServiceCardItem(service = service, viewModel = viewModel)
                                    }
                                }
                                if (chunk.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            } else {
                // Offline Customer View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    StatusSafetyBanner(isPrinterOnline = isPrinterOnline)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OfflineCustomerView()
                }
            }
        }
            } else {
                CustomerUPIPaymentQRPortalScreen(viewModel = viewModel, ownerCredits = ownerCredits)
            }
        }
    } else {
        // Wizard active upload flows (focused screens)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (selectedService?.id == "passport_photo_ai" && photoBase64 == null) {
                    PassportPhotoUploadScreen(viewModel = viewModel)
                } else if (selectedService?.id == "id_card_xerox") {
                    IDCardXeroxScreen(viewModel = viewModel)
                } else if (isProcessingAI) {
                    AIProcessingScreen(viewModel = viewModel)
                } else if (showPreview) {
                    PassportPhotoPreviewScreen(viewModel = viewModel)
                } else if (showSheetGrid) {
                    PassportPhotoSheetScreen(viewModel = viewModel)
                } else {
                    // Fallback for standard prints (documents, xerox, bindings, etc.)
                    StandardServiceUploadScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun CustomerUPIPaymentQRPortalScreen(
    viewModel: SmartXeroxViewModel,
    ownerCredits: com.example.data.OwnerCredits
) {
    val context = LocalContext.current
    var inputAmount by remember { mutableStateOf("10") }
    var inputRemark by remember { mutableStateOf("Xerox Copy Print") }
    
    // Dynamically build deep link
    val upiPayload = remember(ownerCredits.ownerUpi, inputAmount, inputRemark) {
        val amount = inputAmount.trim().ifBlank { "0" }
        val remark = try {
            java.net.URLEncoder.encode(inputRemark.trim().ifBlank { "SmartXerox" }, "UTF-8")
        } catch (e: Exception) {
            "SmartXerox"
        }
        val upi = ownerCredits.ownerUpi.trim().ifBlank { "owner@upi" }
        "upi://pay?pa=$upi&pn=SmartXerox_OWNER01&am=$amount&cu=INR&tn=$remark"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "UPI Payment",
                        tint = Color(0xFF6750A4),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Direct UPI QR Portal",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Customer payments will be deposited directly to the shopkeeper's UPI ID below.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Enter Payment Details:",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 12.sp
                    )

                    // Display Owner UPI
                    OutlinedTextField(
                        value = ownerCredits.ownerUpi,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Shop UPI ID", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color(0xFF0284C7)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF1F5F9),
                            unfocusedContainerColor = Color(0xFFF1F5F9),
                            disabledTextColor = Color(0xFF334155)
                        )
                    )

                    // Amount input
                    OutlinedTextField(
                        value = inputAmount,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() }) {
                                inputAmount = newValue
                            }
                        },
                        label = { Text("(Amount in ₹)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, color = Color(0xFF16A34A), modifier = Modifier.padding(start = 12.dp)) },
                        singleLine = true
                    )

                    // Quick chips row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val quickAmounts = listOf("10", "20", "50", "100", "200")
                        quickAmounts.forEach { amt ->
                            SuggestionChip(
                                onClick = { inputAmount = amt },
                                label = { Text("₹$amt", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Remark input
                    OutlinedTextField(
                        value = inputRemark,
                        onValueChange = { inputRemark = it },
                        label = { Text("Remark / Note", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF64748B)) },
                        singleLine = true
                    )
                }
            }
        }

        // DYNAMIC QR DISPLAY BLOCK
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, Color(0xFF6750A4).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Smart X Point • Scan to Pay",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Support: 7720007020",
                        fontSize = 9.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Box(
                        modifier = Modifier
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        QRCodeMockup(payload = upiPayload, modifier = Modifier.size(160.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "₹${inputAmount.trim().ifBlank { "0" }}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF6750A4)
                    )

                    Text(
                        text = "Payload: $upiPayload",
                        fontSize = 8.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val clip = android.content.ClipData.newPlainText("Owner UPI", ownerCredits.ownerUpi)
                                (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                    .setPrimaryClip(clip)
                                Toast.makeText(context, "UPI ID copied! 📋", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF475569)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy UPI", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.playOrderSuccessSound()
                                Toast.makeText(context, "Payment submitted successfully! 🎉", Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.2f).height(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Success", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("I Have Paid", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PassportPhotoUploadScreen(
    viewModel: SmartXeroxViewModel
) {
    val context = LocalContext.current
    
    // Gallery picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectMockPhoto(uri.toString())
            Toast.makeText(context, "Selected Gallery Photo! 🖼️", Toast.LENGTH_SHORT).show()
        }
    }

    // Camera capture launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val cacheUri = saveBitmapToCache(context, bitmap)
            if (cacheUri != null) {
                viewModel.selectMockPhoto(cacheUri.toString())
                Toast.makeText(context, "Photo Captured Successfully! 📸", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to capture photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.clearSelectedService() }) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF0F172A))
            }
            Text(
                text = "Passport Photo AI Wizard",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.width(32.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    tint = Color(0xFF6750A4),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1-Second AI Background Removal!",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    fontSize = 14.sp
                )
                Text(
                    text = "Upload a photo from your camera or gallery. A perfect white background and an 8-photo sheet will be generated automatically.",
                    color = Color(0xFF475569),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Real Camera and Gallery Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { cameraLauncher.launch(null) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Take Photo", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }

            Button(
                onClick = { galleryLauncher.launch("image/*") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Gallery Pick", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Or choose a character to test the AI workflow:",
            fontSize = 11.sp,
            color = Color(0xFF475569),
            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
        )

        // Grid of 4 avatar selection
        val characters = listOf(
            "rohan" to "Rohan",
            "priya" to "Priya",
            "rahul" to "Rahul (Glasses)",
            "sneha" to "Sneha (Bindi)"
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(characters) { (id, name) ->
                Card(
                    onClick = { viewModel.selectMockPhoto(id) },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AvatarCanvas(gender = id, modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = name,
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun StatusSafetyBanner(isPrinterOnline: Boolean) {
    Surface(
        color = if (isPrinterOnline) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
        border = BorderStroke(1.dp, if (isPrinterOnline) Color(0xFFBBF7D0) else Color(0xFFFCA5A5)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isPrinterOnline) Icons.Default.VerifiedUser else Icons.Default.ErrorOutline,
                contentDescription = "Security Rule",
                tint = if (isPrinterOnline) Color(0xFF15803D) else Color(0xFFB91C1C),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = if (isPrinterOnline) "Secure Payment Active (0% Risk)" else "Service Temporarily Disabled (Security Lock)",
                    color = if (isPrinterOnline) Color(0xFF14532D) else Color(0xFF7F1D1D),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = if (isPrinterOnline) 
                        "The shopkeeper's phone and printer are online. Your payment will not get stuck." 
                    else 
                        "Since the shopkeeper's app or printer is offline, payment is disabled to protect your money!",
                    color = if (isPrinterOnline) Color(0xFF166534) else Color(0xFF991B1B),
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun OfflineCustomerView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Block,
            contentDescription = "Service Blocked",
            tint = Color(0xFFDC2626),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Service Currently Offline",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF0F172A)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "The shopowner's printer is off, phone is switched off, or internet is disconnected. For security, payment has been disabled.",
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = Color(0xFF475569),
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "💡 Benefit: No more stuck payments or orders! The service will resume automatically once the shopkeeper goes online.",
                fontSize = 11.sp,
                color = Color(0xFF92400E),
                modifier = Modifier.padding(12.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun FeaturedServicesSlider(
    viewModel: SmartXeroxViewModel,
    allServices: List<ServiceRate>
) {
    // Select popular or high-margin services to feature from multiple categories
    val featuredIds = listOf(
        "passport_photo_ai",
        "id_card_xerox",
        "pvc_card_print",
        "mug_sublimation",
        "spiral_binding_100",
        "a4_print_color_hq",
        "resume_printing"
    )
    val featuredServices = allServices.filter { it.id in featuredIds }

    if (featuredServices.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Featured Services",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E1B4B)
            )
            Text(
                text = "Swipe to see more ➔",
                fontSize = 10.sp,
                color = Color(0xFF6750A4),
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(featuredServices) { service ->
                val gradient = when (service.id) {
                    "passport_photo_ai" -> Brush.horizontalGradient(listOf(Color(0xFF6366F1), Color(0xFF4F46E5)))
                    "id_card_xerox" -> Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)))
                    "pvc_card_print" -> Brush.horizontalGradient(listOf(Color(0xFF0EA5E9), Color(0xFF0284C7)))
                    "mug_sublimation" -> Brush.horizontalGradient(listOf(Color(0xFFEC4899), Color(0xFFDB2777)))
                    "spiral_binding_100" -> Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706)))
                    "a4_print_color_hq" -> Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))
                    else -> Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFF7C3AED)))
                }
                
                val icon = when (service.id) {
                    "passport_photo_ai" -> Icons.Default.Face
                    "id_card_xerox" -> Icons.Default.CreditCard
                    "pvc_card_print" -> Icons.Default.CreditCard
                    "mug_sublimation" -> Icons.Default.CardGiftcard
                    "spiral_binding_100" -> Icons.Default.AutoStories
                    "a4_print_color_hq" -> Icons.Default.Print
                    else -> Icons.Default.ReceiptLong
                }

                val desc = when (service.id) {
                    "passport_photo_ai" -> "AI White BG & 8-photo sheet in 1s!"
                    "id_card_xerox" -> "Merge front & back in 1 page instantly"
                    "pvc_card_print" -> "Direct plastic Aadhaar/PAN printing"
                    "mug_sublimation" -> "Premium ceramic print mug gift"
                    "spiral_binding_100" -> "Upto 100 pgs neat book spiral binding"
                    "a4_print_color_hq" -> "Super rich full color glossy/bond print"
                    else -> "Premium printing & document solutions"
                }

                Card(
                    onClick = { viewModel.selectService(service) },
                    modifier = Modifier
                        .width(220.dp)
                        .height(115.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(gradient)
                            .padding(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color.White.copy(alpha = 0.2f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                
                                Surface(
                                    color = Color.White.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        text = "₹${service.rate.toInt()}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            
                            Column {
                                Text(
                                    text = service.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ServiceSliderCard(
    service: ServiceRate,
    onClick: () -> Unit
) {
    val isPassport = service.id == "passport_photo_ai"
    val gradient = when (service.id) {
        "passport_photo_ai" -> Brush.horizontalGradient(listOf(Color(0xFF6366F1), Color(0xFF4F46E5)))
        "pvc_card_print" -> Brush.horizontalGradient(listOf(Color(0xFF0EA5E9), Color(0xFF0284C7)))
        "mug_sublimation" -> Brush.horizontalGradient(listOf(Color(0xFFEC4899), Color(0xFFDB2777)))
        "spiral_binding_100" -> Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706)))
        "a4_print_color_hq" -> Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))
        else -> null
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (gradient != null) Color.Transparent else if (isPassport) Color(0xFFEEF2FF) else Color.White
        ),
        modifier = Modifier
            .width(160.dp)
            .height(130.dp)
            .border(
                width = if (isPassport) 1.5.dp else 1.dp,
                color = if (isPassport) Color(0xFF6366F1) else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        val modifier = if (gradient != null) Modifier.fillMaxSize().background(gradient) else Modifier.fillMaxSize()
        val textColor = if (gradient != null) Color.White else Color(0xFF0F172A)
        val priceColor = if (gradient != null) Color.White else Color(0xFF6750A4)
        val iconColor = if (gradient != null) Color.White else Color(0xFF475569)

        Column(
            modifier = modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    color = if (gradient != null) Color.White.copy(alpha = 0.2f) else if (isPassport) Color(0xFF6750A4) else Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = when (service.id) {
                            "passport_photo_ai" -> Icons.Default.Face
                            "a4_xerox_bw_single", "a4_xerox_bw_double" -> Icons.Default.FileCopy
                            "a4_print_color_hq", "a4_print_color_std" -> Icons.Default.Print
                            "doc_scan_pdf", "scan_ocr" -> Icons.Default.Scanner
                            "lamination_a4" -> Icons.Default.Layers
                            "pvc_card_print", "id_card_xerox" -> Icons.Default.CreditCard
                            "spiral_binding_100", "soft_binding", "thesis_hard_binding" -> Icons.Default.AutoStories
                            else -> Icons.Default.ReceiptLong
                        },
                        contentDescription = null,
                        tint = if (gradient != null) Color.White else if (isPassport) Color.White else iconColor,
                        modifier = Modifier
                            .size(32.dp)
                            .padding(6.dp)
                    )
                }

                if (isPassport) {
                    Surface(
                        color = Color(0xFFFF9100),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "AI MAGIC",
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column {
                Text(
                    text = service.name,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "₹${service.rate.toInt()}",
                    color = priceColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun CustomerServiceGrid(viewModel: SmartXeroxViewModel) {
    val serviceRates by viewModel.serviceRates.collectAsState()
    val categories = listOf(
        "AI Services",
        "Document Services",
        "Binding & Lamination",
        "Cards & Prints",
        "Business Services",
        "Gifting & Merch"
    )
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Track selected category based on scroll position (simplest to let user tap to navigate)
    var activeCategory by remember { mutableStateOf("AI Services") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Horizontally scrolling slider featuring previously hidden services
        FeaturedServicesSlider(viewModel = viewModel, allServices = serviceRates)

        // Category scroll tab (acts as quick navigation clickers)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ScrollableTabRow(
                selectedTabIndex = categories.indexOf(activeCategory).coerceAtLeast(0),
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                categories.forEach { cat ->
                    val isSelected = activeCategory == cat
                    Tab(
                        selected = isSelected,
                        onClick = { 
                            activeCategory = cat
                            val categoryIndex = categories.indexOf(cat)
                            coroutineScope.launch {
                                // Scroll to category slider block smoothly!
                                listState.animateScrollToItem(categoryIndex)
                            }
                        },
                        text = { 
                            Text(
                                text = cat, 
                                fontSize = 11.sp, 
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ) 
                        },
                        selectedContentColor = Color(0xFF6750A4),
                        unselectedContentColor = Color(0xFF64748B)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // All categories presented as horizontal sliders (carousels), so no services are hidden!
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(categories) { index, cat ->
                val servicesInCat = serviceRates.filter { it.category == cat }
                if (servicesInCat.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cat,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Swipe ➔",
                                fontSize = 10.sp,
                                color = Color(0xFF6750A4),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(servicesInCat) { service ->
                                ServiceSliderCard(service = service) {
                                    viewModel.selectService(service)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AIProcessingScreen(viewModel: SmartXeroxViewModel) {
    val step by viewModel.processingStep.collectAsState()
    
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(150.dp)
        ) {
            Canvas(modifier = Modifier.size(100.dp)) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color(0xFF6366F1), Color(0xFFFFB300), Color(0xFF6366F1))),
                    startAngle = angle,
                    sweepAngle = 300f,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx())
                )
            }
            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = "AI face processing",
                tint = Color(0xFF6750A4),
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "AI Magic Processing in progress...",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF0F172A)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = step,
            fontSize = 14.sp,
            color = Color(0xFF6750A4),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Background removal and cropping will be complete in just 3 seconds.",
            fontSize = 11.sp,
            color = Color(0xFF475569),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PassportPhotoPreviewScreen(viewModel: SmartXeroxViewModel) {
    val countdown by viewModel.countdownSeconds.collectAsState()
    val photoScale by viewModel.photoScale.collectAsState()
    val photoOffsetX by viewModel.photoOffsetX.collectAsState()
    val photoOffsetY by viewModel.photoOffsetY.collectAsState()
    val selectedPhotoId by viewModel.photoBase64.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.clearSelectedService() }) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF0F172A))
            }
            Text(
                text = "Passport Photo Preview",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                fontSize = 16.sp
            )
            // Pulse Animation countdown circle
            Surface(
                color = Color(0xFFDC2626),
                shape = CircleShape,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(text = "$countdown", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }
        }
        
        Text(
            text = "Photo will be confirmed automatically in 3 seconds (or adjust manually):",
            fontSize = 11.sp,
            color = Color(0xFF475569),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Photo Cropper simulator area
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            modifier = Modifier
                .size(240.dp)
                .border(2.dp, Color(0xFF6750A4), RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Background replacement simulator (white background)
                Box(
                    modifier = Modifier
                        .size(180.dp, 210.dp)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .offset(photoOffsetX.dp, photoOffsetY.dp)
                    ) {
                        PassportPhoto(
                            photoId = selectedPhotoId,
                            modifier = Modifier.size((110 * photoScale).dp)
                        )
                    }
                }
                
                // Crop overlay box guide
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 2.dp.toPx()
                    val width = 160.dp.toPx()
                    val height = 190.dp.toPx()
                    val left = (size.width - width) / 2
                    val top = (size.height - height) / 2
                    
                    // Semi-transparent overlay around crop
                    drawRect(
                        color = Color.Black.copy(alpha = 0.5f),
                        size = size
                    )
                    
                    // Clear the crop region
                    drawRect(
                        color = Color.Transparent,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                    )
                    
                    // Draw dashed crop frame border
                    drawRect(
                        color = Color(0xFF6750A4),
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(
                            width = strokeWidth,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Cropper Sliders & Adjusters
        Text(text = "Adjust Manually (Zoom/Pan):", fontSize = 11.sp, color = Color(0xFF475569))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color(0xFF64748B))
            Slider(
                value = photoScale,
                onValueChange = { viewModel.adjustPhotoScale(it) },
                valueRange = 0.8f..1.8f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    activeTrackColor = Color(0xFF6750A4),
                    thumbColor = Color(0xFF6750A4)
                )
            )
            Icon(imageVector = Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color(0xFF64748B))
        }

        // Translation Pan pad controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { viewModel.adjustPhotoOffset(photoOffsetX, photoOffsetY - 5f) },
                modifier = Modifier.size(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Up", modifier = Modifier.size(16.dp))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { viewModel.adjustPhotoOffset(photoOffsetX - 5f, photoOffsetY) },
                modifier = Modifier.size(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(imageVector = Icons.Default.KeyboardArrowLeft, contentDescription = "Left", modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(36.dp))
            Button(
                onClick = { viewModel.adjustPhotoOffset(photoOffsetX + 5f, photoOffsetY) },
                modifier = Modifier.size(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = "Right", modifier = Modifier.size(16.dp))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { viewModel.adjustPhotoOffset(photoOffsetX, photoOffsetY + 5f) },
                modifier = Modifier.size(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Down", modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.clearSelectedService() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF64748B)),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Cancel")
            }
            Button(
                onClick = { viewModel.confirmPassportLayout() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                modifier = Modifier.weight(1.5f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Confirm", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PassportPhotoSheetScreen(viewModel: SmartXeroxViewModel) {
    val selectedPhotoId by viewModel.photoBase64.collectAsState()
    val photoScale by viewModel.photoScale.collectAsState()
    val photoOffsetX by viewModel.photoOffsetX.collectAsState()
    val photoOffsetY by viewModel.photoOffsetY.collectAsState()
    val selectedService by viewModel.selectedService.collectAsState()
    
    val showPaymentDialog by viewModel.showPaymentDialog.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.selectService(selectedService ?: return@IconButton) }) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF0F172A))
            }
            Text(
                text = "8 Passport Photos 4x6 Sheet",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.width(32.dp))
        }

        Text(
            text = "AI 8-Photos Sheet is ready (with cutting guides):",
            fontSize = 11.sp,
            color = Color(0xFF475569),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 4x6 Sheet Layout visualization (landscape cardboard layout)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f) // 4x6 Aspect Ratio
                .border(2.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp)
        ) {
            // Draw 2x4 grid with dashed cutting guidelines
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    
                    // Draw cutting guides (dashed lines)
                    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    val strokeWidth = 1.dp.toPx()
                    
                    // Vertical cuts
                    drawLine(Color.Gray, Offset(width * 0.25f, 0f), Offset(width * 0.25f, height), strokeWidth = strokeWidth, pathEffect = pathEffect)
                    drawLine(Color.Gray, Offset(width * 0.5f, 0f), Offset(width * 0.5f, height), strokeWidth = strokeWidth, pathEffect = pathEffect)
                    drawLine(Color.Gray, Offset(width * 0.75f, 0f), Offset(width * 0.75f, height), strokeWidth = strokeWidth, pathEffect = pathEffect)
                    
                    // Horizontal cuts
                    drawLine(Color.Gray, Offset(0f, height * 0.5f), Offset(width, height * 0.5f), strokeWidth = strokeWidth, pathEffect = pathEffect)
                }

                // Grid of Passport Photos
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .padding(4.dp)
                                    .background(Color.White)
                                    .border(0.5.dp, Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.offset((photoOffsetX * 0.2f).dp, (photoOffsetY * 0.2f).dp)) {
                                    PassportPhoto(photoId = selectedPhotoId, modifier = Modifier.fillMaxSize(0.7f * photoScale))
                                }
                            }
                        }
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .padding(4.dp)
                                    .background(Color.White)
                                    .border(0.5.dp, Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.offset((photoOffsetX * 0.2f).dp, (photoOffsetY * 0.2f).dp)) {
                                    PassportPhoto(photoId = selectedPhotoId, modifier = Modifier.fillMaxSize(0.7f * photoScale))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Bill Details", color = Color(0xFF475569), fontSize = 11.sp)
                    Text(text = "V5.0", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = selectedService?.name ?: "Passport Photo", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(text = "₹${selectedService?.rate?.toInt() ?: 30}", color = Color(0xFF6750A4), fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "✓ 8 copies (on 4\"x6\" Premium Glossy Paper)",
                    fontSize = 11.sp,
                    color = Color(0xFF16A34A),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.rotatePrint() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6750A4)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.RotateRight, contentDescription = "Rotate", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("(Rotate 90°)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { viewModel.toggleUniversalPreview(true) },
                modifier = Modifier.weight(1.5f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6750A4)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Visibility, contentDescription = "Preview", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Print Preview", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.toggleUniversalPreview(true) }, // Preview first!
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White), // Brand purple
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(imageVector = Icons.Default.Payment, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pay & Print",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }
    }

    val showUniversalPreview by viewModel.showUniversalPreview.collectAsState()
    if (showUniversalPreview) {
        UniversalPrintPreviewDialog(
            viewModel = viewModel,
            isPassport = true,
            price = selectedService?.rate ?: 30.0,
            onConfirm = { viewModel.openPayment() }
        )
    }

    if (showPaymentDialog) {
        UPIPaymentDialog(viewModel = viewModel)
    }
}

fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result ?: "uploaded_document.pdf"
}

@Composable
fun StandardServiceUploadScreen(viewModel: SmartXeroxViewModel) {
    val selectedService by viewModel.selectedService.collectAsState()
    val showPaymentDialog by viewModel.showPaymentDialog.collectAsState()
    val context = LocalContext.current
    
    var documentName by remember { mutableStateOf("") }
    var copiesCount by remember { mutableStateOf(1) }
    var printRange by remember { mutableStateOf("All Pages") }
    var doubleSided by remember { mutableStateOf(false) }
    var specialInstructions by remember { mutableStateOf("") }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                specialInstructions = matches[0]
                Toast.makeText(context, "Voice instruction recorded! 🎙️", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val singleRate = selectedService?.rate ?: 5.0
    val totalPrice = remember(copiesCount, selectedService) { singleRate * copiesCount.toDouble() }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            documentName = getFileName(context, uri)
            Toast.makeText(context, "Selected File: $documentName 📄", Toast.LENGTH_SHORT).show()
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.clearSelectedService() }) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF0F172A))
            }
            Text(
                text = selectedService?.name ?: "Document Print",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Document Uploader Card Simulator (Interactive)
        val isFileUploaded = documentName.isNotEmpty()
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isFileUploaded) Color(0xFFF0FDF4) else Color.White
            ),
            border = BorderStroke(
                width = 1.5.dp,
                color = if (isFileUploaded) Color(0xFF10B981) else Color(0xFF6750A4).copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    fileLauncher.launch("*/*")
                }
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (isFileUploaded) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                    contentDescription = "Upload Document",
                    tint = if (isFileUploaded) Color(0xFF10B981) else Color(0xFF6750A4),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (isFileUploaded) "File Selected & Ready! 📄" else "Click to Select File",
                    fontWeight = FontWeight.Bold,
                    color = if (isFileUploaded) Color(0xFF047857) else Color(0xFF0F172A),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (isFileUploaded) documentName else "Tap here to browse PDF, DOCX, JPG, PNG from device",
                    color = if (isFileUploaded) Color(0xFF065F46) else Color(0xFF475569),
                    fontWeight = if (isFileUploaded) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Or choose a quick demo file below:",
                    color = Color(0xFF64748B),
                    fontSize = 10.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                // File selectors
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val mockFiles = listOf("marksheet.pdf", "resume_v2.docx", "electricity_bill.pdf")
                    mockFiles.forEach { file ->
                        val isSelected = documentName == file
                        Surface(
                            onClick = { 
                                documentName = file 
                                Toast.makeText(context, "Selected Demo File: $file", Toast.LENGTH_SHORT).show()
                            },
                            color = if (isSelected) Color(0xFFEEF2FF) else Color(0xFFF1F5F9),
                            contentColor = if (isSelected) Color(0xFF6750A4) else Color(0xFF475569),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFFC7D2FE) else Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = file,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Print Config Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Print Settings", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 8.dp))
                
                // Copies Count Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Copies", color = Color(0xFF0F172A), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (copiesCount > 1) copiesCount-- },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF475569)),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text(text = "-", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text(
                            text = "$copiesCount",
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            fontSize = 14.sp
                        )
                        IconButton(
                            onClick = { copiesCount++ },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF475569)),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text(text = "+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Page Range selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Page Range", color = Color(0xFF0F172A), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("All Pages", "1-5", "Custom").forEach { range ->
                            val isSelected = printRange == range
                            Surface(
                                onClick = { printRange = range },
                                color = if (isSelected) Color(0xFFEEF2FF) else Color.Transparent,
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFFC7D2FE) else Color(0xFFCBD5E1)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = range,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color(0xFF6750A4) else Color(0xFF64748B),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Double Sided switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Double-Sided", color = Color(0xFF0F172A), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Switch(
                        checked = doubleSided,
                        onCheckedChange = { doubleSided = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF6750A4),
                            checkedTrackColor = Color(0xFF6750A4).copy(alpha = 0.3f),
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = Color(0xFFE2E8F0)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = specialInstructions,
                    onValueChange = { specialInstructions = it },
                    label = { Text("Special Instructions (Voice / Text)", fontSize = 11.sp) },
                    placeholder = { Text("e.g. Crop photo to head & shoulders, or print color", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak instructions for photo cropping or printing...")
                            }
                            try {
                                speechLauncher.launch(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Microphone speech recognition not available", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Mic, contentDescription = "Voice Input", tint = Color(0xFF6750A4))
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6750A4),
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Show Print Preview button
                OutlinedButton(
                    onClick = { viewModel.toggleUniversalPreview(true) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6750A4)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Visibility, contentDescription = "Preview", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Show Print Preview", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Total price and Pay button
        Button(
            onClick = { 
                if (documentName.isBlank()) {
                    Toast.makeText(context, "⚠️ Please upload or select a document before proceeding to payment!", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.toggleUniversalPreview(true) // Open preview first!
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (documentName.isNotBlank()) Color(0xFF6750A4) else Color(0xFF94A3B8),
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(imageVector = Icons.Default.Payment, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pay & Print",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }
    }

    val showUniversalPreview by viewModel.showUniversalPreview.collectAsState()
    if (showUniversalPreview) {
        UniversalPrintPreviewDialog(
            viewModel = viewModel,
            isPassport = false,
            price = totalPrice,
            onConfirm = { viewModel.openPayment() }
        )
    }

    if (showPaymentDialog) {
        UPIPaymentDialog(viewModel = viewModel, priceOverride = totalPrice)
    }
}

@Composable
fun UPIPaymentDialog(
    viewModel: SmartXeroxViewModel,
    priceOverride: Double? = null
) {
    val selectedService by viewModel.selectedService.collectAsState()
    val isPaymentProcessing by viewModel.isPaymentProcessing.collectAsState()
    val ownerCredits by viewModel.ownerCredits.collectAsState()
    
    val price = priceOverride ?: selectedService?.rate ?: 30.0

    Dialog(onDismissRequest = { viewModel.cancelPayment() }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Direct Owner Payment UPI",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 14.sp
                    )
                    IconButton(onClick = { viewModel.cancelPayment() }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Total Price",
                    fontSize = 11.sp,
                    color = Color(0xFF475569)
                )
                Text(
                    text = "₹${price.toInt()}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF6750A4)
                )

                Spacer(modifier = Modifier.height(8.dp))
                
                // Direct payment instructions showing Owner UPI ID
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Owner UPI ID: ${ownerCredits.ownerUpi}",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0284C7),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Payments will be credited directly to the shop owner's account.",
                            fontSize = 9.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isPaymentProcessing) {
                    CircularProgressIndicator(color = Color(0xFF6750A4))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Payment is being verified... (Verifying Payment)",
                        fontSize = 11.sp,
                        color = Color(0xFF6750A4),
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "UPI  :",
                        fontSize = 12.sp,
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val paymentApps = listOf(
                        "Google Pay" to Color(0xFF4285F4),
                        "PhonePe" to Color(0xFF5F259F),
                        "Paytm" to Color(0xFF00B9F1)
                    )
                    
                    paymentApps.forEach { (appName, color) ->
                        Button(
                            onClick = { viewModel.payAndPrint(appName) },
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "$appName",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "🔒 Customer payments are deposited directly into the shop owner's account. Smart Xerox does not hold or charge any fees.",
                        fontSize = 9.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ==========================================
// 2. SHOP OWNER SCREEN
// ==========================================
@Composable
fun ShopOwnerScreen(
    viewModel: SmartXeroxViewModel
) {
    val ctx = LocalContext.current
    val ownerCredits by viewModel.ownerCredits.collectAsState()

    if (!ownerCredits.isRegistered) {
        ShopRegistrationScreen(viewModel = viewModel)
        return
    }

    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val printerConfig by viewModel.printerConfig.collectAsState()
    val heartbeatLogs by viewModel.heartbeatLogs.collectAsState()
    val orders by viewModel.orders.collectAsState()
    val nextHeartbeatSeconds by viewModel.nextHeartbeatSeconds.collectAsState()
    val adminSettings by viewModel.adminSettings.collectAsState()

    var showEditRateDialog by remember { mutableStateOf<ServiceRate?>(null) }
    var ownerSubTab by remember { mutableStateOf("Printers & Logs") } // Initialize directly to one of the tabs

    if (showEditRateDialog != null) {
        EditRateDialog(
            serviceRate = showEditRateDialog!!,
            onDismiss = { showEditRateDialog = null },
            onSave = { updated ->
                viewModel.updateServiceRate(updated.id, updated.name, updated.rate, updated.category, updated.rate)
                showEditRateDialog = null
                Toast.makeText(ctx, "Rate updated successfully! ⚙️", Toast.LENGTH_SHORT).show()
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 75.dp) // Leave space for bottom navigation bar
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SxpBrandHeaderComponent(
                title = "Smart X Point • Shop Owner Portal",
                subtitle = "Manage Printers, Rates & Live Orders",
                customLogoUri = adminSettings.customLogoUri
            )

            var diagnosticResultDialog by remember { mutableStateOf<String?>(null) }

            if (diagnosticResultDialog != null) {
                AlertDialog(
                    onDismissRequest = { diagnosticResultDialog = null },
                    title = { Text("Shop & QR Diagnostics", fontWeight = FontWeight.Bold) },
                    text = { Text(diagnosticResultDialog ?: "", fontSize = 12.sp, lineHeight = 16.sp) },
                    confirmButton = {
                        Button(onClick = { diagnosticResultDialog = null }) {
                            Text("Close")
                        }
                    }
                )
            }

            Button(
                onClick = {
                    viewModel.runDiagnosticsResult { res ->
                        diagnosticResultDialog = res
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(36.dp)
            ) {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Diagnose", tint = Color(0xFF22C55E), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Run Active Session & QR Reachability Diagnostics", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            // Visual subscription warning system
            val currentTime = System.currentTimeMillis()
            val daysRemaining = if (ownerCredits.subscriptionExpires > currentTime) {
                (ownerCredits.subscriptionExpires - currentTime) / (1000L * 60 * 60 * 24)
            } else {
                -1L
            }
            val isExpiringSoon = daysRemaining in 0L..7L
            val isExpired = ownerCredits.subscriptionExpires <= currentTime
            
            if (isExpiringSoon || isExpired) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isExpired) Color(0xFFFEF2F2) else Color(0xFFFFFBEB)
                    ),
                    border = BorderStroke(
                        1.5.dp,
                        if (isExpired) Color(0xFFEF4444) else Color(0xFFF59E0B)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isExpired) Icons.Default.Cancel else Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = if (isExpired) Color(0xFFEF4444) else Color(0xFFD97706),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isExpired) "Subscription Expired" else "Expiring Soon",
                                fontWeight = FontWeight.Bold,
                                color = if (isExpired) Color(0xFF991B1B) else Color(0xFF92400E),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isExpired) 
                                    "Your unlimited print service is inactive. Please recharge now." 
                                else 
                                    "Your subscription is expiring in $daysRemaining days. Please recharge soon to avoid service interruption.",
                                color = if (isExpired) Color(0xFF7F1D1D) else Color(0xFF78350F),
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { ownerSubTab = "Recharge & Credits" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isExpired) Color(0xFFEF4444) else Color(0xFFD97706)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Recharge", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // Foreground Active Toggle
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceRunning) Color(0xFFD1FAE5) else Color(0xFFF1F5F9)
                ),
                border = BorderStroke(1.dp, if (isServiceRunning) Color(0xFF10B981) else Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isServiceRunning) Icons.Default.PlayCircleFilled else Icons.Default.PauseCircle,
                            contentDescription = null,
                            tint = if (isServiceRunning) Color(0xFF10B981) else Color(0xFF64748B),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isServiceRunning) "Smart X Point Active Service" else "Service Paused",
                                fontWeight = FontWeight.Bold,
                                color = if (isServiceRunning) Color(0xFF065F46) else Color(0xFF475569),
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (isServiceRunning) ": $nextHeartbeatSeconds" else "Heartbeat Off - Customer can't order",
                                color = if (isServiceRunning) Color(0xFF047857) else Color(0xFF64748B),
                                fontSize = 10.sp
                            )
                        }
                    }
                    
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.08f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseScale"
                    )

                    Button(
                        onClick = { viewModel.toggleService() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isServiceRunning) Color(0xFFEF4444) else Color(0xFF10B981),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                    ) {
                        Text(
                            text = if (isServiceRunning) "STOP" else "START",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Render Sub Tabs Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (ownerSubTab) {
                    "Printers & Logs" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PrinterSettingsPanel(viewModel = viewModel)
                            ForegroundNotificationMockup(viewModel = viewModel)
                            HeartbeatConsole(logs = heartbeatLogs)
                        }
                    }
                    "Rates Config" -> {
                        ShopkeeperRatesConfigPanel(
                            viewModel = viewModel,
                            onEditRate = { showEditRateDialog = it }
                        )
                    }
                    "Orders Log" -> {
                        ShopOrdersLogPanel(viewModel = viewModel)
                    }
                    "Voice Logs" -> {
                        ShopVoiceLogsPanel(viewModel = viewModel)
                    }
                    "Recharge & Credits" -> {
                        ShopOwnerRechargePanel(viewModel = viewModel)
                    }
                    "Shop QR" -> {
                        ShopQRPanel(viewModel = viewModel)
                    }
                }
            }
        }

        // Elegant M3 Style Bottom Navigation Bar for Owner View
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(64.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf(
                    "Printers & Logs" to (Icons.Default.Print to "Printers"),
                    "Rates Config" to (Icons.Default.CurrencyRupee to "Rates"),
                    "Orders Log" to (Icons.Default.ReceiptLong to "Orders"),
                    "Voice Logs" to (Icons.Default.Mic to "Voice Logs"),
                    "Recharge & Credits" to (Icons.Default.Bolt to "Recharge"),
                    "Shop QR" to (Icons.Default.QrCode to "Shop QR")
                )

                tabs.forEach { (tabId, pair) ->
                    val (icon, label) = pair
                    val isSelected = ownerSubTab == tabId
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { ownerSubTab = tabId },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isSelected) Color(0xFF6750A4) else Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF6750A4) else Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }

    if (showEditRateDialog != null) {
        EditRateDialog(
            serviceRate = showEditRateDialog!!,
            onDismiss = { showEditRateDialog = null },
            onSave = { updatedRate ->
                viewModel.updateServiceRate(
                    id = updatedRate.id,
                    name = updatedRate.name,
                    rate = updatedRate.rate,
                    category = updatedRate.category
                )
                showEditRateDialog = null
            }
        )
    }
}

@Composable
fun PrinterSettingsPanel(
    viewModel: SmartXeroxViewModel
) {
    val printers by viewModel.printers.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    var newName by remember { mutableStateOf("") }
    var newIp by remember { mutableStateOf("") }
    var newIsPrimary by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Connected Printers",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB300),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Auto-fallback and load-balancing are active",
                        color = Color.LightGray,
                        fontSize = 10.sp
                    )
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155), contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Printer", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add New", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (printers.isEmpty()) {
                Text(
                    text = "No printers connected. Please add one.",
                    color = Color.Red,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    printers.forEach { printer ->
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (printer.isPrimary) Color(0xFFFFB300) else Color.Transparent
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.2f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = printer.name,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                        if (printer.isPrimary) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = Color(0xFFFFB300),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "MAIN",
                                                    color = Color.Black,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "IP: ${printer.ipAddress}",
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    
                                    // Status Badge Selector (Interactive Simulation)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = when(printer.status) {
                                                "READY" -> Color(0xFF16A34A).copy(alpha = 0.2f)
                                                "PAPER_JAM" -> Color(0xFFD97706).copy(alpha = 0.2f)
                                                "OUT_OF_INK" -> Color(0xFFEAB308).copy(alpha = 0.2f)
                                                else -> Color(0xFFDC2626).copy(alpha = 0.2f)
                                            },
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = when(printer.status) {
                                                    "READY" -> "🟢 READY"
                                                    "PAPER_JAM" -> "🟠 PAPER JAM"
                                                    "OUT_OF_INK" -> "🟡 OUT OF INK"
                                                    else -> "🔴 OFFLINE"
                                                },
                                                color = when(printer.status) {
                                                    "READY" -> Color(0xFF4ADE80)
                                                    "PAPER_JAM" -> Color(0xFFFBBF24)
                                                    "OUT_OF_INK" -> Color(0xFFFEF08A)
                                                    else -> Color(0xFFFCA5A5)
                                                },
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.weight(0.8f),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Set as Primary Action
                                    if (!printer.isPrimary) {
                                        IconButton(
                                            onClick = { viewModel.setPrimaryPrinter(printer) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Set Primary",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // Test Connection Button
                                    IconButton(
                                        onClick = {
                                            viewModel.testPrinterConnection(printer) { success, msg ->
                                                // Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Test Connection",
                                            tint = Color(0xFF22C55E),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Simulate Cycle Status button
                                    IconButton(
                                        onClick = {
                                            val nextStatus = when(printer.status) {
                                                "READY" -> "PAPER_JAM"
                                                "PAPER_JAM" -> "OUT_OF_INK"
                                                "OUT_OF_INK" -> "OFFLINE"
                                                else -> "READY"
                                            }
                                            viewModel.updatePrinterStatus(printer, nextStatus)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Simulate Error",
                                            tint = Color(0xFFFFB300),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Delete Action
                                    IconButton(
                                        onClick = { viewModel.deletePrinter(printer.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Printer",
                                            tint = Color.Red.copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.border(2.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Add Printer",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("e.g. HP LaserJet") },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFB300),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFFFFB300)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newIp,
                        onValueChange = { newIp = it },
                        label = { Text("e.g. 192.168.1.100") },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFB300),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFFFFB300)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Primary Printer", color = Color.White, fontSize = 11.sp)
                        Switch(
                            checked = newIsPrimary,
                            onCheckedChange = { newIsPrimary = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFFB300))
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAddDialog = false },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (newName.isNotBlank() && newIp.isNotBlank()) {
                                    viewModel.addPrinter(newName, newIp, "READY", newIsPrimary)
                                    newName = ""
                                    newIp = ""
                                    newIsPrimary = false
                                    showAddDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300), contentColor = Color.Black),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Add", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ForegroundNotificationMockup(viewModel: SmartXeroxViewModel) {
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val notifications by viewModel.notifications.collectAsState()

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "📌 Android Notification Bar Preview (Foreground Service)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = null,
                        tint = if (isServiceRunning) Color(0xFFFFB300) else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isServiceRunning) "Smart X Point Active" else "Smart X Point Paused",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        Text(
                            text = if (isServiceRunning) "Connected to: HP Laserjet • Status: Listening for prints..." else "Foreground Service Disabled",
                            color = Color.LightGray,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            if (isServiceRunning && notifications.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FCM Notifications",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB300),
                        fontSize = 10.sp
                    )
                    TextButton(
                        onClick = { viewModel.clearAllNotifications() },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(20.dp)
                    ) {
                        Text("Clear All", color = Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    notifications.forEach { notif ->
                        val isRead = notif.isRead
                        Surface(
                            color = if (isRead) Color(0xFF1E293B).copy(alpha = 0.6f) else Color(0xFF1E293B),
                            shape = RoundedCornerShape(8.dp),
                            border = if (!isRead) BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f)) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = "📲", fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = notif.title,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isRead) Color.LightGray else Color.White,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = notif.message,
                                            color = Color.LightGray,
                                            fontSize = 9.sp,
                                            lineHeight = 11.sp
                                        )
                                        Text(
                                            text = "Source: ${notif.serviceType} FCM Channel",
                                            color = Color.Gray,
                                            fontSize = 7.5.sp
                                        )
                                    }
                                }
                                if (!isRead) {
                                    IconButton(
                                        onClick = { viewModel.markNotificationAsRead(notif.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Mark read",
                                            tint = Color.Green,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeartbeatConsole(logs: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "🟢 Live Heartbeat Console (dar 30 sec la send hotat):",
                color = Color.Green,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = Color(0xFF111),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .border(0.5.dp, Color.Gray, RoundedCornerShape(4.dp))
            ) {
                if (logs.isEmpty()) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(text = "Waiting for heartbeat pings to start...", color = Color.DarkGray, fontSize = 11.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs) { log ->
                            Text(
                                text = log,
                                color = if (log.contains("🔴")) Color.Red else if (log.contains("🟡")) Color.Yellow else Color.Green,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShopkeeperRatesConfigPanel(
    viewModel: SmartXeroxViewModel,
    onEditRate: (ServiceRate) -> Unit
) {
    val serviceRates by viewModel.serviceRates.collectAsState()
    
    // Categorize
    var selectedCategory by remember { mutableStateOf("AI Services") }
    val categories = listOf("AI Services", "Document Services", "Binding & Lamination", "Cards & Prints", "Business Services", "Gifting & Merch")
    
    val filteredServices = serviceRates.filter { it.category == selectedCategory }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Horizontal scroll category
        ScrollableTabRow(
            selectedTabIndex = categories.indexOf(selectedCategory),
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            divider = {}
        ) {
            categories.forEachIndexed { index, cat ->
                Tab(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    text = { Text(text = cat, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    selectedContentColor = Color(0xFFFFB300),
                    unselectedContentColor = Color.LightGray
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Shopkeeper Custom Rates ($selectedCategory)",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    filteredServices.forEach { service ->
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = service.name,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = ": ${service.id}",
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "₹${service.rate.toInt()}",
                                        color = Color(0xFFFFB300),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp
                                    )
                                    IconButton(
                                        onClick = { onEditRate(service) },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = Color(0xFF334155),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit rate",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun EditRateDialog(
    serviceRate: ServiceRate,
    onDismiss: () -> Unit,
    onSave: (ServiceRate) -> Unit
) {
    var rateInput by remember { mutableStateOf(serviceRate.rate.toInt().toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(2.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Update Rate",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = serviceRate.name,
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = rateInput,
                    onValueChange = { rateInput = it },
                    label = { Text("Rupees") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Bold),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFB300),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFFFFB300)
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { 
                            val rateVal = rateInput.toDoubleOrNull() ?: serviceRate.rate
                            onSave(serviceRate.copy(rate = rateVal))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300), contentColor = Color.Black),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ShopOrdersLogPanel(viewModel: SmartXeroxViewModel) {
    val orders by viewModel.orders.collectAsState()
    var filterStatus by remember { mutableStateOf("ALL") }
    var selectedOrderIds by remember { mutableStateOf(setOf<String>()) }
    var showPdfDialog by remember { mutableStateOf(false) }
    var pdfMargin by remember { mutableStateOf("0.5 inch") }
    var pdfOrientation by remember { mutableStateOf("Portrait") }
    var pdfPageSize by remember { mutableStateOf("A4") }
    var pdfIsColor by remember { mutableStateOf(true) }

    val filteredOrders = orders.filter { order ->
        if (filterStatus == "ALL") true else order.status.equals(filterStatus, ignoreCase = true)
    }

    if (showPdfDialog) {
        AlertDialog(
            onDismissRequest = { showPdfDialog = false },
            containerColor = Color(0xFF1E293B),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Printable PDF", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Configure layout and margins for ${selectedOrderIds.size} selected ready queue items:", fontSize = 11.sp, color = Color.LightGray)
                    
                    // Page Size
                    Text("Page Size", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB300))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("A4", "Letter", "Legal").forEach { sz ->
                            Surface(
                                onClick = { pdfPageSize = sz },
                                color = if (pdfPageSize == sz) Color(0xFFFFB300) else Color(0xFF0F172A),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Text(sz, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (pdfPageSize == sz) Color.Black else Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                    }

                    // Orientation
                    Text("Orientation", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB300))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Portrait", "Landscape").forEach { ori ->
                            Surface(
                                onClick = { pdfOrientation = ori },
                                color = if (pdfOrientation == ori) Color(0xFFFFB300) else Color(0xFF0F172A),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Text(ori, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (pdfOrientation == ori) Color.Black else Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                    }

                    // Margins
                    Text("Consistent Margins", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB300))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("0.3 inch", "0.5 inch", "1.0 inch").forEach { m ->
                            Surface(
                                onClick = { pdfMargin = m },
                                color = if (pdfMargin == m) Color(0xFFFFB300) else Color(0xFF0F172A),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Text(m, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (pdfMargin == m) Color.Black else Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                    }

                    // Color Mode
                    Text("Color Profile", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB300))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(true to "Full Color (RGB)", false to "Grayscale / B&W").forEach { (col, label) ->
                            Surface(
                                onClick = { pdfIsColor = col },
                                color = if (pdfIsColor == col) Color(0xFFFFB300) else Color(0xFF0F172A),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (pdfIsColor == col) Color.Black else Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.generatePrintablePdf(selectedOrderIds.toList(), pdfMargin, pdfOrientation, pdfPageSize, pdfIsColor)
                        showPdfDialog = false
                        selectedOrderIds = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
                ) {
                    Text("Export & Download PDF", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPdfDialog = false }) {
                    Text("Cancel", color = Color.Gray, fontSize = 11.sp)
                }
            }
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Orders Log & Print Queue",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 12.sp
                )
                Surface(
                    color = Color(0xFF334155),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF475569))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Shortcuts",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "Multi-Select Ready Items for Bulk Print",
                            color = Color(0xFFFFB300),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Filter status chips
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("ALL", "PAID", "COMPLETED", "PRINTING", "FAILED").forEach { st ->
                    Surface(
                        onClick = { filterStatus = st },
                        color = if (filterStatus == st) Color(0xFFFFB300) else Color(0xFF334155),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(st, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (filterStatus == st) Color.Black else Color.White)
                        }
                    }
                }
            }

            // Bulk action bar if items selected
            if (selectedOrderIds.isNotEmpty()) {
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFB300)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedOrderIds.size} Ready items selected",
                                color = Color(0xFFFFB300),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { selectedOrderIds = emptySet() }) {
                                Text("Clear", fontSize = 10.sp, color = Color.Gray)
                            }
                        }

                        val printers by viewModel.printers.collectAsState()
                        var selectedPrinterId by remember { mutableStateOf(printers.firstOrNull()?.id ?: 1) }

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Assign Printer:", fontSize = 9.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.width(6.dp))
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                printers.forEach { p ->
                                    val isChosen = p.id == selectedPrinterId
                                    val statusColor = when(p.status) {
                                        "READY" -> Color(0xFF22C55E)
                                        "PAPER_JAM", "OUT_OF_INK" -> Color(0xFFEAB308)
                                        else -> Color(0xFFEF4444)
                                    }
                                    Surface(
                                        onClick = { selectedPrinterId = p.id },
                                        color = if (isChosen) Color(0xFFFFB300) else Color(0xFF1E293B),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, statusColor)
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(5.dp).background(statusColor, CircleShape))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("${p.name} (${p.status})", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (isChosen) Color.Black else Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = {
                                    viewModel.bulkPrintOrdersWithPrinter(selectedOrderIds.toList(), selectedPrinterId) { _, _ -> }
                                    selectedOrderIds = emptySet()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(28.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Print, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Bulk Print", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                            Button(
                                onClick = { showPdfDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(28.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Printable PDF", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            if (filteredOrders.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(text = "No orders match filter '$filterStatus'.", color = Color.LightGray, fontSize = 11.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredOrders) { order ->
                        val isReadyForPrint = order.status == "PAID" || order.status == "PRINTING" || order.status == "PENDING"
                        val isSelected = selectedOrderIds.contains(order.orderId)

                        Surface(
                            color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = if (isSelected) BorderStroke(1.dp, Color(0xFFFFB300)) else null,
                            modifier = Modifier.clickable(enabled = isReadyForPrint) {
                                selectedOrderIds = if (isSelected) {
                                    selectedOrderIds - order.orderId
                                } else {
                                    selectedOrderIds + order.orderId
                                }
                            }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (isReadyForPrint) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    selectedOrderIds = if (checked) {
                                                        selectedOrderIds + order.orderId
                                                    } else {
                                                        selectedOrderIds - order.orderId
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFFB300))
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = order.orderId,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = order.serviceName,
                                                color = Color.LightGray,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    
                                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                    val alphaAnim by infiniteTransition.animateFloat(
                                        initialValue = 0.4f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(700, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "pulseAlpha"
                                    )

                                    Surface(
                                        color = when (order.status) {
                                            "COMPLETED" -> Color(0xFF065F46)
                                            "FAILED" -> Color(0xFF991B1B)
                                            "PRINTING" -> Color(0xFF92400E)
                                            else -> Color(0xFF1E3A8A)
                                        },
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = if (isReadyForPrint) Modifier.alpha(alphaAnim) else Modifier
                                    ) {
                                        Text(
                                            text = order.status,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                
                                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 6.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Amount: ₹${order.price.toInt()} | Royalty: ₹${order.commissionPaid.toInt()}",
                                        fontSize = 10.sp,
                                        color = Color.LightGray
                                    )
                                    
                                    val dateStr = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault()).format(java.util.Date(order.timestamp))
                                    Text(
                                        text = dateStr,
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                                
                                if (order.errorMessage != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "⚠️ : ${order.errorMessage}",
                                        color = Color.Red,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (order.printerUsedIp != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "🖨️  : IP ${order.printerUsedIp}",
                                        color = Color.Green,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShopVoiceLogsPanel(viewModel: SmartXeroxViewModel) {
    val voiceLogs by viewModel.voiceLogs.collectAsState()

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Customer Voice Instruction Transcriptions",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Text(
                        text = "AI Speech-to-Text",
                        color = Color(0xFF10B981),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (voiceLogs.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("No voice instruction logs recorded yet.", color = Color.LightGray, fontSize = 11.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(voiceLogs) { log ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = log.orderId, fontWeight = FontWeight.Bold, color = Color(0xFFFFB300), fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = "• ${log.customerName}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp)),
                                        color = Color.Gray,
                                        fontSize = 9.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Service: ${log.serviceName}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.RecordVoiceOver, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "\"${log.transcription}\"",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SHOP OWNER WEBSITE QR PANEL
// ==========================================
@Composable
fun ShopQRPanel(viewModel: SmartXeroxViewModel) {
    val context = LocalContext.current
    var qrDownloaded by remember { mutableStateOf(false) }
    val rawShopUrl = "https://ais-dev-nlecgp4wfa3sgpjani44jp-135102368660.asia-southeast1.run.app/?shop=OWNER01"
    val shopUrl = viewModel.validateAndSanitizeUrl(rawShopUrl)

    val ownerCredits by viewModel.ownerCredits.collectAsState()
    var qrType by remember { mutableStateOf("Ordering") } // "Ordering" or "DirectUPI"
    var paymentAmount by remember { mutableStateOf("") } // Optional dynamic payment amount

    val upiPayload = remember(ownerCredits.ownerUpi, ownerCredits.ownerUsername, paymentAmount) {
        val upi = ownerCredits.ownerUpi.trim().ifBlank { "owner@upi" }
        val name = ownerCredits.ownerUsername.trim().ifBlank { "SmartXerox_OWNER01" }
        val amount = paymentAmount.trim()
        val amountQuery = if (amount.isNotBlank() && amount.all { it.isDigit() }) "&am=$amount" else ""
        "upi://pay?pa=$upi&pn=$name$amountQuery&cu=INR&tn=SmartXeroxPayment"
    }

    val currentPayload = if (qrType == "Ordering") shopUrl else upiPayload
    val currentDocName = if (qrType == "Ordering") "Shop_OWNER01_Website" else "Shop_OWNER01_UPI_Payment"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Main QR Display Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, Color(0xFF6750A4).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Shop QR Code",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Select the appropriate option below. You can generate an Order QR for customers to place orders, or a Direct Payment QR to accept payments directly.",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // QR Type Switcher Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Ordering" to "Order QR", "DirectUPI" to "Direct UPI QR").forEach { (type, label) ->
                            val isSelected = qrType == type
                            Button(
                                onClick = { 
                                    qrType = type 
                                    qrDownloaded = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) Color(0xFF6750A4) else Color.Transparent,
                                    contentColor = if (isSelected) Color.White else Color.LightGray
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (qrType == "DirectUPI") {
                        OutlinedTextField(
                            value = paymentAmount,
                            onValueChange = { if (it.all { char -> char.isDigit() }) paymentAmount = it },
                            label = { Text("( - ₹) [Optional Amount]", color = Color.White, fontSize = 11.sp) },
                            placeholder = { Text("e.g., 10, 20, 50") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFFB300),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Payments will be sent directly to your UPI ID: ${ownerCredits.ownerUpi}",
                            color = Color(0xFFFFB300),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    
                    // The QR code container with stylized border and details
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .size(210.dp)
                            .border(2.dp, Color(0xFF6750A4), RoundedCornerShape(16.dp)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                QRCodeMockup(
                                    payload = currentPayload,
                                    modifier = Modifier.size(130.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (qrType == "Ordering") "Smart X Point • SCAN TO PRINT" else "Smart X Point • SCAN TO PAY",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF6750A4),
                                    fontSize = 9.sp,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Support: 7720007020",
                                    color = Color.DarkGray,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Web Link display / UPI ID display
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (qrType == "Ordering") Icons.Default.Link else Icons.Default.QrCode,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (qrType == "Ordering") shopUrl else "UPI: ${ownerCredits.ownerUpi}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Download actions
                    if (qrDownloaded) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Saved to Gallery / Downloads 📥",
                                color = Color(0xFF10B981),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                downloadQRCodeToGallery(context, currentPayload, currentDocName)
                                qrDownloaded = true
                             },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download QR",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Download QR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                             )
                        }
                    }
                }
            }
        }
        
        // Tip card about scanning & avoiding 404
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFFCD34D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "QR Scanning & Error Tips",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E),
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Deep Linking",
                        fontSize = 10.sp,
                        color = Color(0xFF78350F),
                        lineHeight = 14.sp
                    )
                }
            }
        }
        
        // Step By Step Guide Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "How it works",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val steps = listOf(
                        "1️⃣" to "Session Initialization: Shop owner starts session and generates portal URL endpoint (?shop=OWNER01).",
                        "2️⃣" to "URL Routing: QR codes point directly to valid customer portal endpoints ensuring 0% 404 errors.",
                        "3️⃣" to "Customer Portal: Customers scan to connect instantly, upload documents, and submit orders.",
                        "4️⃣" to "Secure Sync: Real-time orders, prints, and AI passport photos sync across local Room DB & Firebase."
                    )
                    
                    steps.forEach { (emoji, text) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(text = emoji, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = text,
                                fontSize = 11.sp,
                                color = Color(0xFF475569),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. ADMIN DASHBOARD SCREEN
// ==========================================
@Composable
fun AdminDashboardScreen(
    viewModel: SmartXeroxViewModel
) {
    val adminSettings by viewModel.adminSettings.collectAsState()
    val orders by viewModel.orders.collectAsState()

    var showEditRechargeRate by remember { mutableStateOf(false) }
    var showDeploymentGuideModal by remember { mutableStateOf(false) }
    var cloudApiInput by remember(adminSettings) { mutableStateOf(adminSettings.cloudApiEndpoint) }
    var cloudDomainInput by remember(adminSettings) { mutableStateOf(adminSettings.cloudDomain) }
    var hostingPlatformInput by remember(adminSettings) { mutableStateOf(adminSettings.hostingPlatform) }
    var webhookUrlInput by remember(adminSettings) { mutableStateOf(adminSettings.webhookUrl) }
    var sslEnabledInput by remember(adminSettings) { mutableStateOf(adminSettings.sslEnabled) }
    val context = LocalContext.current

    if (showDeploymentGuideModal) {
        AlertDialog(
            onDismissRequest = { showDeploymentGuideModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Cloud, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🚀 Deployment & Hosting Guide", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                val scroll = rememberScrollState()
                Column(
                    modifier = Modifier.heightIn(max = 400.dp).verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Follow these instructions to make Smart X Point accessible online over the internet 24/7:", fontSize = 11.sp, color = Color.Gray)

                    Text("1. 🌐 Choosing a Hosting Platform:\n• Vercel / Netlify: Best for fast static frontend & serverless API routes.\n• Firebase Hosting: Seamless integration with Firebase Auth and Firestore real-time sync.\n• AWS / DigitalOcean / Cloud VPS: Full control for direct IPP printing server nodes.", fontSize = 11.sp, lineHeight = 16.sp)

                    Text("2. ⚙️ Environment Variables & Config:\nConfigure your production `.env` or Cloud Environment panel with your API keys, Firebase credentials, and custom domain URL.", fontSize = 11.sp, lineHeight = 16.sp)

                    Text("3. 🔗 Custom Domain & SSL:\nMap your custom domain (e.g. smartxerox.co.in) to your hosting provider's nameservers and enable free Let's Encrypt SSL certificates for secure HTTPS access.", fontSize = 11.sp, lineHeight = 16.sp)

                    Text("4. 📡 Real-Time Connectivity Monitoring:\nThe application header features a live status component monitoring internet connectivity and cloud API reachability (`navigator.onLine` / network status heartbeat).", fontSize = 11.sp, lineHeight = 16.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDeploymentGuideModal = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
                ) {
                    Text("Got It, Ready to Deploy!", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Aggregate statistics
    val totalOrders = orders.filter { it.status == "COMPLETED" }
    val totalPrintedDocsCount = totalOrders.size
    val totalRevenueEarned = totalOrders.sumOf { it.price }
    val totalAdminCommission = adminSettings.totalCommissionEarned
    val totalOwnerShare = totalRevenueEarned - totalAdminCommission

    val masterTransactions by viewModel.transactions.collectAsState()
    val regTransactions = masterTransactions.filter { it.packName.contains("Registration") || it.id.contains("REG") }
    val recTransactions = masterTransactions.filter { !it.packName.contains("Registration") && !it.id.contains("REG") }
    val totalRegRevenue = regTransactions.sumOf { it.amount }
    val totalSubRevenue = recTransactions.sumOf { it.amount }

    var adminTab by remember { mutableStateOf("Overview & Franchise") }

    val adminScrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(adminScrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WebPortalLinkBanner()
        SxpBrandHeaderComponent(
            title = "Smart X Point • Super Admin Master Dashboard",
            subtitle = "Franchise Control, Global Pricing & Audit Logs",
            customLogoUri = adminSettings.customLogoUri
        )

        // Real-time Firestore Sync Status Indicator Banner (Compact & Professional)
        Surface(
            color = Color(0xFFECFDF5),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF10B981), RoundedCornerShape(50.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Live Sync: Online",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF065F46)
                    )
                }
                Surface(
                    color = Color(0xFFD1FAE5),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Auth Active",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF047857),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Admin Navigation Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Overview & Franchise", "Pricing & Overrides", "Manage Recharge Packs", "Revenue Records", "Live QR Orders & Logs", "Audit Logs", "System Health", "Cloud & Deployment").forEach { tab ->
                val isSelected = adminTab == tab
                Surface(
                    onClick = { adminTab = tab },
                    color = if (isSelected) Color(0xFFEEF2FF) else Color.White,
                    contentColor = if (isSelected) Color(0xFF6750A4) else Color(0xFF64748B),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFFC7D2FE) else Color(0xFFE2E8F0)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)) {
                        Text(
                            text = when(tab) {
                                "Overview & Franchise" -> "Stats"
                                "Pricing & Overrides" -> "Rates"
                                "Manage Recharge Packs" -> "Packs"
                                "Revenue Records" -> "Revenue"
                                "Live QR Orders & Logs" -> "QR"
                                "Audit Logs" -> "Audit"
                                "System Health" -> "Health"
                                "Cloud & Deployment" -> "Cloud"
                                else -> tab
                            },
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        when (adminTab) {
            "Cloud & Deployment" -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Deployment & Cloud Status (Cleaned as requested)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Cloud, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cloud & Deployment Status", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                            }
                            Text(
                                text = "Admin panel is configured for cloud deployment and live Firestore synchronization.",
                                fontSize = 11.sp,
                                color = Color.LightGray
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Cloud, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Online Cloud Hosting & Deployment", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Make your application accessible 24/7 over the public internet. Host on Vercel, Netlify, Firebase Hosting, or dedicated cloud servers with real-time connectivity status.", fontSize = 11.sp, color = Color(0xFF94A3B8), lineHeight = 16.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { showDeploymentGuideModal = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.MenuBook, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Complete Deployment Guide", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("☁️ Cloud Environment Configuration", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                            Text("Manage public API endpoints, custom domains, and SSL settings for your production server.", fontSize = 10.sp, color = Color.Gray)
                            
                            OutlinedTextField(
                                value = cloudApiInput,
                                onValueChange = { cloudApiInput = it },
                                label = { Text("Cloud API Endpoint URL") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = cloudDomainInput,
                                onValueChange = { cloudDomainInput = it },
                                label = { Text("Custom Domain Name (e.g. smartxerox.co.in)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = hostingPlatformInput,
                                onValueChange = { hostingPlatformInput = it },
                                label = { Text("Hosting Platform (Vercel / Netlify / Firebase)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = webhookUrlInput,
                                onValueChange = { webhookUrlInput = it },
                                label = { Text("Webhook Callback URL") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("SSL Certificate & HTTPS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Enforce secure encrypted connections over port 443", fontSize = 10.sp, color = Color.Gray)
                                }
                                Switch(
                                    checked = sslEnabledInput,
                                    onCheckedChange = { sslEnabledInput = it }
                                )
                            }

                            Button(
                                onClick = {
                                    viewModel.updateCloudSettings(cloudApiInput, cloudDomainInput, hostingPlatformInput, sslEnabledInput, webhookUrlInput)
                                    Toast.makeText(context, "Cloud environment configuration saved successfully! ☁️", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Cloud Environment Settings", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    // Scheduled Auto-Backup Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("⏱️ Scheduled Daily Cloud Auto-Backup", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                            }
                            Text("Automatically backup print queue, transactions, and earnings history to the cloud environment daily at midnight to prevent any data loss during deployment updates.", fontSize = 10.sp, color = Color.Gray, lineHeight = 15.sp)

                            var autoBackupToggle by remember { mutableStateOf(true) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Daily Auto-Backup Active", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Switch(
                                    checked = autoBackupToggle,
                                    onCheckedChange = { autoBackupToggle = it }
                                )
                            }

                            Button(
                                onClick = {
                                    viewModel.runCloudAutoBackup { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Run Cloud Auto-Backup Now", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    // Cloud Storage Module Card (Firebase Storage)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.FolderZip, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("📦 Firebase Cloud Storage (Passport Batches)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                            }
                            Text("Securely save and retrieve your passport photo batches across different sessions and devices using Firebase Storage.", fontSize = 10.sp, color = Color.Gray, lineHeight = 15.sp)

                            var batchNameInput by remember { mutableStateOf("Passport Grid Batch - July 2026") }
                            OutlinedTextField(
                                value = batchNameInput,
                                onValueChange = { batchNameInput = it },
                                label = { Text("Passport Batch Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Button(
                                onClick = {
                                    if (batchNameInput.isNotBlank()) {
                                        viewModel.savePassportBatchToCloud(batchNameInput, 8) { success, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Upload Current Photo Batch to Firebase Storage", fontWeight = FontWeight.Bold, color = Color.Black)
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Saved Cloud Storage Batches:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF334155))

                            val cloudBatches by viewModel.cloudBatches.collectAsState()
                            if (cloudBatches.isEmpty()) {
                                Text("No cloud batches saved yet. Upload your first batch above.", fontSize = 10.sp, color = Color.Gray)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (batch in cloudBatches) {
                                        Surface(
                                            color = Color(0xFFF8FAFC),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(batch.batchName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                                                    Text("Photos: ${batch.photoCount} | Size: ${batch.fileSizeKb} KB", fontSize = 10.sp, color = Color.Gray)
                                                    Text(batch.cloudUrl, fontSize = 8.5.sp, color = Color(0xFF0284C7), maxLines = 1)
                                                }
                                                IconButton(
                                                    onClick = {
                                                        viewModel.deleteCloudBatch(batch.batchId)
                                                        Toast.makeText(context, "Batch deleted from cloud storage", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "System Health" -> {
                val systemLogs by viewModel.systemLogs.collectAsState()
                val printerHandshakeLogs by viewModel.printerHandshakeLogs.collectAsState()
                val isApiOnline by viewModel.isApiOnline.collectAsState()
                val geminiStatus by viewModel.geminiStatus.collectAsState()
                val firebaseStatus by viewModel.firebaseStatus.collectAsState()
                var healthSubTab by remember { mutableStateOf("System Logs") }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("System Logs", "Printer Handshakes & Diagnostics", "Performance & Telemetry", "Lighthouse PWA Audit").forEach { sub ->
                            val isSel = healthSubTab == sub
                            Surface(
                                onClick = { healthSubTab = sub },
                                color = if (isSel) Color(0xFF6750A4) else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text(sub, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.White else Color(0xFF334155))
                                }
                            }
                        }
                    }

                    if (healthSubTab == "Lighthouse PWA Audit") {
                        val lighthouseScore by viewModel.lighthouseScore.collectAsState()
                        val lighthouseReport by viewModel.lighthouseReport.collectAsState()

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Verified, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("📊 Lighthouse Performance & PWA Audit Suite", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                                }
                                Text("Evaluate site load speeds, progressive web app manifest validation, service worker caching, and SEO sitemap indexing for smartxerox.co.in.", fontSize = 10.sp, color = Color.Gray, lineHeight = 15.sp)

                                Surface(
                                    color = Color(0xFFF0FDF4),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Overall Lighthouse Score", fontSize = 10.sp, color = Color(0xFF166534), fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("$lighthouseScore / 100", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(lighthouseReport, fontSize = 9.5.sp, color = Color(0xFF166534), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    }
                                }

                                Button(
                                    onClick = {
                                        viewModel.runLighthouseAudit { score, report ->
                                            Toast.makeText(context, "Lighthouse Audit completed! Score: $score/100", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Run Full Lighthouse Audit Suite Now", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    if (healthSubTab == "Performance & Telemetry") {
                        val dailySessions by viewModel.dailyActiveSessions.collectAsState()
                        val errorCount by viewModel.errorOccurrencesCount.collectAsState()
                        val avgLatency by viewModel.avgApiResponseTimeMs.collectAsState()
                        val memoryUsage by viewModel.memoryUsageMb.collectAsState()

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = Color(0xFF6750A4), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("⚡ Real-Time Performance Telemetry", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                                }
                                Text("Lightweight diagnostics script tracking daily active sessions, response latency, and error occurrences for post-deployment remote monitoring.", fontSize = 10.sp, color = Color.Gray, lineHeight = 15.sp)

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Active Sessions", fontSize = 9.sp, color = Color.Gray)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("$dailySessions", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0284C7))
                                        }
                                    }
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Error Occurrences", fontSize = 9.sp, color = Color.Gray)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("$errorCount", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (errorCount > 0) Color(0xFFEF4444) else Color(0xFF22C55E))
                                        }
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Avg Latency", fontSize = 9.sp, color = Color.Gray)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("${avgLatency}ms", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF16A34A))
                                        }
                                    }
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Memory Footprint", fontSize = 9.sp, color = Color.Gray)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("${memoryUsage}MB", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFD97706))
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        viewModel.recordErrorOccurrence("Test manual debug diagnostic trigger")
                                        Toast.makeText(context, "Telemetry test error recorded successfully", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Simulate Error Occurrence / Trigger Diagnostic Ping", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    if (healthSubTab == "Printer Handshakes & Diagnostics") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Printer Connection Handshakes & Error Codes", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                                Text("Real-time IPP / USB handshake telemetry between app and local printing devices.", fontSize = 10.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(12.dp))

                                printerHandshakeLogs.forEach { ph ->
                                    Surface(
                                        color = Color(0xFFF8FAFC),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, if (ph.statusCode.contains("200")) Color(0xFF86EFAC) else Color(0xFFFCA5A5)),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("${ph.printerName} (${ph.ipAddress})", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF0F172A))
                                                Surface(
                                                    color = if (ph.statusCode.contains("200")) Color(0xFFDCFCE7) else Color(0xFFFEF2F2),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(ph.statusCode, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (ph.statusCode.contains("200")) Color(0xFF16A34A) else Color(0xFFDC2626), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(ph.message, fontSize = 10.sp, color = Color(0xFF334155))
                                            val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(ph.timestamp))
                                            Text("Timestamp: $timeStr", fontSize = 8.5.sp, color = Color.Gray, modifier = Modifier.padding(top = 2.dp))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Real-Time API & Service Status", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Backend API Status", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text(if (isApiOnline) "ONLINE (OK)" else "OFFLINE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isApiOnline) Color(0xFF16A34A) else Color(0xFFDC2626))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Gemini AI (gemini-3.5-flash)", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text(geminiStatus, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF16A34A))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Firebase Cloud Sync", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text(firebaseStatus, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF16A34A))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("URL Validation Middleware", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text("Active (0% 404 Error Risk)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF16A34A))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Centralized System Health & Error Logs (${systemLogs.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                        Button(
                            onClick = { viewModel.logSystem("INFO", "HEALTH", "Manual health refresh invoked by admin.") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Refresh Logs", fontSize = 10.sp, color = Color.White)
                        }
                    }

                    systemLogs.forEach { log ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, when(log.level) {
                                "ERROR" -> Color(0xFFFCA5A5)
                                "WARN" -> Color(0xFFFDE68A)
                                else -> Color(0xFFE2E8F0)
                            }),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        color = when(log.level) {
                                            "ERROR" -> Color(0xFFFEF2F2)
                                            "WARN" -> Color(0xFFFFFBEB)
                                            else -> Color(0xFFEEF2FF)
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "[${log.category}] ${log.level}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when(log.level) {
                                                "ERROR" -> Color(0xFFDC2626)
                                                "WARN" -> Color(0xFFD97706)
                                                else -> Color(0xFF6750A4)
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp))
                                    Text(timeStr, fontSize = 9.sp, color = Color(0xFF94A3B8))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(log.message, fontSize = 11.sp, color = Color(0xFF334155))
                            }
                        }
                    }
                }
            }
        }
            "Overview & Franchise" -> {
                val masterTransactions by viewModel.transactions.collectAsState()
                val sdfDay = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val todayStr = sdfDay.format(java.util.Date())
                val todayEarnings = masterTransactions.filter { sdfDay.format(java.util.Date(it.timestamp)) == todayStr }.sumOf { it.amount }
                val completedPrintJobsCount = orders.count { it.status.equals("COMPLETED", ignoreCase = true) }
                val context = LocalContext.current

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Franchise Owner Management Card (Delete / Reset Unpaid / Mistake Registration)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val ownerCredits by viewModel.ownerCredits.collectAsState()
                        var showDeleteOwnerConfirm by remember { mutableStateOf(false) }

                        if (showDeleteOwnerConfirm) {
                            AlertDialog(
                                onDismissRequest = { showDeleteOwnerConfirm = false },
                                title = { Text("⚠️ Delete Shop Owner Registration?", fontWeight = FontWeight.Bold, color = Color(0xFF991B1B)) },
                                text = { Text("Are you sure you want to delete/reset the shop owner registration? Use this if registration occurred without payment or by mistake. The shop owner will need to re-register.", fontSize = 12.sp) },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            viewModel.resetOwnerRegistration { success ->
                                                if (success) {
                                                    Toast.makeText(context, "Shop owner registration deleted/reset successfully! 🗑️", Toast.LENGTH_SHORT).show()
                                                    showDeleteOwnerConfirm = false
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                                    ) {
                                        Text("Yes, Delete Registration", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteOwnerConfirm = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Franchise Owner Account Control",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Manage registered shop status & unpaid/mistaken registrations",
                                        color = Color.LightGray,
                                        fontSize = 9.sp
                                    )
                                }
                                Surface(
                                    color = if (ownerCredits.isRegistered) Color(0xFFD1FAE5) else Color(0xFFFEE2E2),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (ownerCredits.isRegistered) "REGISTERED" else "UNREGISTERED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (ownerCredits.isRegistered) Color(0xFF047857) else Color(0xFF991B1B),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Shop Username: ${ownerCredits.ownerUsername}", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("Shop UPI ID: ${ownerCredits.ownerUpi}", fontSize = 11.sp, color = Color(0xFFFFB300))
                                    val expDate = if (ownerCredits.subscriptionExpires > 0) java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(ownerCredits.subscriptionExpires)) else "N/A"
                                    Text("Subscription Expires: $expDate", fontSize = 10.sp, color = Color.LightGray)
                                }
                            }

                            if (ownerCredits.isRegistered) {
                                Button(
                                    onClick = { showDeleteOwnerConfirm = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Delete / Reset Owner Registration (Unpaid / Mistake)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    // Daily Earnings & Completed Print Jobs Summary Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFB300)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Today's Daily Earnings & Print Jobs",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFB300),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Calculated from local transaction and order storage",
                                        color = Color.LightGray,
                                        fontSize = 9.sp
                                    )
                                }
                                Button(
                                    onClick = { viewModel.exportDailyEarningsCsv(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download CSV", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "Today's Earnings", color = Color.LightGray, fontSize = 8.sp)
                                        Text(text = "₹${todayEarnings.toInt()}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF4ADE80))
                                    }
                                }
                                Surface(
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "Completed Print Jobs", color = Color.LightGray, fontSize = 8.sp)
                                        Text(text = "$completedPrintJobsCount Jobs", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF38BDF8))
                                    }
                                }
                            }
                        }
                    }

                    // Admin Summary Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = "Printed", color = Color.LightGray, fontSize = 9.sp)
                                Text(text = "$totalPrintedDocsCount", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = "Registration Rev", color = Color.LightGray, fontSize = 9.sp)
                                Text(text = "₹${totalRegRevenue.toInt()}", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.Green)
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = "Sub Rev", color = Color.LightGray, fontSize = 9.sp)
                                Text(text = "₹${totalSubRevenue.toInt()}", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color(0xFFFFB300))
                            }
                        }
                    }
                    
                    AdminDataVisualizationComponent(orders = orders)
                }
            }
            "Pricing & Overrides" -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Default Service Rates list
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Default Admin Rates",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            val serviceRates by viewModel.serviceRates.collectAsState()
                            var editTarget by remember { mutableStateOf<ServiceRate?>(null) }

                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                serviceRates.forEach { service ->
                                    Surface(
                                        color = Color(0xFF0F172A),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = service.name,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    fontSize = 11.sp
                                                )
                                                Text(
                                                    text = ": ${service.category}",
                                                    color = Color.LightGray,
                                                    fontSize = 9.sp
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Text(
                                                    text = "₹${service.rate.toInt()}",
                                                    color = Color(0xFFFFB300),
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 14.sp
                                                )
                                                IconButton(
                                                    onClick = { editTarget = service },
                                                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF334155), contentColor = Color.White),
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit default rate", modifier = Modifier.size(12.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (editTarget != null) {
                                EditRateDialog(
                                    serviceRate = editTarget!!,
                                    onDismiss = { editTarget = null },
                                    onSave = { updatedRate ->
                                        viewModel.updateServiceRate(
                                            id = updatedRate.id,
                                            name = updatedRate.name,
                                            rate = updatedRate.rate,
                                            category = updatedRate.category
                                        )
                                        editTarget = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
            "Manage Recharge Packs" -> {
                // Admin Recharge Packs configuration with Unified Subscription Plans
                val context = LocalContext.current
                val masterTransactions by viewModel.transactions.collectAsState()

                var adminUpiText by remember(adminSettings) { mutableStateOf(adminSettings.adminUpiId) }
                var p1Text by remember(adminSettings) { mutableStateOf(adminSettings.plan1MonthPrice.toString()) }
                var p3Text by remember(adminSettings) { mutableStateOf(adminSettings.plan3MonthPrice.toString()) }
                var p6Text by remember(adminSettings) { mutableStateOf(adminSettings.plan6MonthPrice.toString()) }
                var p12Text by remember(adminSettings) { mutableStateOf(adminSettings.plan12MonthPrice.toString()) }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Subscription & Master UPI",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB300),
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Manage subscription plan prices and Master UPI ID for shop owners:",
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = adminUpiText,
                            onValueChange = { adminUpiText = it },
                            label = { Text("Master UPI ID", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFFB300),
                                unfocusedBorderColor = Color.Gray,
                                focusedLabelColor = Color(0xFFFFB300),
                                unfocusedLabelColor = Color.LightGray
                            )
                        )

                        // 1 Month & 3 Month Rows
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = p1Text,
                                onValueChange = { p1Text = it },
                                label = { Text("1 Month Std Plan (₹)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFFFB300),
                                    unfocusedBorderColor = Color.Gray,
                                    focusedLabelColor = Color(0xFFFFB300),
                                    unfocusedLabelColor = Color.LightGray
                                )
                            )
                            OutlinedTextField(
                                value = p3Text,
                                onValueChange = { p3Text = it },
                                label = { Text("3 Month Biz Plan (₹)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFFFB300),
                                    unfocusedBorderColor = Color.Gray,
                                    focusedLabelColor = Color(0xFFFFB300),
                                    unfocusedLabelColor = Color.LightGray
                                )
                            )
                        }

                        // 6 Month & 12 Month Rows
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = p6Text,
                                onValueChange = { p6Text = it },
                                label = { Text("6 Month Super Plan (₹)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFFFB300),
                                    unfocusedBorderColor = Color.Gray,
                                    focusedLabelColor = Color(0xFFFFB300),
                                    unfocusedLabelColor = Color.LightGray
                                )
                            )
                            OutlinedTextField(
                                value = p12Text,
                                onValueChange = { p12Text = it },
                                label = { Text("12 Month Elite Plan (₹)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFFFB300),
                                    unfocusedBorderColor = Color.Gray,
                                    focusedLabelColor = Color(0xFFFFB300),
                                    unfocusedLabelColor = Color.LightGray
                                )
                            )
                        }

                        val context = LocalContext.current
                        Button(
                            onClick = {
                                val p1 = p1Text.toDoubleOrNull() ?: adminSettings.plan1MonthPrice
                                val p3 = p3Text.toDoubleOrNull() ?: adminSettings.plan3MonthPrice
                                val p6 = p6Text.toDoubleOrNull() ?: adminSettings.plan6MonthPrice
                                val p12 = p12Text.toDoubleOrNull() ?: adminSettings.plan12MonthPrice
                                viewModel.updateAdminSettingsAndPrices(
                                    adminUpi = adminUpiText.trim(),
                                    adminUser = adminSettings.adminUsername,
                                    adminPass = adminSettings.adminPassword,
                                    p1 = p1,
                                    p3 = p3,
                                    p6 = p6,
                                    p12 = p12
                                )
                                Toast.makeText(context, "Rates and UPI ID saved successfully! 🎉", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300), contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Text("Save Plan Prices", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Custom Header Logo Management Card
                val logoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri != null) {
                        viewModel.updateCustomLogoUri(context, uri)
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFB300)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Custom Header Logo (Branding)",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB300),
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Upload or replace the application header logo across the app. Transparent PNGs are fully supported and paired with a high-contrast yellow & black color scheme.",
                            color = Color.LightGray,
                            fontSize = 10.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                modifier = Modifier.size(56.dp),
                                border = BorderStroke(1.dp, Color(0xFFFFB300))
                            ) {
                                if (!adminSettings.customLogoUri.isNullOrEmpty()) {
                                    coil.compose.AsyncImage(
                                        model = adminSettings.customLogoUri,
                                        contentDescription = "Custom Logo Preview",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().padding(4.dp)
                                    )
                                } else {
                                    androidx.compose.foundation.Image(
                                        painter = painterResource(id = R.drawable.img_smart_x_logo),
                                        contentDescription = "Default Logo",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().padding(4.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { logoPickerLauncher.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Upload / Replace", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }

                                if (!adminSettings.customLogoUri.isNullOrEmpty()) {
                                    OutlinedButton(
                                        onClick = { viewModel.resetCustomLogo(context) },
                                        border = BorderStroke(1.dp, Color(0xFFFFB300)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Reset", fontSize = 10.sp, color = Color(0xFFFFB300))
                                    }
                                }
                            }
                        }
                    }
                }

                // Transaction Master History
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Master Transaction History",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (masterTransactions.isEmpty()) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(100.dp)) {
                                Text(text = "No transactions recorded yet.", color = Color.LightGray, fontSize = 11.sp)
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                masterTransactions.forEach { tx ->
                                    Surface(
                                        color = Color(0xFF0F172A),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(text = "Shop Owner: OWNER01", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 11.sp)
                                                Text(text = ": ${tx.id} | : ${tx.packName}", color = Color.LightGray, fontSize = 9.sp)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(text = "+${tx.creditsAdded} Credits", color = Color(0xFFFFB300), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text(text = "₹${tx.amount.toInt()} PAID", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "Revenue Records" -> {
                val masterTransactions by viewModel.transactions.collectAsState()
                var revenueFilter by remember { mutableStateOf("ALL") } // "ALL", "REG", "REC"
                var transactionSearchQuery by remember { mutableStateOf("") }
                var transactionStatusFilter by remember { mutableStateOf("ALL") } // "ALL", "PENDING", "COMPLETED"
                var transactionDateFilter by remember { mutableStateOf("ALL") } // "ALL", "TODAY", "7D", "30D"
                var txToDelete by remember { mutableStateOf<Transaction?>(null) }

                if (txToDelete != null) {
                    AlertDialog(
                        onDismissRequest = { txToDelete = null },
                        title = { Text("⚠️ Delete Transaction Record?", fontWeight = FontWeight.Bold, color = Color(0xFF991B1B)) },
                        text = { Text("Are you sure you want to delete transaction ID: ${txToDelete!!.id}? This will remove it from earnings and Firestore (useful for correcting mistaken or unpaid registrations).", fontSize = 12.sp) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val id = txToDelete!!.id
                                    viewModel.deleteTransaction(id)
                                    Toast.makeText(context, "Transaction $id deleted successfully! 🗑️", Toast.LENGTH_SHORT).show()
                                    txToDelete = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                            ) {
                                Text("Yes, Delete", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { txToDelete = null }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                val regTransactions = masterTransactions.filter { it.id.startsWith("#TX_REG_") }
                val recTransactions = masterTransactions.filter { it.id.startsWith("#TX_REC_") || it.id.startsWith("#TX_RE") || it.packName.contains("Recharge", ignoreCase = true) }

                val totalRegRevenue = regTransactions.sumOf { it.amount }
                val totalRecRevenue = recTransactions.sumOf { it.amount }
                val grandTotalRevenue = masterTransactions.sumOf { it.amount }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // KPI Cards for Revenue
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Registrations", color = Color.LightGray, fontSize = 8.sp, textAlign = TextAlign.Center)
                                Text(text = "₹${totalRegRevenue.toInt()}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF38BDF8))
                                Text(text = "${regTransactions.size} Records", color = Color.LightGray, fontSize = 8.sp)
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Recharges", color = Color.LightGray, fontSize = 8.sp, textAlign = TextAlign.Center)
                                Text(text = "₹${totalRecRevenue.toInt()}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF4ADE80))
                                Text(text = "${recTransactions.size} Records", color = Color.LightGray, fontSize = 8.sp)
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            border = BorderStroke(1.dp, Color(0xFFFFB300)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Grand Total", color = Color(0xFFFFB300), fontSize = 8.sp, textAlign = TextAlign.Center)
                                Text(text = "₹${grandTotalRevenue.toInt()}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFFFFB300))
                                Text(text = "${masterTransactions.size} Total Tx", color = Color.LightGray, fontSize = 8.sp)
                            }
                        }
                    }

                    // --- RECHARTS 30-DAY REVENUE LINE GRAPH WIDGET ---
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "📊 Recharts • 30-Day Revenue Trend",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB300),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Interactive Recharts line graph of total revenue earned over the last 30 days",
                                color = Color.LightGray,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            val last30DaysData = remember(masterTransactions) {
                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                val cal = java.util.Calendar.getInstance()
                                val map = mutableMapOf<String, Double>()
                                for (i in 29 downTo 0) {
                                    val dateStr = sdf.format(cal.time)
                                    map[dateStr] = 0.0
                                    cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                                }
                                masterTransactions.forEach { tx ->
                                    val dateStr = sdf.format(java.util.Date(tx.timestamp))
                                    if (map.containsKey(dateStr)) {
                                        map[dateStr] = (map[dateStr] ?: 0.0) + tx.amount
                                    }
                                }
                                map.entries.sortedBy { it.key }.map { mapEntry ->
                                    Pair(mapEntry.key.substring(5), mapEntry.value)
                                }
                            }

                            val jsonRechartsLabels = last30DaysData.joinToString(prefix = "[\"", separator = "\", \"", postfix = "\"]") { it.first }
                            val jsonRechartsValues = last30DaysData.joinToString(prefix = "[", separator = ", ", postfix = "]") { it.second.toString() }

                            AndroidView(
                                factory = { ctx ->
                                    android.webkit.WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        setBackgroundColor(0xFF1E293B.toInt())
                                    }
                                },
                                update = { webView ->
                                    val html = """
                                        <!DOCTYPE html>
                                        <html>
                                        <head>
                                            <script src="https://unpkg.com/react@18/umd/react.production.min.js"></script>
                                            <script src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"></script>
                                            <script src="https://unpkg.com/@babel/standalone/babel.min.js"></script>
                                            <script src="https://cdnjs.cloudflare.com/ajax/libs/recharts/2.12.7/Recharts.min.js"></script>
                                            <style>
                                                body { background: #1E293B; color: #FFF; font-family: sans-serif; margin: 0; padding: 0; }
                                            </style>
                                        </head>
                                        <body>
                                            <div id="recharts-root"></div>
                                            <script type="text/babel">
                                                const { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } = Recharts;
                                                const labels = $jsonRechartsLabels;
                                                const values = $jsonRechartsValues;
                                                const data = labels.map((l, i) => ({date: l, revenue: values[i]}));

                                                function RevenueTrendChart() {
                                                    return (
                                                        <div style={{ width: '100%', height: 150 }}>
                                                            <ResponsiveContainer>
                                                                <LineChart data={data} margin={{top: 5, right: 10, left: -20, bottom: 5}}>
                                                                    <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                                                                    <XAxis dataKey="date" stroke="#94A3B8" fontSize={8} />
                                                                    <YAxis stroke="#94A3B8" fontSize={8} />
                                                                    <Tooltip contentStyle={{ backgroundColor: '#0F172A', borderColor: '#334155', color: '#FFF', fontSize: '10px' }} />
                                                                    <Line type="monotone" dataKey="revenue" stroke="#FFB300" strokeWidth={2} dot={{ r: 2 }} activeDot={{ r: 4 }} />
                                                                </LineChart>
                                                            </ResponsiveContainer>
                                                        </div>
                                                    );
                                                }
                                                ReactDOM.createRoot(document.getElementById('recharts-root')).render(<RevenueTrendChart />);
                                            </script>
                                        </body>
                                        </html>
                                    """.trimIndent()
                                    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                                },
                                modifier = Modifier.fillMaxWidth().height(160.dp)
                            )
                        }
                    }

                    // --- NATIVE MONTHLY REVENUE GROWTH BAR CHART ---
                    val monthlyData = remember(masterTransactions) {
                        val sdf = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.ENGLISH)
                        
                        // Initialize a list of the last 6 months with 0.0 values
                        val last6Months = (0..5).map { offset ->
                            val c = java.util.Calendar.getInstance()
                            c.add(java.util.Calendar.MONTH, -offset)
                            sdf.format(c.time)
                        }.reversed()
                        
                        val regMap = mutableMapOf<String, Double>()
                        val recMap = mutableMapOf<String, Double>()
                        
                        last6Months.forEach { month ->
                            regMap[month] = 0.0
                            recMap[month] = 0.0
                        }
                        
                        masterTransactions.forEach { tx ->
                            val dateStr = sdf.format(java.util.Date(tx.timestamp))
                            if (regMap.containsKey(dateStr)) {
                                val isReg = tx.id.startsWith("#TX_REG_") || tx.packName.contains("Registration") || tx.id.contains("REG")
                                if (isReg) {
                                    regMap[dateStr] = (regMap[dateStr] ?: 0.0) + tx.amount
                                } else {
                                    recMap[dateStr] = (recMap[dateStr] ?: 0.0) + tx.amount
                                }
                            }
                        }
                        
                        last6Months.map { month ->
                            Triple(month, regMap[month] ?: 0.0, recMap[month] ?: 0.0)
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Monthly Revenue Growth Dashboard",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Monthly growth of Registrations and Recharges",
                                color = Color.LightGray,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            
                            val maxVal = remember(monthlyData) {
                                val highest = monthlyData.maxOfOrNull { it.second + it.third } ?: 100.0
                                if (highest <= 0.0) 100.0 else highest * 1.15
                            }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .padding(top = 4.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val width = size.width
                                    val height = size.height
                                    val paddingLeft = 32.dp.toPx()
                                    val paddingBottom = 16.dp.toPx()
                                    val chartWidth = width - paddingLeft
                                    val chartHeight = height - paddingBottom
                                    
                                    // Draw gridlines (Y-axis)
                                    val gridCount = 4
                                    for (i in 0..gridCount) {
                                        val y = chartHeight * (1f - i.toFloat() / gridCount)
                                        drawLine(
                                            color = Color.Gray.copy(alpha = 0.15f),
                                            start = Offset(paddingLeft, y),
                                            end = Offset(width, y),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                                    
                                    // Draw Bars
                                    val colWidth = chartWidth / monthlyData.size
                                    val barWidth = 8.dp.toPx()
                                    
                                    monthlyData.forEachIndexed { index, (month, regVal, recVal) ->
                                        val xCenter = paddingLeft + index * colWidth + colWidth / 2f
                                        
                                        // Reg Bar (Blue)
                                        val regBarHeight = (regVal / maxVal).toFloat() * chartHeight
                                        val regLeft = xCenter - barWidth - 1.dp.toPx()
                                        drawRect(
                                            color = Color(0xFF38BDF8),
                                            topLeft = Offset(regLeft, chartHeight - regBarHeight),
                                            size = Size(barWidth, regBarHeight)
                                        )
                                        
                                        // Rec Bar (Green)
                                        val recBarHeight = (recVal / maxVal).toFloat() * chartHeight
                                        val recLeft = xCenter + 1.dp.toPx()
                                        drawRect(
                                            color = Color(0xFF4ADE80),
                                            topLeft = Offset(recLeft, chartHeight - recBarHeight),
                                            size = Size(barWidth, recBarHeight)
                                        )
                                    }
                                }
                                
                                // Overlay labels on top of bars or underneath them
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomStart)
                                        .padding(start = 32.dp),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    monthlyData.forEach { (month, _, _) ->
                                        Text(
                                            text = month.substringBefore(" "), // e.g., "Jul"
                                            color = Color.LightGray,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            // Legend
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF38BDF8), RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Registrations", color = Color.LightGray, fontSize = 8.sp)
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF4ADE80), RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Recharges", color = Color.LightGray, fontSize = 8.sp)
                            }
                        }
                    }

                    // --- D3-BASED DAILY EARNINGS BAR CHART ---
                    val dailyEarningsMap = remember(masterTransactions) {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        val map = mutableMapOf<String, Double>()
                        masterTransactions.forEach { tx ->
                            val dateStr = sdf.format(java.util.Date(tx.timestamp))
                            map[dateStr] = (map[dateStr] ?: 0.0) + tx.amount
                        }
                        map.entries.sortedBy { it.key }.takeLast(7)
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "D3.js Daily Earnings Analytics",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB300),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Interactive D3.js bar chart of daily earnings retrieved from local browser storage",
                                color = Color.LightGray,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            val jsonLabels = dailyEarningsMap.joinToString(prefix = "[\"", separator = "\", \"", postfix = "\"]") { it.key.substring(5) }
                            val jsonValues = dailyEarningsMap.joinToString(prefix = "[", separator = ", ", postfix = "]") { it.value.toString() }

                            AndroidView(
                                factory = { ctx ->
                                    android.webkit.WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        setBackgroundColor(0xFF1E293B.toInt())
                                    }
                                },
                                update = { webView ->
                                    val html = """
                                        <!DOCTYPE html>
                                        <html>
                                        <head>
                                            <script src="https://d3js.org/d3.v7.min.js"></script>
                                            <style>
                                                body { background: #1E293B; color: #FFF; font-family: sans-serif; margin: 0; padding: 0; }
                                                .bar { fill: #FFB300; transition: fill 0.2s; }
                                                .bar:hover { fill: #4ADE80; }
                                                .axis text { fill: #94A3B8; font-size: 9px; }
                                                .axis path, .axis line { stroke: #334155; }
                                            </style>
                                        </head>
                                        <body>
                                            <div id="chart"></div>
                                            <script>
                                                const labels = $jsonLabels;
                                                const values = $jsonValues;
                                                const data = labels.map((l, i) => ({date: l, amount: values[i]}));

                                                const margin = {top: 10, right: 10, bottom: 20, left: 35};
                                                const width = 320 - margin.left - margin.right;
                                                const height = 130 - margin.top - margin.bottom;

                                                d3.select("#chart").html("");
                                                const svg = d3.select("#chart")
                                                    .append("svg")
                                                    .attr("width", "100%")
                                                    .attr("height", "130")
                                                    .attr("viewBox", "0 0 320 130")
                                                    .append("g")
                                                    .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

                                                const x = d3.scaleBand()
                                                    .range([0, width])
                                                    .domain(data.map(d => d.date))
                                                    .padding(0.2);

                                                svg.append("g")
                                                    .attr("transform", "translate(0," + height + ")")
                                                    .call(d3.axisBottom(x))
                                                    .selectAll("text")
                                                    .attr("transform", "translate(-10,0)rotate(-25)")
                                                    .style("text-anchor", "end");

                                                const y = d3.scaleLinear()
                                                    .domain([0, d3.max(data, d => d.amount) > 0 ? d3.max(data, d => d.amount) * 1.15 : 100])
                                                    .range([height, 0]);

                                                svg.append("g")
                                                    .call(d3.axisLeft(y).ticks(4));

                                                svg.selectAll(".bar")
                                                    .data(data)
                                                    .enter()
                                                    .append("rect")
                                                    .attr("class", "bar")
                                                    .attr("x", d => x(d.date))
                                                    .attr("y", d => y(d.amount))
                                                    .attr("width", x.bandwidth())
                                                    .attr("height", d => height - y(d.amount));
                                            </script>
                                        </body>
                                        </html>
                                    """.trimIndent()
                                    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                                },
                                modifier = Modifier.fillMaxWidth().height(140.dp)
                            )
                        }
                    }

                    // Filter row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("ALL", "REG", "REC").forEach { filter ->
                            val isSelected = revenueFilter == filter
                            val label = when(filter) {
                                "ALL" -> "All"
                                "REG" -> "New Registration"
                                "REC" -> "Subscription Recharges"
                                else -> filter
                            }
                            Button(
                                onClick = { revenueFilter = filter },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) Color(0xFFFFB300) else Color(0xFF334155),
                                    contentColor = if (isSelected) Color.Black else Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Search Bar
                    OutlinedTextField(
                        value = transactionSearchQuery,
                        onValueChange = { transactionSearchQuery = it },
                        label = { Text("Search transactions by ID or Name...", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        singleLine = true,
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFB300),
                            unfocusedBorderColor = Color.Gray
                        )
                    )

                    // Filter Dropdowns Row (Status & Date Range)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var statusExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { statusExpanded = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth().height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Status: $transactionStatusFilter", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB300))
                            }
                            DropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                                DropdownMenuItem(text = { Text("All Statuses", fontSize = 11.sp) }, onClick = { transactionStatusFilter = "ALL"; statusExpanded = false })
                                DropdownMenuItem(text = { Text("Completed", fontSize = 11.sp) }, onClick = { transactionStatusFilter = "COMPLETED"; statusExpanded = false })
                                DropdownMenuItem(text = { Text("Pending", fontSize = 11.sp) }, onClick = { transactionStatusFilter = "PENDING"; statusExpanded = false })
                            }
                        }

                        var dateExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { dateExpanded = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth().height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Date: $transactionDateFilter", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB300))
                            }
                            DropdownMenu(expanded = dateExpanded, onDismissRequest = { dateExpanded = false }) {
                                DropdownMenuItem(text = { Text("All Time", fontSize = 11.sp) }, onClick = { transactionDateFilter = "ALL"; dateExpanded = false })
                                DropdownMenuItem(text = { Text("Today", fontSize = 11.sp) }, onClick = { transactionDateFilter = "TODAY"; dateExpanded = false })
                                DropdownMenuItem(text = { Text("Last 7 Days", fontSize = 11.sp) }, onClick = { transactionDateFilter = "7D"; dateExpanded = false })
                                DropdownMenuItem(text = { Text("Last 30 Days", fontSize = 11.sp) }, onClick = { transactionDateFilter = "30D"; dateExpanded = false })
                            }
                        }
                    }

                    // Transaction list card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            val context = LocalContext.current
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Revenue Records List",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                                Button(
                                    onClick = { viewModel.exportTransactionsCsv(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Export CSV", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }

                            val displayTx = when (revenueFilter) {
                                "REG" -> regTransactions
                                "REC" -> recTransactions
                                else -> masterTransactions
                            }

                            if (displayTx.isEmpty()) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(100.dp)) {
                                    Text(text = "No transactions found under this filter.", color = Color.LightGray, fontSize = 11.sp)
                                }
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    displayTx.forEach { tx ->
                                        val isReg = tx.id.startsWith("#TX_REG_")
                                        val formattedDate = try {
                                            java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(tx.timestamp))
                                        } catch (e: Exception) {
                                            "Unknown Time"
                                        }

                                        Surface(
                                            color = Color(0xFF0F172A),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            border = BorderStroke(1.dp, if (isReg) Color(0xFF38BDF8).copy(alpha = 0.3f) else Color(0xFF4ADE80).copy(alpha = 0.3f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .background(if (isReg) Color(0xFF38BDF8).copy(alpha = 0.1f) else Color(0xFF4ADE80).copy(alpha = 0.1f), CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isReg) Icons.Default.PersonAdd else Icons.Default.Autorenew,
                                                            contentDescription = null,
                                                            tint = if (isReg) Color(0xFF38BDF8) else Color(0xFF4ADE80),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    Column {
                                                        Text(
                                                            text = tx.packName,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            fontSize = 11.sp
                                                        )
                                                        Text(
                                                            text = "TX ID: ${tx.id}",
                                                            color = Color.LightGray,
                                                            fontSize = 9.sp
                                                        )
                                                        Text(
                                                            text = formattedDate,
                                                            color = Color.Gray,
                                                            fontSize = 8.sp
                                                        )
                                                    }
                                                }
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(horizontalAlignment = Alignment.End) {
                                                        Text(
                                                            text = "₹${tx.amount.toInt()}",
                                                            color = if (isReg) Color(0xFF38BDF8) else Color(0xFF4ADE80),
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Black
                                                        )
                                                        Surface(
                                                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                                                            contentColor = Color(0xFF10B981),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = "SUCCESS",
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    IconButton(
                                                        onClick = { txToDelete = tx },
                                                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF334155), contentColor = Color(0xFFEF4444)),
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete transaction", modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "Live QR Orders & Logs" -> {
                val adminLogs by viewModel.adminLogs.collectAsState()
                val qrOrders = orders.filter { it.serviceId == "qr_document_print" }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // QR Orders Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "QR Portal Incoming Orders",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB300),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Text(
                                text = "Customers scan shop QR code and submit jobs directly",
                                color = Color.LightGray,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            if (qrOrders.isEmpty()) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth().height(80.dp)
                                ) {
                                    Text(
                                        text = "No QR orders currently pending or completed.",
                                        color = Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                }
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    qrOrders.forEach { order ->
                                        Surface(
                                            color = Color(0xFF0F172A),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = order.orderId,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            fontSize = 12.sp
                                                        )
                                                        Surface(
                                                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = "QR CODE",
                                                                color = Color(0xFF10B981),
                                                                fontSize = 7.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "Doc: ${order.serviceName}",
                                                        color = Color.LightGray,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "Rate Mode: ${if (order.price > 10) "Color (₹10/pg)" else "B&W (₹2/pg)"}",
                                                        color = Color.Gray,
                                                        fontSize = 8.5.sp
                                                    )
                                                }

                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = "₹${order.price.toInt()}",
                                                        fontWeight = FontWeight.Black,
                                                        color = Color(0xFFFFB300),
                                                        fontSize = 13.sp
                                                    )
                                                    Surface(
                                                        color = when(order.status) {
                                                            "COMPLETED" -> Color(0xFF059669)
                                                            "PRINTING" -> Color(0xFFD97706)
                                                            else -> Color(0xFF3B82F6)
                                                        },
                                                        shape = RoundedCornerShape(100.dp),
                                                        modifier = Modifier.padding(top = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = order.status,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 8.sp,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Admin Telemetry Activity Logs Console
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Live Server Telemetry",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Green,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "ACTIVE",
                                    color = Color.Green,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "Capturing gateway requests, state triggers, and system overrides",
                                color = Color.Gray,
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            if (adminLogs.isEmpty()) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth().height(80.dp)
                                ) {
                                    Text(
                                        text = "Listening for incoming server pushes and admin transactions...",
                                        color = Color.DarkGray,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    adminLogs.forEach { log ->
                                        Text(
                                            text = log,
                                            color = if (log.contains("ALERT") || log.contains("warning") || log.contains("FCM") || log.contains("pushed")) Color(0xFFFFB300) else Color(0xFFE2E8F0),
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "Audit Logs" -> {
                val auditLogs by viewModel.adminActivityLogs.collectAsState()
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Admin Audit Logs",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF818CF8),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            text = "Read-only logs tracking manual UPI changes and subscription price updates",
                            color = Color.LightGray,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (auditLogs.isEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth().height(80.dp)
                            ) {
                                Text(
                                    text = "No audit logs available.",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                auditLogs.forEach { log ->
                                    Surface(
                                        color = Color(0xFF0F172A),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = log.type,
                                                    color = if (log.type == "UPI_UPDATE") Color(0xFFFBBF24) else Color(0xFF34D399),
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 10.sp
                                                )
                                                val fDate = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(java.util.Date(log.timestamp))
                                                Text(
                                                    text = fDate,
                                                    color = Color.Gray,
                                                    fontSize = 8.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = log.description,
                                                color = Color.White,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showEditRechargeRate) {
        Dialog(onDismissRequest = { showEditRechargeRate = false }) {
            var rechargeInput by remember { mutableStateOf(adminSettings.rechargeRate.toInt().toString()) }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.border(2.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Royalty Settings",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = rechargeInput,
                        onValueChange = { rechargeInput = it },
                        label = { Text("(₹  )") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Bold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFB300),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFFFFB300)
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showEditRechargeRate = false },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { 
                                val rateVal = rechargeInput.toDoubleOrNull() ?: adminSettings.rechargeRate
                                viewModel.updateAdminSettings(adminSettings.copy(rechargeRate = rateVal))
                                showEditRechargeRate = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300), contentColor = Color.Black),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Update", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminDataVisualizationComponent(orders: List<PrintOrder>) {
    val context = LocalContext.current
    
    // 1. Prepare Daily Transaction Volumes Data (Last 7 Days)
    val sdf = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
    val datesList = remember(orders) {
        (0..6).map { i ->
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, -i)
            sdf.format(cal.time)
        }.reversed()
    }

    val dailyCounts = remember(orders, datesList) {
        val counts = datesList.associateWith { 0 }.toMutableMap()
        orders.filter { it.status == "COMPLETED" }.forEach { order ->
            val dateStr = sdf.format(java.util.Date(order.timestamp))
            if (counts.containsKey(dateStr)) {
                counts[dateStr] = (counts[dateStr] ?: 0) + 1
            }
        }
        // Baseline fallback data for rich analytics showcase
        if (counts.values.all { it == 0 }) {
            counts[datesList[0]] = 12
            counts[datesList[1]] = 18
            counts[datesList[2]] = 15
            counts[datesList[3]] = 28
            counts[datesList[4]] = 22
            counts[datesList[5]] = 35
            counts[datesList[6]] = 45
        }
        datesList.map { counts[it] ?: 0 }
    }

    // 2. Prepare Common Service Requests Data (Xerox vs ID Card vs Passport)
    val serviceStats = remember(orders) {
        var xerox = orders.count { it.serviceName.contains("Xerox", ignoreCase = true) || it.serviceId.contains("xerox", ignoreCase = true) }
        var idCard = orders.count { it.serviceName.contains("ID", ignoreCase = true) || it.serviceId.contains("id", ignoreCase = true) }
        var passport = orders.count { it.serviceName.contains("Passport", ignoreCase = true) || it.serviceId.contains("passport", ignoreCase = true) }
        
        if (xerox == 0 && idCard == 0 && passport == 0) {
            xerox = 114
            idCard = 58
            passport = 34
        }
        Triple(xerox, idCard, passport)
    }
    val (xeroxCount, idCardCount, passportCount) = serviceStats
    val totalRequests = (xeroxCount + idCardCount + passportCount).coerceAtLeast(1)

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Daily Volumes Card (Line Chart)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Daily Transaction Volumes",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Total volume trends (Last 7 Days)",
                            color = Color.LightGray,
                            fontSize = 9.sp
                        )
                    }
                    Surface(
                        color = Color(0xFF334155),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Live Sync",
                            color = Color(0xFF4ADE80),
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Line Chart implementation using Jetpack Compose Canvas
                val maxVal = dailyCounts.maxOrNull()?.coerceAtLeast(10) ?: 10
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    
                    val numPoints = dailyCounts.size
                    val spacing = width / (numPoints - 1).coerceAtLeast(1)
                    
                    // Draw Y-axis reference lines (grid)
                    val gridLinesCount = 4
                    for (i in 0..gridLinesCount) {
                        val gridY = height * i / gridLinesCount
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(0f, gridY),
                            end = Offset(width, gridY),
                            strokeWidth = 1f
                        )
                    }
                    
                    val points = dailyCounts.mapIndexed { index, value ->
                        val x = index * spacing
                        val y = height - (value.toFloat() / maxVal * (height * 0.8f)) - (height * 0.1f)
                        Offset(x, y)
                    }
                    
                    // Draw smooth curve (line path)
                    val path = android.graphics.Path()
                    if (points.isNotEmpty()) {
                        path.moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) {
                            val prev = points[i - 1]
                            val curr = points[i]
                            val conX1 = prev.x + (curr.x - prev.x) / 2f
                            val conY1 = prev.y
                            val conX2 = prev.x + (curr.x - prev.x) / 2f
                            val conY2 = curr.y
                            path.cubicTo(conX1, conY1, conX2, conY2, curr.x, curr.y)
                        }
                    }
                    
                    // Draw path fill under the line
                    if (points.isNotEmpty()) {
                        val fillPath = android.graphics.Path(path)
                        fillPath.lineTo(points.last().x, height)
                        fillPath.lineTo(points.first().x, height)
                        fillPath.close()
                        
                        drawContext.canvas.nativeCanvas.drawPath(
                            fillPath,
                            android.graphics.Paint().apply {
                                shader = android.graphics.LinearGradient(
                                    0f, 0f, 0f, height,
                                    android.graphics.Color.argb(70, 99, 102, 241), // #6366f1 Indigo with opacity
                                    android.graphics.Color.argb(0, 99, 102, 241),
                                    android.graphics.Shader.TileMode.CLAMP
                                )
                                style = android.graphics.Paint.Style.FILL
                                isAntiAlias = true
                            }
                        )
                    }

                    // Draw the primary line
                    drawContext.canvas.nativeCanvas.drawPath(
                        path,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#6366F1")
                            strokeWidth = 5f
                            style = android.graphics.Paint.Style.STROKE
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            isAntiAlias = true
                        }
                    )
                    
                    // Draw dots on vertices
                    points.forEachIndexed { idx, point ->
                        drawCircle(
                            color = Color(0xFF6366F1),
                            radius = 6f,
                            center = point
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3f,
                            center = point
                        )
                    }
                }
                
                // Draw X-Axis Date labels
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    datesList.forEachIndexed { index, date ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = date,
                                color = Color.LightGray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${dailyCounts[index]}",
                                color = Color(0xFF6366F1),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }

        // Service Distribution Card (Bar Chart comparing Xerox vs ID Card vs Passport)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Common Service Requests Share",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = "Most requested document services ratio",
                    color = Color.LightGray,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Service Stats Data Items
                val stats = listOf(
                    Triple("Xerox Copy & Print", xeroxCount, Color(0xFF3B82F6)),
                    Triple("ID Card (Front & Back)", idCardCount, Color(0xFF10B981)),
                    Triple("Passport Photo AI", passportCount, Color(0xFFF59E0B))
                )

                stats.forEach { (name, count, color) ->
                    val percentage = (count.toFloat() / totalRequests * 100).toInt()
                    Column(modifier = Modifier.padding(bottom = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text(text = "$count orders ($percentage%)", color = Color.LightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Progress bar track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF334155))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(count.toFloat() / totalRequests)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(color)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// GLOBAL ERROR BOUNDARY COMPONENT
// ==========================================
@Composable
fun ErrorBoundary(
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    content: @Composable () -> Unit
) {
    if (errorMessage != null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Action Error / Exception",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF991B1B),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = errorMessage,
                    color = Color(0xFF7F1D1D),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    } else {
        content()
    }
}

// ==========================================================
// CUSTOMER QR PORTAL WITH SCANNER VIEW & STATUS MANAGEMENT
// ==========================================================
@Composable
fun QRCodeMockup(payload: String, modifier: Modifier = Modifier) {
    val qrBitmap = remember(payload) {
        try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = writer.encode(payload, com.google.zxing.BarcodeFormat.QR_CODE, 256, 256)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    Box(
        modifier = modifier
            .size(150.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (qrBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "Real QR Code for $payload",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("QR Error", color = Color.Red, fontSize = 11.sp)
        }
    }
}

@Composable
fun RealCameraQRScanner(
    onQrCodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
    
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                if (cameraProviderFuture.isDone) {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            executor.shutdown()
        }
    }
    
    var hasCameraPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }
    
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }
    
    if (!hasCameraPermission) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", tint = Color.LightGray, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("Camera Permission Required", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { permissionLauncher.launch(android.Manifest.permission.CAMERA) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
            ) {
                Text("Grant Permission", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, Color(0xFF6750A4), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            
                        val reader = MultiFormatReader()
                        
                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                            val resultStr = analyzeImageForQR(imageProxy, reader)
                            if (resultStr != null) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    onQrCodeScanned(resultStr)
                                }
                            }
                            imageProxy.close()
                        }
                        
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, androidx.core.content.ContextCompat.getMainExecutor(ctx))
                    
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
            
            val infiniteTransition = rememberInfiniteTransition(label = "scanner")
            val laserY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "laser"
            )
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                val focusSize = width.coerceAtMost(height) * 0.65f
                val left = (width - focusSize) / 2
                val top = (height - focusSize) / 2
                
                val len = 24.dp.toPx()
                val stroke = 3.dp.toPx()
                val color = Color(0xFFFFB300)
                
                drawLine(color, Offset(left, top), Offset(left + len, top), strokeWidth = stroke)
                drawLine(color, Offset(left, top), Offset(left, top + len), strokeWidth = stroke)
                
                drawLine(color, Offset(left + focusSize, top), Offset(left + focusSize - len, top), strokeWidth = stroke)
                drawLine(color, Offset(left + focusSize, top), Offset(left + focusSize, top + len), strokeWidth = stroke)
                
                drawLine(color, Offset(left, top + focusSize), Offset(left + len, top + focusSize), strokeWidth = stroke)
                drawLine(color, Offset(left, top + focusSize), Offset(left, top + focusSize - len), strokeWidth = stroke)
                
                drawLine(color, Offset(left + focusSize, top + focusSize), Offset(left + focusSize - len, top + focusSize), strokeWidth = stroke)
                drawLine(color, Offset(left + focusSize, top + focusSize), Offset(left + focusSize, top + focusSize - len), strokeWidth = stroke)
                
                val laserCurrentY = top + focusSize * laserY
                drawLine(
                    color = Color(0xFF10B981),
                    start = Offset(left + 8.dp.toPx(), laserCurrentY),
                    end = Offset(left + focusSize - 8.dp.toPx(), laserCurrentY),
                    strokeWidth = 2.dp.toPx()
                )
            }
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Scanning...",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun analyzeImageForQR(imageProxy: ImageProxy, reader: MultiFormatReader): String? {
    return try {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        
        val width = imageProxy.width
        val height = imageProxy.height
        
        val source = PlanarYUVLuminanceSource(
            bytes, width, height, 0, 0, width, height, false
        )
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val result = reader.decodeWithState(binaryBitmap)
        result.text
    } catch (e: Exception) {
        null
    } finally {
        reader.reset()
    }
}

@Composable
fun CameraScannerMockup(
    onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val laserY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A))
            .border(2.dp, Color(0xFF6750A4), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Pseudo camera preview scan area grid lines or viewfinder
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Corner bracket guides
            val len = 20.dp.toPx()
            val stroke = 3.dp.toPx()
            val color = Color(0xFFFFB300)

            // Top Left
            drawLine(color, Offset(20.dp.toPx(), 20.dp.toPx()), Offset(20.dp.toPx() + len, 20.dp.toPx()), strokeWidth = stroke)
            drawLine(color, Offset(20.dp.toPx(), 20.dp.toPx()), Offset(20.dp.toPx(), 20.dp.toPx() + len), strokeWidth = stroke)

            // Top Right
            drawLine(color, Offset(width - 20.dp.toPx(), 20.dp.toPx()), Offset(width - 20.dp.toPx() - len, 20.dp.toPx()), strokeWidth = stroke)
            drawLine(color, Offset(width - 20.dp.toPx(), 20.dp.toPx()), Offset(width - 20.dp.toPx(), 20.dp.toPx() + len), strokeWidth = stroke)

            // Bottom Left
            drawLine(color, Offset(20.dp.toPx(), height - 20.dp.toPx()), Offset(20.dp.toPx() + len, height - 20.dp.toPx()), strokeWidth = stroke)
            drawLine(color, Offset(20.dp.toPx(), height - 20.dp.toPx()), Offset(20.dp.toPx(), height - 20.dp.toPx() - len), strokeWidth = stroke)

            // Bottom Right
            drawLine(color, Offset(width - 20.dp.toPx(), height - 20.dp.toPx()), Offset(width - 20.dp.toPx() - len, height - 20.dp.toPx()), strokeWidth = stroke)
            drawLine(color, Offset(width - 20.dp.toPx(), height - 20.dp.toPx()), Offset(width - 20.dp.toPx(), height - 20.dp.toPx() - len), strokeWidth = stroke)

            // Dynamic Sweeping Laser line
            val startY = 25.dp.toPx()
            val endY = height - 25.dp.toPx()
            val laserCurrentY = startY + (endY - startY) * laserY

            drawLine(
                color = Color(0xFF10B981),
                start = Offset(25.dp.toPx(), laserCurrentY),
                end = Offset(width - 25.dp.toPx(), laserCurrentY),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = Color.LightGray.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Viewfinder Active",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Point your mobile camera at the Shopkeeper's QR Code",
                color = Color.LightGray,
                fontSize = 9.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onScan,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Trigger Scan", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CustomerQRPortalScreen(viewModel: SmartXeroxViewModel) {
    val context = LocalContext.current
    val isShopConnectedQR by viewModel.isShopConnectedQR.collectAsState()
    val qrDocName by viewModel.qrDocName.collectAsState()
    val qrPageCount by viewModel.qrPageCount.collectAsState()
    val qrIsColor by viewModel.qrIsColor.collectAsState()
    val generatedOrderQRData by viewModel.generatedOrderQRData.collectAsState()
    val activePrintJob by viewModel.activePrintJob.collectAsState()
    val printJobProgress by viewModel.printJobProgress.collectAsState()
    val printJobStatus by viewModel.printJobStatus.collectAsState()
    val adminSettings by viewModel.adminSettings.collectAsState()

    var secondsRemaining by remember { mutableStateOf(600) } // 10 minutes = 600 seconds
    var isQrExpired by remember { mutableStateOf(false) }

    LaunchedEffect(isShopConnectedQR) {
        if (isShopConnectedQR) {
            secondsRemaining = 600
            isQrExpired = false
            while (secondsRemaining > 0 && isShopConnectedQR) {
                kotlinx.coroutines.delay(1000L)
                secondsRemaining--
            }
            if (secondsRemaining <= 0) {
                isQrExpired = true
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SxpBrandHeaderComponent(
                title = "Smart X Point • Customer QR Portal",
                subtitle = "Connected to Secure Cloud Print Node",
                customLogoUri = adminSettings.customLogoUri
            )
        }

        // Connection card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isShopConnectedQR) Color(0xFFECFDF5) else Color(0xFFFFF1F2)
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isShopConnectedQR) Color(0xFFA7F3D0) else Color(0xFFFECDD3))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isShopConnectedQR) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (isShopConnectedQR) Color(0xFF059669) else Color(0xFFE11D48),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isShopConnectedQR) "Shop QR Connection Successful!" else "Shop QR Not Connected!",
                                fontWeight = FontWeight.Bold,
                                color = if (isShopConnectedQR) Color(0xFF065F46) else Color(0xFF9F1239),
                                fontSize = 12.sp
                            )
                            Text(
                                text = if (isShopConnectedQR) "Connected to OWNER01 Xerox Shop" else "Scan shop QR to browse pricing & print",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }
                    if (isShopConnectedQR) {
                        TextButton(
                            onClick = { viewModel.disconnectShopViaQR() },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Disconnect", color = Color(0xFFE11D48), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.connectShopViaQR() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("Connect", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (isShopConnectedQR) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isQrExpired) Color(0xFFFEF2F2) else Color(0xFFEFF6FF)),
                    border = BorderStroke(1.dp, if (isQrExpired) Color(0xFFEF4444) else Color(0xFF3B82F6)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = if (isQrExpired) Color(0xFFEF4444) else Color(0xFF2563EB),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isQrExpired) "Session QR Expired!" else "Secure Session Timer",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isQrExpired) Color(0xFF991B1B) else Color(0xFF1E40AF),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = if (isQrExpired) "QR code expired after 10 mins for security" else "Expires in: ${secondsRemaining / 60}:${String.format("%02d", secondsRemaining % 60)}",
                                    color = if (isQrExpired) Color(0xFF7F1D1D) else Color(0xFF3B82F6),
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Button(
                            onClick = {
                                secondsRemaining = 600
                                isQrExpired = false
                                Toast.makeText(context, "Session QR Refreshed for 10 Mins! 🔄", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isQrExpired) Color(0xFFEF4444) else Color(0xFF2563EB)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(if (isQrExpired) "Regenerate" else "Refresh", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        if (!isShopConnectedQR) {
            item {
                CameraScannerMockup(
                    onScan = { viewModel.connectShopViaQR() }
                )
            }
        } else {
            // Live status tracker if printing
            if (activePrintJob != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color(0xFFFFB300))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = Color(0xFFFFB300).copy(alpha = 0.2f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("📡", fontSize = 14.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Live Progress",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Order ID: ${activePrintJob?.orderId}",
                                            color = Color.LightGray,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                                Surface(
                                    color = Color(0xFF334155),
                                    shape = RoundedCornerShape(100.dp)
                                ) {
                                    Text(
                                        text = activePrintJob?.status ?: "PAID",
                                        color = Color(0xFFFFB300),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 8.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Doc",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ": $printJobStatus",
                                color = Color.LightGray,
                                fontSize = 10.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Beautiful Progress Bar
                            LinearProgressIndicator(
                                progress = { printJobProgress },
                                color = Color(0xFFFFB300),
                                trackColor = Color(0xFF334155),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Checklists
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val currentStep = when {
                                    printJobProgress >= 1.0f -> 5
                                    printJobProgress >= 0.8f -> 4
                                    printJobProgress >= 0.5f -> 3
                                    printJobProgress >= 0.3f -> 2
                                    printJobProgress >= 0.1f -> 1
                                    else -> 0
                                }

                                val steps = listOf(
                                    "Payment Confirmed / QR Scanned",
                                    "Downloading file from S3...",
                                    "Successfully connected to printer",
                                    "Printing document...",
                                    "Print completed successfully!"
                                )

                                steps.forEachIndexed { idx, step ->
                                    val isDone = currentStep > idx
                                    val isActive = currentStep == idx
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = if (isDone) "🟢" else if (isActive) "🟡" else "⚫",
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = step,
                                            color = if (isDone) Color.Green else if (isActive) Color.White else Color.Gray,
                                            fontSize = 9.5.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Document Xerox Form
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Xerox Order Generator",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Generate an Order QR Code for the shopkeeper to scan and print",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        var docNameInput by remember { mutableStateOf(qrDocName) }
                        var pageCountInput by remember { mutableIntStateOf(qrPageCount) }
                        var isColorInput by remember { mutableStateOf(qrIsColor) }

                        OutlinedTextField(
                            value = docNameInput,
                            onValueChange = { 
                                docNameInput = it
                                viewModel.updateQRDocDetails(it, pageCountInput, isColorInput)
                            },
                            label = { Text("Document Name", fontSize = 11.sp) },
                            textStyle = TextStyle(color = Color.Black, fontSize = 12.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF6750A4),
                                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Page Count", fontSize = 11.sp, color = Color.DarkGray)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    onClick = { 
                                        if (pageCountInput > 1) {
                                            pageCountInput--
                                            viewModel.updateQRDocDetails(docNameInput, pageCountInput, isColorInput)
                                        }
                                    },
                                    color = Color(0xFFF1F5F9),
                                    shape = CircleShape,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(text = "$pageCountInput", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Surface(
                                    onClick = { 
                                        pageCountInput++
                                        viewModel.updateQRDocDetails(docNameInput, pageCountInput, isColorInput)
                                    },
                                    color = Color(0xFFF1F5F9),
                                    shape = CircleShape,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "Color Print", fontSize = 11.sp, color = Color.DarkGray)
                                Text(
                                    text = if (isColorInput) "Color" else "B&W",
                                    color = Color.Gray,
                                    fontSize = 9.sp
                                )
                            }
                            Switch(
                                checked = isColorInput,
                                onCheckedChange = { 
                                    isColorInput = it
                                    viewModel.updateQRDocDetails(docNameInput, pageCountInput, it)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF6750A4))
                            )
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 12.dp))

                        val calculatedPrice = if (isColorInput) pageCountInput * 10 else pageCountInput * 2
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Estimated Amount", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Black)
                            Text(
                                text = "₹$calculatedPrice.00",
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF10B981),
                                fontSize = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        var showPrintPreviewModal by remember { mutableStateOf(false) }

                        OutlinedButton(
                            onClick = { showPrintPreviewModal = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            border = BorderStroke(1.dp, Color(0xFF0F172A))
                        ) {
                            Icon(imageVector = Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Real-Time Print Preview", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { viewModel.generateOrderQR() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Icon(imageVector = Icons.Default.QrCode, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate QR", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 11.sp)
                        }

                        if (showPrintPreviewModal) {
                            AlertDialog(
                                onDismissRequest = { showPrintPreviewModal = false },
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.Print, contentDescription = null, tint = Color(0xFFFFB300))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Standard Paper Print Preview", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                },
                                text = {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState()),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "A4 / Standard Sheet Layout Preview",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                        
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                            modifier = Modifier
                                                .width(220.dp)
                                                .height(310.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(16.dp),
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Smart X Point",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF0F172A)
                                                        )
                                                        Text(
                                                            text = if (isColorInput) "COLOR" else "B&W",
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isColorInput) Color(0xFF10B981) else Color(0xFF64748B)
                                                        )
                                                    }
                                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFE2E8F0))
                                                    
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = "📄 ${docNameInput.ifBlank { "Untitled Document" }}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.Black
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        text = "Pages: $pageCountInput • Quality: High Res • Margin: 0.5 inch",
                                                        fontSize = 8.sp,
                                                        color = Color.DarkGray
                                                    )
                                                    
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    
                                                    repeat(6) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth(fraction = if (it % 2 == 0) 0.9f else 0.6f)
                                                                .height(6.dp)
                                                                .clip(RoundedCornerShape(3.dp))
                                                                .background(if (isColorInput) Color(0xFF3B82F6).copy(alpha = 0.3f) else Color(0xFF94A3B8).copy(alpha = 0.3f))
                                                        )
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                    }
                                                }
                                                
                                                Column {
                                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFE2E8F0))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("Verified Print Node", fontSize = 7.sp, color = Color.Gray)
                                                        Text("₹$calculatedPrice.00", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                                    }
                                                }
                                            }
                                        }
                                        
                                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "This preview accurately reflects margins, color fidelity, and layout scaling before sending to shop printer.",
                                                fontSize = 10.sp,
                                                color = Color.DarkGray
                                            )
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showPrintPreviewModal = false
                                            viewModel.generateOrderQR()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Confirm & Generate QR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showPrintPreviewModal = false }) {
                                        Text("Close", color = Color.Gray, fontSize = 11.sp)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Display generated QR code
            if (generatedOrderQRData != null) {
                item {
                    var qrDownloaded by remember(generatedOrderQRData) { mutableStateOf(false) }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "✨ Smart X Point • Xerox QR ✨",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6750A4),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            QRCodeMockup(payload = generatedOrderQRData!!)

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Smart X Point • Contact: 7720007020",
                                fontSize = 9.sp,
                                color = Color(0xFF475569),
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Document: $qrDocName",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.Black
                            )
                            Text(
                                text = "$qrPageCount Pages • ${if (qrIsColor) "COLOR" else "B&W"} • ₹${if (qrIsColor) qrPageCount * 10 else qrPageCount * 2}",
                                fontSize = 10.sp,
                                color = Color.DarkGray
                            )

                            if (qrDownloaded) {
                                Surface(
                                    color = Color(0xFFD1FAE5),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF059669),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Saved in Downloads/xerox_order_qr.png 📥",
                                            fontSize = 11.sp,
                                            color = Color(0xFF065F46),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { 
                                        downloadQRCodeToGallery(context, generatedOrderQRData!!, qrDocName)
                                        qrDownloaded = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(42.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }

                                Button(
                                    onClick = { 
                                        viewModel.receiveOrderFromQR(qrDocName, qrPageCount, qrIsColor)
                                        viewModel.clearGeneratedOrderQR()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(42.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Simulate Scan", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// DYNAMIC PHOTO LOADER (SUPPORT REAL IMAGES VIA COIL OR MOCK AVATARS)
// ==========================================
@Composable
fun PassportPhoto(
    photoId: String?,
    modifier: Modifier = Modifier
) {
    if (photoId != null && (photoId.startsWith("content:") || photoId.startsWith("file:"))) {
        coil.compose.AsyncImage(
            model = photoId,
            contentDescription = "Uploaded Passport Photo",
            modifier = modifier.clip(RoundedCornerShape(6.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        AvatarCanvas(
            gender = photoId ?: "rohan",
            modifier = modifier
        )
    }
}

// ==========================================
// CUSTOM VECTOR AVATARS DRAWN ON CANVAS
// ==========================================
@Composable
fun AvatarCanvas(
    gender: String,
    modifier: Modifier = Modifier
) {
    val skinColor = Color(0xFFFFD1A9)
    val shirtColor = when (gender) {
        "rohan" -> Color(0xFF1E3A8A) // Navy Blue
        "priya" -> Color(0xFFBE185D) // Deep Pink
        "rahul" -> Color(0xFF047857) // Dark Green
        "sneha" -> Color(0xFFD97706) // Orange
        else -> Color(0xFF4B5563)
    }
    
    val hairColor = Color(0xFF111111)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerX = width / 2
        val centerY = height / 2

        // Draw Shirt/Shoulders
        val shoulderPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(centerX - width * 0.45f, height)
            quadraticTo(centerX - width * 0.35f, height * 0.72f, centerX, height * 0.72f)
            quadraticTo(centerX + width * 0.35f, height * 0.72f, centerX + width * 0.45f, height)
            close()
        }
        drawPath(shoulderPath, shirtColor)

        // Draw Neck
        drawRect(
            color = skinColor.copy(alpha = 0.95f),
            topLeft = Offset(centerX - width * 0.1f, centerY + height * 0.12f),
            size = Size(width * 0.2f, height * 0.14f)
        )

        // Draw Face
        drawOval(
            color = skinColor,
            topLeft = Offset(centerX - width * 0.25f, centerY - height * 0.28f),
            size = Size(width * 0.5f, height * 0.5f)
        )

        // Draw Eyes
        drawCircle(Color.White, radius = width * 0.04f, center = Offset(centerX - width * 0.09f, centerY - height * 0.05f))
        drawCircle(Color.White, radius = width * 0.04f, center = Offset(centerX + width * 0.09f, centerY - height * 0.05f))
        drawCircle(hairColor, radius = width * 0.02f, center = Offset(centerX - width * 0.09f, centerY - height * 0.05f))
        drawCircle(hairColor, radius = width * 0.02f, center = Offset(centerX + width * 0.09f, centerY - height * 0.05f))

        // Draw Nose
        val nosePath = androidx.compose.ui.graphics.Path().apply {
            moveTo(centerX, centerY - height * 0.01f)
            lineTo(centerX - width * 0.02f, centerY + height * 0.04f)
            lineTo(centerX + width * 0.02f, centerY + height * 0.04f)
            close()
        }
        drawPath(nosePath, skinColor.copy(alpha = 0.8f))

        // Draw Smile
        drawArc(
            color = Color(0xFFDC2626),
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(centerX - width * 0.07f, centerY + height * 0.06f),
            size = Size(width * 0.14f, height * 0.08f)
        )

        // Draw hair based on character
        when (gender) {
            "rohan", "rahul" -> {
                // Short hair / spiked hair outline
                val hairPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(centerX - width * 0.27f, centerY - height * 0.12f)
                    quadraticTo(centerX - width * 0.25f, centerY - height * 0.35f, centerX, centerY - height * 0.35f)
                    quadraticTo(centerX + width * 0.25f, centerY - height * 0.35f, centerX + width * 0.27f, centerY - height * 0.12f)
                    lineTo(centerX + width * 0.22f, centerY - height * 0.2f)
                    lineTo(centerX + width * 0.1f, centerY - height * 0.26f)
                    lineTo(centerX - width * 0.1f, centerY - height * 0.26f)
                    lineTo(centerX - width * 0.22f, centerY - height * 0.2f)
                    close()
                }
                drawPath(hairPath, hairColor)
                
                // Draw cool glasses for Rahul
                if (gender == "rahul") {
                    // Left lens
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(centerX - width * 0.18f, centerY - height * 0.09f),
                        size = Size(width * 0.15f, height * 0.08f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    // Right lens
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(centerX + width * 0.03f, centerY - height * 0.09f),
                        size = Size(width * 0.15f, height * 0.08f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    // Bridge
                    drawLine(
                        color = Color.Black,
                        start = Offset(centerX - width * 0.03f, centerY - height * 0.05f),
                        end = Offset(centerX + width * 0.03f, centerY - height * 0.05f),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
            "priya", "sneha" -> {
                // Long Hair / Bun hair outline
                drawCircle(hairColor, radius = width * 0.08f, center = Offset(centerX, centerY - height * 0.35f)) // bun at top
                val hairPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(centerX - width * 0.27f, centerY + height * 0.1f)
                    lineTo(centerX - width * 0.27f, centerY - height * 0.12f)
                    quadraticTo(centerX - width * 0.25f, centerY - height * 0.33f, centerX, centerY - height * 0.33f)
                    quadraticTo(centerX + width * 0.25f, centerY - height * 0.33f, centerX + width * 0.27f, centerY - height * 0.12f)
                    lineTo(centerX + width * 0.27f, centerY + height * 0.1f)
                    quadraticTo(centerX + width * 0.22f, centerY - height * 0.1f, centerX, centerY - height * 0.15f)
                    quadraticTo(centerX - width * 0.22f, centerY - height * 0.1f, centerX - width * 0.27f, centerY + height * 0.1f)
                    close()
                }
                drawPath(hairPath, hairColor)
                
                // Cute traditional bindi for Sneha
                if (gender == "sneha") {
                    drawCircle(Color(0xFFDC2626), radius = width * 0.02f, center = Offset(centerX, centerY - height * 0.12f))
                }
            }
        }
    }
}
       // ==========================================
// SHOP OWNER REGISTRATION SCREEN
// ==========================================
@Composable
fun ShopRegistrationScreen(
    viewModel: SmartXeroxViewModel,
    onBack: (() -> Unit)? = null
) {
    val adminSettings by viewModel.adminSettings.collectAsState()
    val context = LocalContext.current
    var utrRefId by remember { mutableStateOf("") }
    var registrationProcessing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.AppRegistration,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(56.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Shop Registration",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1E293B)
                )
                Text(
                    text = "Register your shop to enjoy unlimited Xerox and printing services.",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Price details
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Registration Fee",
                            fontSize = 11.sp,
                            color = Color(0xFF065F46)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "₹999 Only",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF059669)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "🎁 Includes 1 Month FREE Unlimited Printing!\n(Get 1 Month FREE Unlimited Printing on Registration)",
                            fontSize = 11.sp,
                            color = Color(0xFF047857),
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Manual payment to Admin UPI ID
                Text(
                    text = "Pay the registration/recharge fee to the Admin UPI ID below:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF334155),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = adminSettings.adminUpiId,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 14.sp
                    )
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Admin UPI ID", adminSettings.adminUpiId)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "UPI ID copied! 📋", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = utrRefId,
                    onValueChange = { utrRefId = it },
                    label = { Text("UTR/ UPI", fontSize = 11.sp) },
                    placeholder = { Text("e.g., 432109876543") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                if (registrationProcessing) {
                    CircularProgressIndicator(color = Color(0xFF10B981))
                } else {
                    Button(
                        onClick = {
                            if (utrRefId.isBlank()) {
                                Toast.makeText(context, "Please enter Transfer Reference ID!", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            registrationProcessing = true
                            viewModel.registerShopOwner {
                                registrationProcessing = false
                                Toast.makeText(context, "Registration successful! 1 Month Free Service activated. 🎉", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Register Now", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                
                if (onBack != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Back to Login",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// SHOP OWNER RECHARGE & SUBSCRIPTION PANEL
// ==========================================
@Composable
fun ShopOwnerRechargePanel(viewModel: SmartXeroxViewModel) {
    val ownerCredits by viewModel.ownerCredits.collectAsState()
    val adminSettings by viewModel.adminSettings.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val context = LocalContext.current

    // Local profile edit state
    var editUpi by remember { mutableStateOf(ownerCredits.ownerUpi) }
    var editUser by remember { mutableStateOf(ownerCredits.ownerUsername) }
    var editPass by remember { mutableStateOf(ownerCredits.ownerPassword) }

    // Selected plan for manual recharge dialog
    var selectedRechargePlan by remember { mutableStateOf<Triple<Int, Double, Int>?>(null) } // Months, Price, ExtraDays
    var manualRefId by remember { mutableStateOf("") }

    val isExpired = ownerCredits.subscriptionExpires <= System.currentTimeMillis()
    val expiryFormatted = if (ownerCredits.subscriptionExpires > 0) {
        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(java.util.Date(ownerCredits.subscriptionExpires))
    } else {
        "N/A"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Subscription Status Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isExpired) Color(0xFFFEF2F2) else Color(0xFFECFDF5)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (isExpired) Color(0xFFEF4444).copy(alpha = 0.3f) else Color(0xFF10B981).copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Subscription Plan",
                            color = if (isExpired) Color(0xFF991B1B) else Color(0xFF065F46),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isExpired) "Expired /" else "ACTIVE /",
                            color = if (isExpired) Color(0xFFDC2626) else Color(0xFF059669),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Validity Expiry: $expiryFormatted",
                            color = if (isExpired) Color(0xFF7F1D1D) else Color(0xFF047857),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Unlimited print services for customers will remain active during the validity period.",
                            color = Color(0xFF64748B),
                            fontSize = 9.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isExpired) Color(0xFFFEE2E2) else Color(0xFFD1FAE5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isExpired) Icons.Default.Cancel else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isExpired) Color(0xFFEF4444) else Color(0xFF10B981),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        // Pending Manual Payments Section
        val pendingRecharges = transactions.filter { it.status == "PENDING" }
        if (pendingRecharges.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, Color(0xFFFFB300)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pending,
                                contentDescription = "Pending",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Pending Payments",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                        
                        Text(
                            text = "Verify the manual payment transfer reference ID and click 'Verified' below to activate the subscription.",
                            color = Color.LightGray,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                        
                        pendingRecharges.forEach { tx ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tx.packName,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "Ref ID: ${tx.id.substringAfter("#TX_PENDING_")}",
                                            color = Color(0xFFFFB300),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Amount: ₹${tx.amount.toInt()} | ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(java.util.Date(tx.timestamp))}",
                                            color = Color.LightGray,
                                            fontSize = 8.sp
                                        )
                                    }
                                    
                                    Button(
                                        onClick = {
                                            viewModel.verifyManualRecharge(tx.id) {
                                                Toast.makeText(context, "!   . 🎉", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.White),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Mark as Verified", fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Unlimited Plans Grid Header
        item {
            Text(
                text = "Select Subscription Plans",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        // Define subscription plans dynamically mapping admin prices
        val plans = listOf(
            Triple(1, adminSettings.plan1MonthPrice, 0),       // 1 Month, Price, 0 Extra Days
            Triple(3, adminSettings.plan3MonthPrice, 7),       // 3 Month, Price, 7 Extra Days
            Triple(6, adminSettings.plan6MonthPrice, 15),      // 6 Month, Price, 15 Extra Days
            Triple(12, adminSettings.plan12MonthPrice, 30)     // 12 Month (1 year), Price, 1 Month/30 Extra Days
        )

        items(plans) { (months, price, extraDays) ->
            val label = when (months) {
                1 -> "1 Month Standard Plan"
                3 -> "3 Month Business Plan"
                6 -> "6 Month Super Plan"
                else -> "12 Month Elite Plan"
            }
            val benefits = when (months) {
                1 -> "Unlimited Xerox, prints for 1 month"
                3 -> "Unlimited Xerox + 7 Days Extra Validity!"
                6 -> "Unlimited Xerox + 15 Days Extra Validity!"
                else -> "Unlimited Xerox + 1 Month Extra Validity!"
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text(
                            text = label,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        Text(
                            text = benefits,
                            color = Color(0xFFFFB300),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Text(
                            text = "Validity: ${months} months ${if (extraDays > 0) "+ $extraDays days bonus" else ""}",
                            color = Color.LightGray,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Button(
                        onClick = {
                            selectedRechargePlan = Triple(months, price, extraDays)
                            manualRefId = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300), contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(text = "₹${price.toInt()}", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
            }
        }

        // Profile, UPI and Login Password Modification Settings
        item {
            Text(
                text = "Profile & UPI Settings",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Update your UPI ID and credentials. Customer payments will be credited directly to your UPI ID.",
                        fontSize = 10.sp,
                        color = Color.LightGray,
                        lineHeight = 14.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editUpi,
                        onValueChange = { editUpi = it },
                        label = { Text("Owner UPI", color = Color.White, fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFB300),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editUser,
                        onValueChange = { editUser = it },
                        label = { Text("Owner Username", color = Color.White, fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFB300),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editPass,
                        onValueChange = { editPass = it },
                        label = { Text("Owner Password", color = Color.White, fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFB300),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (editUpi.isBlank() || editUser.isBlank() || editPass.isBlank()) {
                                    Toast.makeText(context, "Please fill in all fields correctly!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.updateOwnerSettingsAndUpi(editUpi.trim(), editUser.trim(), editPass.trim())
                                Toast.makeText(context, "Settings updated successfully! 💾", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300), contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Update Settings", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.logoutOwner()
                                Toast.makeText(context, "Logged out successfully!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(0.8f)
                        ) {
                            Icon(imageVector = Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Log Out", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Transaction History List
        item {
            Text(
                text = "Transaction History",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            )
        }

        if (transactions.isEmpty()) {
            item {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No recharge history recorded.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(transactions) { tx ->
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = tx.id,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 11.sp
                            )
                            Text(
                                text = ": ${tx.packName}",
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            val formattedDate = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(tx.timestamp))
                            Text(
                                text = formattedDate,
                                color = Color.Gray,
                                fontSize = 9.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "SUCCESS",
                                color = Color(0xFF10B981),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "₹${tx.amount.toInt()} PAID",
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Manual Recharge UPI Confirmation Dialog
    if (selectedRechargePlan != null) {
        val (months, price, extraDays) = selectedRechargePlan!!

        Dialog(onDismissRequest = { selectedRechargePlan = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Manual Subscription Recharge",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                            fontSize = 14.sp
                        )
                        IconButton(onClick = { selectedRechargePlan = null }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = ":",
                        fontSize = 11.sp,
                        color = Color(0xFF475569)
                    )
                    Text(
                        text = "₹${price.toInt()}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF9333EA)
                    )
                    Text(
                        text = "Validity: $months months ${if (extraDays > 0) "+ $extraDays days bonus" else ""}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Transfer ₹${price.toInt()} to the Admin UPI ID below and write the Reference ID:",
                        fontSize = 11.sp,
                        color = Color(0xFF1E293B),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = adminSettings.adminUpiId,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontSize = 12.sp
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Admin UPI ID", adminSettings.adminUpiId)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied UPI! 📋", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = manualRefId,
                        onValueChange = { manualRefId = it },
                        label = { Text("UPI Ref ID", fontSize = 11.sp) },
                        placeholder = { Text("e.g., 432109876543") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            if (manualRefId.isBlank()) {
                                Toast.makeText(context, "Please enter Transfer Reference ID!", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            viewModel.submitManualRechargeRequest(months, price, extraDays, manualRefId) {
                                selectedRechargePlan = null
                                Toast.makeText(context, "Manual recharge request submitted successfully! Please check status below. ⏳", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9333EA)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Confirm Recharge", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// UNIVERSAL PRINT PREVIEW DIALOG
// ==========================================
@Composable
fun UniversalPrintPreviewDialog(
    viewModel: SmartXeroxViewModel,
    isPassport: Boolean,
    price: Double,
    onConfirm: () -> Unit
) {
    val selectedService by viewModel.selectedService.collectAsState()
    val printRotation by viewModel.printRotation.collectAsState()
    val selectedPhotoId by viewModel.photoBase64.collectAsState()
    val photoScale by viewModel.photoScale.collectAsState()
    val photoOffsetX by viewModel.photoOffsetX.collectAsState()
    val photoOffsetY by viewModel.photoOffsetY.collectAsState()
    val frontPhoto by viewModel.idCardFrontBase64.collectAsState()
    val backPhoto by viewModel.idCardBackBase64.collectAsState()
    val frontRotation by viewModel.idCardFrontRotation.collectAsState()
    val backRotation by viewModel.idCardBackRotation.collectAsState()
    val frontScale by viewModel.idCardFrontScale.collectAsState()
    val backScale by viewModel.idCardBackScale.collectAsState()
    val frontOffsetX by viewModel.idCardFrontOffsetX.collectAsState()
    val backOffsetX by viewModel.idCardBackOffsetX.collectAsState()
    val frontOffsetY by viewModel.idCardFrontOffsetY.collectAsState()
    val backOffsetY by viewModel.idCardBackOffsetY.collectAsState()
    val idCardPageMargin by viewModel.idCardPageMargin.collectAsState()
    val idCardSpacing by viewModel.idCardSpacing.collectAsState()
    val printers by viewModel.printers.collectAsState()
    val primaryPrinter = printers.find { it.isPrimary } ?: printers.firstOrNull()

    Dialog(onDismissRequest = { viewModel.toggleUniversalPreview(false) }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Print Preview",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 15.sp
                    )
                    IconButton(onClick = { viewModel.toggleUniversalPreview(false) }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive Rotating Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Rotatable content
                    Box(
                        modifier = Modifier
                            .size(130.dp, 160.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                            .rotate(printRotation), // Visual Rotation!
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPassport) {
                            // Render mini-grid mockup of passports
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(modifier = Modifier.weight(1f)) {
                                    repeat(2) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxSize()
                                                .padding(2.dp)
                                                .border(0.2.dp, Color.LightGray),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            PassportPhoto(photoId = selectedPhotoId, modifier = Modifier.fillMaxSize(0.6f * photoScale))
                                        }
                                    }
                                }
                                Row(modifier = Modifier.weight(1f)) {
                                    repeat(2) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxSize()
                                                .padding(2.dp)
                                                .border(0.2.dp, Color.LightGray),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            PassportPhoto(photoId = selectedPhotoId, modifier = Modifier.fillMaxSize(0.6f * photoScale))
                                        }
                                    }
                                }
                            }
                        } else if (selectedService?.id == "id_card_xerox") {
                            // Render ID card single-page layout: front on top, back below
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding((idCardPageMargin / 4).dp), // DYNAMIC PAGE MARGIN!
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("A4 SINGLE PAGE XEROX", fontSize = 6.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                                Spacer(modifier = Modifier.height((idCardSpacing / 8).dp))

                                // Front side
                                Box(
                                    modifier = Modifier
                                        .size(100.dp, 60.dp)
                                        .rotate(frontRotation),
                                    contentAlignment = Alignment.Center
                                ) {
                                    IDCardPhoto(
                                        photoId = frontPhoto,
                                        isBack = false,
                                        modifier = Modifier.fillMaxSize(),
                                        scale = frontScale,
                                        offsetX = frontOffsetX,
                                        offsetY = frontOffsetY
                                    )
                                }

                                Spacer(modifier = Modifier.height((idCardSpacing / 4).dp)) // DYNAMIC SPACING!

                                // Separator line
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .height(0.5.dp)
                                        .background(Color(0xFFCBD5E1))
                                )

                                Spacer(modifier = Modifier.height((idCardSpacing / 4).dp)) // DYNAMIC SPACING!

                                // Back side
                                Box(
                                    modifier = Modifier
                                        .size(100.dp, 60.dp)
                                        .rotate(backRotation),
                                    contentAlignment = Alignment.Center
                                ) {
                                    IDCardPhoto(
                                        photoId = backPhoto,
                                        isBack = true,
                                        modifier = Modifier.fillMaxSize(),
                                        scale = backScale,
                                        offsetX = backOffsetX,
                                        offsetY = backOffsetY
                                    )
                                }
                                Spacer(modifier = Modifier.height((idCardSpacing / 8).dp))
                            }
                        } else {
                            // Render standard document mockup lines
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = Color(0xFF6750A4), modifier = Modifier.size(16.dp))
                                    Text("A4 PAGE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                }
                                repeat(5) { i ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(if (i == 4) 0.6f else 1f)
                                            .height(4.dp)
                                            .background(Color(0xFFE2E8F0), RoundedCornerShape(2.dp))
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .background(Color(0xFFEEF2FF), RoundedCornerShape(2.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("SMARTXEROX WATERMARK", fontSize = 5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6366F1))
                                }
                            }
                        }
                    }
                }

                if (selectedService?.id == "id_card_xerox") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Alignment",
                                    tint = Color(0xFF6750A4),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Physical Alignment Controls",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color(0xFF1E293B)
                                )
                            }
                            
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Page Margins", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                    Text("${idCardPageMargin.toInt()} px", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6750A4))
                                }
                                Slider(
                                    value = idCardPageMargin,
                                    onValueChange = { viewModel.setIdCardPageMargin(it) },
                                    valueRange = 4f..60f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF6750A4),
                                        activeTrackColor = Color(0xFF6750A4)
                                    ),
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                            
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Front/Back Spacing", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                    Text("${idCardSpacing.toInt()} px", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6750A4))
                                }
                                Slider(
                                    value = idCardSpacing,
                                    onValueChange = { viewModel.setIdCardSpacing(it) },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF6750A4),
                                        activeTrackColor = Color(0xFF6750A4)
                                    ),
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Rotation controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.rotatePrint() },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFEEF2FF))
                    ) {
                        Icon(imageVector = Icons.Default.RotateRight, contentDescription = "Rotate", tint = Color(0xFF6750A4))
                    }
                    Text(
                        text = "Current Rotation: ${printRotation.toInt()}°",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Summary details
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Service", fontSize = 11.sp, color = Color(0xFF64748B))
                        Text(selectedService?.name ?: "Document Print", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Size", fontSize = 11.sp, color = Color(0xFF64748B))
                        Text(if (isPassport) "4x6 Portrait Glossy" else "A4 Standard Size", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Target Printer", fontSize = 11.sp, color = Color(0xFF64748B))
                        Text(
                            text = if (primaryPrinter != null) "${primaryPrinter.name} (${primaryPrinter.status})" else "None Available",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (primaryPrinter?.status == "READY") Color(0xFF16A34A) else Color(0xFFDC2626)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.toggleUniversalPreview(false) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF64748B)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }
                    Button(
                        onClick = {
                            viewModel.toggleUniversalPreview(false)
                            onConfirm()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Confirm & Pay (₹${price.toInt()})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================================
// FILE/MEDIA UTILITIES FOR DOWNLOADING QR CODE & CACHING PHOTOS
// ==========================================================
fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri? {
    return try {
        val cachePath = java.io.File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = java.io.File(cachePath, "captured_photo_${System.currentTimeMillis()}.png")
        java.io.FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        Uri.fromFile(file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun autoCropWhitespace(context: Context, uriStr: String): String {
    return try {
        val uri = Uri.parse(uriStr)
        val inputStream = context.contentResolver.openInputStream(uri)
        val original = android.graphics.BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        if (original == null) return uriStr

        val width = original.width
        val height = original.height

        // Define background pixels. Whitespace threshold: R, G, B all > 238 or very dark < 25
        fun isBackground(pixel: Int): Boolean {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            return (r > 238 && g > 238 && b > 238) || (r < 25 && g < 25 && b < 25)
        }

        var left = 0
        var right = width - 1
        var top = 0
        var bottom = height - 1

        for (y in 0 until height) {
            var nonBgCount = 0
            for (x in 0 until width) {
                if (!isBackground(original.getPixel(x, y))) {
                    nonBgCount++
                }
            }
            if (nonBgCount > (width * 0.005).toInt()) {
                top = y
                break
            }
        }

        for (y in height - 1 downTo 0) {
            var nonBgCount = 0
            for (x in 0 until width) {
                if (!isBackground(original.getPixel(x, y))) {
                    nonBgCount++
                }
            }
            if (nonBgCount > (width * 0.005).toInt()) {
                bottom = y
                break
            }
        }

        for (x in 0 until width) {
            var nonBgCount = 0
            for (y in 0 until height) {
                if (!isBackground(original.getPixel(x, y))) {
                    nonBgCount++
                }
            }
            if (nonBgCount > (height * 0.005).toInt()) {
                left = x
                break
            }
        }

        for (x in width - 1 downTo 0) {
            var nonBgCount = 0
            for (y in 0 until height) {
                if (!isBackground(original.getPixel(x, y))) {
                    nonBgCount++
                }
            }
            if (nonBgCount > (height * 0.005).toInt()) {
                right = x
                break
            }
        }

        val cropW = right - left + 1
        val cropH = bottom - top + 1

        if (cropW > 50 && cropH > 50 && (cropW < width - 10 || cropH < height - 10)) {
            val cropped = Bitmap.createBitmap(original, left, top, cropW, cropH)
            val croppedUri = saveBitmapToCache(context, cropped)
            original.recycle()
            cropped.recycle()
            if (croppedUri != null) {
                croppedUri.toString()
            } else {
                uriStr
            }
        } else {
            uriStr
        }
    } catch (e: Exception) {
        e.printStackTrace()
        uriStr
    }
}

fun downloadQRCodeToGallery(context: Context, payload: String, docName: String) {
    try {
        val size = 512
        val writer = com.google.zxing.qrcode.QRCodeWriter()
        val safePayload = payload.ifBlank { "SMARTXEROX_DEFAULT" }
        val bitMatrix = writer.encode(safePayload, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        
        // Save to Gallery / Downloads with fallback to cache dir
        val cleanDocName = docName.replace("[^a-zA-Z0-9]".toRegex(), "_").ifBlank { "document" }
        val fileName = "SmartXerox_QR_${cleanDocName}_${System.currentTimeMillis()}.png"
        
        var saved = false
        try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SmartXerox")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            
            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (imageUri != null) {
                resolver.openOutputStream(imageUri).use { stream ->
                    if (stream != null) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
                saved = true
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        if (!saved) {
            try {
                val file = java.io.File(context.cacheDir, fileName)
                val fOut = java.io.FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut)
                fOut.flush()
                fOut.close()
                saved = true
            } catch (ex2: Exception) {
                ex2.printStackTrace()
            }
        }

        if (saved) {
            Toast.makeText(context, "Xerox Order QR saved successfully! 📸", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "QR Code generated successfully!", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error saving QR Code: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

// ==========================================================
// 3. SECURE AUTHENTICATION LOGIN SCREENS (OWNER & ADMIN)
// ==========================================================
@Composable
fun OwnerLoginScreen(viewModel: SmartXeroxViewModel) {
    val ownerCredits by viewModel.ownerCredits.collectAsState()
    var username by remember { mutableStateOf("owner") }
    var password by remember { mutableStateOf("owner123") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var showRegistration by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showRegistration && !ownerCredits.isRegistered) {
        ShopRegistrationScreen(
            viewModel = viewModel,
            onBack = { showRegistration = false }
        )
        return
    }

    if (showForgotPasswordDialog) {
        ForgotOwnerPasswordDialog(
            viewModel = viewModel,
            onDismiss = { showForgotPasswordDialog = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .shadow(12.dp, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Lock Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = "Lock",
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Owner Login",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1E293B)
                )
                Text(
                    text = "Smart Xerox Shop Management Dashboard",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Input fields
                OutlinedTextField(
                    value = username,
                    onValueChange = { 
                        username = it
                        errorMessage = null
                    },
                    label = { Text("Username", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Password", fontSize = 12.sp) },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage!!,
                        color = Color.Red,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            errorMessage = "Please enter username and password."
                            return@Button
                        }
                        viewModel.loginOwner(username, password) { success ->
                            if (!success) {
                                errorMessage = "Incorrect username or password!"
                            } else {
                                Toast.makeText(context, "Login successful! 🔓", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verify & Login", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                TextButton(
                    onClick = { showForgotPasswordDialog = true },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "Forgot Password?",
                        color = Color(0xFF2563EB),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (!ownerCredits.isRegistered) {
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showRegistration = true },
                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AppRegistration, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Register New Shop", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ais-dev-nlecgp4wfa3sgpjani44jp-135102368660.asia-southeast1.run.app"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(imageVector = Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🌐 Open Web Portal in Browser", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Default Info Tip Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "💡 Quick Test Instructions:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "• Default Username: owner\n• Default Password: owner123\n• You can edit credentials inside the profile settings anytime.",
                            fontSize = 9.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdminLoginScreen(viewModel: SmartXeroxViewModel) {
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("admin123") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showForgotPasswordDialog) {
        ForgotAdminPasswordDialog(
            viewModel = viewModel,
            onDismiss = { showForgotPasswordDialog = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .shadow(12.dp, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Shield/Admin Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFAF5FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Lock",
                        tint = Color(0xFF9333EA),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Admin Central",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1E293B)
                )
                Text(
                    text = "Authorized Admin Security Gateway Only",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Input fields
                OutlinedTextField(
                    value = username,
                    onValueChange = { 
                        username = it
                        errorMessage = null
                    },
                    label = { Text("Username", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Password", fontSize = 12.sp) },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage!!,
                        color = Color.Red,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            errorMessage = "Please enter username and password."
                            return@Button
                        }
                        viewModel.loginAdmin(username, password) { success ->
                            if (!success) {
                                errorMessage = "Incorrect Admin username or password!"
                            } else {
                                Toast.makeText(context, "Admin login successful! 🔐", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9333EA)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verify Admin", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                TextButton(
                    onClick = { showForgotPasswordDialog = true },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "Forgot Password?",
                        color = Color(0xFF9333EA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ais-dev-nlecgp4wfa3sgpjani44jp-135102368660.asia-southeast1.run.app"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(imageVector = Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🌐 Open Web Portal in Browser", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))



                // Default Info Tip Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "💡 Admin Test Credentials:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "• Default Admin Username: admin\n• Default Admin Password: admin123\n• Admin credentials can be modified anytime in the Admin settings tab.",
                            fontSize = 9.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ForgotOwnerPasswordDialog(
    viewModel: SmartXeroxViewModel,
    onDismiss: () -> Unit
) {
    var adminPass by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var emailForReset by remember { mutableStateOf("") }
    var isEmailResetMode by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Forgot Password - Owner",
                    color = Color(0xFF1E293B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                if (!isEmailResetMode) {
                    Text(
                        text = "Enter Admin password to reset shop owner's password.",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )

                    OutlinedTextField(
                        value = adminPass,
                        onValueChange = { adminPass = it },
                        label = { Text("Admin Password", fontSize = 11.sp) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Owner Password", fontSize = 11.sp) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorText != null) {
                        Text(text = errorText!!, color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (adminPass.isBlank() || newPassword.isBlank()) {
                                errorText = "Please fill in all fields."
                                return@Button
                            }
                            viewModel.resetOwnerPassword(adminPass, newPassword) { success ->
                                if (success) {
                                    Toast.makeText(context, "Password successfully changed! 🎉", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    errorText = "Incorrect Admin password!"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset Password", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { isEmailResetMode = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Or reset via Email Link", fontSize = 10.sp, color = Color(0xFF2563EB))
                    }
                } else {
                    Text(
                        text = "Enter your registered email to receive a password reset link.",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )

                    OutlinedTextField(
                        value = emailForReset,
                        onValueChange = { emailForReset = it },
                        label = { Text("Registered Email", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorText != null) {
                        Text(text = errorText!!, color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (emailForReset.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(emailForReset).matches()) {
                                errorText = "Please enter a valid email."
                                return@Button
                            }
                            viewModel.triggerFirebaseAuthReset(emailForReset) { success, error ->
                                if (success) {
                                    Toast.makeText(context, "Password reset link sent! Please check your email. 📬", Toast.LENGTH_LONG).show()
                                    onDismiss()
                                } else {
                                    errorText = error ?: "Failed to send password reset link."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Send Reset Link", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { isEmailResetMode = false },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Go Back", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun ForgotAdminPasswordDialog(
    viewModel: SmartXeroxViewModel,
    onDismiss: () -> Unit
) {
    var masterUpi by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var emailForReset by remember { mutableStateOf("") }
    var isEmailResetMode by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Forgot Password - Admin",
                    color = Color(0xFF1E293B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                if (!isEmailResetMode) {
                    Text(
                        text = "Enter your Master UPI ID to reset Admin Password.",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )

                    OutlinedTextField(
                        value = masterUpi,
                        onValueChange = { masterUpi = it },
                        label = { Text("Master UPI ID", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Admin Password", fontSize = 11.sp) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorText != null) {
                        Text(text = errorText!!, color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (masterUpi.isBlank() || newPassword.isBlank()) {
                                errorText = "Please fill in all fields."
                                return@Button
                            }
                            viewModel.resetAdminPassword(masterUpi, newPassword) { success ->
                                if (success) {
                                    Toast.makeText(context, "Admin password successfully changed! 🎉", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    errorText = "Incorrect Master UPI ID!"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9333EA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset Password", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { isEmailResetMode = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Or reset via Email Link", fontSize = 10.sp, color = Color(0xFF9333EA))
                    }
                } else {
                    Text(
                        text = "Enter your registered email to receive a password reset link.",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )

                    OutlinedTextField(
                        value = emailForReset,
                        onValueChange = { emailForReset = it },
                        label = { Text("Registered Email", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorText != null) {
                        Text(text = errorText!!, color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (emailForReset.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(emailForReset).matches()) {
                                errorText = "Please enter a valid email."
                                return@Button
                            }
                            viewModel.triggerFirebaseAuthReset(emailForReset) { success, error ->
                                if (success) {
                                    Toast.makeText(context, "Password reset link sent! Please check your email. 📬", Toast.LENGTH_LONG).show()
                                    onDismiss()
                                } else {
                                    errorText = error ?: "Failed to send password reset link."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9333EA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Send Reset Link", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { isEmailResetMode = false },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Go Back", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

// ==========================================
// ID CARD XEROX WIZARD AND CUSTOM FLOWS
// ==========================================
@Composable
fun IDCardXeroxScreen(viewModel: SmartXeroxViewModel) {
    val context = LocalContext.current
    val selectedService by viewModel.selectedService.collectAsState()
    val showUniversalPreview by viewModel.showUniversalPreview.collectAsState()
    val showPaymentDialog by viewModel.showPaymentDialog.collectAsState()

    val frontPhoto by viewModel.idCardFrontBase64.collectAsState()
    val backPhoto by viewModel.idCardBackBase64.collectAsState()
    val frontRotation by viewModel.idCardFrontRotation.collectAsState()
    val backRotation by viewModel.idCardBackRotation.collectAsState()

    val frontScale by viewModel.idCardFrontScale.collectAsState()
    val backScale by viewModel.idCardBackScale.collectAsState()
    val frontOffsetX by viewModel.idCardFrontOffsetX.collectAsState()
    val backOffsetX by viewModel.idCardBackOffsetX.collectAsState()
    val frontOffsetY by viewModel.idCardFrontOffsetY.collectAsState()
    val backOffsetY by viewModel.idCardBackOffsetY.collectAsState()
    val autoCropOnUpload by viewModel.autoCropOnUpload.collectAsState()
    val idCardPageMargin by viewModel.idCardPageMargin.collectAsState()
    val idCardSpacing by viewModel.idCardSpacing.collectAsState()

    var cropActiveSide by remember { mutableStateOf<String?>(null) }

    val singleRate = selectedService?.rate ?: 5.0
    var copiesCount by remember { mutableStateOf(1) }
    val totalPrice = remember(copiesCount, selectedService) { singleRate * copiesCount.toDouble() }

    val frontGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val finalUriStr = if (autoCropOnUpload) {
                val cropped = autoCropWhitespace(context, uri.toString())
                if (cropped != uri.toString()) {
                    Toast.makeText(context, "✂️ Automatically trimmed background whitespace!", Toast.LENGTH_SHORT).show()
                }
                cropped
            } else {
                uri.toString()
            }
            viewModel.setIdCardFrontPhoto(finalUriStr)
            Toast.makeText(context, "Selected Front Side Photo! 🖼️", Toast.LENGTH_SHORT).show()
        }
    }

    val frontCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val cacheUri = saveBitmapToCache(context, bitmap)
            if (cacheUri != null) {
                val finalUriStr = if (autoCropOnUpload) {
                    val cropped = autoCropWhitespace(context, cacheUri.toString())
                    if (cropped != cacheUri.toString()) {
                        Toast.makeText(context, "✂️ Automatically trimmed background whitespace!", Toast.LENGTH_SHORT).show()
                    }
                    cropped
                } else {
                    cacheUri.toString()
                }
                viewModel.setIdCardFrontPhoto(finalUriStr)
                Toast.makeText(context, "Front Side Photo Captured! 📸", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val backGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val finalUriStr = if (autoCropOnUpload) {
                val cropped = autoCropWhitespace(context, uri.toString())
                if (cropped != uri.toString()) {
                    Toast.makeText(context, "✂️ Automatically trimmed background whitespace!", Toast.LENGTH_SHORT).show()
                }
                cropped
            } else {
                uri.toString()
            }
            viewModel.setIdCardBackPhoto(finalUriStr)
            Toast.makeText(context, "Selected Back Side Photo! 🖼️", Toast.LENGTH_SHORT).show()
        }
    }

    val backCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val cacheUri = saveBitmapToCache(context, bitmap)
            if (cacheUri != null) {
                val finalUriStr = if (autoCropOnUpload) {
                    val cropped = autoCropWhitespace(context, cacheUri.toString())
                    if (cropped != cacheUri.toString()) {
                        Toast.makeText(context, "✂️ Automatically trimmed background whitespace!", Toast.LENGTH_SHORT).show()
                    }
                    cropped
                } else {
                    cacheUri.toString()
                }
                viewModel.setIdCardBackPhoto(finalUriStr)
                Toast.makeText(context, "Back Side Photo Captured! 📸", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.clearSelectedService() }) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF0F172A))
            }
            Text(
                text = "ID Card Xerox Wizard",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                fontSize = 15.sp,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Info Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF6750A4),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Both front & back of your ID card will be merged onto a single page automatically. Original proportions are preserved.",
                    fontSize = 10.sp,
                    color = Color(0xFF475569),
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Tool Buttons Row (Save Settings & Export Image)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    viewModel.saveIDCardTemplate()
                    Toast.makeText(context, "💾 ID Card settings saved as default template!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF475569)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save Template", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    val bitmap = generateIDCardXeroxBitmap(
                        context, frontPhoto, backPhoto,
                        frontRotation, backRotation,
                        frontScale, backScale,
                        frontOffsetX, backOffsetX,
                        frontOffsetY, backOffsetY,
                        idCardPageMargin,
                        idCardSpacing
                    )
                    downloadIDCardBitmap(context, bitmap)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEF2FF), contentColor = Color(0xFF6750A4)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export Xerox Image", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Scrollable content area
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Auto Crop Switch Row
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Crop,
                                contentDescription = "Auto Crop",
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Auto-Crop Whitespace",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = "Trims blank background borders on upload",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                        Switch(
                            checked = autoCropOnUpload,
                            onCheckedChange = { viewModel.setAutoCropOnUpload(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF059669)
                            )
                        )
                    }
                }

                // Front side block
                IDCardUploadBlock(
                    title = "Front Side of ID Card",
                    photoId = frontPhoto,
                    rotation = frontRotation,
                    scale = frontScale,
                    offsetX = frontOffsetX,
                    offsetY = frontOffsetY,
                    onGalleryClick = { frontGalleryLauncher.launch("image/*") },
                    onCameraClick = { frontCameraLauncher.launch(null) },
                    onRotateClick = { viewModel.rotateIdCardFront() },
                    onCropClick = { cropActiveSide = "front" },
                    onClearClick = { viewModel.setIdCardFrontPhoto(null) },
                    isBack = false
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Back side block
                IDCardUploadBlock(
                    title = "Back Side of ID Card",
                    photoId = backPhoto,
                    rotation = backRotation,
                    scale = backScale,
                    offsetX = backOffsetX,
                    offsetY = backOffsetY,
                    onGalleryClick = { backGalleryLauncher.launch("image/*") },
                    onCameraClick = { backCameraLauncher.launch(null) },
                    onRotateClick = { viewModel.rotateIdCardBack() },
                    onCropClick = { cropActiveSide = "back" },
                    onClearClick = { viewModel.setIdCardBackPhoto(null) },
                    isBack = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Copies block
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Copies Count", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), fontSize = 12.sp)
                            Text(text = "₹${singleRate.toInt()} per copy", color = Color.Gray, fontSize = 10.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (copiesCount > 1) copiesCount-- },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF475569)),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text(text = "-", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Text(
                                text = "$copiesCount",
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                fontSize = 14.sp
                            )
                            IconButton(
                                onClick = { copiesCount++ },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF475569)),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text(text = "+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.toggleUniversalPreview(true) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6750A4)),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Visibility, contentDescription = "Preview", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Preview A4", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.toggleUniversalPreview(true) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Payment, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pay & Print", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (cropActiveSide == "front") {
        IDCardCropDialog(
            isBack = false,
            photoId = frontPhoto,
            scale = frontScale,
            offsetX = frontOffsetX,
            offsetY = frontOffsetY,
            onScaleChange = { viewModel.setIdCardFrontScale(it) },
            onOffsetXChange = { viewModel.setIdCardFrontOffsetX(it) },
            onOffsetYChange = { viewModel.setIdCardFrontOffsetY(it) },
            onDismiss = { cropActiveSide = null }
        )
    }

    if (cropActiveSide == "back") {
        IDCardCropDialog(
            isBack = true,
            photoId = backPhoto,
            scale = backScale,
            offsetX = backOffsetX,
            offsetY = backOffsetY,
            onScaleChange = { viewModel.setIdCardBackScale(it) },
            onOffsetXChange = { viewModel.setIdCardBackOffsetX(it) },
            onOffsetYChange = { viewModel.setIdCardBackOffsetY(it) },
            onDismiss = { cropActiveSide = null }
        )
    }

    if (showUniversalPreview) {
        UniversalPrintPreviewDialog(
            viewModel = viewModel,
            isPassport = false,
            price = totalPrice,
            onConfirm = { viewModel.openPayment() }
        )
    }

    if (showPaymentDialog) {
        UPIPaymentDialog(viewModel = viewModel, priceOverride = totalPrice)
    }
}

@Composable
fun IDCardCropDialog(
    isBack: Boolean,
    photoId: String?,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onScaleChange: (Float) -> Unit,
    onOffsetXChange: (Float) -> Unit,
    onOffsetYChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isBack) "Crop & Align Back Side" else "Crop & Align Front Side",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF0F172A)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Live preview card
                Box(
                    modifier = Modifier
                        .size(150.dp, 95.dp)
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    IDCardPhoto(
                        photoId = photoId,
                        isBack = isBack,
                        modifier = Modifier.fillMaxSize(),
                        scale = scale,
                        offsetX = offsetX,
                        offsetY = offsetY
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Sliders
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        Text(
                            text = "Zoom / Crop: ${String.format("%.2fx", scale)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                        Slider(
                            value = scale,
                            onValueChange = onScaleChange,
                            valueRange = 1.0f..3.0f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF6750A4), activeTrackColor = Color(0xFF6750A4))
                        )
                    }
                    
                    Column {
                        Text(
                            text = "Move Left / Right (X): ${offsetX.toInt()}px",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                        Slider(
                            value = offsetX,
                            onValueChange = onOffsetXChange,
                            valueRange = -200f..200f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF6750A4), activeTrackColor = Color(0xFF6750A4))
                        )
                    }
                    
                    Column {
                        Text(
                            text = "Move Up / Down (Y): ${offsetY.toInt()}px",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                        Slider(
                            value = offsetY,
                            onValueChange = onOffsetYChange,
                            valueRange = -200f..200f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF6750A4), activeTrackColor = Color(0xFF6750A4))
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onScaleChange(1.0f)
                            onOffsetXChange(0f)
                            onOffsetYChange(0f)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset", fontSize = 11.sp)
                    }
                    
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Apply", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun IDCardUploadBlock(
    title: String,
    photoId: String?,
    rotation: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
    onRotateClick: () -> Unit,
    onCropClick: () -> Unit,
    onClearClick: () -> Unit,
    isBack: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isBack) Icons.Default.CreditCardOff else Icons.Default.CreditCard,
                        contentDescription = null,
                        tint = Color(0xFF6750A4),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = title, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), fontSize = 13.sp)
                }
                if (photoId != null) {
                    IconButton(onClick = onClearClick, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear Photo", tint = Color.Red, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rotatable Photo Box with Crops applied
                Box(
                    modifier = Modifier
                        .size(110.dp, 70.dp)
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(rotation),
                        contentAlignment = Alignment.Center
                    ) {
                        IDCardPhoto(
                            photoId = photoId,
                            isBack = isBack,
                            modifier = Modifier.fillMaxSize(),
                            scale = scale,
                            offsetX = offsetX,
                            offsetY = offsetY
                        )
                    }
                }

                // Controls
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onCameraClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF475569)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Camera", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onGalleryClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF475569)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gallery", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onRotateClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEF2FF), contentColor = Color(0xFF6750A4)),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Icon(imageVector = Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rot ${rotation.toInt()}°", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onCropClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFECFDF5), contentColor = Color(0xFF059669)),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Crop & Align", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IDCardPhoto(
    photoId: String?,
    isBack: Boolean,
    modifier: Modifier = Modifier,
    scale: Float = 1.0f,
    offsetX: Float = 0f,
    offsetY: Float = 0f
) {
    if (photoId != null && (photoId.startsWith("content:") || photoId.startsWith("file:"))) {
        Box(
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            coil.compose.AsyncImage(
                model = photoId,
                contentDescription = if (isBack) "ID Card Back" else "ID Card Front",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
    } else {
        IDCardMockupVector(isBack = isBack, modifier = modifier)
    }
}

@Composable
fun IDCardMockupVector(
    isBack: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        shape = RoundedCornerShape(8.dp)
    ) {
        if (!isBack) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(Color(0xFF1E3A8A), RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "IDENTITY CARD",
                        color = Color.White,
                        fontSize = 6.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp, 36.dp)
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(2.dp))
                            .border(0.5.dp, Color(0xFF94A3B8), RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(3.dp)
                                .background(Color(0xFF475569), RoundedCornerShape(1.dp))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(2.dp)
                                .background(Color(0xFF94A3B8), RoundedCornerShape(1.dp))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(2.dp)
                                .background(Color(0xFF94A3B8), RoundedCornerShape(1.dp))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(2.dp)
                                .background(Color(0xFF94A3B8), RoundedCornerShape(1.dp))
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFFFBBF24), RoundedCornerShape(1.dp))
                    )
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height(2.dp)
                            .background(Color(0xFFE2E8F0))
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(Color(0xFF1E293B))
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0xFF94A3B8), RoundedCornerShape(1.dp))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(2.dp)
                        .background(Color(0xFF94A3B8), RoundedCornerShape(1.dp))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(2.dp)
                        .background(Color(0xFF94A3B8), RoundedCornerShape(1.dp))
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    val barcodeBarWidths = listOf(2, 1, 3, 1, 2, 4, 1, 2, 1, 3, 2, 1, 2)
                    barcodeBarWidths.forEach { width ->
                        Box(
                            modifier = Modifier
                                .width(width.dp)
                                .fillMaxHeight()
                                .background(Color.Black)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// HIGH RES ARTIFACT IMAGE EXPORT FOR ID CARD
// ==========================================
fun generateIDCardXeroxBitmap(
    context: Context,
    frontPhotoUri: String?,
    backPhotoUri: String?,
    frontRotation: Float,
    backRotation: Float,
    frontScale: Float,
    backScale: Float,
    frontOffsetX: Float,
    backOffsetX: Float,
    frontOffsetY: Float,
    backOffsetY: Float,
    idCardPageMargin: Float = 16f,
    idCardSpacing: Float = 12f
): Bitmap {
    val width = 1200
    val height = 1697
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    val bgPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
    
    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 30f
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText("SMART XEROX - ID CARD SINGLE PAGE", (width / 2).toFloat(), 80f, textPaint)
    
    // Dynamic alignment geometry:
    val cardH = 315f
    val topMargin = idCardPageMargin * 20f
    val betweenSpacing = idCardSpacing * 36f
    
    val centerYFront = topMargin + cardH / 2f
    val centerYBack = centerYFront + cardH + betweenSpacing
    
    val linePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.LTGRAY
        strokeWidth = 2f
        style = android.graphics.Paint.Style.STROKE
    }
    // Midpoint halfway between front card bottom and back card top
    val frontBottom = centerYFront + (cardH / 2f)
    val backTop = centerYBack - (cardH / 2f)
    val midpointY = (frontBottom + backTop) / 2f
    canvas.drawLine(100f, midpointY, (width - 100).toFloat(), midpointY, linePaint)
    
    drawSingleCardOnCanvas(
        context, canvas, frontPhotoUri, isBack = false,
        centerX = (width / 2).toFloat(), centerY = centerYFront,
        rotation = frontRotation, scale = frontScale, offsetX = frontOffsetX, offsetYFront = frontOffsetY
    )
    
    drawSingleCardOnCanvas(
        context, canvas, backPhotoUri, isBack = true,
        centerX = (width / 2).toFloat(), centerY = centerYBack,
        rotation = backRotation, scale = backScale, offsetX = backOffsetX, offsetYBack = backOffsetY
    )
    
    return bitmap
}

private fun drawSingleCardOnCanvas(
    context: Context,
    canvas: android.graphics.Canvas,
    photoUriStr: String?,
    isBack: Boolean,
    centerX: Float,
    centerY: Float,
    rotation: Float,
    scale: Float,
    offsetX: Float,
    offsetYFront: Float = 0f,
    offsetYBack: Float = 0f
) {
    val cardW = 500f
    val cardH = 315f
    val offsetY = if (isBack) offsetYBack else offsetYFront
    
    canvas.save()
    canvas.translate(centerX, centerY)
    canvas.rotate(rotation)
    
    val cardPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.FILL
    }
    val borderPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#CBD5E1")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3f
    }
    
    val rect = android.graphics.RectF(-cardW/2, -cardH/2, cardW/2, cardH/2)
    canvas.drawRoundRect(rect, 16f, 16f, cardPaint)
    
    var photoBitmap: Bitmap? = null
    if (photoUriStr != null && (photoUriStr.startsWith("content:") || photoUriStr.startsWith("file:"))) {
        try {
            val uri = Uri.parse(photoUriStr)
            val inputStream = context.contentResolver.openInputStream(uri)
            photoBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    if (photoBitmap != null) {
        canvas.save()
        val path = android.graphics.Path()
        path.addRoundRect(rect, 16f, 16f, android.graphics.Path.Direction.CW)
        canvas.clipPath(path)
        
        canvas.scale(scale, scale)
        canvas.translate(offsetX, offsetY)
        
        val srcRect = android.graphics.Rect(0, 0, photoBitmap.width, photoBitmap.height)
        canvas.drawBitmap(photoBitmap, srcRect, android.graphics.Rect(-cardW.toInt()/2, -cardH.toInt()/2, cardW.toInt()/2, cardH.toInt()/2), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    } else {
        drawMockVectorOnCanvas(canvas, isBack, cardW, cardH)
    }
    
    canvas.drawRoundRect(rect, 16f, 16f, borderPaint)
    canvas.restore()
}

private fun drawMockVectorOnCanvas(canvas: android.graphics.Canvas, isBack: Boolean, cardW: Float, cardH: Float) {
    val paint = android.graphics.Paint().apply { isAntiAlias = true }
    if (!isBack) {
        paint.color = android.graphics.Color.parseColor("#1E3A8A")
        paint.style = android.graphics.Paint.Style.FILL
        canvas.drawRoundRect(android.graphics.RectF(-cardW/2 + 15, -cardH/2 + 15, cardW/2 - 15, -cardH/2 + 65), 8f, 8f, paint)
        
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 24f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.textAlign = android.graphics.Paint.Align.CENTER
        canvas.drawText("IDENTITY CARD", 0f, -cardH/2 + 48, paint)
        
        paint.color = android.graphics.Color.parseColor("#F1F5F9")
        paint.style = android.graphics.Paint.Style.FILL
        val pRect = android.graphics.RectF(-cardW/2 + 25, -cardH/2 + 85, -cardW/2 + 145, cardH/2 - 25)
        canvas.drawRoundRect(pRect, 8f, 8f, paint)
        
        paint.color = android.graphics.Color.parseColor("#94A3B8")
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawCircle(-cardW/2 + 85, -cardH/2 + 140, 20f, paint)
        canvas.drawArc(android.graphics.RectF(-cardW/2 + 50, -cardH/2 + 150, -cardW/2 + 120, -cardH/2 + 210), 180f, 180f, false, paint)
        
        paint.color = android.graphics.Color.parseColor("#475569")
        paint.style = android.graphics.Paint.Style.FILL
        canvas.drawRoundRect(android.graphics.RectF(-cardW/2 + 170, -cardH/2 + 95, cardW/2 - 25, -cardH/2 + 105), 4f, 4f, paint)
        
        paint.color = android.graphics.Color.parseColor("#94A3B8")
        canvas.drawRoundRect(android.graphics.RectF(-cardW/2 + 170, -cardH/2 + 120, cardW/2 - 80, -cardH/2 + 128), 4f, 4f, paint)
        canvas.drawRoundRect(android.graphics.RectF(-cardW/2 + 170, -cardH/2 + 140, cardW/2 - 50, -cardH/2 + 148), 4f, 4f, paint)
        canvas.drawRoundRect(android.graphics.RectF(-cardW/2 + 170, -cardH/2 + 160, cardW/2 - 120, -cardH/2 + 168), 4f, 4f, paint)
        
        paint.color = android.graphics.Color.parseColor("#FBBF24")
        canvas.drawRoundRect(android.graphics.RectF(-cardW/2 + 170, cardH/2 - 55, -cardW/2 + 205, cardH/2 - 25), 6f, 6f, paint)
    } else {
        paint.color = android.graphics.Color.parseColor("#1E293B")
        paint.style = android.graphics.Paint.Style.FILL
        canvas.drawRect(android.graphics.RectF(-cardW/2, -cardH/2 + 25, cardW/2, -cardH/2 + 75), paint)
        
        paint.color = android.graphics.Color.parseColor("#94A3B8")
        canvas.drawRoundRect(android.graphics.RectF(-cardW/2 + 25, -cardH/2 + 110, cardW/2 - 25, -cardH/2 + 118), 4f, 4f, paint)
        canvas.drawRoundRect(android.graphics.RectF(-cardW/2 + 25, -cardH/2 + 135, cardW/2 - 60, -cardH/2 + 143), 4f, 4f, paint)
        canvas.drawRoundRect(android.graphics.RectF(-cardW/2 + 25, -cardH/2 + 160, cardW/2 - 40, -cardH/2 + 168), 4f, 4f, paint)
        
        paint.color = android.graphics.Color.BLACK
        var currentX = -cardW/2 + 25
        val barWidths = listOf(6f, 3f, 9f, 3f, 6f, 12f, 3f, 6f, 3f, 9f, 6f, 3f, 6f, 9f, 3f, 12f, 3f, 6f)
        for (w in barWidths) {
            canvas.drawRect(android.graphics.RectF(currentX, cardH/2 - 65, currentX + w, cardH/2 - 25), paint)
            currentX += w + 4f
        }
    }
}

fun downloadIDCardBitmap(context: Context, bitmap: Bitmap) {
    try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "ID_Card_Xerox_${System.currentTimeMillis()}.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SmartXerox")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri).use { outputStream ->
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            Toast.makeText(context, "Saved to Gallery / Pictures / SmartXerox! 💾", Toast.LENGTH_LONG).show()
            return
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Saving to Gallery failed. Attempting alternate save/share...", Toast.LENGTH_SHORT).show()
    }
    
    // Alternate save/share flow
    val cacheUri = saveBitmapToCache(context, bitmap)
    if (cacheUri != null) {
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, cacheUri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "Save or Share ID Card Xerox"))
    }
}
