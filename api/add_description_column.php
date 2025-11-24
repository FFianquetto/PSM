<?php
/**
 * Script para agregar la columna 'description' a la tabla recipes
 * Ejecutar este script una sola vez si la tabla ya existe sin este campo
 */

require_once 'models/RecipeModel.php';
require_once 'config/database.php';

header('Content-Type: application/json');

try {
    $database = new DatabaseConfig();
    $db = $database->getConnection();
    
    // Verificar si la columna ya existe
    $checkSql = "SHOW COLUMNS FROM recipes LIKE 'description'";
    $stmt = $db->prepare($checkSql);
    $stmt->execute();
    $columnExists = $stmt->fetch();
    
    if ($columnExists) {
        echo json_encode([
            'success' => true,
            'message' => 'La columna description ya existe en la tabla recipes'
        ]);
    } else {
        // Agregar la columna description después de title
        $alterSql = "ALTER TABLE recipes ADD COLUMN description TEXT AFTER title";
        $db->exec($alterSql);
        
        echo json_encode([
            'success' => true,
            'message' => 'Columna description agregada exitosamente a la tabla recipes'
        ]);
    }
    
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'error' => 'Error al actualizar tabla: ' . $e->getMessage()
    ]);
}
?>

