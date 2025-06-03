# MealSync Backend

A robust Spring Boot application for meal planning and recipe synchronization with AI-powered features.

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
- **Language**: Java 22
- **Database**: PostgreSQL
- **Security**: Spring Security with JWT
- **Documentation**: SpringDoc OpenAPI
- **Mapping**: MapStruct
- **Build Tool**: Maven
- **Cloud Services**: AWS S3, Google Cloud Vision
- **External APIs**: Stability AI, Gemini AI

## 🏗️ Architecture

MealSync follows a clean architecture pattern with clear separation of concerns:

- **Domain**: Core business logic and entities
- **Application**: Services and use cases
- **Infrastructure**: External interfaces and data access
- **Presentation**: API endpoints and controllers

## 📦 Prerequisites

- Java 22 or higher
- Maven 3.8+
- PostgreSQL 14+
- Docker (optional, for containerization)

## 🔧 Environment Variables

The following environment variables need to be set:

```
# Database Configuration
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mealsync
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=yourpassword

# JWT Configuration
JWT_SECRET=your_jwt_secret_key
JWT_EXPIRATION_MS=86400000

# AWS Configuration
AWS_ACCESS_KEY=your_aws_access_key
AWS_SECRET_KEY=your_aws_secret_key
AWS_REGION=your_aws_region
AWS_BUCKET_NAME=your_bucket_name

# Stability AI Configuration
STABILITY_API_KEY=your_stability_api_key
STABILITY_API_URL=https://api.stability.ai/v1/generation/stable-diffusion-xl-1024-v1-0/text-to-image

# Google Cloud Vision Configuration
GOOGLE_APPLICATION_CREDENTIALS=path/to/your/google-credentials.json

# Gemini AI Configuration
GEMINI_API_KEY=your_gemini_api_key
```

## 🚀 Getting Started

### Running Locally

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

### Using Docker

1. Build the Docker image
   ```bash
   docker build -t mealsync .
   ```

2. Run the container
   ```bash
   docker run -p 8081:8081 --env-file .env mealsync
   ```

## 🧪 Testing

Run the tests with Maven:
```bash
./mvnw test
```

## 📚 API Documentation

When the application is running, access the API documentation at:
```
http://localhost:8081/swagger-ui.html
```

## 🚀 Deployment

This project is set up for continuous integration and deployment using:

- **GitHub Actions**: Automated build, test, and deployment pipeline
- **Docker**: Containerization for consistent deployment
- **AWS**: Cloud hosting platform

The CI/CD pipeline automatically builds and tests the application on each push to the main branch, and deploys to AWS when tests pass.

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
