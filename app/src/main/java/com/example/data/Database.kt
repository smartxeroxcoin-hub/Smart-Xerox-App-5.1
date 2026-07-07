package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "service_rates")
data class ServiceRate(
    @PrimaryKey val id: String,
    val name: String,
    val rate: Double,
    val category: String,
    val ownerCustomRate: Double? = null
)

@Entity(tableName = "admin_settings")
data class AdminSettings(
    @PrimaryKey val id: Int = 1,
    val rechargeRate: Double = 0.0, // Deprecated commission rate (always 0.0 now)
    val totalCommissionEarned: Double = 0.0, // Deprecated royalty
    val overrideCustomRates: Boolean = false,
    
    // New fields: Credentials, manual UPI and customizable plan prices
    val adminUsername: String = "admin",
    val adminPassword: String = "admin123",
    val adminUpiId: String = "admin@upi",
    val plan1MonthPrice: Double = 299.0,
    val plan3MonthPrice: Double = 849.0,
    val plan6MonthPrice: Double = 1649.0,
    val plan12MonthPrice: Double = 3149.0,
    val customLogoUri: String? = null,
    val cloudApiEndpoint: String = "https://api.smartxerox.co.in",
    val cloudDomain: String = "smartxerox.co.in",
    val hostingPlatform: String = "Firebase Hosting / Vercel",
    val sslEnabled: Boolean = true,
    val webhookUrl: String = "https://webhook.smartxerox.co.in/notify"
)

@Entity(tableName = "print_orders")
data class PrintOrder(
    @PrimaryKey val orderId: String,
    val serviceId: String,
    val serviceName: String,
    val customerPhotoBase64: String? = null, // Simulated photo
    val isAIGenerated: Boolean = false,
    val price: Double,
    val commissionPaid: Double,
    val timestamp: Long = System.currentTimeMillis(),
    var status: String, // PENDING, PAID, PRINTING, COMPLETED, FAILED
    val errorMessage: String? = null,
    val printerUsedIp: String? = null
)

@Entity(tableName = "printer_config")
data class PrinterConfig(
    @PrimaryKey val id: Int = 1,
    val ipAddress: String = "192.168.1.100",
    val model: String = "HP Laserjet Pro M404dw",
    val isPrinterConnected: Boolean = true,
    val isMainPrinterWorking: Boolean = true,
    val fallbackIpAddress: String = "192.168.1.101",
    val fallbackModel: String = "Epson L3150 Duplex",
    val isFallbackActive: Boolean = true
)

@Entity(tableName = "printers")
data class Printer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val ipAddress: String,
    val status: String = "READY", // READY, PAPER_JAM, OUT_OF_INK, OFFLINE
    val isPrimary: Boolean = false
)

@Entity(tableName = "recharge_packs")
data class RechargePack(
    @PrimaryKey val id: String,
    val name: String,
    val price: Double,
    val credits: Int,
    val description: String
)

@Entity(tableName = "owner_credits")
data class OwnerCredits(
    @PrimaryKey val id: Int = 1,
    val balance: Int = 100, // Deprecated balance
    val subscriptionStatus: String = "ACTIVE", // ACTIVE or EXPIRED
    val subscriptionExpires: Long = 0, // Expiry timestamp
    
    // New fields: Registration, custom UPI and custom login credentials
    val isRegistered: Boolean = false,
    val registrationDate: Long = 0,
    val ownerUpi: String = "owner@upi",
    val ownerUsername: String = "owner",
    val ownerPassword: String = "owner123"
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val id: String,
    val packName: String,
    val amount: Double,
    val creditsAdded: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SUCCESS"
)

@Dao
interface SmartXeroxDao {
    // Service Rates
    @Query("SELECT * FROM service_rates ORDER BY category, name")
    fun getAllServiceRates(): Flow<List<ServiceRate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiceRates(rates: List<ServiceRate>)

    @Update
    suspend fun updateServiceRate(rate: ServiceRate)

    // Admin Settings
    @Query("SELECT * FROM admin_settings WHERE id = 1 LIMIT 1")
    fun getAdminSettings(): Flow<AdminSettings?>

    @Query("SELECT * FROM admin_settings WHERE id = 1 LIMIT 1")
    suspend fun getAdminSettingsDirect(): AdminSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdminSettings(settings: AdminSettings)

    @Update
    suspend fun updateAdminSettings(settings: AdminSettings)

    // Print Orders
    @Query("SELECT * FROM print_orders ORDER BY timestamp DESC")
    fun getAllOrders(): Flow<List<PrintOrder>>

    @Query("SELECT * FROM print_orders ORDER BY timestamp DESC")
    suspend fun getAllOrdersDirect(): List<PrintOrder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: PrintOrder)

    @Update
    suspend fun updateOrder(order: PrintOrder)

    @Query("DELETE FROM print_orders")
    suspend fun clearAllOrders()

    // Printer Config
    @Query("SELECT * FROM printer_config WHERE id = 1 LIMIT 1")
    fun getPrinterConfig(): Flow<PrinterConfig?>

    @Query("SELECT * FROM printer_config WHERE id = 1 LIMIT 1")
    suspend fun getPrinterConfigDirect(): PrinterConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrinterConfig(config: PrinterConfig)

    @Update
    suspend fun updatePrinterConfig(config: PrinterConfig)

    // New: Multiple Printers
    @Query("SELECT * FROM printers ORDER BY isPrimary DESC, name ASC")
    fun getAllPrinters(): Flow<List<Printer>>

    @Query("SELECT * FROM printers ORDER BY isPrimary DESC, name ASC")
    suspend fun getAllPrintersDirect(): List<Printer>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrinter(printer: Printer)

    @Update
    suspend fun updatePrinter(printer: Printer)

    @Query("DELETE FROM printers WHERE id = :id")
    suspend fun deletePrinterById(id: Int)

    // New: Recharge Packs
    @Query("SELECT * FROM recharge_packs ORDER BY price ASC")
    fun getAllRechargePacks(): Flow<List<RechargePack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRechargePack(pack: RechargePack)

    @Query("DELETE FROM recharge_packs WHERE id = :id")
    suspend fun deleteRechargePack(id: String)

    // New: Owner Credits
    @Query("SELECT * FROM owner_credits WHERE id = 1 LIMIT 1")
    fun getOwnerCredits(): Flow<OwnerCredits?>

    @Query("SELECT * FROM owner_credits WHERE id = 1 LIMIT 1")
    suspend fun getOwnerCreditsDirect(): OwnerCredits?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOwnerCredits(credits: OwnerCredits)

    // New: Transactions
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(tx: Transaction)

    // New: Cloud Storage Batches
    @Query("SELECT * FROM cloud_storage_batches ORDER BY timestamp DESC")
    fun getAllCloudBatches(): Flow<List<CloudStorageBatch>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCloudBatch(batch: CloudStorageBatch)

    @Query("DELETE FROM cloud_storage_batches WHERE batchId = :id")
    suspend fun deleteCloudBatch(id: String)
}

@Entity(tableName = "cloud_storage_batches")
data class CloudStorageBatch(
    @PrimaryKey val batchId: String,
    val batchName: String,
    val photoCount: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val cloudUrl: String,
    val fileSizeKb: Int = 1240
)

@Database(
    entities = [
        ServiceRate::class,
        AdminSettings::class,
        PrintOrder::class,
        PrinterConfig::class,
        Printer::class,
        RechargePack::class,
        OwnerCredits::class,
        Transaction::class,
        CloudStorageBatch::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): SmartXeroxDao
}
