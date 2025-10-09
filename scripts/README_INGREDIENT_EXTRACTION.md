# Ingredient Dataset Extraction Guide

## Overview
This guide helps you extract a comprehensive ingredient dataset from Kaggle's Food.com dataset for use in MealSync.

## Prerequisites

1. **Kaggle Account**: Sign up at https://www.kaggle.com
2. **Python 3.7+**: Ensure Python is installed
3. **Required Python packages**:
   ```bash
   pip install pandas kaggle
   ```

## Step-by-Step Instructions

### 1. Setup Kaggle API

1. Go to https://www.kaggle.com/settings
2. Scroll to "API" section
3. Click "Create New API Token"
4. Download `kaggle.json`
5. Move it to the correct location:
   ```bash
   mkdir -p ~/.kaggle
   mv ~/Downloads/kaggle.json ~/.kaggle/
   chmod 600 ~/.kaggle/kaggle.json
   ```

### 2. Download the Dataset

```bash
# Navigate to your project scripts directory
cd /home/liam/Downloads/github_repo/mealsync/scripts

# Download the Food.com dataset
kaggle datasets download -d shuyangli94/food-com-recipes-and-user-interactions

# Unzip it
unzip food-com-recipes-and-user-interactions.zip
```

**Expected files:**
- `RAW_recipes.csv` - Raw recipe data with ingredients
- `RAW_interactions.csv` - User interactions
- `PP_recipes.csv` - Preprocessed recipes
- `PP_users.csv` - Preprocessed users

### 3. Run the Extraction Script

```bash
# Make the script executable
chmod +x extract_ingredients.py

# Run it
python3 extract_ingredients.py
```

**Output files:**
- `ingredients_extracted.csv` - All unique ingredients (10k-20k)
- `ingredients_top1000.csv` - Top 1000 most common ingredients

### 4. Import into PostgreSQL

#### Option A: Using psql command

```bash
# Connect to your database
psql -U postgres -d mealsync

# Run the SQL script
\i scripts/import_ingredients.sql

# Import the CSV
\copy ingredient_names(name, frequency) FROM 'scripts/ingredients_extracted.csv' DELIMITER ',' CSV HEADER;
```

#### Option B: Using pgAdmin or DBeaver

1. Open your database tool
2. Create the table using `import_ingredients.sql`
3. Use the import wizard to load `ingredients_extracted.csv`

### 5. Verify the Import

```sql
-- Check total count
SELECT COUNT(*) FROM ingredient_names;

-- View top 20 ingredients
SELECT name, frequency FROM ingredient_names ORDER BY frequency DESC LIMIT 20;

-- Test search
SELECT * FROM ingredient_names WHERE name LIKE '%tomato%';
```

## Expected Results

After extraction, you should have:

- **~10,000-20,000 unique ingredients** (filtered)
- **~50,000+ ingredients** (unfiltered)
- Ingredients ranked by frequency
- Common cooking ingredients well-represented

### Top Ingredients (Expected)

1. salt
2. sugar
3. butter
4. eggs
5. flour
6. water
7. oil
8. milk
9. pepper
10. onion
... and many more

## Troubleshooting

### Issue: "RAW_recipes.csv not found"

**Solution**: The dataset might use different filenames. Check what's in the zip:
```bash
ls -la *.csv
```

Update the `csv_file` variable in `extract_ingredients.py` accordingly.

### Issue: Different CSV structure

If the ingredients column has a different name, check the CSV structure:
```python
import pandas as pd
df = pd.read_csv('RAW_recipes.csv', nrows=5)
print(df.columns)
print(df.head())
```

### Issue: Ingredients not parsing correctly

The ingredients might be in different formats:
- JSON array: `["salt", "pepper"]`
- Comma-separated: `"salt, pepper, garlic"`
- Line-separated: `"salt\npepper\ngarlic"`

The script handles all these formats, but you may need to adjust the parsing logic.

## Alternative: Use Pre-extracted List

If the Kaggle download doesn't work, you can use these alternatives:

1. **USDA Food List** (Simple, 350k items):
   - https://fdc.nal.usda.gov/download-datasets.html
   - Download "Foundation Foods" CSV
   - Extract the "description" column

2. **GitHub Ingredient Lists**:
   ```bash
   # Simple ingredient list (1,000 items)
   curl https://raw.githubusercontent.com/schollz/ingredients/master/ingredients.txt > ingredients.txt

   # Convert to CSV
   echo "name,frequency" > ingredients_simple.csv
   cat ingredients.txt | awk '{print $0",1"}' >> ingredients_simple.csv
   ```

## Next Steps

After importing ingredients:

1. **Create Ingredient Validation Service** (see main project docs)
2. **Integrate with Google Vision AI** for ingredient detection
3. **Add fuzzy matching** for typo tolerance
4. **Add ingredient autocomplete** in your UI

## Need Help?

Check the main project documentation or create an issue on GitHub.
