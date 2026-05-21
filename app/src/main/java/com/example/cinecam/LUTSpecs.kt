package com.example.cinecam

import androidx.compose.ui.graphics.ColorMatrix

enum class LutCategory {
    CINEMATIC, GLOOMY, VINTAGE, VIBRANT_ANIME, CYBERPUNK, MONOCHROME
}

data class LutSpec(
    val id: String,
    val name: String,
    val category: LutCategory,
    val description: String,
    val rScale: Float = 1f,
    val gScale: Float = 1f,
    val bScale: Float = 1f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val brightnessOffset: Float = 0f,
    // Tint tint offsets
    val rOffset: Float = 0f,
    val gOffset: Float = 0f,
    val bOffset: Float = 0f,
    // Custom matrix mixing coefficients
    val matrixMix: FloatArray? = null
) {
    // Generate an adjusted ColorMatrix that auto-adapts to camera/sensor specs
    fun getComposerMatrix(
        sensorBrightness: Float, // -1f to 1f (representing exposure/compensation)
        sensorNoise: Float,      // 0f to 1f (representing ISO sensor noise)
        userIntensity: Float,    // 0f to 1f (user blending dial)
        kelvinTemp: Float,       // 2000K to 12000K (adjusts Blue-Yellow tint)
        kelvinTint: Float        // -50f to 50f (adjusts Green-Magenta balance)
    ): ColorMatrix {
        // 1. Calculate color temperature factors based on Kelvin Temp
        // Cooler (low Kelvin) adds blue, warmer (high Kelvin) adds red/yellow
        val tFactor = (kelvinTemp - 6500f) / 4500f // -1 to 1.2
        val tempR = if (tFactor > 0) 1f + tFactor * 0.15f else 1f + tFactor * 0.05f
        val tempG = 1f - kelvinTint * 0.003f
        val tempB = if (tFactor < 0) 1f - tFactor * 0.25f else 1f - tFactor * 0.08f

        // 2. Camera specs auto-adjust logic.
        // If sensor noise (ISO) is high, we soften the LUT's contrast and saturation to prevent severe color noise clipping.
        // If image brightness is too high, we compress highlights (protect highlights).
        val adjustedNoiseContrast = if (sensorNoise > 0.5f) {
            contrast * (1f - (sensorNoise - 0.5f) * 0.4f)
        } else {
            contrast
        }
        val adjustedNoiseSat = if (sensorNoise > 0.6f) {
            saturation * (1f - (sensorNoise - 0.6f) * 0.3f)
        } else {
            saturation
        }

        // Apply exposure highlight compression
        val highlightCompress = if (sensorBrightness > 0.4f) {
            -0.12f * (sensorBrightness - 0.4f)
        } else {
            0f
        }

        // Blend LUT values with normal identity values based on intensity (0 to 1)
        val finalR = (1f - userIntensity) + (rScale * tempR) * userIntensity
        val finalG = (1f - userIntensity) + (gScale * tempG) * userIntensity
        val finalB = (1f - userIntensity) + (bScale * tempB) * userIntensity

        val finalCont = (1f - userIntensity) + adjustedNoiseContrast * userIntensity
        val finalSat = (1f - userIntensity) + adjustedNoiseSat * userIntensity
        val finalBright = (brightnessOffset + highlightCompress) * userIntensity

        // Synthesize ColorMatrix details
        val matrix = ColorMatrix()

        // Apply contrast matrix
        // Contrast calculation in matrix format:
        // x' = (x - 0.5) * contrast + 0.5 + brightnessOffset
        val t = (1f - finalCont) * 0.5f + finalBright
        
        // Base identity Matrix
        val m = floatArrayOf(
            finalCont * finalR, 0f, 0f, 0f, t * 255f + rOffset * userIntensity * 255f,
            0f, finalCont * finalG, 0f, 0f, t * 255f + gOffset * userIntensity * 255f,
            0f, 0f, finalCont * finalB, 0f, t * 255f + bOffset * userIntensity * 255f,
            0f, 0f, 0f, 1f, 0f
        )

        // Adjust Saturation
        // Luminance weights: R=0.299, G=0.587, B=0.114
        val invSat = 1f - finalSat
        val rSat = 0.299f * invSat
        val gSat = 0.587f * invSat
        val bSat = 0.114f * invSat

        val satMat = floatArrayOf(
            rSat + finalSat, gSat, bSat, 0f, 0f,
            rSat, gSat + finalSat, bSat, 0f, 0f,
            rSat, gSat, bSat + finalSat, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )

        // If a custom matrix mix is provided, blend it in
        if (matrixMix != null && matrixMix.size == 20) {
            val mixed = FloatArray(20)
            for (i in 0 until 20) {
                mixed[i] = m[i] * (1f - userIntensity) + matrixMix[i] * userIntensity
            }
            return ColorMatrix(mixed)
        }

        // Multiply contrast-gain matrix with saturation matrix
        val result = multiplyMatrices(m, satMat)
        return ColorMatrix(result)
    }

    private fun multiplyMatrices(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(20)
        // 4x5 matrices multiplication
        for (row in 0..3) {
            for (col in 0..3) {
                var sum = 0f
                for (k in 0..3) {
                    sum += a[row * 5 + k] * b[k * 5 + col]
                }
                out[row * 5 + col] = sum
            }
            // Add translation offset (last column of 5)
            out[row * 5 + 4] = a[row * 5 + 4] + a[row * 5 + 0] * b[0 * 5 + 4] + a[row * 5 + 1] * b[1 * 5 + 4] + a[row * 5 + 2] * b[2 * 5 + 4]
        }
        return out
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LutSpec

        if (id != other.id) return false
        if (name != other.name) return false
        if (category != other.category) return false
        if (description != other.description) return false
        if (rScale != other.rScale) return false
        if (gScale != other.gScale) return false
        if (bScale != other.bScale) return false
        if (contrast != other.contrast) return false
        if (saturation != other.saturation) return false
        if (brightnessOffset != other.brightnessOffset) return false
        if (rOffset != other.rOffset) return false
        if (gOffset != other.gOffset) return false
        if (bOffset != other.bOffset) return false
        if (matrixMix != null) {
            if (other.matrixMix == null) return false
            if (!matrixMix.contentEquals(other.matrixMix)) return false
        } else if (other.matrixMix != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + rScale.hashCode()
        result = 31 * result + gScale.hashCode()
        result = 31 * result + bScale.hashCode()
        result = 31 * result + contrast.hashCode()
        result = 31 * result + saturation.hashCode()
        result = 31 * result + brightnessOffset.hashCode()
        result = 31 * result + rOffset.hashCode()
        result = 31 * result + gOffset.hashCode()
        result = 31 * result + bOffset.hashCode()
        result = 31 * result + (matrixMix?.contentHashCode() ?: 0)
        return result
    }
}

object LUTRegistry {
    val luts: List<LutSpec> = generateAllLuts()

    private fun generateAllLuts(): List<LutSpec> {
        val list = mutableListOf<LutSpec>()

        // ----------------- CATEGORY 1: CINEMATIC (12 LUTs) -----------------
        list.add(LutSpec("cine_teal_orange", "Teal & Orange", LutCategory.CINEMATIC, "Hollywood standard blockbuster orange skintones & teal shadows", 1.05f, 0.95f, 1.15f, 1.15f, 1.25f, 0f, 0.05f, -0.02f, 0.08f))
        list.add(LutSpec("cine_log", "CineLog-H", LutCategory.CINEMATIC, "Flat log profile with mild pastel highlights & vast dynamic latitude", 0.9f, 0.9f, 0.92f, 0.75f, 0.7f, 0.12f))
        list.add(LutSpec("cine_bleach", "Bleach Bypass", LutCategory.CINEMATIC, "Silver halides left in emulsion: harsh contrast and low chroma saturation", 0.98f, 0.98f, 0.98f, 1.45f, 0.45f, -0.04f))
        list.add(LutSpec("cine_kodachrome", "Kodachrome 64", LutCategory.CINEMATIC, "Rich primary reds, warm nostalgic saturation, deep dye density", 1.18f, 0.96f, 0.88f, 1.1f, 1.18f, -0.01f))
        list.add(LutSpec("cine_velvia", "Fuji Velvia", LutCategory.CINEMATIC, "Deep pine greens, intense landscapes, clean sky blues & deep contrast", 0.92f, 1.2f, 1.15f, 1.2f, 1.35f, -0.02f))
        list.add(LutSpec("cine_technicolor", "Technicolor 3-Strip", LutCategory.CINEMATIC, "Nostalgic golden-age dye transfer print with separated primary hues", 1.2f, 1.05f, 0.9f, 1.15f, 1.4f, -0.01f))
        list.add(LutSpec("cine_oasis", "Oasis Warmth", LutCategory.CINEMATIC, "Desert heat filter with high latitude yellows and golden skin rendering", 1.15f, 1.02f, 0.82f, 1.05f, 1.15f, 0.02f, 0.04f, 0.01f, -0.04f))
        list.add(LutSpec("cine_scifi", "Sci-Fi Zinc", LutCategory.CINEMATIC, "Cold, clinical, industrial slate look with metallic steel-blue highlights", 0.88f, 1.02f, 1.15f, 1.18f, 0.85f, -0.01f, -0.03f, 0.01f, 0.05f))
        list.add(LutSpec("cine_rogue", "Rogue Cine", LutCategory.CINEMATIC, "Deep desaturated twilight look with high midtone contrast and cool shadows", 0.95f, 1.0f, 1.05f, 1.25f, 0.75f, -0.02f, -0.02f, -0.01f, 0.03f))
        list.add(LutSpec("cine_emerald", "Emerald Bay", LutCategory.CINEMATIC, "Marine-inspired cinematic grade with rich deep moss-green tones", 0.9f, 1.12f, 1.05f, 1.08f, 1.12f, 0f, -0.04f, 0.05f, 0.01f))
        list.add(LutSpec("cine_vintage_70s", "Vintage 70s", LutCategory.CINEMATIC, "Warm golden shadows, yellow highlights & rich analog style retro grade", 1.12f, 1.06f, 0.88f, 1.02f, 1.08f, 0.01f, 0.05f, 0.02f, -0.05f))
        list.add(LutSpec("cine_sunset_bliss", "Sunset Bliss", LutCategory.CINEMATIC, "Warm golden hour orange wash, perfect for silhouettes & backlit scenes", 1.22f, 0.98f, 0.82f, 1.12f, 1.32f, 0f, 0.08f, 0.01f, -0.06f))

        // ----------------- CATEGORY 2: GLOOMY & MOODY (10 LUTs) -----------------
        list.add(LutSpec("gloo_overcast", "Gloomy Overcast", LutCategory.GLOOMY, "Dim overcast cold winter days with low saturation and slight dim vignette", 0.92f, 0.96f, 1.05f, 0.95f, 0.65f, -0.04f))
        list.add(LutSpec("gloo_ash", "Ash Mist", LutCategory.GLOOMY, "Pale desaturated gray look with high soot factor and dusty shadow shelf", 0.98f, 0.98f, 1.0f, 0.85f, 0.48f, 0.05f))
        list.add(LutSpec("gloo_depths", "Cold Depths", LutCategory.GLOOMY, "Deep oceanic turquoise sub-surface look with cold shadows", 0.8f, 0.98f, 1.18f, 1.12f, 0.88f, -0.02f, -0.04f, 0.01f, 0.06f))
        list.add(LutSpec("gloo_decay", "Decay Forest", LutCategory.GLOOMY, "Dark mossy woodlands with heavy brown midtones and compressed highlights", 0.92f, 0.98f, 0.85f, 1.15f, 0.72f, -0.05f, 0.02f, -0.01f, -0.04f))
        list.add(LutSpec("gloo_steel", "Industrial Steel", LutCategory.GLOOMY, "Machinery slate blue-grey tone, clinical and emotionless atmosphere", 0.9f, 1.0f, 1.1f, 1.22f, 0.68f, -0.01f, -0.03f, -0.01f, 0.04f))
        list.add(LutSpec("gloo_rain", "Rain Shadow", LutCategory.GLOOMY, "Heavy clouds look, shadows lean to green, colors are washed away", 0.88f, 1.05f, 0.98f, 0.9f, 0.58f, -0.02f, -0.02f, 0.02f, -0.01f))
        list.add(LutSpec("gloo_ghostly", "Ghostly Haunt", LutCategory.GLOOMY, "Pale, green-yellow tint with low-key midtone boost and hazy highlights", 0.95f, 1.1f, 0.9f, 0.95f, 0.5f, 0.06f, -0.02f, 0.04f, -0.03f))
        list.add(LutSpec("gloo_mansion", "Gothic Mansion", LutCategory.GLOOMY, "Aged wood, deep shadows, high contrast but dark values", 0.92f, 0.92f, 0.96f, 1.3f, 0.52f, -0.08f))
        list.add(LutSpec("gloo_ochre", "Moody Ochre", LutCategory.GLOOMY, "Gloomy background with warm clay tones and faded green leaves", 1.08f, 0.95f, 0.82f, 1.08f, 0.76f, -0.02f, 0.04f, -0.01f, -0.05f))
        list.add(LutSpec("gloo_void", "Shadowed Void", LutCategory.GLOOMY, "Extremely deep black points and soft glowing highlights", 0.95f, 0.95f, 1.02f, 1.45f, 0.75f, -0.1f))

        // ----------------- CATEGORY 3: RETRO & VINTAGE (10 LUTs) -----------------
        list.add(LutSpec("vin_polaroid_82", "Polaroid 1982", LutCategory.VINTAGE, "Authentic vintage photo tone: yellowed highlights and blue fade shadows", 1.08f, 1.02f, 0.98f, 0.98f, 0.88f, 0.03f, 0.04f, 0.01f, -0.05f))
        list.add(LutSpec("vin_fade", "Warm Vintage Fade", LutCategory.VINTAGE, "Soft nostalgic warm tone with high black floor and cozy low contrast", 1.1f, 1.05f, 0.95f, 0.85f, 0.9f, 0.08f, 0.03f, 0.02f, -0.02f))
        list.add(LutSpec("vin_sepia", "Sepia Rust", LutCategory.VINTAGE, "Rich warm mahogany brown sepia wash with high organic contrast", 1.15f, 1.0f, 0.78f, 1.1f, 0.9f, 0.01f, 0.08f, 0.02f, -0.1f))
        list.add(LutSpec("vin_super8", "Super 8 Retro", LutCategory.VINTAGE, "Grainy, warm saturation, vintage 16mm vibe with heavy vignetted edges", 1.15f, 1.05f, 0.9f, 1.15f, 1.15f, -0.02f))
        list.add(LutSpec("vin_daguerre", "Daguerreotype", LutCategory.VINTAGE, "Victorian dust plating: dark sepia-gray with soft resolution look", 1.02f, 0.98f, 0.9f, 1.25f, 0.25f, 0.04f, 0.02f, 0f, -0.03f))
        list.add(LutSpec("vin_crimson", "Crimson Age", LutCategory.VINTAGE, "Nostalgic 1950s dye fade with heavy red channel emphasis", 1.25f, 0.92f, 0.88f, 1.05f, 1.05f, 0.01f, 0.06f, -0.02f, -0.04f))
        list.add(LutSpec("vin_lomo", "Lomo Chrome", LutCategory.VINTAGE, "Saturated color shifting: heavy reds & cyans with severe contrast wrap", 1.25f, 0.85f, 1.2f, 1.35f, 1.3f, -0.04f))
        list.add(LutSpec("vin_silverscreen", "Silver Screen", LutCategory.VINTAGE, "Slightly warm silver greyscale with rich organic silver glow", 1.0f, 1.0f, 1.0f, 1.18f, 0f, 0.02f, 0.02f, 0.01f, -0.01f))
        list.add(LutSpec("vin_matte", "Creamy Matte", LutCategory.VINTAGE, "Soft editorial style fade, beautiful skin tones & low matte blacks", 1.04f, 1.02f, 0.98f, 0.82f, 0.85f, 0.09f))
        list.add(LutSpec("vin_gold_dust", "Gold Dust", LutCategory.VINTAGE, "Dust-covered golden highlight bloom, warm luxury film look", 1.2f, 1.1f, 0.85f, 1.05f, 1.1f, 0.04f))

        // ----------------- CATEGORY 4: VIBRANT & ANIME (10 LUTs) -----------------
        list.add(LutSpec("vib_shinkai", "Shinkai Sky", LutCategory.VIBRANT_ANIME, "Anime master inspired bright deep blue skies and high saturation greens", 0.95f, 1.18f, 1.28f, 1.12f, 1.35f, 0.01f))
        list.add(LutSpec("vib_sakura", "Sakura Blossom", LutCategory.VIBRANT_ANIME, "Romantic soft pink hue over greens & dreamy whites, warm pastel charm", 1.22f, 0.95f, 1.08f, 0.92f, 1.15f, 0.04f, 0.06f, -0.04f, 0.02f))
        list.add(LutSpec("vib_tokyo", "Tokyo Neon", LutCategory.VIBRANT_ANIME, "Intense magenta tones mixed with cyan night reflections, ultra saturated", 1.25f, 0.82f, 1.28f, 1.18f, 1.45f, -0.02f))
        list.add(LutSpec("vib_sunshine", "Sunshine Glow", LutCategory.VIBRANT_ANIME, "Cheerful overexposed golden glow, morning grass & happy atmospheres", 1.15f, 1.12f, 0.9f, 1.02f, 1.25f, 0.05f))
        list.add(LutSpec("vib_fantasy", "Fantasy Purple", LutCategory.VIBRANT_ANIME, "Deep dreamland violets & warm magenta-golds", 1.08f, 0.9f, 1.25f, 1.08f, 1.28f, 0.02f))
        list.add(LutSpec("vib_pop", "Pop Art", LutCategory.VIBRANT_ANIME, "Swaying primary hues, maximalist saturation & punchy graphics", 1.15f, 1.15f, 1.15f, 1.2f, 1.6f, 0f))
        list.add(LutSpec("vib_matcha", "Matcha Custard", LutCategory.VIBRANT_ANIME, "Soft warm matcha pastel tea hues with creamy highlights", 0.98f, 1.15f, 0.9f, 0.98f, 1.08f, 0.05f))
        list.add(LutSpec("vib_grape", "Sweet Grape", LutCategory.VIBRANT_ANIME, "Candy purple tones paired with cozy pink shadows", 1.05f, 0.95f, 1.22f, 1.05f, 1.22f, 0.02f))
        list.add(LutSpec("vib_sunset_warm", "Sunset Glow", LutCategory.VIBRANT_ANIME, "Fiery deep red and gold color grading, gorgeous sunset skies", 1.25f, 1.0f, 0.8f, 1.1f, 1.35f, 0f))
        list.add(LutSpec("vib_electric", "Electric Teal", LutCategory.VIBRANT_ANIME, "Vibrant glowing turquoise highlights & punchy green shadows", 0.85f, 1.2f, 1.22f, 1.15f, 1.28f, -0.01f))

        // ----------------- CATEGORY 5: CYBERPUNK & ACIDIC (10 LUTs) -----------------
        list.add(LutSpec("cyb_cyber", "Cyberwave City", LutCategory.CYBERPUNK, "Dystopian city neon pink-magenta midtones with saturated cyan lights", 1.22f, 0.85f, 1.35f, 1.2f, 1.38f, -0.04f))
        list.add(LutSpec("cyb_purple", "Purple Acid Rain", LutCategory.CYBERPUNK, "Dark indigo wash with hot pink color grading, synthetic atmosphere", 1.08f, 0.8f, 1.28f, 1.15f, 1.25f, -0.03f))
        list.add(LutSpec("cyb_synth", "Synthwave Twilight", LutCategory.CYBERPUNK, "Classic 80s grid violet shadows and rich warm orange highlights", 1.22f, 0.9f, 1.18f, 1.22f, 1.32f, -0.01f))
        list.add(LutSpec("cyb_acid", "Acidic Slime", LutCategory.CYBERPUNK, "Extremely punchy toxic green and yellow hues with deep black base", 0.9f, 1.35f, 0.8f, 1.25f, 1.4f, -0.06f))
        list.add(LutSpec("cyb_pink", "Infuse Magenta", LutCategory.CYBERPUNK, "Cybernetic bright hot violet-pink tints on skin and highlights", 1.3f, 0.88f, 1.05f, 1.08f, 1.28f, 0.02f))
        list.add(LutSpec("cyb_toxic", "Toxic Shadow", LutCategory.CYBERPUNK, "Radioactive green-yellow shadows blended with deep metal blue", 0.92f, 1.15f, 0.98f, 1.18f, 1.15f, -0.03f))
        list.add(LutSpec("cyb_mercury", "Mercury Silver", LutCategory.CYBERPUNK, "High sheen silver metal with electric neon teal reflections", 0.95f, 1.08f, 1.15f, 1.28f, 0.72f, 0.01f))
        list.add(LutSpec("cyb_grid", "Violet Matrix", LutCategory.CYBERPUNK, "Deep glowing purple and cybernetically graded violet grid", 1.12f, 0.82f, 1.3f, 1.12f, 1.25f, 0.01f))
        list.add(LutSpec("cyb_amber", "Solstice Embers", LutCategory.CYBERPUNK, "Molten lava glowing red-orange, high key deep shadow contrast", 1.28f, 0.95f, 0.75f, 1.3f, 1.35f, -0.04f))
        list.add(LutSpec("cyb_abyss", "Deep Cyber Abyss", LutCategory.CYBERPUNK, "Extremely dark cobalt-blue depths and cyber neon skies", 0.8f, 0.95f, 1.22f, 1.25f, 1.12f, -0.08f))

        // ----------------- CATEGORY 6: MONOCHROME B&W (10 LUTs) -----------------
        list.add(LutSpec("mon_hicont", "High Contrast Noir", LutCategory.MONOCHROME, "Crushed ink-blot shadows and glowing crisp silver-white highlights", 1f, 1f, 1f, 1.62f, 0f, -0.08f))
        list.add(LutSpec("mon_charcoal", "Charcoal Grit", LutCategory.MONOCHROME, "Soft charcoal texture grey monochrome, gentle matte look", 1f, 1f, 1f, 0.88f, 0f, 0.04f))
        list.add(LutSpec("mon_platinum", "Platinum Deluxe", LutCategory.MONOCHROME, "Luxurious silver prints: wide tonal gray spectrum and luminous whites", 1f, 1f, 1f, 1.12f, 0f, 0.01f))
        list.add(LutSpec("mon_sky", "Dramatic Ortho", LutCategory.MONOCHROME, "Simulated orthochromatic red-blind film: black skies, ultra bright whites", 0.75f, 1.2f, 1.05f, 1.35f, 0f, -0.02f))
        list.add(LutSpec("mon_slate", "Slate Gray", LutCategory.MONOCHROME, "Monochrome with a cool steel-blue slate undertone", 0.95f, 0.98f, 1.08f, 1.08f, 0f, 0f, -0.02f, 0.01f, 0.04f))
        list.add(LutSpec("mon_matte", "Matte Faded Ink", LutCategory.MONOCHROME, "Chasm ash slate: washed-out light gray tones, high black floor", 1f, 1f, 1f, 0.8f, 0f, 0.12f))
        list.add(LutSpec("mon_silh", "Silhouette", LutCategory.MONOCHROME, "Severe back-light crunch: shadows instantly go to pure black", 1f, 1f, 1f, 1.95f, 0f, -0.15f))
        list.add(LutSpec("mon_infra", "Infrared Wood", LutCategory.MONOCHROME, "Simulated photographic infrared spectrum: glowing greens, dark skies", 0.7f, 1.45f, 0.85f, 1.28f, 0f, 0.02f))
        list.add(LutSpec("mon_sepb", "Sepia-Silver Mono", LutCategory.MONOCHROME, "Monochrome wash tinted with a rich antiquarian yellow-brown stain", 1.08f, 1.0f, 0.88f, 1.1f, 0f, 0f, 0.04f, 0.01f, -0.03f))
        list.add(LutSpec("mon_velvet", "Carbon Velvet", LutCategory.MONOCHROME, "Extremely smooth light transitions with organic deep velvet gradients", 1f, 1f, 1f, 1.02f, 0f, -0.02f))

        return list
    }
}
