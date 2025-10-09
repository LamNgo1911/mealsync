package com.lamngo.mealsync.application.data;

import java.util.Set;

public class KnownIngredients {
    public static final Set<String> INGREDIENTS = Set.of(
            // A
            "acorn squash", "adobo sauce", "agave nectar", "alfalfa", "alfredo sauce", "almond butter", "almond extract",
            "almond flour", "almond milk", "amaranth", "amchoor", "anchovy", "anchovy paste", "anise", "apple",
            "apple cider vinegar", "apricot", "arborio rice", "arrowroot", "artichoke", "artichoke heart", "arugula",
            "asafoetida", "asafoetida powder", "asparagus", "avocado",

            // B
            "baby corn", "baby spinach", "bacon", "bacon bits", "bagel", "baguette", "baking powder", "baking soda",
            "balsamic vinegar", "bamboo shoots", "banana", "barley", "barley flour", "basil", "basil pesto", "bay leaf",
            "bean sprouts", "beef", "beef broth", "beef jerky", "beet", "beetroot", "bell pepper paste", "beluga lentil",
            "berries", "biscuit", "bison", "black bean", "black garlic", "black pepper", "black sesame seed",
            "black truffle", "black-eyed pea", "blue cheese", "blue cornmeal", "blueberry", "bluefish", "bok choy",
            "bread", "bread crumbs", "breadfruit", "brie cheese", "brined olives", "broccoli", "broccoli rabe", "broth",
            "brown lentil", "brown mustard seed", "brown rice", "brown sugar", "brussels sprout", "butter", "buttermilk",
            "butternut squash",

            // C
            "cabbage", "cabbage kimchi", "cactus", "cactus pear", "cake flour", "calamari", "calf liver", "camembert",
            "candied ginger", "cantaloupe", "cantaloupe melon", "capers", "caraway", "cardamom", "cardoon", "carob powder",
            "carrot", "cashew", "cashew nut", "cassava", "cauliflower", "cavatappi", "cayenne", "celeriac", "celery",
            "celery seed", "champagne vinegar", "chanterelle mushroom", "chapati", "char siu", "cheddar cheese", "cheese",
            "cheese curd", "cherry", "cherry tomato", "chervil", "chestnut", "chickpea", "chicory", "chili", "chili flake",
            "chili powder", "chile de arbol", "chipotle", "chive", "chive blossom", "chive leaf", "chocolate",
            "chocolate chips", "chocolate syrup", "cider vinegar", "cinnamon", "cinnamon stick", "citrus", "clam",
            "clotted cream", "clove", "cocoa powder", "coconut", "coconut cream", "coconut milk", "coconut oil",
            "coconut sugar", "cod", "coffee", "coffee bean", "cognac", "collagen powder", "collard greens", "confit duck",
            "cooked ham", "cooked quinoa", "cooked rice", "corn", "corn flour", "cornmeal", "cornstarch", "cotija cheese",
            "cottage cheese", "crab", "cranberry", "cranberry sauce", "cream", "cream cheese", "creme fraiche",
            "crimini mushroom", "crouton", "crystallized ginger", "cucumber", "cucumber pickle", "cumin", "curd", "currant",
            "curry powder",

            // D
            "daikon", "dark chocolate", "dark rum", "dashi", "dashi stock", "date", "date paste", "deer meat", "dijon mustard",
            "dill", "dried apricot", "dried basil", "dried blueberry", "dried cherry", "dried coconut", "dried cranberry",
            "dried fig", "dried mushroom", "dried orange peel", "dried parsley", "dried plum", "dried rosemary", "dried thyme",
            "dried tomato", "duck", "duck confit", "dulse",

            // E
            "edamame", "edible flowers", "egg", "egg noodles", "egg white", "egg yolk", "eggplant", "emmental cheese",
            "enchilada sauce", "endive", "escarole", "evaporated milk",

            // F
            "farfalle", "farro", "fava bean", "fennel", "fenugreek", "fenugreek leaf", "feta cheese", "fiddlehead",
            "fiddlehead fern", "fig", "fillet fish", "fire roasted tomato", "fish", "fish sauce", "flat leaf parsley",
            "flax meal", "flaxseed", "flour", "flour tortilla", "fontina cheese", "french beans", "fresno chili",
            "frozen spinach", "frozen yam", "frisée", "fruit", "fruit puree", "fuyu persimmon",

            // G
            "galangal", "galangal root", "garam masala", "garlic", "garlic powder", "gelatin", "ghee", "ghee butter",
            "ginger", "ginger paste", "gluten-free flour", "gluten-free oats", "goat cheese", "goat milk", "golden raisin",
            "goose", "gooseberry", "grape", "grapefruit", "grated coconut", "green bean", "green bell pepper",
            "green cabbage", "green chili", "green chili pepper", "green lentil", "green onion", "green pea", "green tomato",
            "grits", "ground beef", "ground cumin", "ground flaxseed", "ground pork", "ground turkey", "gruyere cheese",
            "guajillo chili", "guava", "guava paste",

            // H
            "habanero", "half and half", "hazelnut", "hazelnut oil", "honey", "honey mustard", "honeydew", "horseradish",
            "hot sauce", "huckleberry", "huitlacoche",

            // I
            "ice cream", "iceberg lettuce", "italian sausage",

            // J
            "jack cheese", "jackfruit", "jaggery", "jaggery syrup", "jalapeno", "japanese pumpkin", "jerusalem artichoke",
            "jicama",

            // K
            "kabocha", "kale", "kale powder", "kefir", "kefir cheese", "ketchup", "kidney bean", "kimchi", "kohlrabi",
            "kohlrabi bulb", "kombu", "kumquat",

            // L
            "lamb", "lamb chop", "lamb shank", "lard", "lasagna noodle", "leek", "lemon", "lemon balm", "lemon grass",
            "lemon zest", "lemongrass paste", "lentil", "lentil flour", "lettuce", "lettuce mix", "lime", "lime juice",
            "lime zest", "linguine", "lobster tail", "london broil", "long grain rice", "longan", "lotus root", "lump crab",
            "lychee",

            // M
            "macadamia butter", "macadamia nut", "mackerel", "mandarin orange", "mango", "mango puree", "mangosteen",
            "maple sugar", "maple syrup", "maracuja", "margarine", "marinara sauce", "marjoram", "marmalade", "masa harina",
            "matzo meal", "mayhaw", "mayonnaise", "meatball", "meatloaf", "melon", "melon ball", "milk", "milk chocolate",
            "milk powder", "milk thistle", "miso", "mizuna", "mochi", "mozzarella ball", "mozzarella cheese", "mulberry",
            "mulukhiyah", "mushroom", "mushroom powder", "mustard", "mustard green", "mustard seed",

            // N
            "napa cabbage", "navy bean", "navy bean paste", "nectarine", "nectarine puree", "nori", "nut butter", "nutmeg",
            "nutmeg powder",

            // O
            "oat", "oat bran", "oat flour", "oat groats", "oat milk", "oat milk beverage", "okra", "okra seed", "olive",
            "olive oil", "olive tapenade", "onion", "onion powder", "onion ring", "orange", "orange blossom",
            "orange marmalade", "orange zest", "oregano", "oregano leaf", "orzo",

            // P
            "palm sugar", "pandan leaf", "papaya", "papaya puree", "paprika", "paprika powder", "parmesan cheese",
            "parsley", "parsley flakes", "parsnip", "parsnip puree", "parsnip root", "passion fruit", "passionfruit pulp",
            "pasta", "pasta sheet", "peach", "peach puree", "peanut", "peanut butter", "peanut flour", "peanut oil",
            "peanut oil roasted", "pear", "pearl barley", "pearl onion", "peas", "pecan", "pecorino", "pecorino romano",
            "pepper", "peppermint", "peppermint extract", "persimmon", "pico de gallo", "pine nut", "pineapple",
            "pineapple juice", "pinto bean", "pistachio", "pistachio paste", "plum", "plum puree", "poblano",
            "poblano chili", "pomegranate juice", "poppy seed", "pork", "pork belly", "port wine", "portobello", "potato",
            "potato starch", "prosciutto", "prune", "pumpkin", "pumpkin puree", "pumpkin seed", "purple cabbage",

            // Q
            "quail egg", "quail meat", "quinoa", "quinoa flour",

            // R
            "radicchio", "radish", "radish sprouts", "rainbow chard", "raisin", "raspberry", "raspberry puree",
            "red bell chili", "red bell pepper", "red cabbage", "red chili", "red lentil", "red onion", "red pepper",
            "red pepper flakes", "reduced fat milk", "reindeer meat", "relish", "rice", "ricotta", "ricotta salata",
            "risotto rice", "roasted almond", "roasted garlic", "romaine lettuce", "rosemary", "rosemary extract",
            "rutabaga", "rutabaga puree", "rye", "rye flour",

            // S
            "safflower oil", "saffron", "sage", "sage leaf", "salami", "salmon", "salsa", "salt", "sardine", "scallion",
            "sea bass", "seaweed", "sesame", "sesame oil", "shallot", "sherbet", "shrimp", "smoked salmon", "snap pea",
            "sorrel", "soy milk", "soy sauce", "spinach", "squid", "stevia", "sugar", "sunflower seed", "sweet potato",
            "swiss chard",

            // T
            "tahini", "tamarind", "tangerine", "tapioca", "teff", "thyme", "tofu", "tomatillo", "tomato", "tomato paste",
            "tomato sauce", "trout", "tuna", "turkey", "turmeric", "turnip",

            // V
            "vanilla extract", "veal", "vegetable oil", "vinegar",

            // W
            "wasabi", "watercress", "watermelon", "wheat", "wheat flour", "white bean", "white chocolate", "white onion",
            "white rice", "wine vinegar",

            // Y
            "yogurt",

            // Z
            "zucchini"
    );
}