# 🍳 Food Recipe Recommendation Agent

A modern, AI-powered recipe recommendation system built with Java Spring Boot, featuring a beautiful web interface and comprehensive database integration. This application uses OpenAI's GPT model to generate personalized recipes based on available ingredients, cuisine preferences, and dietary restrictions.

## ✨ Features

### 🤖 AI-Powered Recipe Generation
- **Smart Recipe Creation**: Generate detailed recipes using available ingredients
- **Multi-Cuisine Support**: Choose from various cuisine styles (Italian, Mexican, Indian, Asian, etc.)
- **Dietary Accommodations**: Support for vegetarian, vegan, gluten-free, dairy-free, and other dietary restrictions
- **Professional Cooking Tips**: Get expert cooking advice and techniques
- **Nutritional Information**: Receive nutritional insights for generated recipes

### 🎨 Modern User Interface
- **Responsive Design**: Beautiful, mobile-friendly interface built with Tailwind CSS
- **Real-time Feedback**: Instant validation and user feedback
- **Print & Share**: Print recipes or copy to clipboard
- **Favorites System**: Save and manage favorite recipes
- **Database Integration**: Persistent storage with PostgreSQL

### 🔒 Enterprise-Grade Security
- **Encrypted API Key Storage**: AES-256/GCM encryption for OpenAI API keys
- **Session-Based Security**: Secure session management with HttpOnly cookies
- **Input Validation**: Comprehensive validation with XSS prevention
- **Rate Limiting**: Protection against abuse with configurable limits
- **Security Headers**: Complete security header implementation
- **Audit Logging**: Comprehensive security event logging

### 🗄️ Database Features
- **PostgreSQL Integration**: Robust database backend
- **User Management**: Multi-user support with user accounts
- **Recipe Persistence**: Save and retrieve recipes from database
- **Favorites Management**: Database-backed favorites system
- **Search Capabilities**: Full-text search on recipes
- **Data Analytics**: User statistics and recipe counts

## 🛠️ Technology Stack

### Backend
- **Java 17**: Modern Java with latest features
- **Spring Boot 3.2.0**: Enterprise-grade framework
- **Spring Security**: Comprehensive security implementation
- **Spring Data JPA**: Database abstraction layer
- **PostgreSQL**: Robust relational database
- **Hibernate**: Object-relational mapping
- **Maven**: Dependency management and build tool

### Frontend
- **HTML5**: Semantic markup
- **Tailwind CSS**: Utility-first CSS framework
- **JavaScript (ES6+)**: Modern JavaScript features
- **Thymeleaf**: Server-side template engine
- **Font Awesome**: Icon library

### Security & Performance
- **AES-256/GCM Encryption**: Military-grade encryption
- **Bucket4j**: Rate limiting implementation
- **HikariCP**: High-performance connection pooling
- **Jackson**: JSON processing
- **JWT**: Token-based authentication support

## 📋 Prerequisites

- **Java 17 or higher**
- **Maven 3.6 or higher**
- **PostgreSQL 12 or higher**
- **OpenAI API key** (get one from [OpenAI Platform](https://platform.openai.com/api-keys))

## 🚀 Quick Start

### 1. Clone the Repository
```bash
git clone <repository-url>
cd food_recipe_recommendation_agent
```

### 2. Database Setup
```bash
# Start PostgreSQL service
sudo systemctl start postgresql

# Run the database setup script
sudo -u postgres psql -f database_setup.sql
```

### 3. Build and Run
```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

### 4. Access the Application
Open your browser and navigate to: **http://localhost:8080**

## 🔧 Configuration

### Application Properties
The application can be configured through `src/main/resources/application.properties`:

```properties
# Server Configuration
server.port=8080

# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/recipe_assistant
spring.datasource.username=recipe_user
spring.datasource.password=recipe_password

# Security Configuration
app.encryption.key=your-super-secret-encryption-key-change-in-production-32-chars

# Rate Limiting
app.rate-limit.requests-per-minute=10

# OpenAI Configuration
openai.api.url=https://api.openai.com/v1/chat/completions
```

### Environment Variables
```bash
# Optional: Set OpenAI API key as environment variable
export OPENAI_API_KEY="your-api-key-here"
```

## 📖 Usage Guide

### 1. Set Up Your OpenAI API Key
1. Get an API key from [OpenAI Platform](https://platform.openai.com/api-keys)
2. In the application, enter your API key in the "OpenAI API Key Setup" section
3. Click "Set Key" to validate and store your key securely

### 2. Generate Recipes
1. **Enter Ingredients**: List the ingredients you have available
2. **Select Cuisine** (Optional): Choose your preferred cuisine style
3. **Add Dietary Restrictions** (Optional): Specify any dietary requirements
4. **Generate Recipe**: Click "Generate Recipe" to create a personalized recipe

### 3. Get Cooking Tips
1. Enter ingredients you want tips for
2. Click "Get Tips" for professional cooking advice and techniques

### 4. Manage Favorites
1. Save recipes to your favorites collection
2. View and manage your saved recipes
3. Remove recipes from favorites as needed

## 🏗️ Project Structure

```
src/
├── main/
│   ├── java/com/recipeassistant/
│   │   ├── RecipeAssistantApplication.java          # Main application class
│   │   ├── config/
│   │   │   ├── SecurityConfig.java                  # Security configuration
│   │   │   └── WebConfig.java                       # Web configuration
│   │   ├── controller/
│   │   │   └── RecipeController.java                # REST API endpoints
│   │   ├── interceptor/
│   │   │   └── RateLimitInterceptor.java            # Rate limiting
│   │   ├── model/
│   │   │   ├── Recipe.java                          # Recipe entity
│   │   │   ├── RecipeRequest.java                   # Request DTO
│   │   │   ├── RecipeResponse.java                  # Response DTO
│   │   │   ├── User.java                            # User entity
│   │   │   └── UserFavorite.java                    # User favorites entity
│   │   ├── repository/
│   │   │   ├── RecipeRepository.java                # Recipe data access
│   │   │   ├── UserRepository.java                  # User data access
│   │   │   └── UserFavoriteRepository.java          # Favorites data access
│   │   └── service/
│   │       ├── DatabaseService.java                 # Database operations
│   │       ├── EncryptionService.java               # Encryption/decryption
│   │       ├── OpenAIService.java                   # OpenAI API integration
│   │       ├── RateLimitService.java                # Rate limiting logic
│   │       └── SecurityAuditService.java            # Security logging
│   └── resources/
│       ├── application.properties                   # Configuration
│       ├── static/js/
│       │   └── database-migration.js                # Database migration script
│       └── templates/
│           └── index.html                           # Main web interface
└── test/
    └── java/com/recipeassistant/
        └── RecipeAssistantApplicationTests.java     # Application tests
```

## 🔌 API Endpoints

### Web Interface
- `GET /` - Main application page

### API Key Management
- `POST /set-api-key` - Set and validate OpenAI API key
- `POST /clear-api-key` - Clear stored API key
- `GET /api-key-status` - Check if API key is set

### Recipe Generation
- `POST /generate-recipe` - Generate recipes from ingredients
- `POST /get-cooking-tips` - Get cooking tips for ingredients

### Database Operations
- `POST /save-recipe` - Save recipe to database
- `GET /get-user-recipes` - Get user's saved recipes
- `GET /get-user-favorites` - Get user's favorite recipes
- `POST /add-to-favorites` - Add recipe to favorites
- `POST /remove-from-favorites` - Remove recipe from favorites
- `GET /database-status` - Check database connectivity

## 🧪 Testing

### Run Tests
```bash
mvn test
```

### Manual Testing
```bash
# Test API key validation
curl -H "Content-Type: application/json" -X POST http://localhost:8080/set-api-key \
  -d '{"apiKey":"sk-test123"}'

# Test recipe generation
curl -H "Content-Type: application/json" -X POST http://localhost:8080/generate-recipe \
  -d '{"ingredients":"chicken, rice, vegetables"}'

# Test rate limiting
for i in {1..15}; do
  curl -H "Content-Type: application/json" -X POST http://localhost:8080/set-api-key \
    -d '{"apiKey":"sk-test"}' -w "Status: %{http_code}\n"
done
```

## 🚀 Deployment

**Do not deploy this app to Vercel.** Vercel does not run Java/Spring Boot (it targets static sites and serverless runtimes like Node/Python/Go). Deploy to a Java-friendly platform instead (see below).

### Development
```bash
mvn spring-boot:run
```

### Production Build
```bash
mvn clean package
java -jar target/food-recipe-recommendation-agent-1.0.0.jar
```

### Docker Deployment
```dockerfile
FROM openjdk:17-jdk-slim
COPY target/food-recipe-recommendation-agent-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Where to deploy (Java-friendly)
- **Railway / Render / Fly.io**: Connect repo, set build to `mvn clean package` and start command to `java -jar target/food-recipe-recommendation-agent-1.0.0.jar`; add PostgreSQL and env vars.
- **AWS / GCP / Azure**: Run the JAR on EC2/App Engine/App Service, or use container (ECS/EKS, Cloud Run, AKS).
- **Heroku**: Supports Java; use `Procfile` with `web: java -jar target/food-recipe-recommendation-agent-1.0.0.jar` (after `mvn clean package` in buildpack).

## 🔒 Security Features

### Encryption
- **AES-256/GCM**: Military-grade encryption for API keys
- **Secure Key Management**: Keys stored in encrypted sessions
- **Automatic Cleanup**: Keys cleared when session ends

### Input Validation
- **Bean Validation**: Comprehensive input validation
- **XSS Prevention**: Script injection protection
- **Length Limits**: Maximum character limits
- **Pattern Validation**: Safe input patterns

### Rate Limiting
- **Bucket4j Integration**: 10 requests per minute per IP
- **Configurable Limits**: Adjustable rate limits
- **Memory Efficient**: Automatic cleanup of expired buckets

### Security Headers
- **X-Frame-Options**: Clickjacking prevention
- **X-XSS-Protection**: Browser XSS filtering
- **X-Content-Type-Options**: MIME type sniffing prevention
- **Cache-Control**: Secure cache headers

## 📊 Database Schema

### Users Table
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP
);
```

### Recipes Table
```sql
CREATE TABLE recipes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    ingredients TEXT,
    instructions TEXT,
    cuisine VARCHAR(100),
    dietary_restrictions TEXT,
    prep_time INTEGER,
    cook_time INTEGER,
    servings INTEGER,
    is_favorite BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);
```

### User Favorites Table
```sql
CREATE TABLE user_favorites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    recipe_id UUID REFERENCES recipes(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

## 🐛 Troubleshooting

### Common Issues

#### Database Connection Issues
```bash
# Check PostgreSQL status
sudo systemctl status postgresql

# Test database connection
psql -h localhost -U recipe_user -d recipe_assistant
```

#### Port Already in Use
```bash
# Find process using port 8080
sudo lsof -i :8080

# Kill the process
sudo kill -9 <PID>
```

#### API Key Issues
- Ensure API key starts with "sk-"
- Check OpenAI account has sufficient credits
- Verify API key is valid and active

### Logs
```bash
# View application logs
tail -f logs/application.log

# Check security audit logs
grep "SECURITY" logs/application.log
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Guidelines
- Follow Java coding standards
- Write comprehensive tests
- Update documentation
- Ensure security best practices

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **OpenAI** for providing the GPT API
- **Spring Boot** team for the excellent framework
- **Tailwind CSS** for the beautiful UI components
- **PostgreSQL** for the robust database system

## 📞 Support

If you encounter any issues or have questions:

1. Check the [Troubleshooting](#-troubleshooting) section
2. Review the [API Documentation](#-api-endpoints)
3. Open an issue on GitHub
4. Contact the development team

---

**Built with ❤️ using Java, Spring Boot, and OpenAI GPT**

*Last updated: September 2025*