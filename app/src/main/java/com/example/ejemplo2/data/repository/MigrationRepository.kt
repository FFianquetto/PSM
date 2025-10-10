package com.example.ejemplo2.data.repository

import com.example.ejemplo2.data.api.ApiClient
import com.example.ejemplo2.data.api.*
import com.example.ejemplo2.data.entity.User
import com.example.ejemplo2.data.remote.entity.MySQLUser
import com.example.ejemplo2.utils.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repositorio para manejar la migración de datos de SQLite a MySQL
 */
class MigrationRepository {
    
    private val apiService = ApiClient.migrationApiService
    
    /**
     * Verificar si la API está funcionando
     */
    suspend fun checkApiHealth(): Result<HealthResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.checkHealth()
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception("Respuesta vacía de la API"))
                }
            } else {
                Result.failure(Exception("Error HTTP: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Configurar la base de datos MySQL
     */
    suspend fun setupDatabase(): Result<SetupResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.setupDatabase()
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception("Respuesta vacía de la API"))
                }
            } else {
                Result.failure(Exception("Error HTTP: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Migrar usuarios desde SQLite a MySQL
     */
    suspend fun migrateUsers(users: List<User>): Result<MigrationResponse> = withContext(Dispatchers.IO) {
        try {
            // Convertir usuarios de SQLite a formato de migración
            val migrationData = users.map { user ->
                UserMigrationData(
                    name = user.name,
                    lastName = user.lastName,
                    email = user.email,
                    password = user.password,
                    phone = user.phone,
                    address = user.address,
                    alias = user.alias,
                    avatarPath = user.avatarPath,
                    createdAt = user.createdAt,
                    updatedAt = user.updatedAt
                )
            }
            
            val request = MigrateUsersRequest(users = migrationData)
            val response = apiService.migrateUsers(request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception("Respuesta vacía de la API"))
                }
            } else {
                Result.failure(Exception("Error HTTP: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Crear un usuario individual en MySQL
     */
    suspend fun createUser(user: MySQLUser): Result<CreateUserResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.createUser(user)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception("Respuesta vacía de la API"))
                }
            } else {
                Result.failure(Exception("Error HTTP: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Obtener todos los usuarios de MySQL
     */
    suspend fun getAllUsers(): Result<List<MySQLUser>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getAllUsers()
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.data != null) {
                    Result.success(body.data.users)
                } else {
                    Result.failure(Exception("Respuesta vacía de la API"))
                }
            } else {
                Result.failure(Exception("Error HTTP: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Actualizar un usuario específico en MySQL
     */
    suspend fun updateUser(user: User): Result<UpdateUserResponse> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("MigrationRepository", "Iniciando actualización de usuario ID: ${user.id}")
            android.util.Log.d("MigrationRepository", "Datos del usuario: ${user.name} ${user.lastName}, Email: ${user.email}, Alias: ${user.alias}")
            
            // Convertir usuario de SQLite a MySQL
            val mysqlUser = MySQLUser(
                id = user.id, // Usar el mismo ID
                name = user.name,
                lastName = user.lastName,
                email = user.email,
                password = user.password,
                phone = user.phone,
                address = user.address,
                alias = user.alias,
                avatarPath = user.avatarPath,
                createdAt = user.createdAt,
                updatedAt = user.updatedAt
            )
            
            android.util.Log.d("MigrationRepository", "Enviando PUT a: /users/by-email")
            android.util.Log.d("MigrationRepository", "Datos MySQL: ${mysqlUser.name} ${mysqlUser.lastName}, Email: ${mysqlUser.email}")
            
            val response = apiService.updateUserByEmail(mysqlUser)
            
            android.util.Log.d("MigrationRepository", "Respuesta HTTP: ${response.code()} - ${response.message()}")
            
            if (response.isSuccessful) {
                val body = response.body()
                android.util.Log.d("MigrationRepository", "Respuesta exitosa: ${body?.data}")
                if (body?.data != null) {
                    Result.success(body.data)
                } else {
                    android.util.Log.e("MigrationRepository", "Respuesta vacía de la API")
                    Result.failure(Exception("Respuesta vacía de la API"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("MigrationRepository", "Error HTTP: ${response.code()} - ${response.message()}")
                android.util.Log.e("MigrationRepository", "Error body: $errorBody")
                Result.failure(Exception("Error HTTP: ${response.code()} - ${response.message()} - $errorBody"))
            }
        } catch (e: Exception) {
            android.util.Log.e("MigrationRepository", "Excepción en updateUser: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Migrar un usuario individual desde SQLite
     */
    suspend fun migrateSingleUser(user: User): Result<CreateUserResponse> = withContext(Dispatchers.IO) {
        try {
            val mysqlUser = MySQLUser(
                name = user.name,
                lastName = user.lastName,
                email = user.email,
                password = user.password,
                phone = user.phone,
                address = user.address,
                alias = user.alias,
                avatarPath = user.avatarPath,
                createdAt = user.createdAt,
                updatedAt = user.updatedAt
            )
            
            createUser(mysqlUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Autenticar usuario (login)
     */
    suspend fun loginUser(email: String, password: String): ValidationResult = withContext(Dispatchers.IO) {
        try {
            val request = LoginRequest(email = email, password = password)
            val response = apiService.loginUser(request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.data != null) {
                    ValidationResult(true, body.data.message)
                } else {
                    ValidationResult(false, "Respuesta vacía de la API: ${body}")
                }
            } else {
                val errorMessage = when (response.code()) {
                    401 -> "Credenciales incorrectas"
                    400 -> "Email y contraseña requeridos"
                    else -> "Error HTTP: ${response.code()}"
                }
                ValidationResult(false, errorMessage)
            }
        } catch (e: Exception) {
            ValidationResult(false, "Error de conexión: ${e.message}")
        }
    }
    
    /**
     * Obtener usuario por email usando API
     */
    suspend fun getUserByEmail(email: String): MySQLUser? = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getAllUsers()
            if (response.isSuccessful) {
                val body = response.body()
                body?.data?.users?.find { it.email == email }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
