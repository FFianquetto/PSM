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
    private var recipeId: Long = -1
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_detail)
        
        // Inicializar repositorio
        recipeRepository = RecipeRepository(this)
        
        // Obtener ID de la receta desde el intent
        recipeId = intent.getLongExtra("recipe_id", -1)
        
        if (recipeId == -1L) {
            finish()
            return
        }
        
        // Configurar botón de regreso
        setupBackButton()
        
        // Cargar datos de la receta
        loadRecipeDetails()
    }
    
    private fun setupBackButton() {
        val backButton = findViewById<ImageView>(R.id.backButton)
        backButton.setOnClickListener {
            finish()
        }
    }
    
    private fun loadRecipeDetails() {
        lifecycleScope.launch {
            try {
                val recipe = recipeRepository.getRecipeById(recipeId)
                if (recipe != null) {
                    displayRecipeData(recipe)
                } else {
                    Log.e("RecipeDetailActivity", "Receta no encontrada con ID: $recipeId")
                    finish()
                }
            } catch (e: Exception) {
                Log.e("RecipeDetailActivity", "Error cargando detalles de receta: ${e.message}", e)
                finish()
            }
        }
    }
    
    private fun displayRecipeData(recipe: Recipe) {
        // Título
        findViewById<TextView>(R.id.recipeTitle).text = recipe.title
        
        // Autor
        findViewById<TextView>(R.id.recipeAuthor).text = recipe.authorName
        
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
        
        // Cargar imágenes de la receta
        loadRecipeImages()
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
}
