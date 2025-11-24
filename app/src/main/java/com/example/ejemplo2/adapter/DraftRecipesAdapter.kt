package com.example.ejemplo2.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ejemplo2.R
import com.example.ejemplo2.data.entity.Recipe
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class DraftRecipesAdapter(
    private var drafts: List<Recipe> = emptyList(),
    private val onRecipeClick: (Recipe) -> Unit = {},
    private val onPublishClick: (Recipe) -> Unit
) : RecyclerView.Adapter<DraftRecipesAdapter.DraftViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("America/Mexico_City")
    }

    class DraftViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.recipeTitle)
        val dateTextView: TextView = itemView.findViewById(R.id.recipeDate)
        val statusTextView: TextView = itemView.findViewById(R.id.recipeStatus)
        val publishButton: Button = itemView.findViewById(R.id.publishButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DraftViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recipe_draft, parent, false)
        return DraftViewHolder(view)
    }

    override fun onBindViewHolder(holder: DraftViewHolder, position: Int) {
        val recipe = drafts[position]

        holder.titleTextView.text = recipe.title
        holder.dateTextView.text = dateFormat.format(recipe.updatedAt)
        holder.statusTextView.text = holder.itemView.context.getString(R.string.profile_recipe_status_draft)

        holder.itemView.setOnClickListener {
            onRecipeClick(recipe)
        }

        holder.publishButton.setOnClickListener {
            onPublishClick(recipe)
        }
    }

    override fun getItemCount(): Int = drafts.size

    fun updateRecipes(newDrafts: List<Recipe>) {
        drafts = newDrafts
        notifyDataSetChanged()
    }
}

