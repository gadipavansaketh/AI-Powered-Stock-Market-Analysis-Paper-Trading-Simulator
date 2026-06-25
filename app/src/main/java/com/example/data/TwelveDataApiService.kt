package com.example.data

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface TwelveDataApiService {

    @GET("quote")
    suspend fun getMultiQuotes(
        @Query("symbol") symbols: String,
        @Query("exchange") exchange: String = "NSE",
        @Query("apikey") apiKey: String
    ): ResponseBody

    companion object {
        private const val BASE_URL = "https://api.twelvedata.com/"

        fun create(): TwelveDataApiService {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()

            return retrofit.create(TwelveDataApiService::class.java)
        }
    }
}
