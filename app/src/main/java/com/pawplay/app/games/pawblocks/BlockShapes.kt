package com.pawplay.app.games.pawblocks

/**
 * The block set of Paw Blocks: eight families, sixteen shapes (docs/DESIGN-SYSTEM.md, "Block encoding").
 * Pure Kotlin, no Compose, so the rules and the colour tests run on a plain JVM. Colours are 0xAARRGGBB
 * longs; the Compose files turn them into `Color`.
 *
 * A family owns a colour and a mark (a small picture drawn on every cell, so blocks differ by more than
 * hue). Blocks never rotate: a bar lying down and standing up are two shapes of one family.
 */
enum class Mark { STAR, HEART, DROP, FISH, LEAF, BONE, PAW, MOON }

const val INK_COLOUR = 0xFF2B2320L
const val WHITE_COLOUR = 0xFFFFFFFFL

/** Colours run light to dark in the order the families arrive on the ramp; stage 1 is the two brightest. */
enum class Family(val stage: Int, val colour: Long, val mark: Mark, val markColour: Long) {
    DOT(1, 0xFFFFD23FL, Mark.STAR, INK_COLOUR),        // Sunshine
    BAR2(1, 0xFFFF9ACBL, Mark.HEART, INK_COLOUR),      // Bubblegum
    BAR3(2, 0xFF28A9E3L, Mark.DROP, INK_COLOUR),       // Sky
    SQUARE(3, 0xFFE85A10L, Mark.FISH, INK_COLOUR),     // Tangerine
    CORNER(3, 0xFF1A8344L, Mark.LEAF, WHITE_COLOUR),   // Leaf
    BAR4(4, 0xFF83532BL, Mark.BONE, WHITE_COLOUR),     // Cocoa
    RECT(5, 0xFF5A31A6L, Mark.PAW, WHITE_COLOUR),      // Grape
    BAR5(6, 0xFF223475L, Mark.MOON, WHITE_COLOUR),     // Midnight
}

/** A cell offset inside a block: [col] to the right, [row] down, from the block's top-left. */
data class Cell(val col: Int, val row: Int)

class BlockShape(val id: String, val family: Family, val cells: List<Cell>) {
    val width: Int = cells.maxOf { it.col } + 1
    val height: Int = cells.maxOf { it.row } + 1
    val size: Int get() = cells.size
    override fun toString() = id
}

object BlockShapes {
    private fun bar(id: String, family: Family, length: Int, horizontal: Boolean) =
        BlockShape(id, family, (0 until length).map { if (horizontal) Cell(it, 0) else Cell(0, it) })

    private fun block(id: String, family: Family, vararg cells: Pair<Int, Int>) =
        BlockShape(id, family, cells.map { Cell(it.first, it.second) })

    /** Every shape, in family order (which is the order they arrive on the ramp). */
    val all: List<BlockShape> = listOf(
        block("dot", Family.DOT, 0 to 0),
        bar("bar2h", Family.BAR2, 2, true), bar("bar2v", Family.BAR2, 2, false),
        bar("bar3h", Family.BAR3, 3, true), bar("bar3v", Family.BAR3, 3, false),
        block("sq", Family.SQUARE, 0 to 0, 1 to 0, 0 to 1, 1 to 1),
        // The letters name where the elbow cell is.
        block("cTL", Family.CORNER, 0 to 0, 1 to 0, 0 to 1),
        block("cTR", Family.CORNER, 0 to 0, 1 to 0, 1 to 1),
        block("cBR", Family.CORNER, 1 to 0, 0 to 1, 1 to 1),
        block("cBL", Family.CORNER, 0 to 0, 0 to 1, 1 to 1),
        bar("bar4h", Family.BAR4, 4, true), bar("bar4v", Family.BAR4, 4, false),
        block("rectV", Family.RECT, 0 to 0, 1 to 0, 0 to 1, 1 to 1, 0 to 2, 1 to 2),
        block("rectH", Family.RECT, 0 to 0, 1 to 0, 2 to 0, 0 to 1, 1 to 1, 2 to 1),
        bar("bar5h", Family.BAR5, 5, true), bar("bar5v", Family.BAR5, 5, false),
    )

    val byId: Map<String, BlockShape> = all.associateBy { it.id }

    val dot: BlockShape = byId.getValue("dot")

    /** What can be on offer at [stage]: every family that has arrived by then (dots stay in every stage). */
    fun forStage(stage: Int): List<BlockShape> = all.filter { it.family.stage <= stage }
}
