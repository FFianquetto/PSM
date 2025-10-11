package com.example.ejemplo2.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "recipe_images",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["recipeId"])]
)
data class RecipeImage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val recipeId: Long,
    val imageData: ByteArray, // Byte array para guardar la imagen
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    // Override equals y hashCode porque ByteArray no los implementa correctamente
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RecipeImage

        if (id != other.id) return false
        if (recipeId != other.recipeId) return false
        if (!imageData.contentEquals(other.imageData)) return false
        if (description != other.description) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + recipeId.hashCode()
        result = 31 * result + imageData.contentHashCode()
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}


