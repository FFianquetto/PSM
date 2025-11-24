package com.example.ejemplo2.data.api

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.ejemplo2.data.database.AppDatabase
import com.example.ejemplo2.data.entity.RecipeImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
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
                put("description", recipe.description)
                put("ingredients", recipe.ingredients)
                put("steps", recipe.steps)
                put("authorName", recipe.authorName)
                put("tags", recipe.tags ?: "")
                put("cookingTime", recipe.cookingTime)
                put("servings", recipe.servings)
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

            val imagesFlow = recipeImageDao.getImagesByRecipeId(recipeId)
            var imagesList: List<RecipeImage>? = null
            imagesFlow.collect { list ->
                imagesList = list
                return@collect
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
                    // No enviar description, ya no está en la tabla
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
    
    /**
     * Obtener usuario por email desde MySQL
     */
    suspend fun getUserByEmail(email: String): Result<UserData> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/users/by-email")
            Log.d(TAG, "Consultando usuario por email: $url")
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                
                // Enviar el email en el cuerpo de la petición
                val requestBody = JSONObject().apply {
                    put("email", email)
                }
                
                connection.outputStream.use { outputStream ->
                    outputStream.write(requestBody.toString().toByteArray())
                }
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Respuesta del servidor: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(connection.inputStream)).readText()
                    Log.d(TAG, "Respuesta completa: $response")
                    
                    val jsonResponse = JSONObject(response)
                    val userData = jsonResponse.getJSONObject("data").getJSONObject("user")
                    
                    val user = UserData(
                        id = userData.getLong("id"),
                        name = userData.getString("name"),
                        lastName = userData.getString("lastName"),
                        email = userData.getString("email"),
                        phone = userData.optString("phone", ""),
                        address = userData.optString("address", ""),
                        alias = userData.getString("alias"),
                        avatarPath = userData.optString("avatarPath", null),
                        createdAt = userData.getLong("createdAt"),
                        updatedAt = userData.getLong("updatedAt")
                    )
                    
                    Result.success(user)
                } else {
                    val errorResponse = BufferedReader(InputStreamReader(connection.errorStream)).readText()
                    Log.e(TAG, "Error obteniendo usuario por email: $errorResponse")
                    Result.failure(Exception("Error obteniendo usuario por email: $responseCode"))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando usuario por email", e)
            Result.failure(e)
        }
    }
    
    /**
     * Obtener usuario por ID desde MySQL
     */
    suspend fun getUserById(userId: Long): Result<UserData> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/users/$userId")
            Log.d(TAG, "Consultando usuario: $url")
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Respuesta del servidor: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(connection.inputStream)).readText()
                    Log.d(TAG, "Respuesta completa: $response")
                    
                    val jsonResponse = JSONObject(response)
                    val userData = jsonResponse.getJSONObject("data").getJSONObject("user")
                    
                    val user = UserData(
                        id = userData.getLong("id"),
                        name = userData.getString("name"),
                        lastName = userData.getString("lastName"),
                        email = userData.getString("email"),
                        phone = userData.optString("phone", ""),
                        address = userData.optString("address", ""),
                        alias = userData.getString("alias"),
                        avatarPath = userData.optString("avatarPath", null),
                        createdAt = userData.getLong("createdAt"),
                        updatedAt = userData.getLong("updatedAt")
                    )
                    
                    Result.success(user)
                } else {
                    val errorResponse = BufferedReader(InputStreamReader(connection.errorStream)).readText()
                    Log.e(TAG, "Error obteniendo usuario: $errorResponse")
                    Result.failure(Exception("Error obteniendo usuario: $responseCode"))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando usuario", e)
            Result.failure(e)
        }
    }
    
    /**
     * Crear una receta con imágenes directamente en MySQL
     */
    suspend fun createRecipeWithImages(
        title: String,
        description: String,
        ingredients: String,
        steps: String,
        authorId: Long,
        authorName: String,
        tags: String? = null,
        cookingTime: Int = 0,
        servings: Int = 1,
        isPublished: Boolean = false,
        images: List<Pair<android.graphics.Bitmap, String>> = emptyList(),
        createdAtOverride: Long? = null,
        updatedAtOverride: Long? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val createdAt = createdAtOverride ?: System.currentTimeMillis()
            val updatedAt = updatedAtOverride ?: createdAt
            
            // Convertir imágenes a Base64 (permite múltiples imágenes)
            val imagesArray = JSONArray()
            images.forEach { (bitmap, _) ->
                val byteArrayOutputStream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream)
                val imageBytes = byteArrayOutputStream.toByteArray()
                val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                
                imagesArray.put(JSONObject().apply {
                    put("imageData", base64Image)
                    put("createdAt", createdAt)
                    put("updatedAt", updatedAt)
                })
            }
            
            // Crear el JSON de la receta
            val requestBody = JSONObject().apply {
                put("title", title)
                put("description", description)
                put("ingredients", ingredients)
                put("steps", steps)
                put("authorId", authorId)
                put("authorName", authorName)
                put("tags", tags ?: "")
                put("cookingTime", cookingTime)
                put("servings", servings)
                put("isPublished", isPublished)
                put("createdAt", createdAt)
                put("updatedAt", updatedAt)
                put("images", imagesArray)
            }
            
            // Hacer la petición HTTP
            val url = URL("$BASE_URL/recipes")
            Log.d(TAG, "Creando receta en MySQL: $url")
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.doInput = true
                connection.connectTimeout = 30000 // 30 segundos para imágenes grandes
                connection.readTimeout = 30000
                
                Log.d(TAG, "Enviando receta: $title con ${images.size} imágenes")
                
                // Enviar datos
                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(requestBody.toString())
                writer.flush()
                writer.close()
                
                // Leer respuesta
                val responseCode = connection.responseCode
                Log.d(TAG, "Respuesta del servidor: $responseCode")
                
                val reader = if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    BufferedReader(InputStreamReader(connection.inputStream))
                } else {
                    BufferedReader(InputStreamReader(connection.errorStream))
                }
                
                val response = reader.readText()
                reader.close()
                
                Log.d(TAG, "Respuesta completa: $response")
                
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    val jsonResponse = JSONObject(response)
                    val recipeId = jsonResponse.getJSONObject("data").getLong("recipeId")
                    val imagesInserted = jsonResponse.getJSONObject("data").optInt("imagesInserted", 0)
                    
                    Log.d(TAG, "Receta creada en MySQL: ID=$recipeId, Imágenes=$imagesInserted")
                    Result.success(recipeId)
                } else {
                    val errorJson = JSONObject(response)
                    val errorMessage = errorJson.optString("error", "Error desconocido")
                    Log.e(TAG, "Error creando receta (código $responseCode): $errorMessage")
                    Result.failure(Exception("Error del servidor ($responseCode): $errorMessage"))
                }
            } finally {
                connection.disconnect()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creando receta en MySQL", e)
            Result.failure(e)
        }
    }
    
    /**
     * Obtener una receta por ID desde MySQL con todas sus imágenes
     */
    suspend fun getRecipeById(recipeId: Long): Result<RecipeDetailData> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/recipes/$recipeId")
            Log.d(TAG, "=== CONSULTANDO RECETA POR ID ===")
            Log.d(TAG, "URL: $url")
            Log.d(TAG, "Recipe ID: $recipeId")
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Código de respuesta: $responseCode")
                
                val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader(InputStreamReader(connection.inputStream)).readText()
                } else {
                    val errorStream = connection.errorStream
                    if (errorStream != null) {
                        BufferedReader(InputStreamReader(errorStream)).readText()
                    } else {
                        "Sin mensaje de error"
                    }
                }
                
                Log.d(TAG, "Respuesta completa (primeros 500 chars): ${response.take(500)}")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try {
                        val jsonResponse = JSONObject(response)
                        Log.d(TAG, "JSON parseado correctamente")
                        
                        // Verificar estructura de respuesta
                        if (!jsonResponse.has("data")) {
                            Log.e(TAG, "ERROR: Respuesta no tiene campo 'data'")
                            Log.e(TAG, "Keys disponibles: ${jsonResponse.keys().asSequence().toList()}")
                            return@withContext Result.failure(Exception("Respuesta inválida: falta campo 'data'"))
                        }
                        
                        val dataObj = jsonResponse.getJSONObject("data")
                        Log.d(TAG, "Campo 'data' encontrado")
                        
                        if (!dataObj.has("recipe")) {
                            Log.e(TAG, "ERROR: Campo 'data' no tiene 'recipe'")
                            Log.e(TAG, "Keys en data: ${dataObj.keys().asSequence().toList()}")
                            
                            // Verificar si hay un error
                            if (dataObj.has("error")) {
                                val errorMsg = dataObj.getString("error")
                                Log.e(TAG, "Error en respuesta: $errorMsg")
                                return@withContext Result.failure(Exception(errorMsg))
                            }
                            
                            return@withContext Result.failure(Exception("Respuesta inválida: falta campo 'recipe'"))
                        }
                        
                        val recipeJson = dataObj.getJSONObject("recipe")
                        Log.d(TAG, "✓ Receta encontrada en respuesta JSON")
                        Log.d(TAG, "  Título: ${recipeJson.optString("title", "N/A")}")
                        Log.d(TAG, "  ID: ${recipeJson.optLong("id", -1)}")
                        Log.d(TAG, "  Autor: ${recipeJson.optString("authorName", "N/A")}")
                        
                        // Procesar todas las imágenes
                        val imagesList = mutableListOf<ByteArray>()
                        if (recipeJson.has("images") && !recipeJson.isNull("images")) {
                            try {
                                val imagesArray = recipeJson.getJSONArray("images")
                                Log.d(TAG, "Procesando ${imagesArray.length()} imágenes")
                                
                                for (j in 0 until imagesArray.length()) {
                                    try {
                                        val base64Image = imagesArray.getString(j)
                                        if (base64Image.isNotEmpty()) {
                                            val cleanedBase64 = base64Image.trim().replace("\n", "").replace("\r", "").replace(" ", "")
                                            if (cleanedBase64.isNotEmpty()) {
                                                val decodedImage = Base64.decode(cleanedBase64, Base64.DEFAULT)
                                                imagesList.add(decodedImage)
                                                Log.d(TAG, "Imagen ${j + 1} decodificada: ${decodedImage.size} bytes")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error decodificando imagen ${j + 1}: ${e.message}", e)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error procesando array de imágenes: ${e.message}", e)
                            }
                        } else {
                            Log.d(TAG, "No hay imágenes en la respuesta")
                        }
                        
                        Log.d(TAG, "Total imágenes procesadas: ${imagesList.size}")
                        
                        // Crear objeto RecipeDetailData
                        val recipe = RecipeDetailData(
                            id = recipeJson.getLong("id"),
                            title = recipeJson.getString("title"),
                            description = recipeJson.optString("description", ""),
                            ingredients = recipeJson.optString("ingredients", ""),
                            steps = recipeJson.optString("steps", ""),
                            authorId = recipeJson.getLong("authorId"),
                            authorName = recipeJson.optString("authorName", "Autor desconocido"),
                            cookingTime = recipeJson.optInt("cookingTime", 0),
                            servings = recipeJson.optInt("servings", 1),
                            rating = recipeJson.optDouble("rating", 0.0).toFloat(),
                            isPublished = recipeJson.optBoolean("isPublished", false),
                            createdAt = recipeJson.getLong("createdAt"),
                            updatedAt = recipeJson.getLong("updatedAt"),
                            images = imagesList
                        )
                        
                        Log.d(TAG, "Receta parseada exitosamente: ${recipe.title}")
                        Result.success(recipe)
                    } catch (e: JSONException) {
                        Log.e(TAG, "Error parseando JSON: ${e.message}", e)
                        Log.e(TAG, "Respuesta recibida: $response")
                        Result.failure(Exception("Error parseando respuesta: ${e.message}"))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error procesando respuesta: ${e.message}", e)
                        Result.failure(e)
                    }
                } else {
                    Log.e(TAG, "Error HTTP $responseCode: $response")
                    Result.failure(Exception("Error del servidor ($responseCode): $response"))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando receta por ID: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Obtener recetas a las que un usuario le dio like desde MySQL
     */
    suspend fun getLikedRecipesByUserId(userId: Long): Result<List<RecipeFeedData>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/users/$userId/liked-recipes")
            Log.d(TAG, "Consultando recetas con like del usuario: $url")
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Respuesta del servidor: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(connection.inputStream)).readText()
                    Log.d(TAG, "=== RESPUESTA DE RECETAS CON LIKE ===")
                    Log.d(TAG, "Respuesta completa (primeros 1000 chars): ${response.take(1000)}")
                    
                    try {
                        val jsonResponse = JSONObject(response)
                        Log.d(TAG, "JSON parseado correctamente")
                        
                        // Verificar estructura
                        if (!jsonResponse.has("data")) {
                            Log.e(TAG, "ERROR: Respuesta no tiene campo 'data'")
                            Log.e(TAG, "Keys disponibles: ${jsonResponse.keys().asSequence().toList()}")
                            return@withContext Result.failure(Exception("Respuesta inválida: falta campo 'data'"))
                        }
                        
                        val dataObj = jsonResponse.getJSONObject("data")
                        
                        if (!dataObj.has("recipes")) {
                            Log.e(TAG, "ERROR: Campo 'data' no tiene 'recipes'")
                            Log.e(TAG, "Keys en data: ${dataObj.keys().asSequence().toList()}")
                            return@withContext Result.failure(Exception("Respuesta inválida: falta campo 'recipes'"))
                        }
                        
                        val recipesArray = dataObj.getJSONArray("recipes")
                        Log.d(TAG, "=== RESPUESTA DE RECETAS CON LIKE ===")
                        Log.d(TAG, "Total recetas con like en respuesta: ${recipesArray.length()}")
                        
                        // Log detallado de las primeras recetas si hay alguna
                        if (recipesArray.length() > 0) {
                            Log.d(TAG, "Primeras recetas encontradas:")
                            for (i in 0 until minOf(3, recipesArray.length())) {
                                val recipe = recipesArray.getJSONObject(i)
                                Log.d(TAG, "  Receta ${i + 1}: ID=${recipe.optLong("id")}, Título=${recipe.optString("title", "N/A")}")
                            }
                        } else {
                            Log.w(TAG, "⚠️ No se encontraron recetas con like para el usuario ID: $userId")
                        }
                        
                        val recipes = mutableListOf<RecipeFeedData>()
                        for (i in 0 until recipesArray.length()) {
                            try {
                                val recipeJson = recipesArray.getJSONObject(i)
                                Log.d(TAG, "Procesando receta ${i + 1}/${recipesArray.length()}: ID=${recipeJson.optLong("id")}, Título=${recipeJson.optString("title", "N/A")}")
                                
                                // Procesar todas las imágenes de la receta
                                var imageData: ByteArray? = null
                                val imagesList = mutableListOf<ByteArray>()
                                
                                // Procesar array de imágenes si existe
                                if (recipeJson.has("images") && !recipeJson.isNull("images")) {
                                    try {
                                        val imagesArray = recipeJson.getJSONArray("images")
                                        for (j in 0 until imagesArray.length()) {
                                            try {
                                                val base64Image = imagesArray.getString(j)
                                                if (base64Image.isNotEmpty()) {
                                                    // Limpiar el string Base64
                                                    val cleanedBase64 = base64Image.trim().replace("\n", "").replace("\r", "").replace(" ", "")
                                                    if (cleanedBase64.isNotEmpty()) {
                                                        val decodedImage = Base64.decode(cleanedBase64, Base64.DEFAULT)
                                                        imagesList.add(decodedImage)
                                                        Log.d(TAG, "Imagen ${j + 1} decodificada para receta ${recipeJson.optLong("id")}: ${decodedImage.size} bytes")
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error decodificando imagen ${j + 1} para receta ${recipeJson.optLong("id")}", e)
                                            }
                                        }
                                        // Usar la primera imagen como imageData para compatibilidad
                                        imageData = imagesList.firstOrNull()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error procesando array de imágenes para receta ${recipeJson.optLong("id")}", e)
                                    }
                                }
                                
                                // Fallback: Si no hay array de imágenes, usar image_data (compatibilidad)
                                if (imagesList.isEmpty() && recipeJson.has("image_data") && !recipeJson.isNull("image_data")) {
                                    try {
                                        val base64Image = recipeJson.getString("image_data")
                                        if (base64Image.isNotEmpty()) {
                                            val cleanedBase64 = base64Image.trim().replace("\n", "").replace("\r", "").replace(" ", "")
                                            if (cleanedBase64.isNotEmpty()) {
                                                imageData = Base64.decode(cleanedBase64, Base64.DEFAULT)
                                                imagesList.add(imageData)
                                                Log.d(TAG, "Imagen única decodificada para receta ${recipeJson.optLong("id")}: ${imageData.size} bytes")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error decodificando imagen única para receta ${recipeJson.optLong("id")}", e)
                                    }
                                }
                                
                                Log.d(TAG, "Total imágenes procesadas para receta ${recipeJson.optLong("id")}: ${imagesList.size}")
                                
                                val authorName = recipeJson.optString("author_name", recipeJson.optString("authorName", ""))
                                val authorAlias = recipeJson.optString("author_alias", recipeJson.optString("authorAlias", authorName))
                                val authorAvatar = recipeJson.optString("author_avatar", recipeJson.optString("authorAvatar", null))
                                    .takeIf { it.isNotBlank() }
                                
                                val recipe = RecipeFeedData(
                                    id = recipeJson.getLong("id"),
                                    title = recipeJson.getString("title"),
                                    description = recipeJson.optString("description", ""),
                                    authorId = recipeJson.optLong("author_id", recipeJson.optLong("authorId", -1)),
                                    authorName = authorName,
                                    authorAlias = authorAlias,
                                    authorAvatar = authorAvatar,
                                    cookingTime = recipeJson.optInt("cooking_time", 0),
                                    servings = recipeJson.optInt("servings", 1),
                                    rating = recipeJson.optDouble("rating", 0.0).toFloat(),
                                    tags = recipeJson.optString("tags", null), // Obtener tags del JSON
                                    isPublished = true, // Solo obtenemos recetas publicadas
                                    createdAt = recipeJson.optLong("created_at", recipeJson.optLong("createdAt", 0)),
                                    imageData = imageData,
                                    images = imagesList
                                )
                                recipes.add(recipe)
                                Log.d(TAG, "Receta ${i + 1} agregada exitosamente")
                            } catch (e: Exception) {
                                Log.e(TAG, "ERROR procesando receta ${i + 1}: ${e.message}", e)
                                // Continuar con la siguiente receta en lugar de fallar todo
                            }
                        }
                        
                        Log.d(TAG, "=== TOTAL RECETAS CON LIKE PROCESADAS: ${recipes.size} ===")
                        Result.success(recipes)
                    } catch (e: JSONException) {
                        Log.e(TAG, "ERROR parseando JSON: ${e.message}", e)
                        Log.e(TAG, "Respuesta recibida: ${response.take(500)}")
                        Result.failure(Exception("Error parseando respuesta JSON: ${e.message}"))
                    } catch (e: Exception) {
                        Log.e(TAG, "ERROR procesando respuesta: ${e.message}", e)
                        Result.failure(e)
                    }
                } else {
                    val errorResponse = BufferedReader(InputStreamReader(connection.errorStream)).readText()
                    Log.e(TAG, "Error obteniendo recetas con like: $errorResponse")
                    Result.failure(Exception("Error obteniendo recetas con like: $responseCode"))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando recetas con like", e)
            Result.failure(e)
        }
    }
    
    /**
     * Obtener recetas publicadas por un usuario en MySQL
     */
    suspend fun getUserPublishedRecipes(userId: Long): Result<List<UserRecipeData>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/users/$userId/recipes")
            Log.d(TAG, "Consultando recetas publicadas del usuario: $url")
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Respuesta del servidor (user recipes): $responseCode")
                
                val response = BufferedReader(InputStreamReader(
                    if (responseCode == HttpURLConnection.HTTP_OK) connection.inputStream else connection.errorStream
                )).readText()
                
                Log.d(TAG, "Respuesta completa (primeros 800 chars): ${response.take(800)}")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonResponse = JSONObject(response)
                    if (!jsonResponse.has("data")) {
                        Log.e(TAG, "Respuesta inválida: falta campo 'data'")
                        return@withContext Result.failure(Exception("Respuesta inválida del servidor"))
                    }
                    
                    val dataObj = jsonResponse.getJSONObject("data")
                    val recipesArray = dataObj.optJSONArray("recipes") ?: JSONArray()
                    
                    val recipes = mutableListOf<UserRecipeData>()
                    for (i in 0 until recipesArray.length()) {
                        val recipeJson = recipesArray.getJSONObject(i)
                        
                        val authorId = recipeJson.optLong("author_id", recipeJson.optLong("authorId", -1))
                        val authorName = recipeJson.optString("author_name", recipeJson.optString("authorName", ""))
                        val cookingTime = recipeJson.optInt("cooking_time", recipeJson.optInt("cookingTime", 0))
                        val servings = recipeJson.optInt("servings", 1)
                        val isPublished = recipeJson.optBoolean("is_published", recipeJson.optBoolean("isPublished", false))
                        val createdAt = recipeJson.optLong("created_at", recipeJson.optLong("createdAt", 0))
                        val updatedAt = if (recipeJson.has("updated_at")) {
                            recipeJson.optLong("updated_at")
                        } else {
                            recipeJson.optLong("updatedAt", createdAt)
                        }
                        
                        val imagesList = mutableListOf<ByteArray>()
                        var imageData: ByteArray? = null
                        
                        if (recipeJson.has("images") && !recipeJson.isNull("images")) {
                            val imagesArray = recipeJson.getJSONArray("images")
                            for (j in 0 until imagesArray.length()) {
                                try {
                                    val base64Image = imagesArray.getString(j)
                                    if (base64Image.isNotEmpty()) {
                                        val cleanedBase64 = base64Image.trim().replace("\n", "").replace("\r", "").replace(" ", "")
                                        if (cleanedBase64.isNotEmpty()) {
                                            val decoded = Base64.decode(cleanedBase64, Base64.DEFAULT)
                                            imagesList.add(decoded)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error decodificando imagen ${j + 1} para receta ${recipeJson.optLong("id")}", e)
                                }
                            }
                            imageData = imagesList.firstOrNull()
                        } else if (recipeJson.has("image_data") && !recipeJson.isNull("image_data")) {
                            try {
                                val base64Image = recipeJson.getString("image_data")
                                val cleanedBase64 = base64Image.trim().replace("\n", "").replace("\r", "").replace(" ", "")
                                if (cleanedBase64.isNotEmpty()) {
                                    imageData = Base64.decode(cleanedBase64, Base64.DEFAULT)
                                    imagesList.add(imageData)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error decodificando image_data para receta ${recipeJson.optLong("id")}", e)
                            }
                        }
                        
                        val description = recipeJson.optString("description", "").takeIf { it.isNotBlank() }
                        val ingredients = recipeJson.optString("ingredients", "").takeIf { it.isNotBlank() }
                        val steps = recipeJson.optString("steps", "").takeIf { it.isNotBlank() }
                        val tags = recipeJson.optString("tags", "").takeIf { it.isNotBlank() }
                        
                        val recipe = UserRecipeData(
                            id = recipeJson.getLong("id"),
                            title = recipeJson.getString("title"),
                            description = description,
                            ingredients = ingredients,
                            steps = steps,
                            authorId = authorId,
                            authorName = authorName,
                            tags = tags,
                            cookingTime = cookingTime,
                            servings = servings,
                            rating = recipeJson.optDouble("rating", 0.0).toFloat(),
                            isPublished = isPublished,
                            createdAt = createdAt,
                            updatedAt = if (updatedAt == 0L) null else updatedAt,
                            images = imagesList,
                            imageData = imageData
                        )
                        recipes.add(recipe)
                    }
                    
                    Log.d(TAG, "Recetas publicadas encontradas: ${recipes.size}")
                    Result.success(recipes)
                } else {
                    Log.e(TAG, "Error HTTP $responseCode al obtener recetas del usuario: $response")
                    Result.failure(Exception("Error obteniendo recetas del usuario: $responseCode"))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando recetas del usuario", e)
            Result.failure(e)
        }
    }
    
    /**
     * Obtener feed de recetas desde MySQL
     */
    suspend fun getRecipesFeed(): Result<List<RecipeFeedData>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/recipes/feed")
            Log.d(TAG, "Consultando feed de recetas: $url")
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Respuesta del servidor: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(connection.inputStream)).readText()
                    Log.d(TAG, "=== RESPUESTA DEL SERVIDOR ===")
                    Log.d(TAG, "Respuesta completa (primeros 1000 chars): ${response.take(1000)}")
                    
                    try {
                        val jsonResponse = JSONObject(response)
                        Log.d(TAG, "JSON parseado correctamente")
                        
                        // Verificar estructura
                        if (!jsonResponse.has("data")) {
                            Log.e(TAG, "ERROR: Respuesta no tiene campo 'data'")
                            Log.e(TAG, "Keys disponibles: ${jsonResponse.keys().asSequence().toList()}")
                            return@withContext Result.failure(Exception("Respuesta inválida: falta campo 'data'"))
                        }
                        
                        val dataObj = jsonResponse.getJSONObject("data")
                        
                        if (!dataObj.has("recipes")) {
                            Log.e(TAG, "ERROR: Campo 'data' no tiene 'recipes'")
                            Log.e(TAG, "Keys en data: ${dataObj.keys().asSequence().toList()}")
                            return@withContext Result.failure(Exception("Respuesta inválida: falta campo 'recipes'"))
                        }
                        
                        val recipesArray = dataObj.getJSONArray("recipes")
                        Log.d(TAG, "Total recetas en respuesta: ${recipesArray.length()}")
                        
                        val recipes = mutableListOf<RecipeFeedData>()
                        for (i in 0 until recipesArray.length()) {
                            try {
                                val recipeJson = recipesArray.getJSONObject(i)
                                Log.d(TAG, "Procesando receta ${i + 1}/${recipesArray.length()}: ID=${recipeJson.optLong("id")}, Título=${recipeJson.optString("title", "N/A")}")
                                
                                // Procesar todas las imágenes de la receta
                                var imageData: ByteArray? = null
                                val imagesList = mutableListOf<ByteArray>()
                                
                                // Procesar array de imágenes si existe
                                if (recipeJson.has("images") && !recipeJson.isNull("images")) {
                                    try {
                                        val imagesArray = recipeJson.getJSONArray("images")
                                        for (j in 0 until imagesArray.length()) {
                                            try {
                                                val base64Image = imagesArray.getString(j)
                                                if (base64Image.isNotEmpty()) {
                                                    // Limpiar el string Base64
                                                    val cleanedBase64 = base64Image.trim().replace("\n", "").replace("\r", "").replace(" ", "")
                                                    if (cleanedBase64.isNotEmpty()) {
                                                        val decodedImage = Base64.decode(cleanedBase64, Base64.DEFAULT)
                                                        imagesList.add(decodedImage)
                                                        Log.d(TAG, "Imagen ${j + 1} decodificada para receta ${recipeJson.optLong("id")}: ${decodedImage.size} bytes")
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error decodificando imagen ${j + 1} para receta ${recipeJson.optLong("id")}", e)
                                            }
                                        }
                                        // Usar la primera imagen como imageData para compatibilidad
                                        imageData = imagesList.firstOrNull()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error procesando array de imágenes para receta ${recipeJson.optLong("id")}", e)
                                    }
                                }
                                
                                // Fallback: Si no hay array de imágenes, usar image_data (compatibilidad)
                                if (imagesList.isEmpty() && recipeJson.has("image_data") && !recipeJson.isNull("image_data")) {
                                    try {
                                        val base64Image = recipeJson.getString("image_data")
                                        if (base64Image.isNotEmpty()) {
                                            val cleanedBase64 = base64Image.trim().replace("\n", "").replace("\r", "").replace(" ", "")
                                            if (cleanedBase64.isNotEmpty()) {
                                                imageData = Base64.decode(cleanedBase64, Base64.DEFAULT)
                                                imagesList.add(imageData)
                                                Log.d(TAG, "Imagen única decodificada para receta ${recipeJson.optLong("id")}: ${imageData.size} bytes")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error decodificando imagen única para receta ${recipeJson.optLong("id")}", e)
                                    }
                                }
                                
                                Log.d(TAG, "Total imágenes procesadas para receta ${recipeJson.optLong("id")}: ${imagesList.size}")
                                
                                // Manejar campos que vienen en snake_case desde MySQL
                                val authorId = if (recipeJson.has("author_id")) {
                                    recipeJson.getLong("author_id")
                                } else {
                                    recipeJson.getLong("authorId")
                                }
                                
                                val authorName = if (recipeJson.has("author_name")) {
                                    recipeJson.getString("author_name")
                                } else {
                                    recipeJson.getString("authorName")
                                }
                                
                                val cookingTime = if (recipeJson.has("cooking_time")) {
                                    recipeJson.optInt("cooking_time", 0)
                                } else {
                                    recipeJson.optInt("cookingTime", 0)
                                }
                                
                                val createdAt = if (recipeJson.has("created_at")) {
                                    recipeJson.getLong("created_at")
                                } else {
                                    recipeJson.getLong("createdAt")
                                }
                                
                                val authorAlias = recipeJson.optString("author_alias", recipeJson.optString("authorAlias", authorName))
                                val authorAvatar = recipeJson.optString("author_avatar", recipeJson.optString("authorAvatar", null))
                                    .takeIf { it.isNotBlank() }
                                
                                val recipe = RecipeFeedData(
                                    id = recipeJson.getLong("id"),
                                    title = recipeJson.getString("title"),
                                    description = recipeJson.optString("description", ""),
                                    authorId = authorId,
                                    authorName = authorName,
                                    authorAlias = authorAlias,
                                    authorAvatar = authorAvatar,
                                    cookingTime = cookingTime,
                                    servings = recipeJson.optInt("servings", 1),
                                    rating = recipeJson.optDouble("rating", 0.0).toFloat(),
                                    tags = null, // Ya no usamos tags
                                    isPublished = true, // Solo obtenemos recetas publicadas
                                    createdAt = createdAt,
                                    imageData = imageData,
                                    images = imagesList
                                )
                                recipes.add(recipe)
                                Log.d(TAG, "✓ Receta ${i + 1} agregada exitosamente: ${recipe.title}")
                            } catch (e: Exception) {
                                Log.e(TAG, "ERROR procesando receta ${i + 1}: ${e.message}", e)
                                Log.e(TAG, "Stack trace: ${e.stackTrace.joinToString("\n")}")
                                // Continuar con la siguiente receta en lugar de fallar todo
                            }
                        }
                        
                        Log.d(TAG, "=== TOTAL RECETAS PROCESADAS: ${recipes.size} ===")
                        Result.success(recipes)
                    } catch (e: JSONException) {
                        Log.e(TAG, "ERROR parseando JSON: ${e.message}", e)
                        Log.e(TAG, "Respuesta recibida: ${response.take(500)}")
                        Result.failure(Exception("Error parseando respuesta JSON: ${e.message}"))
                    } catch (e: Exception) {
                        Log.e(TAG, "ERROR procesando respuesta: ${e.message}", e)
                        Result.failure(e)
                    }
                } else {
                    val errorResponse = BufferedReader(InputStreamReader(connection.errorStream)).readText()
                    Log.e(TAG, "Error obteniendo feed: $errorResponse")
                    Result.failure(Exception("Error obteniendo feed: $responseCode"))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando feed de recetas", e)
            Result.failure(e)
        }
    }
    
    /**
     * Buscar recetas por nombre desde MySQL
     */
    suspend fun searchRecipes(query: String): Result<List<RecipeFeedData>> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = URL("$BASE_URL/recipes/search?q=$encodedQuery")
            Log.d(TAG, "Buscando recetas con query: '$query'")
            Log.d(TAG, "URL: $url")
            
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Respuesta del servidor: $responseCode")
                
                val response = BufferedReader(InputStreamReader(
                    if (responseCode == HttpURLConnection.HTTP_OK) connection.inputStream else connection.errorStream
                )).readText()
                
                Log.d(TAG, "Respuesta completa (primeros 500 chars): ${response.take(500)}")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonResponse = JSONObject(response)
                    
                    if (jsonResponse.has("success") && jsonResponse.getBoolean("success")) {
                        val dataObj = jsonResponse.getJSONObject("data")
                        val recipesArray = dataObj.getJSONArray("recipes")
                        
                        val recipes = mutableListOf<RecipeFeedData>()
                        for (i in 0 until recipesArray.length()) {
                            try {
                                val recipeJson = recipesArray.getJSONObject(i)
                                
                                // Procesar imágenes similar a getRecipesFeed
                                val imagesList = mutableListOf<ByteArray>()
                                if (recipeJson.has("images") && !recipeJson.isNull("images")) {
                                    val imagesArray = recipeJson.getJSONArray("images")
                                    for (j in 0 until imagesArray.length()) {
                                        try {
                                            val base64Image = imagesArray.getString(j)
                                            val cleanedBase64 = base64Image.trim().replace("\n", "").replace("\r", "").replace(" ", "")
                                            if (cleanedBase64.isNotEmpty()) {
                                                val decodedImage = Base64.decode(cleanedBase64, Base64.DEFAULT)
                                                imagesList.add(decodedImage)
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error decodificando imagen $j: ${e.message}")
                                        }
                                    }
                                } else if (recipeJson.has("image_data") && !recipeJson.isNull("image_data")) {
                                    try {
                                        val base64Image = recipeJson.getString("image_data")
                                        val cleanedBase64 = base64Image.trim().replace("\n", "").replace("\r", "").replace(" ", "")
                                        if (cleanedBase64.isNotEmpty()) {
                                            val decodedImage = Base64.decode(cleanedBase64, Base64.DEFAULT)
                                            imagesList.add(decodedImage)
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error decodificando imagen_data: ${e.message}")
                                    }
                                }
                                
                                val authorId = recipeJson.optLong("author_id", recipeJson.optLong("authorId", -1))
                                val authorName = recipeJson.optString("author_name", recipeJson.optString("authorName", ""))
                                val createdAt = recipeJson.optLong("created_at", recipeJson.optLong("createdAt", 0))
                                
                                val authorAlias = recipeJson.optString("author_alias", recipeJson.optString("authorAlias", authorName))
                                val authorAvatar = recipeJson.optString("author_avatar", recipeJson.optString("authorAvatar", null))
                                    .takeIf { it.isNotBlank() }
                                
                                val recipe = RecipeFeedData(
                                    id = recipeJson.getLong("id"),
                                    title = recipeJson.getString("title"),
                                    description = recipeJson.optString("description", ""),
                                    authorId = authorId,
                                    authorName = authorName,
                                    authorAlias = authorAlias,
                                    authorAvatar = authorAvatar,
                                    cookingTime = recipeJson.optInt("cooking_time", recipeJson.optInt("cookingTime", 0)),
                                    servings = recipeJson.optInt("servings", 1),
                                    rating = recipeJson.optDouble("rating", 0.0).toFloat(),
                                    tags = null,
                                    isPublished = true,
                                    createdAt = createdAt,
                                    imageData = imagesList.firstOrNull(),
                                    images = imagesList,
                                    likes = recipeJson.optInt("likes", 0),
                                    dislikes = recipeJson.optInt("dislikes", 0),
                                    voteType = -1
                                )
                                recipes.add(recipe)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parseando receta $i: ${e.message}", e)
                            }
                        }
                        
                        Log.d(TAG, "✓ Recetas encontradas: ${recipes.size}")
                        Result.success(recipes)
                    } else {
                        val error = jsonResponse.optString("error", "Error desconocido")
                        Log.e(TAG, "Error en respuesta: $error")
                        Result.failure(Exception(error))
                    }
                } else {
                    val errorJson = try {
                        JSONObject(response)
                    } catch (e: Exception) {
                        JSONObject().apply { put("error", response) }
                    }
                    val error = errorJson.optString("error", "Error buscando recetas: $responseCode")
                    Log.e(TAG, "Error HTTP: $error")
                    Result.failure(Exception(error))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error buscando recetas: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Actualizar voto en la tabla recipe_votes de MySQL
     * voteType: 1 = me gusta, 0 = dislike, -1 = quitar voto (eliminar)
     */
    suspend fun syncVoteToMySQL(recipeId: Long, userId: Long, voteType: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Validar parámetros antes de continuar
            if (recipeId <= 0) {
                Log.e(TAG, "✗ ERROR: recipeId inválido: $recipeId")
                return@withContext Result.failure(Exception("Recipe ID inválido: $recipeId"))
            }
            
            if (userId <= 0) {
                Log.e(TAG, "✗ ERROR: userId inválido: $userId")
                return@withContext Result.failure(Exception("User ID inválido: $userId"))
            }
            
            Log.d(TAG, "=== syncVoteToMySQL START ===")
            Log.d(TAG, "Recipe ID: $recipeId")
            Log.d(TAG, "User ID: $userId")
            Log.d(TAG, "Vote Type: $voteType")
            
            val jsonObject = JSONObject().apply {
                put("recipeId", recipeId)
                put("userId", userId)
                put("voteType", voteType)
                put("createdAt", System.currentTimeMillis())
            }
            
            val requestBody = JSONObject().apply {
                put("vote", jsonObject)
            }
            
            Log.d(TAG, "JSON a enviar: ${requestBody.toString()}")
            
            // Hacer la petición HTTP
            val url = URL("$BASE_URL/votes")
            Log.d(TAG, "Conectando a: $url")
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.doInput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                Log.d(TAG, "Enviando voto a recipe_votes: recipe_id=$recipeId, user_id=$userId, vote_type=$voteType")
                
                // Enviar datos
                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(requestBody.toString())
                writer.flush()
                writer.close()
                
                // Leer respuesta
                val responseCode = connection.responseCode
                Log.d(TAG, "Respuesta del servidor: $responseCode")
                
                val reader = if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    BufferedReader(InputStreamReader(connection.inputStream))
                } else {
                    BufferedReader(InputStreamReader(connection.errorStream))
                }
                
                val response = reader.readText()
                reader.close()
                
                Log.d(TAG, "Respuesta completa: $response")
                
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    val jsonResponse = JSONObject(response)
                    val message = jsonResponse.optJSONObject("data")?.optString("message") 
                        ?: jsonResponse.optString("message")
                    
                    Log.d(TAG, "✓ Voto actualizado exitosamente: $message")
                    Result.success(message ?: "Voto actualizado exitosamente")
                } else {
                    val errorJson = try {
                        JSONObject(response)
                    } catch (e: Exception) {
                        JSONObject().apply { put("error", response) }
                    }
                    val errorMessage = errorJson.optString("error", "Error desconocido")
                    Log.e(TAG, "✗ Error actualizando voto (código $responseCode): $errorMessage")
                    Result.failure(Exception("Error del servidor ($responseCode): $errorMessage"))
                }
            } finally {
                connection.disconnect()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "✗ EXCEPCIÓN en syncVoteToMySQL: ${e.message}", e)
            Log.e(TAG, "Stack trace: ${e.stackTrace.joinToString("\n")}")
            Result.failure(e)
        }
    }
    
    /**
     * Actualizar voto en la tabla comment_likes de MySQL
     * voteType: 1 = me gusta, 0 = dislike, -1 = quitar voto (eliminar)
     */
    suspend fun syncCommentVoteToMySQL(commentId: Long, userId: Long, voteType: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Validar parámetros antes de continuar
            if (commentId <= 0) {
                Log.e(TAG, "✗ ERROR: commentId inválido: $commentId")
                return@withContext Result.failure(Exception("Comment ID inválido: $commentId"))
            }
            
            if (userId <= 0) {
                Log.e(TAG, "✗ ERROR: userId inválido: $userId")
                return@withContext Result.failure(Exception("User ID inválido: $userId"))
            }
            
            Log.d(TAG, "=== syncCommentVoteToMySQL START ===")
            Log.d(TAG, "Comment ID: $commentId")
            Log.d(TAG, "User ID: $userId")
            Log.d(TAG, "Vote Type: $voteType")
            
            val jsonObject = JSONObject().apply {
                put("commentId", commentId)
                put("userId", userId)
                put("voteType", voteType)
                put("createdAt", System.currentTimeMillis())
            }
            
            val requestBody = JSONObject().apply {
                put("vote", jsonObject)
            }
            
            Log.d(TAG, "JSON a enviar: ${requestBody.toString()}")
            
            // Hacer la petición HTTP
            val url = URL("$BASE_URL/comment-votes")
            Log.d(TAG, "Conectando a: $url")
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.doInput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                Log.d(TAG, "Enviando voto a comment_likes: comment_id=$commentId, user_id=$userId, vote_type=$voteType")
                
                // Enviar datos
                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(requestBody.toString())
                writer.flush()
                writer.close()
                
                // Leer respuesta
                val responseCode = connection.responseCode
                Log.d(TAG, "Respuesta del servidor: $responseCode")
                
                val reader = if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    BufferedReader(InputStreamReader(connection.inputStream))
                } else {
                    BufferedReader(InputStreamReader(connection.errorStream))
                }
                
                val response = reader.readText()
                reader.close()
                
                Log.d(TAG, "Respuesta completa: $response")
                
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    val jsonResponse = JSONObject(response)
                    val message = jsonResponse.optJSONObject("data")?.optString("message") 
                        ?: jsonResponse.optString("message")
                    
                    Log.d(TAG, "✓ Voto de comentario actualizado exitosamente: $message")
                    Result.success(message ?: "Voto actualizado exitosamente")
                } else {
                    val errorJson = try {
                        JSONObject(response)
                    } catch (e: Exception) {
                        JSONObject().apply { put("error", response) }
                    }
                    val errorMessage = errorJson.optString("error", "Error desconocido")
                    Log.e(TAG, "✗ Error actualizando voto de comentario (código $responseCode): $errorMessage")
                    Result.failure(Exception("Error del servidor ($responseCode): $errorMessage"))
                }
            } finally {
                connection.disconnect()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "✗ EXCEPCIÓN en syncCommentVoteToMySQL: ${e.message}", e)
            Log.e(TAG, "Stack trace: ${e.stackTrace.joinToString("\n")}")
            Result.failure(e)
        }
    }
    
    /**
     * Data class para conteos de votos de comentarios
     */
    data class CommentVotesCount(
        val likes: Int,
        val dislikes: Int
    )
    
    /**
     * Obtener conteos de likes y dislikes de un comentario
     */
    suspend fun getCommentVotesCount(commentId: Long): Result<CommentVotesCount> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/comments/$commentId/votes-count")
            Log.d(TAG, "Obteniendo conteos de votos del comentario $commentId: $url")
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Respuesta del servidor: $responseCode")
                
                val response = BufferedReader(InputStreamReader(
                    if (responseCode == HttpURLConnection.HTTP_OK) connection.inputStream else connection.errorStream
                )).readText()
                
                Log.d(TAG, "Respuesta completa de conteos de votos de comentario: $response")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonResponse = JSONObject(response)
                    val dataObj = jsonResponse.getJSONObject("data")
                    
                    val likes = dataObj.getInt("likes")
                    val dislikes = dataObj.getInt("dislikes")
                    
                    val votesCount = CommentVotesCount(likes = likes, dislikes = dislikes)
                    Log.d(TAG, "✓ Conteos obtenidos exitosamente - Likes: $likes, Dislikes: $dislikes")
                    Result.success(votesCount)
                } else {
                    val errorJson = try {
                        JSONObject(response)
                    } catch (e: Exception) {
                        JSONObject().apply { put("error", response) }
                    }
                    val error = errorJson.optString("error", "Error obteniendo conteos de votos de comentario: $responseCode")
                    Log.e(TAG, "Error obteniendo conteos de votos de comentario: $error")
                    Result.failure(Exception(error))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en getCommentVotesCount: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Crear una respuesta a un comentario
     */
    suspend fun createCommentReply(commentId: Long, userId: Long, replyText: String): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/comment-replies")
            Log.d(TAG, "Creando respuesta al comentario $commentId: $url")
            
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.doOutput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val jsonData = JSONObject().apply {
                    put("reply", JSONObject().apply {
                        put("commentId", commentId)
                        put("userId", userId)
                        put("replyText", replyText)
                        put("createdAt", System.currentTimeMillis())
                    })
                }
                
                Log.d(TAG, "Enviando datos: ${jsonData.toString()}")
                
                connection.outputStream.use { outputStream ->
                    outputStream.write(jsonData.toString().toByteArray(Charsets.UTF_8))
                }
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Respuesta del servidor al crear respuesta: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    val response = BufferedReader(InputStreamReader(connection.inputStream)).readText()
                    val jsonResponse = JSONObject(response)
                    
                    val dataObj = jsonResponse.getJSONObject("data")
                    val replyId = dataObj.getLong("replyId")
                    Log.d(TAG, "✓ Respuesta creada exitosamente con ID: $replyId")
                    Result.success(replyId)
                } else {
                    val errorResponse = BufferedReader(InputStreamReader(connection.errorStream)).readText()
                    val errorJson = try {
                        JSONObject(errorResponse)
                    } catch (e: Exception) {
                        JSONObject().apply { put("error", errorResponse) }
                    }
                    val error = errorJson.optString("error", "Error creando respuesta: $responseCode")
                    Log.e(TAG, "Error HTTP: $error")
                    Result.failure(Exception(error))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en createCommentReply: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Obtener todas las respuestas de un comentario
     */
    suspend fun getRepliesByCommentId(commentId: Long): Result<List<ReplyData>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/comments/$commentId/replies")
            Log.d(TAG, "Obteniendo respuestas del comentario $commentId: $url")
            
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Respuesta del servidor: $responseCode")
                
                val response = BufferedReader(InputStreamReader(
                    if (responseCode == HttpURLConnection.HTTP_OK) connection.inputStream else connection.errorStream
                )).readText()
                
                Log.d(TAG, "Respuesta completa: $response")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonResponse = JSONObject(response)
                    val dataObj = jsonResponse.getJSONObject("data")
                    val repliesArray = dataObj.getJSONArray("replies")
                    
                    Log.d(TAG, "Total respuestas en array: ${repliesArray.length()}")
                    
                    val replies = mutableListOf<ReplyData>()
                    for (i in 0 until repliesArray.length()) {
                        try {
                            val replyJson = repliesArray.getJSONObject(i)
                            Log.d(TAG, "Parseando respuesta ${i + 1}: ${replyJson.toString().take(200)}")
                            
                            val reply = ReplyData(
                                id = replyJson.getLong("id"),
                                commentId = replyJson.getLong("comment_id"),
                                userId = replyJson.getLong("user_id"),
                                replyText = replyJson.getString("reply_text"),
                                createdAt = replyJson.getLong("created_at"),
                                userName = replyJson.optString("name", "Usuario desconocido"),
                                userLastName = replyJson.optString("last_name", ""),
                                userEmail = replyJson.optString("email", ""),
                                userAlias = replyJson.optString("alias", "")
                            )
                            replies.add(reply)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parseando respuesta ${i + 1}: ${e.message}", e)
                        }
                    }
                    
                    Log.d(TAG, "✓ Respuestas obtenidas exitosamente: ${replies.size}")
                    Result.success(replies)
                } else {
                    val errorJson = try {
                        JSONObject(response)
                    } catch (e: Exception) {
                        JSONObject().apply { put("error", response) }
                    }
                    val error = errorJson.optString("error", "Error obteniendo respuestas: $responseCode")
                    Log.e(TAG, "Error obteniendo respuestas: $error")
                    Result.failure(Exception(error))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en getRepliesByCommentId: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Sincronizar voto de respuesta (reply) a MySQL
     * voteType: 1 = me gusta, 0 = dislike, -1 = quitar voto (eliminar)
     */
    suspend fun syncReplyVoteToMySQL(replyId: Long, userId: Long, voteType: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (replyId <= 0) {
                Log.e(TAG, "✗ ERROR: replyId inválido: $replyId")
                return@withContext Result.failure(Exception("Reply ID inválido: $replyId"))
            }
            if (userId <= 0) {
                Log.e(TAG, "✗ ERROR: userId inválido: $userId")
                return@withContext Result.failure(Exception("User ID inválido: $userId"))
            }
            Log.d(TAG, "=== syncReplyVoteToMySQL START ===")
            Log.d(TAG, "Reply ID: $replyId")
            Log.d(TAG, "User ID: $userId")
            Log.d(TAG, "Vote Type: $voteType")
            val jsonObject = JSONObject().apply {
                put("replyId", replyId)
                put("userId", userId)
                put("voteType", voteType)
                put("createdAt", System.currentTimeMillis())
            }
            val requestBody = JSONObject().apply {
                put("vote", jsonObject)
            }
            Log.d(TAG, "JSON a enviar: ${requestBody.toString()}")
            val url = URL("$BASE_URL/reply-votes")
            Log.d(TAG, "Conectando a: $url")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.doInput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                Log.d(TAG, "Enviando voto a reply_likes: reply_id=$replyId, user_id=$userId, vote_type=$voteType")
                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(requestBody.toString())
                writer.flush()
                writer.close()
                val responseCode = connection.responseCode
                Log.d(TAG, "Respuesta del servidor: $responseCode")
                val reader = if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    BufferedReader(InputStreamReader(connection.inputStream))
                } else {
                    BufferedReader(InputStreamReader(connection.errorStream))
                }
                val response = reader.readText()
                reader.close()
                Log.d(TAG, "Respuesta completa: $response")
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    val jsonResponse = JSONObject(response)
                    val message = jsonResponse.optJSONObject("data")?.optString("message")
                        ?: jsonResponse.optString("message")
                    Log.d(TAG, "✓ Voto de reply actualizado exitosamente: $message")
                    Result.success(message ?: "Voto actualizado exitosamente")
                } else {
                    val errorJson = try {
                        JSONObject(response)
                    } catch (e: Exception) {
                        JSONObject().apply { put("error", response) }
                    }
                    val errorMessage = errorJson.optString("error", "Error desconocido")
                    Log.e(TAG, "✗ Error actualizando voto de reply (código $responseCode): $errorMessage")
                    Result.failure(Exception("Error del servidor ($responseCode): $errorMessage"))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ EXCEPCIÓN en syncReplyVoteToMySQL: ${e.message}", e)
            Log.e(TAG, "Stack trace: ${e.stackTrace.joinToString("\n")}")
            Result.failure(e)
        }
    }
    
    /**
     * Data class para conteos de votos de respuestas
     */
    data class ReplyVotesCount(
        val likes: Int,
        val dislikes: Int
    )
    
    /**
     * Obtener conteos de likes y dislikes de una respuesta
     */
    suspend fun getReplyVotesCount(replyId: Long): Result<ReplyVotesCount> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/replies/$replyId/votes-count")
            Log.d(TAG, "Obteniendo conteos de votos de la respuesta $replyId: $url")
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Respuesta del servidor: $responseCode")
                
                val response = BufferedReader(InputStreamReader(
                    if (responseCode == HttpURLConnection.HTTP_OK) connection.inputStream else connection.errorStream
                )).readText()
                
                Log.d(TAG, "Respuesta completa de conteos de votos de reply: $response")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonResponse = JSONObject(response)
                    val dataObj = jsonResponse.getJSONObject("data")
                    val likes = dataObj.getInt("likes")
                    val dislikes = dataObj.getInt("dislikes")
                    
                    Log.d(TAG, "✓ Conteos obtenidos: likes=$likes, dislikes=$dislikes")
                    Result.success(ReplyVotesCount(likes, dislikes))
                } else {
                    val errorJson = try {
                        JSONObject(response)
                    } catch (e: Exception) {
                        JSONObject().apply { put("error", response) }
                    }
                    val errorMessage = errorJson.optString("error", "Error desconocido")
                    Log.e(TAG, "✗ Error obteniendo conteos de votos de reply (código $responseCode): $errorMessage")
                    Result.failure(Exception("Error del servidor ($responseCode): $errorMessage"))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en getReplyVotesCount: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Truncar todas las tablas de MySQL
     */
    suspend fun truncateAllMySQLTables(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/truncate-all")
            Log.d(TAG, "Truncando todas las tablas de MySQL: $url")
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Código de respuesta: $responseCode")
                
                val response = if (responseCode == HttpURLConnection.HTTP_OK || responseCode == 207) {
                    BufferedReader(InputStreamReader(connection.inputStream)).readText()
                } else {
                    val errorStream = connection.errorStream
                    if (errorStream != null) {
                        BufferedReader(InputStreamReader(errorStream)).readText()
                    } else {
                        "Sin mensaje de error"
                    }
                }
                
                Log.d(TAG, "Respuesta completa: $response")
                
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == 207) {
                    try {
                        val jsonResponse = JSONObject(response)
                        val message = jsonResponse.optString("message", "Tablas truncadas exitosamente")
                        val tablesCount = jsonResponse.optInt("tablesTruncated", 0)
                        Log.d(TAG, "✓ Tablas truncadas: $tablesCount - $message")
                        Result.success("$message ($tablesCount tablas)")
                    } catch (e: JSONException) {
                        Log.e(TAG, "Error parseando JSON: ${e.message}", e)
                        Result.success("Tablas truncadas (respuesta: $response)")
                    }
                } else {
                    val errorJson = try {
                        JSONObject(response)
                    } catch (e: Exception) {
                        JSONObject().apply { put("error", response) }
                    }
                    val errorMessage = errorJson.optString("error", "Error desconocido")
                    Log.e(TAG, "Error truncando tablas (código $responseCode): $errorMessage")
                    Result.failure(Exception("Error del servidor ($responseCode): $errorMessage"))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error truncando tablas de MySQL", e)
            Result.failure(e)
        }
    }
    
    /**
     * Clase de datos para representar un usuario
     */
    data class UserData(
        val id: Long,
        val name: String,
        val lastName: String,
        val email: String,
        val phone: String,
        val address: String,
        val alias: String,
        val avatarPath: String?,
        val createdAt: Long,
        val updatedAt: Long
    )
    
    /**
     * Clase de datos para representar los detalles completos de una receta
     */
    data class RecipeDetailData(
        val id: Long,
        val title: String,
        val description: String,
        val ingredients: String,
        val steps: String,
        val authorId: Long,
        val authorName: String,
        val cookingTime: Int,
        val servings: Int,
        val rating: Float,
        val isPublished: Boolean,
        val createdAt: Long,
        val updatedAt: Long,
        val images: List<ByteArray>
    )
    
    /**
     * Clase de datos para representar una receta del feed
     */
    data class RecipeFeedData(
        val id: Long,
        val title: String,
        val description: String,
        val authorId: Long,
        val authorName: String,
        val authorAlias: String,
        val authorAvatar: String? = null,
        val cookingTime: Int,
        val servings: Int,
        val rating: Float,
        val tags: String?,
        val isPublished: Boolean,
        val createdAt: Long,
        val imageData: ByteArray?, // Primera imagen para compatibilidad
        val images: List<ByteArray> = emptyList(), // Todas las imágenes
        val likes: Int = 0,
        val dislikes: Int = 0,
        val voteType: Int = -1 // -1 = sin voto, 0 = dislike, 1 = like
    )
    
    /**
     * Data class para recetas publicadas de un usuario
     */
    data class UserRecipeData(
        val id: Long,
        val title: String,
        val description: String?,
        val ingredients: String?,
        val steps: String?,
        val authorId: Long,
        val authorName: String,
        val tags: String?,
        val cookingTime: Int,
        val servings: Int,
        val rating: Float,
        val isPublished: Boolean,
        val createdAt: Long,
        val updatedAt: Long?,
        val images: List<ByteArray> = emptyList(),
        val imageData: ByteArray? = null
    )
    
    /**
     * Data class para respuestas a comentarios
     */
    data class ReplyData(
        val id: Long,
        val commentId: Long,
        val userId: Long,
        val replyText: String,
        val createdAt: Long,
        val userName: String,
        val userLastName: String,
        val userEmail: String,
        val userAlias: String,
        val likes: Int = 0,
        val dislikes: Int = 0,
        val voteType: Int = -1 // -1 = sin voto, 0 = dislike, 1 = like
    ) {
        fun getFullName(): String = "$userName $userLastName"
    }
    
    /**
     * Data class para comentarios
     */
    data class CommentData(
        val id: Long,
        val recipeId: Long,
        val userId: Long,
        val commentText: String,
        val createdAt: Long,
        val userName: String,
        val userLastName: String,
        val userEmail: String,
        val userAlias: String,
        val likes: Int = 0,
        val dislikes: Int = 0,
        val voteType: Int = -1 // -1 = sin voto, 0 = dislike, 1 = like
    ) {
        fun getFullName(): String = "$userName $userLastName"
    }
    
    /**
     * Data class para conteos de votos
     */
    data class VotesCount(
        val likes: Int,
        val dislikes: Int
    )
    
    /**
     * Crear un comentario en una receta
     */
    suspend fun createComment(recipeId: Long, userId: Long, commentText: String): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/recipes/$recipeId/comments")
            Log.d(TAG, "Creando comentario en receta $recipeId: $url")
            
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.doOutput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val jsonData = JSONObject().apply {
                    put("user_id", userId)
                    put("comment_text", commentText)
                }
                
                connection.outputStream.use { outputStream ->
                    outputStream.write(jsonData.toString().toByteArray(Charsets.UTF_8))
                }
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Respuesta del servidor al crear comentario: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(connection.inputStream)).readText()
                    val jsonResponse = JSONObject(response)
                    
                    if (jsonResponse.has("success") && jsonResponse.getBoolean("success")) {
                        val dataObj = jsonResponse.getJSONObject("data")
                        val commentId = dataObj.getLong("comment_id")
                        Log.d(TAG, "✓ Comentario creado exitosamente con ID: $commentId")
                        Result.success(commentId)
                    } else {
                        val error = jsonResponse.optString("error", "Error desconocido")
                        Log.e(TAG, "Error creando comentario: $error")
                        Result.failure(Exception(error))
                    }
                } else {
                    val errorResponse = BufferedReader(InputStreamReader(connection.errorStream)).readText()
                    val errorJson = try {
                        JSONObject(errorResponse)
                    } catch (e: Exception) {
                        JSONObject().apply { put("error", errorResponse) }
                    }
                    val error = errorJson.optString("error", "Error creando comentario: $responseCode")
                    Log.e(TAG, "Error HTTP: $error")
                    Result.failure(Exception(error))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en createComment: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Obtener todos los comentarios de una receta
     */
    suspend fun getCommentsByRecipeId(recipeId: Long): Result<List<CommentData>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/recipes/$recipeId/comments")
            Log.d(TAG, "Obteniendo comentarios de receta $recipeId: $url")
            
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Respuesta del servidor: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(connection.inputStream)).readText()
                    Log.d(TAG, "Respuesta completa de comentarios (primeros 500 chars): ${response.take(500)}")
                    
                    val jsonResponse = JSONObject(response)
                    
                    if (jsonResponse.has("success") && jsonResponse.getBoolean("success")) {
                        val dataObj = jsonResponse.getJSONObject("data")
                        val commentsArray = dataObj.getJSONArray("comments")
                        
                        Log.d(TAG, "Total comentarios en array: ${commentsArray.length()}")
                        
                        val comments = mutableListOf<CommentData>()
                        for (i in 0 until commentsArray.length()) {
                            try {
                                val commentJson = commentsArray.getJSONObject(i)
                                Log.d(TAG, "Parseando comentario ${i + 1}: ${commentJson.toString().take(200)}")
                                
                                val comment = CommentData(
                                    id = commentJson.getLong("id"),
                                    recipeId = commentJson.getLong("recipe_id"),
                                    userId = commentJson.getLong("user_id"),
                                    commentText = commentJson.getString("comment_text"),
                                    createdAt = commentJson.getLong("created_at"),
                                    userName = commentJson.optString("name", "Usuario desconocido"),
                                    userLastName = commentJson.optString("last_name", ""),
                                    userEmail = commentJson.optString("email", ""),
                                    userAlias = commentJson.optString("alias", "")
                                )
                                comments.add(comment)
                                Log.d(TAG, "✓ Comentario ${i + 1} parseado exitosamente")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parseando comentario ${i + 1}: ${e.message}", e)
                                // Continuar con el siguiente comentario en lugar de fallar todo
                            }
                        }
                        
                        Log.d(TAG, "✓ Comentarios obtenidos exitosamente: ${comments.size}")
                        Result.success(comments)
                    } else {
                        val error = jsonResponse.optString("error", "Error desconocido")
                        Log.e(TAG, "Error obteniendo comentarios: $error")
                        Log.e(TAG, "Respuesta completa: $response")
                        Result.failure(Exception(error))
                    }
                } else {
                    val errorResponse = BufferedReader(InputStreamReader(connection.errorStream)).readText()
                    Log.e(TAG, "Error HTTP $responseCode al obtener comentarios: $errorResponse")
                    val errorJson = try {
                        JSONObject(errorResponse)
                    } catch (e: Exception) {
                        JSONObject().apply { put("error", errorResponse) }
                    }
                    val error = errorJson.optString("error", "Error obteniendo comentarios: $responseCode")
                    Log.e(TAG, "Error HTTP: $error")
                    Result.failure(Exception(error))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en getCommentsByRecipeId: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Obtener conteos de likes y dislikes de una receta
     */
    suspend fun getRecipeVotesCount(recipeId: Long): Result<VotesCount> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/recipes/$recipeId/votes-count")
            Log.d(TAG, "Obteniendo conteos de votos de receta $recipeId: $url")
            
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Respuesta del servidor: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(connection.inputStream)).readText()
                    Log.d(TAG, "Respuesta completa de conteos de votos: $response")
                    
                    val jsonResponse = JSONObject(response)
                    
                    if (jsonResponse.has("success") && jsonResponse.getBoolean("success")) {
                        val dataObj = jsonResponse.getJSONObject("data")
                        val likes = dataObj.getInt("likes")
                        val dislikes = dataObj.getInt("dislikes")
                        
                        val votesCount = VotesCount(likes = likes, dislikes = dislikes)
                        Log.d(TAG, "✓ Conteos obtenidos exitosamente - Likes: $likes, Dislikes: $dislikes")
                        Result.success(votesCount)
                    } else {
                        val error = jsonResponse.optString("error", "Error desconocido")
                        Log.e(TAG, "Error obteniendo conteos de votos: $error")
                        Result.failure(Exception(error))
                    }
                } else {
                    val errorResponse = BufferedReader(InputStreamReader(connection.errorStream)).readText()
                    Log.e(TAG, "Error HTTP $responseCode al obtener conteos de votos: $errorResponse")
                    val errorJson = try {
                        JSONObject(errorResponse)
                    } catch (e: Exception) {
                        JSONObject().apply { put("error", errorResponse) }
                    }
                    val error = errorJson.optString("error", "Error obteniendo conteos de votos: $responseCode")
                    Log.e(TAG, "Error HTTP: $error")
                    Result.failure(Exception(error))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en getRecipeVotesCount: ${e.message}", e)
            Result.failure(e)
        }
    }
}

