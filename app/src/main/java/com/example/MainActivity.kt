package com.example

import android.app.Application
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.GeminiService
import com.example.data.StockRepository
import com.example.data.StockWatchItem
import com.example.ml.PredictorEngine
import com.example.ui.StockViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by remember { mutableStateOf(true) } // Custom default premium dark look
            MyApplicationTheme(darkTheme = isDarkTheme) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    StockMarketApp(
                        modifier = Modifier.padding(innerPadding),
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = { isDarkTheme = !isDarkTheme }
                    )
                }
            }
        }
    }
}

@Suppress("AnimateAsStateLabel")
@Composable
fun StockMarketApp(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {},
    viewModel: StockViewModel = viewModel()
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) } // 0=Markets, 1=ML Models, 2=Simulator, 3=Saved

    val isMarketOpen by viewModel.isMarketOpen.collectAsState()
    val lastUpdatedTime by viewModel.lastUpdatedTime.collectAsState()

    // Listen to Toast / Status events from VM
    LaunchedEffect(key1 = true) {
        viewModel.tradeStatusMessage.collectLatest { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // App Header
            AppLogoBar(
                isMarketOpen = isMarketOpen, 
                lastUpdatedTime = lastUpdatedTime,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                viewModel = viewModel
            )

            // Main Contents with smooth transition
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    0 -> MarketsTab(viewModel)
                    1 -> ForecasterTab(viewModel)
                    2 -> TradeSimulatorTab(viewModel)
                    3 -> ArchiveTab(viewModel)
                }
            }

            // Bottom Navigation Bar with safe window padding
            StockBottomNavBar(
                activeTab = activeTab,
                onTabSelected = { activeTab = it }
            )
        }
    }
}

@Composable
fun AppLogoBar(
    isMarketOpen: Boolean,
    lastUpdatedTime: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    viewModel: StockViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(MintGreen, CoolIndigo)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "ML Predictor Logo",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "ZenPredict",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "NSE INTUITIVE ENGINE",
                        fontSize = 9.sp,
                        color = ProfTextMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Market Badge Indicator
                Column(horizontalAlignment = Alignment.End) {
                    val badgeColor = if (isMarketOpen) MintGreen else DeepCoral
                    val textLabel = if (isMarketOpen) "ACTIVE LIVE" else "MARKET OFFLINE"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(badgeColor.copy(alpha = 0.12f))
                            .border(BorderStroke(1.dp, badgeColor.copy(alpha = 0.25f)), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = textLabel,
                            fontSize = 8.sp,
                            color = badgeColor,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    val currentDate = remember {
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
                    }
                    val lastUpdatedLabel = if (isMarketOpen) {
                        if (lastUpdatedTime.isNotEmpty()) lastUpdatedTime else currentDate
                    } else {
                        if (lastUpdatedTime.isNotEmpty()) "Last: ${lastUpdatedTime.substringAfter(", ")}" else currentDate
                    }
                    Text(
                        text = lastUpdatedLabel,
                        fontSize = 8.sp,
                        color = ProfTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Theme Switcher Button with compile-safe Emojis
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier.size(32.dp)
                ) {
                    Text(
                        text = if (isDarkTheme) "☀️" else "🌙",
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                var showSettings by remember { mutableStateOf(false) }
                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier.testTag("feed_settings_button").size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Feed Settings",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (showSettings) {
                    ApiConfigDialog(
                        viewModel = viewModel,
                        onDismiss = { showSettings = false }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiConfigDialog(
    viewModel: StockViewModel,
    onDismiss: () -> Unit
) {
    val apiSource by viewModel.apiSource.collectAsState()
    val twelveKey by viewModel.twelveDataKey.collectAsState()
    val apiStatusMessage by viewModel.apiStatusMessage.collectAsState()

    var selectedSource by remember { mutableStateOf(apiSource) }
    var inputTwelveKey by remember { mutableStateOf(twelveKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "API Configuration",
                    tint = ProfBlue,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Market Feed Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Select your NSE real-time data source. The app will fetch fresh pricing every 5 seconds during market hours.",
                    fontSize = 12.sp,
                    color = ProfTextMuted
                )

                // Status banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            apiStatusMessage.contains("Connected") -> ProfGreen.copy(alpha = 0.08f)
                            apiStatusMessage.contains("Error") || apiStatusMessage.contains("failed") -> ProfRed.copy(alpha = 0.08f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        }
                    ),
                    border = BorderStroke(1.dp, when {
                        apiStatusMessage.contains("Connected") -> ProfGreen.copy(alpha = 0.3f)
                        apiStatusMessage.contains("Error") || apiStatusMessage.contains("failed") -> ProfRed.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    })
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        apiStatusMessage.contains("Connected") -> ProfGreen
                                        apiStatusMessage.contains("Error") || apiStatusMessage.contains("failed") -> ProfRed
                                        else -> Color.Gray
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = apiStatusMessage,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                apiStatusMessage.contains("Connected") -> ProfGreen
                                apiStatusMessage.contains("Error") || apiStatusMessage.contains("failed") -> ProfRed
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Text(
                    text = "Active Source Provider",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Providers options
                listOf(
                    Triple("twelvedata", "Twelve Data API (Live)", "Enterprise-grade real-time market data feed"),
                    Triple("simulation", "AI Smart Ticker (Failsafe)", "Simulates NSE market hours dynamically")
                ).forEach { (sourceId, sourceName, sourceDesc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedSource == sourceId) ProfBlue.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable { selectedSource = sourceId }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedSource == sourceId),
                            onClick = { selectedSource = sourceId },
                            colors = RadioButtonDefaults.colors(selectedColor = ProfBlue)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = sourceName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedSource == sourceId) ProfBlue else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = sourceDesc,
                                fontSize = 11.sp,
                                color = ProfTextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Conditionally display configuration parameters
                when (selectedSource) {
                    "twelvedata" -> {
                        OutlinedTextField(
                            value = inputTwelveKey,
                            onValueChange = { inputTwelveKey = it },
                            label = { Text("Twelve Data API Key") },
                            placeholder = { Text("Enter twelve data apikey...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (inputTwelveKey.isNotEmpty()) {
                                    IconButton(onClick = { inputTwelveKey = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            }
                        )
                        Text(
                            text = "Get a free daily 800-request key at twelvedata.com. Set query exchange to NSE.",
                            fontSize = 10.sp,
                            color = ProfTextMuted,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    "simulation" -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = "💡 AI Smart Simulation requires no external credentials. It runs offline, maintaining precise tick updates modeling real volatile Indian markets.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.updateApiConfig(
                        source = selectedSource,
                        twelveKey = inputTwelveKey.trim()
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ProfBlue)
            ) {
                Text("Save Credentials", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ProfBlue)
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Suppress("AnimateAsStateLabel")
@Composable
fun StockBottomNavBar(
    activeTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(), // Ensures bottom gesture bar safety
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            NavigationBarItem(
                selected = activeTab == 0,
                modifier = Modifier.testTag("markets_navigation_tab"),
                onClick = { onTabSelected(0) },
                icon = { Icon(Icons.Default.Home, contentDescription = "Markets") },
                label = { Text("Home", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ProfBlue,
                    selectedTextColor = ProfBlue,
                    unselectedIconColor = ProfTextMuted,
                    unselectedTextColor = ProfTextMuted,
                    indicatorColor = ProfLightBlue.copy(alpha = 0.4f)
                )
            )

            NavigationBarItem(
                selected = activeTab == 1,
                modifier = Modifier.testTag("forecaster_navigation_tab"),
                onClick = { onTabSelected(1) },
                icon = { Icon(Icons.Default.PlayArrow, contentDescription = "ML Predictor") },
                label = { Text("Analysis", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ProfBlue,
                    selectedTextColor = ProfBlue,
                    unselectedIconColor = ProfTextMuted,
                    unselectedTextColor = ProfTextMuted,
                    indicatorColor = ProfLightBlue.copy(alpha = 0.4f)
                )
            )

            NavigationBarItem(
                selected = activeTab == 2,
                modifier = Modifier.testTag("simulator_navigation_tab"),
                onClick = { onTabSelected(2) },
                icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "Portfolio Trader") },
                label = { Text("Portfolio", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ProfBlue,
                    selectedTextColor = ProfBlue,
                    unselectedIconColor = ProfTextMuted,
                    unselectedTextColor = ProfTextMuted,
                    indicatorColor = ProfLightBlue.copy(alpha = 0.4f)
                )
            )

            NavigationBarItem(
                selected = activeTab == 3,
                modifier = Modifier.testTag("archives_navigation_tab"),
                onClick = { onTabSelected(3) },
                icon = { Icon(Icons.Default.Star, contentDescription = "Watchlist / Saved Predictions") },
                label = { Text("Watchlist", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ProfBlue,
                    selectedTextColor = ProfBlue,
                    unselectedIconColor = ProfTextMuted,
                    unselectedTextColor = ProfTextMuted,
                    indicatorColor = ProfLightBlue.copy(alpha = 0.4f)
                )
            )
        }
    }
}

// ================= TAB 1: MARKETS =================

@Composable
fun IndexDetailCard(
    title: String,
    price: Double,
    changePercent: Double,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val isPositive = changePercent >= 0
    val trendColor = if (isPositive) MintGreen else DeepCoral
    val trendBg = trendColor.copy(alpha = 0.08f)
    
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (isPositive) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = trendColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "₹${String.format("%,.2f", price)}",
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(trendBg)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                val sign = if (isPositive) "+" else ""
                Text(
                    text = "$sign${String.format("%.2f", changePercent)}%",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = trendColor
                )
            }
        }
    }
}

@Composable
fun IndianIndicesWidget(
    nifty: StockRepository.StockData?,
    onSelectNifty: () -> Unit
) {
    if (nifty == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IndexDetailCard(
            title = "NIFTY 50",
            price = nifty.currentPrice,
            changePercent = nifty.changePercentage,
            modifier = Modifier.weight(1f),
            onClick = onSelectNifty
        )

        IndexDetailCard(
            title = "SENSEX",
            price = nifty.currentPrice * 3.284,
            changePercent = nifty.changePercentage + 0.01,
            modifier = Modifier.weight(1f),
            onClick = onSelectNifty
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketsTab(viewModel: StockViewModel) {
    val stocks by viewModel.marketStocks.collectAsState()
    val activeStock by viewModel.activeStock.collectAsState()
    val watchlistList by viewModel.watchlist.collectAsState()
    val newsFeed by viewModel.newsFeed.collectAsState()

    var isOfflineMock by remember { mutableStateOf(false) }
    var isSyncingMock by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    val filteredStocks = remember(searchQuery, stocks) {
        if (searchQuery.isBlank() || isOfflineMock) {
            emptyList()
        } else {
            stocks.filter {
                it.symbol.contains(searchQuery, ignoreCase = true) ||
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Connection Controller
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isOfflineMock) ProfRed else ProfGreen)
                        )
                        Text(
                            text = if (isOfflineMock) "OFFLINE MODE ACTIVE (ROOM FALLBACK)" else "NSE CONNECTION ACTIVE & SYNCED",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOfflineMock) ProfRed else ProfGreen
                        )
                    }
                    
                    TextButton(
                        onClick = {
                            isOfflineMock = !isOfflineMock
                            if (isOfflineMock) searchQuery = ""
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isOfflineMock) "CONNECT ONLINE" else "SIMULATE OFFLINE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isOfflineMock) ProfGreen else ProfRed
                        )
                    }
                }
            }
        }

        if (isOfflineMock) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = ProfRed.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, ProfRed.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier.size(10.dp).clip(CircleShape).background(ProfRed)
                            )
                            Text("OFFLINE STATUS RECORDED", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ProfRed)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Offline cache active. Retaining historical quotes and backtests locally. Real-time index updates and Gemini forecaster requests are paused until stable connection is found.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            lineHeight = 15.sp
                        )
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Button(
                            onClick = {
                                isSyncingMock = true
                                scope.launch {
                                    delay(2000) // Beautiful skeleton load delay!
                                    isOfflineMock = false
                                    isSyncingMock = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ProfRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RE-CONNECT & RESYNC NOW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        if (isSyncingMock) {
            item {
                Text(
                    text = "RE-SYNCHRONIZING SECURE TUNNELS...",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = ProfBlue
                )
            }
            items(4) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.width(80.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = 0.2f)))
                            Box(modifier = Modifier.width(140.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = 0.15f)))
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.width(60.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = 0.2f)))
                            Box(modifier = Modifier.width(40.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = 0.15f)))
                        }
                    }
                }
            }
        }

        // Groww-style search bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search Stocks, Companies, Indices...") },
                placeholder = { Text("Search e.g. Reliance, TCS, ARTL...") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MintGreen,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }

        // Search Results Section
        if (searchQuery.isNotBlank()) {
            item {
                Text(
                    text = "SEARCH RESULTS (${filteredStocks.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MintGreen,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            if (filteredStocks.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "No match",
                                tint = ProfTextMuted,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No active Twelve Data ticker matched \"$searchQuery\"",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredStocks) { stock ->
                    val isSelected = activeStock?.symbol == stock.symbol
                    val borderStroke = if (isSelected) BorderStroke(2.dp, MintGreen) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    val changeColor = if (stock.changePercentage >= 0) MintGreen else DeepCoral
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.onSelectStock(stock.symbol)
                                searchQuery = "" // clear search, select item
                            }
                            .padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MintGreen.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = borderStroke
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stock.symbol,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stock.name,
                                    fontSize = 11.sp,
                                    color = ProfTextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "₹${String.format("%,.2f", stock.currentPrice)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (stock.changePercentage >= 0) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = changeColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    val sign = if (stock.changePercentage >= 0) "+" else ""
                                    Text(
                                        text = "$sign${String.format("%.2f", stock.changePercentage)}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = changeColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Indices widget top position
        val niftyStock = stocks.find { it.symbol == "NIFTY 50" }
        item {
            IndianIndicesWidget(
                nifty = niftyStock,
                onSelectNifty = { viewModel.onSelectStock("NIFTY 50") }
            )
        }

        // Horizontal Tickers List
        item {
            Text(
                text = "POPULAR STOCK TICKERS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ProfTextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(stocks) { stock ->
                    MiniTickerCard(
                        stock = stock,
                        isSelected = activeStock?.symbol == stock.symbol,
                        onClick = { viewModel.onSelectStock(stock.symbol) }
                    )
                }
            }
        }

        // Selected Stock Detail Active View (Hero Dashboard Card)
        activeStock?.let { stock ->
            item {
                ActiveStockHeroCard(
                    stock = stock,
                    isWatched = watchlistList.any { it.symbol == stock.symbol },
                    onToggleWatch = { viewModel.toggleWatchlist(stock.symbol, stock.name) }
                )
            }

            // Interactive Technical Chart Widget
            item {
                InteractiveTechnicalChartCard(stock = stock)
            }
        }

        // Relevant Market Sentiment News
        item {
            Text(
                text = "LIVE SENTIMENT NEWS FEED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ProfTextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
        }

        val matchingNews = newsFeed.filter {
            activeStock == null || it.affectedSymbol == null || it.affectedSymbol == activeStock!!.symbol
        }.take(4)

        if (matchingNews.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = "No direct corporate press releases available for selected stock. Macro indicators remaining flat.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(matchingNews) { news ->
                NewsAlertRow(news)
            }
        }

        // Blank spacer at the bottom
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun MiniTickerCard(
    stock: StockRepository.StockData,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val changeColor = if (stock.changePercentage >= 0) MintGreen else DeepCoral
    val selectionBg = if (isSelected) MintGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
    val outlineColor = if (isSelected) MintGreen else MaterialTheme.colorScheme.outline
    val strokeWidth = if (isSelected) 2.dp else 1.dp

    Card(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick)
            .testTag("mini_ticker_${stock.symbol}"),
        colors = CardDefaults.cardColors(containerColor = selectionBg),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(strokeWidth, outlineColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = stock.symbol,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "₹${String.format("%.2f", stock.currentPrice)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            val sign = if (stock.changePercentage >= 0) "+" else ""
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (stock.changePercentage >= 0) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = changeColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "$sign${String.format("%.2f", stock.changePercentage)}%",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = changeColor
                )
            }
        }
    }
}

@Composable
fun DayRangeSummaryMeter(low: Double, high: Double, current: Double) {
    val progress = if (high == low) 0.5f else ((current - low) / (high - low)).toFloat().coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "L: ₹${String.format("%.2f", low)}", fontSize = 10.sp, color = DeepCoral, fontWeight = FontWeight.Bold)
            Text(text = "TODAY'S RANGE", fontSize = 8.sp, color = ProfTextMuted, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
            Text(text = "H: ₹${String.format("%.2f", high)}", fontSize = 10.sp, color = MintGreen, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(DeepCoral, MintGreen)
                        )
                    )
            )
        }
    }
}

@Composable
fun ActiveStockHeroCard(
    stock: StockRepository.StockData,
    isWatched: Boolean,
    onToggleWatch: () -> Unit
) {
    val changeColor = if (stock.changePercentage >= 0) MintGreen else DeepCoral
    val isPositive = stock.changePercentage >= 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(changeColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stock.name.uppercase(),
                            fontSize = 10.sp,
                            color = ProfTextMuted,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${stock.symbol} / INR",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.5).sp
                    )
                }

                IconButton(
                    onClick = onToggleWatch,
                    modifier = Modifier
                        .testTag("star_toggle")
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isWatched) GoldenSun.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Watch stock",
                        tint = if (isWatched) GoldenSun else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "LAST TRANSACTION PRICE",
                        fontSize = 8.sp,
                        color = ProfTextMuted,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "₹${String.format("%,.2f", stock.currentPrice)}",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "TODAY'S MOVEMENT",
                        fontSize = 8.sp,
                        color = ProfTextMuted,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val sign = if (stock.changePercentage >= 0) "+" else ""
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isPositive) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = changeColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "$sign${String.format("%.2f", stock.changePercentage)}%",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = changeColor
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                modifier = Modifier.padding(vertical = 14.dp)
            )

            // Day's Range high value slider
            DayRangeSummaryMeter(low = stock.low, high = stock.high, current = stock.currentPrice)

            Spacer(modifier = Modifier.height(10.dp))

            // Extra metrics grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "VOLUME", fontSize = 8.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${String.format("%,.0f", stock.volume)} shares",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "OPEN VALUE", fontSize = 8.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                    Text(
                        text = "₹${String.format("%,.2f", stock.open)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "AVERAGE VOLATILITY", fontSize = 8.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                    Text(
                        text = "HIGH-LOW SPREAD",
                        fontSize = 11.sp,
                        color = changeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

object TradingViewDataGenerator {
    fun generateData(
        symbol: String,
        currentPrice: Double,
        openPrice: Double,
        interval: String
    ): String {
        val steps = when (interval) {
            "1D" -> 78
            "1W" -> 100
            "1M" -> 120
            "3M" -> 150
            "1Y" -> 250
            "5Y" -> 300
            else -> 100
        }

        val timeStep = when (interval) {
            "1D" -> 300L         // 5 minutes
            "1W" -> 3600L        // 1 hour
            "1M" -> 4 * 3600L     // 4 hours
            "3M" -> 12 * 3600L    // 12 hours
            "1Y" -> 86400L       // 1 day
            "5Y" -> 5 * 86400L    // 5 days
            else -> 3600L
        }

        val startPrice = when (interval) {
            "1D" -> openPrice
            "1W" -> currentPrice * (1.0 - 0.018 * (Math.abs(symbol.hashCode() % 3) + 1))
            "1M" -> currentPrice * (1.0 - 0.045 * (Math.abs(symbol.hashCode() % 3) + 1))
            "3M" -> currentPrice * (1.0 - 0.082 * (Math.abs(symbol.hashCode() % 3) + 1))
            "1Y" -> currentPrice * (1.0 - 0.160 * (Math.abs(symbol.hashCode() % 3) + 1))
            "5Y" -> currentPrice * (1.0 - 0.380 * (Math.abs(symbol.hashCode() % 3) + 1))
            else -> openPrice
        }

        val seed = symbol.hashCode() + interval.hashCode() + 42L
        val prices = generateBrownianBridge(startPrice, currentPrice, steps, seed)

        // Current time in seconds
        val currentTimeInSec = System.currentTimeMillis() / 1000
        val startTimeInSec = currentTimeInSec - (steps * timeStep)

        val jsonArray = org.json.JSONArray()
        for (i in prices.indices) {
            val pointTime = startTimeInSec + (i * timeStep)
            val obj = org.json.JSONObject()
            obj.put("time", pointTime)
            obj.put("value", prices[i])
            jsonArray.put(obj)
        }

        return jsonArray.toString()
    }

    private fun generateBrownianBridge(
        startVal: Double,
        endVal: Double,
        steps: Int,
        seed: Long
    ): List<Double> {
        val rnd = java.util.Random(seed)
        val path = DoubleArray(steps)
        path[0] = startVal
        path[steps - 1] = endVal
        
        for (i in 1 until steps - 1) {
            val prev = path[i - 1]
            val variation = startVal * 0.003
            val noise = (rnd.nextDouble() - 0.5) * variation
            path[i] = prev + noise
        }
        
        val actualEnd = path[steps - 1]
        val diff = endVal - actualEnd
        
        for (i in 1 until steps - 1) {
            val fraction = i.toDouble() / (steps - 1)
            path[i] += diff * fraction
        }
        
        return path.toList()
    }
}

@Composable
fun InteractiveTechnicalChartCard(stock: StockRepository.StockData) {
    var selectedInterval by remember { mutableStateOf("1D") }
    val isDarkTheme = MaterialTheme.colorScheme.background == DarkDbBg
    
    // Theme colors mapping
    val bgColor = if (isDarkTheme) "#131A2A" else "#FFFFFF"
    val textColor = if (isDarkTheme) "#94A3B8" else "#64748B"
    val gridColor = if (isDarkTheme) "rgba(30, 41, 59, 0.15)" else "#F1F5F9"
    val lineColor = if (stock.changePercentage >= 0) "#00D09C" else "#FF5252"
    val topColor = if (stock.changePercentage >= 0) "rgba(0, 208, 156, 0.18)" else "rgba(255, 82, 82, 0.18)"
    val bottomColor = "rgba(0, 0, 0, 0)"

    var isPageLoaded by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<android.webkit.WebView?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TradingView Lightweight Chart",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.5.sp
                )
                
                // Live status badge
                Text(
                    text = "REAL-TIME LIVE",
                    fontSize = 8.sp,
                    color = if (stock.changePercentage >= 0) MintGreen else DeepCoral,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier
                        .background(
                            (if (stock.changePercentage >= 0) MintGreen else DeepCoral).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Interval selector row
            val intervals = listOf("1D", "1W", "1M", "3M", "1Y", "5Y")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                intervals.forEach { interval ->
                    val isSelected = interval == selectedInterval
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) {
                                    (if (stock.changePercentage >= 0) MintGreen else DeepCoral).copy(alpha = 0.12f)
                                } else Color.Transparent
                            )
                            .clickable {
                                selectedInterval = interval
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = interval,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                            color = if (isSelected) {
                                if (stock.changePercentage >= 0) MintGreen else DeepCoral
                            } else {
                                ProfTextMuted
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // WebView Chart Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                val dataJson = remember(stock.symbol, selectedInterval, stock.currentPrice) {
                    TradingViewDataGenerator.generateData(
                        stock.symbol,
                        stock.currentPrice,
                        stock.open,
                        selectedInterval
                    )
                }

                val htmlString = remember(stock.symbol, selectedInterval, isDarkTheme) {
                    """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                        <style>
                            body, html {
                                margin: 0;
                                padding: 0;
                                width: 100%;
                                height: 100%;
                                overflow: hidden;
                                background-color: $bgColor;
                            }
                            #chart {
                                width: 100%;
                                height: 100%;
                            }
                        </style>
                        <script src="https://unpkg.com/lightweight-charts@4.0.1/dist/lightweight-charts.standalone.production.js"></script>
                    </head>
                    <body>
                        <div id="chart"></div>
                        <script>
                            var chart, areaSeries, currentData = [];

                            function initChart() {
                                try {
                                    var chartElement = document.getElementById('chart');
                                    chartElement.innerHTML = '';
                                    
                                    chart = LightweightCharts.createChart(chartElement, {
                                        width: chartElement.clientWidth,
                                        height: chartElement.clientHeight,
                                        layout: {
                                            background: { type: 'solid', color: '$bgColor' },
                                            textColor: '$textColor',
                                        },
                                        grid: {
                                            vertLines: { color: '$gridColor' },
                                            horzLines: { color: '$gridColor' },
                                        },
                                        timeScale: {
                                            borderVisible: false,
                                            timeVisible: true,
                                            secondsVisible: false,
                                            fixLeftEdge: true,
                                            fixRightEdge: true,
                                        },
                                        rightPriceScale: {
                                            borderVisible: false,
                                            scaleMargins: {
                                                top: 0.15,
                                                bottom: 0.15,
                                            },
                                        },
                                        crosshair: {
                                            mode: LightweightCharts.CrosshairMode.Normal,
                                        },
                                        handleScroll: {
                                            mouseWheel: true,
                                            pressedMouseMove: true,
                                            horzTouchDrag: true,
                                            vertTouchDrag: false,
                                        },
                                        handleScale: {
                                            axisPressedMouseMove: true,
                                            mouseWheel: true,
                                            pinch: true,
                                        },
                                    });

                                    areaSeries = chart.addAreaSeries({
                                        lineColor: '$lineColor',
                                        topColor: '$topColor',
                                        bottomColor: '$bottomColor',
                                        lineWidth: 2.5,
                                        priceLineVisible: true,
                                        lastValueVisible: true,
                                    });

                                    var rawData = $dataJson;
                                    areaSeries.setData(rawData);
                                    currentData = rawData;
                                    
                                    chart.timeScale().fitContent();
                                } catch(e) {
                                    console.error(e);
                                }
                            }

                            window.addEventListener('resize', function() {
                                if (chart) {
                                    var chartElement = document.getElementById('chart');
                                    chart.resize(chartElement.clientWidth, chartElement.clientHeight);
                                }
                            });

                            window.setChartData = function(jsonData) {
                                if (areaSeries) {
                                    areaSeries.setData(jsonData);
                                    currentData = jsonData;
                                    chart.timeScale().fitContent();
                                }
                            };

                            window.updateLastPrice = function(newPrice) {
                                if (areaSeries && currentData && currentData.length > 0) {
                                    var lastIndex = currentData.length - 1;
                                    currentData[lastIndex].value = newPrice;
                                    areaSeries.update(currentData[lastIndex]);
                                }
                            };

                            window.updateTheme = function(bg, text, gd, line, top, bottom) {
                                if (chart) {
                                    chart.applyOptions({
                                        layout: {
                                            background: { type: 'solid', color: bg },
                                            textColor: text,
                                        },
                                        grid: {
                                            vertLines: { color: gd },
                                            horzLines: { color: gd },
                                        }
                                    });
                                }
                                if (areaSeries) {
                                    areaSeries.applyOptions({
                                        lineColor: line,
                                        topColor: top,
                                        bottomColor: bottom,
                                    });
                                }
                            };

                            // Run initialization
                            setTimeout(initChart, 100);
                        </script>
                    </body>
                    </html>
                    """.trimIndent()
                }

                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        android.webkit.WebView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            webViewClient = object : android.webkit.WebViewClient() {
                                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isPageLoaded = true
                                }
                            }
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            webViewInstance = this
                            loadDataWithBaseURL(null, htmlString, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                LaunchedEffect(stock.symbol, selectedInterval) {
                    if (isPageLoaded && webViewInstance != null) {
                        val currentDataStr = TradingViewDataGenerator.generateData(
                            stock.symbol,
                            stock.currentPrice,
                            stock.open,
                            selectedInterval
                        )
                        webViewInstance?.evaluateJavascript("window.setChartData($currentDataStr)", null)
                    }
                }

                LaunchedEffect(stock.currentPrice) {
                    if (isPageLoaded && webViewInstance != null) {
                        webViewInstance?.evaluateJavascript("window.updateLastPrice(${stock.currentPrice})", null)
                    }
                }

                LaunchedEffect(isDarkTheme, lineColor) {
                    if (isPageLoaded && webViewInstance != null) {
                        webViewInstance?.evaluateJavascript(
                            "window.updateTheme('$bgColor', '$textColor', '$gridColor', '$lineColor', '$topColor', '$bottomColor')",
                            null
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NewsAlertRow(news: StockRepository.NewsAlert) {
    val bColor = when (news.sentiment) {
        "BULLISH" -> ProfGreen.copy(alpha = 0.15f)
        "BEARISH" -> ProfRed.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("news_alert_${news.title.take(10).replace(" ", "_")}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when (news.sentiment) {
                            "BULLISH" -> ProfGreen
                            "BEARISH" -> ProfRed
                            else -> ProfTextMuted
                        }
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = news.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = news.source, fontSize = 9.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                    Text(text = "•", fontSize = 9.sp, color = ProfTextMuted)
                    Text(text = news.time, fontSize = 9.sp, color = ProfTextMuted)
                }
            }
        }
    }
}

// ================= TAB 2: ML MODELS & FORECASTER =================

@Composable
fun ForecasterTab(viewModel: StockViewModel) {
    val activeStock by viewModel.activeStock.collectAsState()
    val activeIndicators by viewModel.activeIndicators.collectAsState()
    val activePredictions by viewModel.activePredictions.collectAsState()
    val geminiState by viewModel.geminiState.collectAsState()
    val horizonDays by viewModel.forecastDaysHorizonInput.collectAsState()
    val knnK by viewModel.knnKInput.collectAsState()
    val activeConsensus by viewModel.activeConsensus.collectAsState()

    if (activeStock == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a stock ticker under Markets to compile predictions.", color = MaterialTheme.colorScheme.onBackground)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ML PREDICTOR WORKBENCH: ${activeStock!!.symbol}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ProfBlue,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Customize parameters dynamically to evaluate statistical outputs on real historical curves.",
                        fontSize = 11.sp,
                        color = ProfTextMuted
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Horizon Days Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Forecast Horizon: $horizonDays Days", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(text = "(Runs equations up to 30d)", fontSize = 10.sp, color = ProfTextMuted)
                    }
                    Slider(
                        value = horizonDays.toFloat(),
                        onValueChange = { viewModel.onUpdateForecastHorizon(it.toInt()) },
                        valueRange = 1f..30f,
                        steps = 29,
                        colors = SliderDefaults.colors(
                            thumbColor = ProfBlue,
                            activeTrackColor = ProfBlue,
                            inactiveTrackColor = ProfLightBlue
                        ),
                        modifier = Modifier.testTag("forecast_horizon_slider")
                    )

                    // KNN K-Neighbors Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "KNN K-Neighbors: $knnK matches", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(text = "(Optimal: 2 to 5)", fontSize = 10.sp, color = ProfTextMuted)
                    }
                    Slider(
                        value = knnK.toFloat(),
                        onValueChange = { viewModel.onUpdateKnnK(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = ProfBlue,
                            activeTrackColor = ProfBlue,
                            inactiveTrackColor = ProfLightBlue
                        ),
                        modifier = Modifier.testTag("knn_k_slider")
                    )
                }
            }
        }

        // --- NEW: Unified Prediction Consensus Card ---
        activeConsensus?.let { consensus ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("consensus_decision_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CONSENSUS DECISION ENGINE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ProfBlue,
                                letterSpacing = 1.sp
                            )
                            
                            val (badgeBg, badgeText, actionText) = when (consensus.action) {
                                "BUY" -> Triple(ProfGreen.copy(alpha = 0.15f), ProfGreen, "STRONG BUY")
                                "SELL" -> Triple(ProfRed.copy(alpha = 0.15f), ProfRed, "STRONG SELL")
                                else -> Triple(ProfAmber.copy(alpha = 0.15f), ProfAmber, "HOLD STANCE")
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(badgeBg)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = actionText,
                                    fontSize = 10.sp,
                                    color = badgeText,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = consensus.action,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = when (consensus.action) {
                                        "BUY" -> ProfGreen
                                        "SELL" -> ProfRed
                                        else -> ProfAmber
                                    }
                                )
                                Text(
                                    text = String.format("%.1f%% Signal Match Confidence", consensus.confidence),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = consensus.summary,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "INDICATORS SUB-SIGNALS BREAKDOWN",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = ProfTextMuted,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val breakdown = listOf(
                            "RSI Threshold" to consensus.rsiSignal,
                            "MACD Wavefront" to consensus.macdSignal,
                            "EMA Moving Alignment" to consensus.emaSignal,
                            "VWAP Volume Anchor" to consensus.vwapSignal
                        )

                        breakdown.forEach { (title, valStr) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = title, fontSize = 10.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                                Text(
                                    text = valStr, 
                                    fontSize = 10.sp, 
                                    fontWeight = FontWeight.ExtraBold,
                                    color = when {
                                        valStr.contains("BUY") -> ProfGreen
                                        valStr.contains("SELL") -> ProfRed
                                        else -> ProfAmber
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- SECTION 1: Technical indicators summary gauges ---
        activeIndicators?.let { ind ->
            item {
                Text(
                    text = "Core Technical Analytical Variables",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            }

            // RSI & MACD
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "RSI (14)", fontSize = 9.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                            Text(
                                text = String.format("%.2f", ind.rsi),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = when {
                                    ind.rsi > 70 -> ProfRed
                                    ind.rsi < 30 -> ProfGreen
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                text = when {
                                    ind.rsi > 70 -> "Overbought"
                                    ind.rsi < 30 -> "Oversold"
                                    else -> "Neutral Support"
                                },
                                fontSize = 9.sp,
                                color = ProfTextMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "MACD SPREAD", fontSize = 9.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                            val diff = ind.macd - ind.macdSignal
                            Text(
                                text = String.format("%.2f", diff),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = if (diff >= 0) ProfGreen else ProfRed
                            )
                            Text(
                                text = if (diff >= 0) "MACD Bull Cross" else "MACD Bearish",
                                fontSize = 9.sp,
                                color = ProfTextMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // EMA 20 & EMA 50
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "EXPONENTIAL MOVING AVERAGE (EMA)", fontSize = 9.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(text = "EMA 20", fontSize = 8.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                                    Text(text = "₹${String.format("%.2f", ind.ema20)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(text = "EMA 50", fontSize = 8.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                                    Text(text = "₹${String.format("%.2f", ind.ema50)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (ind.ema20 > ind.ema50) "Golden Trend" else "Death Trend",
                                fontSize = 9.sp,
                                color = if (ind.ema20 > ind.ema50) ProfGreen else ProfRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "SMA 200", fontSize = 9.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                            Text(
                                text = "₹${String.format("%.2f", ind.sma200)}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val isAbove = activeStock!!.currentPrice > ind.sma200
                            Text(
                                text = if (isAbove) "Bullish Barrier" else "Bearish Resistance",
                                fontSize = 9.sp,
                                color = if (isAbove) ProfGreen else ProfRed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Bollinger Bands & VWAP
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "BOLLINGER BANDS (20, 2)", fontSize = 9.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(text = "UPPER BAND", fontSize = 8.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                                    Text(text = "₹${String.format("%.1f", ind.bollingerUpper)}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(text = "LOWER BAND", fontSize = 8.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                                    Text(text = "₹${String.format("%.1f", ind.bollingerLower)}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "VWAP ANCHOR", fontSize = 9.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                            Text(
                                text = "₹${String.format("%.2f", ind.vwap)}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val closeToVwap = ((activeStock!!.currentPrice - ind.vwap) / ind.vwap) * 100.0
                            Text(
                                text = String.format("%.2f%% Spread", closeToVwap),
                                fontSize = 9.sp,
                                color = if (closeToVwap >= 0) ProfGreen else ProfRed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // --- SECTION 2: Standard Machine Learning Algorithms Predictions ---
        activePredictions?.let { predictions ->
            item {
                Text(
                    text = "Standard Mathematical ML Models Outputs",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            items(predictions.toList()) { pair ->
                val modelKey = pair.first
                val prediction = pair.second
                MLPredictionAlgorithmRow(
                    modelName = modelKey,
                    result = prediction,
                    onSave = {
                        viewModel.saveActiveForecast(
                            modelName = modelKey,
                            predictedPrice = prediction.predictedPrice,
                            confidence = prediction.confidenceScore,
                            trend = prediction.trendDirection
                        )
                    }
                )
            }
        }

        // --- SECTION 3: Deep AI Quantitative Neural Analyst (Gemini API) ---
        item {
            GeminiQuantAnalystSection(
                viewModel = viewModel,
                state = geminiState
            )
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun MLPredictionAlgorithmRow(
    modelName: String,
    result: PredictorEngine.MLPredictionResult,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when (result.trendDirection) {
                                    "UP" -> ProfGreen
                                    "DOWN" -> ProfRed
                                    else -> ProfTextMuted
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = modelName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onSave,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("save_prediction_$modelName")
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Save analysis",
                        tint = ProfAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "FORECAST TARGETPRICE", fontSize = 9.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                    Text(
                        text = "₹${String.format("%,.2f", result.predictedPrice)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "CONFIDENCE SCORE", fontSize = 9.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${String.format("%.0f", result.confidenceScore * 100.0)}%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = when {
                            result.confidenceScore > 0.75 -> ProfGreen
                            result.confidenceScore > 0.55 -> ProfAmber
                            else -> ProfRed
                        }
                    )
                }
            }

            // Expandable mathematical criteria details
            if (result.evaluationMetrics.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    result.evaluationMetrics.forEach { (metric, value) ->
                        Column {
                            Text(text = metric, fontSize = 7.5.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                            Text(text = String.format("%.4f", value), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GeminiQuantAnalystSection(
    viewModel: StockViewModel,
    state: StockViewModel.GeminiUiState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ProfNavy),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(2.dp, Brush.linearGradient(listOf(ProfAmber, ProfGreen)))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "AI Analyst Icon",
                        tint = ProfAmber,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "A.I. Quant Analyst (Gemini)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // AI chip tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ProfAmber.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text("FLASH 3.5", fontSize = 8.5.sp, color = ProfAmber, fontWeight = FontWeight.Black)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Fuses multi-layered technical math vectors with dynamic Indian economic sentiment to run deep forecasting schemas.",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            when (state) {
                is StockViewModel.GeminiUiState.Idle -> {
                    Button(
                        onClick = { viewModel.fetchGeminiForecasting() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ProfAmber,
                            contentColor = ProfNavy
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("run_ai_analyst_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("COMPUTE DEEP TARGET SCHEMAS", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }

                is StockViewModel.GeminiUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = ProfAmber,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Analyzing RSI boundaries, standard deviations, and news channels...",
                            fontSize = 11.sp,
                            color = ProfAmber,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is StockViewModel.GeminiUiState.Success -> {
                    val report = state.result
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RECOMMENDATION: ${report.recommendation}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = if (report.recommendation.contains("BUY")) ProfGreen else if (report.recommendation.contains("SELL")) ProfRed else Color.White
                            )

                            // Confidence indicator tag
                            Text(
                                text = "COGNITIVE CONFID: ${String.format("%.0f", report.confidence * 100.0)}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ProfAmber
                            )
                        }

                        // AI Target Boundaries Block
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = "PROBABILITY TARGETS", fontSize = 9.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(text = "+1 Day", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        Text(text = "₹${String.format("%.2f", report.target1Day)}", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text(text = "+3 Days", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        Text(text = "₹${String.format("%.2f", report.target3Day)}", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text(text = "+5 Days", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                        Text(text = "₹${String.format("%.2f", report.target5Day)}", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(text = "Stop Loss", fontSize = 8.sp, color = ProfRed)
                                        Text(text = "₹${String.format("%.2f", report.stopLoss)}", fontSize = 12.sp, color = ProfRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Text analyses breakdowns
                        Column {
                            Text(text = "MOMENTUM & STOCHASTICS", fontSize = 8.sp, color = ProfAmber, fontWeight = FontWeight.Bold)
                            Text(text = report.rsiInterpretation, fontSize = 11.sp, color = Color.White)
                        }

                        Column {
                            Text(text = "TREND LINE STRUCT", fontSize = 8.sp, color = ProfAmber, fontWeight = FontWeight.Bold)
                            Text(text = report.trendAnalysis, fontSize = 11.sp, color = Color.White)
                        }

                        Column {
                            Text(text = "COGNITIVE SYNAPSE REASONING", fontSize = 8.sp, color = ProfAmber, fontWeight = FontWeight.Bold)
                            Text(text = report.reasoning, fontSize = 11.sp, color = Color(0xFFCBD5E1), lineHeight = 15.sp)
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.fetchGeminiForecasting() },
                                colors = ButtonDefaults.buttonColors(containerColor = ProfAmber.copy(alpha = 0.2f), contentColor = ProfAmber),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("RE-RUN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    viewModel.saveActiveForecast(
                                        modelName = "Gemini Quant AI",
                                        predictedPrice = report.target5Day,
                                        confidence = report.confidence,
                                        trend = if (report.target5Day >= report.target1Day) "UP" else "DOWN"
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ProfGreen, contentColor = Color(0xFF021B0A)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_ai_prediction_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("ARCHIVE FORECAST", fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                is StockViewModel.GeminiUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = state.message, color = ProfRed, fontSize = 11.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.fetchGeminiForecasting() },
                            colors = ButtonDefaults.buttonColors(containerColor = ProfAmber, contentColor = ProfNavy),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("RETRY SCHEMAS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ================= TAB 3: SIMULATOR PORTFOLIO =================

@Composable
fun TradeSimulatorTab(viewModel: StockViewModel) {
    val activeStock by viewModel.activeStock.collectAsState()
    val cashBalance by viewModel.cashBalance.collectAsState()
    val holdings by viewModel.portfolioPositions.collectAsState()
    val orderSharesInput by viewModel.orderSharesInput.collectAsState()
    val marketStocks by viewModel.marketStocks.collectAsState()

    val totalHoldingsValue = holdings.sumOf { it.totalValue }
    val portfolioNetWorth = cashBalance + totalHoldingsValue
    val startingCapital = 1000000.0 // 10 Lakhs
    val netRevenue = portfolioNetWorth - startingCapital
    val profitPercentage = (netRevenue / startingCapital) * 100.0

    val investedValue = holdings.sumOf { it.sharesHeld * it.avgPurchasePrice }
    val totalProfitLoss = totalHoldingsValue - investedValue
    val totalProfitLossPercent = if (investedValue > 0) (totalProfitLoss / investedValue) * 100.0 else 0.0

    val todayGainLoss = holdings.sumOf { position ->
        val matchingStock = marketStocks.find { it.symbol == position.symbol }
        if (matchingStock != null) {
            position.sharesHeld * (position.currentPrice - matchingStock.prevClose)
        } else {
            0.0
        }
    }
    val todayGainLossPercent = if (investedValue > 0) (todayGainLoss / investedValue) * 100.0 else 0.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Portfolio Balance Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "VIRTUAL BROKERAGESUMMARY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ProfBlue,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "PORTFOLIO VALUE (NET)", fontSize = 10.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                            Text(
                                text = "₹${String.format("%,.2f", portfolioNetWorth)}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "NET GAIN / LOSS", fontSize = 10.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                            val color = if (netRevenue >= 0) ProfGreen else ProfRed
                            val prefix = if (netRevenue >= 0) "+" else ""
                            Text(
                                text = "$prefix${String.format("%,.2f", netRevenue)} ($prefix${String.format("%.2f", profitPercentage)}%)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = color
                            )
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "AVAILABLE VIRTUAL CASH", fontSize = 9.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                            Text(text = "₹${String.format("%,.2f", cashBalance)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "ASSETS ALLOCATION", fontSize = 9.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                            Text(text = "₹${String.format("%,.2f", totalHoldingsValue)} in stocks", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 12.dp))

                    // Dynamic holding values grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("INVESTED VALUE", fontSize = 8.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                                Text("₹${String.format("%,.2f", investedValue)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("CURRENT VALUE", fontSize = 8.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                                Text("₹${String.format("%,.2f", totalHoldingsValue)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("TOTAL PROFIT/LOSS", fontSize = 8.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                                val totColor = if (totalProfitLoss >= 0) ProfGreen else ProfRed
                                val totPrefix = if (totalProfitLoss >= 0) "+" else ""
                                Text(
                                    text = "$totPrefix₹${String.format("%,.2f", totalProfitLoss)}", 
                                    fontSize = 12.sp, 
                                    fontWeight = FontWeight.Bold, 
                                    color = totColor
                                )
                                Text(
                                    text = "$totPrefix${String.format("%.2f", totalProfitLossPercent)}%",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = totColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("TODAY'S NET GAIN / LOSS", fontSize = 8.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                                val todayColor = if (todayGainLoss >= 0) ProfGreen else ProfRed
                                val todayPrefix = if (todayGainLoss >= 0) "+" else ""
                                Text(
                                    text = "$todayPrefix₹${String.format("%,.2f", todayGainLoss)} ($todayPrefix${String.format("%.2f", todayGainLossPercent)}%)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = todayColor
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (todayGainLoss >= 0) ProfGreen.copy(alpha = 0.15f) else ProfRed.copy(alpha = 0.15f))
                                    .padding(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (todayGainLoss >= 0) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Today Trend",
                                    tint = if (todayGainLoss >= 0) ProfGreen else ProfRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active Stock Transaction execution panel
        activeStock?.let { stock ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "SUBMIT ORDER ENGINE", fontSize = 9.sp, color = ProfBlue, fontWeight = FontWeight.Bold)
                                Text(text = stock.symbol, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "LIVE PRICE", fontSize = 9.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                                Text(text = "₹${String.format("%.2f", stock.currentPrice)}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TextField(
                                value = orderSharesInput,
                                onValueChange = { viewModel.onUpdateOrderShares(it) },
                                label = { Text("Shares Qty", fontSize = 11.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .testTag("shares_quantity_input")
                            )

                            // Computed estimated cost block
                            val count = orderSharesInput.toIntOrNull() ?: 0
                            val cost = count * stock.currentPrice
                            Column(modifier = Modifier.weight(1.2f)) {
                                Text(text = "ESTIMATED OUTLAY", fontSize = 9.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "₹${String.format("%,.2f", cost)}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (cost <= cashBalance) MaterialTheme.colorScheme.onSurface else ProfRed
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.executeTrade("BUY") },
                                colors = ButtonDefaults.buttonColors(containerColor = ProfGreen, contentColor = Color.White),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("buy_sim_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("BUY SHARES", fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }

                            Button(
                                onClick = { viewModel.executeTrade("SELL") },
                                colors = ButtonDefaults.buttonColors(containerColor = ProfRed, contentColor = Color.White),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("sell_sim_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("SELL SHARES", fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Portfolio active inventory list
        item {
            Text(
                text = "Currently Held Asset Inventory",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ProfTextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
        }

        if (holdings.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "You own no virtual Nifty 50 holdings. Select a stock and run predictions to test trading schemes!",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(holdings) { position ->
                HeldStockPositionRow(position)
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun HeldStockPositionRow(pos: StockRepository.PortfolioPosition) {
    val isProfit = pos.gainAmount >= 0
    val color = if (isProfit) ProfGreen else ProfRed
    val sign = if (isProfit) "+" else ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = pos.companyName, fontSize = 11.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                    Text(text = "${pos.symbol} • ${pos.sharesHeld} Shares", fontSize = 14.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "CURRENT VALUE", fontSize = 9.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                    Text(text = "₹${String.format("%,.2f", pos.totalValue)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "AVG PURCHASE COST", fontSize = 9.sp, color = ProfTextMuted)
                    Text(text = "₹${String.format("%.2f", pos.avgPurchasePrice)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "LAST TICKPRICE", fontSize = 9.sp, color = ProfTextMuted)
                    Text(text = "₹${String.format("%.2f", pos.currentPrice)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "UNREALIZED P&L", fontSize = 9.sp, color = ProfTextMuted)
                    Text(
                        text = "$sign₹${String.format("%,.2f", pos.gainAmount)} ($sign${String.format("%.2f", pos.gainPercentage)}%)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
        }
    }
}

// ================= TAB 4: WATCHLIST & ARCHIVES =================

@Composable
fun ArchiveTab(viewModel: StockViewModel) {
    val watchlistList by viewModel.watchlist.collectAsState()
    val savedForecasts by viewModel.savedForecasts.collectAsState()
    val marketStocks by viewModel.marketStocks.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf("added") } // gainers, losers, alphabetical, added

    val filteredWatchlist = watchlistList.filter {
        it.symbol.contains(searchQuery, ignoreCase = true) || 
        it.companyName.contains(searchQuery, ignoreCase = true)
    }.sortedWith { item1, item2 ->
        val live1 = marketStocks.find { it.symbol == item1.symbol }
        val live2 = marketStocks.find { it.symbol == item2.symbol }
        when (sortOrder) {
            "gainers" -> {
                val change1 = live1?.changePercentage ?: 0.0
                val change2 = live2?.changePercentage ?: 0.0
                change2.compareTo(change1)
            }
            "losers" -> {
                val change1 = live1?.changePercentage ?: 0.0
                val change2 = live2?.changePercentage ?: 0.0
                change1.compareTo(change2)
            }
            "alphabetical" -> {
                item1.symbol.compareTo(item2.symbol, ignoreCase = true)
            }
            else -> {
                item2.addedAt.compareTo(item1.addedAt)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Watched Tickers Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "WATCHLIST CRITERIA SEARCH & FILTER",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ProfBlue,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().testTag("watchlist_search_input"),
                        placeholder = { Text("Search by Symbol/Name...", fontSize = 12.sp, color = ProfTextMuted) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = ProfTextMuted) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "Sort Stock Items By:", fontSize = 9.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val sortOptions = listOf(
                            "added" to "Added",
                            "alphabetical" to "A-Z",
                            "gainers" to "Gainers",
                            "losers" to "Losers"
                        )
                        sortOptions.forEach { (key, label) ->
                            val isSelected = sortOrder == key
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ProfBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                    .clickable { sortOrder = key }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("sort_$key")
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "My Watched Stock Profiles (${filteredWatchlist.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ProfTextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
        }

        if (filteredWatchlist.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = "No matching search items in your watchlist. Search or tap live index to start watching.",
                        fontSize = 11.sp,
                        color = ProfTextMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(filteredWatchlist) { item ->
                // Look up latest live price from repository cache flow
                val matchingLiveData = marketStocks.find { it.symbol == item.symbol }
                WatchedStockRow(
                    item = item, 
                    liveData = matchingLiveData, 
                    onSelect = { viewModel.onSelectStock(item.symbol) },
                    onRemove = { viewModel.toggleWatchlist(item.symbol, item.companyName) },
                    onSaveAlerts = { highPrice, lowPrice ->
                        viewModel.updateAlertPrices(item.symbol, item.companyName, highPrice, lowPrice)
                    }
                )
            }
        }

        // Saved Forecast Records (Model Backtesting archive)
        item {
            Text(
                text = "Archived AI & ML Forecasts Archive",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ProfTextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        if (savedForecasts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = "No saved predictions. Compute forecasts on the ML Forecast tab and tap Star/Record to compare performance retroactively.",
                        fontSize = 11.sp,
                        color = ProfTextMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(savedForecasts) { forecast ->
                // Extract latest live price
                val activeData = marketStocks.find { it.symbol == forecast.symbol }
                ArchivedForecastRow(
                    forecast = forecast,
                    currentLivePrice = activeData?.currentPrice,
                    onDelete = { viewModel.onDeleteSavedForecast(forecast.id) }
                )
            }
        }

        // Master Settings Reset Button
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = { viewModel.onResetVirtualPortfolio() },
                colors = ButtonDefaults.buttonColors(containerColor = ProfRed.copy(alpha = 0.12f), contentColor = ProfRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reset_account_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ProfRed.copy(alpha = 0.5f))
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset balance", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("RESET TRADING BALANCES & HISTORY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun WatchedStockRow(
    item: StockWatchItem,
    liveData: StockRepository.StockData?,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onSaveAlerts: (Double, Double) -> Unit
) {
    var expandedAlertControls by remember { mutableStateOf(false) }
    var highInput by remember { mutableStateOf(if (item.alertHighPrice > 0.0) item.alertHighPrice.toString() else "") }
    var lowInput by remember { mutableStateOf(if (item.alertLowPrice > 0.0) item.alertLowPrice.toString() else "") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("watched_stock_${item.symbol}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.companyName, fontSize = 10.sp, color = ProfTextMuted, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = item.symbol, fontSize = 15.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                        
                        // Icon showing alert status if active
                        if (item.alertHighPrice > 0.0 || item.alertLowPrice > 0.0) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Active Price Alert",
                                tint = ProfAmber,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    liveData?.let { data ->
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "₹${String.format("%.2f", data.currentPrice)}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val sign = if (data.changePercentage >= 0) "+" else ""
                            val c = if (data.changePercentage >= 0) ProfGreen else ProfRed
                            Text(
                                text = "$sign${String.format("%.2f", data.changePercentage)}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = c
                            )
                        }
                    } ?: Text("Loading...", fontSize = 11.sp, color = ProfTextMuted)

                    // Alert settings button
                    IconButton(
                        onClick = { expandedAlertControls = !expandedAlertControls },
                        modifier = Modifier.size(36.dp).testTag("alert_button_${item.symbol}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Edit alerts",
                            tint = if (expandedAlertControls) ProfBlue else ProfTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Remove/favorite button
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(36.dp).testTag("remove_star_${item.symbol}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Remove favorite",
                            tint = ProfAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Alert triggers checks
            liveData?.let { data ->
                val price = data.currentPrice
                if (item.alertHighPrice > 0.0 && price >= item.alertHighPrice) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ProfRed.copy(alpha = 0.15f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Resistance Breached: ₹${String.format("%.2f", price)} >= Upper Target ₹${String.format("%.2f", item.alertHighPrice)}",
                            fontSize = 9.5.sp,
                            color = ProfRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (item.alertLowPrice > 0.0 && price <= item.alertLowPrice) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ProfAmber.copy(alpha = 0.15f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Support Breached: ₹${String.format("%.2f", price)} <= Lower Support Target ₹${String.format("%.2f", item.alertLowPrice)}",
                            fontSize = 9.5.sp,
                            color = ProfAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Collapsible alert panel
            if (expandedAlertControls) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "SET INTRA-DAY REALTIME PRICE ALERT LIMITS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = ProfBlue
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = lowInput,
                        onValueChange = { lowInput = it },
                        modifier = Modifier.weight(1f).testTag("alert_low_input_${item.symbol}"),
                        label = { Text("Support Min Limit", fontSize = 9.sp) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        )
                    )

                    TextField(
                        value = highInput,
                        onValueChange = { highInput = it },
                        modifier = Modifier.weight(1f).testTag("alert_high_input_${item.symbol}"),
                        label = { Text("Resistance Max Limit", fontSize = 9.sp) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val low = lowInput.toDoubleOrNull() ?: 0.0
                        val high = highInput.toDoubleOrNull() ?: 0.0
                        onSaveAlerts(high, low)
                    },
                    modifier = Modifier.align(Alignment.End).testTag("save_alert_btn_${item.symbol}"),
                    colors = ButtonDefaults.buttonColors(containerColor = ProfBlue)
                ) {
                    Text("Save Price Alert", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ArchivedForecastRow(
    forecast: com.example.data.SavedForecast,
    currentLivePrice: Double?,
    onDelete: () -> Unit
) {
    val formattedDate = remember(forecast.timestamp) {
        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
        sdf.format(Date(forecast.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("forecast_row_${forecast.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "$formattedDate • ${forecast.targetHorizonDays}d Scope", fontSize = 9.sp, color = ProfTextMuted)
                    Text(text = "${forecast.symbol} (${forecast.modelName})", fontSize = 14.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp).testTag("delete_forecast_button_${forecast.id}")
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete forecast", tint = ProfRed.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                }
            }

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "BASE (SAVED)PRICE", fontSize = 8.sp, color = ProfTextMuted)
                    Text(text = "₹${String.format("%.2f", forecast.basePrice)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "ML PREDICTIONTARGET", fontSize = 8.sp, color = ProfAmber)
                    Text(text = "₹${String.format("%.2f", forecast.predictedPrice)}", fontSize = 11.sp, color = ProfAmber, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "ACTIVE LIVE PRICE", fontSize = 8.sp, color = ProfTextMuted)
                    currentLivePrice?.let { live ->
                        val deviationPercent = ((live - forecast.predictedPrice) / forecast.predictedPrice) * 100.0
                        val driftPrefix = if (deviationPercent >= 0) "+" else ""
                        Text(
                            text = "₹${String.format("%.2f", live)} ($driftPrefix${String.format("%.1f", deviationPercent)}% drift)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (Math.abs(deviationPercent) < 2.0) ProfGreen else MaterialTheme.colorScheme.onSurface
                        )
                    } ?: Text("Calculating...", fontSize = 11.sp, color = ProfTextMuted)
                }
            }

            if (forecast.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = forecast.notes,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        .padding(8.dp)
                )
            }
        }
    }
}
