package com.example.ejemplo2.utils

import android.content.Context
import android.util.Log
import com.example.ejemplo2.config.DatabaseConfig
import com.example.ejemplo2.data.remote.dao.MySQLUserDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MySQLDiagnostic {
    
    private const val TAG = "MySQLDiagnostic"
    
    suspend fun testConnection(context: Context): String = withContext(Dispatchers.IO) {
        try {
            DatabaseConfig.initialize(context)
            
            val connectionUrl = DatabaseConfig.getConnectionUrl()
            val username = DatabaseConfig.getDatabaseUsername()
            val password = DatabaseConfig.getDatabasePassword()
            val host = DatabaseConfig.getDatabaseHost()
            val port = DatabaseConfig.getDatabasePort()
            val database = DatabaseConfig.getDatabaseName()
            
            Log.d(TAG, "=== MySQL Connection Test ===")
            Log.d(TAG, "Host: $host")
            Log.d(TAG, "Port: $port")
            Log.d(TAG, "Database: $database")
            Log.d(TAG, "Username: $username")
            Log.d(TAG, "Password: ${if (password.isEmpty()) "EMPTY" else "SET"}")
            Log.d(TAG, "Full URL: $connectionUrl")
            
            val mysqlDao = MySQLUserDao()
            
            // Intentar conexión
            Log.d(TAG, "Attempting connection...")
            val isConnected = mysqlDao.connect(connectionUrl, username, password)
            
            if (isConnected) {
                Log.d(TAG, "✅ MySQL connection successful!")
                
                // Intentar crear tabla
                Log.d(TAG, "Creating table...")
                val tableCreated = mysqlDao.createTable()
                Log.d(TAG, "Table creation result: $tableCreated")
                
                // Verificar que la conexión sigue activa
                val stillConnected = mysqlDao.isConnected()
                Log.d(TAG, "Connection still active: $stillConnected")
                
                mysqlDao.disconnect()
                
                "✅ Conexión MySQL exitosa\nHost: $host:$port\nBase: $database\nUsuario: $username"
            } else {
                Log.e(TAG, "❌ MySQL connection failed!")
                "❌ Error de conexión MySQL\nHost: $host:$port\nBase: $database\nUsuario: $username\n\nPosibles causas:\n• MySQL no está ejecutándose\n• Puerto incorrecto\n• Base de datos no existe\n• Credenciales incorrectas"
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ MySQL diagnostic error: ${e.message}", e)
            "❌ Error: ${e.message}\n\nDetalles del error:\n${e.stackTraceToString()}"
        }
    }
    
    suspend fun testMultipleConnections(context: Context): String = withContext(Dispatchers.IO) {
        try {
            DatabaseConfig.initialize(context)
            
            val username = DatabaseConfig.getDatabaseUsername()
            val password = DatabaseConfig.getDatabasePassword()
            val database = DatabaseConfig.getDatabaseName()
            val port = DatabaseConfig.getDatabasePort()
            
            val testConfigs = listOf(
                "127.0.0.1" to "127.0.0.1",
                "localhost" to "localhost", 
                "10.0.2.2" to "10.0.2.2 (Android Emulator)",
                "192.168.1.100" to "192.168.1.100 (IP Local)"
            )
            
            val results = mutableListOf<String>()
            
            for ((host, description) in testConfigs) {
                val connectionUrl = "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC"
                
                Log.d(TAG, "Testing connection to: $description ($host:$port)")
                
                val mysqlDao = MySQLUserDao()
                val isConnected = mysqlDao.connect(connectionUrl, username, password)
                
                if (isConnected) {
                    Log.d(TAG, "✅ Connection successful to $description")
                    results.add("✅ $description: CONECTADO")
                    mysqlDao.disconnect()
                } else {
                    Log.d(TAG, "❌ Connection failed to $description")
                    results.add("❌ $description: FALLÓ")
                }
            }
            
            results.joinToString("\n")
        } catch (e: Exception) {
            Log.e(TAG, "Multiple connection test error: ${e.message}", e)
            "❌ Error en pruebas múltiples: ${e.message}"
        }
    }
    
    suspend fun testMySQLOnly(context: Context): String = withContext(Dispatchers.IO) {
        try {
            DatabaseConfig.initialize(context)
            val connectionUrl = DatabaseConfig.getConnectionUrl()
            val username = DatabaseConfig.getDatabaseUsername()
            val password = DatabaseConfig.getDatabasePassword()
            val host = DatabaseConfig.getDatabaseHost()
            val port = DatabaseConfig.getDatabasePort()
            val database = DatabaseConfig.getDatabaseName()
            
            Log.d(TAG, "=== PRUEBA ESPECÍFICA MYSQL ===")
            Log.d(TAG, "Host: $host")
            Log.d(TAG, "Port: $port")
            Log.d(TAG, "Database: $database")
            Log.d(TAG, "Username: $username")
            Log.d(TAG, "Password: ${if (password.isEmpty()) "EMPTY" else "SET"}")
            Log.d(TAG, "Full URL: $connectionUrl")
            
            val mysqlDao = MySQLUserDao()
            Log.d(TAG, "Intentando conectar...")
            val isConnected = mysqlDao.connect(connectionUrl, username, password)
            
            if (isConnected) {
                Log.d(TAG, "✅ ¡Conexión MySQL exitosa!")
                mysqlDao.disconnect()
                "✅ ¡MySQL CONECTADO!\nHost: $host:$port\nBase: $database\nUsuario: $username"
            } else {
                Log.e(TAG, "❌ Falló la conexión MySQL")
                "❌ MySQL NO CONECTADO\nHost: $host:$port\nBase: $database\nUsuario: $username\n\n🔧 SOLUCIONES:\n1. Crear archivo .env en app/src/main/assets/\n2. Verificar que MySQL esté ejecutándose\n3. Usar DB_HOST=10.0.2.2 para emulador"
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en prueba MySQL: ${e.message}", e)
            "❌ Error: ${e.message}\n\n🔧 CREA EL ARCHIVO .env EN:\napp/src/main/assets/.env"
        }
    }
}