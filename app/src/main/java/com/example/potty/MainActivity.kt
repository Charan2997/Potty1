package com.example.potty

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.fragment.app.FragmentActivity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import coil.compose.AsyncImage
import com.example.potty.ui.theme.PottyTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : FragmentActivity() {
    private val viewModel: PottyViewModel by viewModels {
        PottyViewModelFactory(application, (application as PottyApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Notification Channels
        NotificationHelper(this)

        setContent {
            val isDarkMode by viewModel.preferenceManager.isNotificationEnabled(PreferenceManager.IS_DARK_MODE).collectAsState(initial = isSystemInDarkTheme())
            PottyTheme(darkTheme = isDarkMode) {
                val profile by viewModel.userProfile.collectAsState()
                val securityEnabled by viewModel.securityEnabled.collectAsState()
                val isLocked by viewModel.isAppLocked.collectAsState()
                
                when {
                    securityEnabled == null -> {
                        // Loading state / Splash
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                            Icon(painterResource(id = R.drawable.ic_app_logo), null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    profile == null || profile?.googleId == "local_user" -> {
                        // Always show login if no profile exists or legacy local_user exists
                        LoginScreen(viewModel)
                    }
                    securityEnabled == true && isLocked -> {
                        LockScreen(viewModel, this)
                    }
                    else -> {
                        MainContent(viewModel)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.lockApp()
    }
}

@Composable
fun LockScreen(viewModel: PottyViewModel, activity: FragmentActivity) {
    val securityManager = remember { SecurityManager(activity) }

    LaunchedEffect(Unit) {
        if (securityManager.canAuthenticate()) {
            securityManager.showNativeAuthPrompt(activity,
                onSuccess = { viewModel.unlockApp() },
                onError = { /* Handle fatal error */ }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_app_logo),
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text("Potty is Locked", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text("Unlock to access your dashboard", color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                securityManager.showNativeAuthPrompt(activity,
                    onSuccess = { viewModel.unlockApp() },
                    onError = {}
                )
            },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Default.LockOpen, null)
            Text("Unlock App", modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
fun LoginScreen(viewModel: PottyViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = CredentialManager.create(context)
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_app_logo),
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text("Welcome to Potty", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text("Track your finances with ease.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                fontSize = 12.sp
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = {
                coroutineScope.launch {
                    errorMessage = null
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    try {
                        val result = credentialManager.getCredential(context = context, request = request)
                        val credential = result.credential
                        
                        if (BuildConfig.DEBUG) Log.d("PottyAuth", "Credential received: ${credential.type}")
                        
                        if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                            if (BuildConfig.DEBUG) Log.d("PottyAuth", "Login successful")
                            viewModel.updateProfile(UserProfileEntity(
                                googleId = googleIdToken.id, // Real Google ID as Key
                                fullName = googleIdToken.displayName ?: "Google User",
                                college = "My University",
                                course = "Design",
                                currentYear = 1,
                                profilePicUri = googleIdToken.profilePictureUri?.toString()
                            ))
                        } else {
                            errorMessage = "Unexpected login result. Try again."
                        }
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.e("PottyAuth", "Login failed! Message: ${e.message}")
                        if (e.message?.contains("28444") == true) {
                            errorMessage = "Setup Error: You must add your email to 'Test Users' in the Google Cloud Console."
                        } else {
                            errorMessage = "Login failed: ${e.localizedMessage ?: "Unknown error"}"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Login, contentDescription = null)
            Text("Login with Google", modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(viewModel: PottyViewModel) {
    var navigationStack = remember { mutableStateListOf<Screen>(Screen.Dashboard) }
    val activeScreen = navigationStack.last()

    Scaffold(
        bottomBar = {
            if (activeScreen in listOf(Screen.Dashboard, Screen.Expenses, Screen.Subscriptions, Screen.CollegeFees, Screen.Profile)) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    listOf(Screen.Dashboard, Screen.Expenses, Screen.Subscriptions, Screen.CollegeFees, Screen.Profile).forEach { screen ->
                        NavigationBarItem(
                            icon = { 
                                if (screen == Screen.Dashboard) {
                                    Icon(painterResource(id = R.drawable.ic_app_logo), contentDescription = screen.title, modifier = Modifier.size(24.dp))
                                } else {
                                    Icon(screen.icon, contentDescription = screen.title) 
                                }
                            },
                            label = { Text(screen.title, fontSize = 10.sp) },
                            selected = activeScreen == screen,
                            onClick = { 
                                navigationStack.clear()
                                navigationStack.add(screen)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).background(MaterialTheme.colorScheme.background)) {
            when (activeScreen) {
                Screen.Dashboard -> DashboardScreen(viewModel)
                Screen.Expenses -> ExpenseScreen(viewModel)
                Screen.Subscriptions -> SubscriptionScreen(viewModel)
                Screen.CollegeFees -> CollegeFeeScreen(viewModel)
                Screen.Profile -> ProfileScreen(
                    viewModel = viewModel,
                    onNavigateToInfo = { navigationStack.add(Screen.PersonalInfo) },
                    onNavigateToNotifications = { navigationStack.add(Screen.Notifications) },
                    onNavigateToSecurity = { navigationStack.add(Screen.Security) }
                )
                Screen.PersonalInfo -> PersonalInfoScreen(
                    viewModel = viewModel,
                    onBack = { if (navigationStack.size > 1) navigationStack.removeAt(navigationStack.lastIndex) }
                )
                Screen.Notifications -> NotificationsScreen(
                    viewModel = viewModel,
                    onBack = { if (navigationStack.size > 1) navigationStack.removeAt(navigationStack.lastIndex) }
                )
                Screen.Security -> SecurityScreen(
                    viewModel = viewModel,
                    onBack = { if (navigationStack.size > 1) navigationStack.removeAt(navigationStack.lastIndex) }
                )
            }
        }
    }
}

enum class Screen(val title: String, val icon: ImageVector) {
    Dashboard("Home", Icons.Default.Home),
    Expenses("Expenses", Icons.Default.ShoppingCart),
    Subscriptions("Subs", Icons.Default.Refresh),
    CollegeFees("Fees", Icons.Default.School),
    Profile("Profile", Icons.Default.Person),
    PersonalInfo("Personal Info", Icons.Default.Person),
    Notifications("Notifications", Icons.Default.Notifications),
    Security("Security", Icons.Default.Security)
}

// --- DASHBOARD SCREEN ---

@Composable
fun DashboardScreen(viewModel: PottyViewModel) {
    val expenses by viewModel.expenses.collectAsState()
    val subs by viewModel.subscriptions.collectAsState()
    
    val totalIncome = expenses.filter { it.isIncome }.sumOf { it.amount }
    val totalExpense = expenses.filter { !it.isIncome }.sumOf { it.amount }
    val tradingCost = subs.filter { it.isTrading }.sumOf { it.cost }
    val regularSubsTotal = subs.filter { !it.isTrading }.sumOf { it.cost }
    
    val totalBalance = totalIncome - tradingCost - totalExpense - regularSubsTotal

    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

    val cal = java.util.Calendar.getInstance()
    cal.add(java.util.Calendar.MONTH, -1)
    val lastMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.time)

    val dailyExp = expenses.filter { !it.isIncome && it.timestamp.startsWith(todayStr) }.sumOf { it.amount }
    
    val monthlyExpense = expenses.filter { !it.isIncome && it.timestamp.startsWith(currentMonthStr) }.sumOf { it.amount }
    val monthlySubs = subs.filter { !it.isTrading && it.renewalInterval == "MONTHLY" }.sumOf { it.cost }
    val oneTimeThisMonth = subs.filter { !it.isTrading && it.renewalInterval == "ONE_TIME" && it.validUntilDate.startsWith(currentMonthStr) }.sumOf { it.cost }
    val monthlySpend = monthlyExpense + monthlySubs + oneTimeThisMonth

    val lastMonthExpense = expenses.filter { !it.isIncome && it.timestamp.startsWith(lastMonthStr) }.sumOf { it.amount }
    // Note: Comparing last month's recurring is tricky if we don't have history, but we'll use current for now
    val lastMonthSpend = lastMonthExpense + monthlySubs 

    val percentageChange = if (lastMonthSpend > 0) {
        ((monthlySpend - lastMonthSpend) / lastMonthSpend) * 100
    } else {
        0.0
    }

    var selectedCategory by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_app_logo), 
                contentDescription = "Logo", 
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onBackground
            )
            Text("Potty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // Main Balance Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Total Balance", color = Color.Gray.copy(alpha = 0.8f), fontSize = 14.sp)
                Text("₹${String.format(Locale.US, "%,.2f", totalBalance)}", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("MONTHLY SPEND", color = Color.Gray.copy(alpha = 0.8f), fontSize = 12.sp)
                        Text("₹${String.format(Locale.US, "%,.2f", monthlySpend)}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (lastMonthSpend > 0) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            val displayPercentage = String.format(Locale.US, "%.1f%%", Math.abs(percentageChange))
                            val indicator = if (percentageChange >= 0) "+" else "-"
                            val text = "$indicator$displayPercentage vs last month"
                            
                            Text(
                                text = text,
                                color = if (percentageChange > 0) Color(0xFFE57373) else if (percentageChange < 0) Color(0xFF81C784) else Color.LightGray,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), 
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Small Stats Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("DAILY EXP", "₹${String.format(Locale.US, "%,.2f", dailyExp)}", Icons.Default.TrendingDown, Modifier.weight(1f))
            StatCard("SAVINGS", "₹${String.format(Locale.US, "%,.2f", totalIncome)}", Icons.Default.Savings, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Categories", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        
        val categories = listOf(
            "Food & Mess" to Icons.Default.Restaurant,
            "Stationary" to Icons.Default.Book,
            "Travel" to Icons.Default.DirectionsCar,
            "Shopping" to Icons.Default.ShoppingBag,
            "College Expenses" to Icons.Default.School,
            "Misc" to Icons.Default.Category
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(categories) { (name, icon) ->
                CategoryItem(
                    name = name, 
                    icon = icon, 
                    isSelected = selectedCategory == name,
                    onClick = { 
                        selectedCategory = if (selectedCategory == name) null else name 
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Recent Transactions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.weight(1f))
            Text("View All", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        val filteredExpenses = expenses.filter { 
            it.timestamp.startsWith(todayStr) && (selectedCategory == null || it.primaryCategory == selectedCategory)
        }

        if (filteredExpenses.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text("No transactions for today", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredExpenses.take(5)) { expense ->
                    TransactionListItem(expense)
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, modifier = Modifier.padding(start = 4.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun CategoryItem(name: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon, 
                    contentDescription = name, 
                    modifier = Modifier.size(24.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Text(
            text = if (name.contains("&")) name.replace("&", "\n&") else name, 
            fontSize = 10.sp, 
            modifier = Modifier.padding(top = 8.dp), 
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TransactionListItem(expense: ExpenseEntity, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when (expense.primaryCategory) {
                            "Food & Mess" -> Icons.Default.Restaurant
                            "Stationary" -> Icons.Default.Book
                            "Travel" -> Icons.Default.DirectionsCar
                            "Shopping" -> Icons.Default.ShoppingBag
                            "College Expenses" -> Icons.Default.School
                            else -> Icons.Default.ShoppingCart
                        }, 
                        contentDescription = null, 
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(expense.description, fontWeight = FontWeight.Medium)
                Text("Today, 09:41 AM", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (expense.tags.isNotEmpty()) {
                    Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        expense.tags.split(",").forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(tag.uppercase(), fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
            Text(
                text = "${if (expense.isIncome) "+" else "-"}₹${expense.amount}",
                fontWeight = FontWeight.Bold,
                color = if (expense.isIncome) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// --- EXPENSES SCREEN ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(viewModel: PottyViewModel) {
    val expenses by viewModel.expenses.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var selectedExpense by remember { mutableStateOf<ExpenseEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TODAY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val todayTotal = expenses.filter { it.timestamp.startsWith(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }.sumOf { if (it.isIncome) it.amount else -it.amount }
                Text("Total:₹${String.format(Locale.US, "%.0f", todayTotal)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(expenses) { expense ->
                    TransactionListItem(expense, onClick = { selectedExpense = expense })
                }
            }
        }
        
        FloatingActionButton(
            onClick = { showAddSheet = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.onPrimary)
        }

        if (showAddSheet) {
            ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
                AddExpenseSheet(onConfirm = { amt, cat, desc, income, mode ->
                    viewModel.addExpense(amt, cat, "", desc, income, "", "", mode)
                    showAddSheet = false
                })
            }
        }

        if (selectedExpense != null) {
            ModalBottomSheet(onDismissRequest = { selectedExpense = null }) {
                EditExpenseSheet(
                    expense = selectedExpense!!,
                    onConfirm = { updated ->
                        viewModel.updateExpense(updated)
                        selectedExpense = null
                    },
                    onDelete = {
                        viewModel.deleteExpense(selectedExpense!!)
                        selectedExpense = null
                    }
                )
            }
        }
    }
}

@Composable
fun AddExpenseSheet(onConfirm: (Double, String, String, Boolean, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isIncome by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("Food & Mess") }
    var paymentMode by remember { mutableStateOf("UPI") }

    val categories = if (isIncome) {
        listOf("Stipend", "Pocket Money", "Freelance")
    } else {
        listOf("Food & Mess", "Stationary", "Travel", "Shopping", "College Expenses", "Misc")
    }
    
    val paymentModes = listOf("UPI", "Card", "Cash", "Netbanking")

    val isFormValid = amount.isNotEmpty() && (amount.toDoubleOrNull() ?: 0.0) > 0.0 && category.isNotEmpty()

    // Reset category if it's not in the current list
    LaunchedEffect(isIncome) {
        category = if (isIncome) "Stipend" else "Food & Mess"
    }

    Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Toggle
        Row(modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))) {
            Box(modifier = Modifier.width(100.dp).height(40.dp).clip(RoundedCornerShape(24.dp)).background(if (!isIncome) MaterialTheme.colorScheme.primary else Color.Transparent).clickable { isIncome = false }, contentAlignment = Alignment.Center) {
                Text("Expense", color = if (!isIncome) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(modifier = Modifier.width(100.dp).height(40.dp).clip(RoundedCornerShape(24.dp)).background(if (isIncome) MaterialTheme.colorScheme.primary else Color.Transparent).clickable { isIncome = true }, contentAlignment = Alignment.Center) {
                Text("Income", color = if (isIncome) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Integrated Amount Input
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("₹", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = amount,
                onValueChange = { 
                    if (it.count { char -> char == '.' } <= 1 && it.all { char -> char.isDigit() || char == '.' }) {
                        amount = it 
                    }
                },
                textStyle = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                decorationBox = { innerTextField ->
                    if (amount.isEmpty()) {
                        Text("0.00", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    }
                    innerTextField()
                }
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Description
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            BasicTextField(
                value = description,
                onValueChange = { description = it },
                textStyle = TextStyle(fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (description.isEmpty()) {
                        Text("What was this for?", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    innerTextField()
                }
            )
        }
        Divider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outline)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Mode Selection
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("TRANSACTION MODE", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(paymentModes) { mode ->
                    val isSelected = paymentMode == mode
                    Surface(
                        modifier = Modifier.clickable { paymentMode = mode },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    ) {
                        Text(
                            text = mode,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Category Dropdown
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("CATEGORY", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                var expanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.clickable { expanded = true }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(category, color = MaterialTheme.colorScheme.onSurface)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(
                    expanded = expanded, 
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat, color = MaterialTheme.colorScheme.onSurface) }, 
                            onClick = { category = cat; expanded = false }
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { 
                val finalDesc = if (description.isBlank()) category else description
                onConfirm(amount.toDoubleOrNull() ?: 0.0, category, finalDesc, isIncome, paymentMode) 
            },
            enabled = isFormValid,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Text("Save ${if (isIncome) "Income" else "Expense"}", modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun EditExpenseSheet(expense: ExpenseEntity, onConfirm: (ExpenseEntity) -> Unit, onDelete: () -> Unit) {
    var amount by remember { mutableStateOf(expense.amount.toString()) }
    var description by remember { mutableStateOf(expense.description) }

    Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
        Text("Edit Transaction", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { 
                if (it.count { char -> char == '.' } <= 1 && it.all { char -> char.isDigit() || char == '.' }) {
                    amount = it 
                }
            },
            label = { Text("Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onConfirm(expense.copy(amount = amount.toDoubleOrNull() ?: 0.0, description = description)) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Update", color = MaterialTheme.colorScheme.onPrimary)
        }
        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
            Text("Delete Log")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// --- SUBSCRIPTIONS SCREEN ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(viewModel: PottyViewModel) {
    val subs by viewModel.subscriptions.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var selectedSub by remember { mutableStateOf<SubscriptionEntity?>(null) }
    var selectedIntervalView by remember { mutableStateOf("MONTHLY") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val totalAmount = subs.filter { it.renewalInterval == selectedIntervalView }.sumOf { it.cost }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box {
                Row(
                    modifier = Modifier.clickable { dropdownExpanded = true },
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (selectedIntervalView == "MONTHLY") "Monthly Recurring" else "Yearly Recurring",
                        color = Color.Gray
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Monthly Recurring") },
                        onClick = {
                            selectedIntervalView = "MONTHLY"
                            dropdownExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Yearly Recurring") },
                        onClick = {
                            selectedIntervalView = "YEARLY"
                            dropdownExpanded = false
                        }
                    )
                }
            }
            Text(
                text = "₹${String.format(Locale.US, "%,.2f", totalAmount)}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(subs) { sub ->
                    SubscriptionCard(
                        sub, 
                        onToggle = { viewModel.updateSubscription(sub.copy(isAutoRenewing = !sub.isAutoRenewing)) },
                        onClick = { selectedSub = sub }
                    )
                }
            }
        }
        
        FloatingActionButton(
            onClick = { showAddSheet = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.onPrimary)
        }
        
        if (showAddSheet) {
            ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
                AddSubscriptionSheet(onConfirm = { name, cost, interval, date, category, trading, tType ->
                    viewModel.addSubscription(name, cost, interval, date, category, trading, tType)
                    showAddSheet = false
                })
            }
        }

        if (selectedSub != null) {
            ModalBottomSheet(onDismissRequest = { selectedSub = null }) {
                EditSubscriptionSheet(
                    sub = selectedSub!!,
                    onConfirm = { updated ->
                        viewModel.updateSubscription(updated)
                        selectedSub = null
                    },
                    onDelete = {
                        viewModel.deleteSubscription(selectedSub!!)
                        selectedSub = null
                    }
                )
            }
        }
    }
}

@Composable
fun SubscriptionCard(sub: SubscriptionEntity, onToggle: () -> Unit, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                    Box(contentAlignment = Alignment.Center) { 
                        Icon(
                            if (sub.isTrading) Icons.Default.TrendingUp else {
                                when(sub.category.lowercase()) {
                                    "entertainment" -> Icons.Default.Movie
                                    "audio" -> Icons.Default.MusicNote
                                    "softwares" -> Icons.Default.Code
                                    "ai services" -> Icons.Default.SmartToy
                                    else -> Icons.Default.Star
                                }
                            }, 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        ) 
                    }
                }
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(sub.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (sub.isTrading) "Type: ${sub.tradingType}" else "Cat: ${sub.category}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("₹${sub.cost}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
                Switch(
                    checked = sub.isAutoRenewing, 
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Column {
                    Text("Billing Cycle", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)) {
                        Text(
                            when(sub.renewalInterval) {
                                "MONTHLY" -> "Monthly"
                                "YEARLY" -> "Yearly"
                                "ONE_TIME" -> "One Time"
                                else -> sub.renewalInterval
                            }, 
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), 
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("Renews: ${sub.validUntilDate}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSubscriptionSheet(onConfirm: (String, Double, String, String, String, Boolean, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isTrading by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("Entertainment") }
    var tradingType by remember { mutableStateOf("Stocks") }
    var interval by remember { mutableStateOf("MONTHLY") }
    var debitDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    val categories = listOf("Entertainment", "Audio", "Softwares", "AI Services")
    val tradingTypes = listOf("Stocks", "F\u0026O", "Mutual Funds")

    val isFormValid = amount.isNotEmpty() && (amount.toDoubleOrNull() ?: 0.0) > 0.0

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        debitDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Toggle
        Row(modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))) {
            Box(modifier = Modifier.width(100.dp).height(40.dp).clip(RoundedCornerShape(24.dp)).background(if (!isTrading) MaterialTheme.colorScheme.primary else Color.Transparent).clickable { isTrading = false }, contentAlignment = Alignment.Center) {
                Text("Subs", color = if (!isTrading) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(modifier = Modifier.width(100.dp).height(40.dp).clip(RoundedCornerShape(24.dp)).background(if (isTrading) MaterialTheme.colorScheme.primary else Color.Transparent).clickable { isTrading = true }, contentAlignment = Alignment.Center) {
                Text("Trading", color = if (isTrading) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Amount Input
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("₹", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = amount,
                onValueChange = { 
                    if (it.count { char -> char == '.' } <= 1 && it.all { char -> char.isDigit() || char == '.' }) {
                        amount = it 
                    }
                },
                textStyle = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                decorationBox = { innerTextField ->
                    if (amount.isEmpty()) {
                        Text("0.00", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    }
                    innerTextField()
                }
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Service Name
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            BasicTextField(
                value = description,
                onValueChange = { description = it },
                textStyle = TextStyle(fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (description.isEmpty()) {
                        Text("Service/Asset Name", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    innerTextField()
                }
            )
        }
        Divider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outline)

        Spacer(modifier = Modifier.height(24.dp))

        // Debit Date
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = debitDate, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Divider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outline)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Billing Cycle Selector
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("BILLING CYCLE", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))) {
                listOf("MONTHLY" to "Monthly", "YEARLY" to "Yearly", "ONE_TIME" to "One Time").forEach { (valKey, label) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (interval == valKey) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { interval = valKey },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (interval == valKey) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Category / Trading Type Selector
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(if (isTrading) "TRADING TYPE" else "CATEGORY", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(if (isTrading) tradingTypes else categories) { item ->
                    val isSelected = (if (isTrading) tradingType else category) == item
                    Surface(
                        modifier = Modifier.clickable { if (isTrading) tradingType = item else category = item },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    ) {
                        Text(
                            text = item,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { 
                val finalTitle = if (description.isBlank()) (if (isTrading) tradingType else category) else description
                onConfirm(finalTitle, amount.toDoubleOrNull() ?: 0.0, interval, debitDate, category, isTrading, tradingType) 
            },
            enabled = isFormValid,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Text("Add ${if (isTrading) "Trading Asset" else "Subscription"}", modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun EditSubscriptionSheet(sub: SubscriptionEntity, onConfirm: (SubscriptionEntity) -> Unit, onDelete: () -> Unit) {
    var amount by remember { mutableStateOf(sub.cost.toString()) }
    var name by remember { mutableStateOf(sub.name) }

    Column(modifier = Modifier.padding(24.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Text("Edit Subscription", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { 
                if (it.count { char -> char == '.' } <= 1 && it.all { char -> char.isDigit() || char == '.' }) {
                    amount = it 
                }
            },
            label = { Text("Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = name, 
            onValueChange = { name = it }, 
            label = { Text("Service Name") }, 
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onConfirm(sub.copy(cost = amount.toDoubleOrNull() ?: 0.0, name = name)) }, 
            modifier = Modifier.fillMaxWidth(), 
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Update", color = MaterialTheme.colorScheme.onPrimary)
        }
        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
            Text("Delete Subscription")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// --- COLLEGE FEES SCREEN ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollegeFeeScreen(viewModel: PottyViewModel) {
    val fees by viewModel.collegeFees.collectAsState()
    var showUpdateSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            LazyColumn {
                (1..4).forEach { year ->
                    item { 
                        val yearFees = fees.filter { it.collegeYear == year }
                        YearFeeGroup(year, yearFees, onToggle = { viewModel.toggleFeePaid(it) }) 
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showUpdateSheet = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Edit, contentDescription = "Update Fees", tint = MaterialTheme.colorScheme.onPrimary)
        }

        if (showUpdateSheet) {
            val lastTuition = fees.find { it.feeType == "Tuition Fee" && it.collegeYear == 1 }?.baseAmount ?: 0.0
            val lastHostel = fees.find { it.feeType == "Hostel Fee" && it.collegeYear == 1 }?.baseAmount ?: 0.0
            val lastInflation = fees.firstOrNull()?.inflationRate ?: 0.0
            val lastCompounding = fees.find { it.feeType == "Hostel Fee" }?.isHostelCompounded ?: true

            ModalBottomSheet(onDismissRequest = { showUpdateSheet = false }) {
                UpdateFeesSheet(
                    initialTuition = if (lastTuition == 0.0) "" else lastTuition.toString(),
                    initialHostel = if (lastHostel == 0.0) "" else lastHostel.toString(),
                    initialInflation = if (lastInflation == 0.0) "" else lastInflation.toString().replace(".0", ""),
                    initialHostelComp = lastCompounding,
                    onConfirm = { tuition, hostel, inflation, hostelComp ->
                        viewModel.updateAllFees(tuition, hostel, inflation, hostelComp)
                        showUpdateSheet = false
                    }
                )
            }
        }
    }
}

@Composable
fun UpdateFeesSheet(
    initialTuition: String,
    initialHostel: String,
    initialInflation: String,
    initialHostelComp: Boolean,
    onConfirm: (Double, Double, Double, Boolean) -> Unit
) {
    var tuitionAmount by remember { mutableStateOf(initialTuition) }
    var hostelAmount by remember { mutableStateOf(initialHostel) }
    var inflationRate by remember { mutableStateOf(initialInflation) }
    var isHostelCompounded by remember { mutableStateOf(initialHostelComp) }

    Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Update College Fees", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text("Adjust parameters for fee calculation", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Tuition Base Input
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Base Tuition Amount", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("₹", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.width(12.dp))
                BasicTextField(
                    value = tuitionAmount,
                    onValueChange = { 
                        if (it.count { char -> char == '.' } <= 1 && it.all { char -> char.isDigit() || char == '.' }) {
                            tuitionAmount = it 
                        }
                    },
                    textStyle = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    decorationBox = { innerTextField ->
                        if (tuitionAmount.isEmpty()) Text("0.00", fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        innerTextField()
                    }
                )
            }
            Divider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outline)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Inflation Input
            Column(modifier = Modifier.weight(1f)) {
                Text("Annual Inflation", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = inflationRate,
                        onValueChange = { 
                            if (it.count { char -> char == '.' } <= 1 && it.all { char -> char.isDigit() || char == '.' }) {
                                inflationRate = it 
                            }
                        },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface)
                    )
                    Text("%", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Divider(color = MaterialTheme.colorScheme.outline)
            }
            // Hostel Input
            Column(modifier = Modifier.weight(1f)) {
                Text("Hostel Fees", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("₹", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = hostelAmount,
                        onValueChange = { 
                            if (it.count { char -> char == '.' } <= 1 && it.all { char -> char.isDigit() || char == '.' }) {
                                hostelAmount = it 
                            }
                        },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface)
                    )
                }
                Divider(color = MaterialTheme.colorScheme.outline)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Hostel Compounding Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Hostel Compounding", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Include hostel in annual inflation increase", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Switch(
                checked = isHostelCompounded,
                onCheckedChange = { isHostelCompounded = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { 
                onConfirm(
                    tuitionAmount.toDoubleOrNull() ?: 35000.0,
                    hostelAmount.toDoubleOrNull() ?: 12000.0,
                    inflationRate.toDoubleOrNull() ?: 8.0,
                    isHostelCompounded
                ) 
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("UPDATE FEES", color = MaterialTheme.colorScheme.onPrimary)
        }
        
        TextButton(onClick = { }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun YearFeeGroup(year: Int, fees: List<CollegeFeeEntity>, onToggle: (CollegeFeeEntity) -> Unit) {
    val tuitionFee = fees.find { it.feeType == "Tuition Fee" }
    val hostelFee = fees.find { it.feeType == "Hostel Fee" }
    
    val totalCalculated = (tuitionFee?.calculatedAmount ?: 0.0) + (hostelFee?.calculatedAmount ?: 0.0)
    val inflationRate = fees.firstOrNull()?.inflationRate ?: 8.0
    val isPaid = fees.isNotEmpty() && fees.all { it.isPaid }
    
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp).fillMaxHeight()) {
            Icon(
                if (isPaid) Icons.Default.CheckCircle else Icons.Outlined.Circle, 
                contentDescription = null, 
                modifier = Modifier.size(24.dp),
                tint = if (isPaid) Color.Black else Color.Gray
            )
            // The Line
            if (year < 4) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f) // Stretch to fill the remaining height
                        .background(if (isPaid) Color.Black else Color.LightGray)
                )
            }
        }
        Column(modifier = Modifier.padding(start = 8.dp, bottom = 12.dp).weight(1f)) {
            Text("Year $year", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(12.dp))
            if (fees.isEmpty()) {
                Text("No fees set for this year", color = Color.Gray, fontSize = 12.sp)
            } else {
                CombinedFeeCard(
                    year = year,
                    totalAmount = totalCalculated,
                    tuitionAmount = tuitionFee?.calculatedAmount ?: 0.0,
                    hostelAmount = hostelFee?.calculatedAmount ?: 0.0,
                    inflationRate = inflationRate,
                    isPaid = isPaid,
                    onToggle = { fees.forEach { onToggle(it) } }
                )
            }
        }
    }
}

@Composable
fun CombinedFeeCard(
    year: Int,
    totalAmount: Double,
    tuitionAmount: Double,
    hostelAmount: Double,
    inflationRate: Double,
    isPaid: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Fees", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Row {
                        Text("Tuition: \u20b9${String.format(Locale.US, "%,.0f", tuitionAmount)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(" \u2022 ", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Hostel: \u20b9${String.format(Locale.US, "%,.0f", hostelAmount)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("\u20b9${String.format(Locale.US, "%,.0f", totalAmount)}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            
            if (year > 1) {
                Surface(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Text("+${String.format(Locale.US, "%.0f", inflationRate)}% Compounded", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isPaid) MaterialTheme.colorScheme.secondary else Color.Transparent)
                        .clickable { if (!isPaid) onToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Paid", color = if (isPaid) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (!isPaid) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { if (isPaid) onToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Unpaid", color = if (!isPaid) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// --- PROFILE SCREEN ---

@Composable
fun ProfileScreen(
    viewModel: PottyViewModel,
    onNavigateToInfo: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToSecurity: () -> Unit
) {
    val context = LocalContext.current
    val profile by viewModel.userProfile.collectAsState()
    val isDarkMode by viewModel.preferenceManager.isNotificationEnabled(PreferenceManager.IS_DARK_MODE).collectAsState(initial = false)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                modifier = Modifier.size(100.dp), 
                shape = CircleShape, 
                color = Color(0xFFB07070)
            ) { 
                if (profile?.profilePicUri != null) {
                    AsyncImage(
                        model = profile?.profilePicUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(profile?.fullName ?: "User", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface)
        Text("Year ${profile?.currentYear ?: 1}, ${profile?.course ?: "Design"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        ProfileSection(
            title = "ACCOUNT SETTINGS",
            items = listOf("Personal Information"),
            onClick = { item -> if (item == "Personal Information") onNavigateToInfo() }
        )
        
        ProfileSection(
            title = "PREFERENCES",
            items = listOf("Notification Settings", "Theme Mode", "Security"),
            isDarkMode = isDarkMode,
            onThemeToggle = { viewModel.setNotificationEnabled(PreferenceManager.IS_DARK_MODE, it) },
            onClick = { item -> 
                if (item == "Notification Settings") onNavigateToNotifications()
                if (item == "Security") onNavigateToSecurity()
            }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("DOWNLOAD STATEMENT", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clickable { viewModel.exportStatement(context) }, 
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), 
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Download, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface) }
                }
                Text("Download Account Statements", modifier = Modifier.padding(start = 16.dp).weight(1f), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun ProfileSection(
    title: String, 
    items: List<String>, 
    isDarkMode: Boolean = false,
    onThemeToggle: (Boolean) -> Unit = {},
    onClick: (String) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
            Column {
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onClick(item) }.padding(16.dp), 
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(item, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                        if (item == "Theme Mode") {
                            Switch(
                                checked = isDarkMode, 
                                onCheckedChange = { onThemeToggle(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        } else {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (index < items.size - 1) Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalInfoScreen(viewModel: PottyViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val profile by viewModel.userProfile.collectAsState()
    
    var name by remember(profile) { mutableStateOf(profile?.fullName ?: "") }
    var college by remember(profile) { mutableStateOf(profile?.college ?: "") }
    var course by remember(profile) { mutableStateOf(profile?.course ?: "") }
    var currentYear by remember(profile) { mutableIntStateOf(profile?.currentYear ?: 1) }
    var profilePicUri by remember(profile) { mutableStateOf(profile?.profilePicUri) }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            // Persist permission for future use
            val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flag)
            profilePicUri = uri.toString()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Personal Info") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            actions = { 
                TextButton(onClick = {
                    val currentId = profile?.googleId ?: return@TextButton
                    viewModel.updateProfile(UserProfileEntity(
                        googleId = currentId,
                        fullName = name,
                        college = college,
                        course = course,
                        currentYear = currentYear,
                        profilePicUri = profilePicUri
                    ))
                    onBack()
                }) { Text("Save", color = MaterialTheme.colorScheme.primary) } 
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
        Column(modifier = Modifier.padding(16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Surface(
                    modifier = Modifier.size(100.dp), 
                    shape = CircleShape, 
                    color = Color(0xFFB07070)
                ) {
                    if (profilePicUri != null) {
                        AsyncImage(
                            model = profilePicUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .size(28.dp)
                        .offset(x = 4.dp, y = 4.dp)
                        .clickable { 
                            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = name, 
                onValueChange = { name = it }, 
                label = { Text("Full Name") }, 
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = college, 
                onValueChange = { college = it }, 
                label = { Text("College/University") }, 
                modifier = Modifier.fillMaxWidth(), 
                trailingIcon = { Icon(Icons.Default.School, null) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = course, 
                onValueChange = { course = it }, 
                label = { Text("Course / Degree") }, 
                placeholder = { Text("e.g. B.Tech Computer Science") }, 
                modifier = Modifier.fillMaxWidth(), 
                trailingIcon = { Icon(Icons.Default.Book, null) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Select Academic Year", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
                YearSlider(
                    selectedYear = currentYear,
                    onYearSelected = { currentYear = it }
                )
            }
        }
    }
}

@Composable
fun YearSlider(selectedYear: Int, onYearSelected: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        // Background track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        
        // Progress track
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight((selectedYear - 1).toFloat().coerceAtLeast(0.001f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.weight((5 - selectedYear).toFloat().coerceAtLeast(0.001f)))
        }

        // Years and indicators (Slotted for vertical alignment)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            (1..5).forEach { year ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.clickable { onYearSelected(year) }
                ) {
                    // Circle indicator (Positioned ON the line)
                    Surface(
                        modifier = Modifier.size(16.dp),
                        shape = CircleShape,
                        color = if (year <= selectedYear) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(2.dp, if (year <= selectedYear) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    ) {}
                    
                    // Label (Positioned BELOW the line)
                    Text(
                        text = year.toString(),
                        fontSize = 12.sp,
                        fontWeight = if (year == selectedYear) FontWeight.Bold else FontWeight.Normal,
                        color = if (year == selectedYear) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 40.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(viewModel: PottyViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    
    // Permission state
    var hasPermission by remember { mutableStateOf(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    ) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Toggle states from DataStore
    val dailySummary by viewModel.preferenceManager.isNotificationEnabled(PreferenceManager.DAILY_SUMMARY).collectAsState(initial = true)
    val weeklyReport by viewModel.preferenceManager.isNotificationEnabled(PreferenceManager.WEEKLY_REPORT).collectAsState(initial = true)
    val subAlerts by viewModel.preferenceManager.isNotificationEnabled(PreferenceManager.SUBSCRIPTION_ALERTS).collectAsState(initial = true)
    val feeReminders by viewModel.preferenceManager.isNotificationEnabled(PreferenceManager.FEE_REMINDERS).collectAsState(initial = true)
    val budgetThresholds by viewModel.preferenceManager.isNotificationEnabled(PreferenceManager.BUDGET_THRESHOLDS).collectAsState(initial = true)
    val reminders by viewModel.preferenceManager.isNotificationEnabled(PreferenceManager.GENERAL_REMINDERS).collectAsState(initial = true)

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Notification Settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Preferences", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text("Manage how and when you receive updates about your financial activity.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(24.dp))
            
            val settings = listOf(
                Triple("Daily Summary", PreferenceManager.DAILY_SUMMARY, dailySummary),
                Triple("Weekly Report", PreferenceManager.WEEKLY_REPORT, weeklyReport),
                Triple("Subscription Alerts", PreferenceManager.SUBSCRIPTION_ALERTS, subAlerts),
                Triple("College Fee Reminders", PreferenceManager.FEE_REMINDERS, feeReminders),
                Triple("Budget Thresholds", PreferenceManager.BUDGET_THRESHOLDS, budgetThresholds),
                Triple("Reminders", PreferenceManager.GENERAL_REMINDERS, reminders)
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(settings) { (title, key, state) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(), 
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), 
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                Text("Get a breakdown of your activity.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = state, 
                                onCheckedChange = { enabled -> 
                                    viewModel.setNotificationEnabled(key, enabled)
                                    // Schedule/Cancel background work if it's the daily summary
                                    if (key == PreferenceManager.DAILY_SUMMARY) {
                                        if (enabled) WorkerScheduler.scheduleDailySummary(context) 
                                        else WorkerScheduler.cancelDailySummary(context)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(viewModel: PottyViewModel, onBack: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val securityEnabled by viewModel.securityEnabled.collectAsState()

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete All Data?", fontWeight = FontWeight.Bold) },
            text = { Text("This action is permanent and will wipe all your financial data, including expenses, subscriptions, and college fees. Your profile info will remain intact.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    showDeleteDialog = false
                    onBack()
                }) {
                    Text("Delete Everything", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Security") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            actions = { TextButton(onClick = {}) { Text("Save", color = MaterialTheme.colorScheme.primary) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
        Column(modifier = Modifier.padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Your financial data is encrypted and protected with industry-standard protocols.", color = MaterialTheme.colorScheme.onSecondary, fontSize = 14.sp)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("ACCESS SECURITY", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Card(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Column {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.onSurface) }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable App Lock", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Text("Secure your data with PIN/Biometrics", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = securityEnabled == true,
                            onCheckedChange = { enabled ->
                                viewModel.setNotificationEnabled(PreferenceManager.SECURITY_ENABLED, enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Devices, null, tint = MaterialTheme.colorScheme.onSurface) }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Use System Lock", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Text("Always prompt for Fingerprint/PIN", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("DATA MANAGEMENT", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("The following actions are permanent and cannot be undone.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier
                        .clickable { showDeleteDialog = true }
                        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Text("Delete All Data", modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            OutlinedButton(
                onClick = { viewModel.logout() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Text("Log Out", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
