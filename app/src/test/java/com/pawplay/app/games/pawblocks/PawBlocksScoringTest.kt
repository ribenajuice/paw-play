package com.pawplay.app.games.pawblocks

import com.pawplay.app.data.BestScore
import org.junit.Assert.assertEquals
import org.junit.Test

/** The points table (docs/PRD.md story 63) as plain numbers. */
class PawBlocksScoringTest {
    @Test
    fun `a block is worth 3 and lines add 10, 30, 60 and 100 for one, two, three and four or more`() {
        assertEquals(3, PawBlocksScoring.PLACE_POINTS)
        assertEquals(listOf(0, 10, 30, 60, 100), (0..4).map { PawBlocksScoring.lineBonus(it) })
        assertEquals(listOf(3, 13, 33, 63, 103), (0..4).map { PawBlocksScoring.placementPoints(it) })
        for (lines in 4..20) assertEquals("$lines lines", 100, PawBlocksScoring.lineBonus(lines))
        assertEquals(0, PawBlocksScoring.lineBonus(-3))
    }

    @Test
    fun `the score is capped at six digits, the same cap as the saved best`() {
        assertEquals(999_999, PawBlocksScoring.MAX_SCORE)
        assertEquals(BestScore.MAX, PawBlocksScoring.MAX_SCORE)
        assertEquals(999_999, PawBlocksScoring.add(999_990, 103))
        assertEquals(999_999, PawBlocksScoring.add(999_999, 3))
        assertEquals(999_999, PawBlocksScoring.add(Int.MAX_VALUE, 3))
        assertEquals(16, PawBlocksScoring.add(3, 13))
    }

    @Test
    fun `points never take anything away`() {
        assertEquals(50, PawBlocksScoring.add(50, 0))
        assertEquals(0, PawBlocksScoring.add(0, -5))
    }
}
