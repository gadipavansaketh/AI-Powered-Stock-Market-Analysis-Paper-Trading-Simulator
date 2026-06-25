package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// --- entities ---

@Entity(tableName = "watchlist_items")
data class StockWatchItem(
    @PrimaryKey val symbol: String,
    val companyName: String,
    val alertHighPrice: Double = 0.0,
    val alertLowPrice: Double = 0.0,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "simulation_trades")
data class SimulationTrade(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val symbol: String,
    val companyName: String,
    val shares: Int,
    val tradePrice: Double,
    val type: String, // "BUY" or "SELL"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_forecasts")
data class SavedForecast(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val symbol: String,
    val modelName: String,
    val targetHorizonDays: Int,
    val basePrice: Double,
    val predictedPrice: Double,
    val confidence: Double,
    val trend: String,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "portfolio_balance")
data class PortfolioCash(
    @PrimaryKey val id: Int = 1,
    val cashBalance: Double
)

// --- DAOs ---

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist_items ORDER BY addedAt DESC")
    fun getAllWatchlist(): Flow<List<StockWatchItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: StockWatchItem)

    @Query("DELETE FROM watchlist_items WHERE symbol = :symbol")
    suspend fun deleteItem(symbol: String)

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist_items WHERE symbol = :symbol LIMIT 1)")
    suspend fun isWatched(symbol: String): Boolean
}

@Dao
interface TradeDao {
    @Query("SELECT * FROM simulation_trades ORDER BY timestamp DESC")
    fun getAllTradesFlow(): Flow<List<SimulationTrade>>

    @Query("SELECT * FROM simulation_trades ORDER BY timestamp DESC")
    suspend fun getAllTrades(): List<SimulationTrade>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: SimulationTrade)

    @Query("DELETE FROM simulation_trades")
    suspend fun clearAllTrades()
}

@Dao
interface ForecastDao {
    @Query("SELECT * FROM saved_forecasts ORDER BY timestamp DESC")
    fun getAllForecasts(): Flow<List<SavedForecast>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForecast(forecast: SavedForecast)

    @Query("DELETE FROM saved_forecasts WHERE id = :id")
    suspend fun deleteForecastById(id: Int)
}

@Dao
interface PortfolioCashDao {
    @Query("SELECT * FROM portfolio_balance WHERE id = 1 LIMIT 1")
    suspend fun getCashBalance(): PortfolioCash?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateCashBalance(cash: PortfolioCash)
}

// --- Database Database ---

@Database(
    entities = [StockWatchItem::class, SimulationTrade::class, SavedForecast::class, PortfolioCash::class],
    version = 1,
    exportSchema = false
)
abstract class StockDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun tradeDao(): TradeDao
    abstract fun forecastDao(): ForecastDao
    abstract fun portfolioCashDao(): PortfolioCashDao
}
