<?php
/**
 * Script para crear la base de datos PSM con todas sus tablas
 * Ejecutar: php create_database.php
 */

require_once 'config/database.php';

try {
    echo "Iniciando creación de base de datos...\n\n";
    
    // Conexión sin especificar base de datos para crearla
    $pdo = new PDO('mysql:host=127.0.0.1;charset=utf8mb4', 'root', '');
    $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    
    // Crear la base de datos
    echo "Creando base de datos 'psm'...\n";
    $pdo->exec("CREATE DATABASE IF NOT EXISTS psm CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    echo "✓ Base de datos 'psm' creada\n\n";
    
    // Usar la base de datos
    $pdo->exec("USE psm");
    
    // Crear tabla de usuarios
    echo "Creando tabla 'users'...\n";
    $pdo->exec("
        CREATE TABLE IF NOT EXISTS users (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            name VARCHAR(100) NOT NULL,
            last_name VARCHAR(100) NOT NULL,
            email VARCHAR(255) UNIQUE NOT NULL,
            password VARCHAR(255) NOT NULL,
            phone VARCHAR(20),
            address TEXT,
            alias VARCHAR(50) UNIQUE NOT NULL,
            avatar_path TEXT,
            created_at BIGINT NOT NULL,
            updated_at BIGINT NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    ");
    echo "✓ Tabla 'users' creada\n\n";
    
    // Crear tabla de recetas
    echo "Creando tabla 'recipes'...\n";
    $pdo->exec("
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
    ");
    echo "✓ Tabla 'recipes' creada\n\n";
    
    // Crear tabla de imágenes
    echo "Creando tabla 'recipe_images'...\n";
    $pdo->exec("
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
    ");
    echo "✓ Tabla 'recipe_images' creada\n\n";
    
    // Crear tabla de votos
    echo "Creando tabla 'recipe_votes'...\n";
    $pdo->exec("
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
    ");
    echo "✓ Tabla 'recipe_votes' creada\n\n";
    
    // Crear tabla de comentarios
    echo "Creando tabla 'recipe_comments'...\n";
    $pdo->exec("
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
    ");
    echo "✓ Tabla 'recipe_comments' creada\n\n";
    
    // Crear tabla de likes en comentarios
    echo "Creando tabla 'comment_likes'...\n";
    $pdo->exec("
        CREATE TABLE IF NOT EXISTS comment_likes (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            comment_id BIGINT NOT NULL,
            user_id BIGINT NOT NULL,
            like_type TINYINT NOT NULL COMMENT '1 = like, 0 = sin like',
            created_at BIGINT NOT NULL,
            FOREIGN KEY (comment_id) REFERENCES recipe_comments(id) ON DELETE CASCADE,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            UNIQUE KEY unique_like (comment_id, user_id),
            INDEX idx_comment_id (comment_id),
            INDEX idx_user_id (user_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    ");
    echo "✓ Tabla 'comment_likes' creada\n\n";
    
    // Crear tabla de respuestas a comentarios
    echo "Creando tabla 'comment_replies'...\n";
    $pdo->exec("
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
    ");
    echo "✓ Tabla 'comment_replies' creada\n\n";
    
    echo "===============================================\n";
    echo "✓ Base de datos PSM creada exitosamente\n";
    echo "✓ Todas las tablas fueron creadas\n";
    echo "===============================================\n";
    
} catch (PDOException $e) {
    echo "Error: " . $e->getMessage() . "\n";
    exit(1);
}
?>

