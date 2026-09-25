package com.pawplay.app.games.pawtrace

import kotlin.math.max
import kotlin.math.min

/**
 * Where things go on the Paw Trace play screen, as plain numbers in dp (docs/DESIGN-SYSTEM.md, "Play
 * screen layout"). The reference play area is 360 x 692dp: home 56dp at (20, 20); the glyph box 320dp
 * at left 20, top 186 (centred at 50% of the height); the play-on button 88dp centred with its top at
 * 560. On any other phone the box scales to fit and every dp measure that is sized for a fingertip
 * (band, corridor, marker, buttons) stays fixed. The glyph never sits under home or play-on.
 */
data class TraceLayout(
    val boxLeft: Float,
    val boxTop: Float,
    val boxSize: Float,
    val playOnLeft: Float,
    val playOnTop: Float,
) {
    /** dp per glyph unit. */
    val unitDp: Float get() = boxSize / 100f
    val boxBottom: Float get() = boxTop + boxSize
    val playOnBottom: Float get() = playOnTop + PLAY_ON_SIZE

    companion object {
        const val HOME_SIZE = 56f
        const val HOME_INSET = 20f
        const val PLAY_ON_SIZE = 88f
        const val REFERENCE_BOX = 320f
        const val MIN_BOX = 288f
        const val SIDE_MARGIN = 20f
        const val MIN_SIDE_MARGIN = 16f
        const val CLEARANCE = 16f
        const val PLAY_ON_BOTTOM_MARGIN = 44f
        const val MIN_PLAY_ON_BOTTOM_MARGIN = 12f
        const val FLOOR_BOX = 100f // only so a zero-sized or absurd window cannot produce a zero or negative box
    }
}

/**
 * Lays the screen out for a play area [width] x [height] dp (inside any system bars).
 *
 * The box is at most 320dp and at least 288dp wherever the screen allows it: side margins are 20dp and
 * shrink to 16dp before the box drops below 288dp. It is centred vertically on the play area when that
 * keeps 16dp clear of the home button above and the play-on button below; otherwise it moves down or up
 * only as far as needed, and the play-on button's bottom margin (44dp) shrinks to as little as 12dp.
 * On a screen too short even for that (under about 496dp tall) the play-on button may overlap the bottom
 * of the glyph; it only exists on the celebration, when the glyph is finished.
 */
fun traceLayout(width: Float, height: Float): TraceLayout {
    val homeBottom = TraceLayout.HOME_INSET + TraceLayout.HOME_SIZE
    val clear = TraceLayout.CLEARANCE

    var size = min(TraceLayout.REFERENCE_BOX, width - 2 * TraceLayout.SIDE_MARGIN)
    if (size < TraceLayout.MIN_BOX) size = min(TraceLayout.MIN_BOX, width - 2 * TraceLayout.MIN_SIDE_MARGIN)
    // Shrink for a short screen, but never below the minimum box for that.
    val roomHeight = height - (homeBottom + clear) - (clear + TraceLayout.PLAY_ON_SIZE + TraceLayout.MIN_PLAY_ON_BOTTOM_MARGIN)
    if (size > roomHeight) size = max(roomHeight, min(size, TraceLayout.MIN_BOX))
    size = max(size, TraceLayout.FLOOR_BOX)

    // Vertical position: centred if possible, else as near to centred as the two clearances allow.
    val highestTop = homeBottom + clear
    val lowestTop = height - TraceLayout.MIN_PLAY_ON_BOTTOM_MARGIN - TraceLayout.PLAY_ON_SIZE - clear - size
    val centred = height / 2f - size / 2f
    val boxTop = if (highestTop > lowestTop) highestTop else centred.coerceIn(highestTop, lowestTop)

    val idealPlayOnTop = height - TraceLayout.PLAY_ON_BOTTOM_MARGIN - TraceLayout.PLAY_ON_SIZE
    val lowestPlayOnTop = height - TraceLayout.MIN_PLAY_ON_BOTTOM_MARGIN - TraceLayout.PLAY_ON_SIZE
    val playOnTop = min(max(boxTop + size + clear, idealPlayOnTop), lowestPlayOnTop)

    return TraceLayout(
        boxLeft = (width - size) / 2f,
        boxTop = boxTop,
        boxSize = size,
        playOnLeft = (width - TraceLayout.PLAY_ON_SIZE) / 2f,
        playOnTop = playOnTop,
    )
}
