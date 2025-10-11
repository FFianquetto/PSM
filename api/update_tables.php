<?php
/**
 * Script para actualizar las tablas existentes
 */

require_once 'models/RecipeModel.php';

// Configurar headers CORS
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');
header('Content-Type: application/json');

// Manejar preflight requests
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

try {
    $recipeModel = new RecipeModel();
    
    // Primero intentar crear las tablas (esto actualizará la estructura si es necesario)
    $recipeModel->createTables();
    
    $response = [
        'data' => [
            'message' => 'Tablas actualizadas exitosamente'
        ],
        'message' => 'Actualización completada',
        'error' => null
    ];
    
    http_response_code(200);
    echo json_encode($response, JSON_UNESCAPED_UNICODE);
    
} catch (Exception $e) {
    $response = [
        'data' => null,
        'message' => null,
        'error' => $e->getMessage()
    ];
    
    http_response_code(500);
    echo json_encode($response, JSON_UNESCAPED_UNICODE);
}
?>
