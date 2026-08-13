package com.coxmegascale.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.util.Collections;
import org.junit.Test;

/** Regression examples for scaling anchors, point estimates, caps, and odds. */
public class RaidMathTest
{
    private final RaidMath raidMath = new RaidMath();

    @Test
    public void purpleChanceMatchesKnownExample()
    {
        RaidMath.PurpleChance result = raidMath.calculatePurpleChance(957_000);

        assertEquals(2, result.potentialRolls);
        assertEquals("19.00%", RaidMath.formatPercent(result.distribution.get(0)));
        assertEquals("51.69%", RaidMath.formatPercent(result.distribution.get(1)));
        assertEquals("29.31%", RaidMath.formatPercent(result.distribution.get(2)));
    }

    @Test
    public void roomPointsMatchKnownScale()
    {
        RaidMath.RoomPoints result = raidMath.calculateRoomPoints(22, RaidMath.RaidOptions.defaults(22));

        assertEquals(534_729, result.grandTotal);
    }

    @Test
    public void crabsAddFixedExpectedPointsWhenPresentInLayout()
    {
        RaidMath.RaidOptions options = new RaidMath.RaidOptions(
            22,
            0,
            75,
            RaidMath.PUZZLE_ICE_DEMON,
            RaidMath.PUZZLE_TIGHTROPE,
            false,
            true
        );
        RaidMath.RoomPoints result = raidMath.calculateRoomPoints(22, options);

        assertEquals(536_329, result.grandTotal);
        assertEquals("Crabs", result.rooms.get(3).name);
        assertEquals(1_600, result.rooms.get(3).points);
    }

    @Test
    public void zeroPointsHaveZeroPurpleChance()
    {
        RaidMath.PurpleChance result = raidMath.calculatePurpleChance(0);
        assertEquals(0, result.points);
        assertEquals(0, result.potentialRolls);
        assertEquals(0.0, result.expectedPurples, 0.0);
        assertEquals(1.0, result.distribution.get(0), 0.0);
    }

    @Test
    public void purpleRollsUseExactBoundariesAndCapAtSix()
    {
        assertEquals(1, raidMath.calculatePurpleChance(570_000).potentialRolls);
        assertEquals(2, raidMath.calculatePurpleChance(570_001).potentialRolls);
        RaidMath.PurpleChance capped = raidMath.calculatePurpleChance(4_000_000);
        assertEquals(6, capped.potentialRolls);
        assertEquals(3_420_000 / 867_500.0, capped.expectedPurples, 0.000_000_1);
    }

    @Test
    public void fishingCapUsesRaidScaleWithoutPotionBonusDoses()
    {
        assertEquals(20, raidMath.calculateFishingPoints(1, 75).fishToCap);
        assertEquals(440, raidMath.calculateFishingPoints(22, 75).fishToCap);
        assertEquals(2_000, raidMath.calculateFishingPoints(100, 75).fishToCap);
    }

    @Test
    public void scoutedRoomOptionsExcludeAbsentCombatRoomsAndCountDuplicatePuzzles()
    {
        RaidMath.RaidOptions options = new RaidMath.RaidOptions(22, 0, 75,
            RaidMath.PUZZLE_TIGHTROPE, RaidMath.PUZZLE_TIGHTROPE, "", false, false, 12,
            false, true, false, false);

        RaidMath.RoomPoints result = raidMath.calculateRoomPoints(22, options);
        assertEquals(0, result.rooms.stream().filter(row -> "guardians".equals(row.key)).count());
        assertEquals(1, result.rooms.stream().filter(row -> "mystics".equals(row.key)).count());
        assertEquals(2, result.rooms.stream().filter(row -> "tightrope".equals(row.key)).count());
    }

    @Test
    public void missingCombatRoomsUseBaselineContributionEstimates()
    {
        RaidMath.RaidOptions options = new RaidMath.RaidOptions(100, 0, 75,
            "", "", "", false, false, 0,
            false, false, false, true, true, true, true, true, false);

        RaidMath.RoomPoints result = raidMath.calculateRoomPoints(100, options);

        assertEquals(5, result.rooms.stream()
            .filter(row -> row.note != null && row.note.contains("Combat contribution estimate"))
            .count());
        assertEquals(2_271, raidMath.calculateMuttadilesPoints(1));
        assertEquals(1_362, raidMath.calculateTektonPoints(1));
        assertEquals(2_282, raidMath.calculateVasaPoints(1));
        assertEquals(1_948, raidMath.calculateVespulaPoints(1));
        assertEquals(2_497, raidMath.calculateVanguardsPoints(1));
        assertEquals(4_205, raidMath.calculateMuttadilesPoints(2));
        assertEquals(2_467, raidMath.calculateTektonPoints(2));
        assertEquals(3_498, raidMath.calculateVasaPoints(2));
        assertEquals(3_383, raidMath.calculateVespulaPoints(2));
        assertEquals(5_410, raidMath.calculateVanguardsPoints(2));
    }

    @Test
    public void overloadSipOverrideChangesExpectedOverloadPoints()
    {
        RaidMath.RaidOptions options = new RaidMath.RaidOptions(
            22,
            0,
            75,
            RaidMath.PUZZLE_ICE_DEMON,
            RaidMath.PUZZLE_TIGHTROPE,
            false,
            false,
            12
        );

        RaidMath.RoomPoints result = raidMath.calculateRoomPoints(22, options);
        RaidMath.PointRow overloads = result.rooms.stream()
            .filter(row -> "overloads".equals(row.key))
            .findFirst()
            .orElseThrow(AssertionError::new);

        assertEquals(7_200, overloads.points);
        assertEquals(472_929, result.grandTotal);
    }

    @Test
    public void calculatesOnlyTheRemainingOverloadPointsForCurrentExpected()
    {
        assertEquals(300_000, raidMath.calculateRemainingOverloadPoints(5, 505));
        assertEquals(0, raidMath.calculateRemainingOverloadPoints(505, 505));
        assertEquals(0, raidMath.calculateRemainingOverloadPoints(506, 505));
    }

    @Test
    public void iceDemonStatsStayFixedAcrossRaidScale()
    {
        RaidMath.StatSection puzzleRooms = raidMath.calculateNpcStats(100).stream()
            .filter(section -> "puzzleRooms".equals(section.key))
            .findFirst()
            .orElseThrow(AssertionError::new);
        RaidMath.StatGroup iceDemon = puzzleRooms.groups.stream()
            .filter(group -> "Ice Demon".equals(group.name))
            .findFirst()
            .orElseThrow(AssertionError::new);

        assertEquals(160, iceDemon.stats.get("defence").intValue());
        assertEquals(390, iceDemon.stats.get("magic").intValue());
    }

    @Test
    public void npcStatsAreReusedUntilTheScaleChanges()
    {
        java.util.List<RaidMath.StatSection> scaleHundred = raidMath.calculateNpcStats(100);
        assertSame(scaleHundred, raidMath.calculateNpcStats(100));
        java.util.List<RaidMath.StatSection> scaleNinetyNine = raidMath.calculateNpcStats(99);
        assertNotSame(scaleHundred, scaleNinetyNine);
        assertSame(scaleNinetyNine, raidMath.calculateNpcStats(99));
    }

    @Test
    public void olmPointsCapAtScaleFiftyOne()
    {
        RaidMath.OlmPoints result = raidMath.calculateOlmPoints(100);

        assertEquals(56_440, result.head);
        assertEquals(761_940, result.hands);
        assertEquals(818_380, result.total);
        assertEquals(9, result.phases);
        assertEquals(51, result.cappedScale);
    }

    @Test
    public void expectedOlmPointsShowHandsAndPerPhaseWithoutHead()
    {
        RaidMath.RoomPoints result = raidMath.calculateRoomPoints(100, RaidMath.RaidOptions.defaults(100));

        assertEquals("Olm hands", result.totals.get(0).name);
        assertEquals(761_940, result.totals.get(0).points);
        assertEquals("Per Phase", result.totals.get(1).name);
        assertEquals(84_660, result.totals.get(1).points);
    }

    @Test
    public void ralosCountsIndividualSuccessfulHits()
    {
        SpecPlanner planner = new SpecPlanner(raidMath);
        SpecPlanner.SpecSummary olmHead = planner.buildCoxSpecPlan(100, Collections.emptyList())
            .stream()
            .filter(summary -> "olmHead".equals(summary.roomKey))
            .findFirst()
            .orElseThrow(AssertionError::new);

        assertEquals(Integer.valueOf(5), olmHead.specs.get("Ralos"));
        assertEquals("5x Ralos", olmHead.lines.get(1));
    }

    @Test
    public void mysticsDefencePlanUsesElderMaulsThenRalos()
    {
        SpecPlanner planner = new SpecPlanner(raidMath);
        SpecPlanner.SpecSummary mystics = planner.buildCoxSpecPlan(100, Collections.emptyList())
            .stream()
            .filter(summary -> "mystics".equals(summary.roomKey))
            .findFirst()
            .orElseThrow(AssertionError::new);

        assertEquals(Integer.valueOf(2), mystics.specs.get("Elder Maul"));
        assertEquals(Integer.valueOf(4), mystics.specs.get("Ralos"));
        assertEquals(Integer.valueOf(2), mystics.allStatSpecs.get("Elder Maul"));
        assertEquals(Integer.valueOf(2), mystics.allStatSpecs.get("Ralos"));
    }
}
