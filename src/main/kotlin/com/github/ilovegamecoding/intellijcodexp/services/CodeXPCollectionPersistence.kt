package com.github.ilovegamecoding.intellijcodexp.services

import com.github.ilovegamecoding.intellijcodexp.models.CodeXPCollectionRecord
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.util.UUID

interface CodeXPCollectionPersistence {
    @Throws(IOException::class)
    fun load(): CodeXPCollectionRecord?

    /** Returning means the whole transaction committed. Throwing means the previous record remains current. */
    @Throws(IOException::class)
    fun save(record: CodeXPCollectionRecord)
}

/** Test dependency; never used for a normal IDE profile. */
internal class MemoryCollectionPersistence(
    private val remember: Boolean = true,
) : CodeXPCollectionPersistence {
    private var state: CodeXPCollectionRecord? = null

    override fun load(): CodeXPCollectionRecord? = state?.detachedCopy()

    override fun save(record: CodeXPCollectionRecord) {
        if (remember) state = record.detachedCopy()
    }
}

/**
 * Commit pointer + two identical, checksummed copies of that exact revision. Recovery never rolls
 * back to an older ledger (which could refund a spend or resurrect a reset). 2.2.0 only writes
 * CodeXP.xml and cannot erase this directory. Unknown/corrupt storage fails closed.
 */
class FileCollectionPersistence(
    private val directory: Path,
    private val beforeCommit: () -> Unit = {},
) : CodeXPCollectionPersistence {
    private val pointer get() = directory.resolve("current.xml")
    private val initialized get() = directory.resolve("initialized")
    private val firstPending get() = directory.resolve("first.pending")

    @Synchronized
    override fun load(): CodeXPCollectionRecord? = if (hasCommit()) readRevision(pointer) else null

    private fun readRevision(
        path: Path,
        expectedRevision: String? = null,
    ): CodeXPCollectionRecord {
        try {
            val manifest = JDOMUtil.load(path)
            if (manifest.name != "CodeXPCollection" || manifest.getAttributeValue("version") != "1") throw IOException("Unsupported collection manifest.")
            val revision = manifest.getAttributeValue("revision") ?: throw IOException("Missing revision.")
            val checksum = manifest.getAttributeValue("sha256") ?: throw IOException("Missing checksum.")
            if (!revision.matches(Regex("[0-9a-f-]{36}")) || !checksum.matches(Regex("[0-9a-f]{64}"))) throw IOException("Invalid collection manifest.")
            if (expectedRevision != null && revision != expectedRevision) throw IOException("Mismatched pending revision.")
            for (suffix in listOf("xml", "backup.xml")) {
                val bytes = runCatching { Files.readAllBytes(directory.resolve("$revision.$suffix")) }.getOrNull() ?: continue
                if (sha256(bytes) != checksum) continue
                val record = runCatching { XmlSerializer.deserialize(JDOMUtil.load(bytes.inputStream()), CodeXPCollectionRecord::class.java) }.getOrNull()
                if (record != null) return record
            }
            throw IOException("Neither copy of the committed collection revision is readable.")
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("Invalid collection storage.", error)
        }
    }

    private fun hasCommit(): Boolean {
        if (!Files.notExists(pointer)) return true
        if (Files.notExists(directory) || Files.exists(firstPending)) return false
        val revisions =
            Files.list(directory).use { files ->
                files.map { it.fileName.toString() }.filter { it.matches(Regex("[0-9a-f-]{36}\\.(xml|backup\\.xml|pending)")) }.toList()
            }
        if (Files.notExists(initialized) && revisions.isEmpty()) return false

        // The earlier RC consumed each UUID.pending by renaming it to current.xml. Recover a
        // staged first write only when every revision is complete and still has its own pending pointer.
        val pending = revisions.filter { it.endsWith(".pending") }
        if (pending.isNotEmpty() && Files.readString(initialized) == "CodeXP collection storage v1\n") {
            val staged = pending.map { it.removeSuffix(".pending") }
            if (revisions.toSet() == staged.flatMap { listOf("$it.xml", "$it.backup.xml", "$it.pending") }.toSet()) {
                if (staged.all { readRevision(directory.resolve("$it.pending"), it).isInitialMigration() }) return false
            }
        }
        throw IOException("Collection commit pointer is missing; refusing to replay migration.")
    }

    // An orphaned later transaction must never qualify, even if both committed data files were lost.
    private fun CodeXPCollectionRecord.isInitialMigration(): Boolean {
        val initial = migration ?: return false
        val gift = if (initial.baselineXP > 0) mapOf("migration.gift.v1" to 1L) else emptyMap()
        return schemaVersion == 1 && collection.schemaVersion == 1 && resetCount == 0L &&
            initial.baselineXP == checkpoint.xp && initial.noticePending == gift.isNotEmpty() &&
            collection.equippedIds.isEmpty() && collection.ownedIds == collection.unseenIds &&
            collection.creditedEntitlements == gift && collection.choiceCredits == (gift["migration.gift.v1"] ?: 0L)
    }

    @Synchronized
    override fun save(record: CodeXPCollectionRecord) {
        Files.createDirectories(directory)
        val first = !hasCommit()
        // This witness is consumed by the first commit itself. It cannot survive a successful spend or reset.
        if (first && Files.notExists(firstPending)) writeNew(firstPending, byteArrayOf())
        if (Files.notExists(initialized)) writeNew(initialized, "CodeXP collection storage v2\n".toByteArray())
        val revision = UUID.randomUUID().toString()
        val bytes = JDOMUtil.writeElement(XmlSerializer.serialize(record)).toByteArray(Charsets.UTF_8)
        writeNew(directory.resolve("$revision.xml"), bytes)
        writeNew(directory.resolve("$revision.backup.xml"), bytes)
        val manifest = Element("CodeXPCollection").setAttribute("version", "1").setAttribute("revision", revision).setAttribute("sha256", sha256(bytes))
        val pending = directory.resolve("$revision.pending")
        writeNew(pending, JDOMUtil.writeElement(manifest).toByteArray(Charsets.UTF_8))
        val prepared = if (first) Files.move(pending, firstPending, ATOMIC_MOVE, REPLACE_EXISTING) else pending
        beforeCommit()
        // No non-atomic fallback: a failed move must not turn a spend into a partially saved grant.
        Files.move(prepared, pointer, ATOMIC_MOVE, REPLACE_EXISTING)
        // The pointer replacement is the commit point. Housekeeping cannot report it as a failure.
        runCatching { FileChannel.open(directory, READ).use { it.force(true) } }
        runCatching {
            Files.list(directory).use { files ->
                files
                    .filter { it.fileName.toString().matches(Regex("[0-9a-f-]{36}\\.(xml|backup\\.xml|pending)")) && !it.fileName.toString().startsWith(revision) }
                    .forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun writeNew(
        path: Path,
        bytes: ByteArray,
    ) {
        FileChannel.open(path, CREATE_NEW, WRITE).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
