-- Script para crear la base de datos PSM con todas sus tablas
-- Proyecto de Recetas de Cocina

-- Crear la base de datos
CREATE DATABASE IF NOT EXISTS psm CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Usar la base de datos
USE psm;

-- ============================================
-- TABLA: users (Usuarios)
-- ============================================
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- TABLA: recipes (Recetas/Publicaciones)
-- ============================================
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- TABLA: recipe_images (Imágenes de Recetas)
-- ============================================
CREATE TABLE IF NOT EXISTS recipe_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    image_data LONGBLOB NOT NULL,
    description TEXT,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
    INDEX idx_recipe_id (recipe_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- TABLA: recipe_votes (Votos/Likes en Recetas)
-- ============================================
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- TABLA: recipe_comments (Comentarios en Recetas)
-- ============================================
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- TABLA: comment_likes (Likes en Comentarios)
-- ============================================
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- TABLA: comment_replies (Respuestas a Comentarios)
-- ============================================
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Mensaje de confirmación
-- ============================================
SELECT 'Base de datos PSM creada exitosamente con todas las tablas' AS Resultado;

