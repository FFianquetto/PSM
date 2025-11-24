package com.example.ejemplo2.utils

import android.content.Context
import android.util.Log
import com.example.ejemplo2.data.api.ApiService
import com.example.ejemplo2.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabaseResetHelper {
    private const val TAG = "DatabaseResetHelper"
    
    /**
     * Resetea todas las tablas de SQLite usando DELETE (equivalente a TRUNCATE)
     */
    suspend fun truncateSQLiteTables(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== Iniciando reset de tablas SQLite ===")
            AppDatabase.truncateAllTables(context)
            Log.d(TAG, "✓ Tablas SQLite reseteadas exitosamente")
            Result.success("Tablas SQLite reseteadas exitosamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error reseteando tablas SQLite: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Resetea todas las tablas de MySQL a través de la API
     */
    suspend fun truncateMySQLTables(apiService: ApiService): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== Iniciando reset de tablas MySQL ===")
            val result = apiService.truncateAllMySQLTables()
            if (result.isSuccess) {
                Log.d(TAG, "✓ Tablas MySQL reseteadas exitosamente")
                Result.success(result.getOrNull() ?: "Tablas MySQL reseteadas exitosamente")
            } else {
                Log.e(TAG, "Error reseteando tablas MySQL: ${result.exceptionOrNull()?.message}")
                Result.failure(result.exceptionOrNull() ?: Exception("Error desconocido"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reseteando tablas MySQL: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Resetea ambas bases de datos (SQLite y MySQL)
     */
    suspend fun resetAllDatabases(context: Context, apiService: ApiService): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== Iniciando reset completo de bases de datos ===")
            
            // Resetear SQLite
            val sqliteResult = truncateSQLiteTables(context)
            if (sqliteResult.isFailure) {
                return@withContext Result.failure(sqliteResult.exceptionOrNull() ?: Exception("Error reseteando SQLite"))
            }
            
            // Resetear MySQL
            val mysqlResult = truncateMySQLTables(apiService)
            if (mysqlResult.isFailure) {
                return@withContext Result.failure(mysqlResult.exceptionOrNull() ?: Exception("Error reseteando MySQL"))
            }
            
            val sqliteMessage = sqliteResult.getOrNull() ?: "SQLite reset completado"
            val mysqlMessage = mysqlResult.getOrNull() ?: "MySQL reset completado"
            
            Log.d(TAG, "✓ Reset completo exitoso")
            Result.success(Pair(sqliteMessage, mysqlMessage))
        } catch (e: Exception) {
            Log.e(TAG, "Error en reset completo: ${e.message}", e)
            Result.failure(e)
        }
    }
}

