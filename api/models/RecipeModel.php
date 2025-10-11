<?php
/**
 * Modelo para manejar recetas en MySQL
 */

require_once 'config/database.php';

class RecipeModel {
    private $db;
    
    public function __construct() {
        $database = new DatabaseConfig();
        $this->db = $database->getConnection();
    }
    
    /**
     * Crear todas las tablas necesarias para recetas
     */
    public function createTables() {
        try {
            // Tabla recipes (publicaciones/recetas) - actualizar estructura existente
            $sqlRecipes = "
                CREATE TABLE IF NOT EXISTS recipes (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    title VARCHAR(255) NOT NULL,
                    ingredients TEXT,
                    steps TEXT,
                    image_path VARCHAR(500),
                    author_id BIGINT NOT NULL,
                    author_name VARCHAR(255) NOT NULL,
                    tags TEXT,
                    cooking_time INT DEFAULT 0,
                    servings INT DEFAULT 1,
                    rating FLOAT DEFAULT 0.0,
                    is_published BOOLEAN DEFAULT FALSE,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE,
                    INDEX idx_author_id (author_id),
                    INDEX idx_created_at (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            ";
            
            // Tabla recipe_images
            $sqlImages = "
                CREATE TABLE IF NOT EXISTS recipe_images (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    recipe_id BIGINT NOT NULL,
                    image_data LONGBLOB NOT NULL,
                    description TEXT,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
                    INDEX idx_recipe_id (recipe_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            ";
            
            // Tabla recipe_votes
            $sqlVotes = "
                CREATE TABLE IF NOT EXISTS recipe_votes (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    recipe_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    vote_type TINYINT NOT NULL COMMENT '1 = me gusta, 0 = no me gusta',
                    created_at BIGINT NOT NULL,
                    FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    UNIQUE KEY unique_vote (recipe_id, user_id),
                    INDEX idx_recipe_id (recipe_id),
                    INDEX idx_user_id (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            ";
            
            // Tabla recipe_comments
            $sqlComments = "
                CREATE TABLE IF NOT EXISTS recipe_comments (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    recipe_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    comment_text TEXT NOT NULL,
                    created_at BIGINT NOT NULL,
                    FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    INDEX idx_recipe_id (recipe_id),
                    INDEX idx_user_id (user_id),
                    INDEX idx_created_at (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            ";
            
            // Tabla comment_likes
            $sqlLikes = "
                CREATE TABLE IF NOT EXISTS comment_likes (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    comment_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    created_at BIGINT NOT NULL,
                    FOREIGN KEY (comment_id) REFERENCES recipe_comments(id) ON DELETE CASCADE,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    UNIQUE KEY unique_like (comment_id, user_id),
                    INDEX idx_comment_id (comment_id),
                    INDEX idx_user_id (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            ";
            
            // Tabla comment_replies
            $sqlReplies = "
                CREATE TABLE IF NOT EXISTS comment_replies (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    comment_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    reply_text TEXT NOT NULL,
                    created_at BIGINT NOT NULL,
                    FOREIGN KEY (comment_id) REFERENCES recipe_comments(id) ON DELETE CASCADE,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    INDEX idx_comment_id (comment_id),
                    INDEX idx_user_id (user_id),
                    INDEX idx_created_at (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            ";
            
            // Ejecutar todas las creaciones
            $this->db->exec($sqlRecipes);
            $this->db->exec($sqlImages);
            $this->db->exec($sqlVotes);
            $this->db->exec($sqlComments);
            $this->db->exec($sqlLikes);
            $this->db->exec($sqlReplies);
            
            return true;
        } catch(PDOException $e) {
            throw new Exception("Error creando tablas: " . $e->getMessage());
        }
    }
    
    /**
     * Insertar una imagen de receta
     */
    public function insertRecipeImage($imageData) {
        $sql = "
            INSERT INTO recipe_images (recipe_id, image_data, description, created_at, updated_at)
            VALUES (:recipe_id, :image_data, :description, :created_at, :updated_at)
        ";
        
        try {
            $stmt = $this->db->prepare($sql);
            $result = $stmt->execute($imageData);
            
            if ($result) {
                return $this->db->lastInsertId();
            }
            return false;
        } catch(PDOException $e) {
            throw new Exception("Error insertando imagen: " . $e->getMessage());
        }
    }
    
    /**
     * Obtener imágenes de una receta
     */
    public function getRecipeImages($recipeId) {
        $sql = "SELECT * FROM recipe_images WHERE recipe_id = :recipe_id ORDER BY created_at ASC";
        
        try {
            $stmt = $this->db->prepare($sql);
            $stmt->execute(['recipe_id' => $recipeId]);
            return $stmt->fetchAll();
        } catch(PDOException $e) {
            throw new Exception("Error obteniendo imágenes: " . $e->getMessage());
        }
    }
    
    /**
     * Verificar si una imagen ya existe por recipe_id y created_at
     */
    public function imageExists($recipeId, $createdAt) {
        $sql = "SELECT COUNT(*) FROM recipe_images WHERE recipe_id = :recipe_id AND created_at = :created_at";
        
        try {
            $stmt = $this->db->prepare($sql);
            $stmt->execute([
                'recipe_id' => $recipeId,
                'created_at' => $createdAt
            ]);
            return $stmt->fetchColumn() > 0;
        } catch(PDOException $e) {
            throw new Exception("Error verificando imagen: " . $e->getMessage());
        }
    }
    
    /**
     * Insertar una receta
     */
    public function insertRecipe($recipeData) {
        $sql = "
            INSERT INTO recipes (title, ingredients, steps, image_path, author_id, author_name, tags, cooking_time, servings, rating, is_published, created_at, updated_at)
            VALUES (:title, :ingredients, :steps, :image_path, :author_id, :author_name, :tags, :cooking_time, :servings, :rating, :is_published, :created_at, :updated_at)
        ";
        
        try {
            $stmt = $this->db->prepare($sql);
            $result = $stmt->execute($recipeData);
            
            if ($result) {
                return $this->db->lastInsertId();
            }
            return false;
        } catch(PDOException $e) {
            throw new Exception("Error insertando receta: " . $e->getMessage());
        }
    }
    
    /**
     * Verificar si una receta ya existe por author_id, title y created_at
     */
    public function recipeExists($userId, $title, $createdAt) {
        $sql = "SELECT COUNT(*) FROM recipes WHERE author_id = :author_id AND title = :title AND created_at = :created_at";
        
        try {
            $stmt = $this->db->prepare($sql);
            $stmt->execute([
                'author_id' => $userId,
                'title' => $title,
                'created_at' => $createdAt
            ]);
            return $stmt->fetchColumn() > 0;
        } catch(PDOException $e) {
            throw new Exception("Error verificando receta: " . $e->getMessage());
        }
    }
    
    /**
     * Migrar recetas desde SQLite
     */
    public function migrateRecipes($recipes) {
        $migrated = 0;
        $errors = [];
        
        foreach ($recipes as $recipe) {
            try {
                // Verificar si la receta ya existe
                if ($this->recipeExists($recipe['userId'], $recipe['title'], $recipe['createdAt'])) {
                    $errors[] = "Receta '{$recipe['title']}' del usuario {$recipe['userId']} ya existe";
                    continue;
                }
                
                // Preparar datos para inserción
                $recipeData = [
                    'title' => $recipe['title'],
                    'ingredients' => $recipe['ingredients'],
                    'steps' => $recipe['steps'],
                    'image_path' => null, // Ya no usamos image_path, las imágenes van en recipe_images
                    'author_id' => $recipe['userId'],
                    'author_name' => $recipe['authorName'],
                    'tags' => $recipe['tags'] ?? null,
                    'cooking_time' => $recipe['cookingTime'] ?? 0,
                    'servings' => $recipe['servings'] ?? 1,
                    'rating' => $recipe['rating'] ?? 0.0,
                    'is_published' => $recipe['isPublished'] ?? false,
                    'created_at' => $recipe['createdAt'],
                    'updated_at' => $recipe['updatedAt']
                ];
                
                $this->insertRecipe($recipeData);
                $migrated++;
                
            } catch (Exception $e) {
                $errors[] = "Error migrando receta '{$recipe['title']}': " . $e->getMessage();
            }
        }
        
        return [
            'migrated' => $migrated,
            'errors' => $errors,
            'total' => count($recipes)
        ];
    }
    
    /**
     * Migrar imágenes de recetas desde SQLite
     */
    public function migrateRecipeImages($images) {
        $migrated = 0;
        $errors = [];
        
        foreach ($images as $image) {
            try {
                // Verificar si la imagen ya existe
                if ($this->imageExists($image['recipeId'], $image['createdAt'])) {
                    $errors[] = "Imagen de receta {$image['recipeId']} con timestamp {$image['createdAt']} ya existe";
                    continue;
                }
                
                // Preparar datos para inserción
                $imageData = [
                    'recipe_id' => $image['recipeId'],
                    'image_data' => base64_decode($image['imageData']), // Decodificar de base64
                    'description' => $image['description'] ?? null,
                    'created_at' => $image['createdAt'],
                    'updated_at' => $image['updatedAt']
                ];
                
                $this->insertRecipeImage($imageData);
                $migrated++;
                
            } catch (Exception $e) {
                $errors[] = "Error migrando imagen de receta {$image['recipeId']}: " . $e->getMessage();
            }
        }
        
        return [
            'migrated' => $migrated,
            'errors' => $errors,
            'total' => count($images)
        ];
    }
}
?>

