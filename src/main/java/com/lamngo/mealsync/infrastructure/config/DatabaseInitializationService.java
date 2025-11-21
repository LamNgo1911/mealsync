package com.lamngo.mealsync.infrastructure.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Automatically initializes PostgreSQL extensions and indexes on application
 * startup.
 * This eliminates the need to manually run SQL scripts.
 * 
 * Runs automatically on startup and gracefully handles cases where:
 * - Extension already exists (skips creation)
 * - User doesn't have superuser privileges (logs warning, continues)
 * - Database is not PostgreSQL (skips silently)
 */
@Component
public class DatabaseInitializationService implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializationService.class);

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Automatically sets up pg_trgm extension and index on application startup.
     * This runs after the application context is fully initialized.
     * Uses ApplicationRunner to ensure it runs after Spring Data JPA is ready.
     * 
     * Note: Not transactional because DDL statements are auto-commit and we want
     * to handle exceptions gracefully without affecting transaction state.
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            // Check if we're using PostgreSQL (skip for H2 in tests)
            if (!isPostgreSQL()) {
                logger.debug("Not using PostgreSQL, skipping extension setup");
                return;
            }

            logger.info("Initializing PostgreSQL extensions and indexes...");

            // Check if pg_trgm extension exists
            boolean extensionExists = checkExtensionExists("pg_trgm");

            if (!extensionExists) {
                logger.info("Creating pg_trgm extension...");
                try {
                    // CREATE EXTENSION IF NOT EXISTS is idempotent
                    entityManager.createNativeQuery("CREATE EXTENSION IF NOT EXISTS pg_trgm")
                            .executeUpdate();
                    logger.info("✓ pg_trgm extension created successfully");
                } catch (Exception e) {
                    // Extension creation requires superuser privileges
                    // If it fails, log warning but continue (graceful degradation)
                    logger.warn("⚠ Could not create pg_trgm extension. This requires superuser privileges.");
                    logger.warn("⚠ Recipe similarity checking will be disabled. To enable it, run:");
                    logger.warn("⚠   CREATE EXTENSION IF NOT EXISTS pg_trgm;");
                    logger.warn("⚠ Error: {}", e.getMessage());
                    return; // Don't try to create index if extension doesn't exist
                }
            } else {
                logger.debug("✓ pg_trgm extension already exists");
            }

            // Check if GIN index exists
            boolean indexExists = checkIndexExists("idx_recipes_name_trgm");

            if (!indexExists) {
                logger.info("Creating GIN index for recipe name similarity search...");
                try {
                    // CREATE INDEX IF NOT EXISTS is idempotent
                    entityManager.createNativeQuery("""
                            CREATE INDEX IF NOT EXISTS idx_recipes_name_trgm
                            ON recipes USING gin(name gin_trgm_ops)
                            """)
                            .executeUpdate();
                    logger.info("✓ GIN index created successfully");
                } catch (Exception e) {
                    logger.warn("⚠ Could not create GIN index: {}", e.getMessage());
                }
            } else {
                logger.debug("✓ GIN index already exists");
            }

            logger.info("✓ Database extensions and indexes initialized successfully");

        } catch (Exception e) {
            // Don't fail application startup if extension setup fails
            logger.error("Error initializing database extensions: {}", e.getMessage(), e);
            logger.warn("Application will continue, but recipe similarity checking may be disabled");
        }
    }

    /**
     * Checks if the database is PostgreSQL.
     */
    private boolean isPostgreSQL() {
        try {
            @SuppressWarnings("unchecked")
            List<Object> results = entityManager.createNativeQuery(
                    "SELECT version()")
                    .getResultList();
            if (!results.isEmpty()) {
                String version = results.get(0).toString();
                return version.toLowerCase().contains("postgresql");
            }
        } catch (Exception e) {
            logger.debug("Error checking database type: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Checks if a PostgreSQL extension exists.
     */
    private boolean checkExtensionExists(String extensionName) {
        try {
            @SuppressWarnings("unchecked")
            List<Object> results = entityManager.createNativeQuery(
                    "SELECT 1 FROM pg_extension WHERE extname = :name")
                    .setParameter("name", extensionName)
                    .getResultList();
            return !results.isEmpty();
        } catch (Exception e) {
            logger.debug("Error checking extension existence: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a PostgreSQL index exists.
     */
    private boolean checkIndexExists(String indexName) {
        try {
            @SuppressWarnings("unchecked")
            List<Object> results = entityManager.createNativeQuery(
                    "SELECT 1 FROM pg_indexes WHERE indexname = :name")
                    .setParameter("name", indexName)
                    .getResultList();
            return !results.isEmpty();
        } catch (Exception e) {
            logger.debug("Error checking index existence: {}", e.getMessage());
            return false;
        }
    }
}
