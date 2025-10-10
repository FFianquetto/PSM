package com.example.ejemplo2.config

import android.content.Context
import java.io.IOException
import java.util.*

object DatabaseConfig {
    
    private var config: Properties? = null
    
    fun initialize(context: Context) {
        if (config == null) {
            config = Properties()
            try {
                val inputStream = context.assets.open(".env")
                config?.load(inputStream)
                inputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
    
    fun getDatabaseHost(): String {
        return config?.getProperty("DB_HOST", "localhost") ?: "localhost"
    }
    
    fun getDatabasePort(): Int {
        return config?.getProperty("DB_PORT", "3306")?.toIntOrNull() ?: 3306
    }
    
    fun getDatabaseName(): String {
        return config?.getProperty("DB_DATABASE", "psm") ?: "psm"
    }
    
    fun getDatabaseUsername(): String {
        return config?.getProperty("DB_USERNAME", "root") ?: "root"
    }
    
    fun getDatabasePassword(): String {
        return config?.getProperty("DB_PASSWORD", "") ?: ""
    }
    
    fun getConnectionUrl(): String {
        val host = getDatabaseHost()
        val port = getDatabasePort()
        val database = getDatabaseName()
        return "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC"
    }
    
    fun isHybridMode(): Boolean {
        return config?.getProperty("APP_MODE", "hybrid") == "hybrid"
    }
    
    fun getSyncInterval(): Long {
        return config?.getProperty("SYNC_INTERVAL", "300000")?.toLongOrNull() ?: 300000L
    }
}
