package me.snifex.snifexcore

import org.bukkit.Location
import kotlin.math.max
import kotlin.reflect.KClass
import kotlin.reflect.full.functions

class Matrix2<T : Any>(val xSize: Int, val ySize: Int, private val classType: KClass<T>, default: T? = null): Iterable<T?> {
    companion object {
        inline operator fun <reified T: Any> invoke(xSize: Int, ySize: Int, default: T? = null) = Matrix2(xSize, ySize, T::class, default)
    }

    private val content: Array<Any?> = Array(xSize * ySize) { default }

    operator fun get(x: Int, y: Int) = content[xSize * y + x] as T?
    operator fun get(index: Int) = content[index] as T?
    operator fun set(x: Int, y: Int, value: T?) { content[xSize * y + x] = value }
    operator fun set(index: Int, value: T?) { content[index] = value }
    val values get() = content.map { it as T? }.toList()
    val xIndices get() = 0 until xSize
    val yIndices get() = 0 until ySize

    fun xVector(y: Int): List<T?> {
        if (y >= ySize) throw IndexOutOfBoundsException("Matrix2.xVector(y: Int) => y($y) out of bounds of ySize($ySize)")
        return content.copyOfRange(xSize * y, xSize * (y + 1)).map { it as T? }
    }
    fun yVector(x: Int): List<T?> {
        if (x >= xSize) throw IndexOutOfBoundsException("Matrix2.yVector(x: Int) => x($x) out of bounds of xSize($xSize)")
        return content.toList().slice(x..xSize * ySize step xSize).map { it as T? }
    }

    operator fun plus(other: Matrix2<T>): Matrix2<T> {
        val xHigher = max(xSize, other.xSize)
        val yHigher = max(ySize, other.ySize)

        val final = Matrix2(xHigher, yHigher, classType)
        if (!classType.functions.any { it.isOperator && it.name == "plus" && it.parameters.first()::class == classType && it.returnType.classifier == classType}) error("UnresolvedReverence: class ${classType.simpleName} does not contain a plus(b: ${classType.simpleName}) operator function")
        val plusFunc = classType.functions.first { it.isOperator && it.name == "plus" && it.parameters.first()::class == classType && it.returnType.classifier == classType }


        for (i in 0 until xHigher) {
            for (j in 0 until yHigher) {
                val a = if (i >= xSize) null else if (j >= ySize) null else this[i, j]
                val b = if (i >= other.xSize) null else if (j >= other.ySize) null else other[i, j]

                val sum = if (a == null && b == null) null else if (a == null) b else if (b == null) a else plusFunc.call(a, b) as T?
                final[i, j] = sum
            }
        }
        return final
    }

    override fun iterator(): Iterator<T?> = content.map { it as T? }.iterator()
}

class Vector2(var x: Int, var y: Int) {
    operator fun plus(other: Vector2): Vector2 = Vector2(x + other.x, y + other.y)
    operator fun minus(other: Vector2): Vector2 = Vector2(x - other.x, y - other.y)
    operator fun plusAssign(other: Vector2) { x += other.x; y += other.y }
    operator fun minusAssign(other: Vector2) { x -= other.x; y -= other.y }

    operator fun times(other: Vector2): Vector2 = Vector2(x * other.x, y * other.y)
    operator fun times(int: Int): Vector2 = Vector2(x * int, y * int)
    operator fun div(other: Vector2): Vector2 = Vector2(x / other.x, y / other.y)
    operator fun div(int: Int): Vector2 = Vector2(x / int, y / int)
    operator fun timesAssign(other: Vector2) { x *= other.x; y *= other.y }
    operator fun divAssign(other: Vector2) { x /= other.x; y /= other.y }
    operator fun timesAssign(int: Int) { x *= int; y *= int }
    operator fun divAssign(int: Int) { x /= int; y /= int }
}

class Vector3(var x: Int, var y: Int, var z: Int) {
    operator fun plus(other: Vector3): Vector3 = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3): Vector3 = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun plusAssign(other: Vector3) { x += other.x; y += other.y; z += other.z }
    operator fun minusAssign(other: Vector3) { x -= other.x; y -= other.y; z -= other.z }

    operator fun times(other: Vector3): Vector3 = Vector3(x * other.x, y * other.y, z * other.z)
    operator fun times(int: Int): Vector3 = Vector3(x * int, y * int, z * int)
    operator fun div(other: Vector3): Vector3 = Vector3(x / other.x, y / other.y, z / other.z)
    operator fun div(int: Int): Vector3 = Vector3(x / int, y / int, z / int)
    operator fun timesAssign(other: Vector3) { x *= other.x; y *= other.y; z *= other.z }
    operator fun divAssign(other: Vector3) { x /= other.x; y /= other.y; z /= other.z }
    operator fun timesAssign(int: Int) { x *= int; y *= int; z *= z }
    operator fun divAssign(int: Int) { x /= int; y /= int; z /= z }
}

fun v2(x: Int, y: Int) = Vector2(x, y)
fun v3(x: Int, y: Int, z: Int) = Vector3(x, y, z)
fun v3(loc: Location) = v3(loc.x.toInt(), loc.y.toInt(), loc.z.toInt())

fun IntRange.wrap(int: Int): Int {
    return when {
        int > last -> wrap(int - count())
        int < first -> wrap(int + count())
        else -> int
    }
}