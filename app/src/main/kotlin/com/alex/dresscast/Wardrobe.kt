package com.alex.dresscast

import com.alex.dresscast.L.t

/**
 * The clothes you own. Prefs.noItems holds the keys you DON'T have (empty = you have everything),
 * so the advice only suggests your own clothes and finds an alternative when something is missing.
 */
object Wardrobe {
    data class Piece(val key: String, val en: String, val el: String) {
        val name get() = t(en, el)
    }

    data class Group(val en: String, val el: String, val pieces: List<Piece>) {
        val name get() = t(en, el)
    }

    val groups = listOf(
        Group("Outerwear", "Επανωφόρια", listOf(
            Piece("cardigan", "Cardigan", "Ζακέτα"),
            Piece("lightjacket", "Light jacket", "Ελαφρύ μπουφάν"),
            Piece("raincoat", "Rain jacket", "Αδιάβροχο"),
            Piece("windbreaker", "Windbreaker", "Αντιανεμικό"),
            Piece("winterjacket", "Winter jacket", "Χειμερινό μπουφάν"),
            Piece("coat", "Wool coat", "Μάλλινο παλτό"),
        )),
        Group("Clothes", "Ρούχα", listOf(
            Piece("tshirt", "T-shirt", "Κοντομάνικο"),
            Piece("longsleeve", "Long sleeves", "Μακρυμάνικο"),
            Piece("hoodie", "Hoodie", "Φούτερ"),
            Piece("sweater", "Sweater", "Πουλόβερ"),
            Piece("jeans", "Jeans", "Τζιν"),
            Piece("trousers", "Trousers", "Παντελόνι"),
            Piece("linen", "Light trousers", "Λινό παντελόνι"),
            Piece("shorts", "Shorts", "Σορτς"),
            Piece("thermal", "Thermals", "Ισοθερμικά"),
        )),
        Group("Shoes", "Παπούτσια", listOf(
            Piece("sneakers", "Sneakers", "Αθλητικά"),
            Piece("sandals", "Sandals", "Σανδάλια"),
            Piece("boots", "Boots", "Μποτάκια"),
            Piece("waterproof", "Waterproof shoes", "Αδιάβροχα"),
        )),
        Group("Accessories", "Αξεσουάρ", listOf(
            Piece("umbrella", "Umbrella", "Ομπρέλα"),
            Piece("cap", "Cap or hat", "Καπέλο"),
            Piece("glasses", "Sunglasses", "Γυαλιά ηλίου"),
            Piece("scarf", "Scarf", "Κασκόλ"),
            Piece("beanie", "Beanie", "Σκούφος"),
            Piece("gloves", "Gloves", "Γάντια"),
            Piece("sunscreen", "Sunscreen", "Αντηλιακό"),
        )),
    )

    val all get() = groups.flatMap { it.pieces }

    /** Older versions had one "boots" switch for boots and waterproof shoes. */
    fun migrate(p: Prefs) {
        if (p.wardrobeV >= 1) return
        if ("boots" in p.off) p.noItems = (p.off + "waterproof").joinToString(",")
        p.wardrobeV = 1
    }

    fun toggle(p: Prefs, key: String) {
        val off = p.off
        p.noItems = (if (key in off) off - key else off + key).joinToString(",")
    }
}
