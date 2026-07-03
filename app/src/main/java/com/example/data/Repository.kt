package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SmartXeroxRepository(private val dao: SmartXeroxDao) {

    val serviceRates: Flow<List<ServiceRate>> = dao.getAllServiceRates()

    val adminSettings: Flow<AdminSettings> = dao.getAdminSettings().map { 
        it ?: AdminSettings() 
    }

    val printerConfig: Flow<PrinterConfig> = dao.getPrinterConfig().map { 
        it ?: PrinterConfig() 
    }

    val orders: Flow<List<PrintOrder>> = dao.getAllOrders()

    // New: Multiple Printers
    val printers: Flow<List<Printer>> = dao.getAllPrinters()

    // New: Recharge Packs
    val rechargePacks: Flow<List<RechargePack>> = dao.getAllRechargePacks()

    // New: Owner Credits
    val ownerCredits: Flow<OwnerCredits> = dao.getOwnerCredits().map {
        it ?: OwnerCredits(balance = 100)
    }

    // New: Transactions
    val transactions: Flow<List<Transaction>> = dao.getAllTransactions()

    suspend fun updateServiceRate(rate: ServiceRate) {
        dao.updateServiceRate(rate)
    }

    suspend fun updateAdminSettings(settings: AdminSettings) {
        dao.updateAdminSettings(settings)
    }

    suspend fun updatePrinterConfig(config: PrinterConfig) {
        dao.updatePrinterConfig(config)
    }

    suspend fun insertOrder(order: PrintOrder) {
        dao.insertOrder(order)
    }

    suspend fun updateOrder(order: PrintOrder) {
        dao.updateOrder(order)
    }

    suspend fun clearAllOrders() {
        dao.clearAllOrders()
    }

    // New: Printer management
    suspend fun insertPrinter(printer: Printer) {
        dao.insertPrinter(printer)
    }

    suspend fun updatePrinter(printer: Printer) {
        dao.updatePrinter(printer)
    }

    suspend fun deletePrinterById(id: Int) {
        dao.deletePrinterById(id)
    }

    // New: Recharge Pack management
    suspend fun insertRechargePack(pack: RechargePack) {
        dao.insertRechargePack(pack)
    }

    suspend fun deleteRechargePack(id: String) {
        dao.deleteRechargePack(id)
    }

    // New: Credits and Transaction management
    suspend fun updateOwnerCredits(credits: OwnerCredits) {
        dao.insertOwnerCredits(credits)
    }

    suspend fun insertTransaction(tx: Transaction) {
        dao.insertTransaction(tx)
    }

    suspend fun prepopulateData() {
        // Prepopulate printer configuration if not present
        if (dao.getPrinterConfigDirect() == null) {
            dao.insertPrinterConfig(PrinterConfig())
        }

        // Prepopulate admin settings if not present
        if (dao.getAdminSettingsDirect() == null) {
            dao.insertAdminSettings(AdminSettings())
        }

        // Prepopulate printers table with default printers
        val currentPrinters = dao.getAllPrintersDirect()
        if (currentPrinters.isEmpty()) {
            dao.insertPrinter(Printer(name = "HP Laserjet M404dw", ipAddress = "192.168.1.100", status = "READY", isPrimary = true))
            dao.insertPrinter(Printer(name = "Epson L3150 Duplex", ipAddress = "192.168.1.101", status = "READY", isPrimary = false))
            dao.insertPrinter(Printer(name = "Canon ImageRUNNER Backup", ipAddress = "192.168.1.102", status = "PAPER_JAM", isPrimary = false))
        }

        // Prepopulate default recharge packs
        dao.insertRechargePack(RechargePack("pack_mini", "Mini Pack 50", 100.0, 50, "योग्य व्यवसायांसाठी ५० प्रिंट क्रेडिट्स (₹2/print)"))
        dao.insertRechargePack(RechargePack("pack_pro", "Pro Pack 200", 300.0, 200, "जास्त ग्राहकांसाठी २०० प्रिंट क्रेडिट्स (₹1.5/print)"))
        dao.insertRechargePack(RechargePack("pack_unlimited", "Enterprise Pack 1000", 1000.0, 1000, "मोठ्या दुकानांसाठी १,००० प्रिंट क्रेडिट्स (₹1/print)"))

        // Prepopulate default credits
        if (dao.getOwnerCreditsDirect() == null) {
            dao.insertOwnerCredits(OwnerCredits(balance = 100))
        }

        // Prepopulate service rates if empty
        val defaultRates = listOf(
            // Category: Document Services
            ServiceRate("passport_photo_ai", "Passport Photo AI (Background White)", 30.0, "AI Services"),
            ServiceRate("a4_xerox_bw_single", "A4 Xerox Single-Side (B&W)", 2.0, "Document Services"),
            ServiceRate("a4_xerox_bw_double", "A4 Xerox Double-Side (B&W)", 3.0, "Document Services"),
            ServiceRate("a4_print_color_hq", "A4 Color Print (High Quality)", 10.0, "Document Services"),
            ServiceRate("a4_print_color_std", "A4 Color Print (Standard)", 7.0, "Document Services"),
            ServiceRate("legal_xerox_bw", "Legal Size Xerox (B&W)", 3.0, "Document Services"),
            ServiceRate("a3_print_bw", "A3 Printing (B&W)", 10.0, "Document Services"),
            ServiceRate("a3_print_color", "A3 Color Printing", 30.0, "Document Services"),
            ServiceRate("doc_scan_pdf", "Document Scanning to PDF", 5.0, "Document Services"),
            ServiceRate("scan_ocr", "Scanning & OCR Text Conversion", 15.0, "Document Services"),
            
            // Category: Binding & Lamination
            ServiceRate("lamination_a4", "Lamination (A4 Size)", 20.0, "Binding & Lamination"),
            ServiceRate("spiral_binding_100", "Spiral Binding (upto 100 pages)", 40.0, "Binding & Lamination"),
            ServiceRate("soft_binding", "Soft Binding", 60.0, "Binding & Lamination"),
            ServiceRate("thesis_hard_binding", "Thesis Hard Binding", 300.0, "Binding & Lamination"),
            ServiceRate("project_report_bind", "Project Report Print & Bind", 150.0, "Binding & Lamination"),
            
            // Category: Cards & Prints
            ServiceRate("pvc_card_print", "PVC Card Printing (Aadhaar/PAN)", 50.0, "Cards & Prints"),
            ServiceRate("id_card_holder", "ID Card Printing & Holder", 40.0, "Cards & Prints"),
            ServiceRate("photo_print_4x6", "Photo Print (4x6 Inches)", 15.0, "Cards & Prints"),
            ServiceRate("photo_print_5x7", "Photo Print (5x7 Inches)", 25.0, "Cards & Prints"),
            ServiceRate("photo_print_a4", "Photo Print (A4 Glossy)", 50.0, "Cards & Prints"),
            ServiceRate("sticker_sheet", "Sticker Sheet Printing", 30.0, "Cards & Prints"),
            
            // Category: Business Services
            ServiceRate("stamp_paper_print", "Stamp Paper Printing", 50.0, "Business Services"),
            ServiceRate("resume_printing", "Resume Printing & Styling", 10.0, "Business Services"),
            ServiceRate("envelope_printing", "Envelope Printing", 5.0, "Business Services"),
            ServiceRate("certificate_glossy", "Certificate Printing (Glossy)", 30.0, "Business Services"),
            ServiceRate("greeting_card", "Greeting Card Print", 40.0, "Business Services"),
            ServiceRate("brochure_tri_fold", "Brochure Printing (Tri-fold)", 15.0, "Business Services"),
            ServiceRate("pamphlet_a5_bw", "Pamphlet Printing (A5 B&W)", 1.0, "Business Services"),
            ServiceRate("challan_printing", "Challan Printing", 5.0, "Business Services"),
            ServiceRate("bill_book_print", "Bill Book Printing", 120.0, "Business Services"),
            ServiceRate("visiting_card_100", "Visiting Card (100 Cards pack)", 150.0, "Business Services"),
            ServiceRate("barcode_label", "Barcode Label Printing", 2.0, "Business Services"),
            ServiceRate("doc_translation_print", "Document Translation Print", 100.0, "Business Services"),
            ServiceRate("mug_sublimation", "Mug Sublimation Print", 150.0, "Gifting & Merch"),
            ServiceRate("t_shirt_print", "T-Shirt Printing", 250.0, "Gifting & Merch")
        )
        
        dao.insertServiceRates(defaultRates)
    }
}
