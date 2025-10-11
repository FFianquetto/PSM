package com.example.ejemplo2

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ejemplo2.data.repository.RecipeRepository
import com.example.ejemplo2.data.api.ApiService
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class CreateRecipeActivity : AppCompatActivity() {
    
    companion object {
        private const val REQUEST_CODE_GALLERY = 100
        private const val REQUEST_CODE_CAMERA = 101
    }
    
    private lateinit var recipeRepository: RecipeRepository
    private lateinit var apiService: ApiService
    private var currentUserId: Long = -1
    private var currentUserName: String = ""
    
    // Lista para almacenar las imágenes seleccionadas
    private data class RecipeImageData(
        val bitmap: Bitmap,
        var description: String = ""
    )
    private val selectedImages = mutableListOf<RecipeImageData>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_newrecipe)

        // Inicializar repositorio y servicio API
        recipeRepository = RecipeRepository(this)
        apiService = ApiService(this)
        
        // Obtener datos del usuario desde el intent
        currentUserId = intent.getLongExtra("user_id", -1)
        currentUserName = intent.getStringExtra("user_name") ?: "Usuario"
        
        // Verificar si el usuario está logueado
        if (currentUserId == -1L) {
            Toast.makeText(this, "Debes iniciar sesión para crear recetas", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Referencias a los elementos del layout
        val backButton = findViewById<View>(R.id.backButton)
        val recipeTitle = findViewById<EditText>(R.id.recipeTitle)
        val addIngredientButton = findViewById<Button>(R.id.addIngredientButton)
        val ingredientsContainer = findViewById<LinearLayout>(R.id.ingredientsContainer)
        val draftButton = findViewById<Button>(R.id.draftButton)
        val publishButton = findViewById<Button>(R.id.publishButton)
        val imagesContainer = findViewById<LinearLayout>(R.id.imagesContainer)
        val addImageButton = findViewById<LinearLayout>(R.id.addImageButton)

        // Navegación inferior
        val homeButton = findViewById<View>(R.id.homeButton)
        val createButton = findViewById<View>(R.id.createButton)
        val profileButton = findViewById<View>(R.id.profileButton)

        // Botón de regreso
        backButton.setOnClickListener {
            finish()
        }

        // Botón para agregar ingredientes
        addIngredientButton.setOnClickListener {
            addIngredientField(ingredientsContainer)
        }
        
        // Botón para agregar foto
        addImageButton.setOnClickListener {
            showImageSelectionDialog()
        }

        // Botón de borrador
        draftButton.setOnClickListener {
            saveRecipe(false) // Guardar como borrador
        }

        // Botón de publicar
        publishButton.setOnClickListener {
            saveRecipe(true) // Publicar receta
        }

        // Navegación inferior
        homeButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }

        createButton.setOnClickListener {
            // Ya estamos en crear receta, no hacer nada
        }

        profileButton.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }
    }

    private fun addIngredientField(container: LinearLayout) {
        val ingredientField = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
                // Asegurar que tenga la misma altura mínima que el campo original
                minimumHeight = 56 // dp convertido a pixels (aproximadamente)
            }
            hint = "Ingrediente"
            setPadding(16, 16, 16, 16) // Mismo padding que el campo inicial
            setTextSize(18f) // Tamaño más grande para mejor legibilidad
            setTypeface(resources.getFont(R.font.natasans), android.graphics.Typeface.BOLD) // Fuente en negrita
            setBackgroundResource(R.drawable.rounded_background)
            setTextColor(android.graphics.Color.WHITE) // Texto blanco para contraste
            setHintTextColor(android.graphics.Color.WHITE) // Placeholder blanco
        }
        container.addView(ingredientField)
    }
    
    private fun saveRecipe(isPublished: Boolean) {
        val recipeTitle = findViewById<EditText>(R.id.recipeTitle)
        val recipeSteps = findViewById<EditText>(R.id.recipeSteps)
        val cookingTimeInput = findViewById<EditText>(R.id.cookingTimeInput)
        val servingsInput = findViewById<EditText>(R.id.servingsInput)
        val ingredientsContainer = findViewById<LinearLayout>(R.id.ingredientsContainer)
        
        val title = recipeTitle.text.toString().trim()
        val steps = recipeSteps.text.toString().trim()
        val cookingTime = cookingTimeInput.text.toString().trim().toIntOrNull() ?: 0
        val servings = servingsInput.text.toString().trim().toIntOrNull() ?: 1
        
        // Validar campos obligatorios
        if (title.isBlank()) {
            Toast.makeText(this, "El título es obligatorio", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (steps.isBlank()) {
            Toast.makeText(this, "Los pasos son obligatorios", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Recopilar ingredientes
        val ingredients = mutableListOf<String>()
        for (i in 0 until ingredientsContainer.childCount) {
            val child = ingredientsContainer.getChildAt(i)
            if (child is EditText) {
                val ingredient = child.text.toString().trim()
                if (ingredient.isNotBlank()) {
                    ingredients.add(ingredient)
                }
            }
        }
        
        if (ingredients.isEmpty()) {
            Toast.makeText(this, "Debes agregar al menos un ingrediente", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Deshabilitar botones mientras se guarda
        val draftButton = findViewById<Button>(R.id.draftButton)
        val publishButton = findViewById<Button>(R.id.publishButton)
        draftButton.isEnabled = false
        publishButton.isEnabled = false
        
        val buttonText = if (isPublished) "Publicando..." else "Guardando..."
        if (isPublished) {
            publishButton.text = buttonText
        } else {
            draftButton.text = buttonText
        }
        
        lifecycleScope.launch {
            try {
                val ingredientsText = ingredients.joinToString(", ")
                
                // Convertir las imágenes a lista de Pair<Bitmap, String>
                val imagesList = selectedImages.map { imageData ->
                    Pair(imageData.bitmap, imageData.description)
                }
                
                val result = recipeRepository.createRecipeWithImages(
                    title = title,
                    ingredients = ingredientsText,
                    steps = steps,
                    authorId = currentUserId,
                    authorName = currentUserName,
                    cookingTime = cookingTime,
                    servings = servings,
                    isPublished = isPublished,
                    images = imagesList
                )
                
                if (result.isValid) {
                    Toast.makeText(this@CreateRecipeActivity, result.message, Toast.LENGTH_LONG).show()
                    
                    // Sincronización en background (no bloquea la UI)
                    if (selectedImages.isNotEmpty()) {
                        Toast.makeText(this@CreateRecipeActivity, "Sincronizando en segundo plano...", Toast.LENGTH_SHORT).show()
                        
                        // Ejecutar sincronización en un scope separado
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val createdRecipe = recipeRepository.getRecipeById(result.recipeId)
                                if (createdRecipe != null) {
                                    Log.d("CreateRecipe", "Iniciando sincronización en background")
                                    
                                    // Sincronizar receta
                                    val recipeResult = apiService.syncRecipeToMySQL(createdRecipe)
                                    Log.d("CreateRecipe", "Receta sync: ${recipeResult.isSuccess}")
                                    
                                    // Sincronizar imágenes
                                    val imagesResult = apiService.syncRecipeImagesToMySQL(result.recipeId)
                                    Log.d("CreateRecipe", "Imágenes sync: ${imagesResult.isSuccess}")
                                    
                                    // Mostrar resultado en UI thread
                                    withContext(Dispatchers.Main) {
                                        if (recipeResult.isSuccess && imagesResult.isSuccess) {
                                            Toast.makeText(this@CreateRecipeActivity, "Sincronización completada", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(this@CreateRecipeActivity, "Sincronización con errores", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("CreateRecipe", "Error en sincronización background", e)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@CreateRecipeActivity, "Error sincronizando: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    
                    finish() // Regresar inmediatamente
                } else {
                    Toast.makeText(this@CreateRecipeActivity, result.message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CreateRecipeActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                // Rehabilitar botones
                draftButton.isEnabled = true
                publishButton.isEnabled = true
                draftButton.text = "Borrador"
                publishButton.text = "Publicar"
            }
        }
    }
    
    private fun showImageSelectionDialog() {
        val options = arrayOf("Galería", "Cámara", "Cancelar")
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Seleccionar imagen")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> openGallery()
                1 -> openCamera()
                2 -> { /* Cancelar - no hacer nada */ }
            }
        }
        builder.show()
    }
    
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_CODE_GALLERY)
    }
    
    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, REQUEST_CODE_CAMERA)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_GALLERY -> {
                    data?.data?.let { uri ->
                        handleSelectedImage(uri)
                    }
                }
                REQUEST_CODE_CAMERA -> {
                    val bitmap = data?.extras?.get("data") as? Bitmap
                    bitmap?.let { handleCameraImage(it) }
                }
            }
        }
    }
    
    private fun handleSelectedImage(uri: Uri) {
        lifecycleScope.launch {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                
                bitmap?.let {
                    addImageToContainer(it)
                    Toast.makeText(this@CreateRecipeActivity, "Imagen agregada", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CreateRecipeActivity, "Error al cargar imagen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun handleCameraImage(bitmap: Bitmap) {
        lifecycleScope.launch {
            try {
                addImageToContainer(bitmap)
                Toast.makeText(this@CreateRecipeActivity, "Imagen capturada", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@CreateRecipeActivity, "Error al guardar imagen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun addImageToContainer(bitmap: Bitmap) {
        val imagesContainer = findViewById<LinearLayout>(R.id.imagesContainer)
        
        // Inflar el layout del item de imagen
        val imageItemView = layoutInflater.inflate(R.layout.recipe_image_item, imagesContainer, false)
        
        // Configurar la vista de imagen
        val imageView = imageItemView.findViewById<ImageView>(R.id.recipeImageView)
        imageView.setImageBitmap(bitmap)
        
        // Obtener el EditText de descripción
        val descriptionInput = imageItemView.findViewById<EditText>(R.id.imageDescriptionInput)
        
        // Crear objeto de datos de imagen
        val imageData = RecipeImageData(bitmap, "")
        selectedImages.add(imageData)
        
        // Listener para actualizar la descripción cuando el usuario escriba
        descriptionInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                imageData.description = s.toString()
            }
        })
        
        // Configurar botón de eliminar
        val deleteButton = imageItemView.findViewById<ImageView>(R.id.deleteImageButton)
        deleteButton.setOnClickListener {
            // Eliminar de la lista usando referencia al objeto
            selectedImages.remove(imageData)
            // Eliminar de la vista
            imagesContainer.removeView(imageItemView)
            Toast.makeText(this, "Imagen eliminada", Toast.LENGTH_SHORT).show()
        }
        
        // Agregar la vista al contenedor
        imagesContainer.addView(imageItemView)
    }
}
