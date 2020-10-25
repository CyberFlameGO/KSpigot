@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package net.axay.kspigot.gui

import net.axay.kspigot.gui.elements.*
import org.bukkit.inventory.ItemStack
import kotlin.math.absoluteValue

fun <T : ForInventory> kSpigotGUI(
    type: GUIType<T>,
    guiCreator: GUICreator<T> = IndividualGUICreator(),
    builder: GUIBuilder<T>.() -> Unit,
) = GUIBuilder(type, guiCreator).apply(builder).build()

class GUIBuilder<T : ForInventory>(
    val type: GUIType<T>,
    private val guiCreator: GUICreator<T>
) {

    /**
     * The title of this GUI.
     * This title will be visible for every page of
     * this GUI.
     */
    var title: String = ""

    /**
     * The transition applied, if another GUI redirects to
     * this GUI.
     */
    var transitionTo: InventoryChangeEffect? = null

    /**
     * The transition applied, if this GUI redirects to
     * another GUI and the other GUI has no transitionTo
     * value defined.
     */
    var transitionFrom: InventoryChangeEffect? = null

    /**
     * The default page will be loaded first for every
     * GUI instance.
     */
    var defaultPage = 1

    private val guiSlots = HashMap<Int, GUIPage<T>>()

    private var onClickElement: ((GUIClickEvent<T>) -> Unit)? = null

    /**
     * Opens the builder for a new page and adds
     * the new page to the GUI.
     * @param page The index of the page.
     */
    fun page(page: Int, builder: GUIPageBuilder<T>.() -> Unit) {
        guiSlots[page] = GUIPageBuilder(type, page).apply(builder).build()
    }

    /**
     * A callback executed when the user clicks on
     * any GUI elements on any page in this GUI.
     */
    fun onClickElement(onClick: (GUIClickEvent<T>) -> Unit) {
        onClickElement = onClick
    }

    internal fun build() = guiCreator.createInstance(
        GUIData(type, title, guiSlots, defaultPage, transitionTo, transitionFrom, onClickElement)
    )

}

class GUIPageBuilder<T : ForInventory>(
    private val type: GUIType<T>,
    val page: Int
) {

    private val guiSlots = HashMap<Int, GUISlot<T>>()

    var transitionTo: PageChangeEffect? = null
    var transitionFrom: PageChangeEffect? = null

    internal fun build() = GUIPage(page, guiSlots, transitionTo, transitionFrom)

    private fun defineSlots(slots: InventorySlotCompound<T>, element: GUISlot<T>) =
        slots.withInvType(type).forEach { curSlot ->
            curSlot.realSlotIn(type.dimensions)?.let { guiSlots[it] = element }
        }

    /**
     * A button is an item protected from any player
     * actions. If clicked, the specified [onClick]
     * function is invoked.
     */
    fun button(slots: InventorySlotCompound<T>, itemStack: ItemStack, onClick: (GUIClickEvent<T>) -> Unit) =
        defineSlots(slots, GUIButton(itemStack, onClick))

    /**
     * An item protected from any player actions.
     * This is not a button.
     */
    fun placeholder(slots: InventorySlotCompound<T>, itemStack: ItemStack) =
        defineSlots(slots, GUIPlaceholder(itemStack))

    /**
     * A free slot does not block any player actions.
     * The player can put items in this slot or take
     * items out of it.
     */
    fun freeSlot(slots: InventorySlotCompound<T>) = defineSlots(slots, GUIFreeSlot())

    /**
     * This is a button which loads the specified
     * [toPage] if clicked.
     */
    fun pageChanger(
        slots: InventorySlotCompound<T>,
        icon: ItemStack,
        toPage: Int,
        onChange: ((GUIClickEvent<T>) -> Unit)? = null
    ) = defineSlots(
        slots, GUIButtonPageChange(
            icon,
            GUIPageChangeCalculator.GUIConsistentPageCalculator(toPage),
            onChange
        )
    )

    /**
     * This button always tries to find the previous
     * page if clicked, and if a previous page
     * exists it is loaded.
     */
    fun previousPage(
        slots: InventorySlotCompound<T>,
        icon: ItemStack,
        onChange: ((GUIClickEvent<T>) -> Unit)? = null
    ) = defineSlots(
        slots, GUIButtonPageChange(
            icon,
            GUIPageChangeCalculator.GUIPreviousPageCalculator,
            onChange
        )
    )

    /**
     * This button always tries to find the next
     * page if clicked, and if a next page
     * exists it is loaded.
     */
    fun nextPage(
        slots: InventorySlotCompound<T>,
        icon: ItemStack,
        onChange: ((GUIClickEvent<T>) -> Unit)? = null
    ) = defineSlots(
        slots, GUIButtonPageChange(
            icon,
            GUIPageChangeCalculator.GUINextPageCalculator,
            onChange
        )
    )

    /**
     * By pressing this button, the player switches to another
     * GUI. The transition effect is applied.
     */
    fun changeGUI(
        slots: InventorySlotCompound<T>,
        icon: ItemStack,
        newGUI: () -> GUI<*>,
        newPage: Int? = null,
        onChange: ((GUIClickEvent<T>) -> Unit)? = null
    ) = defineSlots(
        slots, GUIButtonInventoryChange(
            icon,
            newGUI,
            newPage,
            onChange
        )
    )

    /**
     * Creates a new compound, holding data which can be displayed
     * in any compound space.
     */
    fun <E> createCompound(
        iconGenerator: (E) -> ItemStack,
        onClick: (clickEvent: GUIClickEvent<T>, element: E) -> Unit
    ) = GUISpaceCompound(type, iconGenerator, onClick)

    /**
     * Creates a new compound, holding data which can be displayed
     * in any compound space.
     * This compound is strictly a rectangle.
     * The space is automatically defined.
     */
    fun <E> createCompound(
        fromSlot: SingleInventorySlot<out T>,
        toSlot: SingleInventorySlot<out T>,
        iconGenerator: (E) -> ItemStack,
        onClick: (clickEvent: GUIClickEvent<T>, element: E) -> Unit
    ): GUIRectSpaceCompound<T, E> {
        val rectSlotCompound = fromSlot rectTo toSlot
        return GUIRectSpaceCompound(
            type,
            iconGenerator,
            onClick,
            (rectSlotCompound.endInclusive.slotInRow - rectSlotCompound.start.slotInRow) + 1
        ).apply {
            addSlots(rectSlotCompound)
            defineSlots(
                rectSlotCompound,
                GUISpaceCompoundElement(this)
            )
        }
    }

    /**
     * Defines an area where the content of the given compound
     * is displayed.
     */
    fun <E> compoundSpace(
        slots: InventorySlotCompound<T>,
        compound: AbstractGUISpaceCompound<T, E>
    ) {
        compound.addSlots(slots)
        defineSlots(
            slots,
            GUISpaceCompoundElement(compound)
        )
    }

    /**
     * By pressing this button,
     * the user scrolls forwards or backwards in the compound.
     */
    fun compoundScroll(
        slots: InventorySlotCompound<T>,
        icon: ItemStack,
        compound: GUISpaceCompound<T, *>,
        scrollDistance: Int = 1,
        scrollTimes: Int = 1,
        reverse: Boolean = false
    ) = defineSlots(
        slots,
        GUISpaceCompoundScrollButton(
            icon,
            compound,
            scrollDistance.absoluteValue,
            scrollTimes,
            reverse
        )
    )

    /**
     * By pressing this button,
     * the user scrolls forwards or backwards in the compound.
     */
    fun compoundScroll(
        slots: InventorySlotCompound<T>,
        icon: ItemStack,
        compound: GUIRectSpaceCompound<T, *>,
        scrollTimes: Int = 1,
        reverse: Boolean = false
    ) = defineSlots(
        slots,
        GUISpaceCompoundScrollButton(
            icon,
            compound,
            compound.compoundWidth,
            scrollTimes,
            reverse
        )
    )

}