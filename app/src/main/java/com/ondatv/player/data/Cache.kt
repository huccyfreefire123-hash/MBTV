package com.ondatv.player.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * The last list and guide kept on disk, so the app shows them at once on start while it
 * fetches fresh ones in the background.
 */
class Cache(dir: File) {
    private val root = File(dir, "catalog").apply { mkdirs() }
    private fun listFile(sourceId: String) = File(root, "list_$sourceId.bin")
    private val epgFile = File(root, "epg.bin")

    fun loadList(sourceId: String): Loaded? = read(listFile(sourceId)) {
        val epgUrl = str()
        val count = readInt()
        val items = ArrayList<Item>(count)
        repeat(count) { items += item(sourceId) }
        Loaded(items, epgUrl)
    }

    fun saveList(sourceId: String, loaded: Loaded) = write(listFile(sourceId)) {
        str(loaded.epgUrl)
        writeInt(loaded.items.size)
        loaded.items.forEach { item(it) }
    }

    /** The guide without the programmes that have already ended. */
    fun loadEpg(now: Long): Map<String, List<Programme>>? = read(epgFile) {
        val out = HashMap<String, List<Programme>>()
        repeat(readInt()) {
            val channel = str()!!
            val list = ArrayList<Programme>()
            repeat(readInt()) {
                val p = Programme(readLong(), readLong(), str()!!, str()!!)
                if (p.stop > now) list += p
            }
            if (list.isNotEmpty()) out[channel] = list
        }
        out
    }

    fun saveEpg(epg: Map<String, List<Programme>>) = write(epgFile) {
        writeInt(epg.size)
        epg.forEach { (channel, list) ->
            str(channel)
            writeInt(list.size)
            list.forEach { writeLong(it.start); writeLong(it.stop); str(it.title); str(it.desc) }
        }
    }

    private val detailsFile = File(root, "details.bin")

    /** Film details fetched before (get_vod_info), by item key. */
    fun loadDetails(): Map<String, Details>? = read(detailsFile) {
        val count = readInt()
        val out = HashMap<String, Details>(count)
        repeat(count) {
            out[str()!!] = Details(str(), str(), str(), str(), str(), str(), readFloat().takeUnless { it.isNaN() })
        }
        out
    }

    fun saveDetails(details: Map<String, Details>) = write(detailsFile) {
        writeInt(details.size)
        details.forEach { (key, d) ->
            str(key); str(d.backdrop); str(d.plot); str(d.genre); str(d.year); str(d.duration); str(d.age)
            writeFloat(d.rating ?: Float.NaN)
        }
    }

    /** How old the saved list / guide is, in ms; null when there is none. */
    fun listAge(sourceId: String): Long? = age(listFile(sourceId))

    fun epgAge(): Long? = age(epgFile)

    private fun age(file: File): Long? =
        if (file.isFile) (System.currentTimeMillis() - file.lastModified()).coerceAtLeast(0) else null

    fun clear(sourceId: String) {
        listFile(sourceId).delete()
        epgFile.delete()
        detailsFile.delete()
    }

    private fun <T> read(file: File, block: DataInputStream.() -> T): T? {
        if (!file.isFile) return null
        // A file from another app version, or cut short, is simply ignored.
        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(file), 64 * 1024)).use { input ->
                if (input.readInt() != MAGIC) return null
                input.block()
            }
        }.getOrNull()
    }

    private fun write(file: File, block: DataOutputStream.() -> Unit) {
        val tmp = File(file.path + ".tmp")
        runCatching {
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 64 * 1024)).use { out ->
                out.writeInt(MAGIC)
                out.block()
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }.onFailure { tmp.delete() }
    }

    private fun DataOutputStream.item(i: Item) {
        writeByte(i.kind.ordinal)
        str(i.id); str(i.name); str(i.group); str(i.logo); str(i.url); str(i.epgId)
        writeInt(i.episodes.size)
        i.episodes.forEach { e ->
            str(e.id); str(e.title); writeInt(e.season); writeInt(e.number); str(e.url); str(e.image); str(e.plot)
        }
        writeFloat(i.rating ?: Float.NaN)
        writeLong(i.added)
        str(i.backdrop); str(i.plot); str(i.genre); str(i.year)
        writeBoolean(i.adult)
        writeInt(i.parts.size)
        i.parts.forEach { p -> str(p.id); writeInt(p.season ?: 0) }
    }

    private fun DataInputStream.item(sourceId: String): Item {
        val kind = Kind.entries[readByte().toInt()]
        val id = str()!!
        val name = str()!!
        val group = str()!!
        val logo = str()
        val url = str()!!
        val epgId = str()
        val episodes = List(readInt()) { Episode(str()!!, str()!!, readInt(), readInt(), str()!!, str(), str()) }
        val rating = readFloat().takeUnless { it.isNaN() }
        return Item(
            sourceId, kind, id, name, group, logo, url, epgId, episodes, rating,
            added = readLong(), backdrop = str(), plot = str(), genre = str(), year = str(),
            adult = readBoolean(),
            parts = List(readInt()) { SeriesPart(str()!!, readInt().takeIf { it > 0 }) },
        )
    }

    // Length-prefixed UTF-8 (writeUTF stops at 64 KB, which a long plot could pass).
    private fun DataOutputStream.str(s: String?) {
        if (s == null) { writeInt(-1); return }
        val bytes = s.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.str(): String? {
        val n = readInt()
        if (n < 0) return null
        val bytes = ByteArray(n)
        readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private companion object {
        const val MAGIC = 0x4D425402 // "MBT" + format version 2 (series seasons, episode pictures)
    }
}
