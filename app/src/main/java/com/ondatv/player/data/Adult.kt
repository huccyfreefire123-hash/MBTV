package com.ondatv.player.data

/**
 * Adult channel categories, recognised by name ("XXX", "Adultos", "+18", "Erotico"...) or by the
 * is_adult flag some Xtream servers send. Accents and case are ignored.
 */
object Adult {
    private val pattern = Regex(
        """(^|[^a-z0-9])(xxx+|adult[oa]?s?|\+ ?18|18 ?\+|porn[a-z]*|erotic[a-z]*|erotico[a-z]*)([^a-z0-9]|$)"""
    )

    // A list has a few hundred categories but tens of thousands of channels: decide each name once.
    private val known = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun isAdultGroup(name: String): Boolean = known.getOrPut(name) { pattern.containsMatchIn(Sports.norm(name)) }

    /** Channels, films and series alike: by the server's category or its is_adult flag, never by title. */
    fun isAdult(item: Item): Boolean = item.adult || isAdultGroup(item.group)
}
