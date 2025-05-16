package com.lamngo.mealsync.application.data;

import java.util.Set;

public class KnownIngredients {
    public static final Set<String> INGREDIENTS = Set.of(
            // Vegetables
            "tomato", "potato", "onion", "garlic", "carrot", "broccoli", "cauliflower", "spinach", "lettuce",
            "zucchini", "eggplant", "cucumber", "bell pepper", "chili", "sweet potato", "pumpkin", "cabbage",
            "kale", "asparagus", "green bean", "pea", "corn", "radish", "leek", "celery", "beetroot", "artichoke",

            // Fruits
            "apple", "banana", "orange", "lemon", "lime", "grape", "strawberry", "blueberry", "raspberry",
            "mango", "pineapple", "watermelon", "melon", "pear", "peach", "plum", "apricot", "kiwi", "avocado",
            "pomegranate", "fig", "coconut", "cherry", "grapefruit",

            // Dairy
            "milk", "cheese", "cream", "yogurt", "butter", "ghee", "condensed milk", "sour cream",

            // Meat & Seafood
            "chicken", "beef", "pork", "lamb", "duck", "turkey", "bacon", "sausage",
            "fish", "salmon", "tuna", "cod", "shrimp", "crab", "lobster", "squid", "clam", "mussel", "octopus",

            // Eggs & Protein
            "egg", "tofu", "tempeh", "seitan", "chickpea", "lentil", "black bean", "kidney bean", "white bean",

            // Grains & Carbs
            "rice", "pasta", "bread", "flour", "quinoa", "oat", "barley", "couscous", "bulgur", "noodle", "tortilla",

            // Baking & Cooking Essentials
            "sugar", "salt", "pepper", "baking soda", "baking powder", "yeast", "vinegar", "oil", "olive oil",
            "canola oil", "vegetable oil", "honey", "maple syrup", "molasses",

            // Sauces & Condiments
            "soy sauce", "ketchup", "mustard", "mayonnaise", "hot sauce", "sriracha", "bbq sauce", "hoisin sauce",
            "fish sauce", "teriyaki sauce", "tahini", "pesto", "tomato sauce",

            // Herbs & Spices
            "basil", "parsley", "cilantro", "mint", "oregano", "thyme", "rosemary", "dill", "chive", "bay leaf",
            "cinnamon", "nutmeg", "clove", "coriander", "cardamom", "paprika", "turmeric", "cumin", "ginger",
            "saffron", "fennel", "anise", "mustard seed", "curry powder", "allspice",

            // Nuts & Seeds
            "walnut", "almond", "peanut", "cashew", "hazelnut", "pistachio", "macadamia", "pecan",
            "chia seed", "flaxseed", "sesame", "sunflower seed", "pumpkin seed",

            // Misc
            "stock", "broth", "gelatin", "cocoa powder", "chocolate", "vanilla extract", "coffee", "tea",
            "lime zest", "lemon zest", "cornstarch", "arrowroot", "miso", "wasabi", "pickles", "capers"
    );
}
