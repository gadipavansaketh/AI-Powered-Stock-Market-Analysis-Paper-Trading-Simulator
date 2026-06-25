package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GeminiService
import com.example.data.SavedForecast
import com.example.data.SimulationTrade
import com.example.data.StockRepository
import com.example.data.StockWatchItem
import com.example.ml.PredictorEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StockViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StockRepository.getInstance(application)

    // --- Subscribed Flows from Repository ---
    val marketStocks: StateFlow<List<StockRepository.StockData>> = repository.marketStocks
    val newsFeed: StateFlow<List<StockRepository.NewsAlert>> = repository.newsFeed
    val cashBalance: StateFlow<Double> = repository.cashBalance
    val isMarketOpen: StateFlow<Boolean> = repository.isMarketOpen
    val lastUpdatedTime: StateFlow<String> = repository.lastUpdatedTime

    // API Source configs and status messages
    val apiSource: StateFlow<String> = repository.apiSource
    val twelveDataKey: StateFlow<String> = repository.twelveDataKey
    val apiStatusMessage: StateFlow<String> = repository.apiStatusMessage

    fun updateApiConfig(source: String, twelveKey: String) {
        repository.saveApiConfig(source, twelveKey)
    }

    val watchlist: StateFlow<List<StockWatchItem>> = repository.getWatchlistFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tradeHistory: StateFlow<List<SimulationTrade>> = repository.getTradesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val portfolioPositions: StateFlow<List<StockRepository.PortfolioPosition>> = repository.getPortfolioPositionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val savedForecasts: StateFlow<List<SavedForecast>> = repository.getSavedForecastsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    // --- UI/UX States ---
    private val _selectedSymbol = MutableStateFlow("NIFTY 50")
    val selectedSymbol: StateFlow<String> = _selectedSymbol.asStateFlow()

    private val _orderSharesInput = MutableStateFlow("10")
    val orderSharesInput: StateFlow<String> = _orderSharesInput.asStateFlow()

    private val _forecastDaysHorizonInput = MutableStateFlow(5)
    val forecastDaysHorizonInput: StateFlow<Int> = _forecastDaysHorizonInput.asStateFlow()

    private val _knnKInput = MutableStateFlow(3)
    val knnKInput: StateFlow<Int> = _knnKInput.asStateFlow()

    // --- Dynamic Alerts Banner ---
    private val _tradeStatusMessage = MutableSharedFlow<String>()
    val tradeStatusMessage: SharedFlow<String> = _tradeStatusMessage.asSharedFlow()

    // --- Gemini API State Engine ---
    sealed interface GeminiUiState {
        object Idle : GeminiUiState
        object Loading : GeminiUiState
        data class Success(val result: GeminiService.GeminiPredictorResult) : GeminiUiState
        data class Error(val message: String) : GeminiUiState
    }

    private val _geminiState = MutableStateFlow<GeminiUiState>(GeminiUiState.Idle)
    val geminiState: StateFlow<GeminiUiState> = _geminiState.asStateFlow()


    // --- Derived state definitions mapping active stock selection details ---
    val activeStock: StateFlow<StockRepository.StockData?> = combine(
        marketStocks,
        selectedSymbol
    ) { stocks, symbol ->
        stocks.find { it.symbol == symbol } ?: stocks.firstBySymbol(symbol)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun List<StockRepository.StockData>.firstBySymbol(symbol: String): StockRepository.StockData? {
        return find { it.symbol == symbol } ?: firstOrNull()
    }

    // Active technical analytical indicators calculated deterministically
    val activeIndicators: StateFlow<PredictorEngine.TechnicalIndicators?> = activeStock
        .combine(marketStocks) { stock, _ ->
            if (stock != null) {
                PredictorEngine.calculateTechnicalIndicators(stock.priceHistory, stock.volumeHistory)
            } else {
                null
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Active Machine Learning Models predictions
    val activePredictions: StateFlow<Map<String, PredictorEngine.MLPredictionResult>?> = combine(
        activeStock,
        forecastDaysHorizonInput,
        knnKInput
    ) { stock, days, kNeighbors ->
        if (stock == null) return@combine null

        val linReg = PredictorEngine.runLinearRegression(stock.priceHistory, days)
        val knn = PredictorEngine.runKNNRegression(stock.priceHistory, stock.volumeHistory, kNeighbors, days)
        val expSmooth = PredictorEngine.runDoubleExponentialSmoothing(stock.priceHistory, daysAhead = days)
        val indicators = PredictorEngine.calculateTechnicalIndicators(stock.priceHistory, stock.volumeHistory)
        val rf = PredictorEngine.runRandomForest(stock.priceHistory, stock.volumeHistory, indicators, days)
        val lstm = PredictorEngine.runBiLSTMReady(stock.priceHistory, stock.volumeHistory, days)

        mapOf(
            "Linear Regression" to linReg,
            "K-Nearest Neighbors (ML)" to knn,
            "Exponential Smoothing" to expSmooth,
            "Random Forest Regressor" to rf,
            "Bi-LSTM Neural Engine" to lstm
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeConsensus: StateFlow<PredictorEngine.ConsensusSignal?> = combine(
        activeStock,
        activeIndicators,
        activePredictions
    ) { stock, indicators, predictions ->
        if (stock == null || indicators == null) null
        else {
            PredictorEngine.calculateConsensusSignal(stock.currentPrice, indicators, predictions)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- User Actions ---

    fun onSelectStock(symbol: String) {
        _selectedSymbol.value = symbol
        // Reset order entry and Gemini results with selection to keep views aligned
        _orderSharesInput.value = "10"
        _geminiState.value = GeminiUiState.Idle
    }

    fun onUpdateOrderShares(shares: String) {
        _orderSharesInput.value = shares.filter { it.isDigit() }
    }

    fun onUpdateForecastHorizon(days: Int) {
        _forecastDaysHorizonInput.value = days.coerceIn(1, 30)
    }

    fun onUpdateKnnK(k: Int) {
        _knnKInput.value = k.coerceIn(1, 10)
    }

    fun toggleWatchlist(symbol: String, name: String) = viewModelScope.launch {
        if (repository.isStockWatched(symbol)) {
            repository.removeFromWatchlist(symbol)
        } else {
            repository.addToWatchlist(symbol, name)
        }
    }

    fun updateAlertPrices(symbol: String, companyName: String, alertHigh: Double, alertLow: Double) = viewModelScope.launch {
        repository.watchlistDao.insertItem(
            StockWatchItem(
                symbol = symbol,
                companyName = companyName,
                alertHighPrice = alertHigh,
                alertLowPrice = alertLow,
                addedAt = System.currentTimeMillis()
            )
        )
        _tradeStatusMessage.emit("Set thresholds for $symbol: ₹$alertLow (support) - ₹$alertHigh (resistance)")
    }

    // --- Simulated Trades ---

    fun executeTrade(type: String) = viewModelScope.launch {
        val stock = activeStock.value ?: return@launch
        val shares = _orderSharesInput.value.toIntOrNull() ?: 0

        if (shares <= 0) {
            _tradeStatusMessage.emit("Error: Please submit a positive stock quantity.")
            return@launch
        }

        val result = repository.executeSimulatedTrade(
            symbol = stock.symbol,
            companyName = stock.name,
            shares = shares,
            tradePrice = stock.currentPrice,
            type = type
        )

        result.onSuccess { msg ->
            _tradeStatusMessage.emit(msg)
            // Auto refresh fields
            _orderSharesInput.value = "10"
        }.onFailure { err ->
            _tradeStatusMessage.emit("Order Failed: ${err.message}")
        }
    }

    fun onResetVirtualPortfolio() = viewModelScope.launch {
        repository.resetAllSimulatedBalances()
        _tradeStatusMessage.emit("Simulated portfolios and trading logs reset to ₹10,00,000!")
    }

    // --- Gemini AI Analytic Quantitative Calls ---

    fun fetchGeminiForecasting() = viewModelScope.launch {
        val stock = activeStock.value ?: return@launch
        val indicators = activeIndicators.value ?: return@launch

        _geminiState.value = GeminiUiState.Loading

        // Bind company-specific headlines
        val relevantNews = newsFeed.value
            .filter { it.affectedSymbol == stock.symbol || it.affectedSymbol == null }
            .take(3)
            .map { it.title }

        try {
            val predictionResult = GeminiService.getAnalyticsAndPrediction(
                symbol = stock.symbol,
                companyName = stock.name,
                currentPrice = stock.currentPrice,
                history = stock.priceHistory,
                indicators = indicators,
                newsHeadlines = relevantNews
            )
            _geminiState.value = GeminiUiState.Success(predictionResult)
        } catch (e: Exception) {
            _geminiState.value = GeminiUiState.Error("Analyst connection issue: ${e.localizedMessage}")
        }
    }

    fun saveActiveForecast(modelName: String, predictedPrice: Double, confidence: Double, trend: String) = viewModelScope.launch {
        val stock = activeStock.value ?: return@launch
        val indicators = activeIndicators.value ?: return@launch

        val notesText = if (modelName.contains("Gemini")) {
            val state = geminiState.value
            if (state is GeminiUiState.Success) {
                state.result.reasoning
            } else {
                "Model predictions initialized offline."
            }
        } else {
            "Deterministic math analysis: RSI=${String.format("%.1f", indicators.rsi)}, 20sma=₹${String.format("%.1f", indicators.sma20)}."
        }

        repository.saveForecast(
            symbol = stock.symbol,
            modelName = modelName,
            targetDays = forecastDaysHorizonInput.value,
            basePrice = stock.currentPrice,
            predictedPrice = predictedPrice,
            confidence = confidence,
            trend = trend,
            notes = notesText
        )
        _tradeStatusMessage.emit("Successfully saved $modelName forecast to analysis watch list!")
    }

    fun onDeleteSavedForecast(id: Int) = viewModelScope.launch {
        repository.deleteForecast(id)
        _tradeStatusMessage.emit("Cleared forecast archive entry.")
    }
}
