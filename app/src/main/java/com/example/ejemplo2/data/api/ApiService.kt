package com.example.ejemplo2.data.api

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.ejemplo2.data.database.AppDatabase
import com.example.ejemplo2.data.entity.RecipeImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ApiService(private val context: Context) {
    
    companion object {
        private const val TAG = "ApiService"
        // TODO: Cambiar esta URL por la URL de tu servidor
        private const val BASE_URL = "http://10.0.2.2:8000/api" // Para emulador Android (agregué puerto 8000)
        // Para dispositivo físico usar: "http://TU_IP_LOCAL:8000/api"
    }
    
    /**
     * Probar conectividad con el servidor
     */
    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/health.php")
            Log.d(TAG, "Probando conexión a: $url")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val responseCode = connection.responseCode
            Log.d(TAG, "Código de respuesta: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).readText()
                Log.d(TAG, "Respuesta del servidor: $response")
                Result.success("Conexión exitosa")
            } else {
                Result.failure(Exception("Error de conexión: $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error probando conexión", e)
            Result.failure(e)
        }
    }
    
    /**
     * Sincronizar receta a MySQL
     */
    suspend fun syncRecipeToMySQL(recipe: com.example.ejemplo2.data.entity.Recipe): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Convertir la receta a JSON
            val jsonObject = JSONObject().apply {
                put("userId", recipe.authorId)
                put("title", recipe.title)
                put("ingredients", recipe.ingredients)
                put("steps", recipe.steps)
                put("authorName", recipe.authorName)
                put("tags", recipe.tags ?: "")
                put("cookingTime", recipe.cookingTime)
                put("servings", recipe.servings)
                put("rating", recipe.rating.toDouble())
                put("isPublished", recipe.isPublished)
                put("createdAt", recipe.createdAt)
                put("updatedAt", recipe.updatedAt)
            }
            
            val requestBody = JSONObject().apply {
                put("recipes", JSONArray().put(jsonObject))
            }
            
            // Hacer la petición HTTP
            val url = URL("$BASE_URL/migrate/recipes")
            Log.d(TAG, "Conectando a: $url")
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.doInput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                Log.d(TAG, "Enviando receta: ${recipe.title}")
                
                // Enviar datos
                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(requestBody.toString())
                writer.flush()
                writer.close()
                
                // Leer respuesta
                val responseCode = connection.responseCode
                Log.d(TAG, "Respuesta del servidor: $responseCode")
                
                val reader = if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader(InputStreamReader(connection.inputStream))
                } else {
                    BufferedReader(InputStreamReader(connection.errorStream))
                }
                
                val response = reader.readText()
                reader.close()
                
                Log.d(TAG, "Respuesta completa: $response")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonResponse = JSONObject(response)
                    val message = jsonResponse.optJSONObject("data")?.optString("message") 
                        ?: jsonResponse.optString("message")
                    val migrated = jsonResponse.optJSONObject("data")?.optInt("migrated") ?: 0
                    
                    Log.d(TAG, "Receta sincronizada: $message, Migradas: $migrated")
                    Result.success("Receta sincronizada: $migrated")
                } else {
                    val errorJson = JSONObject(response)
                    val errorMessage = errorJson.optString("error", "Error desconocido")
                    Log.e(TAG, "Error sincronizando receta (código $responseCode): $errorMessage")
                    Result.failure(Exception("Error del servidor ($responseCode): $errorMessage"))
                }
            } finally {
                connection.disconnect()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando receta", e)
            Result.failure(e)
        }
    }
    
    /**
     * Sincronizar imágenes de recetas a MySQL (método simplificado)
     */
    suspend fun syncRecipeImagesToMySQL(recipeId: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando sincronización de imágenes para receta: $recipeId")
            
            val db = AppDatabase.getDatabase(context)
            val recipeImageDao = db.recipeImageDao()
            
            // Obtener todas las imágenes de la receta desde SQLite
            val imagesFlow = recipeImageDao.getImagesByRecipeId(recipeId)
            var imagesList: List<RecipeImage>? = null
            imagesFlow.collect { list ->
                imagesList = list
                return@collect // Salir del collect después de la primera emisión
            }
            
            Log.d(TAG, "Imágenes encontradas: ${imagesList?.size ?: 0}")
            
            if (imagesList.isNullOrEmpty()) {
                Log.d(TAG, "No hay imágenes para sincronizar")
                return@withContext Result.success("No hay imágenes para sincronizar")
            }
            
            // Convertir las imágenes a JSON
            val jsonArray = JSONArray()
            imagesList?.forEach { image ->
                val jsonObject = JSONObject().apply {
                    put("recipeId", image.recipeId)
                    put("imageData", Base64.encodeToString(image.imageData, Base64.DEFAULT))
                    put("description", image.description ?: "")
                    put("createdAt", image.createdAt)
                    put("updatedAt", image.updatedAt)
                }
                jsonArray.put(jsonObject)
            }
            
            val requestBody = JSONObject().apply {
                put("images", jsonArray)
            }
            
            // Hacer la petición HTTP
            val url = URL("$BASE_URL/migrate/recipe_images")
            Log.d(TAG, "Conectando a: $url")
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.doInput = true
                connection.connectTimeout = 8000 // Timeout más corto
                connection.readTimeout = 8000
                
                Log.d(TAG, "Enviando ${imagesList?.size ?: 0} imágenes...")
                
                // Enviar datos
                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(requestBody.toString())
                writer.flush()
                writer.close()
                
                // Leer respuesta
                val responseCode = connection.responseCode
                Log.d(TAG, "Respuesta del servidor: $responseCode")
                
                val reader = if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader(InputStreamReader(connection.inputStream))
                } else {
                    BufferedReader(InputStreamReader(connection.errorStream))
                }
                
                val response = reader.readText()
                reader.close()
                
                Log.d(TAG, "Respuesta completa: $response")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonResponse = JSONObject(response)
                    val message = jsonResponse.optJSONObject("data")?.optString("message") 
                        ?: jsonResponse.optString("message")
                    val migrated = jsonResponse.optJSONObject("data")?.optInt("migrated") ?: 0
                    
                    Log.d(TAG, "Sincronización exitosa: $message, Migradas: $migrated")
                    Result.success("Imágenes sincronizadas: $migrated de ${imagesList?.size ?: 0}")
                } else {
                    val errorJson = JSONObject(response)
                    val errorMessage = errorJson.optString("error", "Error desconocido")
                    Log.e(TAG, "Error en sincronización (código $responseCode): $errorMessage")
                    Result.failure(Exception("Error del servidor ($responseCode): $errorMessage"))
                }
            } finally {
                connection.disconnect()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando imágenes", e)
            Result.failure(e)
        }
    }
    
    /**
     * Sincronizar todas las imágenes de todas las recetas
     */
    suspend fun syncAllRecipeImages(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val recipeDao = db.recipeDao()
            
            // Obtener todas las recetas
            var recipesList: List<com.example.ejemplo2.data.entity.Recipe>? = null
            recipeDao.getAllRecipes().collect { list ->
                recipesList = list
                return@collect // Salir del collect después de la primera emisión
            }
            
            if (recipesList.isNullOrEmpty()) {
                return@withContext Result.success("No hay recetas para sincronizar")
            }
            
            var totalSynced = 0
            var errors = 0
            
            // Sincronizar imágenes de cada receta
            recipesList?.forEach { recipe ->
                val result = syncRecipeImagesToMySQL(recipe.id)
                if (result.isSuccess) {
                    totalSynced++
                } else {
                    errors++
                }
            }
            
            Result.success("Sincronización completada: $totalSynced recetas, $errors errores")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando todas las imágenes", e)
            Result.failure(e)
        }
    }
}

