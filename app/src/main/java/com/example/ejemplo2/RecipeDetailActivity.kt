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
        
        // Obtener ID de la receta y datos del usuario desde el intent
        recipeId = intent.getLongExtra("recipe_id", -1)
        currentUserId = intent.getLongExtra("user_id", -1)
        currentUserEmail = intent.getStringExtra("user_email") ?: ""
        
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
            val intent = android.content.Intent(this, MainActivity::class.java)
            intent.putExtra("user_id", currentUserId)
            intent.putExtra("user_email", currentUserEmail)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }
        
        createButton.setOnClickListener {
            val intent = android.content.Intent(this, CreateRecipeActivity::class.java)
            intent.putExtra("user_id", currentUserId)
            intent.putExtra("user_email", currentUserEmail)
            startActivity(intent)
        }
        
        profileButton.setOnClickListener {
            val intent = android.content.Intent(this, ProfileActivity::class.java)
            intent.putExtra("user_id", currentUserId)
            intent.putExtra("user_email", currentUserEmail)
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
                
                val result = apiService.getRecipeById(recipeId)
                
                Log.d("RecipeDetailActivity", "Resultado obtenido: ${result.isSuccess}")
                
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
                        
                        // Configurar formulario de comentarios (solo si el usuario está logueado)
                        setupCommentForm()
                    } else {
                        Log.e("RecipeDetailActivity", "Receta no encontrada (result.getOrNull() es null)")
                        android.widget.Toast.makeText(this@RecipeDetailActivity, "Receta no encontrada", android.widget.Toast.LENGTH_LONG).show()
                        finish()
                    }
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("RecipeDetailActivity", "Error cargando receta desde MySQL")
                    Log.e("RecipeDetailActivity", "Mensaje: ${error?.message}")
                    Log.e("RecipeDetailActivity", "Stack trace: ${error?.stackTrace?.joinToString("\n")}")
                    
                    android.widget.Toast.makeText(
                        this@RecipeDetailActivity, 
                        "Error cargando receta: ${error?.message ?: "Error desconocido"}", 
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
        val dateFormat = SimpleDateFormat("dd/MM/yyyy 'a las' HH:mm", Locale.getDefault())
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
        commentsAdapter = CommentsAdapter()
        recyclerView.adapter = commentsAdapter
    }
    
    private fun loadComments() {
        lifecycleScope.launch {
            try {
                Log.d("RecipeDetailActivity", "Cargando comentarios para receta ID: $recipeId")
                val result = apiService.getCommentsByRecipeId(recipeId)
                
                if (result.isSuccess) {
                    val comments = result.getOrNull() ?: emptyList()
                    Log.d("RecipeDetailActivity", "✓ Comentarios cargados: ${comments.size}")
                    
                    withContext(Dispatchers.Main) {
                        val noCommentsText = findViewById<TextView>(R.id.noCommentsText)
                        val commentsRecyclerView = findViewById<RecyclerView>(R.id.commentsRecyclerView)
                        
                        if (comments.isEmpty()) {
                            noCommentsText.visibility = TextView.VISIBLE
                            commentsRecyclerView.visibility = RecyclerView.GONE
                        } else {
                            noCommentsText.visibility = TextView.GONE
                            commentsRecyclerView.visibility = RecyclerView.VISIBLE
                            commentsAdapter.updateComments(comments)
                        }
                    }
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("RecipeDetailActivity", "Error cargando comentarios: ${error?.message}")
                    withContext(Dispatchers.Main) {
                        val noCommentsText = findViewById<TextView>(R.id.noCommentsText)
                        val commentsRecyclerView = findViewById<RecyclerView>(R.id.commentsRecyclerView)
                        noCommentsText.visibility = TextView.VISIBLE
                        commentsRecyclerView.visibility = RecyclerView.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e("RecipeDetailActivity", "Error cargando comentarios: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val noCommentsText = findViewById<TextView>(R.id.noCommentsText)
                    val commentsRecyclerView = findViewById<RecyclerView>(R.id.commentsRecyclerView)
                    noCommentsText.visibility = TextView.VISIBLE
                    commentsRecyclerView.visibility = RecyclerView.GONE
                }
            }
        }
    }
    
    private fun setupCommentForm() {
        val addCommentLayout = findViewById<LinearLayout>(R.id.addCommentLayout)
        val commentEditText = findViewById<android.widget.EditText>(R.id.commentEditText)
        val submitCommentButton = findViewById<android.widget.Button>(R.id.submitCommentButton)
        
        // Obtener ID de MySQL del usuario si está logueado
        if (currentUserId != -1L && currentUserEmail.isNotEmpty()) {
            lifecycleScope.launch {
                try {
                    val userResult = apiService.getUserByEmail(currentUserEmail)
                    if (userResult.isSuccess) {
                        val user = userResult.getOrNull()
                        currentUserMySQLId = user?.id ?: -1L
                        
                        withContext(Dispatchers.Main) {
                            if (currentUserMySQLId != -1L) {
                                // Mostrar formulario de comentarios
                                addCommentLayout.visibility = LinearLayout.VISIBLE
                                
                                // Configurar botón para enviar comentario
                                submitCommentButton.setOnClickListener {
                                    val commentText = commentEditText.text.toString().trim()
                                    if (commentText.isNotEmpty()) {
                                        createComment(commentText)
                                    } else {
                                        android.widget.Toast.makeText(
                                            this@RecipeDetailActivity,
                                            "Por favor escribe un comentario",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } else {
                                addCommentLayout.visibility = LinearLayout.GONE
                            }
                        }
                    } else {
                        Log.w("RecipeDetailActivity", "No se pudo obtener usuario MySQL para comentarios")
                        withContext(Dispatchers.Main) {
                            addCommentLayout.visibility = LinearLayout.GONE
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RecipeDetailActivity", "Error obteniendo usuario MySQL: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        addCommentLayout.visibility = LinearLayout.GONE
                    }
                }
            }
        } else {
            // Usuario no logueado, ocultar formulario
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
}
