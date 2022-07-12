package me.snifex.snifexcore

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import java.util.Arrays
import kotlin.math.ceil

val ItemStackNull = ItemStack(Material.AIR)
val InventoryNull = Bukkit.createInventory(null, 9)
val InventoryContentNull = InventoryContent(0)

class InventoryID : InventoryHolder {
    override fun getInventory(): Inventory {
        return InventoryNull
    }

    internal lateinit var _host: InventoryHost
    val host
        get() = _host
}

class Slot(
    val item: ItemStack = ItemStackNull,
    val locked: Boolean = false,
    val clickEvent: (InventoryInstance, Int, InventoryClickEvent) -> Unit
) {
    companion object {
        val EMPTY = Slot(ItemStackNull, false) { inst, int, e -> return@Slot }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Slot

        if (item != other.item) return false
        if (locked != other.locked) return false
        if (clickEvent != other.clickEvent) return false

        return true
    }

    override fun hashCode(): Int {
        var result = item.hashCode()
        result = 31 * result + locked.hashCode()
        result = 31 * result + clickEvent.hashCode()
        return result
    }

    override fun toString(): String {
        return "Slot(item=$item, locked=$locked, clickEvent=$clickEvent)"
    }
}


class InventoryHost(val title: String, val slots: InventoryContent, val id: InventoryID) {
    val size: Int = slots.size
    internal val instances = mutableListOf<InventoryInstance>()

    init {
        id._host = this
    }

    companion object {
        fun inventoryContentFromItemStacks(invSize: Int, items: Collection<Array<ItemStack>>, slots: Collection<Array<Slot>>): InventoryContent {
            if (items.size != slots.size) return InventoryContentNull
            val toReturn = mutableListOf<Array<Slot>>()

            for (i in items.indices) {
                if (items.elementAt(i).size != invSize || slots.elementAt(i).size != invSize) return InventoryContentNull
                toReturn.set(i, Array(slots.elementAt(i).size) { Slot.EMPTY })
                for (j in items.elementAt(i).indices) {
                    val slot = Slot(items.elementAt(i).elementAt(j), slots.elementAt(i).elementAt(j).locked, slots.elementAt(i).elementAt(j).clickEvent)
                    toReturn[i][j] = slot
                }
            }

            return InventoryContent(invSize, *toReturn.toTypedArray())
        }
    }

    fun instance(): InventoryInstance = InventoryInstance(this, slots.clone()).also { instances.add(it) }

    internal fun instance(content: InventoryContent) = InventoryInstance(this, content).also { instances.add(it) }

    fun freeSlotsIndex(page: Int): IntArray {
        val toReturn = mutableListOf<Int>()
        for (i in 0 until size) {
            if (!slots.pages[page][i].locked) toReturn.add(i)
        }
        return toReturn.toIntArray()
    }

    fun freeSlots(page: Int): Array<Slot> {
        val toReturn = mutableListOf<Slot>()
        for (index in freeSlotsIndex(page)) {
            toReturn.add(slots.pages[page][index])
        }
        return toReturn.toTypedArray()
    }

    fun lockedSlots(page: Int): Array<Slot> {
        val a = slots.pages[0].clone().toList() - freeSlots(page).toSet()
        return a.toTypedArray()
    }
}

class InventoryInstance internal constructor(val host: InventoryHost, val content: InventoryContent) {

    fun getInventory(page: Int): Inventory {
        val inv = Bukkit.createInventory(host.id, host.size, host.title)
        inv.contents = content.pages[page].map { it.item }.toTypedArray()
        return inv
    }

    internal constructor(host: InventoryHost) : this(host, host.slots.clone())
    companion object {
        fun from(inventory: Inventory): Pair<InventoryInstance, Int> {
            val host = (inventory.holder as InventoryID).host
            for (inv in host.instances) {
                for (pageNum in inv.content.pages.indices) {
                    if (inventory.contents.toList().map { it ?: ItemStackNull } == inv.content.pages[pageNum].map { it.item }) return Pair(inv, pageNum)
                }
            }
            return Pair(host.instance(), 0)
        }
    }

    fun setItem(page: Int, index: Int, item: ItemStack) {
        if (index < 0 || index >= content.pages[page].size) return
        content.pages[page][index] = Slot(item, content.pages[page][index].locked, content.pages[page][index].clickEvent)
        updateInventory()
    }

    /**
     * @return A new InventoryInstance derived from the current one. In other words, if you need to set a certain slot's itemstack different from other players it'll create a clone that it returns
     */
    fun setItem(
        player: Player,
        page: Int = if (_playersWithInstanceOpen.containsKey(player)) _playersWithInstanceOpen[player]!! else 0,
        index: Int,
        item: ItemStack
    ): InventoryInstance {
        if (index < 0 || index >= content.pages[page].size) return this
        val toReturn = clone()
        toReturn.content.pages[page][index] = Slot(item, toReturn.content.pages[page][index].locked, toReturn.content.pages[page][index].clickEvent)
        toReturn.openInventory(player, page)
        return toReturn
    }

    fun updateInventory() {
        _playersWithInstanceOpen.forEach { (p, page) -> openInventory(p, page) }
    }

    fun openInventory(player: Player, page: Int) {
        player.closeInventory()
        player.openInventory(getInventory(page))
        _playersWithInstanceOpen[player] = page
    }

    private val _playersWithInstanceOpen = mutableMapOf<Player, Int>()

    /**
     * Map of all players with the Inventory open at the page specified in the entry
     */
    val playersWithInstanceOpen: Map<Player, Int>
        get() = _playersWithInstanceOpen

    fun click(page: Int, event: InventoryClickEvent) {
        content.pages[page][event.slot].clickEvent.invoke(this, page, event)
        if (content.pages[page][event.slot].locked) event.isCancelled = true
    }

    fun freeSlotsIndex(page: Int): IntArray {
        val toReturn = mutableListOf<Int>()
        for (i in 0 until content.pages[page].size) {
            if (!content.pages[page][i].locked) toReturn.add(i)
        }
        return toReturn.toIntArray()
    }

    fun freeSlots(page: Int): Array<Slot> {
        val toReturn = mutableListOf<Slot>()
        for (index in freeSlotsIndex(page)) {
            toReturn.add(content.pages[page][index])
        }
        return toReturn.toTypedArray()
    }

    fun lockedSlots(page: Int): Array<Slot> {
        val a = content.pages[page].clone().toList() - freeSlots(page).toSet()
        return a.toTypedArray()
    }

    fun lockedSlotsIndex(page: Int): IntArray = (content.pages[page].indices.toList() - freeSlotsIndex(page).toSet()).toIntArray()

    fun clone(): InventoryInstance {
        return host.instance(content)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InventoryInstance

        if (host != other.host) return false
        if (content != other.content) return false

        return true
    }

    override fun hashCode(): Int {
        var result = host.hashCode()
        result = 31 * result + content.hashCode()
        return result
    }

    override fun toString(): String {
        return "InventoryInstance(host=$host, content=$content, _playersWithInstanceOpen=$_playersWithInstanceOpen)"
    }

    fun unregister() {
        host.instances.remove(this)
    }
}

object CustomInventory : Listener {
    @EventHandler
    fun OnSlotClick(e: InventoryClickEvent) {
        if (e.clickedInventory == null || e.inventory.holder !is InventoryID) return

        val instance = InventoryInstance.from(e.inventory)
        if (e.clickedInventory!!.holder is InventoryID) instance.first.click(instance.second, e)
        else if (e.action == InventoryAction.MOVE_TO_OTHER_INVENTORY && slotsDestination(e).any { instance.first.lockedSlotsIndex(instance.second).contains(it) }) {
            e.isCancelled = true
            return
        }
    }
}

class InventoryContent(_size: Int, vararg content: Array<Slot>) {
    val size: Int = ceil(_size / 9.0).toInt() * 9
    val pages = mutableListOf(Array(size) { Slot.EMPTY }).apply {
        for (page in content.indices) {
            val inventory = content[page]
            val array = Array(this@InventoryContent.size) { Slot.EMPTY }
            if (inventory.size > this@InventoryContent.size) throw ArrayIndexOutOfBoundsException("Given Array has ${inventory.size} slots while it should have $_size or less slots")
            for (j in inventory.indices) {
                array[j] = inventory[j]
            }
            if (page == 0) this[0] = array else add(array)
        }
    }

    fun clone(): InventoryContent = InventoryContent(size, *pages.toTypedArray())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InventoryContent
        if (size != other.size) return false
        if (pages != other.pages) return false

        return true
    }

    override fun hashCode(): Int {
        var result = size
        result = 31 * result + pages.hashCode()
        return result
    }

    override fun toString(): String {
        return "InventoryContent(size=$size, pages=${pages.toTypedArray().contentDeepToString()})"
    }
}

fun slotsDestination(e: InventoryClickEvent): List<Int> {
    val slots: MutableList<Int> = ArrayList()
    if (e.isShiftClick) {
        val list = e.inventory.contents
        var airSlot = -1
        var stacked = e.currentItem!!.amount
        var t: ItemStack?
        for (p in list.indices) {
            t = list[p]
            if (t == null) {
                if (airSlot == -1) {
                    airSlot = p
                }
                continue
            }
            if (t.type != e.currentItem!!.type) continue
            else if (t.hasItemMeta() && e.currentItem!!.hasItemMeta() && t.itemMeta != e.currentItem!!.itemMeta) continue
            else if (t.itemMeta is Damageable && e.currentItem!!.itemMeta is Damageable && (t.itemMeta as Damageable).damage != (e.currentItem!!.itemMeta as Damageable).damage) continue
            else if (t.maxStackSize == t.amount) continue
            else {
                if (t.amount + stacked > t.maxStackSize) {
                    stacked -= t.maxStackSize - t.amount
                    slots.add(p)
                } else {
                    stacked = 0
                    slots.add(p)
                    break
                }
            }
        }
        if (airSlot != -1 && stacked > 0) {
            slots.add(airSlot)
        }
    }
    return slots
}
