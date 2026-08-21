package com.venkoi.cuentame.core.model.catalog

import java.math.BigDecimal

object CubanFoodiesStarterCatalog {

    val definition = StarterCatalogDefinition(
        key = "cuban_foodies",
        version = 1,
        categories = listOf(
            StarterCategoryDefinition("Produce", 1),
            StarterCategoryDefinition("Meat & Seafood", 2),
            StarterCategoryDefinition("Dairy", 3),
            StarterCategoryDefinition("Dry Goods", 4),
            StarterCategoryDefinition("Bread & Bakery", 5),
            StarterCategoryDefinition("Beverages", 6),
            StarterCategoryDefinition("Packaging", 7),
            StarterCategoryDefinition("Cleaning", 8),
            StarterCategoryDefinition("Miscellaneous", 9)
        ),
        items = listOf(
            // Produce - mass_lb
            item("Produce", "Maya Sweet Plantains (24 lb case)", "mass_lb", "Pound", "lb", 
                options = listOf(pkg("24 lb case", "case", 24, purchase = true))),
            item("Produce", "Zucchini", "mass_lb", "Pound", "lb"),
            item("Produce", "Yuca (65 lb)", "mass_lb", "Pound", "lb",
                options = listOf(pkg("65 lb case", "case", 65, purchase = true))),
            item("Produce", "Yellow Onions (50 lb)", "mass_lb", "Pound", "lb",
                options = listOf(pkg("50 lb bag", "bag", 50, purchase = true))),
            item("Produce", "Roma Tomatoes (25 lb)", "mass_lb", "Pound", "lb",
                options = listOf(pkg("25 lb case", "case", 25, purchase = true))),
            item("Produce", "Red Bell Peppers", "mass_lb", "Pound", "lb"),
            item("Produce", "Russet Potatoes (10 lb)", "mass_lb", "Pound", "lb",
                options = listOf(pkg("10 lb bag", "bag", 10, purchase = true))),
            item("Produce", "Garlic", "mass_lb", "Pound", "lb"),
            item("Produce", "Frozen Peas & Carrots", "mass_lb", "Pound", "lb"),
            item("Produce", "Frozen Spinach", "mass_lb", "Pound", "lb"),
            // Produce - count_each
            item("Produce", "Lemons", "count_each", "Each", "ea"),
            item("Produce", "Limes", "count_each", "Each", "ea"),
            // Produce - mass_oz
            item("Produce", "Cilantro", "mass_oz", "Ounce", "oz"),
            item("Produce", "Chives", "mass_oz", "Ounce", "oz"),
            item("Produce", "Mint", "mass_oz", "Ounce", "oz"),

            // Meat & Seafood - mass_lb
            item("Meat & Seafood", "Boneless Chicken Breast", "mass_lb", "Pound", "lb"),
            item("Meat & Seafood", "Boneless Chicken Thighs", "mass_lb", "Pound", "lb"),
            item("Meat & Seafood", "Chicken Leg Quarters", "mass_lb", "Pound", "lb"),
            item("Meat & Seafood", "Pork Butt (Boneless)", "mass_lb", "Pound", "lb"),
            item("Meat & Seafood", "Pork Butt (Bone-In)", "mass_lb", "Pound", "lb"),
            item("Meat & Seafood", "Smoked Ham", "mass_lb", "Pound", "lb"),
            item("Meat & Seafood", "Prosciutto", "mass_lb", "Pound", "lb"),
            item("Meat & Seafood", "Argentine Shrimp 16/20", "mass_lb", "Pound", "lb"),
            item("Meat & Seafood", "Beef Ball Tips", "mass_lb", "Pound", "lb"),
            item("Meat & Seafood", "Bacon", "mass_lb", "Pound", "lb"),
            item("Meat & Seafood", "Genoa Salami", "mass_lb", "Pound", "lb"),

            // Dairy - count_each
            item("Dairy", "American Cheese Slices", "count_each", "Each", "ea"),
            item("Dairy", "Eggs XL (15 Dozen)", "count_each", "Each", "ea",
                options = listOf(
                    pkg("Dozen", "doz", 12),
                    pkg("15 dozen case", "case", 180, purchase = true)
                )),
            item("Dairy", "Evaporated Milk", "count_each", "Each", "ea"),
            // Dairy - mass_lb
            item("Dairy", "Swiss Cheese", "mass_lb", "Pound", "lb"),
            item("Dairy", "Ricotta Cheese", "mass_lb", "Pound", "lb"),
            item("Dairy", "Cream Cheese", "mass_lb", "Pound", "lb"),
            item("Dairy", "Butter", "mass_lb", "Pound", "lb"),
            item("Dairy", "Butter Blend", "mass_lb", "Pound", "lb"),

            // Dry Goods - mass_lb
            item("Dry Goods", "Jasmine Rice (50 lb)", "mass_lb", "Pound", "lb",
                options = listOf(pkg("50 lb bag", "bag", 50, purchase = true))),
            item("Dry Goods", "Panko Bread Crumbs", "mass_lb", "Pound", "lb"),
            item("Dry Goods", "Black Beans (Cardenal)", "mass_lb", "Pound", "lb"),
            // Dry Goods - mass_oz
            item("Dry Goods", "Paprika", "mass_oz", "Ounce", "oz"),
            item("Dry Goods", "Whole Cumin Seed", "mass_oz", "Ounce", "oz"),
            item("Dry Goods", "Bay Leaves", "mass_oz", "Ounce", "oz"),
            // Dry Goods - volume_gallon_us
            item("Dry Goods", "Olive Oil", "volume_gallon_us", "Gallon", "gal"),
            item("Dry Goods", "Pure Olive Oil", "volume_gallon_us", "Gallon", "gal"),
            item("Dry Goods", "Frying Oil", "volume_gallon_us", "Gallon", "gal"),
            item("Dry Goods", "Soybean Oil", "volume_gallon_us", "Gallon", "gal"),
            item("Dry Goods", "White Vinegar", "volume_gallon_us", "Gallon", "gal"),
            item("Dry Goods", "Cooking Wine", "volume_gallon_us", "Gallon", "gal"),
            // Dry Goods - count_each
            item("Dry Goods", "Crushed Tomatoes #10", "count_each", "Each", "ea"),
            item("Dry Goods", "Black Olives #10", "count_each", "Each", "ea"),
            item("Dry Goods", "Mayonnaise", "count_each", "Each", "ea"),
            item("Dry Goods", "Heinz Ketchup", "count_each", "Each", "ea"),
            item("Dry Goods", "Pickle Chips", "count_each", "Each", "ea"),

            // Bread & Bakery - count_each
            item("Bread & Bakery", "Cuban Bread 36 in", "count_each", "Each", "ea"),
            item("Bread & Bakery", "Media Noche 8 in", "count_each", "Each", "ea"),
            item("Bread & Bakery", "Glazed Donuts", "count_each", "Each", "ea"),

            // Beverages - count_each
            item("Beverages", "Coca-Cola Mexican", "count_each", "Each", "ea"),
            item("Beverages", "Diet Coke", "count_each", "Each", "ea"),
            item("Beverages", "Jarritos Mandarin", "count_each", "Each", "ea"),
            item("Beverages", "Jarritos Mineral", "count_each", "Each", "ea"),
            item("Beverages", "Ironbeer", "count_each", "Each", "ea"),
            item("Beverages", "Jupiña", "count_each", "Each", "ea"),
            item("Beverages", "Disco Chicho Cubanita", "count_each", "Each", "ea"),
            item("Beverages", "Tropicana Orange Blend", "count_each", "Each", "ea"),
            item("Beverages", "Tropicana Apple Juice", "count_each", "Each", "ea"),
            item("Beverages", "Zephyrhills Water", "count_each", "Each", "ea"),

            // Packaging - count_each
            item("Packaging", "Kraft 6 in Hinged Containers", "count_each", "Each", "ea"),
            item("Packaging", "Kraft 8 in Hinged Containers", "count_each", "Each", "ea"),
            item("Packaging", "Soup Containers", "count_each", "Each", "ea"),
            item("Packaging", "16 oz PET Cups", "count_each", "Each", "ea"),
            item("Packaging", "PET Lids", "count_each", "Each", "ea"),
            item("Packaging", "Foam Containers", "count_each", "Each", "ea"),
            item("Packaging", "Foam Container Lids", "count_each", "Each", "ea"),
            item("Packaging", "Brown Bags", "count_each", "Each", "ea"),
            item("Packaging", "Plastic Bags", "count_each", "Each", "ea"),
            item("Packaging", "Paper Bags", "count_each", "Each", "ea"),
            item("Packaging", "Plastic Film", "count_each", "Each", "ea"),
            item("Packaging", "Aluminum Foil", "count_each", "Each", "ea"),
            item("Packaging", "Napkins", "count_each", "Each", "ea"),
            item("Packaging", "Drinking Straws", "count_each", "Each", "ea"),

            // Cleaning - count_each
            item("Cleaning", "Blue Suds", "count_each", "Each", "ea"),
            item("Cleaning", "Skyline Satin Hand Soap", "count_each", "Each", "ea"),
            item("Cleaning", "Nitrile Gloves (Medium)", "count_each", "Each", "ea"),
            item("Cleaning", "Nitrile Gloves (XL)", "count_each", "Each", "ea"),
            item("Cleaning", "Latex Gloves XL", "count_each", "Each", "ea"),
            item("Cleaning", "Center Pull Towels", "count_each", "Each", "ea"),
            item("Cleaning", "Roll Towels", "count_each", "Each", "ea"),
            item("Cleaning", "Grill Bricks", "count_each", "Each", "ea"),
            item("Cleaning", "Griddle Scrubbers", "count_each", "Each", "ea"),
            item("Cleaning", "Masking Tape", "count_each", "Each", "ea"),
            item("Cleaning", "Trash Liners", "count_each", "Each", "ea")
        )
    )

    private fun item(
        category: String,
        name: String,
        unitId: String,
        label: String,
        shortLabel: String,
        options: List<StarterUnitOptionDefinition> = emptyList()
    ): StarterItemDefinition {
        return StarterItemDefinition(
            sourceCategoryName = category,
            name = name,
            baseUnitId = unitId,
            baseOptionLabel = label,
            baseOptionShortLabel = shortLabel,
            additionalUnitOptions = options
        )
    }

    private fun pkg(
        label: String,
        shortLabel: String,
        factor: Int,
        purchase: Boolean = false
    ): StarterUnitOptionDefinition {
        return StarterUnitOptionDefinition(
            displayName = label,
            shortLabel = shortLabel,
            factorToBase = BigDecimal(factor),
            isDefaultCount = false,
            isDefaultPurchase = purchase
        )
    }
}
