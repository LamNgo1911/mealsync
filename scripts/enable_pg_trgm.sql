-- Enable PostgreSQL pg_trgm extension for fast fuzzy text matching
-- This extension provides the similarity() function used for recipe name matching
-- Run this script once on your PostgreSQL database

-- Enable the extension (requires superuser privileges)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Create GIN index on recipe name for fast similarity searches
-- This index dramatically speeds up similarity() queries
CREATE INDEX IF NOT EXISTS idx_recipes_name_trgm 
ON recipes USING gin(name gin_trgm_ops);

-- Verify the extension is enabled
SELECT * FROM pg_extension WHERE extname = 'pg_trgm';

-- Test the similarity function (optional - can be removed)
-- SELECT similarity('Chicken Stir Fry', 'Stir Fry Chicken') AS similarity_score;
-- Expected: ~0.7-0.9 (70-90% similar)

