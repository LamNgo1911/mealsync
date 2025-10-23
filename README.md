# 🍽️ MealSync API

A high-performance Spring Boot backend for AI-powered meal planning and recipe management.

## 📌 Quick Links

- [API Documentation](#-api-documentation)
- [Local Development](#-local-development)
- [Deployment](#-deployment)
- [Tech Stack](#-tech-stack)
- [Environment Setup](#-environment-setup)

## 🌟 Features

- **🔐 Authentication**
  - JWT & Google OAuth 2.0
  - Role-based access control
  - Secure password hashing

- **🍳 Recipe Management**
  - AI-powered ingredient detection from images (OpenAI GPT-4o-mini Vision)
  - Smart recipe generation from ingredient lists
  - Personalized recommendations based on user preferences
  - Advanced search and filtering (cuisine, tags, ingredients, difficulty)
  - Automatic recipe image generation with Stability AI
  - Save and manage favorite recipes

- **🤖 AI Services**
  - **Ingredient Detection**: Upload a photo to detect raw ingredients
  - **Recipe Generation**: Get creative recipes from detected or provided ingredients
  - **User Preference Matching**: Respects dietary restrictions, allergies, and cuisine preferences
  - **Nutrition Information**: Automatic calculation of calories, protein, carbs, and fat

- **⚡ Performance**
  - Containerized with Docker
  - Nginx reverse proxy
  - Rate limiting
  - Caching

## 🤖 AI Workflow

MealSync uses a two-step AI process for recipe generation:

1. **Ingredient Detection** (Optional)
   - Upload a photo of your ingredients
   - OpenAI GPT-4o-mini Vision API analyzes the image
   - Returns a list of detected raw ingredients

2. **Recipe Generation**
   - Provide ingredients (detected or manually entered)
   - System considers user preferences:
     - Dietary restrictions (vegetarian, vegan, gluten-free, etc.)
     - Favorite cuisines (Italian, Asian, Mexican, etc.)
     - Disliked ingredients
   - OpenAI generates 2 creative recipes with:
     - Step-by-step instructions
     - Nutritional information
     - Cooking times and difficulty levels
   - Stability AI generates attractive recipe images
   - Recipes are saved to the database for future reference

## 🚀 Getting Started

### Prerequisites

- Docker & Docker Compose
- Java 21+ (for local development)
- Maven 3.8+
- OpenAI API key (for AI features)

### Local Development

1. Clone the repository:
   ```bash
   git clone https://github.com/LamNgo1911/mealsync
   cd mealsync
   ```

2. Set up environment:
   ```bash
   cp .env.example .env
   # Update .env with your configuration
   ```

3. Start services:
   ```bash
   # With Nginx proxy
   docker-compose -f docker-compose.yml -f docker-compose.nginx.yml up --build
   
   # Or direct Java execution
   ./mvnw spring-boot:run
   ```

4. Access the API:
   - API: `http://localhost/api/v1`
   - Health: `http://localhost/actuator/health`

## 📚 API Documentation

### Base URLs
- **Production**: `http://13.49.27.132/api/v1`
- **Local**: `http://localhost/api/v1`
h
### Authentication
Include JWT token in the `Authorization` header:
```
Authorization: Bearer your-jwt-token
```

### Response Format
All endpoints return a standardized response:
```json
{
  "success": true,
  "data": { ... },
  "message": "Operation completed successfully"
}
```

### Endpoints

#### 🔐 Authentication (`/api/v1/users`)

- **Register User**
  ```
  POST /register
  ```
  - **Request Body**: 
    ```json
    {
      "email": "user@example.com",
      "password": "securePassword123",
      "name": "John Doe"
    }
    ```
  - **Auth**: Not required

- **Login**
  ```
  POST /login
  ```
  - **Request Body**:
    ```json
    {
      "email": "user@example.com",
      "password": "securePassword123"
    }
    ```
  - **Auth**: Not required

- **Google OAuth Login**
  ```
  POST /login/google
  ```
  - **Request Body**:
    ```json
    {
      "idToken": "google-oauth-token"
    }
    ```
  - **Auth**: Not required

#### 👥 Users (`/api/v1/users`)

- **List Users**
  ```
  GET /
  ```
  - **Query Params**:
    - `role`: Filter by role (USER, ADMIN)
    - `status`: Filter by status (ACTIVE, INACTIVE)
  - **Auth**: Admin only

- **Get User by ID**
  ```
  GET /{id}
  ```
  - **Auth**: Admin or self

- **Update User**
  ```
  PUT /{id}
  ```
  - **Request Body**:
    ```json
    {
      "name": "Updated Name",
      "email": "new.email@example.com"
    }
    ```
  - **Auth**: Admin or self

- **Update User Preferences**
  ```
  PUT /{id}/preference
  ```
  - **Request Body**:
    ```json
    {
      "dietaryRestrictions": ["VEGETARIAN", "GLUTEN_FREE"],
      "allergies": ["PEANUTS", "SHELLFISH"],
      "cuisinePreferences": ["ITALIAN", "ASIAN"]
    }
    ```
  - **Auth**: Admin or self

#### 🍽️ Recipes (`/api/v1/recipes`)

- **Detect Ingredients from Image**
  ```
  POST /detect-ingredients
  ```
  - **Content-Type**: `multipart/form-data`
  - **Form Field**: `image` (image file - JPG, PNG)
  - **Response**:
    ```json
    {
      "success": true,
      "data": ["tomato", "onion", "garlic", "chicken", "rice"]
    }
    ```
  - **Auth**: Required
  - **Description**: Upload a photo of ingredients to automatically detect what's in the image using AI vision

- **Generate Recipes from Ingredients**
  ```
  POST /generate-recipes
  ```
  - **Content-Type**: `application/json`
  - **Request Body**:
    ```json
    {
      "ingredients": ["chicken", "rice", "vegetables"],
      "userPreference": {
        "dietaryRestrictions": "vegetarian, gluten-free",
        "favoriteCuisines": "Italian, Asian",
        "dislikedIngredients": "mushrooms, olives"
      }
    }
    ```
  - **Response**:
    ```json
    {
      "success": true,
      "data": [
        {
          "id": "uuid",
          "name": "Chicken Fried Rice",
          "description": "A delicious Asian-inspired dish",
          "cuisine": "Asian",
          "difficulty": "easy",
          "preparationTime": 15,
          "cookingTime": 20,
          "totalTime": 35,
          "servings": 4,
          "calories": 450.0,
          "protein": 25.0,
          "carbohydrates": 55.0,
          "fat": 12.0,
          "imageUrl": "https://...",
          "tags": ["quick", "asian", "main-course"],
          "instructions": ["Step 1...", "Step 2..."],
          "ingredients": [
            {
              "name": "chicken",
              "quantity": "500",
              "unit": "grams"
            }
          ]
        }
      ]
    }
    ```
  - **Auth**: Required
  - **Description**: Generate 2 creative recipes based on provided ingredients and user preferences

- **List Recipes**
  ```
  GET /
  ```
  - **Query Params**:
    - `offset`: Pagination offset (default: 0)
    - `limit`: Items per page (default: 2)
    - `cuisines`: Comma-separated list of cuisines
    - `tags`: Comma-separated list of tags
    - `ingredients`: Comma-separated list of ingredients
    - `difficulty`: EASY, MEDIUM, or HARD
    - `maxTotalTime`: Maximum cooking time in minutes
    - `minServings`: Minimum number of servings
  - **Auth**: Not required

- **Get Recipe Details**
  ```
  GET /{id}
  ```
  - **Auth**: Required

- **Save Recipe to User**
  ```
  POST /save
  ```
  - **Request Body**:
    ```json
    {
      "userId": "uuid",
      "recipeId": "uuid"
    }
    ```
  - **Auth**: User can only save to their own account

- **Get Recommended Recipes**
  ```
  GET /recommended
  ```
  - **Query Params**:
    - `limit`: Number of recommendations (default: 10)
  - **Auth**: Required

- **Get Saved Recipes**
  ```
  GET /saved
  ```
  - **Query Params**:
    - `limit`: Number of recipes (default: 10)
  - **Auth**: Required

#### 🖼️ Media (`/api/v1/photos`)

- **Generate Recipe Image**
  ```
  POST /generate
  ```
  - **Request Body**:
    ```json
    {
      "recipeName": "Pasta Carbonara",
      "ingredients": ["pasta", "eggs", "bacon", "parmesan"],
      "description": "Creamy Italian pasta dish"
    }
    ```
  - **Auth**: Required
  - **Returns**: URL of the generated image stored in AWS S3

### Rate Limiting
- 100 requests/minute per IP
- 10 requests/minute for auth endpoints

### Error Responses
- `400 Bad Request`: Invalid request parameters
- `401 Unauthorized`: Missing or invalid authentication
- `403 Forbidden`: Insufficient permissions
- `404 Not Found`: Resource not found
- `500 Internal Server Error`: Server-side error

## 🛠️ Tech Stack

- **Backend**: Spring Boot 3.4, Java 21
- **Database**: PostgreSQL 14
- **AI/ML**:
  - OpenAI GPT-4o-mini (Vision & Text) - Ingredient detection & recipe generation
  - Google Gemini - Additional AI capabilities
  - Stability AI - Recipe image generation
- **Cloud & Infrastructure**:
  - AWS (EC2, S3)
  - Docker & Docker Compose
  - Nginx (Reverse proxy & load balancing)
- **Security & Auth**: JWT, Spring Security, Google OAuth 2.0

## 🔧 Environment Variables

Required variables in `.env`:

```env
# Database
POSTGRES_DB=mealsync
POSTGRES_USER=user
POSTGRES_PASSWORD=password

# JWT Authentication
JWT_SECRET=your-secret-key
JWT_EXPIRATION_MS=86400000

# Google OAuth
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret

# AWS Services
AWS_ACCESS_KEY=your-key
AWS_SECRET_KEY=your-secret
AWS_BUCKET=your-bucket
AWS_REGION=us-east-1

# OpenAI (for ingredient detection and recipe generation)
OPENAI_API_KEY=your-openai-api-key
OPENAI_API_BASE_URL=https://api.openai.com/v1/chat/completions

# Google AI Services
GEMINI_API_KEY=your-gemini-key
GOOGLE_VISION_CREDENTIALS_PATH=/path/to/credentials.json

# Stability AI (for image generation)
STABILITY_API_KEY=your-stability-key
```

## 🚀 Deployment

### Production
```bash
docker-compose -f docker-compose.prod.yml up -d
```

### CI/CD
- GitHub Actions for automated testing and deployment
- Auto-deploys to AWS EC2 on `main` branch updates

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<div align="center">
  <p>Built with ❤️ by the MealSync Team</p>
  <a href="https://github.com/LamNgo1911/mealsync">
    <img src="https://img.shields.io/github/stars/LamNgo1911/mealsync?style=social" alt="GitHub stars">
  </a>
</div>

### Infrastructure Documentation

For comprehensive infrastructure guides:

- **[Infrastructure Overview](docs/INFRASTRUCTURE.md)** - Architecture, Docker best practices, deployment strategies
- **[Scaling Guide](docs/SCALING_GUIDE.md)** - How to scale from 500 to 10,000+ users
- **[Deployment Cheat Sheet](docs/DEPLOYMENT_CHEATSHEET.md)** - Quick reference for common tasks

### Helper Scripts

```bash
# Initial server setup (run once on new server)
sudo ./scripts/initial-setup.sh

# Set up SSL certificate
sudo ./scripts/setup-ssl.sh yourdomain.com

# Monitor application status
./scripts/monitor.sh
```

## 🔐 Security

This application implements:
- HTTPS enforcement
- JWT-based authentication
- Password encryption
- CORS configuration
- Input validation

For security considerations and vulnerability reporting, please see [SECURITY.md](SECURITY.md).

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 📬 Contact

For any questions or suggestions, please open an issue or contact the repository owner.
