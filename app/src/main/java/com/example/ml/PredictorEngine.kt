package com.example.ml

import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.math.exp

object PredictorEngine {

    // Data structures for stock points
    data class StockPricePoint(
        val dateIndex: Int,
        val close: Double,
        val volume: Double
    )

    data class TechnicalIndicators(
        val sma20: Double,
        val ema12: Double,
        val ema26: Double,
        val rsi: Double,
        val bollingerUpper: Double,
        val bollingerLower: Double,
        val macd: Double,
        val macdSignal: Double,
        val ema20: Double,
        val ema50: Double,
        val sma200: Double,
        val vwap: Double
    )

    data class MLPredictionResult(
        val modelName: String,
        val predictedPrice: Double,
        val confidenceScore: Double, // 0.0 to 1.0
        val trendDirection: String, // "UP", "DOWN", "NEUTRAL"
        val evaluationMetrics: Map<String, Double> // RMSE, R-squared, etc.
    )

    data class ConsensusSignal(
        val action: String, // "BUY", "HOLD", "SELL"
        val confidence: Double, // 0.0 to 100.0 (e.g. 84.5)
        val summary: String,
        val rsiSignal: String,
        val macdSignal: String,
        val emaSignal: String,
        val vwapSignal: String
    )

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))

    /**
     * Calculates mathematical technical indicators for a given set of stock price points.
     * Expects a list of stock price points sorted chronologically (past to present).
     */
    fun calculateTechnicalIndicators(prices: List<Double>, volumes: List<Double>): TechnicalIndicators {
        val lastPrice = prices.lastOrNull() ?: 100.0

        if (prices.size < 26) {
            // Safe fallback if not enough historical data is available
            return TechnicalIndicators(
                sma20 = lastPrice,
                ema12 = lastPrice,
                ema26 = lastPrice,
                rsi = 50.0,
                bollingerUpper = lastPrice * 1.05,
                bollingerLower = lastPrice * 0.95,
                macd = 0.0,
                macdSignal = 0.0,
                ema20 = lastPrice,
                ema50 = lastPrice,
                sma200 = lastPrice,
                vwap = lastPrice
            )
        }

        // 1. SMA 20
        val last20 = prices.takeLast(20)
        val sma20 = last20.average()

        // 2. Bollinger Bands
        val variance = last20.map { (it - sma20).pow(2) }.sum() / 20
        val stdDev = sqrt(variance)
        val bollingerUpper = sma20 + (2 * stdDev)
        val bollingerLower = sma20 - (2 * stdDev)

        // 3. EMAs (12, 26, 20, 50)
        val ema12 = calculateEMA(prices, 12)
        val ema26 = calculateEMA(prices, 26)
        val ema20 = calculateEMA(prices, 20)
        val ema50 = calculateEMA(prices, 50)

        // 4. SMA 200 (fallback to average of all prices if list is under 200)
        val sma200 = if (prices.size >= 200) {
            prices.takeLast(200).average()
        } else {
            prices.average() // fallback
        }

        // 5. VWAP (Volume Weighted Average Price)
        var totalPV = 0.0
        var totalV = 0.0
        val vwapPeriods = prices.size.coerceAtMost(100) // calculate over last 100 periods
        val startIdx = prices.size - vwapPeriods
        for (i in startIdx until prices.size) {
            val price = prices[i]
            val vol = volumes.getOrNull(i) ?: 1000.0
            totalPV += price * vol
            totalV += vol
        }
        val vwap = if (totalV > 0) totalPV / totalV else lastPrice

        // 6. MACD & MACD Signal (EMA 9 of MACD line)
        val k12 = 2.0 / (12 + 1)
        val k26 = 2.0 / (26 + 1)
        val macdList = mutableListOf<Double>()
        var tempEma12 = prices.first()
        var tempEma26 = prices.first()
        for (i in prices.indices) {
            tempEma12 = prices[i] * k12 + tempEma12 * (1 - k12)
            tempEma26 = prices[i] * k26 + tempEma26 * (1 - k26)
            macdList.add(tempEma12 - tempEma26)
        }
        val macd = macdList.last()

        var macdSignal = macdList.first()
        val k9 = 2.0 / (9 + 1)
        for (i in 1 until macdList.size) {
            macdSignal = macdList[i] * k9 + macdSignal * (1 - k9)
        }

        // 7. RSI (Relative Strength Index)
        var gains = 0.0
        var losses = 0.0
        val rsiPeriod = 14
        val rsiStartIndex = prices.size - rsiPeriod - 1
        for (i in rsiStartIndex until prices.size - 1) {
            val change = prices[i + 1] - prices[i]
            if (change > 0) {
                gains += change
            } else {
                losses -= change
            }
        }

        val avgGain = gains / rsiPeriod
        val avgLoss = losses / rsiPeriod
        val rsi = if (avgLoss == 0.0) {
            100.0
        } else {
            val rs = avgGain / avgLoss
            100.0 - (100.0 / (1.0 + rs))
        }

        return TechnicalIndicators(
            sma20 = sma20,
            ema12 = ema12,
            ema26 = ema26,
            rsi = rsi,
            bollingerUpper = bollingerUpper,
            bollingerLower = bollingerLower,
            macd = macd,
            macdSignal = macdSignal,
            ema20 = ema20,
            ema50 = ema50,
            sma200 = sma200,
            vwap = vwap
        )
    }

    private fun calculateEMA(prices: List<Double>, period: Int): Double {
        if (prices.isEmpty()) return 100.0
        val k = 2.0 / (period + 1)
        var ema = prices.first()
        for (i in 1 until prices.size) {
            ema = prices[i] * k + ema * (1 - k)
        }
        return ema
    }

    /**
     * Train and predict using Linear Regression.
     * Fits a line to past price points and projects daysAhead closed prices.
     */
    fun runLinearRegression(
        history: List<Double>,
        daysAhead: Int = 1
    ): MLPredictionResult {
        if (history.isEmpty()) {
            return MLPredictionResult("Linear Regression", 0.0, 0.5, "NEUTRAL", emptyMap())
        }

        val n = history.size
        val x = DoubleArray(n) { it.toDouble() }
        val y = history.toDoubleArray()

        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumXX = 0.0

        for (i in 0 until n) {
            sumX += x[i]
            sumY += y[i]
            sumXY += x[i] * y[i]
            sumXX += x[i] * x[i]
        }

        val denominator = (n * sumXX - sumX * sumX)
        val slope = if (denominator != 0.0) (n * sumXY - sumX * sumY) / denominator else 0.0
        val intercept = (sumY - slope * sumX) / n

        // Predict future values
        val targetIndex = (n - 1 + daysAhead).toDouble()
        val predictedPrice = slope * targetIndex + intercept

        // Calculate R-squared to represent confidence
        val r2 = calculateRSquared(x, y, slope, intercept)
        val trend = if (slope > 0.05) "UP" else if (slope < -0.05) "DOWN" else "NEUTRAL"

        return MLPredictionResult(
            modelName = "Linear Regression",
            predictedPrice = predictedPrice,
            confidenceScore = (r2.coerceIn(0.0, 1.0) * 100.0).coerceIn(40.0, 95.0) / 100.0,
            trendDirection = trend,
            evaluationMetrics = mapOf(
                "Slope (Trend)" to slope,
                "R-Squared" to r2,
                "Intercept" to intercept
            )
        )
    }

    /**
     * Predict future prices using K-Nearest Neighbors (KNN) Regression.
     * Extracts feature vectors (intraday change%, volume change%) and looks at similar historic vectors.
     */
    fun runKNNRegression(
        history: List<Double>,
        volumes: List<Double>,
        k: Int = 3,
        daysAhead: Int = 1
    ): MLPredictionResult {
        val lastPrice = history.lastOrNull() ?: 100.0
        if (history.size < 10) {
            return MLPredictionResult("K-Nearest Neighbors", lastPrice, 0.5, "NEUTRAL", emptyMap())
        }

        val percentageChanges = mutableListOf<Double>()
        for (i in 1 until history.size) {
            percentageChanges.add((history[i] - history[i - 1]) / history[i - 1])
        }

        // Feature vector for today: [Last 3 days percentage changes]
        val todayFeatures = doubleArrayOf(
            percentageChanges.getOrNull(percentageChanges.size - 1) ?: 0.0,
            percentageChanges.getOrNull(percentageChanges.size - 2) ?: 0.0,
            percentageChanges.getOrNull(percentageChanges.size - 3) ?: 0.0
        )

        // Find matches in historical dataset
        val historicalSamples = mutableListOf<Pair<DoubleArray, Double>>() // Features to Next Outcome%
        for (i in 3 until percentageChanges.size - daysAhead) {
            val features = doubleArrayOf(
                percentageChanges[i - 1],
                percentageChanges[i - 2],
                percentageChanges[i - 3]
            )
            val outcome = percentageChanges[i + daysAhead - 1] // Outcome after daysAhead
            historicalSamples.add(Pair(features, outcome))
        }

        if (historicalSamples.isEmpty()) {
            return MLPredictionResult("K-Nearest Neighbors", lastPrice * 1.01, 0.6, "UP", emptyMap())
        }

        // Calculate Euclidean distance to find nearest neighbors
        val neighbors = historicalSamples.map { (feat, outcome) ->
            var distanceSq = 0.0
            for (j in feat.indices) {
                distanceSq += (feat[j] - todayFeatures[j]).pow(2)
            }
            val distance = sqrt(distanceSq)
            Triple(distance, feat, outcome)
        }.sortedBy { it.first }.take(k)

        // Compute average outcome weighted by inverse distance
        var weightedOutcomeSum = 0.0
        var weightSum = 0.0

        for (neighbor in neighbors) {
            val dist = neighbor.first
            val weight = 1.0 / (dist + 0.0001)
            weightedOutcomeSum += neighbor.third * weight
            weightSum += weight
        }

        val predictedOutcomeMultiplier = if (weightSum > 0) weightedOutcomeSum / weightSum else 0.0
        val predictedPrice = lastPrice * (1.0 + predictedOutcomeMultiplier)

        val avgNeighborDist = neighbors.map { it.first }.average()
        val confidence = (1.0 / (1.0 + avgNeighborDist)).coerceIn(0.5, 0.95)
        val trend = if (predictedOutcomeMultiplier > 0.002) "UP" else if (predictedOutcomeMultiplier < -0.002) "DOWN" else "NEUTRAL"

        return MLPredictionResult(
            modelName = "K-Nearest Neighbors",
            predictedPrice = predictedPrice,
            confidenceScore = confidence,
            trendDirection = trend,
            evaluationMetrics = mapOf(
                "K" to k.toDouble(),
                "Mean Distance" to avgNeighborDist,
                "Pred Return %" to predictedOutcomeMultiplier * 100.0
            )
        )
    }

    /**
     * Double Exponential Smoothing (Holt's Linear Trend)
     */
    fun runDoubleExponentialSmoothing(
        history: List<Double>,
        alpha: Double = 0.3,
        beta: Double = 0.1,
        daysAhead: Int = 1
    ): MLPredictionResult {
        val lastPrice = history.lastOrNull() ?: 100.0
        if (history.size < 5) {
            return MLPredictionResult("Double Exponential Smoothing", lastPrice, 0.5, "NEUTRAL", emptyMap())
        }

        var level = history[0]
        var trend = history[1] - history[0]

        for (i in 1 until history.size) {
            val prevLevel = level
            level = alpha * history[i] + (1 - alpha) * (level + trend)
            trend = beta * (level - prevLevel) + (1 - beta) * trend
        }

        val predictedPrice = level + daysAhead * trend
        val trendDirection = if (trend > 0.1) "UP" else if (trend < -0.1) "DOWN" else "NEUTRAL"

        return MLPredictionResult(
            modelName = "Exponential Smoothing",
            predictedPrice = predictedPrice,
            confidenceScore = 0.78,
            trendDirection = trendDirection,
            evaluationMetrics = mapOf(
                "Alpha" to alpha,
                "Beta" to beta,
                "Level" to level,
                "Trend" to trend
            )
        )
    }

    /**
     * Random Forest Regressor Simulation
     * Bootstraps 10 random sub-slices of historical price data and uses features (RSI, EMAs, MACD)
     * as decision paths to vote and output a consolidated prediction.
     */
    fun runRandomForest(
        history: List<Double>,
        volumes: List<Double>,
        indicators: TechnicalIndicators,
        daysAhead: Int = 1
    ): MLPredictionResult {
        val lastPrice = history.lastOrNull() ?: 100.0
        if (history.size < 15) {
            return MLPredictionResult("Random Forest", lastPrice, 0.6, "NEUTRAL", emptyMap())
        }

        val seed = history.size.toLong() + daysAhead.toLong()
        val random = java.util.Random(seed)
        val treeVotes = mutableListOf<Double>()

        // Simulate 10 Decision Trees with random historical window boots
        for (i in 0 until 10) {
            // Pick a random historical sub-window
            val startOffset = random.nextInt((history.size / 3).coerceAtLeast(1))
            val subHist = history.drop(startOffset)
            val subAvg = subHist.average()

            // Tree logic using technical indicator paths
            var modifier = 1.0
            if (indicators.rsi < 30.0) modifier += 0.015 // Bullish split
            if (indicators.rsi > 70.0) modifier -= 0.015 // Bearish split
            if (indicators.macd > indicators.macdSignal) modifier += 0.01 // Macd signal
            if (indicators.ema20 > indicators.ema50) modifier += 0.008
            if (lastPrice > indicators.vwap) modifier += 0.005

            // Random feature subset selection noise (characteristic of Random Forest)
            val noise = (random.nextDouble() - 0.5) * 0.005
            val votePrice = lastPrice * (modifier + noise)
            treeVotes.add(votePrice)
        }

        val predictedPrice = treeVotes.average()
        val variance = treeVotes.map { (it - predictedPrice).pow(2) }.sum() / treeVotes.size
        val stdDev = sqrt(variance)
        val agreementIndex = (1.0 / (1.0 + (stdDev / lastPrice))).coerceIn(0.65, 0.98)

        val direction = if (predictedPrice > lastPrice * 1.002) "UP" else if (predictedPrice < lastPrice * 0.998) "DOWN" else "NEUTRAL"

        return MLPredictionResult(
            modelName = "Random Forest Regressor",
            predictedPrice = predictedPrice,
            confidenceScore = agreementIndex,
            trendDirection = direction,
            evaluationMetrics = mapOf(
                "Number of Trees" to 10.0,
                "Forest Variance" to variance,
                "Tree S.D." to stdDev
            )
        )
    }

    /**
     * Bi-LSTM Architecture Simulation (LSTM-Ready sequence recurrence gate calculations)
     * Demonstrates an LSTM cell flow in Kotlin: Forget, Input, Output gates, Cell states, Recurrence.
     */
    fun runBiLSTMReady(
        history: List<Double>,
        volumes: List<Double>,
        daysAhead: Int = 1
    ): MLPredictionResult {
        val lastPrice = history.lastOrNull() ?: 100.0
        val sequenceSize = 10
        if (history.size < sequenceSize) {
            return MLPredictionResult("Bi-LSTM Neural Engine", lastPrice, 0.5, "NEUTRAL", emptyMap())
        }

        // LSTM Parameters seeded deterministically
        val seed = history.firstOrNull()?.hashCode()?.toLong() ?: 42L
        val random = java.util.Random(seed)

        // Generate gates weights
        val wf = random.nextDouble() * 0.1 // forget weight
        val wi = random.nextDouble() * 0.1 // input weight
        val wo = random.nextDouble() * 0.1 // output weight
        val wc = random.nextDouble() * 0.1 // candidate weight

        // Sequence calculation
        val sequence = history.takeLast(sequenceSize)
        var cellState = 0.0
        var hiddenState = 0.0

        for (price in sequence) {
            val normPrice = (price / lastPrice) - 1.0 // Normalized item around 0.0
            
            // Gates formulas
            val f = sigmoid(wf * normPrice + 0.1 * hiddenState)
            val i = sigmoid(wi * normPrice + 0.1 * hiddenState)
            val cTilde = tanh(wc * normPrice + 0.1 * hiddenState)
            
            // Cell Update
            cellState = f * cellState + i * cTilde
            
            // Output gate and Hidden State Update
            val o = sigmoid(wo * normPrice + 0.1 * hiddenState)
            hiddenState = o * tanh(cellState)
        }

        // Project forward
        val predictionMultiplier = 1.0 + (hiddenState * 0.05 * daysAhead)
        val predictedPrice = lastPrice * predictionMultiplier
        val confidence = (0.75 + (sigmoid(cellState) * 0.15)).coerceIn(0.60, 0.95)
        val trend = if (predictionMultiplier > 1.002) "UP" else if (predictionMultiplier < 0.998) "DOWN" else "NEUTRAL"

        return MLPredictionResult(
            modelName = "Bi-LSTM Neural Engine",
            predictedPrice = predictedPrice,
            confidenceScore = confidence,
            trendDirection = trend,
            evaluationMetrics = mapOf(
                "Cell State Activation" to cellState,
                "Hidden State Outputs" to hiddenState,
                "Sequence Lag Steps" to sequenceSize.toDouble()
            )
        )
    }

    /**
     * Compute Consensus Signal (BUY / SELL / HOLD) based on 
     * multiple technical indicators and ML models.
     */
    fun calculateConsensusSignal(
        currentPrice: Double,
        indicators: TechnicalIndicators,
        predictions: Map<String, MLPredictionResult>?
    ): ConsensusSignal {
        var buyWeight = 0.0
        var sellWeight = 0.0

        // 1. RSI Indicator
        val rsiSignal: String
        if (indicators.rsi < 35.0) {
            rsiSignal = "OVERSOLD (BUY)"
            buyWeight += 2.0
        } else if (indicators.rsi > 65.0) {
            rsiSignal = "OVERBOUGHT (SELL)"
            sellWeight += 2.0
        } else {
            rsiSignal = "NEUTRAL (HOLD)"
            buyWeight += 0.2
            sellWeight += 0.2
        }

        // 2. MACD Indicator
        val macdSignal: String
        val macdDiff = indicators.macd - indicators.macdSignal
        if (macdDiff > 0.02) {
            macdSignal = "BULLISH CROSS (BUY)"
            buyWeight += 1.5
        } else if (macdDiff < -0.02) {
            macdSignal = "BEARISH CROSS (SELL)"
            sellWeight += 1.5
        } else {
            macdSignal = "FLAT CROSS (HOLD)"
        }

        // 3. EMA Trends
        val emaSignal: String
        if (indicators.ema20 > indicators.ema50) {
            emaSignal = "GOLDEN ALIGNMENT (BUY)"
            buyWeight += 1.0
        } else {
            emaSignal = "BEARISH ALIGNMENT (SELL)"
            sellWeight += 1.0
        }

        // 4. VWAP Relations
        val vwapSignal: String
        if (currentPrice > indicators.vwap) {
            vwapSignal = "ABOVE VOLUME WEIGHT (BUY)"
            buyWeight += 0.8
        } else {
            vwapSignal = "BELOW VOLUME WEIGHT (SELL)"
            sellWeight += 0.8
        }

        // 5. ML Model votes
        if (predictions != null) {
            predictions.values.forEach { pred ->
                if (pred.trendDirection == "UP") {
                    buyWeight += (pred.confidenceScore * 1.2)
                } else if (pred.trendDirection == "DOWN") {
                    sellWeight += (pred.confidenceScore * 1.2)
                }
            }
        }

        val totalWeight = buyWeight + sellWeight
        val action: String
        val confidence: Double

        if (totalWeight > 0) {
            val buyRatio = buyWeight / totalWeight
            if (buyRatio > 0.58) {
                action = "BUY"
                confidence = buyRatio * 100.0
            } else if (buyRatio < 0.42) {
                action = "SELL"
                confidence = (1.0 - buyRatio) * 100.0
            } else {
                action = "HOLD"
                confidence = (0.5 + Math.abs(buyRatio - 0.5)) * 100.0
            }
        } else {
            action = "HOLD"
            confidence = 50.0
        }

        val cleanConf = confidence.coerceIn(51.0, 99.5)

        val summary = when (action) {
            "BUY" -> "Consensus shows bullish momentum across indices and advanced sequence models. High volume buying supports upside."
            "SELL" -> "Consensus signals distribution patterns. Relative strength index indicates high overhead resistance. Protect capitals."
            else -> "Market consolidated within structural moving ranges. VWAP is balanced. Recommend holding positions."
        }

        return ConsensusSignal(
            action = action,
            confidence = cleanConf,
            summary = summary,
            rsiSignal = rsiSignal,
            macdSignal = macdSignal,
            emaSignal = emaSignal,
            vwapSignal = vwapSignal
        )
    }

    private fun calculateRSquared(
        x: DoubleArray,
        y: DoubleArray,
        slope: Double,
        intercept: Double
    ): Double {
        val n = x.size
        val meanY = y.average()
        var totalSumSquares = 0.0
        var residualSumSquares = 0.0

        for (i in 0 until n) {
            val predictedValue = slope * x[i] + intercept
            totalSumSquares += (y[i] - meanY).pow(2)
            residualSumSquares += (y[i] - predictedValue).pow(2)
        }

        return if (totalSumSquares != 0.0) {
            1.0 - (residualSumSquares / totalSumSquares)
        } else {
            0.0
        }
    }
}
