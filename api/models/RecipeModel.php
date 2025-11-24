<?php


require_once 'config/database.php';

class RecipeModel {
    private $db;
    
    public function __construct() {
        $database = new DatabaseConfig();
        $this->db = $database->getConnection();
    }
    

    public function createTables() {
        try {
            // Tabla recipes (publicaciones/recetas) - estructura actualizada sin image_path
            $sqlRecipes = "
                CREATE TABLE IF NOT EXISTS recipes (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    title VARCHAR(255) NOT NULL,
                    description TEXT,
                    ingredients TEXT,
                    steps TEXT,
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
            
            // Tabla recipe_images (sin campo description)
            $sqlImages = "
                CREATE TABLE IF NOT EXISTS recipe_images (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    recipe_id BIGINT NOT NULL,
                    image_data LONGBLOB NOT NULL,
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
                    vote_type TINYINT NOT NULL COMMENT '1 = me gusta, 0 = no me gusta',
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
            
            // Eliminar la columna description de recipe_images si existe (migración)
            $this->removeDescriptionColumn();
            
            return true;
        } catch(PDOException $e) {
            throw new Exception("Error creando tablas: " . $e->getMessage());
        }
    }
    
    /**
     * Insertar una imagen de receta (sin campo description)
     */
    public function insertRecipeImage($imageData) {
        $sql = "
            INSERT INTO recipe_images (recipe_id, image_data, created_at, updated_at)
            VALUES (:recipe_id, :image_data, :created_at, :updated_at)
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
     * Obtener imágenes de una receta (sin campo description)
     */
    public function getRecipeImages($recipeId) {
        $sql = "SELECT id, recipe_id, image_data, created_at, updated_at FROM recipe_images WHERE recipe_id = :recipe_id ORDER BY created_at ASC";
        
        try {
            $stmt = $this->db->prepare($sql);
            $stmt->execute(['recipe_id' => $recipeId]);
            return $stmt->fetchAll(PDO::FETCH_ASSOC);
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
            INSERT INTO recipes (title, description, ingredients, steps, author_id, author_name, tags, cooking_time, servings, is_published, created_at, updated_at)
            VALUES (:title, :description, :ingredients, :steps, :author_id, :author_name, :tags, :cooking_time, :servings, :is_published, :created_at, :updated_at)
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
     * Obtener una receta por ID con todas sus imágenes
     */
    public function getRecipeById($recipeId) {
        // CONSULTA 1: Obtener la receta desde la tabla recipes
        $sql = "
            SELECT 
                r.id,
                r.title,
                COALESCE(r.description, '') as description,
                COALESCE(r.ingredients, '') as ingredients,
                COALESCE(r.steps, '') as steps,
                r.author_id,
                COALESCE(CONCAT(u.name, ' ', u.last_name), r.author_name) AS author_name,
                COALESCE(u.alias, r.author_name) AS author_alias,
                COALESCE(u.avatar_path, '') AS author_avatar,
                COALESCE(r.cooking_time, 0) as cooking_time,
                COALESCE(r.servings, 1) as servings,
                COALESCE(r.rating, 0.0) as rating,
                COALESCE(r.is_published, 0) as is_published,
                r.created_at,
                r.updated_at
            FROM recipes r
            LEFT JOIN users u ON r.author_id = u.id
            WHERE r.id = :recipe_id
        ";
        
        try {
            error_log("=== CONSULTA 1: recipes ===");
            error_log("Buscando receta con ID: $recipeId");
            $stmt = $this->db->prepare($sql);
            $stmt->execute(['recipe_id' => $recipeId]);
            $recipe = $stmt->fetch(PDO::FETCH_ASSOC);
            
            if ($recipe) {
                error_log("✓ Receta encontrada en tabla recipes: {$recipe['title']}");
                error_log("  ID: {$recipe['id']}");
                error_log("  Autor ID: {$recipe['author_id']}");
                error_log("  Autor Nombre: " . ($recipe['author_name'] ?? 'NULL'));
                error_log("  Descripción: " . (isset($recipe['description']) ? substr($recipe['description'], 0, 50) . "..." : "NULL"));
                error_log("  Ingredientes: " . (isset($recipe['ingredients']) ? substr($recipe['ingredients'], 0, 50) . "..." : "NULL"));
                error_log("  Pasos: " . (isset($recipe['steps']) ? substr($recipe['steps'], 0, 50) . "..." : "NULL"));
                error_log("  Publicada: " . ($recipe['is_published'] ? 'true' : 'false'));
                
                // Asegurar que author_name no sea NULL
                if (empty($recipe['author_name'])) {
                    error_log("⚠️ WARNING: author_name está vacío, usando 'Autor desconocido'");
                    $recipe['author_name'] = 'Autor desconocido';
                }
                
                // Asegurar que is_published sea un booleano
                $recipe['is_published'] = (bool)(int)$recipe['is_published'];
                
                // CONSULTA 2: Obtener todas las imágenes de la receta desde recipe_images
                try {
                    error_log("=== CONSULTA 2: recipe_images ===");
                    error_log("Buscando imágenes para receta ID: $recipeId");
                    $images = $this->getRecipeImages($recipeId);
                    error_log("✓ Imágenes encontradas: " . count($images));
                    
                    // Convertir imágenes a Base64
                    $imagesArray = [];
                    foreach ($images as $image) {
                        if (isset($image['image_data']) && is_string($image['image_data']) && strlen($image['image_data']) > 0) {
                            $encoded = base64_encode($image['image_data']);
                            $encoded = str_replace(array("\r", "\n", " "), "", $encoded);
                            $imagesArray[] = $encoded;
                            error_log("Imagen codificada: " . strlen($encoded) . " caracteres");
                        }
                    }
                    
                    $recipe['images'] = $imagesArray;
                    $recipe['image_data'] = !empty($imagesArray) ? $imagesArray[0] : null;
                    
                    error_log("Total imágenes procesadas: " . count($imagesArray));
                } catch (Exception $e) {
                    error_log("Error obteniendo imágenes: " . $e->getMessage());
                    $recipe['images'] = [];
                    $recipe['image_data'] = null;
                }
                
                return $recipe;
            } else {
                error_log("✗ Receta NO encontrada con ID: $recipeId");
            }
            
            return null;
        } catch(PDOException $e) {
            error_log("ERROR PDO en getRecipeById: " . $e->getMessage());
            error_log("SQL State: " . $e->getCode());
            throw new Exception("Error obteniendo receta: " . $e->getMessage());
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
                    'description' => $recipe['description'] ?? '',
                    'ingredients' => $recipe['ingredients'],
                    'steps' => $recipe['steps'],
                    'author_id' => $recipe['userId'],
                    'author_name' => $recipe['authorName'],
                    'tags' => $recipe['tags'] ?? null,
                    'cooking_time' => $recipe['cookingTime'] ?? 0,
                    'servings' => $recipe['servings'] ?? 1,
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
                
                // Preparar datos para inserción (sin campo description)
                $imageData = [
                    'recipe_id' => $image['recipeId'],
                    'image_data' => base64_decode($image['imageData']), // Decodificar de base64
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
    
    /**
     * Obtener las últimas recetas publicadas para el feed
     * Ordenadas por fecha de creación descendente (más recientes primero)
     */
    public function getLatestRecipes($limit = 50) {
        // Aumentar límite de memoria para manejar imágenes grandes
        ini_set('memory_limit', '256M');
        
        error_log("=== RecipeModel::getLatestRecipes START (limit=$limit) ===");
        
        $sql = "
            SELECT 
                r.id,
                r.title,
                r.description,
                r.author_id,
                COALESCE(CONCAT(u.name, ' ', u.last_name), r.author_name) AS author_name,
                COALESCE(u.alias, r.author_name) AS author_alias,
                COALESCE(u.avatar_path, '') AS author_avatar,
                r.tags,
                r.cooking_time,
                r.servings,
                r.rating,
                r.created_at
            FROM recipes r
            LEFT JOIN users u ON r.author_id = u.id
            WHERE r.is_published = 1
            ORDER BY r.created_at DESC
            LIMIT :limit
        ";
        
        try {
            error_log("Ejecutando consulta SQL para obtener recetas...");
            $stmt = $this->db->prepare($sql);
            $stmt->bindValue(':limit', $limit, PDO::PARAM_INT);
            $stmt->execute();
            
            $recipes = $stmt->fetchAll(PDO::FETCH_ASSOC);
            error_log("Recetas encontradas: " . count($recipes));
            
            // Agregar TODAS las imágenes de cada receta
            foreach ($recipes as &$recipe) {
                try {
                    error_log("Procesando receta ID: " . $recipe['id']);
                    $allImages = $this->getRecipeImages($recipe['id']);
                    
                    if (!empty($allImages)) {
                        error_log("Imágenes encontradas para receta ID {$recipe['id']}: " . count($allImages));
                        
                        // Convertir todas las imágenes a Base64
                        $imagesArray = [];
                        foreach ($allImages as $image) {
                            if (isset($image['image_data']) && is_string($image['image_data']) && strlen($image['image_data']) > 0) {
                                // Codificar a Base64 sin saltos de línea
                                $encoded = base64_encode($image['image_data']);
                                // Asegurar que no haya saltos de línea ni espacios
                                $encoded = str_replace(array("\r", "\n", " "), "", $encoded);
                                $imagesArray[] = $encoded;
                            }
                        }
                        
                        $recipe['images'] = $imagesArray; // Array de todas las imágenes
                        $recipe['image_data'] = !empty($imagesArray) ? $imagesArray[0] : null; // Primera imagen para compatibilidad
                        error_log("Total imágenes procesadas: " . count($imagesArray));
                    } else {
                        error_log("No se encontraron imágenes para receta ID: " . $recipe['id']);
                        $recipe['images'] = [];
                        $recipe['image_data'] = null;
                    }
                } catch (Exception $e) {
                    error_log("ERROR procesando imágenes para receta " . $recipe['id'] . ": " . $e->getMessage());
                    $recipe['images'] = [];
                    $recipe['image_data'] = null;
                }
            }
            
            error_log("=== RecipeModel::getLatestRecipes END - Total: " . count($recipes) . " ===");
            return $recipes;
        } catch(PDOException $e) {
            error_log("ERROR PDO en getLatestRecipes: " . $e->getMessage());
            error_log("SQL State: " . $e->getCode());
            throw new Exception("Error obteniendo recetas: " . $e->getMessage());
        } catch(Exception $e) {
            error_log("ERROR GENERAL en getLatestRecipes: " . $e->getMessage());
            throw $e;
        }
    }
    
    /**
     * Obtener recetas publicadas por un usuario específico
     */
    public function getPublishedRecipesByUserId($userId) {
        ini_set('memory_limit', '256M');
        
        error_log("=== RecipeModel::getPublishedRecipesByUserId START (userId=$userId) ===");
        
        $sql = "
            SELECT 
                r.id,
                r.title,
                COALESCE(r.description, '') as description,
                COALESCE(r.ingredients, '') as ingredients,
                COALESCE(r.steps, '') as steps,
                r.author_id,
                COALESCE(CONCAT(u.name, ' ', u.last_name), r.author_name) AS author_name,
                COALESCE(u.alias, r.author_name) AS author_alias,
                COALESCE(u.avatar_path, '') AS author_avatar,
                COALESCE(r.tags, '') as tags,
                COALESCE(r.cooking_time, 0) as cooking_time,
                COALESCE(r.servings, 1) as servings,
                COALESCE(r.rating, 0.0) as rating,
                COALESCE(r.is_published, 0) as is_published,
                r.created_at,
                r.updated_at
            FROM recipes r
            LEFT JOIN users u ON r.author_id = u.id
            WHERE r.author_id = :author_id
              AND r.is_published = 1
            ORDER BY r.updated_at DESC, r.created_at DESC
        ";
        
        try {
            $stmt = $this->db->prepare($sql);
            $stmt->bindValue(':author_id', (int)$userId, PDO::PARAM_INT);
            $stmt->execute();
            
            $recipes = $stmt->fetchAll(PDO::FETCH_ASSOC);
            error_log("Recetas publicadas encontradas: " . count($recipes));
            
            foreach ($recipes as &$recipe) {
                $recipe['is_published'] = (bool)$recipe['is_published'];
                
                try {
                    $allImages = $this->getRecipeImages($recipe['id']);
                    
                    if (!empty($allImages)) {
                        $imagesArray = [];
                        foreach ($allImages as $image) {
                            if (isset($image['image_data']) && is_string($image['image_data']) && strlen($image['image_data']) > 0) {
                                $encoded = base64_encode($image['image_data']);
                                $encoded = str_replace(array("\r", "\n", " "), "", $encoded);
                                $imagesArray[] = $encoded;
                            }
                        }
                        $recipe['images'] = $imagesArray;
                        $recipe['image_data'] = !empty($imagesArray) ? $imagesArray[0] : null;
                    } else {
                        $recipe['images'] = [];
                        $recipe['image_data'] = null;
                    }
                } catch (Exception $e) {
                    error_log("ERROR procesando imágenes para receta {$recipe['id']}: " . $e->getMessage());
                    $recipe['images'] = [];
                    $recipe['image_data'] = null;
                }
            }
            unset($recipe);
            
            error_log("=== RecipeModel::getPublishedRecipesByUserId END ===");
            return $recipes;
        } catch(PDOException $e) {
            error_log("ERROR PDO en getPublishedRecipesByUserId: " . $e->getMessage());
            throw new Exception("Error obteniendo recetas del usuario: " . $e->getMessage());
        } catch(Exception $e) {
            error_log("ERROR GENERAL en getPublishedRecipesByUserId: " . $e->getMessage());
            throw $e;
        }
    }
    
    /**
     * Buscar recetas por título, descripción o nombre del usuario creador
     * Retorna recetas cuyo título, descripción o nombre del autor contiene el texto de búsqueda
     */
    public function searchRecipesByTitle($searchQuery, $limit = 50) {
        error_log("=== RecipeModel::searchRecipesByTitle START ===");
        error_log("Búsqueda: '$searchQuery', Límite: $limit");
        
        // Escapar caracteres especiales de SQL
        $searchPattern = '%' . $searchQuery . '%';
        
        $sql = "
            SELECT 
                r.id,
                r.title,
                COALESCE(r.description, '') as description,
                COALESCE(r.ingredients, '') as ingredients,
                COALESCE(r.steps, '') as steps,
                r.author_id,
                COALESCE(CONCAT(u.name, ' ', u.last_name), r.author_name) AS author_name,
                COALESCE(r.cooking_time, 0) as cooking_time,
                COALESCE(r.servings, 1) as servings,
                COALESCE(r.rating, 0.0) as rating,
                COALESCE(r.is_published, 0) as is_published,
                r.created_at,
                r.updated_at
            FROM recipes r
            LEFT JOIN users u ON r.author_id = u.id
            WHERE r.is_published = 1
            AND (
                r.title LIKE :search_query
                OR r.description LIKE :search_query
                OR COALESCE(CONCAT(u.name, ' ', u.last_name), r.author_name) LIKE :search_query
            )
            ORDER BY r.created_at DESC
            LIMIT :limit
        ";
        
        try {
            $stmt = $this->db->prepare($sql);
            $stmt->bindValue(':search_query', $searchPattern, PDO::PARAM_STR);
            $stmt->bindValue(':limit', (int)$limit, PDO::PARAM_INT);
            $stmt->execute();
            
            $recipes = $stmt->fetchAll(PDO::FETCH_ASSOC);
            
            error_log("✓ Recetas encontradas: " . count($recipes));
            
            // Para cada receta, obtener su primera imagen y conteos de votos
            foreach ($recipes as &$recipe) {
                try {
                    // Obtener todas las imágenes
                    $allImages = $this->getRecipeImages($recipe['id']);
                    
                    if (!empty($allImages)) {
                        $imagesArray = [];
                        foreach ($allImages as $image) {
                            if (isset($image['image_data']) && is_string($image['image_data']) && strlen($image['image_data']) > 0) {
                                $encoded = base64_encode($image['image_data']);
                                $encoded = str_replace(array("\r", "\n", " "), "", $encoded);
                                $imagesArray[] = $encoded;
                            }
                        }
                        $recipe['images'] = $imagesArray;
                        $recipe['image_data'] = !empty($imagesArray) ? $imagesArray[0] : null;
                    } else {
                        $recipe['images'] = [];
                        $recipe['image_data'] = null;
                    }
                    
                    // Obtener conteos de votos
                    $votesCount = $this->getRecipeVotesCount($recipe['id']);
                    $recipe['likes'] = $votesCount['likes'];
                    $recipe['dislikes'] = $votesCount['dislikes'];
                } catch (Exception $e) {
                    error_log("ERROR procesando receta {$recipe['id']}: " . $e->getMessage());
                    $recipe['images'] = [];
                    $recipe['image_data'] = null;
                    $recipe['likes'] = 0;
                    $recipe['dislikes'] = 0;
                }
            }
            
            error_log("=== RecipeModel::searchRecipesByTitle END - Total: " . count($recipes) . " ===");
            return $recipes;
            
        } catch(PDOException $e) {
            error_log("ERROR PDO en searchRecipesByTitle: " . $e->getMessage());
            error_log("SQL State: " . $e->getCode());
            throw new Exception("Error buscando recetas: " . $e->getMessage());
        } catch(Exception $e) {
            error_log("ERROR GENERAL en searchRecipesByTitle: " . $e->getMessage());
            throw $e;
        }
    }
    
    /**
     * Obtener solo la primera imagen de una receta
     */
    public function getFirstRecipeImage($recipeId) {
        $sql = "SELECT image_data FROM recipe_images WHERE recipe_id = :recipe_id ORDER BY created_at ASC LIMIT 1";
        
        try {
            $stmt = $this->db->prepare($sql);
            $stmt->execute(['recipe_id' => $recipeId]);
            $result = $stmt->fetch(PDO::FETCH_ASSOC);
            
            if ($result && isset($result['image_data'])) {
                error_log("Imagen obtenida para receta $recipeId - Tamaño: " . strlen($result['image_data']) . " bytes");
                return $result['image_data'];
            } else {
                error_log("No hay imagen para receta $recipeId");
                return null;
            }
        } catch(PDOException $e) {
            error_log("ERROR obteniendo imagen para receta $recipeId: " . $e->getMessage());
            // Si hay error obteniendo la imagen, no fallar la consulta principal
            return null;
        }
    }
    
    /**
     * Crear una receta con sus imágenes directamente en MySQL
     */
    public function createRecipeWithImages($recipeData, $images = []) {
        try {
            // Iniciar transacción
            $this->db->beginTransaction();
            
            // Insertar la receta
            $recipeId = $this->insertRecipe($recipeData);
            
            if (!$recipeId) {
                throw new Exception("Error al crear la receta");
            }
            
            // Insertar las imágenes si hay alguna (permite múltiples imágenes)
            $imagesInserted = 0;
            foreach ($images as $image) {
                // Decodificar base64 a binario
                $imageBinary = base64_decode($image['imageData']);
                
                $imageData = [
                    'recipe_id' => $recipeId,
                    'image_data' => $imageBinary,
                    'created_at' => $image['createdAt'] ?? time() * 1000,
                    'updated_at' => $image['updatedAt'] ?? time() * 1000
                ];
                
                $this->insertRecipeImage($imageData);
                $imagesInserted++;
            }
            
            // Confirmar transacción
            $this->db->commit();
            
            return [
                'recipeId' => $recipeId,
                'imagesInserted' => $imagesInserted
            ];
        } catch (Exception $e) {
            // Revertir transacción en caso de error
            $this->db->rollBack();
            throw new Exception("Error creando receta con imágenes: " . $e->getMessage());
        }
    }
    
    /**
     * Crear o actualizar un voto para una receta en la tabla recipe_votes
     */
    public function createVote($voteData) {
        try {
            // Obtener timestamp actual en milisegundos
            $createdAt = isset($voteData['createdAt']) ? (int)$voteData['createdAt'] : (int)(microtime(true) * 1000);
            
            // Validar que los valores requeridos estén presentes
            if (!isset($voteData['recipeId']) || !isset($voteData['userId']) || !isset($voteData['voteType'])) {
                throw new Exception("Faltan campos requeridos: recipeId, userId o voteType");
            }
            
            $recipeId = (int)$voteData['recipeId'];
            $userId = (int)$voteData['userId'];
            $voteType = (int)$voteData['voteType'];
            
            // Validar que voteType sea 0 o 1 (no -1, eso se maneja en deleteVote)
            if ($voteType != 0 && $voteType != 1) {
                throw new Exception("voteType debe ser 0 (dislike) o 1 (like), recibido: $voteType");
            }
            
            error_log("=== RecipeModel::createVote START ===");
            error_log("recipe_id: $recipeId, user_id: $userId, vote_type: $voteType, created_at: $createdAt");
            
            // Verificar que la receta y el usuario existan (para evitar errores de foreign key)
            $checkRecipeSql = "SELECT id FROM recipes WHERE id = :recipe_id LIMIT 1";
            $checkRecipeStmt = $this->db->prepare($checkRecipeSql);
            $checkRecipeStmt->execute(['recipe_id' => $recipeId]);
            $recipeExists = $checkRecipeStmt->fetch(PDO::FETCH_ASSOC);
            
            if (!$recipeExists) {
                error_log("✗ Error: La receta con ID $recipeId no existe");
                throw new Exception("La receta con ID $recipeId no existe");
            }
            
            $checkUserSql = "SELECT id FROM users WHERE id = :user_id LIMIT 1";
            $checkUserStmt = $this->db->prepare($checkUserSql);
            $checkUserStmt->execute(['user_id' => $userId]);
            $userExists = $checkUserStmt->fetch(PDO::FETCH_ASSOC);
            
            if (!$userExists) {
                error_log("✗ Error: El usuario con ID $userId no existe");
                throw new Exception("El usuario con ID $userId no existe");
            }
            
            error_log("✓ Receta y usuario verificados, procediendo con inserción/actualización");
            
            $sql = "
                INSERT INTO recipe_votes (recipe_id, user_id, vote_type, created_at)
                VALUES (:recipe_id, :user_id, :vote_type, :created_at)
                ON DUPLICATE KEY UPDATE 
                    vote_type = VALUES(vote_type),
                    created_at = VALUES(created_at)
            ";
            
            $stmt = $this->db->prepare($sql);
            $result = $stmt->execute([
                'recipe_id' => $recipeId,
                'user_id' => $userId,
                'vote_type' => $voteType,
                'created_at' => $createdAt
            ]);
            
            if ($result) {
                // Obtener el ID del voto (puede ser el insertado o el existente)
                $voteId = $this->db->lastInsertId();
                if ($voteId) {
                    error_log("✓ Voto creado/actualizado exitosamente con ID: $voteId");
                    return $voteId;
                } else {
                    // Si no hay lastInsertId (porque fue UPDATE), obtener el ID existente
                    $checkSql = "SELECT id FROM recipe_votes WHERE recipe_id = :recipe_id AND user_id = :user_id";
                    $checkStmt = $this->db->prepare($checkSql);
                    $checkStmt->execute(['recipe_id' => $recipeId, 'user_id' => $userId]);
                    $existingVote = $checkStmt->fetch(PDO::FETCH_ASSOC);
                    if ($existingVote) {
                        error_log("✓ Voto actualizado exitosamente con ID: {$existingVote['id']}");
                        return $existingVote['id'];
                    } else {
                        error_log("⚠️ Voto creado/actualizado pero no se pudo obtener el ID");
                        return true; // Retornar true para indicar éxito
                    }
                }
            } else {
                error_log("✗ Error: execute() retornó false");
                return false;
            }
        } catch(PDOException $e) {
            error_log("✗ PDOException en createVote: " . $e->getMessage());
            error_log("SQL State: " . $e->getCode());
            if (isset($stmt)) {
                error_log("Error Info: " . print_r($stmt->errorInfo() ?? [], true));
            }
            throw new Exception("Error creando voto: " . $e->getMessage());
        } catch(Exception $e) {
            error_log("✗ Exception en createVote: " . $e->getMessage());
            throw $e;
        }
    }
    
    /**
     * Eliminar un voto de la tabla recipe_votes
     */
    public function deleteVote($recipeId, $userId) {
        try {
            error_log("=== RecipeModel::deleteVote START ===");
            error_log("recipe_id: $recipeId, user_id: $userId");
            
            $sql = "DELETE FROM recipe_votes WHERE recipe_id = :recipe_id AND user_id = :user_id";
            
            $stmt = $this->db->prepare($sql);
            $result = $stmt->execute([
                'recipe_id' => (int)$recipeId,
                'user_id' => (int)$userId
            ]);
            
            if ($result) {
                $rowCount = $stmt->rowCount();
                error_log("✓ Delete ejecutado, filas afectadas: $rowCount");
                return $rowCount > 0; // Retorna true si se eliminó al menos una fila
            } else {
                error_log("✗ Error: execute() retornó false");
                return false;
            }
        } catch(PDOException $e) {
            error_log("✗ PDOException en deleteVote: " . $e->getMessage());
            throw new Exception("Error eliminando voto: " . $e->getMessage());
        }
    }
    
    /**
     * Obtener las recetas a las que un usuario le dio like (vote_type = 1)
     */
    public function getLikedRecipesByUserId($userId) {
        // Aumentar límite de memoria para manejar imágenes grandes
        ini_set('memory_limit', '256M');
        
        error_log("=== RecipeModel::getLikedRecipesByUserId START ===");
        error_log("User ID recibido: $userId (tipo: " . gettype($userId) . ")");
        
        // Validar que el userId sea válido
        if (!isset($userId) || $userId <= 0) {
            error_log("ERROR: User ID inválido: $userId");
            throw new Exception("User ID inválido: $userId");
        }
        
        // Consulta SQL: obtener recetas a las que el usuario le dio like (vote_type = 1)
        $sql = "
            SELECT 
                r.id,
                r.title,
                r.description,
                r.author_id,
                COALESCE(CONCAT(u.name, ' ', u.last_name), r.author_name) AS author_name,
                COALESCE(u.alias, r.author_name) AS author_alias,
                COALESCE(u.avatar_path, '') AS author_avatar,
                r.tags,
                r.cooking_time,
                r.servings,
                r.rating,
                r.created_at,
                rv.created_at as liked_at
            FROM recipes r
            INNER JOIN recipe_votes rv ON r.id = rv.recipe_id
            LEFT JOIN users u ON r.author_id = u.id
            WHERE rv.user_id = :user_id 
                AND rv.vote_type = 1
                AND r.is_published = 1
            ORDER BY rv.created_at DESC
        ";
        
        try {
            error_log("Ejecutando consulta SQL:");
            error_log("  - Tabla: recipe_votes");
            error_log("  - Condición: user_id = $userId AND vote_type = 1");
            
            $stmt = $this->db->prepare($sql);
            $stmt->execute(['user_id' => (int)$userId]); // Asegurar que sea entero
            
            $recipes = $stmt->fetchAll(PDO::FETCH_ASSOC);
            error_log("✓ Recetas con like encontradas en recipe_votes: " . count($recipes));
            
            // Log detallado de los primeros resultados
            if (count($recipes) > 0) {
                error_log("Primeras recetas encontradas:");
                foreach (array_slice($recipes, 0, 3) as $index => $recipe) {
                    error_log("  Receta " . ($index + 1) . ": ID={$recipe['id']}, Título={$recipe['title']}");
                }
            } else {
                // Verificar si hay votos para este usuario (aunque sean vote_type = 0)
                $checkSql = "SELECT COUNT(*) as total FROM recipe_votes WHERE user_id = :user_id";
                $checkStmt = $this->db->prepare($checkSql);
                $checkStmt->execute(['user_id' => (int)$userId]);
                $checkResult = $checkStmt->fetch(PDO::FETCH_ASSOC);
                $totalVotes = $checkResult['total'] ?? 0;
                
                error_log("⚠️ No se encontraron recetas con like (vote_type = 1)");
                error_log("  - Total votos (todos los tipos) para user_id=$userId: $totalVotes");
                
                if ($totalVotes > 0) {
                    // Verificar cuántos son vote_type = 1 vs 0
                    $checkTypeSql = "SELECT vote_type, COUNT(*) as count FROM recipe_votes WHERE user_id = :user_id GROUP BY vote_type";
                    $checkTypeStmt = $this->db->prepare($checkTypeSql);
                    $checkTypeStmt->execute(['user_id' => (int)$userId]);
                    $voteTypes = $checkTypeStmt->fetchAll(PDO::FETCH_ASSOC);
                    foreach ($voteTypes as $vt) {
                        error_log("    - vote_type {$vt['vote_type']}: {$vt['count']} votos");
                    }
                }
            }
            
            // Agregar todas las imágenes de cada receta
            foreach ($recipes as &$recipe) {
                try {
                    error_log("Procesando receta ID: " . $recipe['id']);
                    $allImages = $this->getRecipeImages($recipe['id']);
                    
                    if (!empty($allImages)) {
                        error_log("Imágenes encontradas para receta ID {$recipe['id']}: " . count($allImages));
                        
                        // Convertir todas las imágenes a Base64
                        $imagesArray = [];
                        foreach ($allImages as $image) {
                            if (isset($image['image_data']) && is_string($image['image_data']) && strlen($image['image_data']) > 0) {
                                // Codificar a Base64 sin saltos de línea
                                $encoded = base64_encode($image['image_data']);
                                // Asegurar que no haya saltos de línea ni espacios
                                $encoded = str_replace(array("\r", "\n", " "), "", $encoded);
                                $imagesArray[] = $encoded;
                            }
                        }
                        
                        $recipe['images'] = $imagesArray; // Array de todas las imágenes
                        $recipe['image_data'] = !empty($imagesArray) ? $imagesArray[0] : null; // Primera imagen para compatibilidad
                        error_log("Total imágenes procesadas: " . count($imagesArray));
                    } else {
                        error_log("No se encontraron imágenes para receta ID: " . $recipe['id']);
                        $recipe['images'] = [];
                        $recipe['image_data'] = null;
                    }
                } catch (Exception $e) {
                    error_log("ERROR procesando imágenes para receta " . $recipe['id'] . ": " . $e->getMessage());
                    $recipe['images'] = [];
                    $recipe['image_data'] = null;
                }
            }
            
            error_log("=== RecipeModel::getLikedRecipesByUserId END - Total: " . count($recipes) . " ===");
            return $recipes;
        } catch(PDOException $e) {
            error_log("ERROR PDO en getLikedRecipesByUserId: " . $e->getMessage());
            error_log("SQL State: " . $e->getCode());
            throw new Exception("Error obteniendo recetas con like: " . $e->getMessage());
        } catch(Exception $e) {
            error_log("ERROR GENERAL en getLikedRecipesByUserId: " . $e->getMessage());
            throw $e;
        }
    }
    
    /**
     * Eliminar la columna description de recipe_images si existe
     */
    public function removeDescriptionColumn() {
        try {
            // Verificar si la columna existe antes de eliminarla
            $checkSql = "SELECT COUNT(*) as count 
                        FROM information_schema.COLUMNS 
                        WHERE TABLE_SCHEMA = DATABASE() 
                        AND TABLE_NAME = 'recipe_images' 
                        AND COLUMN_NAME = 'description'";
            
            $stmt = $this->db->prepare($checkSql);
            $stmt->execute();
            $result = $stmt->fetch(PDO::FETCH_ASSOC);
            
            if ($result && $result['count'] > 0) {
                // La columna existe, eliminarla
                $alterSql = "ALTER TABLE recipe_images DROP COLUMN description";
                $this->db->exec($alterSql);
                error_log("Columna 'description' eliminada de recipe_images");
                return true;
            } else {
                error_log("Columna 'description' no existe en recipe_images, no es necesario eliminarla");
                return false;
            }
        } catch (PDOException $e) {
            // Si hay error, puede ser que la columna no exista o que la tabla no exista
            error_log("Error eliminando columna description: " . $e->getMessage());
            return false;
        }
    }
    
    /**
     * Insertar un comentario en MySQL
     */
    public function insertComment($recipeId, $userId, $commentText) {
        error_log("=== RecipeModel::insertComment START ===");
        error_log("recipe_id: $recipeId, user_id: $userId, comment_text: " . substr($commentText, 0, 50) . "...");
        
        $sql = "
            INSERT INTO recipe_comments (recipe_id, user_id, comment_text, created_at)
            VALUES (:recipe_id, :user_id, :comment_text, :created_at)
        ";
        
        try {
            $createdAt = round(microtime(true) * 1000); // Timestamp en milisegundos
            
            $stmt = $this->db->prepare($sql);
            $result = $stmt->execute([
                'recipe_id' => (int)$recipeId,
                'user_id' => (int)$userId,
                'comment_text' => $commentText,
                'created_at' => $createdAt
            ]);
            
            if ($result) {
                $commentId = $this->db->lastInsertId();
                error_log("✓ Comentario insertado exitosamente con ID: $commentId");
                return $commentId;
            } else {
                error_log("✗ Error: No se pudo insertar el comentario");
                return false;
            }
        } catch(PDOException $e) {
            error_log("ERROR PDO en insertComment: " . $e->getMessage());
            throw new Exception("Error insertando comentario: " . $e->getMessage());
        } catch(Exception $e) {
            error_log("ERROR GENERAL en insertComment: " . $e->getMessage());
            throw $e;
        }
    }
    
    /**
     * Obtener todos los comentarios de una receta desde MySQL
     */
    public function getCommentsByRecipeId($recipeId) {
        error_log("=== RecipeModel::getCommentsByRecipeId START (recipe_id=$recipeId) ===");
        
        $sql = "
            SELECT 
                c.id,
                c.recipe_id,
                c.user_id,
                c.comment_text,
                c.created_at,
                u.name,
                u.last_name,
                u.email,
                u.alias
            FROM recipe_comments c
            INNER JOIN users u ON c.user_id = u.id
            WHERE c.recipe_id = :recipe_id
            ORDER BY c.created_at ASC
        ";
        
        try {
            error_log("Ejecutando consulta SQL para obtener comentarios...");
            $stmt = $this->db->prepare($sql);
            $stmt->execute(['recipe_id' => (int)$recipeId]);
            
            $comments = $stmt->fetchAll(PDO::FETCH_ASSOC);
            error_log("✓ Comentarios encontrados: " . count($comments));
            
            // Log detallado de cada comentario encontrado
            foreach ($comments as $index => $comment) {
                error_log("  Comentario " . ($index + 1) . ":");
                error_log("    ID: {$comment['id']}");
                error_log("    Usuario ID: {$comment['user_id']}");
                error_log("    Usuario: {$comment['name']} {$comment['last_name']}");
                error_log("    Texto: " . substr($comment['comment_text'], 0, 50) . "...");
                error_log("    Creado: {$comment['created_at']}");
            }
            
            return $comments;
        } catch(PDOException $e) {
            error_log("ERROR PDO en getCommentsByRecipeId: " . $e->getMessage());
            error_log("SQL State: " . $e->getCode());
            throw new Exception("Error obteniendo comentarios: " . $e->getMessage());
        } catch(Exception $e) {
            error_log("ERROR GENERAL en getCommentsByRecipeId: " . $e->getMessage());
            throw $e;
        }
    }
    
    /**
     * Obtener conteos de likes y dislikes de una receta desde recipe_votes
     * Retorna un array con 'likes' (vote_type = 1) y 'dislikes' (vote_type = 0)
     */
    public function getRecipeVotesCount($recipeId) {
        error_log("=== RecipeModel::getRecipeVotesCount START (recipe_id=$recipeId) ===");
        
        $sql = "
            SELECT 
                vote_type,
                COUNT(*) as count
            FROM recipe_votes
            WHERE recipe_id = :recipe_id
            GROUP BY vote_type
        ";
        
        try {
            $stmt = $this->db->prepare($sql);
            $stmt->execute(['recipe_id' => (int)$recipeId]);
            
            $results = $stmt->fetchAll(PDO::FETCH_ASSOC);
            
            $likes = 0;
            $dislikes = 0;
            
            foreach ($results as $row) {
                if ($row['vote_type'] == 1) {
                    $likes = (int)$row['count'];
                } elseif ($row['vote_type'] == 0) {
                    $dislikes = (int)$row['count'];
                }
            }
            
            error_log("✓ Conteos obtenidos - Likes: $likes, Dislikes: $dislikes");
            
            return [
                'likes' => $likes,
                'dislikes' => $dislikes
            ];
        } catch(PDOException $e) {
            error_log("ERROR PDO en getRecipeVotesCount: " . $e->getMessage());
            throw new Exception("Error obteniendo conteos de votos: " . $e->getMessage());
        } catch(Exception $e) {
            error_log("ERROR GENERAL en getRecipeVotesCount: " . $e->getMessage());
            throw $e;
        }
    }
    
    /**
     * Crear o actualizar un voto para un comentario en la tabla comment_likes
     */
    public function createCommentVote($voteData) {
        try {
            // Obtener timestamp actual en milisegundos
            $createdAt = isset($voteData['createdAt']) ? (int)$voteData['createdAt'] : (int)(microtime(true) * 1000);
            
            // Validar que los valores requeridos estén presentes
            if (!isset($voteData['commentId']) || !isset($voteData['userId']) || !isset($voteData['voteType'])) {
                throw new Exception("Faltan campos requeridos: commentId, userId o voteType");
            }
            
            $commentId = (int)$voteData['commentId'];
            $userId = (int)$voteData['userId'];
            $voteType = (int)$voteData['voteType'];
            
            // Validar que voteType sea 0 o 1 (no -1, eso se maneja en deleteCommentVote)
            if ($voteType != 0 && $voteType != 1) {
                throw new Exception("voteType debe ser 0 (dislike) o 1 (like), recibido: $voteType");
            }
            
            error_log("=== RecipeModel::createCommentVote START ===");
            error_log("comment_id: $commentId, user_id: $userId, vote_type: $voteType, created_at: $createdAt");
            
            // Verificar que el comentario y el usuario existan
            $checkCommentSql = "SELECT id FROM recipe_comments WHERE id = :comment_id LIMIT 1";
            $checkCommentStmt = $this->db->prepare($checkCommentSql);
            $checkCommentStmt->execute(['comment_id' => $commentId]);
            $commentExists = $checkCommentStmt->fetch(PDO::FETCH_ASSOC);
            
            if (!$commentExists) {
                error_log("✗ Error: El comentario con ID $commentId no existe");
                throw new Exception("El comentario con ID $commentId no existe");
            }
            
            $checkUserSql = "SELECT id FROM users WHERE id = :user_id LIMIT 1";
            $checkUserStmt = $this->db->prepare($checkUserSql);
            $checkUserStmt->execute(['user_id' => $userId]);
            $userExists = $checkUserStmt->fetch(PDO::FETCH_ASSOC);
            
            if (!$userExists) {
                error_log("✗ Error: El usuario con ID $userId no existe");
                throw new Exception("El usuario con ID $userId no existe");
            }
            
            error_log("✓ Comentario y usuario verificados, procediendo con inserción/actualización");
            
            $sql = "
                INSERT INTO comment_likes (comment_id, user_id, vote_type, created_at)
                VALUES (:comment_id, :user_id, :vote_type, :created_at)
                ON DUPLICATE KEY UPDATE 
                    vote_type = VALUES(vote_type),
                    created_at = VALUES(created_at)
            ";
            
            $stmt = $this->db->prepare($sql);
            $result = $stmt->execute([
                'comment_id' => $commentId,
                'user_id' => $userId,
                'vote_type' => $voteType,
                'created_at' => $createdAt
            ]);
            
            if ($result) {
                // Obtener el ID del voto (puede ser el insertado o el existente)
                $voteId = $this->db->lastInsertId();
                if ($voteId) {
                    error_log("✓ Voto de comentario creado/actualizado exitosamente con ID: $voteId");
                    return $voteId;
                } else {
                    // Si no hay lastInsertId (porque fue UPDATE), obtener el ID existente
                    $checkSql = "SELECT id FROM comment_likes WHERE comment_id = :comment_id AND user_id = :user_id";
                    $checkStmt = $this->db->prepare($checkSql);
                    $checkStmt->execute(['comment_id' => $commentId, 'user_id' => $userId]);
                    $existingVote = $checkStmt->fetch(PDO::FETCH_ASSOC);
                    if ($existingVote) {
                        error_log("✓ Voto de comentario actualizado exitosamente con ID: {$existingVote['id']}");
                        return $existingVote['id'];
                    } else {
                        error_log("⚠️ Voto de comentario creado/actualizado pero no se pudo obtener el ID");
                        return true; // Retornar true para indicar éxito
                    }
                }
            } else {
                error_log("✗ Error: execute() retornó false");
                return false;
            }
        } catch(PDOException $e) {
            error_log("✗ PDOException en createCommentVote: " . $e->getMessage());
            error_log("SQL State: " . $e->getCode());
            if (isset($stmt)) {
                error_log("Error Info: " . print_r($stmt->errorInfo() ?? [], true));
            }
            throw new Exception("Error creando voto de comentario: " . $e->getMessage());
        } catch(Exception $e) {
            error_log("✗ Exception en createCommentVote: " . $e->getMessage());
            throw $e;
        }
    }
    
    /**
     * Eliminar un voto de comentario de la tabla comment_likes
     */
    public function deleteCommentVote($commentId, $userId) {
        try {
            error_log("=== RecipeModel::deleteCommentVote START ===");
            error_log("comment_id: $commentId, user_id: $userId");
            
            $sql = "DELETE FROM comment_likes WHERE comment_id = :comment_id AND user_id = :user_id";
            
            $stmt = $this->db->prepare($sql);
            $result = $stmt->execute([
                'comment_id' => (int)$commentId,
                'user_id' => (int)$userId
            ]);
            
            if ($result) {
                $rowCount = $stmt->rowCount();
                error_log("✓ Delete ejecutado, filas afectadas: $rowCount");
                return $rowCount > 0; // Retorna true si se eliminó al menos una fila
            } else {
                error_log("✗ Error: execute() retornó false");
                return false;
            }
        } catch(PDOException $e) {
            error_log("✗ PDOException en deleteCommentVote: " . $e->getMessage());
            throw new Exception("Error eliminando voto de comentario: " . $e->getMessage());
        }
    }
    
    /**
     * Obtener conteos de likes y dislikes de un comentario desde comment_likes
     * Retorna un array con 'likes' (vote_type = 1) y 'dislikes' (vote_type = 0)
     */
    public function getCommentVotesCount($commentId) {
        error_log("=== RecipeModel::getCommentVotesCount START (comment_id=$commentId) ===");
        
        $sql = "
            SELECT 
                vote_type,
                COUNT(*) as count
            FROM comment_likes
            WHERE comment_id = :comment_id
            GROUP BY vote_type
        ";
        
        try {
            $stmt = $this->db->prepare($sql);
            $stmt->execute(['comment_id' => (int)$commentId]);
            
            $results = $stmt->fetchAll(PDO::FETCH_ASSOC);
            
            $likes = 0;
            $dislikes = 0;
            
            foreach ($results as $row) {
                if ($row['vote_type'] == 1) {
                    $likes = (int)$row['count'];
                } elseif ($row['vote_type'] == 0) {
                    $dislikes = (int)$row['count'];
                }
            }
            
            error_log("✓ Conteos obtenidos - Likes: $likes, Dislikes: $dislikes");
            return [
                'likes' => $likes,
                'dislikes' => $dislikes
            ];
        } catch(PDOException $e) {
            error_log("✗ PDOException en getCommentVotesCount: " . $e->getMessage());
            throw new Exception("Error obteniendo conteos de votos del comentario: " . $e->getMessage());
        }
    }
    
    /**
     * Crear una respuesta a un comentario
     */
    public function createCommentReply($replyData) {
        try {
            error_log("=== RecipeModel::createCommentReply START ===");
            
            // Validar campos requeridos
            if (!isset($replyData['commentId']) || !isset($replyData['userId']) || !isset($replyData['replyText'])) {
                throw new Exception("Faltan campos requeridos: commentId, userId o replyText");
            }
            
            $commentId = (int)$replyData['commentId'];
            $userId = (int)$replyData['userId'];
            $replyText = trim($replyData['replyText']);
            $createdAt = isset($replyData['createdAt']) ? (int)$replyData['createdAt'] : (int)(microtime(true) * 1000);
            
            // Validar que el texto no esté vacío
            if (empty($replyText)) {
                throw new Exception("El texto de la respuesta no puede estar vacío");
            }
            
            error_log("comment_id: $commentId, user_id: $userId, reply_text: " . substr($replyText, 0, 50) . "...");
            
            // Verificar que el comentario y el usuario existan
            $checkCommentSql = "SELECT id FROM recipe_comments WHERE id = :comment_id LIMIT 1";
            $checkCommentStmt = $this->db->prepare($checkCommentSql);
            $checkCommentStmt->execute(['comment_id' => $commentId]);
            $commentExists = $checkCommentStmt->fetch(PDO::FETCH_ASSOC);
            
            if (!$commentExists) {
                error_log("✗ Error: El comentario con ID $commentId no existe");
                throw new Exception("El comentario con ID $commentId no existe");
            }
            
            $checkUserSql = "SELECT id FROM users WHERE id = :user_id LIMIT 1";
            $checkUserStmt = $this->db->prepare($checkUserSql);
            $checkUserStmt->execute(['user_id' => $userId]);
            $userExists = $checkUserStmt->fetch(PDO::FETCH_ASSOC);
            
            if (!$userExists) {
                error_log("✗ Error: El usuario con ID $userId no existe");
                throw new Exception("El usuario con ID $userId no existe");
            }
            
            error_log("✓ Comentario y usuario verificados, procediendo con inserción");
            
            $sql = "
                INSERT INTO comment_replies (comment_id, user_id, reply_text, created_at)
                VALUES (:comment_id, :user_id, :reply_text, :created_at)
            ";
            
            $stmt = $this->db->prepare($sql);
            $result = $stmt->execute([
                'comment_id' => $commentId,
                'user_id' => $userId,
                'reply_text' => $replyText,
                'created_at' => $createdAt
            ]);
            
            if ($result) {
                $replyId = $this->db->lastInsertId();
                error_log("✓ Respuesta creada exitosamente con ID: $replyId");
                return $replyId;
            } else {
                error_log("✗ Error: execute() retornó false");
                return false;
            }
        } catch(PDOException $e) {
            error_log("✗ PDOException en createCommentReply: " . $e->getMessage());
            error_log("SQL State: " . $e->getCode());
            if (isset($stmt)) {
                error_log("Error Info: " . print_r($stmt->errorInfo() ?? [], true));
            }
            throw new Exception("Error creando respuesta: " . $e->getMessage());
        } catch(Exception $e) {
            error_log("✗ Exception en createCommentReply: " . $e->getMessage());
            throw $e;
        }
    }
    
    /**
     * Obtener todas las respuestas de un comentario
     */
    public function getRepliesByCommentId($commentId) {
        error_log("=== RecipeModel::getRepliesByCommentId START (comment_id=$commentId) ===");
        
        $sql = "
            SELECT 
                r.id,
                r.comment_id,
                r.user_id,
                r.reply_text,
                r.created_at,
                u.name,
                u.last_name,
                u.email,
                u.alias
            FROM comment_replies r
            INNER JOIN users u ON r.user_id = u.id
            WHERE r.comment_id = :comment_id
            ORDER BY r.created_at ASC
        ";
        
        try {
            error_log("Ejecutando consulta SQL para obtener respuestas...");
            $stmt = $this->db->prepare($sql);
            $stmt->execute(['comment_id' => (int)$commentId]);
            
            $replies = $stmt->fetchAll(PDO::FETCH_ASSOC);
            error_log("✓ Respuestas encontradas: " . count($replies));
            
            // Log detallado de cada respuesta encontrada
            foreach ($replies as $index => $reply) {
                error_log("  Respuesta " . ($index + 1) . ":");
                error_log("    ID: {$reply['id']}");
                error_log("    Usuario ID: {$reply['user_id']}");
                error_log("    Usuario: {$reply['name']} {$reply['last_name']}");
                error_log("    Texto: " . substr($reply['reply_text'], 0, 50) . "...");
                error_log("    Creado: {$reply['created_at']}");
            }
            
            return $replies;
        } catch(PDOException $e) {
            error_log("ERROR PDO en getRepliesByCommentId: " . $e->getMessage());
            error_log("SQL State: " . $e->getCode());
            throw new Exception("Error obteniendo respuestas: " . $e->getMessage());
        } catch(Exception $e) {
            error_log("ERROR GENERAL en getRepliesByCommentId: " . $e->getMessage());
            throw $e;
        }
    }
    
    /**
     * Crear o actualizar un voto para una respuesta (reply) en la tabla reply_likes
     * voteType: 1 = me gusta, 0 = no me gusta (dislike)
     * Si el usuario ya votó, se actualiza el voto
     * Retorna el ID del voto creado/actualizado o false en caso de error
     */
    public function createReplyVote($voteData) {
        try {
            error_log("=== RecipeModel::createReplyVote START ===");
            if (!isset($voteData['replyId']) || !isset($voteData['userId']) || !isset($voteData['voteType'])) {
                throw new Exception("Faltan campos requeridos: replyId, userId o voteType");
            }
            
            $replyId = (int)$voteData['replyId'];
            $userId = (int)$voteData['userId'];
            $voteType = (int)$voteData['voteType'];
            $createdAt = isset($voteData['createdAt']) ? (int)$voteData['createdAt'] : (int)(microtime(true) * 1000);
            
            error_log("reply_id: $replyId, user_id: $userId, vote_type: $voteType");
            
            // Validar que voteType sea 0 o 1
            if ($voteType != 0 && $voteType != 1) {
                throw new Exception("voteType debe ser 0 (dislike) o 1 (like)");
            }
            
            // Verificar que el reply existe
            $checkReplySql = "SELECT id FROM comment_replies WHERE id = :reply_id LIMIT 1";
            $checkReplyStmt = $this->db->prepare($checkReplySql);
            $checkReplyStmt->execute(['reply_id' => $replyId]);
            $replyExists = $checkReplyStmt->fetch(PDO::FETCH_ASSOC);
            
            if (!$replyExists) {
                error_log("✗ Error: El reply con ID $replyId no existe");
                throw new Exception("El reply con ID $replyId no existe");
            }
            
            // Verificar que el usuario existe
            $checkUserSql = "SELECT id FROM users WHERE id = :user_id LIMIT 1";
            $checkUserStmt = $this->db->prepare($checkUserSql);
            $checkUserStmt->execute(['user_id' => $userId]);
            $userExists = $checkUserStmt->fetch(PDO::FETCH_ASSOC);
            
            if (!$userExists) {
                error_log("✗ Error: El usuario con ID $userId no existe");
                throw new Exception("El usuario con ID $userId no existe");
            }
            
            error_log("✓ Reply y usuario verificados, procediendo con inserción/actualización");
            
            // Usar INSERT ... ON DUPLICATE KEY UPDATE para insertar o actualizar
            $sql = "
                INSERT INTO reply_likes (reply_id, user_id, vote_type, created_at)
                VALUES (:reply_id, :user_id, :vote_type, :created_at)
                ON DUPLICATE KEY UPDATE
                    vote_type = VALUES(vote_type),
                    created_at = VALUES(created_at)
            ";
            
            $stmt = $this->db->prepare($sql);
            $result = $stmt->execute([
                'reply_id' => $replyId,
                'user_id' => $userId,
                'vote_type' => $voteType,
                'created_at' => $createdAt
            ]);
            
            if ($result) {
                $voteId = $this->db->lastInsertId();
                // Si lastInsertId() retorna 0, significa que fue un UPDATE, buscar el ID
                if ($voteId == 0) {
                    $getIdSql = "SELECT id FROM reply_likes WHERE reply_id = :reply_id AND user_id = :user_id LIMIT 1";
                    $getIdStmt = $this->db->prepare($getIdSql);
                    $getIdStmt->execute(['reply_id' => $replyId, 'user_id' => $userId]);
                    $existingVote = $getIdStmt->fetch(PDO::FETCH_ASSOC);
                    $voteId = $existingVote ? $existingVote['id'] : true;
                }
                error_log("✓ Voto de reply guardado exitosamente con ID: $voteId");
                return $voteId ?: true;
            } else {
                error_log("✗ Error: execute() retornó false");
                return false;
            }
        } catch(PDOException $e) {
            error_log("✗ PDOException en createReplyVote: " . $e->getMessage());
            error_log("SQL State: " . $e->getCode());
            if (isset($stmt)) {
                error_log("Error Info: " . print_r($stmt->errorInfo() ?? [], true));
            }
            throw new Exception("Error guardando voto de reply: " . $e->getMessage());
        } catch(Exception $e) {
            error_log("✗ Exception en createReplyVote: " . $e->getMessage());
            throw $e;
        }
    }
    
    /**
     * Eliminar un voto de reply de la tabla reply_likes
     */
    public function deleteReplyVote($replyId, $userId) {
        try {
            error_log("=== RecipeModel::deleteReplyVote START ===");
            error_log("reply_id: $replyId, user_id: $userId");
            
            $sql = "DELETE FROM reply_likes WHERE reply_id = :reply_id AND user_id = :user_id";
            
            $stmt = $this->db->prepare($sql);
            $result = $stmt->execute([
                'reply_id' => (int)$replyId,
                'user_id' => (int)$userId
            ]);
            
            if ($result) {
                $rowCount = $stmt->rowCount();
                error_log("✓ Delete ejecutado, filas afectadas: $rowCount");
                return $rowCount > 0; // Retorna true si se eliminó al menos una fila
            } else {
                error_log("✗ Error: execute() retornó false");
                return false;
            }
        } catch(PDOException $e) {
            error_log("✗ PDOException en deleteReplyVote: " . $e->getMessage());
            throw new Exception("Error eliminando voto de reply: " . $e->getMessage());
        }
    }
    
    /**
     * Obtener conteos de likes y dislikes de una respuesta desde reply_likes
     * Retorna un array con 'likes' (vote_type = 1) y 'dislikes' (vote_type = 0)
     */
    public function getReplyVotesCount($replyId) {
        error_log("=== RecipeModel::getReplyVotesCount START (reply_id=$replyId) ===");
        
        $sql = "
            SELECT 
                vote_type,
                COUNT(*) as count
            FROM reply_likes
            WHERE reply_id = :reply_id
            GROUP BY vote_type
        ";
        
        try {
            $stmt = $this->db->prepare($sql);
            $stmt->execute(['reply_id' => (int)$replyId]);
            $results = $stmt->fetchAll(PDO::FETCH_ASSOC);
            
            $likes = 0;
            $dislikes = 0;
            
            foreach ($results as $row) {
                if ($row['vote_type'] == 1) {
                    $likes = (int)$row['count'];
                } else if ($row['vote_type'] == 0) {
                    $dislikes = (int)$row['count'];
                }
            }
            
            error_log("✓ Conteos obtenidos: likes=$likes, dislikes=$dislikes");
            return [
                'likes' => $likes,
                'dislikes' => $dislikes
            ];
        } catch(PDOException $e) {
            error_log("✗ PDOException en getReplyVotesCount: " . $e->getMessage());
            throw new Exception("Error obteniendo conteos de votos de reply: " . $e->getMessage());
        }
    }
}

