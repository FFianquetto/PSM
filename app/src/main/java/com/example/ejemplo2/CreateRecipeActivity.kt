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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ejemplo2.data.repository.RecipeRepository
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
    private var currentUserId: Long = -1
    private var currentUserName: String = ""
    private var selectedImagePath: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_newrecipe)

        // Inicializar repositorio
        recipeRepository = RecipeRepository(this)
        
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
        val recipeImagePlaceholder = findViewById<ImageView>(R.id.recipeImagePlaceholder)

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
        recipeImagePlaceholder.setOnClickListener {
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
            }
            hint = "Ingrediente"
            setPadding(12, 12, 12, 12)
            setTextSize(14f)
            setBackgroundResource(R.drawable.rounded_background)
        }
        container.addView(ingredientField)
    }
    
    private fun saveRecipe(isPublished: Boolean) {
        val recipeTitle = findViewById<EditText>(R.id.recipeTitle)
        val recipeSteps = findViewById<EditText>(R.id.recipeSteps)
        val ingredientsContainer = findViewById<LinearLayout>(R.id.ingredientsContainer)
        
        val title = recipeTitle.text.toString().trim()
        val steps = recipeSteps.text.toString().trim()
        
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
                
                val result = recipeRepository.createRecipe(
                    title = title,
                    ingredients = ingredientsText,
                    steps = steps,
                    authorId = currentUserId,
                    authorName = currentUserName,
                    imagePath = selectedImagePath,
                    isPublished = isPublished
                )
                
                if (result.isValid) {
                    Toast.makeText(this@CreateRecipeActivity, result.message, Toast.LENGTH_LONG).show()
                    finish() // Regresar a la pantalla anterior
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
                    val savedPath = saveImageToInternalStorage(it, "recipe_${System.currentTimeMillis()}.jpg")
                    selectedImagePath = savedPath
                    updateImagePreview(bitmap)
                    Toast.makeText(this@CreateRecipeActivity, "Imagen seleccionada", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CreateRecipeActivity, "Error al cargar imagen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun handleCameraImage(bitmap: Bitmap) {
        lifecycleScope.launch {
            try {
                val savedPath = saveImageToInternalStorage(bitmap, "recipe_camera_${System.currentTimeMillis()}.jpg")
                selectedImagePath = savedPath
                updateImagePreview(bitmap)
                Toast.makeText(this@CreateRecipeActivity, "Imagen capturada", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@CreateRecipeActivity, "Error al guardar imagen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private suspend fun saveImageToInternalStorage(bitmap: Bitmap, filename: String): String {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            val file = File(getExternalFilesDir(null), "recipe_images")
            if (!file.exists()) {
                file.mkdirs()
            }
            
            val imageFile = File(file, filename)
            val outputStream = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            outputStream.flush()
            outputStream.close()
            
            imageFile.absolutePath
        }
    }
    
    private fun updateImagePreview(bitmap: Bitmap) {
        val recipeImagePlaceholder = findViewById<ImageView>(R.id.recipeImagePlaceholder)
        recipeImagePlaceholder.setImageBitmap(bitmap)
        recipeImagePlaceholder.scaleType = ImageView.ScaleType.CENTER_CROP
        recipeImagePlaceholder.layoutParams.width = 200
        recipeImagePlaceholder.layoutParams.height = 200
        recipeImagePlaceholder.requestLayout()
    }
}
