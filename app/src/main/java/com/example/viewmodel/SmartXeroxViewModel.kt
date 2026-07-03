package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.AdminSettings
import com.example.data.AppDatabase
import com.example.data.PrintOrder
import com.example.data.PrinterConfig
import com.example.data.Printer
import com.example.viewmodel.AppPerspective
import com.example.data.ServiceRate
import com.example.data.RechargePack
import com.example.data.OwnerCredits
import com.example.data.Transaction
import com.example.data.SmartXeroxRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppPerspective {
    CUSTOMER, OWNER, ADMIN
}

class SmartXeroxViewModel(application: Application) : AndroidViewModel(application) {

    private val database = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "smartxerox_database"
    ).fallbackToDestructiveMigration().build()

    private val repository = SmartXeroxRepository(database.dao())

    // Owner Contact Details
    val ownerEmail = "smartxerox.co.in@gmail.com"
    val ownerPhone = "7720007020"

    // UI perspective
    private val _perspective = MutableStateFlow(AppPerspective.CUSTOMER)
    val perspective: StateFlow<AppPerspective> = _perspective.asStateFlow()

    // Service rates, config, admin, and orders
    val serviceRates: StateFlow<List<ServiceRate>> = repository.serviceRates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val adminSettings: StateFlow<AdminSettings> = repository.adminSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdminSettings())

    val printerConfig: StateFlow<PrinterConfig> = repository.printerConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PrinterConfig())

    val orders: StateFlow<List<PrintOrder>> = repository.orders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // New: Multiple Printers
    val printers: StateFlow<List<Printer>> = repository.printers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // New: Recharge Packs
    val rechargePacks: StateFlow<List<RechargePack>> = repository.rechargePacks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // New: Owner Credits
    val ownerCredits: StateFlow<OwnerCredits> = repository.ownerCredits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OwnerCredits(balance = 100))

    // New: Transactions
    val transactions: StateFlow<List<Transaction>> = repository.transactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dynamic Shop Heartbeat logs (simulating foreground service)
    private val _heartbeatLogs = MutableStateFlow<List<String>>(emptyList())
    val heartbeatLogs: StateFlow<List<String>> = _heartbeatLogs.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(true)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _nextHeartbeatSeconds = MutableStateFlow(30)
    val nextHeartbeatSeconds: StateFlow<Int> = _nextHeartbeatSeconds.asStateFlow()

    // Customer wizard state
    private val _selectedService = MutableStateFlow<ServiceRate?>(null)
    val selectedService: StateFlow<ServiceRate?> = _selectedService.asStateFlow()

    // Simulated camera and photo processing
    private val _photoBase64 = MutableStateFlow<String?>(null) // We can use mock avatars
    val photoBase64: StateFlow<String?> = _photoBase64.asStateFlow()

    private val _isProcessingAI = MutableStateFlow(false)
    val isProcessingAI: StateFlow<Boolean> = _isProcessingAI.asStateFlow()

    private val _processingStep = MutableStateFlow("")
    val processingStep: StateFlow<String> = _processingStep.asStateFlow()

    private val _showPreview = MutableStateFlow(false)
    val showPreview: StateFlow<Boolean> = _showPreview.asStateFlow()

    private val _countdownSeconds = MutableStateFlow(3)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    private val _showSheetGrid = MutableStateFlow(false)
    val showSheetGrid: StateFlow<Boolean> = _showSheetGrid.asStateFlow()

    private val _photoScale = MutableStateFlow(1f)
    val photoScale: StateFlow<Float> = _photoScale.asStateFlow()

    private val _photoOffsetX = MutableStateFlow(0f)
    val photoOffsetX: StateFlow<Float> = _photoOffsetX.asStateFlow()

    private val _photoOffsetY = MutableStateFlow(0f)
    val photoOffsetY: StateFlow<Float> = _photoOffsetY.asStateFlow()

    // Print Rotation & Customization
    private val _printRotation = MutableStateFlow(0f) // 0, 90, 180, 270
    val printRotation: StateFlow<Float> = _printRotation.asStateFlow()

    // Universal Print Preview toggle (customer can preview before payment)
    private val _showUniversalPreview = MutableStateFlow(false)
    val showUniversalPreview: StateFlow<Boolean> = _showUniversalPreview.asStateFlow()

    // UPI Payment State
    private val _showPaymentDialog = MutableStateFlow(false)
    val showPaymentDialog: StateFlow<Boolean> = _showPaymentDialog.asStateFlow()

    private val _isPaymentProcessing = MutableStateFlow(false)
    val isPaymentProcessing: StateFlow<Boolean> = _isPaymentProcessing.asStateFlow()

    private val _currentCreatedOrderId = MutableStateFlow("")
    val currentCreatedOrderId: StateFlow<String> = _currentCreatedOrderId.asStateFlow()

    // Active order print job progress
    private val _activePrintJob = MutableStateFlow<PrintOrder?>(null)
    val activePrintJob: StateFlow<PrintOrder?> = _activePrintJob.asStateFlow()

    private val _printJobProgress = MutableStateFlow(0f) // 0.0 to 1.0
    val printJobProgress: StateFlow<Float> = _printJobProgress.asStateFlow()

    private val _printJobStatus = MutableStateFlow("")
    val printJobStatus: StateFlow<String> = _printJobStatus.asStateFlow()

    // Jobs
    private var heartbeatJob: Job? = null
    private var countdownJob: Job? = null

    init {
        viewModelScope.launch {
            repository.prepopulateData()
            startHeartbeatEngine()
        }
    }

    fun setPerspective(newPerspective: AppPerspective) {
        _perspective.value = newPerspective
    }

    // Toggle Foreground Service
    fun toggleService() {
        _isServiceRunning.value = !_isServiceRunning.value
        if (_isServiceRunning.value) {
            addHeartbeatLog("🟢 SmartXerox Foreground Service started manually.")
        } else {
            addHeartbeatLog("🔴 Foreground Service stopped. Heartbeat paused. Shop is Offline!")
        }
    }

    private fun startHeartbeatEngine() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            // Add initial log
            addHeartbeatLog("🟢 SmartXerox Active - Printers Ready")
            while (true) {
                for (i in 30 downTo 1) {
                    _nextHeartbeatSeconds.value = i
                    delay(1000)
                }
                if (_isServiceRunning.value) {
                    val allPrintersList = database.dao().getAllPrintersDirect()
                    val primaryPrinter = allPrintersList.find { it.isPrimary }
                    val activePrintersCount = allPrintersList.count { it.status == "READY" }
                    
                    if (primaryPrinter != null && primaryPrinter.status == "READY") {
                        addHeartbeatLog("🟢 Heartbeat sent: Shop Active | Primary Printer: ${primaryPrinter.name} (Online) | $activePrintersCount total ready printers.")
                    } else if (activePrintersCount > 0) {
                        val firstFallback = allPrintersList.find { !it.isPrimary && it.status == "READY" }
                        addHeartbeatLog("🟡 Heartbeat: Primary printer offline! Routing prints to Fallback Printer: ${firstFallback?.name ?: "Epson"}")
                    } else {
                        addHeartbeatLog("🔴 Heartbeat warning: No working printers connected! Shop is Offline.")
                    }
                } else {
                    addHeartbeatLog("⚫ Heartbeat offline: Enable service to start receiving prints.")
                }
            }
        }
    }

    private fun addHeartbeatLog(message: String) {
        val currentTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val formattedLog = "[$currentTime] $message"
        val currentLogs = _heartbeatLogs.value.toMutableList()
        currentLogs.add(0, formattedLog) // Insert at top
        if (currentLogs.size > 50) currentLogs.removeAt(currentLogs.size - 1)
        _heartbeatLogs.value = currentLogs
    }

    // Rate Customization by Shop Owners
    fun updateServiceRate(id: String, name: String, rate: Double, category: String, customRate: Double? = null) {
        viewModelScope.launch {
            repository.updateServiceRate(ServiceRate(id, name, rate, category, customRate))
            addHeartbeatLog("⚙️ Price Updated for $name: Custom Rate = ₹${customRate ?: "Default ($rate)"}")
        }
    }

    // Printer settings
    fun updatePrinterConfig(config: PrinterConfig) {
        viewModelScope.launch {
            repository.updatePrinterConfig(config)
            addHeartbeatLog("⚙️ Classic Printer config updated: ${config.model} (${config.ipAddress})")
        }
    }

    // Admin Settings
    fun updateAdminSettings(settings: AdminSettings) {
        viewModelScope.launch {
            repository.updateAdminSettings(settings)
            addHeartbeatLog("👑 Admin settings updated: Override custom rates: ${settings.overrideCustomRates}")
        }
    }

    // Multiple Printers Operations
    fun addPrinter(name: String, ipAddress: String, status: String = "READY", isPrimary: Boolean = false) {
        viewModelScope.launch {
            val allPrintersList = database.dao().getAllPrintersDirect()
            // If this is set to primary, unset previous primary
            if (isPrimary) {
                allPrintersList.forEach {
                    if (it.isPrimary) {
                        repository.updatePrinter(it.copy(isPrimary = false))
                    }
                }
            }
            repository.insertPrinter(Printer(name = name, ipAddress = ipAddress, status = status, isPrimary = isPrimary))
            addHeartbeatLog("🖨️ Added Printer: $name ($ipAddress)")
        }
    }

    fun updatePrinterStatus(printer: Printer, newStatus: String) {
        viewModelScope.launch {
            repository.updatePrinter(printer.copy(status = newStatus))
            addHeartbeatLog("🖨️ Printer status changed: ${printer.name} -> $newStatus")
        }
    }

    fun setPrimaryPrinter(printer: Printer) {
        viewModelScope.launch {
            val allPrintersList = database.dao().getAllPrintersDirect()
            allPrintersList.forEach {
                if (it.id == printer.id) {
                    repository.updatePrinter(it.copy(isPrimary = true))
                } else if (it.isPrimary) {
                    repository.updatePrinter(it.copy(isPrimary = false))
                }
            }
            addHeartbeatLog("🖨️ Primary Printer set to: ${printer.name}")
        }
    }

    fun deletePrinter(id: Int) {
        viewModelScope.launch {
            repository.deletePrinterById(id)
            addHeartbeatLog("🖨️ Deleted printer ID: $id")
        }
    }

    // Recharge Credit System
    fun buyRechargePack(pack: RechargePack) {
        viewModelScope.launch {
            val currentCredits = database.dao().getOwnerCreditsDirect() ?: OwnerCredits(balance = 100)
            val updatedCredits = currentCredits.copy(balance = currentCredits.balance + pack.credits)
            repository.updateOwnerCredits(updatedCredits)
            
            val tx = Transaction(
                id = "#TX" + (100000 + (Math.random() * 900000).toInt()),
                packName = pack.name,
                amount = pack.price,
                creditsAdded = pack.credits,
                timestamp = System.currentTimeMillis(),
                status = "SUCCESS"
            )
            repository.insertTransaction(tx)
            addHeartbeatLog("💳 Purchased Pack: ${pack.name}. Credits Added: ${pack.credits}. New Balance: ${updatedCredits.balance}")
        }
    }

    // Admin Add Pack
    fun addRechargePack(id: String, name: String, price: Double, credits: Int, description: String) {
        viewModelScope.launch {
            repository.insertRechargePack(RechargePack(id, name, price, credits, description))
        }
    }

    fun deleteRechargePack(id: String) {
        viewModelScope.launch {
            repository.deleteRechargePack(id)
        }
    }

    // Rotation Control
    fun rotatePrint() {
        _printRotation.value = (_printRotation.value + 90f) % 360f
    }

    fun toggleUniversalPreview(show: Boolean) {
        _showUniversalPreview.value = show
    }

    // Select a Service from customer portal
    fun selectService(service: ServiceRate) {
        _selectedService.value = service
        // Reset wizard
        _photoBase64.value = null
        _isProcessingAI.value = false
        _showPreview.value = false
        _showSheetGrid.value = false
        _photoScale.value = 1f
        _photoOffsetX.value = 0f
        _photoOffsetY.value = 0f
        _printRotation.value = 0f
        _showUniversalPreview.value = false
    }

    fun clearSelectedService() {
        _selectedService.value = null
        _showUniversalPreview.value = false
    }

    // Customer uploads mock photo / select avatar
    fun selectMockPhoto(photoId: String) {
        _photoBase64.value = photoId
        startAIProcessing()
    }

    private fun startAIProcessing() {
        _isProcessingAI.value = true
        _processingStep.value = "1. चेहऱ्याची ओळख करत आहे... (Detecting Face)"
        
        viewModelScope.launch {
            delay(1000)
            _processingStep.value = "2. केस आणि खांद्यांच्या कडा वेगळ्या करत आहे... (Isolating Edges)"
            delay(1000)
            _processingStep.value = "3. पांढरा बॅकग्राउंड सेट करत आहे... (Replacing Background)"
            delay(800)
            _processingStep.value = "4. परफेक्ट पासपोर्ट आकारात क्रॉप करत आहे... (Final Cropping: 413x531 px)"
            delay(600)
            _isProcessingAI.value = false
            _showPreview.value = true
            startCountdownTimer()
        }
    }

    private fun startCountdownTimer() {
        countdownJob?.cancel()
        _countdownSeconds.value = 3
        countdownJob = viewModelScope.launch {
            while (_countdownSeconds.value > 0) {
                delay(1000)
                _countdownSeconds.value -= 1
            }
            confirmPassportLayout()
        }
    }

    // Manual Adjustments
    fun adjustPhotoScale(scale: Float) {
        countdownJob?.cancel() // Pause auto confirmation
        _photoScale.value = scale
    }

    fun adjustPhotoOffset(x: Float, y: Float) {
        countdownJob?.cancel()
        _photoOffsetX.value = x
        _photoOffsetY.value = y
    }

    fun confirmPassportLayout() {
        countdownJob?.cancel()
        _showPreview.value = false
        _showSheetGrid.value = true
    }

    fun openPayment() {
        _showPaymentDialog.value = true
    }

    fun cancelPayment() {
        _showPaymentDialog.value = false
    }

    // Get price based on owner settings & admin overrides
    fun getEffectivePrice(service: ServiceRate, settings: AdminSettings): Double {
        return if (settings.overrideCustomRates) {
            service.rate
        } else {
            service.ownerCustomRate ?: service.rate
        }
    }

    // Simulate UPI Payment
    fun payAndPrint(paymentApp: String) {
        _isPaymentProcessing.value = true
        viewModelScope.launch {
            delay(1500) // Razorpay processing
            
            val service = _selectedService.value ?: return@launch
            val settings = database.dao().getAdminSettingsDirect() ?: AdminSettings()
            
            val effectivePrice = getEffectivePrice(service, settings)
            val orderId = "#SX" + (10000 + (Math.random() * 90000).toInt())
            _currentCreatedOrderId.value = orderId
            
            // Get Admin Commission
            val commission = settings.rechargeRate
            
            val order = PrintOrder(
                orderId = orderId,
                serviceId = service.id,
                serviceName = service.name,
                customerPhotoBase64 = _photoBase64.value,
                isAIGenerated = service.id == "passport_photo_ai",
                price = effectivePrice,
                commissionPaid = commission,
                status = "PAID"
            )
            
            repository.insertOrder(order)
            
            // Increment admin accumulated royalty
            repository.updateAdminSettings(settings.copy(
                totalCommissionEarned = settings.totalCommissionEarned + commission
            ))
            
            _isPaymentProcessing.value = false
            _showPaymentDialog.value = false
            
            // Clear selections in wizard
            _selectedService.value = null
            _showUniversalPreview.value = false
            
            // Trigger auto print queue processing!
            processOrderPrint(order)
        }
    }

    // Order Processing Engine (Simulating Foreground Service FCM Receive & Print)
    private fun processOrderPrint(order: PrintOrder) {
        viewModelScope.launch {
            // Check if Foreground Service is alive
            if (!_isServiceRunning.value) {
                addHeartbeatLog("❌ Received Print Request for ${order.orderId}, but Service is PAUSED! Storing order as FAILED.")
                val failedOrder = order.copy(
                    status = "FAILED",
                    errorMessage = "Shop App Service was turned off by Owner."
                )
                repository.updateOrder(failedOrder)
                return@launch
            }

            // Deduct Owner Credit!
            val currentCredits = database.dao().getOwnerCreditsDirect() ?: OwnerCredits(balance = 100)
            if (currentCredits.balance <= 0) {
                addHeartbeatLog("❌ Received Print Request ${order.orderId} but shop has insufficient credits (${currentCredits.balance})!")
                val failedOrder = order.copy(
                    status = "FAILED",
                    errorMessage = "Insufficient credits in Shop Owner balance. Please recharge."
                )
                repository.updateOrder(failedOrder)
                _activePrintJob.value = failedOrder
                delay(2000)
                _activePrintJob.value = null
                return@launch
            }

            // Deduct credit
            repository.updateOwnerCredits(currentCredits.copy(balance = currentCredits.balance - 1))
            addHeartbeatLog("🪙 Deducted 1 credit for Order ${order.orderId}. Remaining Balance: ${currentCredits.balance - 1}")

            _activePrintJob.value = order
            _printJobStatus.value = "FCM Notification: नवीन ऑर्डर प्राप्त! फाईल डाउनलोड होत आहे..."
            _printJobProgress.value = 0.1f
            addHeartbeatLog("📲 FCM Notification: New Order ${order.orderId} received from Server!")
            delay(1200)

            _printJobStatus.value = "S3 मधून प्रिंट फाईल डाउनलोड करत आहे..."
            _printJobProgress.value = 0.3f
            delay(1000)

            val printersList = database.dao().getAllPrintersDirect()
            val primaryPrinter = printersList.find { it.isPrimary }
            
            // Try printing on primary printer
            if (primaryPrinter != null && primaryPrinter.status == "READY") {
                _printJobStatus.value = "IPP द्वारे ${primaryPrinter.name} शी कनेक्ट करत आहे (${primaryPrinter.ipAddress})..."
                _printJobProgress.value = 0.5f
                delay(1200)
                
                _printJobStatus.value = "प्रिंट करत आहे (Printing document)..."
                _printJobProgress.value = 0.8f
                delay(2000)
                
                val completedOrder = order.copy(status = "COMPLETED", printerUsedIp = primaryPrinter.ipAddress)
                repository.updateOrder(completedOrder)
                _activePrintJob.value = completedOrder
                _printJobProgress.value = 1.0f
                _printJobStatus.value = "प्रिंट यशस्वी झाली! (Job Success)"
                addHeartbeatLog("🖨️ Printer Job Completed successfully on ${primaryPrinter.name} (${primaryPrinter.ipAddress})!")
                delay(1500)
            } else {
                // Secondary fallback routing
                val fallbackPrinter = printersList.find { !it.isPrimary && it.status == "READY" }
                if (fallbackPrinter != null) {
                    addHeartbeatLog("⚠️ Primary printer ${primaryPrinter?.name ?: "Main"} is OFFLINE! Auto-routing print job ${order.orderId} to Epson/Secondary Fallback Printer...")
                    _printJobStatus.value = "मुख्य प्रिंटर ऑफलाइन आहे! फॉलबॅक प्रिंटरकडे वळवत आहे (${fallbackPrinter.name})..."
                    _printJobProgress.value = 0.5f
                    delay(1500)

                    _printJobStatus.value = "IPP द्वारे फॉलबॅक प्रिंटरशी कनेक्ट करत आहे (${fallbackPrinter.ipAddress})..."
                    _printJobProgress.value = 0.7f
                    delay(1200)

                    _printJobStatus.value = "${fallbackPrinter.name} वर प्रिंट होत आहे..."
                    _printJobProgress.value = 0.9f
                    delay(2000)

                    val completedOrder = order.copy(status = "COMPLETED", printerUsedIp = fallbackPrinter.ipAddress)
                    repository.updateOrder(completedOrder)
                    _activePrintJob.value = completedOrder
                    _printJobProgress.value = 1.0f
                    _printJobStatus.value = "प्रिंट यशस्वी (${fallbackPrinter.name} फॉलबॅक द्वारे)!"
                    addHeartbeatLog("🖨️ Printer Job Completed successfully on Fallback ${fallbackPrinter.name} (${fallbackPrinter.ipAddress})!")
                    delay(1500)
                } else {
                    // FAILURE MODE (No printer ready)
                    addHeartbeatLog("❌ ERROR: All printers are offline/unavailable! Print job ${order.orderId} failed.")
                    _printJobStatus.value = "सर्व प्रिंटर बंद आहेत! प्रिंट अयशस्वी."
                    _printJobProgress.value = 0.0f
                    
                    val failedOrder = order.copy(
                        status = "FAILED",
                        errorMessage = "Both primary and fallback printers were unavailable."
                    )
                    repository.updateOrder(failedOrder)
                    _activePrintJob.value = failedOrder
                    delay(2000)
                }
            }
            
            _activePrintJob.value = null
            _printJobProgress.value = 0f
            _printJobStatus.value = ""
        }
    }

    fun toggleOverrideCustomRates(override: Boolean) {
        viewModelScope.launch {
            val settings = database.dao().getAdminSettingsDirect() ?: AdminSettings()
            repository.updateAdminSettings(settings.copy(overrideCustomRates = override))
            addHeartbeatLog("👑 Admin settings: Override Custom Rates set to $override")
        }
    }

    // Clear simulated logs & orders to reset
    fun resetSimulator() {
        viewModelScope.launch {
            repository.clearAllOrders()
            // Reset Admin totals
            val settings = database.dao().getAdminSettingsDirect() ?: AdminSettings()
            repository.updateAdminSettings(settings.copy(totalCommissionEarned = 0.0))
            repository.updateOwnerCredits(OwnerCredits(balance = 100))
            _heartbeatLogs.value = emptyList()
            addHeartbeatLog("🔄 System reset completed. Heartbeat restarted.")
        }
    }
}
