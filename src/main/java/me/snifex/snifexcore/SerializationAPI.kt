package me.snifex.snifexcore

import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*

/**
 * Returns serialized String
 * 'T' is the hexadecimal place for the material (max HEX = 500, DEC = 1280)
 * 'A' is the hexadecimal place for amount (max HEX = 40, DEC = 64)
 * @return "TTTAA{itemmeta} || {nbt}"
 */

fun ItemStack.serialized(): String = serializeBukkitObject(this)
fun String.toItemStack(): ItemStack = deserializeBukkitObject(this) as ItemStack

fun ItemMeta.serialized(): String = serializeBukkitObject(this)
fun String.toItemMeta(): ItemMeta = deserializeBukkitObject(this) as ItemMeta

fun Location.serialized(): String = "${world?.uid}|$x|$y|$z|$yaw|$pitch"
fun String.toLocation(): Location {
    val splitStr = split("|")
    return Location(Bukkit.getWorld(UUID.fromString(splitStr[0])), splitStr[1].toDouble(), splitStr[2].toDouble(), splitStr[3].toDouble(), splitStr[4].toFloat(), splitStr[5].toFloat())
}

fun Chunk.serialized(): String = "${world.uid}|$x|$z"
fun String.toChunk(): Chunk {
    val splitStr = split("|")
    return Bukkit.getWorld(UUID.fromString(splitStr[0]))?.getChunkAt(splitStr[1].toInt(), splitStr[2].toInt())!!
}


internal fun serializeBukkitObject(obj: Any): String {
    val io = ByteArrayOutputStream()
    val os = BukkitObjectOutputStream(io)
    os.writeObject(obj)
    os.flush()
    return Base64.getEncoder().encodeToString(io.toByteArray())
}

internal fun deserializeBukkitObject(str: String): Any {
    val io = ByteArrayInputStream(Base64.getDecoder().decode(str))
    val ins = BukkitObjectInputStream(io)
    return ins.readObject()
}
