package com.example.data

import android.content.Context
import androidx.room.Room
import com.example.ml.PredictorEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.sin
import kotlin.random.Random

class StockRepository private constructor(context: Context) {

    // --- Database instance ---
    private val db = Room.databaseBuilder(
        context.applicationContext,
        StockDatabase::class.java,
        "nifty_predict_database.db"
    ).build()

    val watchlistDao = db.watchlistDao()
    val tradeDao = db.tradeDao()
    val forecastDao = db.forecastDao()
    private val cashDao = db.portfolioCashDao()

    // --- State and flows ---
    private val _marketStocks = MutableStateFlow<List<StockData>>(emptyList())
    val marketStocks: StateFlow<List<StockData>> = _marketStocks.asStateFlow()

    private val _newsFeed = MutableStateFlow<List<NewsAlert>>(emptyList())
    val newsFeed: StateFlow<List<NewsAlert>> = _newsFeed.asStateFlow()

    // Starts with 10 Lakhs Indian Rupees (₹1,000,000.00)
    private val _cashBalance = MutableStateFlow(1000000.0)
    val cashBalance: StateFlow<Double> = _cashBalance.asStateFlow()

    private val _isMarketOpen = MutableStateFlow(false)
    val isMarketOpen: StateFlow<Boolean> = _isMarketOpen.asStateFlow()

    private val _lastUpdatedTime = MutableStateFlow("")
    val lastUpdatedTime: StateFlow<String> = _lastUpdatedTime.asStateFlow()

    // --- API Configuration States ---
    private val prefs = context.applicationContext.getSharedPreferences("nifty_api_config", Context.MODE_PRIVATE)

    private val _apiSource = MutableStateFlow(prefs.getString("api_source", "twelvedata").let { if (it == "simulation") "simulation" else "twelvedata" })
    val apiSource: StateFlow<String> = _apiSource.asStateFlow()

    private val _twelveDataKey = MutableStateFlow(prefs.getString("twelve_data_key", "") ?: "")
    val twelveDataKey: StateFlow<String> = _twelveDataKey.asStateFlow()

    private val _apiStatusMessage = MutableStateFlow("Initializing live feed...")
    val apiStatusMessage: StateFlow<String> = _apiStatusMessage.asStateFlow()

    fun saveApiConfig(source: String, twelveKey: String) {
        prefs.edit().apply {
            putString("api_source", source)
            putString("twelve_data_key", twelveKey)
            apply()
        }
        _apiSource.value = source
        _twelveDataKey.value = twelveKey
        _apiStatusMessage.value = "Config updated. Connecting..."
    }

    data class StockData(
        val symbol: String,
        val name: String,
        val currentPrice: Double,
        val prevClose: Double,
        val open: Double,
        val high: Double,
        val low: Double,
        val volume: Double,
        val changePercentage: Double,
        val priceHistory: List<Double>, // Last 100 closing prices
        val volumeHistory: List<Double>,
        val lastTickDirection: Int = 0 // 1 = Up, -1 = Down, 0 = Flat
    )

    data class NewsAlert(
        val id: String = UUID.randomUUID().toString(),
        val title: String,
        val source: String,
        val time: String,
        val sentiment: String, // "BULLISH", "BEARISH", "NEUTRAL"
        val affectedSymbol: String? = null
    )

    init {
        // Initialize mock stock list with realistic price histories
        seedStocks()
        // Initialize seed Indian market news alerts
        seedNews()
        // Initialize balance in database/memory
        CoroutineScope(Dispatchers.IO).launch {
            initPortfolioBalance()
        }
        // Start continuous background market price fluctuate ticks updates
        startMarketSimulation()
    }

    private suspend fun initPortfolioBalance() {
        val existing = cashDao.getCashBalance()
        if (existing == null) {
            cashDao.updateCashBalance(PortfolioCash(cashBalance = 1000000.0))
            _cashBalance.value = 1000000.0
        } else {
            _cashBalance.value = existing.cashBalance
        }
    }

    private fun seedStocks() {
        val seeded = listOf(
            createInitialStock("NIFTY 50", "NSE Index", 23512.40, 0.0001),
            createInitialStock("RELIANCE", "Reliance Industries Ltd", 2942.50, 0.0002),
            createInitialStock("TCS", "Tata Consultancy Services Ltd", 3845.20, 0.00015),
            createInitialStock("HDFCBANK", "HDFC Bank Ltd", 1622.80, -0.00005),
            createInitialStock("INFY", "Infosys Ltd", 1532.70, 0.0001),
            createInitialStock("ICICIBANK", "ICICI Bank Ltd", 1121.30, 0.00025),
            createInitialStock("ARTL", "Bharti Airtel Ltd", 1381.10, 0.0003),
            createInitialStock("ITC", "ITC Ltd", 432.85, 0.00005),
            createInitialStock("SBIN", "State Bank of India", 829.40, -0.0001),
            createInitialStock("LT", "Larsen & Toubro Ltd", 3542.90, 0.00018)
        )
        _marketStocks.value = seeded
    }

    private fun seedNews() {
        _newsFeed.value = listOf(
            NewsAlert(
                title = "RBI maintains interest rate pause, hints at transition to neutral stance by Q3",
                source = "Moneycontrol",
                time = "10 mins ago",
                sentiment = "BULLISH"
            ),
            NewsAlert(
                title = "Reliance Retail reports landmark 12% revenue growth in latest quarterly files",
                source = "Economic Times",
                time = "25 mins ago",
                sentiment = "BULLISH",
                affectedSymbol = "RELIANCE"
            ),
            NewsAlert(
                title = "Infosys secures mega ${'$'}1.2 Billion AI transformation deal with top global bank",
                source = "Livemint",
                time = "1 hour ago",
                sentiment = "BULLISH",
                affectedSymbol = "INFY"
            ),
            NewsAlert(
                title = "SBI credit quality metrics register record improvement with Net NPAs down to 0.4%",
                source = "Financial Express",
                time = "2 hours ago",
                sentiment = "BULLISH",
                affectedSymbol = "SBIN"
            ),
            NewsAlert(
                title = "Monsoon sweeps across agricultural belts in Central India, promising rural demand boom",
                source = "Business Standard",
                time = "3 hours ago",
                sentiment = "BULLISH"
            ),
            NewsAlert(
                title = "TCS notes margins pressure on rising domestic deployment integration costs",
                source = "Economic Times",
                time = "4 hours ago",
                sentiment = "BEARISH",
                affectedSymbol = "TCS"
            ),
            NewsAlert(
                title = "HDFC Bank receives final regulatory clearances for major restructuring of capital base",
                source = "BloombergQuint",
                time = "5 hours ago",
                sentiment = "NEUTRAL",
                affectedSymbol = "HDFCBANK"
            )
        )
    }

    private fun createInitialStock(
        symbol: String,
        name: String,
        basePrice: Double,
        dailyTrend: Double
    ): StockData {
        val historyLimit = 100
        val prices = mutableListOf<Double>()
        val volumes = mutableListOf<Double>()

        // Generate deterministic history using random walk with an underlying trend
        val rnd = Random(symbol.hashCode())
        var activePrice = basePrice * 0.90 // Start slightly lower, walk upwards

        for (i in 0 until historyLimit) {
            // Add a sine wave component to model cyclic market oscillations
            val sineVal = sin(i.toDouble() / 15.0) * (basePrice * 0.03)
            val noise = (rnd.nextDouble() - 0.48) * (basePrice * 0.015)
            val stepTrend = activePrice * dailyTrend
            activePrice += noise + stepTrend + (sineVal / 10.0)
            prices.add(activePrice)

            // Generate representative trading volume (e.g. around 2 Lakh to 5 Million shares)
            val baseVol = if (symbol == "NIFTY 50") 150000.0 else 500000.0
            val volNoise = rnd.nextDouble() * 400000.0
            volumes.add(baseVol + volNoise)
        }

        val open = prices[prices.size - 2]
        val currentPrice = prices.last()
        val prevClose = prices[prices.size - 2]
        val changePercentage = ((currentPrice - prevClose) / prevClose) * 100.0

        val maxPast = prices.takeLast(20).maxOrNull() ?: currentPrice
        val minPast = prices.takeLast(20).minOrNull() ?: currentPrice

        return StockData(
            symbol = symbol,
            name = name,
            currentPrice = currentPrice,
            prevClose = prevClose,
            open = open,
            high = Math.max(currentPrice, open) * (1.0 + (rnd.nextDouble() * 0.005)),
            low = Math.min(currentPrice, open) * (1.0 - (rnd.nextDouble() * 0.005)),
            volume = volumes.last(),
            changePercentage = changePercentage,
            priceHistory = prices,
            volumeHistory = volumes
        )
    }

    fun checkIsNSEMarketOpen(): Boolean {
        val istZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        val cal = java.util.Calendar.getInstance(istZone)
        
        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
        if (dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY) {
            return false
        }
        
        val month = cal.get(java.util.Calendar.MONTH) // 0-indexed: 0 = Jan, 11 = Dec
        val date = cal.get(java.util.Calendar.DAY_OF_MONTH)
        
        // Check standard Indian / NSE market public holidays
        if (month == java.util.Calendar.JANUARY && date == 26) return false // Republic Day
        if (month == java.util.Calendar.APRIL && date == 14) return false   // Ambedkar Jayanti
        if (month == java.util.Calendar.MAY && date == 1) return false     // Maharashtra Day
        if (month == java.util.Calendar.AUGUST && date == 15) return false  // Independence Day
        if (month == java.util.Calendar.OCTOBER && date == 2) return false   // Gandhi Jayanti
        if (month == java.util.Calendar.DECEMBER && date == 25) return false // Christmas
        
        // NSE hour checking: 9:15 AM to 3:30 PM (15:30) IST
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = cal.get(java.util.Calendar.MINUTE)
        val timeMinutes = hour * 60 + minute
        val startMarket = 9 * 60 + 15
        val endMarket = 15 * 60 + 30
        
        return timeMinutes in startMarket..endMarket
    }

    private fun startMarketSimulation() {
        val apiService = TwelveDataApiService.create()

        val twelveDataSymbolMap = mapOf(
            "RELIANCE" to "RELIANCE",
            "TCS" to "TCS",
            "HDFCBANK" to "HDFCBANK",
            "INFY" to "INFY",
            "ICICIBANK" to "ICICIBANK",
            "ARTL" to "BHARTIARTL",
            "ITC" to "ITC",
            "SBIN" to "SBIN",
            "LT" to "LT"
        )

        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                val isOpen = checkIsNSEMarketOpen()
                _isMarketOpen.value = isOpen

                val currentSource = _apiSource.value
                var fetchSuccess = false
                val mappedQuotes = mutableMapOf<String, LiveAPIQuote>()

                if (currentSource != "simulation") {
                    try {
                        val key = if (_twelveDataKey.value.isNotEmpty()) {
                            _twelveDataKey.value
                        } else {
                            com.example.BuildConfig.TWELVE_DATA_API_KEY
                        }

                        if (key.isEmpty() || key == "MY_TWELVE_DATA_API_KEY") {
                            _apiStatusMessage.value = "Twelve Data Error: Missing API Key"
                        } else {
                            val symbolParams = twelveDataSymbolMap.values.joinToString(",")
                            val responseBody = apiService.getMultiQuotes(
                                symbols = symbolParams,
                                exchange = "NSE",
                                apiKey = key
                            )
                            val body = responseBody.string()
                            android.util.Log.d("StockRepository", "Twelve Data Response: $body")

                            if (body.contains("\"status\":\"error\"") || body.contains("\"status\": \"error\"")) {
                                val json = org.json.JSONObject(body)
                                _apiStatusMessage.value = "Twelve Data: " + json.optString("message", "API Error")
                            } else {
                                val json = org.json.JSONObject(body)
                                twelveDataSymbolMap.forEach { (stockSymbol, apiSymbol) ->
                                    val obj = json.optJSONObject(apiSymbol)
                                    if (obj != null) {
                                        val price = obj.optDouble("close", Double.NaN)
                                        if (!price.isNaN()) {
                                            mappedQuotes[stockSymbol] = LiveAPIQuote(
                                                symbol = stockSymbol,
                                                price = price,
                                                prevClose = obj.optDouble("previous_close", price),
                                                open = obj.optDouble("open", price),
                                                high = obj.optDouble("high", price),
                                                low = obj.optDouble("low", price),
                                                volume = obj.optDouble("volume", 0.0),
                                                changePercent = obj.optDouble("percent_change", 0.0)
                                            )
                                        }
                                    }
                                }
                                fetchSuccess = mappedQuotes.isNotEmpty()
                                if (fetchSuccess) {
                                    _apiStatusMessage.value = "Connected: Twelve Data Live NSE"
                                } else {
                                    _apiStatusMessage.value = "Twelve Data: Parse failure"
                                }
                            }
                        }
                    } catch (e: Exception) {
                        _apiStatusMessage.value = "Twelve Data: Network Error"
                        android.util.Log.e("StockRepository", "Network Error: ${e.localizedMessage}")
                    }
                }

                val current = _marketStocks.value
                val updated = current.map { stock ->
                    if (stock.symbol == "NIFTY 50" && !mappedQuotes.containsKey("NIFTY 50")) {
                        stock
                    } else {
                        val liveQuote = mappedQuotes[stock.symbol]
                        if (fetchSuccess && liveQuote != null) {
                            val direction = when {
                                liveQuote.price > stock.currentPrice -> 1
                                liveQuote.price < stock.currentPrice -> -1
                                else -> 0
                            }

                            val hist = stock.priceHistory.toMutableList()
                            if (hist.isEmpty()) hist.add(liveQuote.price)
                            else {
                                if (hist.size >= 100) hist.removeAt(0)
                                hist.add(liveQuote.price)
                            }

                            val volHist = stock.volumeHistory.toMutableList()
                            if (volHist.isEmpty()) volHist.add(liveQuote.volume)
                            else {
                                if (volHist.size >= 100) volHist.removeAt(0)
                                volHist.add(liveQuote.volume)
                            }

                            stock.copy(
                                currentPrice = liveQuote.price,
                                prevClose = liveQuote.prevClose,
                                open = liveQuote.open,
                                high = liveQuote.high,
                                low = liveQuote.low,
                                volume = liveQuote.volume,
                                changePercentage = liveQuote.changePercent,
                                priceHistory = hist,
                                volumeHistory = volHist,
                                lastTickDirection = direction
                            )
                        } else {
                            if (isOpen || !fetchSuccess) {
                                val rnd = Random.Default
                                val continuation = if (stock.changePercentage > 0) 0.52 else 0.48
                                val direction = if (rnd.nextDouble() < continuation) 1 else -1

                                val changePercent = rnd.nextDouble() * 0.0018
                                val tickDelta = stock.currentPrice * changePercent * direction
                                val newPrice = (stock.currentPrice + tickDelta).coerceAtLeast(1.0)

                                val newHigh = if (newPrice > stock.high) newPrice else stock.high
                                val newLow = if (newPrice < stock.low) newPrice else stock.low
                                val volumeDelta = rnd.nextDouble() * 5000.0

                                val hist = stock.priceHistory.toMutableList()
                                if (hist.isEmpty()) hist.add(newPrice)
                                else {
                                    if (hist.size >= 100) hist.removeAt(0)
                                    hist.add(newPrice)
                                }

                                val volHist = stock.volumeHistory.toMutableList()
                                if (volHist.isEmpty()) volHist.add(stock.volume + volumeDelta)
                                else {
                                    if (volHist.size >= 100) volHist.removeAt(0)
                                    volHist.add(stock.volume + volumeDelta)
                                }

                                if (currentSource == "simulation") {
                                    _apiStatusMessage.value = "Connected: AI-Powered Simulated Pricing"
                                } else {
                                    _apiStatusMessage.value = "Failsafe active (Live simulation)"
                                }

                                stock.copy(
                                    currentPrice = newPrice,
                                    high = newHigh,
                                    low = newLow,
                                    volume = stock.volume + volumeDelta,
                                    changePercentage = ((newPrice - stock.prevClose) / stock.prevClose) * 100.0,
                                    priceHistory = hist,
                                    volumeHistory = volHist,
                                    lastTickDirection = direction
                                )
                            } else {
                                stock
                            }
                        }
                    }
                }

                val finalizedStocks = updated.map { stock ->
                    if (stock.symbol == "NIFTY 50" && !mappedQuotes.containsKey("NIFTY 50")) {
                        val otherStocks = updated.filter { it.symbol != "NIFTY 50" }
                        val avgPctChange = otherStocks.map { it.changePercentage }.average()
                        val previousNiftyClose = stock.prevClose
                        val newPrice = previousNiftyClose * (1.0 + avgPctChange / 100.0)

                        val hist = stock.priceHistory.toMutableList()
                        if (hist.isEmpty()) hist.add(newPrice)
                        else {
                            if (hist.size >= 100) hist.removeAt(0)
                            hist.add(newPrice)
                        }

                        val volHist = stock.volumeHistory.toMutableList()
                        if (volHist.isEmpty()) volHist.add(otherStocks.map { it.volume }.sum() / 3.0)
                        else {
                            if (volHist.size >= 100) volHist.removeAt(0)
                            volHist.add(otherStocks.map { it.volume }.sum() / 3.0)
                        }

                        val direction = when {
                            newPrice > stock.currentPrice -> 1
                            newPrice < stock.currentPrice -> -1
                            else -> 0
                        }

                        stock.copy(
                            currentPrice = newPrice,
                            changePercentage = avgPctChange,
                            high = if (newPrice > stock.high) newPrice else stock.high,
                            low = if (newPrice < stock.low) newPrice else stock.low,
                            volume = otherStocks.map { it.volume }.sum() / 3.0,
                            priceHistory = hist,
                            volumeHistory = volHist,
                            lastTickDirection = direction
                        )
                    } else {
                        stock
                    }
                }

                _marketStocks.value = finalizedStocks

                val now = java.util.Calendar.getInstance().time
                val formatter = java.text.SimpleDateFormat("dd MMM, hh:mm:ss a", java.util.Locale.getDefault())
                _lastUpdatedTime.value = formatter.format(now)

                if (isOpen && rndPercentageChance(15)) {
                    injectRandomNews()
                }

                val delayTime = if (isOpen) 5000L else 30000L
                delay(delayTime)
            }
        }
    }

    private fun rndPercentageChance(percentage: Int): Boolean {
        return Random.Default.nextInt(100) < percentage
    }

    private fun injectRandomNews() {
        val alerts = listOf(
            Pair("Reliance Industries launches digital banking pilot alongside Indian payments network.", "RELIANCE"),
            Pair("Tata Group signs key hardware supply pact with foreign semiconductor giant, shares rally.", "TCS"),
            Pair("HDFC Bank expands digital loan portfolio by 18% in high-margin SME sectors.", "HDFCBANK"),
            Pair("Infosys collaborates with leading cloud provider to optimize core enterprise LLM tools.", "INFY"),
            Pair("Nifty 50 approaches strong resistance zones as global tech valuations soften.", "NIFTY 50"),
            Pair("State Bank of India expands agriculture financial packages amid steady rainfall projections.", "SBIN"),
            Pair("Larsen & Toubro emerges as lowest bidder for ₹4,800 Crore green hydrogen pipeline bid.", "LT"),
            Pair("ITC enters high-growth direct-to-consumer organic foods vertical with major acquisition.", "ITC")
        )
        val selected = alerts.random()
        val isBullish = Random.nextBoolean()
        val sentiment = if (isBullish) "BULLISH" else "NEUTRAL"

        val newList = _newsFeed.value.toMutableList().apply {
            add(
                0, // Insert at top
                NewsAlert(
                    title = selected.first,
                    source = listOf("Economic Times", "Livemint", "CNBC-TV18", "Moneycontrol").random(),
                    time = "Just now",
                    sentiment = sentiment,
                    affectedSymbol = selected.second
                )
            )
        }.take(15) // Keep last 15 news items

        _newsFeed.value = newList
    }

    // --- Database actions & wrappers ---

    fun getWatchlistFlow(): Flow<List<StockWatchItem>> = watchlistDao.getAllWatchlist()

    suspend fun addToWatchlist(symbol: String, companyName: String) {
        watchlistDao.insertItem(StockWatchItem(symbol, companyName))
    }

    suspend fun removeFromWatchlist(symbol: String) {
        watchlistDao.deleteItem(symbol)
    }

    suspend fun isStockWatched(symbol: String): Boolean {
        return watchlistDao.isWatched(symbol)
    }

    // --- Saved Analytics / Predictions ---

    fun getSavedForecastsFlow(): Flow<List<SavedForecast>> = forecastDao.getAllForecasts()

    suspend fun saveForecast(
        symbol: String,
        modelName: String,
        targetDays: Int,
        basePrice: Double,
        predictedPrice: Double,
        confidence: Double,
        trend: String,
        notes: String
    ) {
        forecastDao.insertForecast(
            SavedForecast(
                symbol = symbol,
                modelName = modelName,
                targetHorizonDays = targetDays,
                basePrice = basePrice,
                predictedPrice = predictedPrice,
                confidence = confidence,
                trend = trend,
                notes = notes
            )
        )
    }

    suspend fun deleteForecast(id: Int) {
        forecastDao.deleteForecastById(id)
    }

    // --- Simulated Trades Engine ---

    fun getTradesFlow(): Flow<List<SimulationTrade>> = tradeDao.getAllTradesFlow()

    // Calculate dynamic held portfolio positions
    fun getPortfolioPositionsFlow(): Flow<List<PortfolioPosition>> {
        return getTradesFlow().map { trades ->
            val positionsMap = mutableMapOf<String, Pair<Int, Double>>() // symbol -> (net shares, total payment)

            // Compile the trade history to establish positions
            trades.reversed().forEach { trade ->
                val current = positionsMap[trade.symbol] ?: Pair(0, 0.0)
                val currentShares = current.first
                val currentTotalCost = current.second

                if (trade.type == "BUY") {
                    val newShares = currentShares + trade.shares
                    val newTotalCost = currentTotalCost + (trade.shares * trade.tradePrice)
                    positionsMap[trade.symbol] = Pair(newShares, newTotalCost)
                } else if (trade.type == "SELL") {
                    val newShares = (currentShares - trade.shares).coerceAtLeast(0)
                    // Reduce cost proportionally
                    val avgCost = if (currentShares > 0) currentTotalCost / currentShares else 0.0
                    val newTotalCost = (newShares * avgCost).coerceAtLeast(0.0)
                    positionsMap[trade.symbol] = Pair(newShares, newTotalCost)
                }
            }

            // Map and format into positions
            positionsMap.filter { it.value.first > 0 }.map { (symbol, stats) ->
                val shares = stats.first
                val totalCost = stats.second
                val avgBuyPrice = totalCost / shares

                val currentStockData = _marketStocks.value.find { it.symbol == symbol }
                val currentPrice = currentStockData?.currentPrice ?: avgBuyPrice
                val name = currentStockData?.name ?: symbol

                val marketValue = shares * currentPrice
                val totalValueGains = marketValue - totalCost
                val gainPercentage = (totalValueGains / totalCost) * 100.0

                PortfolioPosition(
                    symbol = symbol,
                    companyName = name,
                    sharesHeld = shares,
                    avgPurchasePrice = avgBuyPrice,
                    currentPrice = currentPrice,
                    totalValue = marketValue,
                    gainAmount = totalValueGains,
                    gainPercentage = gainPercentage
                )
            }
        }
    }

    suspend fun executeSimulatedTrade(
        symbol: String,
        companyName: String,
        shares: Int,
        tradePrice: Double,
        type: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (shares <= 0) return@withContext Result.failure(Exception("Shares qty must be positive"))

        val totalCost = shares * tradePrice
        val currentCash = _cashBalance.value

        if (type == "BUY") {
            if (currentCash < totalCost) {
                return@withContext Result.failure(Exception("Inadequate simulated cash. Required: ₹${String.format("%,.2f", totalCost)}, Available: ₹${String.format("%,.2f", currentCash)}"))
            }

            // Deduct money
            val remainingCash = currentCash - totalCost
            cashDao.updateCashBalance(PortfolioCash(cashBalance = remainingCash))
            _cashBalance.value = remainingCash

            // Save order trade record
            tradeDao.insertTrade(
                SimulationTrade(
                    symbol = symbol,
                    companyName = companyName,
                    shares = shares,
                    tradePrice = tradePrice,
                    type = "BUY"
                )
            )
            return@withContext Result.success("Simulated Buy Order of $shares shares $symbol completed!")
        } else {
            // SELL order: Check if enough shares are held
            val positions = getPortfolioPositionsFlow().map { list -> list.find { it.symbol == symbol } }
            // Wait, we can get active order positions directly
            val allActiveTrades = tradeDao.getAllTrades()
            var heldShares = 0
            allActiveTrades.forEach {
                if (it.symbol == symbol) {
                    if (it.type == "BUY") heldShares += it.shares
                    else heldShares -= it.shares
                }
            }

            if (heldShares < shares) {
                return@withContext Result.failure(Exception("Inadequate held stock quantity. You own $heldShares shares of $symbol, requested sell of $shares."))
            }

            // Add money back
            val newCash = currentCash + totalCost
            cashDao.updateCashBalance(PortfolioCash(cashBalance = newCash))
            _cashBalance.value = newCash

            // Save order trade record
            tradeDao.insertTrade(
                SimulationTrade(
                    symbol = symbol,
                    companyName = companyName,
                    shares = shares,
                    tradePrice = tradePrice,
                    type = "SELL"
                )
            )
            return@withContext Result.success("Simulated Sell Order of $shares shares $symbol completed!")
        }
    }

    suspend fun resetAllSimulatedBalances() = withContext(Dispatchers.IO) {
        tradeDao.clearAllTrades()
        cashDao.updateCashBalance(PortfolioCash(cashBalance = 1000000.0))
        _cashBalance.value = 1000000.0
    }

    data class PortfolioPosition(
        val symbol: String,
        val companyName: String,
        val sharesHeld: Int,
        val avgPurchasePrice: Double,
        val currentPrice: Double,
        val totalValue: Double,
        val gainAmount: Double,
        val gainPercentage: Double
    )

    companion object {
        @Volatile
        private var INSTANCE: StockRepository? = null

        fun getInstance(context: Context): StockRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = StockRepository(context)
                INSTANCE = instance
                instance
            }
        }
    }
}

data class LiveAPIQuote(
    val symbol: String,
    val price: Double,
    val prevClose: Double,
    val open: Double,
    val high: Double,
    val low: Double,
    val volume: Double,
    val changePercent: Double
)
