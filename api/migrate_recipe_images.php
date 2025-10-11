<?php
/**
 * Endpoint de migración de imágenes de recetas para la API
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
    // Obtener los datos del request
    $input = file_get_contents('php://input');
    $data = json_decode($input, true);
    
    if (json_last_error() !== JSON_ERROR_NONE) {
        throw new Exception('JSON inválido');
    }
    
    if (!isset($data['images']) || !is_array($data['images'])) {
        throw new Exception('Datos de imágenes requeridos');
    }
    
    // Crear el modelo de recetas
    $recipeModel = new RecipeModel();
    
    // Migrar las imágenes
    $result = $recipeModel->migrateRecipeImages($data['images']);
    
    $response = [
        'data' => [
            'message' => 'Migración de imágenes completada',
            'migrated' => $result['migrated'],
            'total' => $result['total'],
            'errors' => $result['errors']
        ],
        'message' => 'Migración exitosa',
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


