package com.example.ejemplo2.adapter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.util.Base64
import androidx.recyclerview.widget.RecyclerView
import com.example.ejemplo2.R

data class RecipeFeedItem(
    val id: Long,
    val title: String,
    val description: String,
    val authorName: String,
    val authorAlias: String,
    val authorAvatarPath: String? = null,
    val cookingTime: Int,
    val servings: Int,
    val rating: Float,
    val tags: String?,
    val imageData: ByteArray?, // Primera imagen para compatibilidad
    val images: List<ByteArray> = emptyList(), // Todas las imágenes
    val voteType: Int = -1, // -1 = sin voto, 0 = dislike, 1 = like
    val isFavorite: Boolean = false // Si está marcado como favorito
)

class RecipeFeedAdapter(
    private var recipes: List<RecipeFeedItem> = emptyList(),
    private val onItemClick: (RecipeFeedItem) -> Unit = {},
    private val onVoteClick: (RecipeFeedItem, Int, Int) -> Unit = { _, _, _ -> }, // recipe, position, voteType
    private val onFavoriteClick: (RecipeFeedItem, Int, Boolean) -> Unit = { _, _, _ -> } // recipe, position, isFavorite
) : RecyclerView.Adapter<RecipeFeedAdapter.RecipeViewHolder>() {

    class RecipeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val authorAvatar: ImageView = view.findViewById(R.id.authorAvatar)
        val authorName: TextView = view.findViewById(R.id.authorName)
        val authorAlias: TextView = view.findViewById(R.id.authorAlias)
        val recipeImage: ImageView = view.findViewById(R.id.recipeImage)
        val likeButton: ImageView = view.findViewById(R.id.likeButton)
        val dislikeButton: ImageView = view.findViewById(R.id.dislikeButton)
        val commentButton: ImageView = view.findViewById(R.id.commentButton)
        val starButton: ImageView = view.findViewById(R.id.starButton)
        val recipeTitle: TextView = view.findViewById(R.id.recipeTitle)
        val recipeDescription: TextView = view.findViewById(R.id.recipeDescription)
        val cookingTime: TextView = view.findViewById(R.id.cookingTime)
        val servings: TextView = view.findViewById(R.id.servings)
        val rating: TextView = view.findViewById(R.id.rating)
        val recipeTags: TextView = view.findViewById(R.id.recipeTags)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recipe_feed, parent, false)
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = recipes[position]
        
        // Configurar información del autor
        holder.authorName.text = recipe.authorName
        val aliasToDisplay = recipe.authorAlias.takeIf { it.isNotBlank() } ?: recipe.authorName
        holder.authorAlias.text = if (aliasToDisplay.startsWith("@")) aliasToDisplay else "@$aliasToDisplay"
        loadAuthorAvatar(holder.authorAvatar, recipe.authorAvatarPath)
        
        // Configurar imagen de la receta (usar la primera de la lista si hay múltiples)
        val imageToShow = recipe.images.firstOrNull() ?: recipe.imageData
        
        if (imageToShow != null && imageToShow.isNotEmpty()) {
            try {
                val bitmap = BitmapFactory.decodeByteArray(imageToShow, 0, imageToShow.size)
                if (bitmap != null) {
                    holder.recipeImage.setImageBitmap(bitmap)
                    android.util.Log.d("RecipeFeedAdapter", "Imagen cargada para receta: ${recipe.title} (${recipe.images.size} imágenes disponibles)")
                } else {
                    android.util.Log.w("RecipeFeedAdapter", "No se pudo decodificar imagen para receta: ${recipe.title}")
                    holder.recipeImage.setImageResource(R.drawable.ic_no_recipes)
                }
            } catch (e: Exception) {
                android.util.Log.e("RecipeFeedAdapter", "Error decodificando imagen para receta: ${recipe.title}", e)
                holder.recipeImage.setImageResource(R.drawable.ic_no_recipes)
            }
        } else {
            android.util.Log.d("RecipeFeedAdapter", "No hay imágenes para receta: ${recipe.title}")
            holder.recipeImage.setImageResource(R.drawable.ic_no_recipes)
        }
        
        // Configurar información de la receta
        holder.recipeTitle.text = recipe.title
        holder.recipeDescription.text = recipe.description
        
        // Configurar tiempo de cocción
        val cookingTimeText = if (recipe.cookingTime > 0) {
            "${recipe.cookingTime} min"
        } else {
            "No especificado"
        }
        holder.cookingTime.text = cookingTimeText
        
        // Configurar porciones
        val servingsText = if (recipe.servings > 0) {
            "${recipe.servings} ${if (recipe.servings == 1) "porción" else "porciones"}"
        } else {
            "No especificado"
        }
        holder.servings.text = servingsText
        
        // Configurar valoración
        if (recipe.rating > 0) {
            holder.rating.text = String.format("%.1f", recipe.rating)
            holder.rating.visibility = View.VISIBLE
        } else {
            holder.rating.text = ""
            holder.rating.visibility = View.GONE
        }
        
        // Configurar etiquetas
        if (!recipe.tags.isNullOrBlank()) {
            val tagsText = recipe.tags.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(" ") { "#$it" }
            holder.recipeTags.text = tagsText
            holder.recipeTags.visibility = View.VISIBLE
        } else {
            holder.recipeTags.visibility = View.GONE
        }
        
        // Configurar estado de los botones de voto
        updateVoteButtons(holder, recipe.voteType)
        
        // Configurar click listeners
        holder.likeButton.setOnClickListener {
            val newVoteType = if (recipe.voteType == 1) -1 else 1 // Si ya es like, quitar voto. Si no, poner like
            onVoteClick(recipe, position, newVoteType)
        }
        
        holder.dislikeButton.setOnClickListener {
            val newVoteType = if (recipe.voteType == 0) -1 else 0 // Si ya es dislike, quitar voto. Si no, poner dislike
            onVoteClick(recipe, position, newVoteType)
        }
        
        // Botón de comentario (oculto, no hace nada)
        holder.commentButton.setOnClickListener {
            // El botón está oculto, no hay acción
        }
        
        // Configurar estado del botón de favorito
        updateFavoriteButton(holder, recipe.isFavorite)
        
        // Botón de estrella/favorito
        holder.starButton.setOnClickListener {
            val newFavoriteState = !recipe.isFavorite
            onFavoriteClick(recipe, position, newFavoriteState)
        }
        
        holder.itemView.setOnClickListener {
            onItemClick(recipe)
        }
    }

    private fun loadAuthorAvatar(imageView: ImageView, avatarPath: String?) {
        if (avatarPath.isNullOrBlank()) {
            setDefaultAvatar(imageView)
            return
        }
        
        try {
            val bitmap = when {
                avatarPath.startsWith("data:image", ignoreCase = true) -> {
                    val base64Data = avatarPath.substringAfter(",", avatarPath)
                    decodeBase64ToBitmap(base64Data)
                }
                isLikelyBase64(avatarPath) -> decodeBase64ToBitmap(avatarPath)
                avatarPath.startsWith("content://") -> {
                    imageView.context.contentResolver.openInputStream(Uri.parse(avatarPath))?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }
                avatarPath.startsWith("/") -> BitmapFactory.decodeFile(avatarPath)
                else -> null
            }
            
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                setDefaultAvatar(imageView)
            }
        } catch (e: Exception) {
            android.util.Log.e("RecipeFeedAdapter", "Error cargando avatar", e)
            setDefaultAvatar(imageView)
        }
    }
    
    private fun setDefaultAvatar(imageView: ImageView) {
        imageView.setImageResource(R.drawable.ic_profile)
        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
    }
    
    private fun decodeBase64ToBitmap(data: String?): Bitmap? {
        if (data.isNullOrBlank()) return null
        return try {
            val cleaned = data.trim().replace("\n", "").replace("\r", "").replace(" ", "")
            if (cleaned.isEmpty()) return null
            val decoded = Base64.decode(cleaned, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun isLikelyBase64(value: String): Boolean {
        if (value.length < 60) return false
        val base64Regex = Regex("^[A-Za-z0-9+/=\\s]+$")
        return base64Regex.matches(value)
    }

    override fun getItemCount(): Int = recipes.size

    private fun updateVoteButtons(holder: RecipeViewHolder, voteType: Int) {
        // Actualizar botón de like
        val likeColor = if (voteType == 1) {
            R.color.primary_blue // Activo (azul)
        } else {
            R.color.medium_gray // Inactivo (gris)
        }
        holder.likeButton.setColorFilter(holder.itemView.context.getColor(likeColor))
        
        // Actualizar botón de dislike
        val dislikeColor = if (voteType == 0) {
            R.color.vibrant_orange // Activo (naranja)
        } else {
            R.color.medium_gray // Inactivo (gris)
        }
        holder.dislikeButton.setColorFilter(holder.itemView.context.getColor(dislikeColor))
    }
    
    private fun updateFavoriteButton(holder: RecipeViewHolder, isFavorite: Boolean) {
        // Actualizar botón de favorito
        val starColor = if (isFavorite) {
            R.color.vibrant_orange // Activo (naranja)
        } else {
            R.color.medium_gray // Inactivo (gris)
        }
        holder.starButton.setColorFilter(holder.itemView.context.getColor(starColor))
    }

    fun updateRecipes(newRecipes: List<RecipeFeedItem>) {
        recipes = newRecipes
        notifyDataSetChanged()
    }

    fun updateVoteStatus(position: Int, voteType: Int) {
        if (position in recipes.indices) {
            recipes = recipes.toMutableList().apply {
                set(position, recipes[position].copy(voteType = voteType))
            }
            notifyItemChanged(position)
        }
    }
    
    fun updateFavoriteStatus(position: Int, isFavorite: Boolean) {
        if (position in recipes.indices) {
            recipes = recipes.toMutableList().apply {
                set(position, recipes[position].copy(isFavorite = isFavorite))
            }
            notifyItemChanged(position)
        }
    }
}

