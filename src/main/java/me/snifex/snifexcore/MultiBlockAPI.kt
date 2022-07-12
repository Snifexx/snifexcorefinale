package me.snifex.snifexcore

import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import java.util.Locale
import kotlin.properties.Delegates
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.createType
import kotlin.reflect.full.primaryConstructor

typealias BlockChoise = (Block) -> Boolean
typealias BlockEvent = (PlayerInteractEvent, Vector3, MultiBlockInstance) -> Unit

val mbinstLoc = mutableMapOf<Chunk, MutableMap<Location, MultiBlockInstance>>()

sealed class MultiBlock(blocks: Array<Matrix2<BlockChoise>>, blockInteractEvent: Array<Matrix2<BlockEvent>>, or: Vector3 = v3(0, 0, 0), rel: Vector3, internal val instanceClass: KClass<MultiBlockInstance>) {
    val blockChoise get() = _blockChoise
    private lateinit var _blockChoise: Array<Matrix2<BlockChoise>>

    val blockClicks get() = _blockClicks
    private lateinit var _blockClicks: Array<Matrix2<BlockEvent>>

    val xSize get() = _xSize
    private var _xSize by Delegates.notNull<Int>()

    val ySize get() = _ySize
    private var _ySize by Delegates.notNull<Int>()

    val zSize get() = _zSize
    private var _zSize by Delegates.notNull<Int>()

    val origin get() = _origin
    private lateinit var _origin: Vector3

    val reference get() = _reference
    private lateinit var _reference: Vector3

    val allRelLocs by lazy {
        val toReturn = mutableListOf<Vector3>()
        for (i in 0 until xSize) {
            for (j in 0 until ySize) {
                for (h in 0 until zSize) {
                    toReturn.add(v3(i, j, h))
                }
            }
        }
        toReturn
    }

    init {
        if (blocks.size != blockInteractEvent.size) error("IndexOutOfBoundsException: Multiblock's block array and blockInteractEvent array must be of the same size")
        if (blocks.isEmpty() || blockInteractEvent.isEmpty()) error("Multiblock's block array and blockInteractEvent array cannot be empty")
        for (i in blocks.indices) if (blocks[i].xSize != blockInteractEvent[i].xSize || blocks[i].ySize != blockInteractEvent[i].ySize) error("IndexOutOfBoundsException: Multiblock's block matrices must be of the same size")
        if (!contains(or) || !contains(rel)) error("Multiblock's origin and reference vector must be contained in the Matrix")
        _blockChoise = blocks
        _blockClicks = blockInteractEvent
        _ySize = blocks.size
        _xSize = blocks.first().xSize
        _ySize = blocks.first().ySize
        _origin = or
        _reference = rel
    }

    constructor(x: Int, y: Int, z: Int, or: Vector3 = v3(0, 0, 0), rel: Vector3, instanceClass: KClass<MultiBlockInstance>): this(Array(y) { Matrix2(x, z) { false } }, Array(y) { Matrix2(x, z) { a, b, c ->} }, or, rel, instanceClass)

    fun contains(x: Int, y: Int, z: Int): Boolean = contains(v3(x, y, z))
    fun contains(v3: Vector3): Boolean = v3.x > 0 && v3.y > 0 && v3.z > 0 && v3.x < xSize && v3.y < ySize && v3.z < zSize
    fun instance(origin: Location, direction: Direction) = instanceClass.primaryConstructor?.call(origin, this, direction)
}

sealed class MultiBlockInstance(val origin: Location, val multiBlock: MultiBlock, val direction: Direction = Direction.SE) {

    fun relPosition(loc: Location): Vector3? {
        val result = direction.adapt(v3(loc) - v3(origin)) + multiBlock.origin
        return if (multiBlock.contains(result)) result else null
    }

    init {
        register()
    }

    fun register() { multiBlock.allRelLocs.map { origin.add(it - multiBlock.origin) }.forEach(this::registerBlock) }

    fun unregister() { multiBlock.allRelLocs.map { origin.add(it - multiBlock.origin) }.forEach(this::unregisterBlock) }

    fun registerBlock(loc: Location) {
        if (mbinstLoc[loc.chunk] == null) mbinstLoc[loc.chunk] = mutableMapOf()
        mbinstLoc[loc.chunk]!![loc] = this
    }

    fun unregisterBlock(loc: Location) {
        if (mbinstLoc[loc.chunk] == null) return
        mbinstLoc[loc.chunk]!!.remove(loc)
    }

    fun interact(v3: Vector3, e: PlayerInteractEvent) {
        if (!multiBlock.contains(v3)) return
        multiBlock.blockClicks[v3.y][v3.x, v3.z]!!.invoke(e, v3, this)
    }
}

object MultiBlockEvents: Listener {
    @EventHandler
    fun onInteractWithMultiBlock(e: PlayerInteractEvent) {
        if (!(e.action == Action.LEFT_CLICK_BLOCK || e.action == Action.RIGHT_CLICK_BLOCK || e.action == Action.PHYSICAL)) return
        val loc = e.clickedBlock!!.location
        if (!mbinstLoc.contains(loc.chunk) || !mbinstLoc[loc.chunk]!!.containsKey(loc)) return
        val multiblockInstance = mbinstLoc[loc.chunk]!![loc]!!

        val relClicked = multiblockInstance.relPosition(loc)!!
        multiblockInstance.interact(relClicked, e)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onMultiBlockCreation(e: PlayerInteractEvent) {
        val p = e.player
        val clickBlock = e.clickedBlock
        if (p.inventory.itemInMainHand.type != Material.STICK) return
        MultiBlock::class.sealedSubclasses.forEach {
            multiblockType ->
            val multiblock = multiblockType.objectInstance!!
            val origin = multiblock.origin
            // TODO add secure error handling in case subclass of Multiblock is not an object
            if (clickBlock != null && multiblock.blockChoise[origin.y][origin.x, origin.z]?.invoke(clickBlock) == true) {
                for (direction in Direction.values()) {
                    val reference = multiblock.reference
                    val referenceToOrigin = reference - origin
                    val refLoc = clickBlock.location.add(Direction.SE.rotate(v3 = referenceToOrigin, to = direction))
                    if (multiblock.blockChoise[reference.y][reference.x, reference.z]?.invoke(refLoc.block) == true) {
                        multiblock.instance(clickBlock.location, direction)
                        return
                    }
                }
            }
        }
        p.sendMessage("No suitable multiblocks found here.")
    }
}

fun Location.add(v3: Vector3): Location = this.add(v3.x.toDouble(), v3.y.toDouble(), v3.z.toDouble())

enum class Direction {
    SE,SW,NW,NE;

    val opposite: Direction by lazy {
        val idx = Direction.values().indexOf(this)
        Direction.values()[(0..3).wrap(idx + 2)]
    }

    val clockwise: Direction by lazy {
        val idx = Direction.values().indexOf(this)
        Direction.values()[(0..3).wrap(idx + 1)]
    }

    val counterclockwise: Direction by lazy {
        val idx = Direction.values().indexOf(this)
        Direction.values()[(0..3).wrap(idx - 1)]
    }

    fun adapt(v3: Vector3, center: Vector3 = v3(0, 0, 0)): Vector3 = rotate(v3, center, SE)

    fun rotate(v3: Vector3, center: Vector3 = v3(0, 0, 0), to: Direction): Vector3 {
        val adaptedV3 = v3 - center
        return when(to) {
            counterclockwise -> v3(-adaptedV3.z, adaptedV3.y, adaptedV3.y) + center
            clockwise -> v3(adaptedV3.z, adaptedV3.y, -adaptedV3.x) + center
            opposite -> v3(-adaptedV3.x, adaptedV3.y,-adaptedV3.z) + center
            else -> adaptedV3 + center
        }
    }
}