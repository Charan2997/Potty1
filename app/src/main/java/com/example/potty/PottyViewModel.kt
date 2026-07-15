package com.example.potty

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

class PottyViewModel(application: Application, private val repository: ExpenseRepository) : AndroidViewModel(application) {

    val preferenceManager = PreferenceManager(application)
    private val securityManager = SecurityManager(application)

    // --- Security ---
    private val _isSessionLocked = MutableStateFlow(true)
    val isAppLocked: StateFlow<Boolean> = _isSessionLocked.asStateFlow()

    val securityEnabled: StateFlow<Boolean?> = preferenceManager.isNotificationEnabled(PreferenceManager.SECURITY_ENABLED)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun unlockApp() {
        _isSessionLocked.value = false
    }

    fun lockApp() {
        _isSessionLocked.value = true
    }

    // --- Active Profile ---
    val userProfile: StateFlow<UserProfileEntity?> = repository.activeProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val currentGoogleId: String
        get() = userProfile.value?.googleId ?: "local_user"

    // --- Expenses ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val expenses: StateFlow<List<ExpenseEntity>> = userProfile
        .flatMapLatest { profile ->
            if (profile?.googleId != null && profile.googleId != "local_user") {
                repository.getAllExpenses(profile.googleId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
    private val txIdFormat = SimpleDateFormat("ddMMyyyyHHmmss", Locale.getDefault())

    fun addExpense(
        amount: Double,
        category: String,
        subcategory: String,
        description: String,
        isIncome: Boolean = false,
        iconName: String = "",
        tags: String = "",
        paymentMode: String = "UPI"
    ) {
        viewModelScope.launch {
            val newExpense = ExpenseEntity(
                googleId = currentGoogleId,
                timestamp = dateFormat.format(Date()),
                amount = amount,
                primaryCategory = category,
                subcategory = subcategory,
                description = description,
                tags = tags,
                isIncome = isIncome,
                iconName = iconName,
                paymentMode = paymentMode
            )
            repository.insertExpense(newExpense)
        }
    }

    fun updateExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.insertExpense(expense)
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    // --- Subscriptions ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val subscriptions: StateFlow<List<SubscriptionEntity>> = userProfile
        .flatMapLatest { profile ->
            if (profile?.googleId != null && profile.googleId != "local_user") {
                repository.getAllSubscriptions(profile.googleId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addSubscription(
        name: String,
        cost: Double,
        interval: String,
        validUntil: String,
        category: String = "Entertainment",
        isTrading: Boolean = false,
        tradingType: String = ""
    ) {
        viewModelScope.launch {
            val sub = SubscriptionEntity(
                googleId = currentGoogleId,
                name = name,
                cost = cost,
                renewalInterval = interval,
                validUntilDate = validUntil,
                isAutoRenewing = true,
                category = category,
                isTrading = isTrading,
                tradingType = tradingType
            )
            repository.insertSubscription(sub)
        }
    }

    fun updateSubscription(subscription: SubscriptionEntity) {
        viewModelScope.launch {
            repository.insertSubscription(subscription)
        }
    }

    fun deleteSubscription(subscription: SubscriptionEntity) {
        viewModelScope.launch {
            repository.deleteSubscription(subscription)
        }
    }

    // --- College Fees ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val collegeFees: StateFlow<List<CollegeFeeEntity>> = userProfile
        .flatMapLatest { profile ->
            if (profile?.googleId != null && profile.googleId != "local_user") {
                repository.getAllFees(profile.googleId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateAllFees(
        tuition: Double,
        hostel: Double,
        inflation: Double,
        hostelComp: Boolean
    ) {
        viewModelScope.launch {
            val gid = currentGoogleId
            repository.deleteAllFees(gid)
            
            (1..4).forEach { year ->
                val tMultiplier = (1.0 + (inflation / 100.0)).pow(year - 1)
                val tCalculated = tuition * tMultiplier
                
                val tuitionEntity = CollegeFeeEntity(
                    googleId = gid,
                    id = "TUITION_${gid}_$year",
                    feeType = "Tuition Fee",
                    collegeYear = year,
                    baseAmount = tuition,
                    calculatedAmount = tCalculated,
                    inflationRate = inflation,
                    isPaid = false,
                    isHostelCompounded = true
                )
                repository.insertFee(tuitionEntity)

                val hCalculated = if (hostelComp) hostel * tMultiplier else hostel

                val hostelEntity = CollegeFeeEntity(
                    googleId = gid,
                    id = "HOSTEL_${gid}_$year",
                    feeType = "Hostel Fee",
                    collegeYear = year,
                    baseAmount = hostel,
                    calculatedAmount = hCalculated,
                    inflationRate = inflation,
                    isPaid = false,
                    isHostelCompounded = hostelComp
                )
                repository.insertFee(hostelEntity)
            }
        }
    }

    fun toggleFeePaid(fee: CollegeFeeEntity) {
        viewModelScope.launch {
            repository.insertFee(fee.copy(isPaid = !fee.isPaid))
        }
    }

    fun deleteCollegeFee(fee: CollegeFeeEntity) {
        viewModelScope.launch {
            repository.deleteFee(fee)
        }
    }

    // --- Profile Actions ---
    fun updateProfile(profile: UserProfileEntity) {
        viewModelScope.launch {
            repository.updateProfile(profile)
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData(currentGoogleId)
        }
    }

    fun setNotificationEnabled(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.setNotificationEnabled(key, enabled)
        }
    }

    // --- Statement Export (PDF) ---
    fun exportStatement(context: Context) {
        viewModelScope.launch {
            val gid = currentGoogleId
            val allExpenses = repository.getAllExpenses(gid).first()
            val allSubs = repository.getAllSubscriptions(gid).first() // Include all, including trading
            val allPaidFees = repository.getAllFees(gid).first().filter { it.isPaid }
            val profile = userProfile.value ?: UserProfileEntity()

            // Prepare common transaction list
            data class StatementItem(
                val date: String,
                val narration: String,
                val txId: String,
                val valueDate: String,
                val withdrawal: Double?,
                val deposit: Double?,
                val rawDate: Date
            )

            val items = mutableListOf<StatementItem>()

            allExpenses.forEach { exp ->
                val dateObj = try { dateFormat.parse(exp.timestamp) ?: Date() } catch (e: Exception) { Date() }
                items.add(StatementItem(
                    date = displayDateFormat.format(dateObj),
                    narration = exp.description,
                    txId = txIdFormat.format(dateObj).padStart(15, '0'),
                    valueDate = displayDateFormat.format(dateObj),
                    withdrawal = if (!exp.isIncome) exp.amount else null,
                    deposit = if (exp.isIncome) exp.amount else null,
                    rawDate = dateObj
                ))
            }

            allSubs.forEach { sub ->
                val subDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dateObj = try { subDateFormat.parse(sub.validUntilDate) ?: Date() } catch (e: Exception) { Date() }
                val prefix = if (sub.isTrading) "TRADING" else "SUB"
                items.add(StatementItem(
                    date = displayDateFormat.format(dateObj),
                    narration = "$prefix: ${sub.name} (${sub.renewalInterval})",
                    txId = txIdFormat.format(dateObj).padStart(15, '0'),
                    valueDate = displayDateFormat.format(dateObj),
                    withdrawal = sub.cost,
                    deposit = null,
                    rawDate = dateObj
                ))
            }

            allPaidFees.forEach { fee ->
                val dateObj = Date()
                items.add(StatementItem(
                    date = displayDateFormat.format(dateObj),
                    narration = "COLLEGE FEE: ${fee.feeType} (Year ${fee.collegeYear})",
                    txId = txIdFormat.format(dateObj).padStart(15, '0'),
                    valueDate = displayDateFormat.format(dateObj),
                    withdrawal = fee.calculatedAmount,
                    deposit = null,
                    rawDate = dateObj
                ))
            }

            val sortedItems = items.sortedBy { it.rawDate }

            // PDF Generation
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            
            val textPaint = Paint().apply {
                textSize = 9f
                color = Color.BLACK
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val titlePaint = Paint().apply {
                textSize = 14f
                color = Color.BLACK
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val boldPaint = Paint().apply {
                textSize = 9f
                color = Color.BLACK
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 0.5f
            }

            // --- Header Box ---
            canvas.drawRect(30f, 40f, 280f, 180f, Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.BLACK })
            var hY = 60f
            canvas.drawText("USER : ${profile.fullName.uppercase()}", 40f, hY, boldPaint)
            hY += 15f
            canvas.drawText("COLLEGE : ${profile.college.uppercase()}", 40f, hY, textPaint)
            hY += 15f
            canvas.drawText("COURSE : ${profile.course.uppercase()}", 40f, hY, textPaint)
            hY += 15f
            canvas.drawText("ACADEMIC YEAR : ${profile.currentYear}", 40f, hY, textPaint)
            hY += 30f
            canvas.drawText("JOINT HOLDERS : N/A", 40f, hY, textPaint)

            var rHY = 60f
            canvas.drawText("Account App  : POTTY FINANCE", 320f, rHY, textPaint)
            rHY += 15f
            canvas.drawText("Cust ID      : ${Math.abs(profile.googleId.hashCode()).toString().take(8)}", 320f, rHY, textPaint)
            rHY += 15f
            canvas.drawText("Currency     : INR", 320f, rHY, textPaint)
            rHY += 15f
            canvas.drawText("Account Status: ACTIVE", 320f, rHY, textPaint)
            rHY += 15f
            canvas.drawText("Branch Code  : NET", 320f, rHY, textPaint)
            rHY += 15f
            canvas.drawText("Account Type : PERSONAL LEDGER", 320f, rHY, textPaint)

            canvas.drawText("From : ${if (sortedItems.isNotEmpty()) sortedItems.first().date else "N/A"}", 40f, 210f, textPaint)
            canvas.drawText("To : ${if (sortedItems.isNotEmpty()) sortedItems.last().date else "N/A"}", 120f, 210f, textPaint)
            canvas.drawText("Statement of account", 230f, 210f, titlePaint)

            val tHY = 240f
            val tBP = Paint().apply { color = Color.parseColor("#E0F7FA") }
            canvas.drawRect(30f, tHY - 15f, 565f, tHY + 10f, tBP)
            
            canvas.drawText("Date", 35f, tHY, boldPaint)
            canvas.drawText("Narration", 85f, tHY, boldPaint)
            canvas.drawText("Chq./Ref.No.", 235f, tHY, boldPaint)
            canvas.drawText("Value Dt", 350f, tHY, boldPaint)
            canvas.drawText("Withdrawal Amt.", 410f, tHY, boldPaint)
            canvas.drawText("Deposit Amt.", 495f, tHY, boldPaint)

            var cY = 270f
            var totalDebits = 0.0
            var totalCredits = 0.0
            var drCount = 0
            var crCount = 0
            var runningBalance = 0.0

            sortedItems.forEach { item ->
                if (item.deposit != null) {
                    runningBalance += item.deposit
                    totalCredits += item.deposit
                    crCount++
                }
                if (item.withdrawal != null) {
                    runningBalance -= item.withdrawal
                    totalDebits += item.withdrawal
                    drCount++
                }

                canvas.drawText(item.date, 35f, cY, textPaint)
                val nar = if (item.narration.length > 28) item.narration.take(25) + "..." else item.narration
                canvas.drawText(nar, 85f, cY, textPaint)
                canvas.drawText(item.txId, 235f, cY, Paint(textPaint).apply { textSize = 8f })
                canvas.drawText(item.valueDate, 350f, cY, textPaint)
                
                if (item.withdrawal != null) canvas.drawText(String.format(Locale.US, "%,.2f", item.withdrawal), 410f, cY, textPaint)
                if (item.deposit != null) canvas.drawText(String.format(Locale.US, "%,.2f", item.deposit), 495f, cY, textPaint)
                
                canvas.drawLine(30f, cY + 5f, 565f, cY + 5f, linePaint)
                cY += 22f

                if (cY > 700f) return@forEach 
            }

            cY += 30f
            canvas.drawText("STATEMENT SUMMARY :-", 40f, cY, boldPaint)
            cY += 20f
            canvas.drawText("Opening Balance", 40f, cY, textPaint)
            canvas.drawText("Dr Count", 180f, cY, textPaint)
            canvas.drawText("Cr Count", 260f, cY, textPaint)
            canvas.drawText("Debits", 340f, cY, textPaint)
            canvas.drawText("Credits", 420f, cY, textPaint)
            canvas.drawText("Closing Bal", 500f, cY, textPaint)

            cY += 15f
            canvas.drawText("0.00", 40f, cY, boldPaint)
            canvas.drawText(drCount.toString(), 180f, cY, boldPaint)
            canvas.drawText(crCount.toString(), 260f, cY, boldPaint)
            canvas.drawText(String.format(Locale.US, "%,.2f", totalDebits), 340f, cY, boldPaint)
            canvas.drawText(String.format(Locale.US, "%,.2f", totalCredits), 420f, cY, boldPaint)
            canvas.drawText(String.format(Locale.US, "%,.2f", runningBalance), 500f, cY, boldPaint)

            cY += 40f
            canvas.drawText("Statement Generated On: ${SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.getDefault()).format(Date())}", 40f, cY, Paint(textPaint).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC) })

            val fY = 800f
            canvas.drawLine(30f, fY - 10f, 565f, fY - 10f, linePaint)
            canvas.drawText("Generated On: ${SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.getDefault()).format(Date())}", 40f, fY, Paint(textPaint).apply { textSize = 8f })
            canvas.drawText("Generated By: ${profile.fullName}", 250f, fY, Paint(textPaint).apply { textSize = 8f })
            canvas.drawText("Requesting Branch Code: NET", 440f, fY, Paint(textPaint).apply { textSize = 8f })

            pdfDocument.finishPage(page)

            try {
                val f = File(context.cacheDir, "Potty_Statement_${System.currentTimeMillis()}.pdf")
                pdfDocument.writeTo(FileOutputStream(f))
                pdfDocument.close()
                val u = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                val sI = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_SUBJECT, "Potty Account Statement")
                    putExtra(Intent.EXTRA_STREAM, u)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(sI, "Download Statement"))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}

class PottyViewModelFactory(private val application: Application, private val repository: ExpenseRepository) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PottyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PottyViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
