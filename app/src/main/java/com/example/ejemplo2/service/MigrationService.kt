package com.example.ejemplo2.service

import android.content.Context
import com.example.ejemplo2.data.database.AppDatabase
import com.example.ejemplo2.data.entity.User
import com.example.ejemplo2.data.repository.MigrationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

/**
 * Servicio para manejar la migración de datos de SQLite a MySQL
 */
class MigrationService(private val context: Context) {
    
    private val migrationRepository = MigrationRepository()
    private val database = AppDatabase.getDatabase(context)
    private val userDao = database.userDao()
    
    /**
     * Método de prueba para verificar conectividad
     */
    suspend fun testConnectivity(): Result<String> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("MigrationService", "🧪 Probando conectividad...")
            val result = migrationRepository.checkApiHealth()
            result.fold(
                onSuccess = { 
                    android.util.Log.d("MigrationService", "✅ Conectividad OK")
                    Result.success("Conectividad OK") 
                },
                onFailure = { 
                    android.util.Log.e("MigrationService", "❌ Error de conectividad: ${it.message}")
                    Result.failure(it) 
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("MigrationService", "❌ Excepción en prueba de conectividad: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Verificar si la API está disponible
     */
    suspend fun checkApiAvailability(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("MigrationService", "🔍 Verificando disponibilidad de la API...")
            val result = migrationRepository.checkApiHealth()
            result.fold(
                onSuccess = { 
                    android.util.Log.d("MigrationService", "✅ API disponible")
                    Result.success(true) 
                },
                onFailure = { 
                    android.util.Log.e("MigrationService", "❌ API no disponible: ${it.message}")
                    Result.failure(it) 
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("MigrationService", "❌ Excepción verificando API: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Configurar la base de datos MySQL
     */
    suspend fun setupMySQLDatabase(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val result = migrationRepository.setupDatabase()
            result.fold(
                onSuccess = { Result.success(true) },
                onFailure = { Result.failure(it) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Obtener todos los usuarios de SQLite (datos más recientes)
     */
    suspend fun getAllSQLiteUsers(): Result<List<User>> = withContext(Dispatchers.IO) {
        try {
            val users = userDao.getAllUsers()
            // Convertir Flow a List usando first() para obtener los datos más recientes
            val userList = users.first()
            android.util.Log.d("MigrationService", "Obteniendo ${userList.size} usuarios de SQLite para migración")
            
            // Log de cada usuario para verificar que tiene los datos actualizados
            userList.forEach { user ->
                android.util.Log.d("MigrationService", "Usuario: ${user.name} ${user.lastName}, Alias: ${user.alias}, Avatar: ${user.avatarPath}, Actualizado: ${user.updatedAt}")
            }
            
            Result.success(userList)
        } catch (e: Exception) {
            android.util.Log.e("MigrationService", "Error obteniendo usuarios: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Migrar todos los usuarios de SQLite a MySQL
     */
    suspend fun migrateAllUsers(): Result<MigrationResult> = withContext(Dispatchers.IO) {
        try {
            // Obtener usuarios de SQLite
            val sqliteUsersResult = getAllSQLiteUsers()
            if (sqliteUsersResult.isFailure) {
                return@withContext Result.failure(sqliteUsersResult.exceptionOrNull() ?: Exception("Error obteniendo usuarios"))
            }
            
            val sqliteUsers = sqliteUsersResult.getOrNull() ?: emptyList()
            
            if (sqliteUsers.isEmpty()) {
                return@withContext Result.success(
                    MigrationResult(
                        totalUsers = 0,
                        migratedUsers = 0,
                        failedUsers = 0,
                        errors = listOf("No hay usuarios para migrar")
                    )
                )
            }
            
            // Migrar usuarios
            val migrationResult = migrationRepository.migrateUsers(sqliteUsers)
            if (migrationResult.isFailure) {
                return@withContext Result.failure(migrationResult.exceptionOrNull() ?: Exception("Error en la migración"))
            }
            
            val result = migrationResult.getOrNull()
            if (result != null) {
                Result.success(
                    MigrationResult(
                        totalUsers = result.total,
                        migratedUsers = result.migrated,
                        failedUsers = result.total - result.migrated,
                        errors = result.errors
                    )
                )
            } else {
                Result.failure(Exception("Respuesta vacía de la migración"))
            }
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Migrar un usuario específico
     */
    suspend fun migrateSingleUser(userId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUserById(userId)
            if (user == null) {
                return@withContext Result.failure(Exception("Usuario no encontrado"))
            }
            
            android.util.Log.d("MigrationService", "Migrando usuario individual: ${user.name} ${user.lastName}, Alias: ${user.alias}, Avatar: ${user.avatarPath}")
            
            val result = migrationRepository.migrateSingleUser(user)
            result.fold(
                onSuccess = { Result.success(true) },
                onFailure = { Result.failure(it) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Actualizar un usuario específico en MySQL usando el endpoint PUT específico
     */
    suspend fun updateUserInMySQL(userId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("MigrationService", "🚀 Iniciando actualización directa de usuario ID: $userId")
            
            // Obtener usuario actualizado de SQLite
            android.util.Log.d("MigrationService", "🔍 Obteniendo usuario de SQLite...")
            val user = userDao.getUserById(userId)
            if (user == null) {
                android.util.Log.e("MigrationService", "❌ Usuario no encontrado en SQLite con ID: $userId")
                return@withContext Result.failure(Exception("Usuario no encontrado en SQLite"))
            }
            
            android.util.Log.d("MigrationService", "✅ Usuario encontrado: ${user.name} ${user.lastName}, Email: ${user.email}")
            android.util.Log.d("MigrationService", "📧 Actualizando en MySQL usando email: ${user.email}")
            
            // Llamar directamente al método de actualización
            val result = migrationRepository.updateUser(user)
            result.fold(
                onSuccess = { 
                    android.util.Log.d("MigrationService", "✅ Usuario actualizado exitosamente en MySQL")
                    Result.success(true) 
                },
                onFailure = { 
                    android.util.Log.e("MigrationService", "❌ Error actualizando usuario: ${it.message}")
                    Result.failure(it) 
                }
            )
            
        } catch (e: Exception) {
            android.util.Log.e("MigrationService", "❌ Excepción en actualización: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Obtener usuarios de MySQL para verificación
     */
    suspend fun getMySQLUsers(): Result<List<com.example.ejemplo2.data.remote.entity.MySQLUser>> = withContext(Dispatchers.IO) {
        try {
            val result = migrationRepository.getAllUsers()
            result.fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(it) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Ejecutar migración completa con verificación
     */
    suspend fun executeFullMigration(): Result<MigrationResult> = withContext(Dispatchers.IO) {
        try {
            // 1. Verificar API
            val apiCheck = checkApiAvailability()
            if (apiCheck.isFailure) {
                return@withContext Result.failure(Exception("API no disponible: ${apiCheck.exceptionOrNull()?.message}"))
            }
            
            // 2. Configurar base de datos
            val setupResult = setupMySQLDatabase()
            if (setupResult.isFailure) {
                return@withContext Result.failure(Exception("Error configurando base de datos: ${setupResult.exceptionOrNull()?.message}"))
            }
            
            // 3. Migrar usuarios
            val migrationResult = migrateAllUsers()
            if (migrationResult.isFailure) {
                return@withContext Result.failure(Exception("Error en la migración: ${migrationResult.exceptionOrNull()?.message}"))
            }
            
            migrationResult
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Crear un usuario individual en MySQL (igual que el botón de migración)
     */
    suspend fun createUserInMySQL(user: User): Result<String> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("MigrationService", "🔄 Creando usuario en MySQL: ${user.email}")
            
            // Usar la misma lógica que migrateAllUsers() pero con un solo usuario
            val migrationResult = migrationRepository.migrateUsers(listOf(user))
            if (migrationResult.isFailure) {
                return@withContext Result.failure(migrationResult.exceptionOrNull() ?: Exception("Error en la migración"))
            }
            
            val result = migrationResult.getOrNull()
            if (result != null) {
                if (result.migrated > 0) {
                    android.util.Log.d("MigrationService", "✅ Usuario creado en MySQL exitosamente")
                    Result.success("Usuario creado en MySQL exitosamente")
                } else {
                    android.util.Log.e("MigrationService", "❌ Usuario no fue migrado")
                    Result.failure(Exception("Usuario no fue migrado: ${result.errors.joinToString()}"))
                }
            } else {
                Result.failure(Exception("Respuesta vacía de la migración"))
            }
        } catch (e: Exception) {
            android.util.Log.e("MigrationService", "❌ Excepción creando usuario en MySQL: ${e.message}", e)
            Result.failure(e)
        }
    }
}

/**
 * Resultado de la migración
 */
data class MigrationResult(
    val totalUsers: Int,
    val migratedUsers: Int,
    val failedUsers: Int,
    val errors: List<String>
) {
    val isSuccess: Boolean
        get() = failedUsers == 0 && errors.isEmpty()
    
    val successRate: Float
        get() = if (totalUsers > 0) migratedUsers.toFloat() / totalUsers.toFloat() else 0f
}
