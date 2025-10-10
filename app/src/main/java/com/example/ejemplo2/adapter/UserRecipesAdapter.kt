package com.example.ejemplo2.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ejemplo2.R
import com.example.ejemplo2.data.entity.Recipe
import java.text.SimpleDateFormat
import java.util.*

class UserRecipesAdapter(
    private var recipes: List<Recipe> = emptyList(),
    private val onRecipeClick: (Recipe) -> Unit = {}
) : RecyclerView.Adapter<UserRecipesAdapter.RecipeViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    class RecipeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.recipeTitle)
        val dateTextView: TextView = itemView.findViewById(R.id.recipeDate)
        val statusTextView: TextView = itemView.findViewById(R.id.recipeStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recipe_simple, parent, false)
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = recipes[position]
        
        holder.titleTextView.text = recipe.title
        
        // Formatear fecha
        val date = Date(recipe.createdAt)
        holder.dateTextView.text = dateFormat.format(date)
        
        // Configurar estado
        if (recipe.isPublished) {
            holder.statusTextView.text = "Publicada"
            holder.statusTextView.setBackgroundColor(
                holder.itemView.context.getColor(R.color.vibrant_orange)
            )
        } else {
            holder.statusTextView.text = "Borrador"
            holder.statusTextView.setBackgroundColor(
                holder.itemView.context.getColor(android.R.color.darker_gray)
            )
        }
        
        // Click listener
        holder.itemView.setOnClickListener {
            onRecipeClick(recipe)
        }
    }

    override fun getItemCount(): Int = recipes.size

    fun updateRecipes(newRecipes: List<Recipe>) {
        recipes = newRecipes
        notifyDataSetChanged()
    }
}
