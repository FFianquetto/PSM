package com.example.ejemplo2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ejemplo2.data.entity.Recipe
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
        
        // Imagen (si existe)
        loadRecipeImage(recipe.imagePath)
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
    
    private fun loadRecipeImage(imagePath: String?) {
        val recipeImage = findViewById<ImageView>(R.id.recipeImage)
        
        if (imagePath.isNullOrBlank()) {
            // Usar imagen por defecto
            recipeImage.setImageResource(R.drawable.ic_no_recipes)
            return
        }
        
        lifecycleScope.launch {
            try {
                val bitmap = loadBitmapFromPath(imagePath)
                if (bitmap != null) {
                    recipeImage.setImageBitmap(bitmap)
                } else {
                    recipeImage.setImageResource(R.drawable.ic_no_recipes)
                }
            } catch (e: Exception) {
                Log.e("RecipeDetailActivity", "Error cargando imagen: ${e.message}", e)
                recipeImage.setImageResource(R.drawable.ic_no_recipes)
            }
        }
    }
    
    private suspend fun loadBitmapFromPath(path: String): Bitmap? = withContext(Dispatchers.IO) {
        return@withContext try {
            if (path.startsWith("/")) {
                val file = File(path)
                Log.d("RecipeDetailActivity", "Cargando imagen local: ${file.absolutePath}")
                
                if (file.exists()) {
                    val inputStream = FileInputStream(file)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    bitmap
                } else {
                    Log.w("RecipeDetailActivity", "Archivo de imagen no existe: ${file.absolutePath}")
                    null
                }
            } else {
                Log.w("RecipeDetailActivity", "Ruta de imagen no válida: $path")
                null
            }
        } catch (e: Exception) {
            Log.e("RecipeDetailActivity", "Error en loadBitmapFromPath: ${e.message}", e)
            null
        }
    }
}
