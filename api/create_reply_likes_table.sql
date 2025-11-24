-- ============================================
-- TABLA: reply_likes (Likes/Dislikes en Respuestas)
-- ============================================
-- Esta tabla almacena los likes y dislikes de las respuestas a comentarios
-- vote_type: 1 = me gusta, 0 = no me gusta (dislike)

CREATE TABLE IF NOT EXISTS reply_likes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reply_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    vote_type TINYINT NOT NULL COMMENT '1 = me gusta, 0 = no me gusta',
    created_at BIGINT NOT NULL,
    FOREIGN KEY (reply_id) REFERENCES comment_replies(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_reply_like (reply_id, user_id),
    INDEX idx_reply_id (reply_id),
    INDEX idx_user_id (user_id),
    INDEX idx_vote_type (vote_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT '✓ Tabla reply_likes creada exitosamente' AS Resultado;

