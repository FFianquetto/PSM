<?php
/**
 * Controlador principal de la API REST
 */

// Configurar zona horaria de México (UTC-6)
date_default_timezone_set('America/Mexico_City');

require_once 'models/UserModel.php';
require_once 'models/RecipeModel.php';

class ApiController {
    private $userModel;
    private $recipeModel;
    
    public function __construct() {
        $this->userModel = new UserModel();
        $this->recipeModel = new RecipeModel();
    }
    
    /**
     * Manejar las peticiones HTTP
     */
    public function handleRequest() {
        $method = $_SERVER['REQUEST_METHOD'];
        $path = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
        
        // Configurar headers CORS
        header('Access-Control-Allow-Origin: *');
        header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
        header('Access-Control-Allow-Headers: Content-Type, Authorization');
        header('Content-Type: application/json');
        
        // Manejar preflight requests
        if ($method === 'OPTIONS') {
            http_response_code(200);
            exit();
        }
        
        try {
            switch ($method) {
                case 'GET':
                    $this->handleGet($path);
                    break;
                case 'POST':
                    $this->handlePost($path);
                    break;
                case 'PUT':
                    $this->handlePut($path);
                    break;
                default:
                    $this->sendResponse(['error' => 'Método no permitido'], 405);
            }
        } catch (Exception $e) {
            $this->sendResponse(['error' => $e->getMessage()], 500);
        }
    }
    
    /**
     * Manejar peticiones GET
     */
    private function handleGet($path) {
        // Verificar si es una petición para obtener un usuario por ID
        if (preg_match('/^\/api\/users\/(\d+)$/', $path, $matches)) {
            $userId = (int)$matches[1];
            $user = $this->userModel->getUserById($userId);
            
            if ($user) {
                $this->sendResponse([
                    'data' => [
                        'user' => [
                            'id' => $user['id'],
                            'name' => $user['name'],
                            'lastName' => $user['last_name'],
                            'email' => $user['email'],
                            'phone' => $user['phone'],
                            'address' => $user['address'],
                            'alias' => $user['alias'],
                            'avatarPath' => $user['avatar_path'],
                            'createdAt' => $user['created_at'],
                            'updatedAt' => $user['updated_at']
                        ]
                    ]
                ]);
            } else {
                $this->sendResponse(['error' => 'Usuario no encontrado'], 404);
            }
            return;
        }
        
        // Verificar si es una petición para obtener una receta por ID
        if (preg_match('/^\/api\/recipes\/(\d+)$/', $path, $matches)) {
            $recipeId = (int)$matches[1];
            $this->getRecipeById($recipeId);
            return;
        }
        
        // Verificar si es una petición para obtener recetas con like de un usuario
        if (preg_match('/^\/api\/users\/(\d+)\/liked-recipes$/', $path, $matches)) {
            $userId = (int)$matches[1];
            $this->getLikedRecipesByUserId($userId);
            return;
        }
        
        // Verificar si es una petición para obtener comentarios de una receta
        if (preg_match('/^\/api\/recipes\/(\d+)\/comments$/', $path, $matches)) {
            $recipeId = (int)$matches[1];
            $this->getCommentsByRecipeId($recipeId);
            return;
        }
        
        // Verificar si es una petición para obtener conteos de votos de una receta
        if (preg_match('/^\/api\/recipes\/(\d+)\/votes-count$/', $path, $matches)) {
            $recipeId = (int)$matches[1];
            $this->getRecipeVotesCount($recipeId);
            return;
        }
        
        // Verificar si es una petición para obtener conteos de votos de un comentario
        if (preg_match('/^\/api\/comments\/(\d+)\/votes-count$/', $path, $matches)) {
            $commentId = (int)$matches[1];
            $this->getCommentVotesCount($commentId);
            return;
        }
        
        // Verificar si es una petición para obtener respuestas de un comentario
        if (preg_match('/^\/api\/comments\/(\d+)\/replies$/', $path, $matches)) {
            $commentId = (int)$matches[1];
            $this->getRepliesByCommentId($commentId);
            return;
        }
        
        // Verificar si es una petición para obtener conteos de votos de una respuesta
        if (preg_match('/^\/api\/replies\/(\d+)\/votes-count$/', $path, $matches)) {
            $replyId = (int)$matches[1];
            $this->getReplyVotesCount($replyId);
            return;
        }
        
        switch ($path) {
            case '/api/users':
                $users = $this->userModel->getAllUsers();
                $this->sendResponse(['users' => $users]);
                break;
                
            case '/api/users/by-email':
                $this->getUserByEmail();
                break;
                
            case '/api/health':
                $this->sendResponse(['status' => 'OK', 'message' => 'API funcionando']);
                break;
                
            case '/api/recipes/feed':
                $this->getRecipesFeed();
                break;
                
            case '/api/recipes/search':
                $this->searchRecipes();
                break;
                
            case '/api/truncate-all':
                $this->truncateAllTables();
                break;
                
            default:
                $this->sendResponse(['error' => 'Endpoint no encontrado'], 404);
        }
    }
    
    /**
     * Manejar peticiones PUT
     */
    private function handlePut($path) {
        $data = json_decode(file_get_contents('php://input'), true);
        
        if (json_last_error() !== JSON_ERROR_NONE) {
            $this->sendResponse(['error' => 'JSON inválido'], 400);
            return;
        }
        
        // Extraer el ID del path (ej: /api/users/123) o email (ej: /api/users/by-email)
        if (preg_match('/^\/api\/users\/(\d+)$/', $path, $matches)) {
            $userId = (int)$matches[1];
            $this->updateUser($userId, $data);
        } elseif ($path === '/api/users/by-email') {
            $this->updateUserByEmail($data);
        } else {
            $this->sendResponse(['error' => 'Endpoint no encontrado'], 404);
        }
    }
    
    /**
     * Manejar peticiones POST
     */
    private function handlePost($path) {
        $data = json_decode(file_get_contents('php://input'), true);
        
        if (json_last_error() !== JSON_ERROR_NONE) {
            $this->sendResponse(['error' => 'JSON inválido'], 400);
            return;
        }
        
        switch ($path) {
            case '/api/migrate/users':
                $this->migrateUsers($data);
                break;
                
            case '/api/migrate/recipes':
                $this->migrateRecipes($data);
                break;
                
            case '/api/migrate/recipe_images':
                $this->migrateRecipeImages($data);
                break;
                
            case '/api/users':
                $this->createUser($data);
                break;
                
            case '/api/users/by-email':
                $this->getUserByEmail();
                break;
                
            case '/api/login':
                $this->loginUser($data);
                break;
                
            case '/api/setup':
                $this->setupDatabase();
                break;
                
            case '/api/update_tables':
                $this->updateTables();
                break;
                
            case '/api/recipes':
                $this->createRecipe($data);
                break;
                
            case '/api/votes':
                $this->createVote($data);
                break;
                
            case '/api/comment-votes':
                $this->createCommentVote($data);
                break;
                
            case '/api/comment-replies':
                $this->createCommentReply($data);
                break;
                
            case '/api/reply-votes':
                $this->createReplyVote($data);
                break;
                
            default:
                // Intentar match para POST /api/recipes/{recipeId}/comments
                if (preg_match('/^\/api\/recipes\/(\d+)\/comments$/', $path, $matches)) {
                    $recipeId = (int)$matches[1];
                    $this->createComment($recipeId, $data);
                    return;
                }
                $this->sendResponse(['error' => 'Endpoint no encontrado'], 404);
        }
    }
    
    /**
     * Migrar usuarios desde SQLite
     */
    private function migrateUsers($data) {
        if (!isset($data['users']) || !is_array($data['users'])) {
            $this->sendResponse(['error' => 'Datos de usuarios requeridos'], 400);
            return;
        }
        
        $result = $this->userModel->migrateUsers($data['users']);
        
        $this->sendResponse([
            'message' => 'Migración completada',
            'migrated' => $result['migrated'],
            'total' => $result['total'],
            'errors' => $result['errors']
        ]);
    }
    
    /**
     * Migrar recetas desde SQLite
     */
    private function migrateRecipes($data) {
        if (!isset($data['recipes']) || !is_array($data['recipes'])) {
            $this->sendResponse(['error' => 'Datos de recetas requeridos'], 400);
            return;
        }
        
        $result = $this->recipeModel->migrateRecipes($data['recipes']);
        
        $this->sendResponse([
            'message' => 'Migración de recetas completada',
            'migrated' => $result['migrated'],
            'total' => $result['total'],
            'errors' => $result['errors']
        ]);
    }
    
    /**
     * Migrar imágenes de recetas desde SQLite
     */
    private function migrateRecipeImages($data) {
        if (!isset($data['images']) || !is_array($data['images'])) {
            $this->sendResponse(['error' => 'Datos de imágenes requeridos'], 400);
            return;
        }
        
        $result = $this->recipeModel->migrateRecipeImages($data['images']);
        
        $this->sendResponse([
            'message' => 'Migración de imágenes completada',
            'migrated' => $result['migrated'],
            'total' => $result['total'],
            'errors' => $result['errors']
        ]);
    }
    
    /**
     * Actualizar un usuario existente por email
     */
    private function updateUserByEmail($data) {
        $requiredFields = ['email', 'name', 'lastName', 'password', 'alias'];
        
        foreach ($requiredFields as $field) {
            if (!isset($data[$field])) {
                $this->sendResponse(['error' => "Campo requerido: $field"], 400);
                return;
            }
        }
        
        $email = $data['email'];
        
        // Verificar si el usuario existe por email
        $existingUser = $this->userModel->getUserByEmail($email);
        if (!$existingUser) {
            $this->sendResponse(['error' => 'Usuario no encontrado'], 404);
            return;
        }
        
        // Verificar si el alias ya existe en otros usuarios
        if ($data['alias'] !== $existingUser['alias'] && $this->userModel->aliasExists($data['alias'])) {
            $this->sendResponse(['error' => 'El alias ya existe'], 409);
            return;
        }
        
        $userData = [
            'name' => $data['name'],
            'last_name' => $data['lastName'],
            'email' => $data['email'],
            'password' => $data['password'],
            'phone' => $data['phone'] ?? null,
            'address' => $data['address'] ?? null,
            'alias' => $data['alias'],
            'avatar_path' => $data['avatarPath'] ?? null,
            'updated_at' => time() * 1000 // Convertir a milisegundos
        ];
        
        $success = $this->userModel->updateUserByEmail($email, $userData);
        
        if ($success) {
            $this->sendResponse([
                'data' => [
                    'message' => 'Usuario actualizado exitosamente',
                    'userId' => $existingUser['id']
                ]
            ]);
        } else {
            $this->sendResponse(['error' => 'Error actualizando usuario'], 500);
        }
    }
    
    /**
     * Actualizar un usuario existente
     */
    private function updateUser($userId, $data) {
        $requiredFields = ['name', 'lastName', 'email', 'password', 'alias'];
        
        foreach ($requiredFields as $field) {
            if (!isset($data[$field])) {
                $this->sendResponse(['error' => "Campo requerido: $field"], 400);
                return;
            }
        }
        
        // Verificar si el usuario existe
        $existingUser = $this->userModel->getUserById($userId);
        if (!$existingUser) {
            $this->sendResponse(['error' => 'Usuario no encontrado'], 404);
            return;
        }
        
        // Verificar si el email o alias ya existen en otros usuarios
        if ($data['email'] !== $existingUser['email'] && $this->userModel->emailExists($data['email'])) {
            $this->sendResponse(['error' => 'El email ya existe'], 409);
            return;
        }
        
        if ($data['alias'] !== $existingUser['alias'] && $this->userModel->aliasExists($data['alias'])) {
            $this->sendResponse(['error' => 'El alias ya existe'], 409);
            return;
        }
        
        $userData = [
            'name' => $data['name'],
            'last_name' => $data['lastName'],
            'email' => $data['email'],
            'password' => $data['password'],
            'phone' => $data['phone'] ?? null,
            'address' => $data['address'] ?? null,
            'alias' => $data['alias'],
            'avatar_path' => $data['avatarPath'] ?? null,
            'updated_at' => time() * 1000 // Convertir a milisegundos
        ];
        
        $success = $this->userModel->updateUser($userId, $userData);
        
        if ($success) {
            $this->sendResponse([
                'message' => 'Usuario actualizado exitosamente',
                'userId' => $userId
            ]);
        } else {
            $this->sendResponse(['error' => 'Error actualizando usuario'], 500);
        }
    }
    
    /**
     * Crear un usuario individual
     */
    private function createUser($data) {
        $requiredFields = ['name', 'lastName', 'email', 'password', 'alias'];
        
        foreach ($requiredFields as $field) {
            if (!isset($data[$field])) {
                $this->sendResponse(['error' => "Campo requerido: $field"], 400);
                return;
            }
        }
        
        // Verificar si el email o alias ya existen
        if ($this->userModel->emailExists($data['email'])) {
            $this->sendResponse(['error' => 'El email ya existe'], 409);
            return;
        }
        
        if ($this->userModel->aliasExists($data['alias'])) {
            $this->sendResponse(['error' => 'El alias ya existe'], 409);
            return;
        }
        
        $userData = [
            'name' => $data['name'],
            'last_name' => $data['lastName'],
            'email' => $data['email'],
            'password' => $data['password'],
            'phone' => $data['phone'] ?? null,
            'address' => $data['address'] ?? null,
            'alias' => $data['alias'],
            'avatar_path' => $data['avatarPath'] ?? null,
            'created_at' => time() * 1000, // Convertir a milisegundos
            'updated_at' => time() * 1000
        ];
        
        $userId = $this->userModel->insertUser($userData);
        
        if ($userId) {
            $this->sendResponse([
                'data' => [
                    'message' => 'Usuario creado exitosamente',
                    'userId' => $userId
                ]
            ], 201);
        } else {
            $this->sendResponse(['error' => 'Error creando usuario'], 500);
        }
    }
    
    /**
     * Autenticar usuario (login)
     */
    private function loginUser($data) {
        if (!isset($data['email']) || !isset($data['password'])) {
            $this->sendResponse(['error' => 'Email y contraseña requeridos'], 400);
            return;
        }
        
        $email = trim(strtolower($data['email']));
        $password = $data['password'];
        
        // Obtener usuario por email
        $user = $this->userModel->getUserByEmail($email);
        
        if (!$user) {
            $this->sendResponse(['error' => 'Credenciales incorrectas'], 401);
            return;
        }
        
        // Verificar contraseña (en producción deberías usar hash)
        if ($user['password'] !== $password) {
            $this->sendResponse(['error' => 'Credenciales incorrectas'], 401);
            return;
        }
        
        // Login exitoso
        $this->sendResponse([
            'data' => [
                'message' => 'Login exitoso',
                'user' => [
                    'id' => $user['id'],
                    'name' => $user['name'],
                    'lastName' => $user['last_name'],
                    'email' => $user['email'],
                    'password' => $user['password'],
                    'phone' => $user['phone'],
                    'address' => $user['address'],
                    'alias' => $user['alias'],
                    'avatarPath' => $user['avatar_path'],
                    'createdAt' => $user['created_at'],
                    'updatedAt' => $user['updated_at']
                ]
            ]
        ]);
    }
    
    /**
     * Configurar la base de datos
     */
    private function setupDatabase() {
        try {
            $this->userModel->createTable();
            $this->recipeModel->createTables();
            $this->sendResponse(['message' => 'Base de datos configurada exitosamente (usuarios y recetas)']);
        } catch (Exception $e) {
            $this->sendResponse(['error' => $e->getMessage()], 500);
        }
    }
    
    /**
     * Actualizar tablas existentes
     */
    private function updateTables() {
        try {
            $this->recipeModel->createTables();
            $this->sendResponse(['message' => 'Tablas actualizadas exitosamente']);
        } catch (Exception $e) {
            $this->sendResponse(['error' => $e->getMessage()], 500);
        }
    }
    
    /**
     * Crear una receta con imágenes directamente en MySQL
     */
    private function createRecipe($data) {
        try {
            // Validar campos requeridos
            $requiredFields = ['title', 'description', 'ingredients', 'steps', 'authorId', 'authorName'];
            foreach ($requiredFields as $field) {
                if (!isset($data[$field])) {
                    $this->sendResponse(['error' => "Campo requerido: $field"], 400);
                    return;
                }
            }
            
            // Preparar datos de la receta
            $recipeData = [
                'title' => $data['title'],
                'description' => $data['description'] ?? '',
                'ingredients' => $data['ingredients'],
                'steps' => $data['steps'],
                'author_id' => $data['authorId'],
                'author_name' => $data['authorName'],
                'tags' => $data['tags'] ?? null,
                'cooking_time' => $data['cookingTime'] ?? 0,
                'servings' => $data['servings'] ?? 1,
                'is_published' => $data['isPublished'] ?? false,
                'created_at' => $data['createdAt'] ?? time() * 1000,
                'updated_at' => $data['updatedAt'] ?? time() * 1000
            ];
            
            // Preparar imágenes (si hay)
            $images = [];
            if (isset($data['images']) && is_array($data['images'])) {
                $images = $data['images'];
            }
            
            // Crear la receta con imágenes
            $result = $this->recipeModel->createRecipeWithImages($recipeData, $images);
            
            $this->sendResponse([
                'data' => [
                    'recipeId' => $result['recipeId'],
                    'imagesInserted' => $result['imagesInserted'],
                    'message' => 'Receta creada exitosamente'
                ]
            ], 201);
        } catch (Exception $e) {
            $this->sendResponse(['error' => 'Error creando receta: ' . $e->getMessage()], 500);
        }
    }
    
    /**
     * Obtener una receta por ID desde MySQL
     * Hace 2 consultas: una a recipes y otra a recipe_images
     */
    private function getRecipeById($recipeId) {
        try {
            error_log("=== getRecipeById START - ID: $recipeId ===");
            
            // Consulta 1: Obtener la receta desde recipes
            $recipe = $this->recipeModel->getRecipeById($recipeId);
            
            if ($recipe) {
                error_log("Receta encontrada: {$recipe['title']}");
                error_log("Total imágenes: " . (isset($recipe['images']) ? count($recipe['images']) : 0));
                
                $this->sendResponse([
                    'data' => [
                        'recipe' => [
                            'id' => (int)$recipe['id'],
                            'title' => $recipe['title'] ?? '',
                            'description' => $recipe['description'] ?? '',
                            'ingredients' => $recipe['ingredients'] ?? '',
                            'steps' => $recipe['steps'] ?? '',
                            'authorId' => (int)$recipe['author_id'],
                            'authorName' => $recipe['author_name'] ?? '',
                            'cookingTime' => (int)($recipe['cooking_time'] ?? 0),
                            'servings' => (int)($recipe['servings'] ?? 1),
                            'rating' => (float)($recipe['rating'] ?? 0.0),
                            'isPublished' => (bool)($recipe['is_published'] ?? false),
                            'createdAt' => (int)$recipe['created_at'],
                            'updatedAt' => (int)$recipe['updated_at'],
                            'images' => $recipe['images'] ?? [],
                            'image_data' => $recipe['image_data'] ?? null
                        ]
                    ]
                ]);
                
                error_log("=== getRecipeById END - Éxito ===");
            } else {
                error_log("Receta no encontrada con ID: $recipeId");
                $this->sendResponse(['error' => 'Receta no encontrada'], 404);
            }
        } catch (Exception $e) {
            error_log("ERROR en getRecipeById: " . $e->getMessage());
            error_log("Stack trace: " . $e->getTraceAsString());
            $this->sendResponse(['error' => 'Error obteniendo receta: ' . $e->getMessage()], 500);
        }
    }
    
    /**
     * Obtener usuario por email
     */
    private function getUserByEmail() {
        try {
            $data = json_decode(file_get_contents('php://input'), true);
            
            if (json_last_error() !== JSON_ERROR_NONE) {
                $this->sendResponse(['error' => 'JSON inválido'], 400);
                return;
            }
            
            if (!isset($data['email'])) {
                $this->sendResponse(['error' => 'Email requerido'], 400);
                return;
            }
            
            $email = trim(strtolower($data['email']));
            $user = $this->userModel->getUserByEmail($email);
            
            if ($user) {
                $this->sendResponse([
                    'data' => [
                        'user' => [
                            'id' => $user['id'],
                            'name' => $user['name'],
                            'lastName' => $user['last_name'],
                            'email' => $user['email'],
                            'phone' => $user['phone'],
                            'address' => $user['address'],
                            'alias' => $user['alias'],
                            'avatarPath' => $user['avatar_path'],
                            'createdAt' => $user['created_at'],
                            'updatedAt' => $user['updated_at']
                        ]
                    ]
                ]);
            } else {
                $this->sendResponse(['error' => 'Usuario no encontrado'], 404);
            }
        } catch (Exception $e) {
            $this->sendResponse(['error' => 'Error obteniendo usuario: ' . $e->getMessage()], 500);
        }
    }
    
    /**
     * Crear o actualizar un voto en la tabla recipe_votes
     */
    private function createVote($data) {
        try {
            error_log("=== API::createVote START ===");
            error_log("Datos recibidos: " . json_encode($data));
            
            if (!isset($data['vote'])) {
                error_log("✗ Error: 'vote' no está presente en los datos");
                $this->sendResponse(['error' => 'Datos de voto requeridos'], 400);
                return;
            }
            
            $voteData = $data['vote'];
            error_log("voteData: " . json_encode($voteData));
            
            // Validar campos requeridos
            $requiredFields = ['recipeId', 'userId', 'voteType'];
            foreach ($requiredFields as $field) {
                if (!isset($voteData[$field])) {
                    error_log("✗ Error: Campo requerido ausente: $field");
                    $this->sendResponse(['error' => "Campo requerido: $field"], 400);
                    return;
                }
            }
            
            $recipeId = $voteData['recipeId'];
            $userId = $voteData['userId'];
            $voteType = $voteData['voteType'];
            
            error_log("Validando voteType: $voteType");
            
            // Validar que voteType sea -1 (quitar voto), 0 (dislike) o 1 (like)
            if ($voteType != -1 && $voteType != 0 && $voteType != 1) {
                error_log("✗ Error: voteType inválido: $voteType");
                $this->sendResponse(['error' => 'voteType debe ser -1 (quitar voto), 0 (dislike) o 1 (like)'], 400);
                return;
            }
            
            // Si voteType es -1, eliminar el voto
            if ($voteType == -1) {
                error_log("Eliminando voto para recipe_id=$recipeId, user_id=$userId");
                $result = $this->recipeModel->deleteVote($recipeId, $userId);
                if ($result) {
                    error_log("✓ Voto eliminado exitosamente");
                    $this->sendResponse([
                        'data' => [
                            'message' => 'Voto eliminado exitosamente de recipe_votes'
                        ]
                    ], 200);
                } else {
                    error_log("✗ Error: No se pudo eliminar el voto o no existe");
                    $this->sendResponse(['error' => 'Error eliminando voto o voto no encontrado'], 500);
                }
            } else {
                // Si voteType es 0 o 1, crear o actualizar el voto
                error_log("Creando/actualizando voto para recipe_id=$recipeId, user_id=$userId, vote_type=$voteType");
                $result = $this->recipeModel->createVote($voteData);
                if ($result) {
                    error_log("✓ Voto guardado exitosamente con ID: $result");
                    $this->sendResponse([
                        'data' => [
                            'message' => 'Voto guardado exitosamente en recipe_votes',
                            'voteId' => $result
                        ]
                    ], 201);
                } else {
                    error_log("✗ Error: createVote retornó false");
                    $this->sendResponse(['error' => 'Error guardando voto'], 500);
                }
            }
        } catch (Exception $e) {
            error_log("✗ EXCEPCIÓN en createVote: " . $e->getMessage());
            error_log("Stack trace: " . $e->getTraceAsString());
            $this->sendResponse(['error' => 'Error creando voto: ' . $e->getMessage()], 500);
        }
    }
    
    /**
     * Crear o actualizar un voto en la tabla comment_likes
     */
    private function createCommentVote($data) {
        try {
            error_log("=== API::createCommentVote START ===");
            error_log("Datos recibidos: " . json_encode($data));
            
            if (!isset($data['vote'])) {
                error_log("✗ Error: 'vote' no está presente en los datos");
                $this->sendResponse(['error' => 'Datos de voto requeridos'], 400);
                return;
            }
            
            $voteData = $data['vote'];
            error_log("voteData: " . json_encode($voteData));
            
            // Validar campos requeridos
            $requiredFields = ['commentId', 'userId', 'voteType'];
            foreach ($requiredFields as $field) {
                if (!isset($voteData[$field])) {
                    error_log("✗ Error: Campo requerido ausente: $field");
                    $this->sendResponse(['error' => "Campo requerido: $field"], 400);
                    return;
                }
            }
            
            $commentId = $voteData['commentId'];
            $userId = $voteData['userId'];
            $voteType = $voteData['voteType'];
            
            error_log("Validando voteType: $voteType");
            
            // Validar que voteType sea -1 (quitar voto), 0 (dislike) o 1 (like)
            if ($voteType != -1 && $voteType != 0 && $voteType != 1) {
                error_log("✗ Error: voteType inválido: $voteType");
                $this->sendResponse(['error' => 'voteType debe ser -1 (quitar voto), 0 (dislike) o 1 (like)'], 400);
                return;
            }
            
            // Si voteType es -1, eliminar el voto
            if ($voteType == -1) {
                error_log("Eliminando voto de comentario para comment_id=$commentId, user_id=$userId");
                $result = $this->recipeModel->deleteCommentVote($commentId, $userId);
                if ($result) {
                    error_log("✓ Voto de comentario eliminado exitosamente");
                    $this->sendResponse([
                        'data' => [
                            'message' => 'Voto de comentario eliminado exitosamente de comment_likes'
                        ]
                    ], 200);
                } else {
                    error_log("✗ Error: No se pudo eliminar el voto o no existe");
                    $this->sendResponse(['error' => 'Error eliminando voto de comentario o voto no encontrado'], 500);
                }
            } else {
                // Si voteType es 0 o 1, crear o actualizar el voto
                error_log("Creando/actualizando voto de comentario para comment_id=$commentId, user_id=$userId, vote_type=$voteType");
                $result = $this->recipeModel->createCommentVote($voteData);
                if ($result) {
                    error_log("✓ Voto de comentario guardado exitosamente con ID: $result");
                    $this->sendResponse([
                        'data' => [
                            'message' => 'Voto de comentario guardado exitosamente en comment_likes',
                            'voteId' => $result
                        ]
                    ], 201);
                } else {
                    error_log("✗ Error: createCommentVote retornó false");
                    $this->sendResponse(['error' => 'Error guardando voto de comentario'], 500);
                }
            }
        } catch (Exception $e) {
            error_log("✗ EXCEPCIÓN en createCommentVote: " . $e->getMessage());
            error_log("Stack trace: " . $e->getTraceAsString());
            $this->sendResponse(['error' => 'Error creando voto de comentario: ' . $e->getMessage()], 500);
        }
    }
    
    /**
     * Obtener conteos de likes y dislikes de un comentario
     */
    private function getCommentVotesCount($commentId) {
        try {
            error_log("=== API::getCommentVotesCount START (comment_id=$commentId) ===");
            
            $votesCount = $this->recipeModel->getCommentVotesCount($commentId);
            
            error_log("✓ Conteos obtenidos - Likes: {$votesCount['likes']}, Dislikes: {$votesCount['dislikes']}");
            
            $this->sendResponse([
                'data' => [
                    'likes' => $votesCount['likes'],
                    'dislikes' => $votesCount['dislikes']
                ]
            ]);
        } catch (Exception $e) {
            error_log("✗ EXCEPCIÓN en getCommentVotesCount: " . $e->getMessage());
            error_log("Stack trace: " . $e->getTraceAsString());
            $this->sendResponse(['error' => 'Error obteniendo conteos de votos del comentario: ' . $e->getMessage()], 500);
        }
    }
    
    /**
     * Crear una respuesta a un comentario
     */
    private function createCommentReply($data) {
        try {
            error_log("=== API::createCommentReply START ===");
            error_log("Datos recibidos: " . json_encode($data));
            
            if (!isset($data['reply'])) {
                error_log("✗ Error: 'reply' no está presente en los datos");
                $this->sendResponse(['error' => 'Datos de respuesta requeridos'], 400);
                return;
            }
            
            $replyData = $data['reply'];
            error_log("replyData: " . json_encode($replyData));
            
            // Validar campos requeridos
            $requiredFields = ['commentId', 'userId', 'replyText'];
            foreach ($requiredFields as $field) {
                if (!isset($replyData[$field])) {
                    error_log("✗ Error: Campo requerido ausente: $field");
                    $this->sendResponse(['error' => "Campo requerido: $field"], 400);
                    return;
                }
            }
            
            $commentId = $replyData['commentId'];
            $userId = $replyData['userId'];
            $replyText = trim($replyData['replyText']);
            
            // Validar que el texto no esté vacío
            if (empty($replyText)) {
                error_log("✗ Error: El texto de la respuesta está vacío");
                $this->sendResponse(['error' => 'El texto de la respuesta no puede estar vacío'], 400);
                return;
            }
            
            error_log("Creando respuesta para comment_id=$commentId, user_id=$userId");
            $result = $this->recipeModel->createCommentReply($replyData);
            
            if ($result) {
                error_log("✓ Respuesta creada exitosamente con ID: $result");
                $this->sendResponse([
                    'data' => [
                        'message' => 'Respuesta creada exitosamente',
                        'replyId' => $result
                    ]
                ], 201);
            } else {
                error_log("✗ Error: createCommentReply retornó false");
                $this->sendResponse(['error' => 'Error creando respuesta'], 500);
            }
        } catch (Exception $e) {
            error_log("✗ EXCEPCIÓN en createCommentReply: " . $e->getMessage());
            error_log("Stack trace: " . $e->getTraceAsString());
            $this->sendResponse(['error' => 'Error creando respuesta: ' . $e->getMessage()], 500);
        }
    }
    
    /**
     * Obtener todas las respuestas de un comentario
     */
    private function getRepliesByCommentId($commentId) {
        try {
            error_log("=== API::getRepliesByCommentId START (comment_id=$commentId) ===");
            
            $replies = $this->recipeModel->getRepliesByCommentId($commentId);
            
            error_log("✓ Respuestas obtenidas: " . count($replies));
            
            $this->sendResponse([
                'data' => [
                    'replies' => $replies
                ]
            ]);
        } catch (Exception $e) {
            error_log("✗ EXCEPCIÓN en getRepliesByCommentId: " . $e->getMessage());
            error_log("Stack trace: " . $e->getTraceAsString());
            $this->sendResponse(['error' => 'Error obteniendo respuestas: ' . $e->getMessage()], 500);
        }
    }
    
    /**
     * Crear o actualizar un voto en la tabla reply_likes
     */
    private function createReplyVote($data) {
        try {
            error_log("=== API::createReplyVote START ===");
            error_log("Datos recibidos: " . json_encode($data));
            
            if (!isset($data['vote'])) {
                error_log("✗ Error: 'vote' no está presente en los datos");
                $this->sendResponse(['error' => 'Datos de voto requeridos'], 400);
                return;
            }
            
            $voteData = $data['vote'];
            error_log("voteData: " . json_encode($voteData));
            
            // Validar campos requeridos
            $requiredFields = ['replyId', 'userId', 'voteType'];
            foreach ($requiredFields as $field) {
                if (!isset($voteData[$field])) {
                    error_log("✗ Error: Campo requerido ausente: $field");
                    $this->sendResponse(['error' => "Campo requerido: $field"], 400);
                    return;
                }
            }
            
            $replyId = $voteData['replyId'];
            $userId = $voteData['userId'];
            $voteType = $voteData['voteType'];
            
            error_log("Validando voteType: $voteType");
            
            // Validar que voteType sea -1 (quitar voto), 0 (dislike) o 1 (like)
            if ($voteType != -1 && $voteType != 0 && $voteType != 1) {
                error_log("✗ Error: voteType inválido: $voteType");
                $this->sendResponse(['error' => 'voteType debe ser -1 (quitar voto), 0 (dislike) o 1 (like)'], 400);
                return;
            }
            
            // Si voteType es -1, eliminar el voto
            if ($voteType == -1) {
                error_log("Eliminando voto de reply para reply_id=$replyId, user_id=$userId");
                $result = $this->recipeModel->deleteReplyVote($replyId, $userId);
                if ($result) {
                    error_log("✓ Voto de reply eliminado exitosamente");
                    $this->sendResponse([
                        'data' => [
                            'message' => 'Voto de reply eliminado exitosamente de reply_likes'
                        ]
                    ], 200);
                } else {
                    error_log("✗ Error: No se pudo eliminar el voto o no existe");
                    $this->sendResponse(['error' => 'Error eliminando voto de reply o voto no encontrado'], 500);
                }
            } else {
                // Si voteType es 0 o 1, crear o actualizar el voto
                error_log("Creando/actualizando voto de reply para reply_id=$replyId, user_id=$userId, vote_type=$voteType");
                $result = $this->recipeModel->createReplyVote($voteData);
                if ($result) {
                    error_log("✓ Voto de reply guardado exitosamente con ID: $result");
                    $this->sendResponse([
                        'data' => [
                            'message' => 'Voto de reply guardado exitosamente en reply_likes',
                            'voteId' => $result
                        ]
                    ], 201);
                } else {
                    error_log("✗ Error: createReplyVote retornó false");
                    $this->sendResponse(['error' => 'Error guardando voto de reply'], 500);
                }
            }
        } catch (Exception $e) {
            error_log("✗ EXCEPCIÓN en createReplyVote: " . $e->getMessage());
            error_log("Stack trace: " . $e->getTraceAsString());
            $this->sendResponse(['error' => 'Error creando voto de reply: ' . $e->getMessage()], 500);
        }
    }
    
    /**
     * Obtener conteos de likes y dislikes de una respuesta
     */
    private function getReplyVotesCount($replyId) {
        try {
            error_log("=== API::getReplyVotesCount START (reply_id=$replyId) ===");
            
            $votesCount = $this->recipeModel->getReplyVotesCount($replyId);
            
            error_log("✓ Conteos obtenidos: likes={$votesCount['likes']}, dislikes={$votesCount['dislikes']}");
            
            $this->sendResponse([
                'data' => [
                    'likes' => $votesCount['likes'],
                    'dislikes' => $votesCount['dislikes']
                ]
            ]);
        } catch (Exception $e) {
            error_log("✗ EXCEPCIÓN en getReplyVotesCount: " . $e->getMessage());
            error_log("Stack trace: " . $e->getTraceAsString());
            $this->sendResponse(['error' => 'Error obteniendo conteos de votos de reply: ' . $e->getMessage()], 500);
        }
    }
    
    /**
     * Truncar todas las tablas de MySQL
     */
    private function truncateAllTables() {
        try {
            error_log("=== truncateAllTables START ===");
            
            require_once 'config/database.php';
            $database = new DatabaseConfig();
            $db = $database->getConnection();
            
            // Deshabilitar verificación de foreign keys temporalmente
            $db->exec("SET FOREIGN_KEY_CHECKS = 0");
            
            // Lista de tablas en orden: primero las dependientes, al final las independientes
            // Orden correcto para respetar foreign keys:
            $tables = [
                'comment_replies',      // Depende de: recipe_comments, users
                'comment_likes',        // Depende de: recipe_comments, users
                'recipe_comments',      // Depende de: recipes, users
                'recipe_votes',         // Depende de: recipes, users
                'recipe_images',        // Depende de: recipes
                'recipes',              // Depende de: users
                'users'                 // No depende de nada (tabla base)
            ];
            
            $truncatedCount = 0;
            $errors = [];
            
            foreach ($tables as $table) {
                try {
                    // Verificar que la tabla existe
                    $checkSql = "
                        SELECT COUNT(*) as count 
                        FROM information_schema.TABLES 
                        WHERE TABLE_SCHEMA = DATABASE() 
                        AND TABLE_NAME = :table_name
                    ";
                    
                    $checkStmt = $db->prepare($checkSql);
                    $checkStmt->execute(['table_name' => $table]);
                    $result = $checkStmt->fetch(PDO::FETCH_ASSOC);
                    
                    if ($result && $result['count'] > 0) {
                        error_log("Limpiando tabla '$table'...");
                        // Usar DELETE FROM en lugar de TRUNCATE para evitar problemas con foreign keys
                        $db->exec("DELETE FROM `$table`");
                        // Reiniciar AUTO_INCREMENT (equivalente a TRUNCATE)
                        $db->exec("ALTER TABLE `$table` AUTO_INCREMENT = 1");
                        error_log("✓ Tabla '$table' limpiada exitosamente");
                        $truncatedCount++;
                    } else {
                        error_log("⚠️  Tabla '$table' no existe, omitiendo...");
                    }
                } catch (PDOException $e) {
                    $errorMsg = "Error truncando tabla '$table': " . $e->getMessage();
                    error_log("❌ $errorMsg");
                    $errors[] = $errorMsg;
                }
            }
            
            // Rehabilitar verificación de foreign keys
            $db->exec("SET FOREIGN_KEY_CHECKS = 1");
            
            error_log("=== truncateAllTables END ===");
            error_log("Total tablas truncadas: $truncatedCount");
            
            if (empty($errors)) {
                $this->sendResponse([
                    'success' => true,
                    'message' => "Todas las tablas limpiadas exitosamente (datos eliminados y AUTO_INCREMENT reiniciado)",
                    'tablesTruncated' => $truncatedCount
                ]);
            } else {
                $this->sendResponse([
                    'success' => true,
                    'message' => "Tablas limpiadas con algunos errores",
                    'tablesTruncated' => $truncatedCount,
                    'errors' => $errors
                ], 207); // 207 Multi-Status
            }
        } catch (Exception $e) {
            error_log("ERROR en truncateAllTables: " . $e->getMessage());
            error_log("Stack trace: " . $e->getTraceAsString());
            $this->sendResponse(['error' => 'Error truncando tablas: ' . $e->getMessage()], 500);
        }
    }
    
    /**
     * Obtener feed de recetas (últimas recetas publicadas desde MySQL)
     * Devuelve todas las recetas publicadas ordenadas por fecha de creación descendente
     */
    private function getRecipesFeed() {
        try {
            error_log("=== getRecipesFeed START ===");
            
            // Obtener todas las recetas publicadas (hasta 50 como máximo por rendimiento)
            // Ordenadas por created_at DESC para mostrar las más recientes primero
            $recipes = $this->recipeModel->getLatestRecipes(50);
            error_log("Recetas obtenidas desde MySQL: " . count($recipes));
            
            $this->sendResponse([
                'success' => true,
                'data' => [
                    'recipes' => $recipes
                ]
            ]);
            
            error_log("=== getRecipesFeed END ===");
        } catch (Exception $e) {
            error_log("ERROR en getRecipesFeed: " . $e->getMessage());
            error_log("Stack trace: " . $e->getTraceAsString());
            $this->sendResponse(['error' => 'Error obteniendo recetas: ' . $e->getMessage()], 500);
        }
    }
    
    /**
     * Buscar recetas por título, descripción o nombre del usuario creador
     */
    private function searchRecipes() {
        try {
            error_log("=== searchRecipes START ===");
            
            // Obtener query de búsqueda desde parámetros GET
            $searchQuery = isset($_GET['q']) ? trim($_GET['q']) : '';
            
            if (empty($searchQuery)) {
                $this->sendResponse(['error' => 'Parámetro de búsqueda requerido'], 400);
                return;
            }
            
            error_log("Buscando recetas con query: '$searchQuery' (título, descripción o autor)");
            
            // Buscar recetas por título, descripción o nombre del autor
            $recipes = $this->recipeModel->searchRecipesByTitle($searchQuery, 50);
            error_log("Recetas encontradas: " . count($recipes));
            
            $this->sendResponse([
                'success' => true,
                'data' => [
                    'recipes' => $recipes
                ]
            ]);
            
            error_log("=== searchRecipes END ===");
        } catch (Exception $e) {
            error_log("ERROR en searchRecipes: " . $e->getMessage());
            error_log("Stack trace: " . $e->getTraceAsString());
            $this->sendResponse(['error' => 'Error buscando recetas: ' . $e->getMessage()], 500);
        }
    }
    
    /**
     * Obtener recetas a las que un usuario le dio like
     */
    private function getLikedRecipesByUserId($userId) {
        try {
            error_log("=== getLikedRecipesByUserId START - userId=$userId ===");
            
            $recipes = $this->recipeModel->getLikedRecipesByUserId($userId);
            error_log("Recetas con like obtenidas: " . count($recipes));
            
            $this->sendResponse([
                'success' => true,
                'data' => [
                    'recipes' => $recipes
                ]
            ]);
            
            error_log("=== getLikedRecipesByUserId END ===");
        } catch (Exception $e) {
            error_log("ERROR en getLikedRecipesByUserId: " . $e->getMessage());
            error_log("Stack trace: " . $e->getTraceAsString());
            $this->sendResponse(['error' => 'Error obteniendo recetas con like: ' . $e->getMessage()], 500);
        }
    }
    
    /**
     * Crear un comentario para una receta
     */
    private function createComment($recipeId, $data) {
        try {
            error_log("=== createComment START - recipe_id=$recipeId ===");
            
            if (!isset($data['user_id']) || !isset($data['comment_text'])) {
                $this->sendResponse(['error' => 'Faltan campos requeridos: user_id, comment_text'], 400);
                return;
            }
            
            $userId = (int)$data['user_id'];
            $commentText = trim($data['comment_text']);
            
            if (empty($commentText)) {
                $this->sendResponse(['error' => 'El texto del comentario no puede estar vacío'], 400);
                return;
            }
            
            $commentId = $this->recipeModel->insertComment($recipeId, $userId, $commentText);
            
            if ($commentId) {
                $this->sendResponse([
                    'success' => true,
                    'data' => [
                        'comment_id' => $commentId,
                        'message' => 'Comentario creado exitosamente'
                    ]
                ]);
                error_log("✓ Comentario creado con ID: $commentId");
            } else {
                $this->sendResponse(['error' => 'Error creando comentario'], 500);
            }
            
            error_log("=== createComment END ===");
        } catch (Exception $e) {
            error_log("ERROR en createComment: " . $e->getMessage());
            error_log("Stack trace: " . $e->getTraceAsString());
            $this->sendResponse(['error' => 'Error creando comentario: ' . $e->getMessage()], 500);
        }
    }
    
    /**
     * Obtener todos los comentarios de una receta
     */
    private function getCommentsByRecipeId($recipeId) {
        try {
            error_log("=== getCommentsByRecipeId START - recipe_id=$recipeId ===");
            
            $comments = $this->recipeModel->getCommentsByRecipeId($recipeId);
            error_log("Comentarios encontrados: " . count($comments));
            
            $this->sendResponse([
                'success' => true,
                'data' => [
                    'comments' => $comments
                ]
            ]);
            
            error_log("=== getCommentsByRecipeId END ===");
        } catch (Exception $e) {
            error_log("ERROR en getCommentsByRecipeId: " . $e->getMessage());
            error_log("Stack trace: " . $e->getTraceAsString());
            $this->sendResponse(['error' => 'Error obteniendo comentarios: ' . $e->getMessage()], 500);
        }
    }
    
    /**
     * Obtener conteos de likes y dislikes de una receta
     */
    private function getRecipeVotesCount($recipeId) {
        try {
            error_log("=== getRecipeVotesCount START - recipe_id=$recipeId ===");
            
            $votesCount = $this->recipeModel->getRecipeVotesCount($recipeId);
            
            $this->sendResponse([
                'success' => true,
                'data' => [
                    'likes' => $votesCount['likes'],
                    'dislikes' => $votesCount['dislikes']
                ]
            ]);
            
            error_log("=== getRecipeVotesCount END ===");
        } catch (Exception $e) {
            error_log("ERROR en getRecipeVotesCount: " . $e->getMessage());
            error_log("Stack trace: " . $e->getTraceAsString());
            $this->sendResponse(['error' => 'Error obteniendo conteos de votos: ' . $e->getMessage()], 500);
        }
    }
    
    /**
     * Enviar respuesta JSON
     */
    private function sendResponse($data, $statusCode = 200) {
        http_response_code($statusCode);
        echo json_encode($data, JSON_UNESCAPED_UNICODE);
        exit();
    }
}

// Inicializar la API
$api = new ApiController();
$api->handleRequest();
?>
