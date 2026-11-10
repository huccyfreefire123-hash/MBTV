package com.tensei.ytxtube

import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import coil.size.Dimension

/**
 * Posters and banners of IPTV lists often point to a small TMDB rendition (w92, w185, w300) that a
 * phone or a TV then enlarges, which is what makes them blurry. TMDB serves every picture in many
 * sizes at the same path, so when a picture is shown larger than the rendition the list gave, the
 * request asks for one that is big enough. It is the same picture from the same server: only the
 * size in the address changes, and when TMDB refuses it the original address is used as before.
 */
class SharpImages : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = request.data as? String ?: return chain.proceed(request)
        val size = chain.size
        val sharper = sharperTmdb(url, pixels(size.width), pixels(size.height)) ?: return chain.proceed(request)
        val result = chain.proceed(request.newBuilder().data(sharper).build())
        return if (result is ErrorResult) chain.proceed(request) else result
    }

    private fun pixels(d: Dimension): Int? = (d as? Dimension.Pixels)?.px
}

private val tmdbAddress = Regex("""^(https?://image\.tmdb\.org/t/p/)(w\d+|original)(/[^?#\s]+)$""", RegexOption.IGNORE_CASE)

/** The widths TMDB serves, in order. */
private val tmdbWidths = intArrayOf(92, 154, 185, 300, 342, 500, 780, 1280)

/**
 * The address of a bigger rendition of a TMDB picture shown in a box of [boxWidth] x [boxHeight]
 * pixels, or null when [url] is not a TMDB picture or is already big enough for the box.
 */
internal fun sharperTmdb(url: String, boxWidth: Int?, boxHeight: Int?): String? {
    val m = tmdbAddress.matchEntire(url) ?: return null
    val current = m.groupValues[2].lowercase()
    if (current == "original") return null
    val have = current.drop(1).toIntOrNull() ?: return null
    val width = boxWidth ?: return null
    // The box shows the picture cropped to fill it, so what it needs depends on the picture's shape:
    // w300 and w1280 are TMDB's backdrop sizes (wide), the others are poster sizes (tall).
    val shape = if (have == 300 || have == 1280) 16f / 9f else 2f / 3f
    // Enlarging by 10% is not noticeable.
    val needed = maxOf(width.toFloat(), (boxHeight ?: 0) * shape) * 0.9f
    if (have >= needed) return null
    val target = tmdbWidths.firstOrNull { it >= needed && it > have }?.let { "w$it" } ?: "original"
    return m.groupValues[1] + target + m.groupValues[3]
}
