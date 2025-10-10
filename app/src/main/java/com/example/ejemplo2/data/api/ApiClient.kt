package com.example.ejemplo2.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Cliente de Retrofit para la API de migración
 */
object ApiClient {
    
    // URL para emulador Android
    private const val BASE_URL = "http://10.0.2.2:8000/api/" // 10.0.2.2 es localhost en el emulador
    
    // Para dispositivo físico, usar la IP de tu computadora:
    // private const val BASE_URL = "http://192.168.1.100:8000/api/" // Cambiar por tu IP local
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val migrationApiService: MigrationApiService = retrofit.create(MigrationApiService::class.java)
    
    /**
     * Obtener la URL base configurada
     */
    fun getBaseUrl(): String = BASE_URL
}
