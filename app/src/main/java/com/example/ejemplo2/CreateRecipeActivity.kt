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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ejemplo2.data.database.AppDatabase
import com.example.ejemplo2.data.repository.RecipeRepository
import com.example.ejemplo2.data.entity.Recipe
import com.example.ejemplo2.data.api.ApiService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class CreateRecipeActivity : AppCompatActivity() {
    
    companion object {
        private const val REQUEST_CODE_CAMERA = 101
    }

    private suspend fun loadRecipeForEditing() {
        try {
            val recipe = recipeRepository.getRecipeById(editingRecipeId)
            if (recipe == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CreateRecipeActivity, getString(R.string.edit_recipe_load_error), Toast.LENGTH_LONG).show()
                    finish()
                }
                return
            }
            editingRecipe = recipe
            
            val imagesFromDatabase = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(this@CreateRecipeActivity)
                    .recipeImageDao()
                    .getImagesByRecipeId(editingRecipeId)
                    .first()
            }
            
            val decodedImages = imagesFromDatabase.mapNotNull { image ->
                try {
                    val bitmap = BitmapFactory.decodeByteArray(image.imageData, 0, image.imageData.size)
                    val description = image.description ?: ""
                    bitmap?.let { it to description }
                } catch (e: Exception) {
                    Log.e("CreateRecipeActivity", "Error decodificando imagen ${image.id}: ${e.message}", e)
                    null
                }
            }
            
            withContext(Dispatchers.Main) {
                val recipeTitleView = findViewById<EditText>(R.id.recipeTitle)
                val recipeDescriptionView = findViewById<EditText>(R.id.recipeDescription)
                val recipeStepsView = findViewById<EditText>(R.id.recipeSteps)
                val cookingTimeView = findViewById<EditText>(R.id.cookingTimeInput)
                val servingsView = findViewById<EditText>(R.id.servingsInput)
                val ingredientsContainer = findViewById<LinearLayout>(R.id.ingredientsContainer)
                val imagesContainer = findViewById<LinearLayout>(R.id.imagesContainer)
                
                recipeTitleView.setText(recipe.title)
                recipeDescriptionView.setText(recipe.description)
                recipeStepsView.setText(recipe.steps)
                if (recipe.cookingTime > 0) {
                    cookingTimeView.setText(recipe.cookingTime.toString())
                } else {
                    cookingTimeView.text?.clear()
                }
                if (recipe.servings > 0) {
                    servingsView.setText(recipe.servings.toString())
                } else {
                    servingsView.text?.clear()
                }
                
                val ingredientsList = recipe.ingredients.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                ingredientsContainer.removeAllViews()
                if (ingredientsList.isEmpty()) {
                    addIngredientField(ingredientsContainer)
                } else {
                    ingredientsList.forEach { addIngredientField(ingredientsContainer, it) }
                }
                
                selectedImages.clear()
                imagesContainer.removeAllViews()
                decodedImages.forEach { (bitmap, description) ->
                    addImageToContainer(bitmap, description)
                }
            }
        } catch (e: Exception) {
            Log.e("CreateRecipeActivity", "Error cargando receta para editar: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@CreateRecipeActivity, getString(R.string.edit_recipe_load_error), Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private suspend fun saveEditedRecipeInternal(
        title: String,
        description: String,
        ingredientsText: String,
        steps: String,
        cookingTime: Int,
        servings: Int,
        imagesList: List<Pair<Bitmap, String>>,
        draftButton: Button,
        publishButton: Button,
        progressBar: ProgressBar,
        originalDraftText: String,
        originalPublishText: String
    ) {
        val recipeToUpdate = editingRecipe
        if (recipeToUpdate == null) {
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                draftButton.isEnabled = true
                publishButton.isEnabled = true
                draftButton.text = getString(R.string.edit_recipe_save_changes)
                Toast.makeText(this@CreateRecipeActivity, getString(R.string.edit_recipe_load_error), Toast.LENGTH_LONG).show()
            }
            return
        }
        
        val updatedRecipe = recipeToUpdate.copy(
            title = title,
            description = description,
            ingredients = ingredientsText,
            steps = steps,
            cookingTime = cookingTime,
            servings = servings,
            updatedAt = System.currentTimeMillis()
        )
        
        val updateResult = recipeRepository.updateRecipe(updatedRecipe)
        if (!updateResult.isValid) {
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                draftButton.isEnabled = true
                publishButton.isEnabled = true
                draftButton.text = getString(R.string.edit_recipe_save_changes)
                Toast.makeText(this@CreateRecipeActivity, updateResult.message, Toast.LENGTH_LONG).show()
            }
            return
        }
        
        try {
            val imagesValidation = withContext(Dispatchers.IO) {
                val recipeImageDao = AppDatabase.getDatabase(this@CreateRecipeActivity).recipeImageDao()
                recipeImageDao.deleteImagesByRecipeId(editingRecipeId)
                if (imagesList.isNotEmpty()) {
                    recipeRepository.saveRecipeImages(editingRecipeId, imagesList)
                } else {
                    null
                }
            }
            
            if (imagesValidation != null && !imagesValidation.isValid) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    draftButton.isEnabled = true
                    publishButton.isEnabled = true
                    draftButton.text = getString(R.string.edit_recipe_save_changes)
                    Toast.makeText(
                        this@CreateRecipeActivity,
                        imagesValidation.message ?: "Error actualizando imágenes",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return
            }
        } catch (e: Exception) {
            Log.e("CreateRecipeActivity", "Error actualizando imágenes: ${e.message}", e)
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                draftButton.isEnabled = true
                publishButton.isEnabled = true
                draftButton.text = getString(R.string.edit_recipe_save_changes)
                Toast.makeText(this@CreateRecipeActivity, "Error actualizando imágenes: ${e.message}", Toast.LENGTH_LONG).show()
            }
            return
        }
        
        editingRecipe = updatedRecipe
        
        withContext(Dispatchers.Main) {
            progressBar.visibility = View.GONE
            draftButton.isEnabled = true
            publishButton.isEnabled = true
            draftButton.text = getString(R.string.edit_recipe_save_changes)
            publishButton.text = originalPublishText
            Toast.makeText(this@CreateRecipeActivity, getString(R.string.edit_recipe_saved), Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_OK)
            finish()
        }
    }
    
    private lateinit var recipeRepository: RecipeRepository
    private lateinit var apiService: ApiService
    private var currentUserId: Long = -1 // ID de SQLite
    private var currentUserMySQLId: Long = -1 // ID de MySQL
    private var currentUserName: String = ""
    private var currentUserEmail: String = ""
    private var isEditMode: Boolean = false
    private var editingRecipeId: Long = -1
    private var editingRecipe: Recipe? = null
    
    // Lista para almacenar las imágenes seleccionadas
    private data class RecipeImageData(
        val bitmap: Bitmap,
        var description: String = ""
    )
    private val selectedImages = mutableListOf<RecipeImageData>()

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleSelectedImage(it) }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_newrecipe)

        // Inicializar repositorio y servicio API
        recipeRepository = RecipeRepository(this)
        apiService = ApiService(this)
        
        // Restaurar datos del usuario desde savedInstanceState si existe
        if (savedInstanceState != null) {
            currentUserId = savedInstanceState.getLong("saved_user_id", -1)
            currentUserName = savedInstanceState.getString("saved_user_name") ?: ""
            currentUserEmail = savedInstanceState.getString("saved_user_email") ?: ""
            isEditMode = savedInstanceState.getBoolean("saved_is_edit_mode", isEditMode)
            editingRecipeId = savedInstanceState.getLong("saved_recipe_id", editingRecipeId)
            Log.d("CreateRecipeActivity", "Datos restaurados desde savedInstanceState - Email: '$currentUserEmail'")
        }
        
        // Obtener datos del usuario desde el intent (prioridad sobre savedInstanceState)
        val userIdFromIntent = intent.getLongExtra("user_id", -1)
        val userNameFromIntent = intent.getStringExtra("user_name") ?: ""
        val userEmailFromIntent = intent.getStringExtra("user_email") ?: ""
        
        // Actualizar con datos del intent si están disponibles
        if (userIdFromIntent != -1L) {
            currentUserId = userIdFromIntent
        }
        if (userNameFromIntent.isNotEmpty()) {
            currentUserName = userNameFromIntent
        }
        if (userEmailFromIntent.isNotEmpty()) {
            currentUserEmail = userEmailFromIntent
        }
        
        isEditMode = intent.getBooleanExtra("is_edit_mode", false)
        if (isEditMode) {
            editingRecipeId = intent.getLongExtra("recipe_id", -1)
            if (editingRecipeId == -1L) {
                Toast.makeText(this, "No se pudo cargar la receta para editar", Toast.LENGTH_LONG).show()
                finish()
                return
            }
        }
        
        Log.d("CreateRecipeActivity", "Email recibido del intent: '$userEmailFromIntent', Email final: '$currentUserEmail'")

        // Validar que tenemos los datos mínimos necesarios
        if (currentUserId == -1L || currentUserEmail.isBlank()) {
            Log.e("CreateRecipeActivity", "Error: Usuario no logueado o email no disponible - UserId: $currentUserId, Email: '$currentUserEmail'")
            Toast.makeText(this, "Debes iniciar sesión para crear recetas", Toast.LENGTH_LONG).show()
            finish()
            return
        }


        val screenTitle = findViewById<TextView>(R.id.screenTitle)
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

        if (isEditMode) {
            screenTitle.text = getString(R.string.edit_recipe_title)
            draftButton.text = getString(R.string.edit_recipe_save_changes)
            publishButton.visibility = View.GONE
            lifecycleScope.launch {
                loadRecipeForEditing()
            }
        } else {
            // Botón de publicar
            publishButton.setOnClickListener {
                saveRecipe(true) // Publicar receta
            }
        }

        // Navegación inferior
        homeButton.setOnClickListener {
            // SIEMPRE pasar el email al dashboard
            val emailToPass = currentUserEmail.ifEmpty {
                // Intentar obtener del intent original si está vacío
                this.intent.getStringExtra("user_email") ?: ""
            }
            
            Log.d("CreateRecipeActivity", "Navegando a MainActivity (Dashboard) con email: '$emailToPass'")
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("user_id", currentUserId)
            if (currentUserName.isNotEmpty()) {
                intent.putExtra("user_name", currentUserName)
            }
            intent.putExtra("user_email", emailToPass) // SIEMPRE pasar el email
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }

        createButton.setOnClickListener {
            // Ya estamos en crear receta, no hacer nada
        }

        profileButton.setOnClickListener {
            // SIEMPRE pasar el email al perfil
            val emailToPass = currentUserEmail.ifEmpty {
                // Intentar obtener del intent original si está vacío
                this.intent.getStringExtra("user_email") ?: ""
            }
            
            Log.d("CreateRecipeActivity", "Navegando a ProfileActivity con email: '$emailToPass'")
            val intent = Intent(this, ProfileActivity::class.java)
            intent.putExtra("user_id", currentUserId)
            intent.putExtra("user_email", emailToPass) // SIEMPRE pasar el email
            if (currentUserName.isNotEmpty()) {
                intent.putExtra("user_name", currentUserName)
            }
            startActivity(intent)
        }
    }

    private fun addIngredientField(container: LinearLayout, value: String? = null) {
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
            value?.let {
                setText(it)
            }
        }
        container.addView(ingredientField)
    }
    
    private fun saveRecipe(isPublished: Boolean) {
        val recipeTitle = findViewById<EditText>(R.id.recipeTitle)
        val recipeDescription = findViewById<EditText>(R.id.recipeDescription)
        val recipeSteps = findViewById<EditText>(R.id.recipeSteps)
        val cookingTimeInput = findViewById<EditText>(R.id.cookingTimeInput)
        val servingsInput = findViewById<EditText>(R.id.servingsInput)
        val ingredientsContainer = findViewById<LinearLayout>(R.id.ingredientsContainer)
        
        val title = recipeTitle.text.toString().trim()
        val description = recipeDescription.text.toString().trim()
        val steps = recipeSteps.text.toString().trim()
        val cookingTime = cookingTimeInput.text.toString().trim().toIntOrNull() ?: 0
        val servings = servingsInput.text.toString().trim().toIntOrNull() ?: 1
        
        // Validar campos obligatorios
        if (title.isBlank()) {
            Toast.makeText(this, "El título es obligatorio", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (description.isBlank()) {
            Toast.makeText(this, "La descripción es obligatoria", Toast.LENGTH_SHORT).show()
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
        
        val draftButton = findViewById<Button>(R.id.draftButton)
        val publishButton = findViewById<Button>(R.id.publishButton)
        val progressBar = findViewById<ProgressBar>(R.id.saveProgressBar)
        val originalDraftText = draftButton.text.toString()
        val originalPublishText = publishButton.text.toString()
        
        draftButton.isEnabled = false
        publishButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        
        val savingText = getString(R.string.recipe_action_saving)
        val publishingText = getString(R.string.recipe_action_publishing)
        
        if (isPublished && !isEditMode) {
            publishButton.text = publishingText
        } else {
            draftButton.text = savingText
        }
        
        val ingredientsText = ingredients.joinToString(", ")
        val imagesList = selectedImages.map { it.bitmap to it.description }
        
        lifecycleScope.launch {
            try {
                val authorReady = ensureAuthorInformation()
                if (!authorReady) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        draftButton.isEnabled = true
                        publishButton.isEnabled = true
                        draftButton.text = originalDraftText
                        publishButton.text = originalPublishText
                        Toast.makeText(this@CreateRecipeActivity, "Error: No se pudo obtener el nombre del autor", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                if (isEditMode) {
                    saveEditedRecipeInternal(
                        title = title,
                        description = description,
                        ingredientsText = ingredientsText,
                        steps = steps,
                        cookingTime = cookingTime,
                        servings = servings,
                        imagesList = imagesList,
                        draftButton = draftButton,
                        publishButton = publishButton,
                        progressBar = progressBar,
                        originalDraftText = originalDraftText,
                        originalPublishText = originalPublishText
                    )
                    return@launch
                }
                
                if (currentUserEmail.isBlank()) {
                    Toast.makeText(this@CreateRecipeActivity, "Error: Email del usuario no disponible", Toast.LENGTH_LONG).show()
                    Log.e("CreateRecipeActivity", "Email vacío o no disponible")
                    return@launch
                }
                
                Log.d("CreateRecipeActivity", "Obteniendo ID de MySQL para email: $currentUserEmail")
                val userResult = apiService.getUserByEmail(currentUserEmail)
                
                if (userResult.isFailure) {
                    val error = userResult.exceptionOrNull()
                    Log.e("CreateRecipeActivity", "Error obteniendo usuario: ${error?.message}")
                    Toast.makeText(this@CreateRecipeActivity, "Error obteniendo datos del usuario: ${error?.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                val user = userResult.getOrNull()
                if (user == null) {
                    Log.e("CreateRecipeActivity", "Usuario no encontrado para email: $currentUserEmail")
                    Toast.makeText(this@CreateRecipeActivity, "Usuario no encontrado en el sistema", Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                currentUserMySQLId = user.id
                Log.d("CreateRecipeActivity", "ID MySQL obtenido exitosamente: $currentUserMySQLId para email: $currentUserEmail")
                
                Log.d("CreateRecipeActivity", "Guardando receta en SQLite local...")
                val localResult = recipeRepository.createRecipeWithImages(
                    title = title,
                    description = description,
                    ingredients = ingredientsText,
                    steps = steps,
                    authorId = currentUserId,
                    authorName = currentUserName,
                    cookingTime = cookingTime,
                    servings = servings,
                    isPublished = isPublished,
                    images = imagesList
                )
                
                if (!localResult.isValid) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        draftButton.isEnabled = true
                        publishButton.isEnabled = true
                        draftButton.text = originalDraftText
                        publishButton.text = originalPublishText
                    }
                    Toast.makeText(this@CreateRecipeActivity, "Error guardando localmente: ${localResult.message}", Toast.LENGTH_LONG).show()
                    Log.e("CreateRecipeActivity", "Error guardando en SQLite: ${localResult.message}")
                    return@launch
                }
                
                Log.d("CreateRecipeActivity", "✓ Receta guardada en SQLite local: ${localResult.recipeId}")
                
                if (isPublished) {
                    Log.d("CreateRecipeActivity", "Receta marcada como publicada - subiendo a MySQL...")
                    
                    val mysqlResult = apiService.createRecipeWithImages(
                        title = title,
                        description = description,
                        ingredients = ingredientsText,
                        steps = steps,
                        authorId = currentUserMySQLId,
                        authorName = currentUserName,
                        tags = null,
                        cookingTime = cookingTime,
                        servings = servings,
                        isPublished = true,
                        images = imagesList
                    )
                    
                    if (mysqlResult.isSuccess) {
                        Log.d("CreateRecipeActivity", "✓ Receta publicada en MySQL con ID: ${mysqlResult.getOrNull()}")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@CreateRecipeActivity, "Receta publicada exitosamente", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val error = mysqlResult.exceptionOrNull()
                        Log.e("CreateRecipeActivity", "Error publicando en MySQL: ${error?.message}")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@CreateRecipeActivity,
                                "Receta guardada localmente. Error publicando: ${error?.message ?: "Error desconocido"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } else {
                    Log.d("CreateRecipeActivity", "✓ Receta guardada como borrador (solo local)")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CreateRecipeActivity, "Receta guardada como borrador", Toast.LENGTH_SHORT).show()
                    }
                }
                
                withContext(Dispatchers.Main) {
                    val emailToPass = currentUserEmail.ifEmpty {
                        this@CreateRecipeActivity.intent.getStringExtra("user_email") ?: ""
                    }
                    
                    Log.d("CreateRecipeActivity", "Receta guardada - navegando a MainActivity (Dashboard) con email: '$emailToPass'")
                    val intent = Intent(this@CreateRecipeActivity, MainActivity::class.java)
                    intent.putExtra("user_id", currentUserId)
                    intent.putExtra("user_email", emailToPass)
                    if (currentUserName.isNotEmpty()) {
                        intent.putExtra("user_name", currentUserName)
                    }
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                Log.e("CreateRecipeActivity", "✗ EXCEPCIÓN guardando receta: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    draftButton.isEnabled = true
                    publishButton.isEnabled = true
                    draftButton.text = if (isEditMode) getString(R.string.edit_recipe_save_changes) else originalDraftText
                    publishButton.text = originalPublishText
                }
                Toast.makeText(this@CreateRecipeActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                if (!isEditMode) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        draftButton.isEnabled = true
                        publishButton.isEnabled = true
                        draftButton.text = originalDraftText
                        publishButton.text = originalPublishText
                    }
                }
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
        galleryLauncher.launch("image/*")
    }
    
    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, REQUEST_CODE_CAMERA)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
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
    
    private fun addImageToContainer(bitmap: Bitmap, initialDescription: String? = null) {
        val imagesContainer = findViewById<LinearLayout>(R.id.imagesContainer)
        
        // Inflar el layout del item de imagen
        val imageItemView = layoutInflater.inflate(R.layout.recipe_image_item, imagesContainer, false)
        
        // Configurar la vista de imagen
        val imageView = imageItemView.findViewById<ImageView>(R.id.recipeImageView)
        imageView.setImageBitmap(bitmap)
        
        val descriptionInput = imageItemView.findViewById<EditText>(R.id.imageDescriptionInput)
        
        val imageData = RecipeImageData(bitmap, initialDescription ?: "")
        selectedImages.add(imageData)
        descriptionInput.setText(initialDescription ?: "")
        
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
    
    private suspend fun ensureAuthorInformation(): Boolean {
        if (currentUserName.isNotBlank() && currentUserId != -1L) {
            return true
        }

        if (currentUserId != -1L) {
            try {
                val localUser = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@CreateRecipeActivity)
                        .userDao()
                        .getUserById(currentUserId)
                }
                if (localUser != null) {
                    if (currentUserEmail.isBlank()) {
                        currentUserEmail = localUser.email
                    }
                    val composedName = listOfNotNull(
                        localUser.name.takeIf { it.isNotBlank() },
                        localUser.lastName.takeIf { it.isNotBlank() }
                    ).joinToString(" ").trim()
                    currentUserName = if (composedName.isNotBlank()) composedName else localUser.alias
                }
            } catch (e: Exception) {
                Log.e("CreateRecipeActivity", "Error obteniendo usuario local: ${e.message}", e)
            }
        }

        if (currentUserName.isBlank() && currentUserEmail.isNotBlank()) {
            try {
                val remoteResult = apiService.getUserByEmail(currentUserEmail)
                if (remoteResult.isSuccess) {
                    val user = remoteResult.getOrNull()
                    if (user != null) {
                        if (currentUserId == -1L) {
                            currentUserId = user.id
                        }
                        val composedName = listOfNotNull(
                            user.name.takeIf { it.isNotBlank() },
                            user.lastName.takeIf { it.isNotBlank() }
                        ).joinToString(" ").trim()
                        currentUserName = if (composedName.isNotBlank()) composedName else user.alias
                    }
                } else {
                    Log.w("CreateRecipeActivity", "No se pudo obtener usuario remoto para email: '$currentUserEmail'")
                }
            } catch (e: Exception) {
                Log.e("CreateRecipeActivity", "Error obteniendo usuario remoto: ${e.message}", e)
            }
        }

        return currentUserName.isNotBlank()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Guardar datos del usuario para preservarlos
        outState.putLong("saved_user_id", currentUserId)
        outState.putString("saved_user_name", currentUserName)
        outState.putString("saved_user_email", currentUserEmail)
        outState.putBoolean("saved_is_edit_mode", isEditMode)
        outState.putLong("saved_recipe_id", editingRecipeId)
        Log.d("CreateRecipeActivity", "Datos guardados en savedInstanceState - Email: '$currentUserEmail'")
    }
    
    override fun onResume() {
        super.onResume()
        // Asegurar que tenemos el email más reciente del intent
        val emailFromIntent = intent.getStringExtra("user_email") ?: ""
        if (emailFromIntent.isNotEmpty() && emailFromIntent != currentUserEmail) {
            currentUserEmail = emailFromIntent
            Log.d("CreateRecipeActivity", "Email actualizado desde intent en onResume: '$currentUserEmail'")
        }
    }
}
