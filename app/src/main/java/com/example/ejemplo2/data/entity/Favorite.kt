package com.example.ejemplo2.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val recipeId: Long, // ID de la receta favorita
    val userId: Long, // ID del usuario que marcó como favorito
    val title: String, // Título de la receta (para mostrar en lista)
    val description: String = "", // Descripción de la receta
    val authorName: String, // Nombre del autor
    val authorAlias: String, // Alias del autor
    val cookingTime: Int = 0, // Tiempo de cocción
    val servings: Int = 1, // Porciones
    val rating: Float = 0.0f, // Calificación
    val tags: String? = null, // Etiquetas
    val imageData: ByteArray? = null, // Primera imagen (como ByteArray para SQLite)
    val createdAt: Long = System.currentTimeMillis() // Cuándo se agregó a favoritos
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Favorite

        if (id != other.id) return false
        if (recipeId != other.recipeId) return false
        if (userId != other.userId) return false
        if (title != other.title) return false
        if (description != other.description) return false
        if (authorName != other.authorName) return false
        if (authorAlias != other.authorAlias) return false
        if (cookingTime != other.cookingTime) return false
        if (servings != other.servings) return false
        if (rating != other.rating) return false
        if (tags != other.tags) return false
        if (imageData != null) {
            if (other.imageData == null) return false
            if (!imageData.contentEquals(other.imageData)) return false
        } else if (other.imageData != null) return false
        if (createdAt != other.createdAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + recipeId.hashCode()
        result = 31 * result + userId.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + authorName.hashCode()
        result = 31 * result + authorAlias.hashCode()
        result = 31 * result + cookingTime
        result = 31 * result + servings
        result = 31 * result + rating.hashCode()
        result = 31 * result + (tags?.hashCode() ?: 0)
        result = 31 * result + (imageData?.contentHashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        return result
    }
}

