# MealSync Backend

A robust Spring Boot application for meal planning and recipe synchronization with AI-powered features.

## 🔒 Environment Variables & Secrets

Sensitive information (such as database credentials and Docker Hub username) is stored in a `.env` file, which is loaded automatically by Docker Compose.

- **Never commit your real `.env` to version control.**
- Example `.env` content:

```env
POSTGRES_ENDPOINT=localhost
POSTGRES_PORT=5432
POSTGRES_DB_NAME=mealsync_db
POSTGRES_USER=your_database_username
POSTGRES_PASSWORD=your_database_password
DOCKER_HUB_USERNAME=your_dockerhub_username
```

- Update `.env` with your own secrets before running `docker-compose up` or deploying.
- The `.env` file is referenced in both `docker-compose.yml` and the CI/CD pipeline for secure configuration.

## 📦 Deployment & Hosting

- Docker images are pushed to Docker Hub and deployed to AWS EC2 using GitHub Actions and Docker Compose.
- The production backend is available at: http://13.49.27.132:8081/api/v1

## 📚 API Routes (v1)

Below is a summary of main routes (all prefixed with `/api/v1`):

### Auth
- `POST /users/register` — Register a new user
- `POST /users/login` — User login
- `POST /users/login/google` — Google OAuth login

### Users
- `GET /users` — List all users (admin only)
- `GET /users/{id}` — Get user by ID
- `DELETE /users/{id}` — Delete user (admin only)
- `PUT /users/{id}` — Update user (admin or self)
- `PUT /users/{id}/preference` — Update user preference

### Recipes
- `POST /recipes/generate-recipes` — AI-generate recipes from ingredients
- `POST /recipes/save` — Save recipe to user
- `POST /recipes` — Create a recipe
- `GET /recipes/{id}` — Get recipe by ID
- `GET /recipes` — List recipes (paginated)
- `PUT /recipes/{id}` — Update recipe
- `DELETE /recipes/{id}` — Delete recipe

### Ingredient Recognition
- `POST /ingredient-recognition/detect` — Detect ingredients from an image

### Photos
- `POST /photos/generate` — Generate and upload a recipe image

---
- All endpoints return a standard `SuccessResponseEntity<T>` wrapper.
- Most endpoints require authentication; see your security config for details.

## 📋 Overview

MealSync is a backend service that enables users to discover, save, and manage recipes with advanced features including ingredient recognition, AI-generated recipe images, and personalized meal planning.

## 🚀 Features

- **User Authentication & Authorization**
  - JWT-based secure authentication
  - Role-based access control
  - Google OAuth integration
  - Refresh token mechanism

- **Recipe Management**
  - Comprehensive recipe model with ingredients, instructions, nutritional information
  - Recipe search and filtering by ingredients, cuisine, tags
  - User-recipe relationships (favorites, meal plans)

- **AI Integration**
  - Image generation for recipes using Stability AI
  - Ingredient recognition from images via Google Cloud Vision
  - Smart recipe recommendations powered by Gemini AI

- **File Storage**
  - AWS S3 integration for image storage
  - Secure file upload/download

## 🛠️ Tech Stack

- **Framework**: Spring Boot 3.4.3
- **Language**: Java 21 (compile target as per pom.xml)
- **Database**: PostgreSQL 42.7.2
- **Security**: Spring Security with JWT 0.12.6
- **Mapping**: MapStruct 1.5.5.Final
- **Build Tool**: Maven
- **Cloud Services**: AWS S3, Google Cloud Vision 3.22.0
- **External APIs**: Stability AI, Google Gemini AI
- **Authentication**: JWT, Google OAuth
- **HTTP Client**: OkHttp 4.11.0, WebFlux

## 🏗️ Architecture

MealSync follows a clean architecture pattern with clear separation of concerns:

- **Domain**: Core business logic and entities
- **Application**: Services and use cases
- **Infrastructure**: External interfaces and data access
- **Presentation**: API endpoints and controllers

## 📦 Prerequisites

- Docker & Docker Compose (for local and production deployment)
- (For local dev) Java 21+, Maven 3.8+, PostgreSQL 14+

## 🔧 Environment Variables

The following environment variables need to be set:

```
# Database Configuration
POSTGRES_ENDPOINT=localhost
POSTGRES_PORT=5432
POSTGRES_DB_NAME=your_database_name
POSTGRES_USER=your_database_username
POSTGRES_PASSWORD=your_database_password

# JWT Configuration
JWT_SECRET=your_jwt_secret_key
JWT_EXPIRATION_MS=86400000

# Gemini API Configuration
GEMINI_API_BASE_URL=your_gemini_api_url
GEMINI_API_KEY=your_gemini_api_key

# AWS Configuration
AWS_ACCESS_KEY=your_aws_access_key
AWS_SECRET_KEY=your_aws_secret_key
AWS_REGION=your_aws_region
AWS_BUCKET_NAME=your_bucket_name

# Stability AI Configuration
STABILITY_API_KEY=your_stability_api_key
STABILITY_API_URL=your_stability_api_url

# Google Cloud Vision Configuration
GOOGLE_APPLICATION_CREDENTIALS=path/to/your/google-credentials.json
```

## 🐳 Running with Docker & Docker Compose

- The backend will be available at `http://localhost:8081` by default.
- Environment variables are managed via `.env` and `docker-compose.yml`.

## 🚀 Getting Started

### Running Locally (Java)

1. Clone the repository
   ```bash
   git clone https://github.com/LamNgo1911/mealsync
   cd mealsync
   ```

2. Set up environment variables (see above)

3. Build the application
   ```bash
   ./mvnw clean install
   ```

4. Run the application
   ```bash
   ./mvnw spring-boot:run
   ```
   The application will be available at `http://localhost:8081`

### Using Docker Compose

1. Make sure your `.env` file is set up (see above).
2. Start all services:
   ```bash
   docker compose -f docker-compose.prod.yml up
   ```
   The backend will be available at `http://localhost:8081`.

## 🚀 Deployment (CI/CD & Production)

This project uses **GitHub Actions** for CI/CD and automatic deployment to an AWS EC2 instance:

- On every push to `main`, GitHub Actions builds, tests, and deploys the Docker image to your EC2 server.
- The deployment is managed via SSH and Docker Compose on the remote server.
- See `.github/workflows/deploy.yml` for details.

### Production URL

The deployed backend is available at:

```
http://13.49.27.132:8081/api/v1
```
## 🧪 Testing

Run the tests with Maven:
```bash
./mvnw test
```

## 🚀 Deployment

This project is set up for continuous integration and deployment using:

- **GitHub Actions**: Automated build, test, and deployment pipeline
- **Docker & Docker Compose**: Containerization for consistent deployment
- **AWS RDS**: Relational Database Service
- **AWS EC2**: Cloud hosting platform

The CI/CD pipeline automatically builds and tests the application on each push to the main branch, and deploys to AWS EC2 when tests pass.

### Production URL

The backend is deployed at:

```
http://13.49.27.132:8081/api/v1
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
