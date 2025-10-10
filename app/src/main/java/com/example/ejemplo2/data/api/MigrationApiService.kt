package com.example.ejemplo2.data.api

import com.example.ejemplo2.data.remote.entity.MySQLUser
import retrofit2.Response
import retrofit2.http.*

/**
 * Interfaz para la API REST de migración de datos
 */
interface MigrationApiService {
    
    /**
     * Verificar el estado de la API
     */
    @GET("health.php")
    suspend fun checkHealth(): Response<ApiResponse<HealthResponse>>
    
    /**
     * Configurar la base de datos MySQL
     */
    @POST("setup.php")
    suspend fun setupDatabase(): Response<ApiResponse<SetupResponse>>
    
    /**
     * Migrar usuarios desde SQLite a MySQL
     */
    @POST("migrate.php")
    suspend fun migrateUsers(@Body request: MigrateUsersRequest): Response<ApiResponse<MigrationResponse>>
    
    /**
     * Crear un usuario individual en MySQL
     */
    @POST("users")
    suspend fun createUser(@Body user: MySQLUser): Response<ApiResponse<CreateUserResponse>>
    
    /**
     * Obtener todos los usuarios de MySQL
     */
    @GET("users")
    suspend fun getAllUsers(): Response<ApiResponse<UsersResponse>>
    
    /**
     * Actualizar un usuario específico en MySQL
     */
    @PUT("users/{id}")
    suspend fun updateUser(@Path("id") userId: Long, @Body user: MySQLUser): Response<ApiResponse<UpdateUserResponse>>
    
    /**
     * Actualizar un usuario específico en MySQL por email
     */
    @PUT("users/by-email")
    suspend fun updateUserByEmail(@Body user: MySQLUser): Response<ApiResponse<UpdateUserResponse>>
    
    /**
     * Autenticar usuario (login)
     */
    @POST("login")
    suspend fun loginUser(@Body request: LoginRequest): Response<ApiResponse<LoginResponse>>
}

/**
 * Respuesta genérica de la API
 */
data class ApiResponse<T>(
    val data: T? = null,
    val message: String? = null,
    val error: String? = null
)

/**
 * Respuesta de health check
 */
data class HealthResponse(
    val status: String,
    val message: String
)

/**
 * Respuesta de setup
 */
data class SetupResponse(
    val message: String
)

/**
 * Solicitud de migración de usuarios
 */
data class MigrateUsersRequest(
    val users: List<UserMigrationData>
)

/**
 * Datos de usuario para migración
 */
data class UserMigrationData(
    val name: String,
    val lastName: String,
    val email: String,
    val password: String,
    val phone: String? = null,
    val address: String? = null,
    val alias: String,
    val avatarPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Respuesta de migración
 */
data class MigrationResponse(
    val message: String,
    val migrated: Int,
    val total: Int,
    val errors: List<String>
)

/**
 * Respuesta de creación de usuario
 */
data class CreateUserResponse(
    val message: String,
    val userId: Long
)

/**
 * Respuesta de actualización de usuario
 */
data class UpdateUserResponse(
    val message: String,
    val userId: Long
)

/**
 * Respuesta de usuarios
 */
data class UsersResponse(
    val users: List<MySQLUser>
)

/**
 * Solicitud de login
 */
data class LoginRequest(
    val email: String,
    val password: String
)

/**
 * Respuesta de login
 */
data class LoginResponse(
    val message: String,
    val user: MySQLUser
)
