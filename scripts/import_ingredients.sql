-- Create ingredient_names table
CREATE TABLE IF NOT EXISTS ingredient_names (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    normalized_name VARCHAR(255) NOT NULL,
    frequency INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index for fast lookups
CREATE INDEX idx_ingredient_normalized ON ingredient_names(normalized_name);
CREATE INDEX idx_ingredient_name_lower ON ingredient_names(LOWER(name));

-- Import from CSV (run this after generating ingredients_extracted.csv)
-- COPY ingredient_names(name, frequency)
-- FROM '/path/to/ingredients_extracted.csv'
-- DELIMITER ','
-- CSV HEADER;

-- Update normalized names
UPDATE ingredient_names
SET normalized_name = LOWER(REGEXP_REPLACE(name, '[^a-zA-Z0-9]', '', 'g'));

-- Display statistics
SELECT
    COUNT(*) as total_ingredients,
    COUNT(DISTINCT normalized_name) as unique_normalized
FROM ingredient_names;

-- Show top 50 ingredients
SELECT name, frequency
FROM ingredient_names
ORDER BY frequency DESC
LIMIT 50;
