package com.example

import android.os.Bundle
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppPerspective
import com.example.viewmodel.SmartXeroxViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) { innerPadding ->
                    SmartXeroxDashboardScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
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
        Column(modifier = Modifier.fillMaxSize()) {
            // Elegant Top Header with App Title and Global Reset
            HeaderSection(
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
                when (perspective) {
                    AppPerspective.CUSTOMER -> CustomerPortalScreen(
                        viewModel = viewModel,
                        isPrinterOnline = isPrinterOnline
                    )
                    AppPerspective.OWNER -> ShopOwnerScreen(
                        viewModel = viewModel
                    )
                    AppPerspective.ADMIN -> AdminDashboardScreen(
                        viewModel = viewModel
                    )
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
                                text = "ऑटो प्रिंट सुरु आहे (Auto Printing): ${activePrintJob?.orderId}",
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
fun HeaderSection(
    onReset: () -> Unit,
    isServiceRunning: Boolean,
    isPrinterOnline: Boolean
) {
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
                // Purple rounded Logo badge
                Surface(
                    color = Color(0xFF6750A4),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "SX",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
                
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "SmartXerox",
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
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (isPrinterOnline) Color(0xFF22C55E) else Color(0xFFEF4444),
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = if (isPrinterOnline) "Service Active • Heartbeat OK" else "Service Paused • Offline",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.5.sp
                        )
                    }
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
            AppPerspective.CUSTOMER to Triple(Icons.Default.QrCodeScanner, "ग्राहक (Customer)", "QR Portal"),
            AppPerspective.OWNER to Triple(Icons.Default.Storefront, "दुकानदार (Owner)", "Android App"),
            AppPerspective.ADMIN to Triple(Icons.Default.Security, "ॲडमीन (Admin)", "Dashboard")
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

// ==========================================
// 1. CUSTOMER PORTAL SCREEN
// ==========================================
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Customer view header
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Customer Journey",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                    Surface(
                        color = Color(0xFFEEF2FF),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Text(
                            text = "PWA / Scan Only",
                            color = Color(0xFF6750A4),
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "PWA Link",
                        tint = Color(0xFF6750A4),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "smartxerox.co.in/shop/OWNER01",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontSize = 13.sp
                        )
                        Text(
                            text = "QR स्कॅन करून उघडलेली कस्टमर वेबसाईट (No App Install Required)",
                            color = Color(0xFF64748B),
                            fontSize = 9.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(12.dp))
                
                // Visual 4-Step Customer Flow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val steps = listOf(
                        "📷" to "1. QR\nScan",
                        "🤖" to "2. AI\nMagic",
                        "💳" to "3. Direct\nUPI",
                        "📄" to "4. Auto\nPrint"
                    )
                    steps.forEachIndexed { index, (emoji, text) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = if (index == 3) Color(0xFFDCFCE7) else Color(0xFFEEF2FF),
                                border = if (index == 3) BorderStroke(1.dp, Color(0xFFBBF7D0)) else null,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = emoji, fontSize = 16.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = text,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (index == 3) Color(0xFF15803D) else Color(0xFF475569),
                                textAlign = TextAlign.Center,
                                lineHeight = 10.sp
                            )
                        }
                    }
                }
            }
        }

        // V5.0 Damdar Rule Status Banner
        StatusSafetyBanner(isPrinterOnline = isPrinterOnline)

        Spacer(modifier = Modifier.height(12.dp))

        if (!isPrinterOnline) {
            OfflineCustomerView()
        } else {
            // Main Content depending on current wizard step
            Box(modifier = Modifier.weight(1f)) {
                if (selectedService == null) {
                    CustomerServiceGrid(viewModel = viewModel)
                } else if (selectedService?.id == "passport_photo_ai" && photoBase64 == null) {
                    PassportPhotoUploadScreen(viewModel = viewModel)
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
fun PassportPhotoUploadScreen(
    viewModel: SmartXeroxViewModel
) {
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
                    text = "१ सेकंदात AI बॅकग्राउंड रिमूव्हल!",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    fontSize = 14.sp
                )
                Text(
                    text = "कॅमेरा किंवा गॅलरीमधून फोटो अपलोड करा. परफेक्ट पांढरा बॅकग्राउंड आणि ८ फोटोंची शीट आपोआप तयार होईल.",
                    color = Color(0xFF475569),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "चाचणीसाठी खालीलपैकी एक कॅरेक्टर निवडा (Select Character to Test):",
            fontSize = 11.sp,
            color = Color(0xFF475569),
            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
        )

        // Grid of 4 avatar selection
        val characters = listOf(
            "rohan" to "रोहन (Rohan)",
            "priya" to "प्रिया (Priya)",
            "rahul" to "राहुल (Rahul - Glasses)",
            "sneha" to "स्नेहा (Sneha - Bindi)"
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

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { 
                // Randomly pick an avatar to simulate camera snap
                val randomAvatar = listOf("rohan", "priya", "rahul", "sneha").random()
                viewModel.selectMockPhoto(randomAvatar)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "कॅमेराने फोटो काढा (Take Camera Photo)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                    text = if (isPrinterOnline) "ग्राहकांसाठी सुरक्षित पेमेंट चालू आहे (0% Risk)" else "सेवा सध्या तात्पुरती बंद आहे (Security Lock)",
                    color = if (isPrinterOnline) Color(0xFF14532D) else Color(0xFF7F1D1D),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = if (isPrinterOnline) 
                        "दुकानदाराचा फोन आणि प्रिंटर ऑनलाईन आहेत. तुमचे पेमेंट कधीच अडकणार नाही." 
                    else 
                        "दुकानदाराचे ॲप किंवा प्रिंटर बंद असल्याने पेमेंट बटण बंद केले आहे. तुमचे पैसे अडकणार नाहीत!",
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
            text = "सेवा सध्या बंद आहे",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF0F172A)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "दुकानदाराचे प्रिंटर बंद आहे, मोबाईल स्विच ऑफ आहे किंवा इंटरनेट बंद आहे.\nसुरक्षिततेसाठी पेमेंट बटण गायब केले आहे.",
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
                text = "💡 फायदा: ग्राहकाचे पैसे आधीच कट होऊन order अडकणे आता १००% बंद! दुकानदार ऑनलाईन येताच सेवा आपोआप सुरू होईल.",
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
fun CustomerServiceGrid(viewModel: SmartXeroxViewModel) {
    val serviceRates by viewModel.serviceRates.collectAsState()
    
    // Categorize services to make browsing simple
    var selectedCategory by remember { mutableStateOf("AI Services") }
    val categories = listOf("AI Services", "Document Services", "Binding & Lamination", "Cards & Prints", "Business Services", "Gifting & Merch")
    
    val filteredServices = serviceRates.filter { it.category == selectedCategory }

    Column(modifier = Modifier.fillMaxSize()) {
        // Category scroll tab
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ScrollableTabRow(
                selectedTabIndex = categories.indexOf(selectedCategory),
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                categories.forEachIndexed { index, cat ->
                    val isSelected = selectedCategory == cat
                    Tab(
                        selected = isSelected,
                        onClick = { selectedCategory = cat },
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

        Text(
            text = "सर्व्हिस निवडा (Choose Service):",
            fontSize = 13.sp,
            color = Color(0xFF0F172A),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredServices) { service ->
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
                                        "pvc_card_print" -> Icons.Default.CreditCard
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
            text = "AI मॅजिक प्रोसेसिंग सुरू आहे...",
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
            text = "फक्त ३ सेकंदात बॅकग्राउंड रिमूव्ह आणि क्रॉप होईल.",
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
            text = "३ सेकंदात फोटो आपोआप कन्फर्म होईल (किंवा मॅन्युअल करा):",
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
                        AvatarCanvas(
                            gender = selectedPhotoId ?: "rohan",
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
        Text(text = "मॅन्युअल ऍडजस्ट करा (Manual Zoom/Pan):", fontSize = 11.sp, color = Color(0xFF475569))
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
                Text(text = "रद्द करा")
            }
            Button(
                onClick = { viewModel.confirmPassportLayout() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                modifier = Modifier.weight(1.5f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "कन्फर्म करा (Confirm)", fontWeight = FontWeight.Bold)
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
            text = "AI 8-Photos Sheet तयार झाली आहे (कॅटरिंग कट गाईडसह):",
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
                                    AvatarCanvas(gender = selectedPhotoId ?: "rohan", modifier = Modifier.fillMaxSize(0.7f * photoScale))
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
                                    AvatarCanvas(gender = selectedPhotoId ?: "rohan", modifier = Modifier.fillMaxSize(0.7f * photoScale))
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
                    Text(text = "ऑर्डर डिटेल्स (Bill Details):", color = Color(0xFF475569), fontSize = 11.sp)
                    Text(text = "स्मार्टझेरॉक्स V5.0", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
                    text = "✓ 8 कॉपीज् (4\"x6\" प्रिमियम ग्लॉसी Paper वर)",
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
                Text("फिरवा (Rotate 90°)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { viewModel.toggleUniversalPreview(true) },
                modifier = Modifier.weight(1.5f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6750A4)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Visibility, contentDescription = "Preview", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("प्रिंट प्रीव्ह्यू (Print Preview)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    text = "₹${selectedService?.rate?.toInt() ?: 30} पे करून प्रिंट करा (Pay & Print)",
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

@Composable
fun StandardServiceUploadScreen(viewModel: SmartXeroxViewModel) {
    val selectedService by viewModel.selectedService.collectAsState()
    val showPaymentDialog by viewModel.showPaymentDialog.collectAsState()
    
    var documentName by remember { mutableStateOf("") }
    var copiesCount by remember { mutableStateOf(1) }
    var printRange by remember { mutableStateOf("All Pages") }
    var doubleSided by remember { mutableStateOf(false) }

    val singleRate = selectedService?.rate ?: 5.0
    val totalPrice = remember(copiesCount, selectedService) { singleRate * copiesCount.toDouble() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
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

        // Document Uploader Card Simulator
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "Upload Document",
                    tint = Color(0xFF6750A4),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "डॉक्युमेंट फाईल अपलोड करा",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    fontSize = 14.sp
                )
                Text(
                    text = "PDF, DOCX, JPG, PNG supports up to 100MB",
                    color = Color(0xFF475569),
                    fontSize = 10.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                // File selectors
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val mockFiles = listOf("marksheet.pdf", "resume_v2.docx", "electricity_bill.pdf")
                    mockFiles.forEach { file ->
                        val isSelected = documentName == file
                        Surface(
                            onClick = { documentName = file },
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
                Text(text = "प्रिंट सेटिंग्ज (Print Settings):", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 8.dp))
                
                // Copies Count Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "प्रती (Copies):", color = Color(0xFF0F172A), fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                    Text(text = "पेज रेंज (Page Range):", color = Color(0xFF0F172A), fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                    Text(text = "दोन्ही बाजूने प्रिंट (Double-Sided):", color = Color(0xFF0F172A), fontSize = 13.sp, fontWeight = FontWeight.Medium)
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

                // Show Print Preview button
                OutlinedButton(
                    onClick = { viewModel.toggleUniversalPreview(true) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6750A4)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Visibility, contentDescription = "Preview", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("प्रिंट प्रीव्ह्यू तपासा (Show Print Preview)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Total price and Pay button
        Button(
            onClick = { 
                if (documentName.isEmpty()) {
                    documentName = "document.pdf" // Auto pick default
                }
                viewModel.toggleUniversalPreview(true) // Open preview first!
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
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
                    text = "₹${totalPrice.toInt()} पे आणि प्रिंट करा (Pay & Print)",
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
                        text = "Razorpay Secure UPI",
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
                    text = "एकूण रक्कम (Total Price):",
                    fontSize = 11.sp,
                    color = Color(0xFF475569)
                )
                Text(
                    text = "₹${price.toInt()}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF6750A4)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isPaymentProcessing) {
                    CircularProgressIndicator(color = Color(0xFF6750A4))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "पेमेंट व्हेरीफाय होत आहे... (Verifying Payment)",
                        fontSize = 11.sp,
                        color = Color(0xFF6750A4),
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "UPI ॲप निवडा:",
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
                                text = "$appName द्वारे पेमेंट करा",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "🔒 पेमेंट होताच डायरेक्ट दुकानदाराच्या खात्यात पैसे जमा होतील. स्मार्टझेरॉक्स कोणतीही फी अटकावून ठेवत नाही.",
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
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val printerConfig by viewModel.printerConfig.collectAsState()
    val heartbeatLogs by viewModel.heartbeatLogs.collectAsState()
    val orders by viewModel.orders.collectAsState()
    val nextHeartbeatSeconds by viewModel.nextHeartbeatSeconds.collectAsState()

    var showEditRateDialog by remember { mutableStateOf<ServiceRate?>(null) }
    var ownerSubTab by remember { mutableStateOf("Logs & Config") } // Config, Logs & Config, Rates Edit

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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
                    .padding(14.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isServiceRunning) Icons.Default.PlayCircleFilled else Icons.Default.PauseCircle,
                        contentDescription = null,
                        tint = if (isServiceRunning) Color(0xFF10B981) else Color(0xFF64748B),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isServiceRunning) "SmartXerox Active Service" else "Service Paused",
                            fontWeight = FontWeight.Bold,
                            color = if (isServiceRunning) Color(0xFF065F46) else Color(0xFF475569),
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isServiceRunning) "पुढील हार्टबीट: $nextHeartbeatSeconds सेकंदात" else "Heartbeat Off - Customer can't order",
                            color = if (isServiceRunning) Color(0xFF047857) else Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                }
                
                Button(
                    onClick = { viewModel.toggleService() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServiceRunning) Color(0xFFEF4444) else Color(0xFF10B981),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isServiceRunning) "STOP" else "START",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Sub Navigation inside Shop Owner View
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Printers & Logs", "Rates Config", "Orders Log", "Recharge & Credits").forEach { tab ->
                val isSelected = ownerSubTab == tab
                Surface(
                    onClick = { ownerSubTab = tab },
                    color = if (isSelected) Color(0xFFEEF2FF) else Color.White,
                    contentColor = if (isSelected) Color(0xFF6750A4) else Color(0xFF64748B),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFFC7D2FE) else Color(0xFFE2E8F0)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = when(tab) {
                                "Printers & Logs" -> "प्रिन्टर्स (Printers)"
                                "Rates Config" -> "दर (Rates)"
                                "Orders Log" -> "ऑर्डर्स (Orders)"
                                "Recharge & Credits" -> "रिचार्ज (Recharge)"
                                else -> tab
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Render Sub Tabs
        when (ownerSubTab) {
            "Printers & Logs" -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Multi-printer Settings Panel
                    item {
                        PrinterSettingsPanel(
                            viewModel = viewModel
                        )
                    }

                    // Foreground Service Notification Preview Mockup
                    item {
                        ForegroundNotificationMockup(isServiceRunning = isServiceRunning)
                    }

                    // Heartbeat Pings Console
                    item {
                        HeartbeatConsole(logs = heartbeatLogs)
                    }
                }
            }
            "Rates Config" -> {
                ShopkeeperRatesConfigPanel(
                    viewModel = viewModel,
                    onEditRate = { showEditRateDialog = it }
                )
            }
            "Orders Log" -> {
                ShopOrdersLogPanel(orders = orders)
            }
            "Recharge & Credits" -> {
                ShopOwnerRechargePanel(viewModel = viewModel)
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
                        text = "🖨️ मल्टिपल प्रिंटर्स (Connected Printers)",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB300),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "ऑटो-फॉलबॅक आणि लोड-बॅलन्सिंग सक्रिय आहे",
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
                    Text("नवीन जोडा", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (printers.isEmpty()) {
                Text(
                    text = "कोणताही प्रिंटर जोडलेला नाही. कृपया जोडा.",
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
                                                    "READY" -> "🟢 READY (चालू)"
                                                    "PAPER_JAM" -> "🟠 PAPER JAM (अडकलेला)"
                                                    "OUT_OF_INK" -> "🟡 OUT OF INK (शाही संपली)"
                                                    else -> "🔴 OFFLINE (बंद)"
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
                        text = "नवीन प्रिंटर जोडा (Add Printer)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("प्रिंटर नाव (e.g. HP LaserJet)") },
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
                        label = { Text("IP ॲड्रेस (e.g. 192.168.1.100)") },
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
                        Text(text = "मुख्य प्रिंटर बनवा (Primary Printer)", color = Color.White, fontSize = 11.sp)
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
                            Text("रद्द")
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
                            Text("जोडा", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ForegroundNotificationMockup(isServiceRunning: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
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
                            text = if (isServiceRunning) "SmartXerox Active" else "SmartXerox Paused",
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
                        Text(text = "हार्टबीट पिंग्स सुरू होण्यासाठी वाट पाहत आहे...", color = Color.DarkGray, fontSize = 11.sp)
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

    Column(modifier = Modifier.fillMaxSize()) {
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

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "सर्व्हिस रेट सेट करा (Shopkeeper Custom Rates):",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredServices) { service ->
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
                                        text = "आयडी: ${service.id}",
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
                    text = "रेट बदल करा (Update Rate)",
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
                    label = { Text("रेट (Rupees)") },
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
                        Text("रद्द")
                    }
                    Button(
                        onClick = { 
                            val rateVal = rateInput.toDoubleOrNull() ?: serviceRate.rate
                            onSave(serviceRate.copy(rate = rateVal))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300), contentColor = Color.Black),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("सेव्ह", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ShopOrdersLogPanel(orders: List<PrintOrder>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "📊 अलीकडील ऑर्डर्स (Recent Orders Log):",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (orders.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(text = "अजून कोणतीही ऑर्डर आलेली नाही.", color = Color.LightGray, fontSize = 11.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(orders) { order ->
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
                                    
                                    Surface(
                                        color = when (order.status) {
                                            "COMPLETED" -> Color(0xFF065F46)
                                            "FAILED" -> Color(0xFF991B1B)
                                            "PRINTING" -> Color(0xFF92400E)
                                            else -> Color(0xFF1E3A8A)
                                        },
                                        shape = RoundedCornerShape(4.dp)
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
                                        text = "रक्कम: ₹${order.price.toInt()} | रॉयल्टी: ₹${order.commissionPaid.toInt()}",
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
                                        text = "⚠️ त्रुटी: ${order.errorMessage}",
                                        color = Color.Red,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (order.printerUsedIp != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "🖨️ प्रिंटेड ऑन: IP ${order.printerUsedIp}",
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

    // Aggregate statistics
    val totalOrders = orders.filter { it.status == "COMPLETED" }
    val totalPrintedDocsCount = totalOrders.size
    val totalRevenueEarned = totalOrders.sumOf { it.price }
    val totalAdminCommission = adminSettings.totalCommissionEarned
    val totalOwnerShare = totalRevenueEarned - totalAdminCommission

    var adminTab by remember { mutableStateOf("Overview & Franchise") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Admin Navigation Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Overview & Franchise", "Pricing & Overrides", "Manage Recharge Packs").forEach { tab ->
                val isSelected = adminTab == tab
                Surface(
                    onClick = { adminTab = tab },
                    color = if (isSelected) Color(0xFFEEF2FF) else Color.White,
                    contentColor = if (isSelected) Color(0xFF6750A4) else Color(0xFF64748B),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFFC7D2FE) else Color(0xFFE2E8F0)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                        Text(
                            text = when(tab) {
                                "Overview & Franchise" -> "सांख्यिकी (Stats)"
                                "Pricing & Overrides" -> "दर नियंत्रण (Rates)"
                                "Manage Recharge Packs" -> "रिचार्ज व्यवस्थापन"
                                else -> tab
                            },
                            fontSize = 10.sp,
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
            "Overview & Franchise" -> {
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
                            Text(text = "एकूण पेजेस (Printed)", color = Color.LightGray, fontSize = 9.sp)
                            Text(text = "$totalPrintedDocsCount", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "रॉयल्टी (Company Royalty)", color = Color.LightGray, fontSize = 9.sp)
                            Text(text = "₹${totalAdminCommission.toInt()}", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.Green)
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "एकूण गल्ला (Total Turn)", color = Color.LightGray, fontSize = 9.sp)
                            Text(text = "₹${totalRevenueEarned.toInt()}", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color(0xFFFFB300))
                        }
                    }
                }

                // Auto-split visualization bar
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "💸 Razorpay Auto-Split Engine (थेट बँक ट्रान्सफर)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // Drawing split bar
                        val adminRatio = if (totalRevenueEarned > 0) (totalAdminCommission / totalRevenueEarned).toFloat() else 0.15f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f - adminRatio)
                                    .fillMaxHeight()
                                    .background(Color(0xFFFFB300)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "दुकानदार हिस्सा (Owner): ₹${totalOwnerShare.toInt()}", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(adminRatio.coerceAtLeast(0.05f))
                                    .fillMaxHeight()
                                    .background(Color(0xFF059669)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "रॉयल्टी (Admin): ₹${totalAdminCommission.toInt()}", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Recharge Commission Rate Config Panel
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
                                    text = "⚙️ ग्लोबल रॉयल्टी रेट (Company Royalty Rate)",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB300),
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "प्रत्येक यशस्वी प्रिंटमागे कापली जाणारी रक्कम",
                                    color = Color.LightGray,
                                    fontSize = 10.sp
                                )
                            }
                            IconButton(
                                onClick = { showEditRechargeRate = true },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF334155), contentColor = Color.White),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit royalty", modifier = Modifier.size(16.dp))
                            }
                        }
                        HorizontalDivider(color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "सध्याचा रेट (Recharge Rate):", fontSize = 13.sp, color = Color.White)
                            Text(
                                text = "₹${adminSettings.rechargeRate.toInt()} प्रति प्रिंट",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Green
                            )
                        }
                    }
                }

                // Franchise list Table mockup
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "🏬 इतर फ्रेंचायझी लाईव्ह स्टेटस (Franchise Live Tracker):",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        val shops = listOf(
                            Triple("OWNER01 (This)", "🟢 Active", "10,240 Printed"),
                            Triple("PUNE03", "🟢 Active", "43,120 Printed"),
                            Triple("MUMBAI09", "🟢 Active", "89,340 Printed"),
                            Triple("NAGPUR02", "🔴 Offline", "12,900 Printed"),
                            Triple("AMRAVATI01", "🟡 Idle", "4,210 Printed")
                        )

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(shops) { (shopName, status, printed) ->
                                Surface(
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = shopName,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 11.sp
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = status,
                                                color = if (status.contains("Active")) Color.Green else if (status.contains("Idle")) Color.Yellow else Color.Red,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                            Text(text = printed, color = Color.LightGray, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "Pricing & Overrides" -> {
                // Pricing Configuration and Override
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "⚠️ कंपनी दर सक्तीने लागू करा (Override Shop Rates)",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB300),
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "हे चालू केल्यावर दुकानदाराने सेट केलेले सानुकूल दर बदलून कंपनीचे डीफॉल्ट दर सर्व ग्राहकांना दाखवले जातील.",
                                    color = Color.LightGray,
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp
                                )
                            }
                            Switch(
                                checked = adminSettings.overrideCustomRates,
                                onCheckedChange = { viewModel.toggleOverrideCustomRates(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFFB300))
                            )
                        }
                    }
                }

                // Default Service Rates list
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "🛠️ अधिकृत डीफॉल्ट दर व्यवस्थापन (Default Admin Rates):",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        val serviceRates by viewModel.serviceRates.collectAsState()
                        var editTarget by remember { mutableStateOf<ServiceRate?>(null) }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(serviceRates) { service ->
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
                                                text = "वर्ग: ${service.category}",
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
            "Manage Recharge Packs" -> {
                // Admin Recharge Packs configuration
                val rechargePacks by viewModel.rechargePacks.collectAsState()
                val masterTransactions by viewModel.transactions.collectAsState()

                var showAddPackDialog by remember { mutableStateOf(false) }
                var packNameInput by remember { mutableStateOf("") }
                var packPriceInput by remember { mutableStateOf("") }
                var packCreditsInput by remember { mutableStateOf("") }
                var packDescInput by remember { mutableStateOf("") }

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
                                    text = "⚡ क्रेडिट पॅक्स पॅकेज (Recharge Pack Packs)",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB300),
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "दुकानदारांसाठी क्रेडिट पॅकेज व्यवस्थापित करा",
                                    color = Color.LightGray,
                                    fontSize = 10.sp
                                )
                            }
                            Button(
                                onClick = { showAddPackDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155), contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("नवे पॅकेज", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            rechargePacks.forEach { pack ->
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
                                            Text(text = pack.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 11.sp)
                                            Text(text = "${pack.credits} Credits | ₹${pack.price.toInt()}", color = Color.LightGray, fontSize = 10.sp)
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteRechargePack(pack.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Add Recharge Pack Dialog
                if (showAddPackDialog) {
                    Dialog(onDismissRequest = { showAddPackDialog = false }) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.border(2.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(20.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(text = "नवीन क्रेडिट पॅकेज जोडा (Add Package)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                
                                OutlinedTextField(
                                    value = packNameInput,
                                    onValueChange = { packNameInput = it },
                                    label = { Text("पॅकेज नाव (e.g. Mega Saver)") },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White)
                                )
                                OutlinedTextField(
                                    value = packPriceInput,
                                    onValueChange = { packPriceInput = it },
                                    label = { Text("किंमत रुपये (e.g. 500)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    textStyle = TextStyle(color = Color.White)
                                )
                                OutlinedTextField(
                                    value = packCreditsInput,
                                    onValueChange = { packCreditsInput = it },
                                    label = { Text("क्रेडिट्स संख्या (e.g. 300)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    textStyle = TextStyle(color = Color.White)
                                )
                                OutlinedTextField(
                                    value = packDescInput,
                                    onValueChange = { packDescInput = it },
                                    label = { Text("माहिती (e.g. ₹1.6/credit best value)") },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showAddPackDialog = false },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("रद्द")
                                    }
                                    Button(
                                        onClick = {
                                            val pr = packPriceInput.toDoubleOrNull() ?: 0.0
                                            val cr = packCreditsInput.toIntOrNull() ?: 0
                                            if (packNameInput.isNotBlank() && pr > 0 && cr > 0) {
                                                viewModel.addRechargePack("PACK_" + System.currentTimeMillis().toString(), packNameInput, pr, cr, packDescInput)
                                                packNameInput = ""
                                                packPriceInput = ""
                                                packCreditsInput = ""
                                                packDescInput = ""
                                                showAddPackDialog = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300), contentColor = Color.Black),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("जोडा", fontWeight = FontWeight.Bold)
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
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "📜 मास्टर व्यवहार इतिहास (Master Transaction History):",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (masterTransactions.isEmpty()) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(text = "अद्याप कोणताही व्यवहार झालेला नाही.", color = Color.LightGray, fontSize = 11.sp)
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(masterTransactions) { tx ->
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
                                                Text(text = "दुकानदार: OWNER01", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 11.sp)
                                                Text(text = "आयडी: ${tx.id} | पॅक: ${tx.packName}", color = Color.LightGray, fontSize = 9.sp)
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
                        text = "ग्लोबल रॉयल्टी रेट बदल (Royalty Settings)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = rechargeInput,
                        onValueChange = { rechargeInput = it },
                        label = { Text("कंपनी रॉयल्टी (₹ प्रति प्रिंट)") },
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
                            Text("रद्द")
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
                            Text("अपडेट", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
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
// SHOP OWNER RECHARGE AND CREDITS PANEL
// ==========================================
@Composable
fun ShopOwnerRechargePanel(viewModel: SmartXeroxViewModel) {
    val ownerCredits by viewModel.ownerCredits.collectAsState()
    val rechargePacks by viewModel.rechargePacks.collectAsState()
    val transactions by viewModel.transactions.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Credit Balance Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f)),
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
                            text = "🪙 प्रिंट क्रेडिट्स शिल्लक (Credit Balance)",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${ownerCredits.balance} Credits",
                            color = if (ownerCredits.balance > 10) Color(0xFFFFB300) else Color(0xFFEF4444),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "प्रत्येक यशस्वी प्रिंट जॉबवर १ क्रेडिट वजा होते.",
                            color = Color.Gray,
                            fontSize = 9.sp
                        )
                    }

                    Surface(
                        color = Color(0xFF1E293B),
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // Support Information Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "📞 अधिकृत मदत व समर्थन (Support Contact)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Mail, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = viewModel.ownerEmail, color = Color.LightGray, fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Call, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = viewModel.ownerPhone, color = Color.LightGray, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Recharge Packs List
        item {
            Text(
                text = "⚡ क्रेडिट पॅक्स खरेदी करा (Recharge Packs):",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        items(rechargePacks) { pack ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text(
                            text = pack.name,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        Text(
                            text = pack.description,
                            color = Color.LightGray,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = Color(0xFFFFB300).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "+${pack.credits} Credits Added",
                                color = Color(0xFFFFB300),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.buyRechargePack(pack) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300), contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(text = "₹${pack.price.toInt()}", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
            }
        }

        // Transaction History
        item {
            Text(
                text = "📜 रिचार्ज इतिहास (Transaction History):",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 12.sp,
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
                        text = "अद्याप कोणतेही व्यवहार केलेले नाहीत.",
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
                                text = "पॅक: ${tx.packName}",
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
                                text = "+${tx.creditsAdded} Credits",
                                color = Color(0xFFFFB300),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "₹${tx.amount.toInt()} PAID",
                                color = Color(0xFF10B981),
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
                        text = "🔎 प्रिंट प्रीव्ह्यू (Print Preview)",
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
                                            AvatarCanvas(gender = selectedPhotoId ?: "rohan", modifier = Modifier.fillMaxSize(0.6f * photoScale))
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
                                            AvatarCanvas(gender = selectedPhotoId ?: "rohan", modifier = Modifier.fillMaxSize(0.6f * photoScale))
                                        }
                                    }
                                }
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
                        text = "सध्याचे रोटेशन: ${printRotation.toInt()}°",
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
                        Text("सर्व्हिस (Service):", fontSize = 11.sp, color = Color(0xFF64748B))
                        Text(selectedService?.name ?: "डॉक्युमेंट प्रिंट", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("एकूण आकार (Size):", fontSize = 11.sp, color = Color(0xFF64748B))
                        Text(if (isPassport) "4x6 Portrait Glossy" else "A4 Standard Size", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("निवडलेला प्रिंटर (Target Printer):", fontSize = 11.sp, color = Color(0xFF64748B))
                        Text(
                            text = if (primaryPrinter != null) "${primaryPrinter.name} (${primaryPrinter.status})" else "कोणताही उपलब्ध नाही",
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
                        Text("बंद करा")
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
                        Text("कन्फर्म & पे (₹${price.toInt()})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
