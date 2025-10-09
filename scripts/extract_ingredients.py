#!/usr/bin/env python3
"""
Extract unique ingredients from Kaggle Food.com dataset
Outputs a clean CSV file with ingredient names for import into PostgreSQL
"""

import pandas as pd
import json
import re
from collections import Counter

def clean_ingredient_name(ingredient):
    """Clean and normalize ingredient name"""
    # Convert to lowercase
    ingredient = ingredient.lower().strip()

    # Remove quantities and measurements
    measurements = [
        'cup', 'cups', 'tablespoon', 'tablespoons', 'tbsp', 'teaspoon', 'teaspoons', 'tsp',
        'pound', 'pounds', 'lb', 'lbs', 'ounce', 'ounces', 'oz', 'gram', 'grams', 'g',
        'kilogram', 'kilograms', 'kg', 'milliliter', 'milliliters', 'ml', 'liter', 'liters',
        'pinch', 'dash', 'clove', 'cloves', 'package', 'packages', 'can', 'cans',
        'slice', 'slices', 'piece', 'pieces', 'bunch', 'bunches'
    ]

    # Remove numbers and fractions
    ingredient = re.sub(r'\d+[\s\/\-\d]*', '', ingredient)

    # Remove measurements
    for measure in measurements:
        ingredient = re.sub(r'\b' + measure + r'\b', '', ingredient, flags=re.IGNORECASE)

    # Remove common preparation words
    prep_words = [
        'fresh', 'dried', 'frozen', 'canned', 'chopped', 'diced', 'minced', 'sliced',
        'grated', 'shredded', 'melted', 'softened', 'beaten', 'crushed', 'ground',
        'peeled', 'seeded', 'trimmed', 'optional', 'to taste', 'as needed',
        'large', 'small', 'medium', 'whole', 'half', 'quarter'
    ]

    for word in prep_words:
        ingredient = re.sub(r'\b' + word + r'\b', '', ingredient, flags=re.IGNORECASE)

    # Remove special characters except spaces and hyphens
    ingredient = re.sub(r'[^\w\s\-]', '', ingredient)

    # Remove extra whitespace
    ingredient = ' '.join(ingredient.split())

    return ingredient.strip()

def extract_ingredients_from_csv(csv_file):
    """Extract ingredients from the CSV file"""
    print(f"Reading {csv_file}...")

    # Read the CSV
    df = pd.read_csv(csv_file)

    print(f"Loaded {len(df)} recipes")
    print(f"Columns: {df.columns.tolist()}")

    all_ingredients = []

    # The ingredient column might be named 'ingredients' and stored as a list/json
    if 'ingredients' in df.columns:
        for idx, row in df.iterrows():
            if idx % 10000 == 0:
                print(f"Processing recipe {idx}/{len(df)}")

            ingredients = row['ingredients']

            # Handle different formats
            if pd.isna(ingredients):
                continue

            # If it's a string representation of a list
            if isinstance(ingredients, str):
                try:
                    # Try to parse as JSON
                    ingredients = json.loads(ingredients.replace("'", '"'))
                except:
                    # If not JSON, try to split by common delimiters
                    ingredients = re.split(r'[,;\n]', ingredients)

            # Process each ingredient
            if isinstance(ingredients, list):
                for ing in ingredients:
                    if isinstance(ing, str) and ing.strip():
                        cleaned = clean_ingredient_name(ing)
                        if cleaned and len(cleaned) > 2:  # Ignore very short names
                            all_ingredients.append(cleaned)

    return all_ingredients

def main():
    # Process the main recipes file
    csv_file = 'RAW_recipes.csv'  # Or 'PP_recipes.csv'

    print("=" * 60)
    print("Extracting Ingredients from Food.com Dataset")
    print("=" * 60)

    # Extract ingredients
    all_ingredients = extract_ingredients_from_csv(csv_file)

    print(f"\nTotal ingredient mentions: {len(all_ingredients)}")

    # Count occurrences
    ingredient_counts = Counter(all_ingredients)

    print(f"Unique ingredients: {len(ingredient_counts)}")

    # Create DataFrame with frequency
    ingredients_df = pd.DataFrame([
        {'name': ing, 'frequency': count}
        for ing, count in ingredient_counts.most_common()
    ])

    # Filter out very rare ingredients (appear only once, might be noise)
    # You can adjust this threshold
    MIN_FREQUENCY = 2
    ingredients_df_filtered = ingredients_df[ingredients_df['frequency'] >= MIN_FREQUENCY]

    print(f"Ingredients after filtering (frequency >= {MIN_FREQUENCY}): {len(ingredients_df_filtered)}")

    # Save to CSV
    output_file = 'ingredients_extracted.csv'
    ingredients_df_filtered.to_csv(output_file, index=False)
    print(f"\nSaved to: {output_file}")

    # Also save top 1000 most common
    top_1000_file = 'ingredients_top1000.csv'
    ingredients_df_filtered.head(1000).to_csv(top_1000_file, index=False)
    print(f"Saved top 1000 to: {top_1000_file}")

    # Print some statistics
    print("\n" + "=" * 60)
    print("Top 20 Most Common Ingredients:")
    print("=" * 60)
    for idx, row in ingredients_df_filtered.head(20).iterrows():
        print(f"{idx+1:2d}. {row['name']:30s} (appears {row['frequency']:,} times)")

if __name__ == "__main__":
    main()
