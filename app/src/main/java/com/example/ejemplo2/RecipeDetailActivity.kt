package com.example.ejemplo2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ejemplo2.adapter.CommentsAdapter
import com.example.ejemplo2.data.api.ApiService
import com.example.ejemplo2.data.entity.Recipe
import com.example.ejemplo2.data.entity.RecipeImage
import com.example.ejemplo2.data.repository.RecipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.*

class RecipeDetailActivity : AppCompatActivity() {
    
    private lateinit var recipeRepository: RecipeRepository
    private lateinit var apiService: ApiService
    private lateinit var commentsAdapter: CommentsAdapter
    private var recipeId: Long = -1
    private var currentUserId: Long = -1
    private var currentUserEmail: String = ""
    private var currentUserMySQLId: Long = -1
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_detail)
        
        // Inicializar repositorio y servicio API
        recipeRepository = RecipeRepository(this)
        apiService = ApiService(this)
        
        // Obtener ID de la receta desde el intent
        recipeId = intent.getLongExtra("recipe_id", -1)
        
        // Restaurar datos desde savedInstanceState si existe (pero dar prioridad al intent)
        if (savedInstanceState != null) {
            val savedUserId = savedInstanceState.getLong("saved_user_id", -1)
            val savedEmail = savedInstanceState.getString("saved_user_email") ?: ""
            
            // Solo usar savedInstanceState si el intent no tiene datos
            val userIdFromIntent = intent.getLongExtra("user_id", -1)
            if (userIdFromIntent == -1L && savedUserId != -1L) {
                currentUserId = savedUserId
            }
            
            val emailFromIntent = intent.getStringExtra("user_email") ?: ""
            if (emailFromIntent.isEmpty() && savedEmail.isNotEmpty()) {
                currentUserEmail = savedEmail
                Log.d("RecipeDetailActivity", "Email restaurado desde savedInstanceState: '$currentUserEmail'")
            }
        }
        
        // PRIORIDAD 1: Obtener datos del intent (siempre tienen prioridad)
        val userIdFromIntent = intent.getLongExtra("user_id", -1)
        if (userIdFromIntent != -1L) {
            currentUserId = userIdFromIntent
        }
        
        val emailFromIntent = intent.getStringExtra("user_email") ?: ""
        if (emailFromIntent.isNotEmpty()) {
            currentUserEmail = emailFromIntent
            Log.d("RecipeDetailActivity", "Email obtenido del intent: '$currentUserEmail'")
        }
        
        Log.d("RecipeDetailActivity", "=== DATOS DE USUARIO RECIBIDOS ===")
        Log.d("RecipeDetailActivity", "Recipe ID: $recipeId")
        Log.d("RecipeDetailActivity", "User ID: $currentUserId")
        Log.d("RecipeDetailActivity", "User Email: '$currentUserEmail'")
        Log.d("RecipeDetailActivity", "Email del intent: '$emailFromIntent'")
        
        if (recipeId == -1L) {
            finish()
            return
        }
        
        // Configurar navbar
        setupNavbar()
        
        // Configurar RecyclerView para comentarios
        setupCommentsRecyclerView()
        
        // Cargar datos de la receta
        loadRecipeDetails()
    }
    
    private fun setupNavbar() {
        val homeButton = findViewById<View>(R.id.homeButton)
        val createButton = findViewById<View>(R.id.createButton)
        val profileButton = findViewById<View>(R.id.profileButton)
        
        homeButton.setOnClickListener {
            // SIEMPRE pasar el correo, incluso si está vacío (intentar obtener del intent original)
            val emailToPass = currentUserEmail.ifEmpty {
                this.intent.getStringExtra("user_email") ?: ""
            }
            
            Log.d("RecipeDetailActivity", "Navegando a MainActivity (Dashboard) con email: '$emailToPass'")
            val intent = android.content.Intent(this, MainActivity::class.java)
            intent.putExtra("user_id", currentUserId)
            intent.putExtra("user_email", emailToPass) // SIEMPRE pasar el correo
            intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }
        
        createButton.setOnClickListener {
            // SIEMPRE pasar el correo
            val emailToPass = currentUserEmail.ifEmpty {
                this.intent.getStringExtra("user_email") ?: ""
            }
            
            Log.d("RecipeDetailActivity", "Navegando a CreateRecipeActivity con email: '$emailToPass'")
            val intent = android.content.Intent(this, CreateRecipeActivity::class.java)
            intent.putExtra("user_id", currentUserId)
            intent.putExtra("user_email", emailToPass) // SIEMPRE pasar el correo
            startActivity(intent)
        }
        
        profileButton.setOnClickListener {
            // SIEMPRE pasar el correo
            val emailToPass = currentUserEmail.ifEmpty {
                this.intent.getStringExtra("user_email") ?: ""
            }
            
            Log.d("RecipeDetailActivity", "Navegando a ProfileActivity con email: '$emailToPass'")
            val intent = android.content.Intent(this, ProfileActivity::class.java)
            intent.putExtra("user_id", currentUserId)
            intent.putExtra("user_email", emailToPass) // SIEMPRE pasar el correo
            intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }
    }
    
    private fun loadRecipeDetails() {
        lifecycleScope.launch {
            try {
                Log.d("RecipeDetailActivity", "=== INICIANDO CARGA DE RECETA ===")
                Log.d("RecipeDetailActivity", "ID de receta: $recipeId")
                
                // Intentar cargar la receta con reintentos automáticos
                var result = apiService.getRecipeById(recipeId)
                var attempts = 1
                val maxAttempts = 3
                
                // Si falla, reintentar hasta 2 veces más
                while (!result.isSuccess && attempts < maxAttempts) {
                    Log.w("RecipeDetailActivity", "Intento $attempts falló, reintentando en 2 segundos...")
                    val error = result.exceptionOrNull()
                    Log.e("RecipeDetailActivity", "Error en intento $attempts: ${error?.message}")
                    
                    kotlinx.coroutines.delay(2000) // Esperar 2 segundos antes de reintentar
                    attempts++
                    result = apiService.getRecipeById(recipeId)
                }
                
                Log.d("RecipeDetailActivity", "Resultado obtenido después de $attempts intento(s): ${result.isSuccess}")
                
                if (result.isSuccess) {
                    val recipeData = result.getOrNull()
                    if (recipeData != null) {
                        Log.d("RecipeDetailActivity", "✓ Receta obtenida exitosamente desde MySQL")
                        Log.d("RecipeDetailActivity", "  Título: ${recipeData.title}")
                        Log.d("RecipeDetailActivity", "  Descripción: ${recipeData.description.take(50)}...")
                        Log.d("RecipeDetailActivity", "  Ingredientes: ${recipeData.ingredients.take(50)}...")
                        Log.d("RecipeDetailActivity", "  Autor: ${recipeData.authorName}")
                        Log.d("RecipeDetailActivity", "  Imágenes: ${recipeData.images.size}")
                        Log.d("RecipeDetailActivity", "  Tiempo de cocción: ${recipeData.cookingTime} min")
                        Log.d("RecipeDetailActivity", "  Porciones: ${recipeData.servings}")
                        
                        // Mostrar datos en la UI
                        displayRecipeData(recipeData)
                        
                        // Cargar comentarios de la receta
                        loadComments()
                        
                        // Cargar conteos de likes/dislikes
                        loadVotesCount()
                        
                        // Configurar formulario de comentarios (solo si el usuario está logueado)
                        setupCommentForm()
                    } else {
                        Log.e("RecipeDetailActivity", "Receta no encontrada (result.getOrNull() es null)")
                        android.widget.Toast.makeText(this@RecipeDetailActivity, "Receta no encontrada", android.widget.Toast.LENGTH_LONG).show()
                        finish()
                    }
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("RecipeDetailActivity", "Error cargando receta desde MySQL después de $attempts intentos")
                    Log.e("RecipeDetailActivity", "Mensaje: ${error?.message}")
                    Log.e("RecipeDetailActivity", "Stack trace: ${error?.stackTrace?.joinToString("\n")}")
                    
                    android.widget.Toast.makeText(
                        this@RecipeDetailActivity, 
                        "Error cargando receta después de $attempts intentos: ${error?.message ?: "Error desconocido"}", 
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    
                    // No cerrar la actividad automáticamente, mostrar error
                    // finish()
                }
            } catch (e: Exception) {
                Log.e("RecipeDetailActivity", "EXCEPCIÓN en loadRecipeDetails")
                Log.e("RecipeDetailActivity", "Mensaje: ${e.message}")
                Log.e("RecipeDetailActivity", "Stack trace: ${e.stackTrace.joinToString("\n")}")
                
                android.widget.Toast.makeText(
                    this@RecipeDetailActivity, 
                    "Error: ${e.message}", 
                    android.widget.Toast.LENGTH_LONG
                ).show()
                
                // No cerrar automáticamente
                // finish()
            }
        }
    }
    
    private fun displayRecipeData(recipe: com.example.ejemplo2.data.api.ApiService.RecipeDetailData) {
        // Título
        findViewById<TextView>(R.id.recipeTitle).text = recipe.title
        
        // Descripción
        findViewById<TextView>(R.id.recipeDescription).text = recipe.description
        
        // Autor - Obtener el nombre real desde MySQL usando el authorId
        loadAuthorFromMySQL(recipe.authorId)
        
        // Estado
        val statusText = findViewById<TextView>(R.id.recipeStatus)
        if (recipe.isPublished) {
            statusText.text = "Publicada"
            statusText.setBackgroundColor(getColor(R.color.vibrant_orange))
        } else {
            statusText.text = "Borrador"
            statusText.setBackgroundColor(getColor(android.R.color.darker_gray))
        }
        
        // Fecha
        val dateFormat = SimpleDateFormat("dd/MM/yyyy 'a las' HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("America/Mexico_City")
        }
        val dateText = dateFormat.format(Date(recipe.createdAt))
        findViewById<TextView>(R.id.recipeDate).text = dateText
        
        // Ingredientes
        findViewById<TextView>(R.id.recipeIngredients).text = formatIngredients(recipe.ingredients)
        
        // Pasos
        findViewById<TextView>(R.id.recipeSteps).text = recipe.steps
        
        // Tiempo de cocción
        val cookingTimeText = if (recipe.cookingTime > 0) {
            "${recipe.cookingTime} min"
        } else {
            "No especificado"
        }
        findViewById<TextView>(R.id.recipeCookingTime).text = cookingTimeText
        
        // Porciones
        val servingsText = if (recipe.servings > 0) {
            "${recipe.servings} ${if (recipe.servings == 1) "porción" else "porciones"}"
        } else {
            "No especificado"
        }
        findViewById<TextView>(R.id.recipeServings).text = servingsText
        
        // Mostrar imágenes de la receta desde MySQL
        displayRecipeImagesFromMySQL(recipe.images)
    }
    
    private fun displayRecipeImagesFromMySQL(images: List<ByteArray>) {
        val imagesContainer = findViewById<LinearLayout>(R.id.imagesContainer)
        val imageIndicators = findViewById<LinearLayout>(R.id.imageIndicators)
        
        // Limpiar contenedores
        imagesContainer.removeAllViews()
        imageIndicators.removeAllViews()
        
        if (images.isEmpty()) {
            Log.d("RecipeDetailActivity", "No hay imágenes para mostrar")
            // Mostrar imagen por defecto
            val defaultImageView = ImageView(this)
            defaultImageView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            defaultImageView.scaleType = ImageView.ScaleType.CENTER_CROP
            defaultImageView.setImageResource(R.drawable.ic_no_recipes)
            imagesContainer.addView(defaultImageView)
            return
        }
        
        Log.d("RecipeDetailActivity", "Mostrando ${images.size} imágenes desde MySQL")
        
        // Mostrar todas las imágenes
        images.forEachIndexed { index, imageData ->
            try {
                val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                if (bitmap != null) {
                    val imageView = ImageView(this)
                    val layoutParams = LinearLayout.LayoutParams(
                        resources.displayMetrics.widthPixels, // Ancho de pantalla
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )
                    imageView.layoutParams = layoutParams
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                    imageView.setImageBitmap(bitmap)
                    
                    // Agregar margen entre imágenes (excepto la última)
                    if (index < images.size - 1) {
                        layoutParams.setMargins(0, 0, 8, 0)
                    }
                    
                    imagesContainer.addView(imageView)
                    Log.d("RecipeDetailActivity", "Imagen ${index + 1} cargada exitosamente")
                } else {
                    Log.w("RecipeDetailActivity", "Error convirtiendo ByteArray a Bitmap para imagen ${index + 1}")
                }
            } catch (e: Exception) {
                Log.e("RecipeDetailActivity", "Error procesando imagen ${index + 1}", e)
            }
        }
        
        // Crear indicadores de página si hay más de una imagen
        if (images.size > 1) {
            createImageIndicators(images.size)
            imageIndicators.visibility = View.VISIBLE
            setupScrollListener(images.size)
        } else {
            imageIndicators.visibility = View.GONE
        }
    }
    
    private fun loadAuthorFromMySQL(authorId: Long) {
        lifecycleScope.launch {
            try {
                val result = apiService.getUserById(authorId)
                if (result.isSuccess) {
                    val user = result.getOrNull()
                    val authorName = "${user?.name} ${user?.lastName}"
                    findViewById<TextView>(R.id.recipeAuthor).text = authorName
                    Log.d("RecipeDetailActivity", "Usuario obtenido desde MySQL: $authorName")
                } else {
                    findViewById<TextView>(R.id.recipeAuthor).text = "Autor desconocido"
                    Log.e("RecipeDetailActivity", "Error obteniendo usuario: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                findViewById<TextView>(R.id.recipeAuthor).text = "Autor desconocido"
                Log.e("RecipeDetailActivity", "Error consultando usuario desde MySQL: ${e.message}", e)
            }
        }
    }
    
    private fun formatIngredients(ingredients: String): String {
        // Dividir por comas y formatear como lista
        val ingredientList = ingredients.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        
        return if (ingredientList.isEmpty()) {
            "No se especificaron ingredientes"
        } else {
            ingredientList.mapIndexed { index, ingredient ->
                "${index + 1}. $ingredient"
            }.joinToString("\n")
        }
    }
    
    private fun loadRecipeImages() {
        lifecycleScope.launch {
            try {
                val imagesFlow = recipeRepository.getRecipeImages(recipeId)
                imagesFlow.collect { images ->
                    displayRecipeImages(images)
                    return@collect // Salir después de la primera emisión
                }
            } catch (e: Exception) {
                Log.e("RecipeDetailActivity", "Error cargando imágenes: ${e.message}", e)
                // Mostrar imagen por defecto si hay error
                val imagesContainer = findViewById<LinearLayout>(R.id.imagesContainer)
                imagesContainer.removeAllViews()
                val defaultImageView = ImageView(this@RecipeDetailActivity)
                defaultImageView.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                defaultImageView.scaleType = ImageView.ScaleType.CENTER_CROP
                defaultImageView.setImageResource(R.drawable.ic_no_recipes)
                imagesContainer.addView(defaultImageView)
            }
        }
    }
    
    private fun displayRecipeImages(images: List<RecipeImage>) {
        val imagesContainer = findViewById<LinearLayout>(R.id.imagesContainer)
        val imageIndicators = findViewById<LinearLayout>(R.id.imageIndicators)
        
        // Limpiar contenedores
        imagesContainer.removeAllViews()
        imageIndicators.removeAllViews()
        
        if (images.isEmpty()) {
            // No hay imágenes, mostrar imagen por defecto
            val defaultImageView = ImageView(this@RecipeDetailActivity)
            defaultImageView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            defaultImageView.scaleType = ImageView.ScaleType.CENTER_CROP
            defaultImageView.setImageResource(R.drawable.ic_no_recipes)
            imagesContainer.addView(defaultImageView)
            imageIndicators.visibility = View.GONE
            return
        }
        
        // Mostrar todas las imágenes en el carrusel
        lifecycleScope.launch {
            for ((index, image) in images.withIndex()) {
                val bitmap = byteArrayToBitmap(image.imageData)
                
                if (bitmap != null) {
                    val imageView = ImageView(this@RecipeDetailActivity)
                    val layoutParams = LinearLayout.LayoutParams(
                        resources.displayMetrics.widthPixels, // Ancho de pantalla
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )
                    imageView.layoutParams = layoutParams
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                    imageView.setImageBitmap(bitmap)
                    
                    // Agregar margen entre imágenes (excepto la última)
                    if (index < images.size - 1) {
                        layoutParams.setMargins(0, 0, 8, 0)
                    }
                    
                    imagesContainer.addView(imageView)
                    Log.d("RecipeDetailActivity", "Imagen ${index + 1} cargada exitosamente")
                } else {
                    Log.w("RecipeDetailActivity", "Error convirtiendo ByteArray a Bitmap para imagen ${index + 1}")
                }
            }
            
            // Crear indicadores de página si hay más de una imagen
            if (images.size > 1) {
                createImageIndicators(images.size)
                imageIndicators.visibility = View.VISIBLE
                setupScrollListener(images.size)
            } else {
                imageIndicators.visibility = View.GONE
            }
        }
    }
    
    private fun createImageIndicators(totalImages: Int) {
        val imageIndicators = findViewById<LinearLayout>(R.id.imageIndicators)
        
        for (i in 0 until totalImages) {
            val indicator = View(this@RecipeDetailActivity)
            val size = resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 4 // 12dp
            val layoutParams = LinearLayout.LayoutParams(size, size)
            
            // Margen entre indicadores
            if (i > 0) {
                layoutParams.setMargins(8, 0, 0, 0)
            }
            
            indicator.layoutParams = layoutParams
            indicator.background = ContextCompat.getDrawable(this, R.drawable.indicator_dot_inactive)
            imageIndicators.addView(indicator)
        }
        
        // Marcar el primer indicador como activo
        if (totalImages > 0) {
            val firstIndicator = imageIndicators.getChildAt(0) as View
            firstIndicator.background = ContextCompat.getDrawable(this, R.drawable.indicator_dot_active)
        }
    }
    
    private fun setupScrollListener(totalImages: Int) {
        val scrollView = findViewById<HorizontalScrollView>(R.id.imagesScrollView)
        val imageIndicators = findViewById<LinearLayout>(R.id.imageIndicators)
        val screenWidth = resources.displayMetrics.widthPixels
        
        scrollView.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            // Calcular qué imagen está visible
            val currentImageIndex = (scrollX / screenWidth.toFloat()).toInt()
            val clampedIndex = currentImageIndex.coerceIn(0, totalImages - 1)
            
            // Actualizar indicadores
            for (i in 0 until totalImages) {
                val indicator = imageIndicators.getChildAt(i) as View
                if (i == clampedIndex) {
                    indicator.background = ContextCompat.getDrawable(this, R.drawable.indicator_dot_active)
                } else {
                    indicator.background = ContextCompat.getDrawable(this, R.drawable.indicator_dot_inactive)
                }
            }
        }
    }
    
    private suspend fun byteArrayToBitmap(byteArray: ByteArray): Bitmap? = withContext(Dispatchers.IO) {
        return@withContext try {
            BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        } catch (e: Exception) {
            Log.e("RecipeDetailActivity", "Error convirtiendo ByteArray a Bitmap: ${e.message}", e)
            null
        }
    }
    
    private fun setupCommentsRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.commentsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        commentsAdapter = CommentsAdapter(
            onVoteClick = { comment, position, voteType ->
                // Manejar voto del comentario (like o dislike)
                handleCommentVote(comment, position, voteType)
            },
            onReplyClick = { commentId, replyText ->
                // Manejar respuesta a comentario
                handleCommentReply(commentId, replyText)
            },
            onReplyVoteClick = { reply, commentId, voteType ->
                // Manejar voto de respuesta (like o dislike)
                handleReplyVote(reply, commentId, voteType)
            }
        )
        recyclerView.adapter = commentsAdapter
    }
    
    private fun loadComments() {
        lifecycleScope.launch {
            try {
                Log.d("RecipeDetailActivity", "=== CARGANDO COMENTARIOS ===")
                Log.d("RecipeDetailActivity", "ID de receta: $recipeId")
                
                // Intentar cargar comentarios con reintentos automáticos
                var result = apiService.getCommentsByRecipeId(recipeId)
                var attempts = 1
                val maxAttempts = 3
                
                // Si falla, reintentar hasta 2 veces más
                while (!result.isSuccess && attempts < maxAttempts) {
                    Log.w("RecipeDetailActivity", "Intento $attempts de cargar comentarios falló, reintentando en 2 segundos...")
                    val error = result.exceptionOrNull()
                    Log.e("RecipeDetailActivity", "Error en intento $attempts: ${error?.message}")
                    
                    kotlinx.coroutines.delay(2000) // Esperar 2 segundos antes de reintentar
                    attempts++
                    result = apiService.getCommentsByRecipeId(recipeId)
                }
                
                Log.d("RecipeDetailActivity", "Resultado obtenido después de $attempts intento(s): ${result.isSuccess}")
                
                if (result.isSuccess) {
                    val comments = result.getOrNull() ?: emptyList()
                    Log.d("RecipeDetailActivity", "✓ Comentarios cargados: ${comments.size}")
                    
                    // Cargar conteos de votos y estado del voto del usuario para cada comentario
                    val commentsWithVotes = mutableListOf<ApiService.CommentData>()
                    
                    for (comment in comments) {
                        // Cargar conteos de votos
                        val votesCountResult = apiService.getCommentVotesCount(comment.id)
                        val votesCount = if (votesCountResult.isSuccess) {
                            votesCountResult.getOrNull() ?: ApiService.CommentVotesCount(0, 0)
                        } else {
                            ApiService.CommentVotesCount(0, 0)
                        }
                        
                        // Determinar voteType del usuario actual si está logueado
                        var userVoteType = -1
                        if (currentUserEmail.isNotEmpty()) {
                            try {
                                val userResult = apiService.getUserByEmail(currentUserEmail)
                                if (userResult.isSuccess) {
                                    val mysqlUser = userResult.getOrNull()
                                    if (mysqlUser != null) {
                                        // Nota: Por ahora, voteType será -1 por defecto
                                        // En el futuro podrías agregar un endpoint para obtener el voto del usuario
                                        userVoteType = -1
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("RecipeDetailActivity", "Error obteniendo usuario para determinar voteType: ${e.message}")
                            }
                        }
                        
                        val commentWithVotes = comment.copy(
                            likes = votesCount.likes,
                            dislikes = votesCount.dislikes,
                            voteType = userVoteType
                        )
                        commentsWithVotes.add(commentWithVotes)
                        
                        // Cargar respuestas de este comentario con sus conteos de votos
                        val repliesResult = apiService.getRepliesByCommentId(comment.id)
                        if (repliesResult.isSuccess) {
                            val replies = repliesResult.getOrNull() ?: emptyList()
                            Log.d("RecipeDetailActivity", "  Respuestas para comentario ${comment.id}: ${replies.size}")
                            
                            // Cargar conteos de votos para cada respuesta
                            val repliesWithVotes = replies.map { reply ->
                                val votesCountResult = apiService.getReplyVotesCount(reply.id)
                                val votesCount = if (votesCountResult.isSuccess) {
                                    votesCountResult.getOrNull() ?: ApiService.ReplyVotesCount(0, 0)
                                } else {
                                    ApiService.ReplyVotesCount(0, 0)
                                }
                                
                                // Determinar voteType del usuario actual si está logueado
                                var userVoteType = -1
                                if (currentUserEmail.isNotEmpty()) {
                                    try {
                                        val userResult = apiService.getUserByEmail(currentUserEmail)
                                        if (userResult.isSuccess) {
                                            val mysqlUser = userResult.getOrNull()
                                            if (mysqlUser != null) {
                                                // Por ahora, voteType será -1 por defecto
                                                // En el futuro podrías agregar un endpoint para obtener el voto del usuario
                                                userVoteType = -1
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w("RecipeDetailActivity", "Error obteniendo usuario para determinar voteType de reply: ${e.message}")
                                    }
                                }
                                
                                reply.copy(
                                    likes = votesCount.likes,
                                    dislikes = votesCount.dislikes,
                                    voteType = userVoteType
                                )
                            }
                            
                            // Actualizar adapter con las respuestas (incluso si está vacío)
                            withContext(Dispatchers.Main) {
                                commentsAdapter.updateRepliesForComment(comment.id, repliesWithVotes)
                            }
                        } else {
                            // Si falla, inicializar con lista vacía
                            Log.w("RecipeDetailActivity", "  No se pudieron cargar respuestas para comentario ${comment.id}")
                            withContext(Dispatchers.Main) {
                                commentsAdapter.updateRepliesForComment(comment.id, emptyList())
                            }
                        }
                    }
                    
                    // Log detallado de cada comentario
                    commentsWithVotes.forEachIndexed { index, comment ->
                        Log.d("RecipeDetailActivity", "  Comentario ${index + 1}:")
                        Log.d("RecipeDetailActivity", "    ID: ${comment.id}")
                        Log.d("RecipeDetailActivity", "    Texto: ${comment.commentText.take(50)}...")
                        Log.d("RecipeDetailActivity", "    Usuario: ${comment.userName} ${comment.userLastName}")
                        Log.d("RecipeDetailActivity", "    Likes: ${comment.likes}, Dislikes: ${comment.dislikes}")
                    }
                    
                    withContext(Dispatchers.Main) {
                        val noCommentsText = findViewById<TextView>(R.id.noCommentsText)
                        val commentsRecyclerView = findViewById<RecyclerView>(R.id.commentsRecyclerView)
                        
                        if (commentsWithVotes.isEmpty()) {
                            Log.d("RecipeDetailActivity", "No hay comentarios - mostrando mensaje")
                            noCommentsText.visibility = TextView.VISIBLE
                            commentsRecyclerView.visibility = RecyclerView.GONE
                        } else {
                            Log.d("RecipeDetailActivity", "Mostrando ${commentsWithVotes.size} comentarios en RecyclerView")
                            noCommentsText.visibility = TextView.GONE
                            commentsRecyclerView.visibility = RecyclerView.VISIBLE
                            commentsAdapter.updateComments(commentsWithVotes)
                            Log.d("RecipeDetailActivity", "✓ Comentarios actualizados en adapter")
                        }
                    }
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("RecipeDetailActivity", "Error cargando comentarios después de $attempts intentos: ${error?.message}")
                    Log.e("RecipeDetailActivity", "Stack trace: ${error?.stackTrace?.joinToString("\n")}")
                    
                    withContext(Dispatchers.Main) {
                        val noCommentsText = findViewById<TextView>(R.id.noCommentsText)
                        val commentsRecyclerView = findViewById<RecyclerView>(R.id.commentsRecyclerView)
                        noCommentsText.visibility = TextView.VISIBLE
                        commentsRecyclerView.visibility = RecyclerView.GONE
                        Log.w("RecipeDetailActivity", "Mostrando mensaje 'No hay comentarios' debido a error")
                    }
                }
            } catch (e: Exception) {
                Log.e("RecipeDetailActivity", "EXCEPCIÓN cargando comentarios: ${e.message}", e)
                Log.e("RecipeDetailActivity", "Stack trace: ${e.stackTrace.joinToString("\n")}")
                withContext(Dispatchers.Main) {
                    val noCommentsText = findViewById<TextView>(R.id.noCommentsText)
                    val commentsRecyclerView = findViewById<RecyclerView>(R.id.commentsRecyclerView)
                    noCommentsText.visibility = TextView.VISIBLE
                    commentsRecyclerView.visibility = RecyclerView.GONE
                }
            }
        }
    }
    
    private fun loadVotesCount() {
        lifecycleScope.launch {
            try {
                Log.d("RecipeDetailActivity", "=== CARGANDO CONTEO DE VOTOS ===")
                Log.d("RecipeDetailActivity", "ID de receta: $recipeId")
                
                val result = apiService.getRecipeVotesCount(recipeId)
                
                if (result.isSuccess) {
                    val votesCount = result.getOrNull()
                    if (votesCount != null) {
                        Log.d("RecipeDetailActivity", "✓ Conteos obtenidos - Likes: ${votesCount.likes}, Dislikes: ${votesCount.dislikes}")
                        
                        withContext(Dispatchers.Main) {
                            findViewById<TextView>(R.id.likesCount).text = votesCount.likes.toString()
                            findViewById<TextView>(R.id.dislikesCount).text = votesCount.dislikes.toString()
                        }
                    } else {
                        Log.w("RecipeDetailActivity", "Conteos de votos son null")
                        withContext(Dispatchers.Main) {
                            findViewById<TextView>(R.id.likesCount).text = "0"
                            findViewById<TextView>(R.id.dislikesCount).text = "0"
                        }
                    }
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("RecipeDetailActivity", "Error cargando conteos de votos: ${error?.message}")
                    withContext(Dispatchers.Main) {
                        findViewById<TextView>(R.id.likesCount).text = "0"
                        findViewById<TextView>(R.id.dislikesCount).text = "0"
                    }
                }
            } catch (e: Exception) {
                Log.e("RecipeDetailActivity", "EXCEPCIÓN cargando conteos de votos: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    findViewById<TextView>(R.id.likesCount).text = "0"
                    findViewById<TextView>(R.id.dislikesCount).text = "0"
                }
            }
        }
    }
    
    private fun handleCommentVote(comment: ApiService.CommentData, position: Int, voteType: Int) {
        // Verificar si el usuario está logueado
        if (currentUserEmail.isEmpty()) {
            Log.e("RecipeDetailActivity", "⚠️ Intento de voto sin usuario logueado - Email vacío")
            Toast.makeText(this, "Debes iniciar sesión para votar", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Guardar el estado anterior para poder revertir si falla
        val previousVoteType = comment.voteType
        val previousLikes = comment.likes
        val previousDislikes = comment.dislikes
        
        // Calcular nuevos conteos (optimista)
        val newLikes = when {
            voteType == 1 && previousVoteType != 1 -> previousLikes + 1 // Agregar like
            voteType == -1 && previousVoteType == 1 -> previousLikes - 1 // Quitar like
            voteType == 0 && previousVoteType == 1 -> previousLikes - 1 // Cambiar de like a dislike
            else -> previousLikes
        }
        
        val newDislikes = when {
            voteType == 0 && previousVoteType != 0 -> previousDislikes + 1 // Agregar dislike
            voteType == -1 && previousVoteType == 0 -> previousDislikes - 1 // Quitar dislike
            voteType == 1 && previousVoteType == 0 -> previousDislikes - 1 // Cambiar de dislike a like
            else -> previousDislikes
        }
        
        // Actualizar UI inmediatamente (optimista)
        commentsAdapter.updateVoteStatus(position, voteType, newLikes, newDislikes)
        
        val voteAction = when (voteType) {
            1 -> "like agregado"
            0 -> "dislike agregado"
            else -> "voto removido"
        }
        Log.d("RecipeDetailActivity", "$voteAction para comentario ID: ${comment.id}")
        
        lifecycleScope.launch {
            try {
                // PRIMERO: Obtener el ID de MySQL del usuario usando el email
                Log.d("RecipeDetailActivity", "Obteniendo ID de MySQL del usuario con email: $currentUserEmail")
                val userResult = apiService.getUserByEmail(currentUserEmail)
                
                if (!userResult.isSuccess) {
                    Log.e("RecipeDetailActivity", "✗ Error obteniendo usuario desde MySQL: ${userResult.exceptionOrNull()?.message}")
                    // Revertir el cambio en la UI si falla
                    commentsAdapter.updateVoteStatus(position, previousVoteType, previousLikes, previousDislikes)
                    Toast.makeText(this@RecipeDetailActivity, "Error obteniendo información del usuario", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val mysqlUser = userResult.getOrNull()
                if (mysqlUser == null) {
                    Log.e("RecipeDetailActivity", "✗ Usuario no encontrado en MySQL con email: $currentUserEmail")
                    // Revertir el cambio en la UI si falla
                    commentsAdapter.updateVoteStatus(position, previousVoteType, previousLikes, previousDislikes)
                    Toast.makeText(this@RecipeDetailActivity, "Error: Usuario no encontrado", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val mysqlUserId = mysqlUser.id
                Log.d("RecipeDetailActivity", "✓ ID de MySQL obtenido: $mysqlUserId")
                Log.d("RecipeDetailActivity", "Llamando a syncCommentVoteToMySQL con: commentId=${comment.id}, userId=$mysqlUserId, voteType=$voteType")
                
                // SEGUNDO: Enviar el voto con el ID de MySQL
                val result = apiService.syncCommentVoteToMySQL(comment.id, mysqlUserId, voteType)
                
                if (result.isSuccess) {
                    Log.d("RecipeDetailActivity", "✓ Voto de comentario actualizado exitosamente")
                    // Recargar conteos reales del servidor
                    val votesCountResult = apiService.getCommentVotesCount(comment.id)
                    if (votesCountResult.isSuccess) {
                        val votesCount = votesCountResult.getOrNull()
                        if (votesCount != null) {
                            commentsAdapter.updateVoteStatus(position, voteType, votesCount.likes, votesCount.dislikes)
                        }
                    }
                } else {
                    Log.e("RecipeDetailActivity", "✗ Error actualizando voto de comentario: ${result.exceptionOrNull()?.message}")
                    // Revertir el cambio en la UI si falla
                    commentsAdapter.updateVoteStatus(position, previousVoteType, previousLikes, previousDislikes)
                    Toast.makeText(this@RecipeDetailActivity, "Error actualizando voto: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("RecipeDetailActivity", "✗ EXCEPCIÓN en handleCommentVote: ${e.message}", e)
                // Revertir el cambio en la UI si falla
                commentsAdapter.updateVoteStatus(position, previousVoteType, previousLikes, previousDislikes)
                Toast.makeText(this@RecipeDetailActivity, "Error actualizando voto: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun handleCommentReply(commentId: Long, replyText: String) {
        // Verificar si el usuario está logueado
        if (currentUserEmail.isEmpty()) {
            Log.e("RecipeDetailActivity", "⚠️ Intento de respuesta sin usuario logueado - Email vacío")
            Toast.makeText(this, "Debes iniciar sesión para responder", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (replyText.trim().isEmpty()) {
            Toast.makeText(this, "El texto de la respuesta no puede estar vacío", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d("RecipeDetailActivity", "=== CREANDO RESPUESTA ===")
        Log.d("RecipeDetailActivity", "Comment ID: $commentId")
        Log.d("RecipeDetailActivity", "Reply Text: $replyText")
        
        lifecycleScope.launch {
            try {
                // PRIMERO: Obtener el ID de MySQL del usuario usando el email
                Log.d("RecipeDetailActivity", "Obteniendo ID de MySQL del usuario con email: $currentUserEmail")
                val userResult = apiService.getUserByEmail(currentUserEmail)
                
                if (!userResult.isSuccess) {
                    Log.e("RecipeDetailActivity", "✗ Error obteniendo usuario desde MySQL: ${userResult.exceptionOrNull()?.message}")
                    Toast.makeText(this@RecipeDetailActivity, "Error obteniendo información del usuario", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val mysqlUser = userResult.getOrNull()
                if (mysqlUser == null) {
                    Log.e("RecipeDetailActivity", "✗ Usuario no encontrado en MySQL con email: $currentUserEmail")
                    Toast.makeText(this@RecipeDetailActivity, "Error: Usuario no encontrado", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val mysqlUserId = mysqlUser.id
                Log.d("RecipeDetailActivity", "✓ ID de MySQL obtenido: $mysqlUserId")
                Log.d("RecipeDetailActivity", "Llamando a createCommentReply con: commentId=$commentId, userId=$mysqlUserId")
                
                // SEGUNDO: Crear la respuesta con el ID de MySQL
                val result = apiService.createCommentReply(commentId, mysqlUserId, replyText)
                
                if (result.isSuccess) {
                    val replyId = result.getOrNull()
                    Log.d("RecipeDetailActivity", "✓ Respuesta creada exitosamente con ID: $replyId")
                    Toast.makeText(this@RecipeDetailActivity, "Respuesta enviada", Toast.LENGTH_SHORT).show()
                    
                    // Recargar respuestas del comentario con conteos de votos
                    val repliesResult = apiService.getRepliesByCommentId(commentId)
                    if (repliesResult.isSuccess) {
                        val replies = repliesResult.getOrNull() ?: emptyList()
                        // Cargar conteos de votos para cada respuesta
                        val repliesWithVotes = replies.map { reply ->
                            val votesCountResult = apiService.getReplyVotesCount(reply.id)
                            val votesCount = if (votesCountResult.isSuccess) {
                                votesCountResult.getOrNull() ?: ApiService.ReplyVotesCount(0, 0)
                            } else {
                                ApiService.ReplyVotesCount(0, 0)
                            }
                            
                            var userVoteType = -1
                            if (currentUserEmail.isNotEmpty()) {
                                try {
                                    val userResult = apiService.getUserByEmail(currentUserEmail)
                                    if (userResult.isSuccess && userResult.getOrNull() != null) {
                                        userVoteType = -1 // Por defecto, sin voto
                                    }
                                } catch (e: Exception) {
                                    Log.w("RecipeDetailActivity", "Error obteniendo usuario para voteType: ${e.message}")
                                }
                            }
                            
                            reply.copy(
                                likes = votesCount.likes,
                                dislikes = votesCount.dislikes,
                                voteType = userVoteType
                            )
                        }
                        withContext(Dispatchers.Main) {
                            commentsAdapter.updateRepliesForComment(commentId, repliesWithVotes)
                        }
                    }
                } else {
                    Log.e("RecipeDetailActivity", "✗ Error creando respuesta: ${result.exceptionOrNull()?.message}")
                    Toast.makeText(this@RecipeDetailActivity, "Error creando respuesta: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("RecipeDetailActivity", "✗ EXCEPCIÓN en handleCommentReply: ${e.message}", e)
                Toast.makeText(this@RecipeDetailActivity, "Error creando respuesta: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Manejar voto de una respuesta (reply)
     */
    private fun handleReplyVote(reply: ApiService.ReplyData, commentId: Long, voteType: Int) {
        if (currentUserEmail.isEmpty()) {
            Log.e("RecipeDetailActivity", "⚠️ Intento de voto de reply sin usuario logueado - Email vacío")
            Toast.makeText(this, "Debes iniciar sesión para votar", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (reply.id <= 0) {
            Log.e("RecipeDetailActivity", "✗ ERROR: Reply ID inválido: ${reply.id}")
            Toast.makeText(this, "Error: ID de respuesta inválido", Toast.LENGTH_SHORT).show()
            return
        }
        
        val previousVoteType = reply.voteType
        val previousLikes = reply.likes
        val previousDislikes = reply.dislikes
        
        Log.d("RecipeDetailActivity", "=== INICIANDO VOTO DE REPLY ===")
        Log.d("RecipeDetailActivity", "Reply ID: ${reply.id}")
        Log.d("RecipeDetailActivity", "Comment ID: $commentId")
        Log.d("RecipeDetailActivity", "Current User Email: $currentUserEmail")
        Log.d("RecipeDetailActivity", "Vote Type: $voteType")
        
        // Actualizar UI optimísticamente
        val newLikes = when {
            voteType == 1 && previousVoteType != 1 -> previousLikes + 1
            voteType == -1 && previousVoteType == 1 -> previousLikes - 1
            voteType == 0 && previousVoteType == 1 -> previousLikes - 1
            else -> previousLikes
        }
        
        val newDislikes = when {
            voteType == 0 && previousVoteType != 0 -> previousDislikes + 1
            voteType == -1 && previousVoteType == 0 -> previousDislikes - 1
            voteType == 1 && previousVoteType == 0 -> previousDislikes - 1
            else -> previousDislikes
        }
        
        commentsAdapter.updateReplyVoteStatus(commentId, reply.id, voteType, newLikes, newDislikes)
        
        lifecycleScope.launch {
            try {
                Log.d("RecipeDetailActivity", "Obteniendo ID de MySQL del usuario con email: $currentUserEmail")
                val userResult = apiService.getUserByEmail(currentUserEmail)
                
                if (!userResult.isSuccess) {
                    Log.e("RecipeDetailActivity", "✗ Error obteniendo usuario desde MySQL: ${userResult.exceptionOrNull()?.message}")
                    commentsAdapter.updateReplyVoteStatus(commentId, reply.id, previousVoteType, previousLikes, previousDislikes)
                    Toast.makeText(this@RecipeDetailActivity, "Error obteniendo información del usuario", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val mysqlUser = userResult.getOrNull()
                if (mysqlUser == null) {
                    Log.e("RecipeDetailActivity", "✗ Usuario no encontrado en MySQL con email: $currentUserEmail")
                    commentsAdapter.updateReplyVoteStatus(commentId, reply.id, previousVoteType, previousLikes, previousDislikes)
                    Toast.makeText(this@RecipeDetailActivity, "Error: Usuario no encontrado", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val mysqlUserId = mysqlUser.id
                Log.d("RecipeDetailActivity", "✓ ID de MySQL obtenido: $mysqlUserId")
                Log.d("RecipeDetailActivity", "Llamando a syncReplyVoteToMySQL con: replyId=${reply.id}, userId=$mysqlUserId, voteType=$voteType")
                
                val result = apiService.syncReplyVoteToMySQL(reply.id, mysqlUserId, voteType)
                
                if (result.isSuccess) {
                    Log.d("RecipeDetailActivity", "✓ Voto de reply actualizado exitosamente")
                    // Recargar conteos de votos del servidor para asegurar consistencia
                    val votesCountResult = apiService.getReplyVotesCount(reply.id)
                    if (votesCountResult.isSuccess) {
                        val votesCount = votesCountResult.getOrNull() ?: ApiService.ReplyVotesCount(0, 0)
                        withContext(Dispatchers.Main) {
                            commentsAdapter.updateReplyVoteStatus(commentId, reply.id, voteType, votesCount.likes, votesCount.dislikes)
                        }
                    }
                } else {
                    Log.e("RecipeDetailActivity", "✗ Error actualizando voto de reply: ${result.exceptionOrNull()?.message}")
                    commentsAdapter.updateReplyVoteStatus(commentId, reply.id, previousVoteType, previousLikes, previousDislikes)
                    Toast.makeText(this@RecipeDetailActivity, "Error actualizando voto: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("RecipeDetailActivity", "✗ EXCEPCIÓN en handleReplyVote: ${e.message}", e)
                commentsAdapter.updateReplyVoteStatus(commentId, reply.id, previousVoteType, previousLikes, previousDislikes)
                Toast.makeText(this@RecipeDetailActivity, "Error actualizando voto: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setupCommentForm() {
        val addCommentLayout = findViewById<LinearLayout>(R.id.addCommentLayout)
        val commentEditText = findViewById<android.widget.EditText>(R.id.commentEditText)
        val submitCommentButton = findViewById<android.widget.Button>(R.id.submitCommentButton)
        
        Log.d("RecipeDetailActivity", "=== CONFIGURANDO FORMULARIO DE COMENTARIOS ===")
        Log.d("RecipeDetailActivity", "User ID: $currentUserId")
        Log.d("RecipeDetailActivity", "User Email: '$currentUserEmail'")
        Log.d("RecipeDetailActivity", "Email isEmpty: ${currentUserEmail.isEmpty()}")
        
        // Asegurar que tenemos el email del usuario para poder comentar
        // Si el email está vacío pero tenemos user_id, intentar obtenerlo del intent
        if (currentUserEmail.isEmpty() && currentUserId != -1L) {
            val emailFromIntent = this.intent.getStringExtra("user_email") ?: ""
            if (emailFromIntent.isNotEmpty()) {
                currentUserEmail = emailFromIntent
                Log.d("RecipeDetailActivity", "Email recuperado del intent: '$currentUserEmail'")
            } else {
                Log.w("RecipeDetailActivity", "⚠️ Email vacío - intentando obtener desde MySQL...")
            }
        }
        
        // Si el usuario está logueado (tiene user_id), mostrar el formulario
        // Obtendremos el ID de MySQL al momento de enviar el comentario si es necesario
        if (currentUserId != -1L) {
            Log.d("RecipeDetailActivity", "Usuario logueado - Mostrando formulario de comentarios")
            addCommentLayout.visibility = LinearLayout.VISIBLE
            
            // Obtener ID de MySQL del usuario en segundo plano (no bloquea mostrar el formulario)
            if (currentUserEmail.isNotEmpty()) {
                lifecycleScope.launch {
                    try {
                        val userResult = apiService.getUserByEmail(currentUserEmail)
                        if (userResult.isSuccess) {
                            val user = userResult.getOrNull()
                            currentUserMySQLId = user?.id ?: -1L
                            Log.d("RecipeDetailActivity", "✓ Usuario MySQL ID obtenido: $currentUserMySQLId")
                        } else {
                            Log.w("RecipeDetailActivity", "⚠️ No se pudo obtener ID MySQL ahora, se intentará al enviar comentario")
                        }
                    } catch (e: Exception) {
                        Log.e("RecipeDetailActivity", "Error obteniendo usuario MySQL: ${e.message}", e)
                    }
                }
            }
            
            // Configurar botón para enviar comentario
            submitCommentButton.setOnClickListener {
                val commentText = commentEditText.text.toString().trim()
                if (commentText.isNotEmpty()) {
                    // Si aún no tenemos el ID MySQL, intentar obtenerlo antes de enviar
                    if (currentUserMySQLId == -1L && currentUserEmail.isNotEmpty()) {
                        lifecycleScope.launch {
                            try {
                                val userResult = apiService.getUserByEmail(currentUserEmail)
                                if (userResult.isSuccess) {
                                    val user = userResult.getOrNull()
                                    currentUserMySQLId = user?.id ?: -1L
                                    if (currentUserMySQLId != -1L) {
                                        createComment(commentText)
                                    } else {
                                        android.widget.Toast.makeText(
                                            this@RecipeDetailActivity,
                                            "Error: No se pudo identificar tu usuario",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else {
                                    android.widget.Toast.makeText(
                                        this@RecipeDetailActivity,
                                        "Error: No se pudo verificar tu usuario",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(
                                    this@RecipeDetailActivity,
                                    "Error: ${e.message}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else if (currentUserMySQLId != -1L) {
                        Log.d("RecipeDetailActivity", "Enviando comentario con usuario MySQL ID: $currentUserMySQLId")
                        createComment(commentText)
                    } else {
                        android.widget.Toast.makeText(
                            this@RecipeDetailActivity,
                            "Error: Debes iniciar sesión para comentar",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    android.widget.Toast.makeText(
                        this@RecipeDetailActivity,
                        "Por favor escribe un comentario",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            // Usuario no logueado, ocultar formulario
            Log.d("RecipeDetailActivity", "Usuario no logueado - Ocultando formulario")
            addCommentLayout.visibility = LinearLayout.GONE
        }
    }
    
    private fun createComment(commentText: String) {
        if (currentUserMySQLId == -1L) {
            android.widget.Toast.makeText(
                this,
                "Debes iniciar sesión para comentar",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                val submitButton = findViewById<android.widget.Button>(R.id.submitCommentButton)
                val commentEditText = findViewById<android.widget.EditText>(R.id.commentEditText)
                
                // Deshabilitar botón mientras se envía
                submitButton.isEnabled = false
                submitButton.text = "Enviando..."
                
                Log.d("RecipeDetailActivity", "Creando comentario en receta $recipeId con usuario $currentUserMySQLId")
                val result = apiService.createComment(recipeId, currentUserMySQLId, commentText)
                
                if (result.isSuccess) {
                    val commentId = result.getOrNull()
                    Log.d("RecipeDetailActivity", "✓ Comentario creado exitosamente con ID: $commentId")
                    
                    // Limpiar campo de texto
                    commentEditText.setText("")
                    
                    // Recargar comentarios
                    loadComments()
                    
                    android.widget.Toast.makeText(
                        this@RecipeDetailActivity,
                        "Comentario publicado exitosamente",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("RecipeDetailActivity", "Error creando comentario: ${error?.message}")
                    android.widget.Toast.makeText(
                        this@RecipeDetailActivity,
                        "Error al publicar comentario: ${error?.message ?: "Error desconocido"}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                
                // Rehabilitar botón
                withContext(Dispatchers.Main) {
                    submitButton.isEnabled = true
                    submitButton.text = "Publicar comentario"
                }
            } catch (e: Exception) {
                Log.e("RecipeDetailActivity", "Error creando comentario: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val submitButton = findViewById<android.widget.Button>(R.id.submitCommentButton)
                    submitButton.isEnabled = true
                    submitButton.text = "Publicar comentario"
                    
                    android.widget.Toast.makeText(
                        this@RecipeDetailActivity,
                        "Error: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Guardar datos del usuario para preservarlos
        outState.putLong("saved_user_id", currentUserId)
        outState.putString("saved_user_email", currentUserEmail)
        Log.d("RecipeDetailActivity", "Datos guardados en savedInstanceState - Email: '$currentUserEmail'")
    }
    
    override fun onResume() {
        super.onResume()
        Log.d("RecipeDetailActivity", "=== onResume - Recargando comentarios ===")
        // Recargar comentarios cuando se vuelve a la actividad
        if (recipeId != -1L) {
            loadComments()
        }
    }
    
    override fun onBackPressed() {
        // Cuando se presiona el botón de atrás, pasar el correo al MainActivity
        Log.d("RecipeDetailActivity", "onBackPressed - Regresando a MainActivity con email: '$currentUserEmail'")
        
        // SIEMPRE pasar el correo, incluso si está vacío (intentar obtener del intent original)
        val emailToPass = currentUserEmail.ifEmpty {
            this.intent.getStringExtra("user_email") ?: ""
        }
        
        val intent = android.content.Intent(this, MainActivity::class.java)
        intent.putExtra("user_id", currentUserId)
        intent.putExtra("user_email", emailToPass) // SIEMPRE pasar el correo
        intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
        // No llamar super.onBackPressed() porque ya estamos manejando el cierre manualmente
    }
}
