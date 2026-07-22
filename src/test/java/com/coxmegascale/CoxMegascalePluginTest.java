package com.coxmegascale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.coxmegascale.party.CoxTeamUpdate;
import com.coxmegascale.party.MysticsSpecUpdate;
import com.coxmegascale.calc.DefenceTracker;
import java.util.Arrays;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.raids.RaidsPlugin;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CoxMegascalePluginTest
{
    @Test
    public void testPluginLoads() throws Exception
    {
        ExternalPluginManager.loadBuiltin(CoxMegascalePlugin.class);
    }

    @Test
    public void declaresBuiltInRaidsPluginDependency()
    {
        PluginDependency dependency = CoxMegascalePlugin.class.getAnnotation(PluginDependency.class);
        assertEquals(RaidsPlugin.class, dependency.value());
    }

    @Test
    public void partyMessagesDeclareExplicitSchemaVersions()
    {
        assertEquals(0, new CoxTeamUpdate().schemaVersion);
        assertEquals(0, new MysticsSpecUpdate().schemaVersion);
        CoxTeamUpdate team = new CoxTeamUpdate("Raider", 0, null, Arrays.asList(), 0, 0,
            Arrays.asList(), false, -1, 99);
        MysticsSpecUpdate spec = new MysticsSpecUpdate("mystics:1", "BGS", 1, true, 42);
        assertEquals(false, CoxMegascalePlugin.compatiblePartySchema(new CoxTeamUpdate().schemaVersion,
            CoxTeamUpdate.SCHEMA_VERSION));
        assertEquals(false, CoxMegascalePlugin.compatiblePartySchema(new MysticsSpecUpdate().schemaVersion,
            MysticsSpecUpdate.SCHEMA_VERSION));
        assertEquals(CoxTeamUpdate.SCHEMA_VERSION, team.schemaVersion);
        assertEquals(MysticsSpecUpdate.SCHEMA_VERSION, spec.schemaVersion);
        assertEquals(true, CoxMegascalePlugin.compatiblePartySchema(team.schemaVersion, CoxTeamUpdate.SCHEMA_VERSION));
    }

    @Test
    public void outsideRaidPresenceDoesNotExposeRaidState()
    {
        CoxTeamUpdate update = CoxMegascalePlugin.emptyCoxPresenceUpdate();
        assertNull(update.displayName);
        assertEquals(false, update.inCoxRaid);
        assertEquals(-1, update.raidPartyId);
        assertEquals(0, update.personalPoints);
        assertEquals(0, update.thievingLevel);
        assertEquals(true, update.deaths.isEmpty());
        assertEquals(true, update.fishEaten.isEmpty());
        assertEquals(true, update.prepResources.isEmpty());
        assertEquals(true, update.craftedPotions.isEmpty());
        assertEquals(true, update.sharedStorage.isEmpty());
    }

    @Test
    public void canonicalPointRoomsIncludeAllCoxEncounters()
    {
        assertEquals("Muttadiles", CoxMegascalePlugin.canonicalPointRoom("Muttadile"));
        assertEquals("Vespula", CoxMegascalePlugin.canonicalPointRoom("Abyssal portal"));
        assertEquals("Thieving", CoxMegascalePlugin.canonicalPointRoom("Cavern grubs"));
        assertEquals("Mystics", CoxMegascalePlugin.canonicalPointRoom("Skeletal Mystic"));
        assertEquals("Olm", CoxMegascalePlugin.canonicalPointRoom("Great Olm"));
        assertNull(CoxMegascalePlugin.canonicalPointRoom("Chest"));
    }

    @Test
    public void resolvesPlayerRoomFromScoutedRaidGrid()
    {
        WorldPoint gridBase = new WorldPoint(3_200, 3_200, 3);

        assertEquals(0, CoxMegascalePlugin.roomIndexAt(gridBase, 0, new WorldPoint(3_215, 3_215, 3)));
        assertEquals(1, CoxMegascalePlugin.roomIndexAt(gridBase, 0, new WorldPoint(3_232, 3_215, 3)));
        assertEquals(8, CoxMegascalePlugin.roomIndexAt(gridBase, 0, new WorldPoint(3_215, 3_215, 2)));
    }

    @Test
    public void distinguishesChallengeModeWithoutDependingOnRaidScouted()
    {
        assertEquals("normal", CoxMegascalePlugin.raidModeFromChallengeVarbit(0));
        assertEquals("challenge", CoxMegascalePlugin.raidModeFromChallengeVarbit(1));
    }

    @Test
    public void ordersFullAndChallengeLayoutsInPublishedTraversalOrder()
    {
        java.util.List<CoxMegascaleState.ScoutedRoom> rooms = new java.util.ArrayList<>(Arrays.asList(
            new CoxMegascaleState.ScoutedRoom("Combat", "Muttadiles"),
            new CoxMegascaleState.ScoutedRoom("Puzzle", "Tightrope"),
            new CoxMegascaleState.ScoutedRoom("Combat", "Tekton"),
            new CoxMegascaleState.ScoutedRoom("Combat", "Mystics"),
            new CoxMegascaleState.ScoutedRoom("Combat", "Vasa"),
            new CoxMegascaleState.ScoutedRoom("Combat", "Guardians"),
            new CoxMegascaleState.ScoutedRoom("Combat", "Vespula"),
            new CoxMegascaleState.ScoutedRoom("Puzzle", "Thieving"),
            new CoxMegascaleState.ScoutedRoom("Combat", "Vanguards"),
            new CoxMegascaleState.ScoutedRoom("Combat", "Shamans"),
            new CoxMegascaleState.ScoutedRoom("Puzzle", "Ice Demon"),
            new CoxMegascaleState.ScoutedRoom("Puzzle", "Crabs")
        ));

        assertEquals(true, CoxMegascalePlugin.isFullLayout(rooms));
        CoxMegascalePlugin.orderFullLayoutRooms(rooms);
        assertEquals(Arrays.asList("Tekton", "Crabs", "Ice Demon", "Shamans", "Vanguards", "Thieving",
            "Vespula", "Tightrope", "Guardians", "Vasa", "Mystics", "Muttadiles"),
            rooms.stream().map(room -> room.name).collect(java.util.stream.Collectors.toList()));
    }

    @Test
    public void parsesScaledPartySizeWithoutRegexAllocations()
    {
        assertEquals(Integer.valueOf(42), CoxMegascalePlugin.parseScaledPartySize("Scaled party size: 42"));
        assertEquals(Integer.valueOf(100), CoxMegascalePlugin.parseScaledPartySize("<col=ffffff>SCALED PARTY SIZE:</col>&nbsp;<col=00ff00>100</col>"));
        assertNull(CoxMegascalePlugin.parseScaledPartySize("Scaled party size: 0"));
        assertNull(CoxMegascalePlugin.parseScaledPartySize("Scaled party size: 101"));
        assertNull(CoxMegascalePlugin.parseScaledPartySize("Party size: 50"));
    }

    @Test
    public void parsesOnlyDisplayNamesFromRaidPartyRows()
    {
        assertEquals("Raider One", CoxMegascalePlugin.raidParticipantName("<col=ffffff>Raider\u00a0One</col>"));
        assertEquals("Iron-Woman", CoxMegascalePlugin.raidParticipantName("<col=ffffff>Iron-Woman</col><br>Combat: 126"));
        assertNull(CoxMegascalePlugin.raidParticipantName("126"));
        assertNull(CoxMegascalePlugin.raidParticipantName("Start raid"));
        assertNull(CoxMegascalePlugin.raidParticipantName("A name that is too long"));
    }

    @Test
    public void backsOffUnsuccessfulScaleWidgetSearches()
    {
        assertEquals(true, CoxMegascalePlugin.shouldSearchScaleWidget(100, Integer.MIN_VALUE));
        assertEquals(false, CoxMegascalePlugin.shouldSearchScaleWidget(104, 100));
        assertEquals(true, CoxMegascalePlugin.shouldSearchScaleWidget(105, 100));
    }

    @Test
    public void retainsLobbyPartyIdWhenRuneLiteClearsItInsideTheRaid()
    {
        assertEquals(42, CoxMegascalePlugin.effectiveCoxRaidPartyId(42, false, -1));
        assertEquals(42, CoxMegascalePlugin.effectiveCoxRaidPartyId(-1, true, 42));
        assertEquals(-1, CoxMegascalePlugin.effectiveCoxRaidPartyId(-1, false, 42));
    }

    @Test
    public void observesThePrivateCoxStorageContainerInsteadOfTheRewardsChest()
    {
        assertEquals(true, CoxMegascalePlugin.isPrivateCoxStorageContainer(net.runelite.api.gameval.InventoryID.RAIDS_PRIVATESTORAGE));
        assertEquals(false, CoxMegascalePlugin.isPrivateCoxStorageContainer(net.runelite.api.InventoryID.CHAMBERS_OF_XERIC_CHEST.getId()));
    }

    @Test
    public void showsInfoBoxesOnlyWhenEnabledInsideAScoutedRaid()
    {
        assertEquals(false, CoxMegascalePlugin.shouldShowInfoBoxes(false, true, true));
        assertEquals(false, CoxMegascalePlugin.shouldShowInfoBoxes(true, false, true));
        assertEquals(false, CoxMegascalePlugin.shouldShowInfoBoxes(true, true, false));
        assertEquals(true, CoxMegascalePlugin.shouldShowInfoBoxes(true, true, true));
    }

    @Test
    public void generalClientStatePollingDoesNotRunEveryRenderedFrame()
    {
        boolean gameTickSubscriber = false;
        boolean clientTickSubscriber = false;
        for (java.lang.reflect.Method method : CoxMegascalePlugin.class.getDeclaredMethods())
        {
            if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == GameTick.class)
            {
                gameTickSubscriber = true;
            }
            if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == ClientTick.class)
            {
                clientTickSubscriber = true;
            }
        }
        assertEquals(true, gameTickSubscriber);
        assertEquals(false, clientTickSubscriber);
    }

    @Test
    public void prayerEnhanceAutoTargetFollowsScaleThresholds()
    {
        CoxMegascaleState state = new CoxMegascaleState();

        state.setSipsToCap(5 * 24);
        assertEquals(4, state.getPrayerEnhanceTarget());
        state.setSipsToCap(5 * 25);
        assertEquals(6, state.getPrayerEnhanceTarget());
        state.setSipsToCap(5 * 41);
        assertEquals(8, state.getPrayerEnhanceTarget());
    }

    @Test
    public void prepTargetsClampPotionQuantitiesButAllowFishingDomain()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        state.setRevitalisationTarget(1_000);
        state.setXericsAidTarget(1_000);
        state.setPrayerEnhanceOverride(1_000);
        state.setCaveWormTarget(2_001);

        assertEquals(999, state.getRevitalisationTarget());
        assertEquals(999, state.getXericsAidTarget());
        assertEquals(999, state.getPrayerEnhanceTarget());
        assertEquals(CoxMegascaleState.MAX_CAVE_WORM_TARGET, state.getCaveWormTarget());
        assertEquals("Cave worms", state.getResources().get(state.getResources().size() - 1).key);
    }

    @Test
    public void sharesOnlyLocallyConfirmedDeathLosses()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        state.startRoom("Guardians");
        state.setCurrentPoints(100, 200);
        state.noteLocalDeath();

        assertEquals(true, state.setCurrentPoints(80, 140));
        assertEquals(1, state.getLocalTeamDeaths().size());
        assertEquals("Guardians", state.getLocalTeamDeaths().get(0).room);
        assertEquals(20, state.getLocalTeamDeaths().get(0).pointsLost);
        assertEquals(60, state.getDeathPointLosses().get(0).pointsLost);
    }

    @Test
    public void closesPreviousRoomBeforeStartingNextRoom()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        state.setCurrentPoints(1_000, 1_000);
        state.startRoom("Guardians");

        state.setCurrentPoints(1_500, 1_500);
        state.startRoom("Mystics");

        assertEquals(1, state.getTrackedRoomPoints().size());
        assertEquals("Guardians", state.getTrackedRoomPoints().get(0).room);
        assertEquals(500, state.getTrackedRoomPoints().get(0).personalPoints);
        assertEquals("Mystics", state.getActiveRoomName());
    }

    @Test
    public void retainsSharedPointsAndDeathLogPerMember()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        state.updateTeamMember(42L, "Teammate", 123_456, "Mystics", Arrays.asList(
            new CoxTeamUpdate.Death("Mystics", 5_000, 1_000L)
        ), 2, 3, Arrays.asList(new CoxTeamUpdate.Fish("Kyren", 90, 3)), 99);

        CoxMegascaleState.TeamMember member = state.getTeamMembers().get(0);
        assertEquals("Teammate", member.displayName);
        assertEquals(123_456, member.personalPoints);
        assertEquals(5_000, member.getDeathPointsLost());
        assertEquals(1_200, state.getTeamOverloadPoints());
        assertEquals(270, state.getTeamFishPoints());
        assertEquals(3, state.getTeamNonOverloadSips());
        assertEquals(1_800, state.getTeamNonOverloadPointsLost());
        assertEquals(true, state.hasTeamThievingTotal());
        assertEquals(99, state.getTeamThievingTotal());

        assertEquals(true, state.clearTeamMemberCoxData(42L, "Teammate"));
        member = state.getTeamMembers().get(0);
        assertEquals("Unknown", member.displayName);
        assertEquals(false, member.hasCoxData);
        assertEquals(0, member.personalPoints);
        assertNull(member.activeRoom);
        assertEquals(0, state.getTeamOverloadPoints());
        assertEquals(0, state.getTeamFishPoints());
        assertEquals(false, state.hasTeamThievingTotal());

        assertEquals(true, state.removeTeamMember(42L));
        assertEquals(false, state.hasTeamThievingTotal());
    }

    @Test
    public void teamMembersDefaultToRuneLitePartyOrder()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        state.updateTeamMember(1L, "First", 0, null, Arrays.asList(), 0, 0, Arrays.asList(), 99);
        state.updateTeamMember(2L, "Second", 0, null, Arrays.asList(), 0, 0, Arrays.asList(), 99);
        PartyMember second = mock(PartyMember.class);
        PartyMember first = mock(PartyMember.class);
        when(second.getMemberId()).thenReturn(2L);
        when(first.getMemberId()).thenReturn(1L);

        assertEquals(true, state.syncTeamMembers(Arrays.asList(second, first)));
        assertEquals(Arrays.asList(2L, 1L), Arrays.asList(
            state.getTeamMembers().get(0).memberId, state.getTeamMembers().get(1).memberId));
        assertEquals(Arrays.asList("Second", "First"), Arrays.asList(
            state.getTeamMembers().get(0).displayName, state.getTeamMembers().get(1).displayName));
        org.mockito.Mockito.verify(second, org.mockito.Mockito.never()).getDisplayName();
        org.mockito.Mockito.verify(second, org.mockito.Mockito.never()).isLoggedIn();
        org.mockito.Mockito.verify(first, org.mockito.Mockito.never()).getDisplayName();
        org.mockito.Mockito.verify(first, org.mockito.Mockito.never()).isLoggedIn();
    }

    @Test
    public void duplicatePartySnapshotsDoNotTriggerAnotherUpdate()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        java.util.List<CoxTeamUpdate.Death> deaths = Arrays.asList(new CoxTeamUpdate.Death("Mystics", 5_000, 1_000L));
        java.util.List<CoxTeamUpdate.Fish> fish = Arrays.asList(new CoxTeamUpdate.Fish("Kyren", 90, 3));

        assertEquals(true, state.updateTeamMember(42L, "Teammate", 123_456, "Mystics", deaths, 2, 3, fish, 99));
        for (int i = 0; i < 1_000; i++)
        {
            assertEquals(false, state.updateTeamMember(42L, "Teammate", 123_456, "Mystics", deaths, 2, 3, fish, 99));
        }
        assertEquals(true, state.updateTeamMember(42L, "Teammate", 123_457, "Mystics", deaths, 2, 3, fish, 99));
    }

    @Test
    public void ignoresNonRaidPartyMembersWhenCalculatingTeamThieving()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        state.updateTeamMember(42L, "Raider", 0, null, Arrays.asList(), 0, 0, Arrays.asList(), 99);
        state.clearTeamMemberCoxData(43L, "Outside raid");

        assertEquals(true, state.hasTeamThievingTotal());
        assertEquals(99, state.getTeamThievingTotal());
    }

    @Test
    public void clearsAllRaidDataWhenTheLocalPlayerLeavesCox()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        state.updateTeamMember(1L, "First", 10_000, "Mystics", Arrays.asList(), 2, 0, Arrays.asList(), 99);
        state.updateTeamMember(2L, "Second", 20_000, "Shamans", Arrays.asList(), 3, 1, Arrays.asList(), 98);

        assertEquals(true, state.clearAllTeamCoxData());
        assertEquals(false, state.getTeamMembers().get(0).hasCoxData);
        assertEquals(false, state.getTeamMembers().get(1).hasCoxData);
        assertEquals(0, state.getTeamOverloadPoints());
    }

    @Test
    public void requestsExistingRaidSnapshotsOnlyForAnActiveMembershipChange()
    {
        assertEquals(true, CoxMegascalePlugin.shouldRequestTeamSnapshot(true, true));
        assertEquals(false, CoxMegascalePlugin.shouldRequestTeamSnapshot(false, true));
        assertEquals(false, CoxMegascalePlugin.shouldRequestTeamSnapshot(true, false));
        assertEquals(true, CoxMegascalePlugin.shouldClearTeamCoxData(true, false));
        assertEquals(false, CoxMegascalePlugin.shouldClearTeamCoxData(false, false));
    }

    @Test
    public void rateLimitsRepeatedRosterSnapshotRequests()
    {
        assertEquals(true, CoxMegascalePlugin.rosterSnapshotRequestAllowed(100, null));
        assertEquals(false, CoxMegascalePlugin.rosterSnapshotRequestAllowed(109, 100));
        assertEquals(true, CoxMegascalePlugin.rosterSnapshotRequestAllowed(110, 100));
        assertEquals(true, CoxMegascalePlugin.rosterSnapshotRequestAllowed(1, 100));
    }

    @Test
    public void allowsOneLateRalosHitsplatOnlyWhenTheExpectedTickHadNoHits()
    {
        assertEquals(true, CoxMegascalePlugin.acceptsPendingSpecHitsplat(12, 12, 13));
        assertEquals(true, CoxMegascalePlugin.acceptsPendingSpecHitsplat(13, 12, 13));
        assertEquals(false, CoxMegascalePlugin.acceptsPendingSpecHitsplat(14, 12, 13));
        assertEquals(true, CoxMegascalePlugin.shouldWaitForDelayedSpecHit(12, 12, 13, true));
        assertEquals(false, CoxMegascalePlugin.shouldWaitForDelayedSpecHit(12, 12, 13, false));
        assertEquals(false, CoxMegascalePlugin.shouldWaitForDelayedSpecHit(13, 12, 13, true));
    }

    @Test
    public void confirmsDragonWarhammerOnTheNormalOneTickWindow()
    {
        assertEquals(1, CoxMegascalePlugin.specHitsplatDelay("DWH"));
        assertEquals(1, CoxMegascalePlugin.specHitsplatDelay("BGS"));
        assertEquals(1, CoxMegascalePlugin.specHitsplatDelay("Eye of Ayak"));
        assertEquals(2, CoxMegascalePlugin.specHitsplatDelay("Elder Maul"));
        assertEquals(2, CoxMegascalePlugin.specHitsplatDelay("Ralos"));
        assertEquals(3, CoxMegascalePlugin.specHitsplatExpiryDelay("Eye of Ayak"));
        assertEquals(1, CoxMegascalePlugin.specHitsplatExpiryDelay("Ralos"));
        assertEquals(0, CoxMegascalePlugin.specHitsplatExpiryDelay("DWH"));
    }

    @Test
    public void convertsDamageDrainsAndProjectileHitsCorrectly()
    {
        assertEquals(47, CoxMegascalePlugin.confirmedSpecAmount("Eye of Ayak", Arrays.asList(47)));
        assertEquals(31, CoxMegascalePlugin.confirmedSpecAmount("BGS", Arrays.asList(31)));
        assertEquals(2, CoxMegascalePlugin.confirmedSpecAmount("Ralos", Arrays.asList(12, 0, 8)));
        assertEquals(1, CoxMegascalePlugin.confirmedSpecAmount("Elder Maul", Arrays.asList(52)));
    }

    @Test
    public void rejectsMalformedOrUnboundedDefenceTargetKeys()
    {
        assertEquals(true, CoxMegascaleState.isValidDefenceTargetKey("mystics:123"));
        assertEquals(true, CoxMegascaleState.isValidDefenceTargetKey("olmHead:p9"));
        assertEquals(false, CoxMegascaleState.isValidDefenceTargetKey("mystics:not-an-index"));
        assertEquals(false, CoxMegascaleState.isValidDefenceTargetKey("olmHead:p10"));
        assertEquals(false, CoxMegascaleState.isValidDefenceTargetKey("unknown:1"));
    }

    @Test
    public void sanitizesMalformedPartyPayloadCollections()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        CoxTeamUpdate.Fish spoofed = new CoxTeamUpdate.Fish("Kyren", 999_999, Integer.MAX_VALUE);
        CoxTeamUpdate.Death validDeath = new CoxTeamUpdate.Death("Olm", 500, 10);
        state.updateTeamMember(1L, "A name that is deliberately far too long for RuneScape", 10, "not a room",
            Arrays.asList(null, validDeath), 0, 0, Arrays.asList(null, spoofed), 500, null, null);

        CoxMegascaleState.TeamMember member = state.getTeamMembers().get(0);
        assertEquals(true, member.displayName.length() <= 32);
        assertNull(member.activeRoom);
        assertEquals(99, member.thievingLevel);
        assertEquals(1, member.deaths.size());
        assertEquals(90_000_000, member.getFishPoints());
    }

    @Test
    public void stripsSwingHtmlFromRemoteDisplayNames()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        state.updateTeamMember(1L, "<html><img src=https://bad.example>x", 0, null,
            Arrays.asList(), 0, 0, Arrays.asList(), 99);

        assertEquals("htmlimg srchttpsbadexamplex", state.getTeamMembers().get(0).displayName);
    }

    @Test
    public void hostilePartyTotalsSaturateInsteadOfOverflowing()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        CoxTeamUpdate.Fish fish = new CoxTeamUpdate.Fish("Kyren", 90, Integer.MAX_VALUE);
        java.util.List<CoxTeamUpdate.Death> deaths = new java.util.ArrayList<>();
        for (int i = 0; i < 64; i++)
        {
            deaths.add(new CoxTeamUpdate.Death("Olm", Integer.MAX_VALUE, i));
        }
        for (long memberId = 1; memberId <= 30; memberId++)
        {
            state.updateTeamMember(memberId, "Raider", Integer.MAX_VALUE, "Olm", deaths,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Arrays.asList(fish), 99, null, null);
        }

        assertEquals(Integer.MAX_VALUE, state.getTeamFishPoints());
        assertEquals(Integer.MAX_VALUE, state.getTeamOverloadPoints());
        assertEquals(Integer.MAX_VALUE, state.getTeamMembers().get(0).getDeathPointsLost());
    }

    @Test
    public void consumableUnitsConfirmDoseOrFoodInventoryReduction()
    {
        ItemContainer inventory = mock(ItemContainer.class);
        when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.OVERLOAD_4, 1)});
        assertEquals(4, CoxMegascaleState.consumableUnits(inventory, ItemID.OVERLOAD_4));
        when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.OVERLOAD_3, 1)});
        assertEquals(3, CoxMegascaleState.consumableUnits(inventory, ItemID.OVERLOAD_4));

        when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.KYREN_FISH_6, 2)});
        assertEquals(2, CoxMegascaleState.consumableUnits(inventory, ItemID.KYREN_FISH_6));
        when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.KYREN_FISH_6, 1)});
        assertEquals(1, CoxMegascaleState.consumableUnits(inventory, ItemID.KYREN_FISH_6));
    }

    @Test
    public void olderOrIdenticalSharedStorageSnapshotsDoNotReplaceFreshState()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        assertEquals(true, state.updateSharedStorage(Arrays.asList(new CoxTeamUpdate.ItemCount("Golpar", 10)), 2_000));
        assertEquals(false, state.updateSharedStorage(Arrays.asList(new CoxTeamUpdate.ItemCount("Golpar", 99)), 1_000));
        assertEquals(10, state.getSharedStorageCount("Golpar"));
        assertEquals(false, state.updateSharedStorage(Arrays.asList(new CoxTeamUpdate.ItemCount("Golpar", 10)), 3_000));
        assertEquals(3_000, state.getSharedStorageObservedAtMillis());
    }

    @Test
    public void rejectsFarFutureSharedStorageTimestamps()
    {
        assertEquals(false, CoxMegascaleState.validSharedStorageObservationTime(Long.MAX_VALUE, 1_000));
        assertEquals(true, CoxMegascaleState.validSharedStorageObservationTime(61_000, 1_000));
        assertEquals(false, CoxMegascaleState.validSharedStorageObservationTime(61_001, 1_000));
    }

    @Test
    public void nonOverloadSipsReduceTheEffectiveOverloadCapThroughoutTheRaid()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        state.setSipsToCap(505);

        assertEquals(true, state.recordNonOverloadSip(ItemID.REVITALISATION_4));
        assertEquals(true, state.recordNonOverloadSip(ItemID.PRAYER_ENHANCE_4));
        assertEquals(true, state.recordNonOverloadSip(ItemID.XERICS_AID_4));
        assertEquals(502, state.getEffectiveOverloadSipCap());
        assertEquals(1_800, state.getTeamNonOverloadPointsLost());

        state.startRoom("Olm");
        assertEquals(true, state.recordNonOverloadSip(ItemID.REVITALISATION_4));
        assertEquals(501, state.getEffectiveOverloadSipCap());
    }

    @Test
    public void keepsConsumedOverloadsAndFishOutOfRoomPoints()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        state.setCurrentPoints(0, 0);
        state.startRoom("Guardians");
        state.recordOverloadSip();
        state.recordFishEaten(net.runelite.api.ItemID.KYREN_FISH_6);
        state.setCurrentPoints(690, 690);

        assertEquals(0, state.getActiveRoomPersonalPoints());
        assertEquals(0, state.getActiveRoomGroupPoints());
        assertEquals(600, state.getLocalOverloadPoints());
        assertEquals(90, state.getLocalFishPoints());
    }

    @Test
    public void tracksMysticsEmberlightRouteFromSuccessfulSpecs()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        state.configureMysticsSpecTracker(100, true, true);
        state.startRoom("Mystics");

        assertEquals(2, state.getMysticsSpecRemaining("Elder Maul"));
        assertEquals(2, state.getMysticsSpecRemaining("Ralos"));
        assertEquals(16, state.getMysticsSpecRemaining("Emberlight"));
        assertEquals(368, state.getMysticsSpecRemaining("BGS"));

        assertEquals(true, state.recordMysticsSpecHit("Elder Maul", 1));
        assertEquals(true, state.recordMysticsSpecHit("Ralos", 2));
        assertEquals(true, state.recordMysticsSpecHit("Emberlight", 3));
        assertEquals(true, state.recordMysticsSpecHit("BGS", 150));

        assertEquals(1, state.getMysticsSpecRemaining("Elder Maul"));
        assertEquals(0, state.getMysticsSpecRemaining("Ralos"));
        assertEquals(13, state.getMysticsSpecRemaining("Emberlight"));
        assertEquals(218, state.getMysticsSpecRemaining("BGS"));
    }

    @Test
    public void recalculatesRemainingDefenceSpecsAfterANonStandardHit()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        state.configureDefenceTracker(100);
        state.startRoom("Mystics");

        state.activateDefenceTarget("mystics:1");
        assertEquals(333, state.getActiveDefenceTracker().getCurrentDefence());
        assertEquals(true, state.recordDefenceSpecHit("mystics:1", "Ralos", 1));
        assertEquals(297, state.getActiveDefenceTracker().getCurrentDefence());
        assertEquals(Integer.valueOf(2), state.getActiveDefenceTracker().getRemainingSpecs().get("Elder Maul"));
        assertEquals(Integer.valueOf(4), state.getActiveDefenceTracker().getRemainingSpecs().get("Ralos"));

        assertEquals(true, state.recordDefenceSpecHit("mystics:1", "BGS", 97));
        assertEquals(200, state.getActiveDefenceTracker().getCurrentDefence());
    }

    @Test
    public void defenceMenuColorReflectsDrainProgress()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        state.configureDefenceTracker(100);
        state.startRoom("Shamans");
        state.activateDefenceTarget("shamans:1");

        assertEquals(DefenceTracker.MENU_COLOR_FULL, state.getActiveDefenceTracker().getMenuColor());
        assertEquals(true, state.recordDefenceSpecHit("shamans:1", "BGS", 100));
        assertEquals(DefenceTracker.MENU_COLOR_DRAINED, state.getActiveDefenceTracker().getMenuColor());
        assertEquals(true, state.recordDefenceSpecHit("shamans:1", "BGS", 237));
        assertEquals(DefenceTracker.MENU_COLOR_TARGET, state.getActiveDefenceTracker().getMenuColor());
    }

    @Test
    public void tracksOlmMageHandMagicDefenceSeparately()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        state.configureDefenceTracker(100);
        state.startRoom("Olm");
        state.activateDefenceTarget("mageHand");

        assertEquals("Magic Defence", state.getActiveDefenceTracker().getDefenceLabel());
        assertEquals(50, state.getActiveDefenceTracker().getCurrentDefence());
        assertEquals(true, state.recordDefenceSpecHit("mageHand", "Eye of Ayak", 50));
        assertEquals(0, state.getActiveDefenceTracker().getCurrentDefence());
    }

    @Test
    public void tracksDefenceSeparatelyPerTargetInstance()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        state.configureDefenceTracker(100);
        state.startRoom("Shamans");

        assertEquals(true, state.recordDefenceSpecHit("shamans:1", "Ralos", 1));
        state.activateDefenceTarget("shamans:2");

        assertEquals(340, state.getDefenceTracker("shamans:1").getCurrentDefence());
        assertEquals(374, state.getDefenceTracker("shamans:2").getCurrentDefence());
        assertEquals("Shamans #2", state.getActiveDefenceTracker().getDisplayName());
    }

    @Test
    public void tracksOlmDefenceSeparatelyPerPhaseAndHand()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        state.configureDefenceTracker(100);
        state.startRoom("Olm");
        state.observeOlmHeadSpawned();

        assertEquals("mageHand:p1", state.olmDefenceTargetKey("mageHand"));
        assertEquals(true, state.recordDefenceSpecHit("mageHand:p1", "Eye of Ayak", 50));
        assertEquals(0, state.getDefenceTracker("mageHand:p1").getCurrentDefence());

        state.observeOlmHeadSpawned();
        assertEquals("mageHand:p2", state.olmDefenceTargetKey("mageHand"));
        state.activateDefenceTarget("mageHand:p2");

        assertEquals(50, state.getDefenceTracker("mageHand:p2").getCurrentDefence());
        assertEquals("Mage Hand P2", state.getActiveDefenceTracker().getDisplayName());
    }

    @Test
    public void tracksIceDemonDefenceProfile()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        state.configureDefenceTracker(100);
        state.startRoom("Ice Demon");

        assertEquals(true, state.recordDefenceSpecHit("iceDemon:7", "BGS", 50));
        assertEquals(110, state.getDefenceTracker("iceDemon:7").getCurrentDefence());
        assertEquals("Ice Demon #7", state.getActiveDefenceTracker().getDisplayName());
        assertEquals(true, state.getActiveDefenceTracker().getRemainingSpecs().isEmpty());
    }

    @Test
    public void tracksAdditionalPartyDefenceProfilesWithoutAddingPanelRows()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        state.configureDefenceTracker(100);

        state.startRoom("Tekton");
        state.activateDefenceTarget("tekton:1");
        assertEquals(364, state.getDefenceTracker("tekton:1").getCurrentDefence());
        assertEquals("Tekton #1", state.getActiveDefenceTracker().getDisplayName());

        state.startRoom("Vasa");
        assertEquals(null, state.getDefenceTracker("tekton:1"));
        assertEquals(true, state.recordDefenceSpecHit("vasa:1", "BGS", 1));
        assertEquals(310, state.getActiveDefenceTracker().getCurrentDefence());
        assertEquals("Vasa #1", state.getActiveDefenceTracker().getDisplayName());

        state.startRoom("Vespula");
        assertEquals(true, state.recordDefenceSpecHit("abyssalPortal:1", "BGS", 1));
        assertEquals(312, state.getActiveDefenceTracker().getCurrentDefence());
    }

    @Test
    public void resourceDisplayReflectsCurrentInventoryInsteadOfEveryPickup()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        ItemContainer inventory = mock(ItemContainer.class);

        when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.NOXIFER, 3)});
        assertEquals(true, state.observeInventory(inventory));
        assertEquals(3, state.getCollected(state.getResources().get(0)));

        when(inventory.getItems()).thenReturn(new Item[0]);
        assertEquals(true, state.observeInventory(inventory));
        assertEquals(0, state.getCollected(state.getResources().get(0)));

        when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.NOXIFER, 2), new Item(ItemID.GRIMY_NOXIFER, 1)});
        assertEquals(true, state.observeInventory(inventory));
        assertEquals(3, state.getCollected(state.getResources().get(0)));

        for (int i = 0; i < 10_000; i++)
        {
            assertEquals(false, state.observeInventory(inventory));
        }
    }

    @Test
    public void keepsPersonalPrepResourcesWhenTheyMoveToPrivateCoxStorage()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        ItemContainer inventory = mock(ItemContainer.class);
        ItemContainer privateStorage = mock(ItemContainer.class);

        when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.NOXIFER, 28)});
        assertEquals(true, state.observeInventory(inventory));
        when(privateStorage.getItems()).thenReturn(new Item[]{new Item(ItemID.NOXIFER, 28)});
        assertEquals(true, state.observePrivateStorage(privateStorage));
        when(inventory.getItems()).thenReturn(new Item[0]);
        assertEquals(true, state.observeInventory(inventory));

        assertEquals(28, state.getCollected(state.getResources().get(0)));
        assertEquals(28, state.getLocalPrepResources().get(0).count);
    }

    @Test
    public void recognizesTrackedItemsWhenDiscoveringAnUnknownSharedStorageContainer()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        ItemContainer storage = mock(ItemContainer.class);
        when(storage.getItems()).thenReturn(new Item[]{new Item(ItemID.NOXIFER, 28)});
        assertEquals(true, state.hasTrackedPrepItems(storage));

        when(storage.getItems()).thenReturn(new Item[]{new Item(ItemID.SHARK, 28)});
        assertEquals(false, state.hasTrackedPrepItems(storage));
    }

    @Test
    public void sharesPrepProgressAndSharedStorageSnapshotsWithTeam()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        java.util.List<CoxTeamUpdate.ItemCount> herbs = Arrays.asList(new CoxTeamUpdate.ItemCount("Noxifer", 28));
        java.util.List<CoxTeamUpdate.ItemCount> potions = Arrays.asList(new CoxTeamUpdate.ItemCount("Overloads", 4));
        java.util.List<CoxTeamUpdate.ItemCount> shared = Arrays.asList(
            new CoxTeamUpdate.ItemCount("Golpar", 84), new CoxTeamUpdate.ItemCount("Overloads", 12));

        assertEquals(true, state.updateTeamMember(42L, "Teammate", 0, null, Arrays.asList(), 0, 0, Arrays.asList(), 99, herbs, potions));
        CoxMegascaleState.TeamMember member = state.getTeamMembers().get(0);
        assertEquals(28, member.getPrepResourceCount("Noxifer"));
        assertEquals(4, member.getCraftedPotionCount("Overloads"));

        assertEquals(true, state.updateSharedStorage(shared, 1_000L));
        assertEquals(true, state.hasSharedStorageSnapshot());
        assertEquals(84, state.getSharedStorageCount("Golpar"));
        assertEquals(12, state.getSharedStorageCount("Overloads"));
    }

    @Test
    public void recordsAnEmptySharedStorageSnapshotWhenItsTabIsOpened()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        ItemContainer sharedStorage = mock(ItemContainer.class);
        when(sharedStorage.getItems()).thenReturn(new Item[0]);

        assertEquals(true, state.observeSharedStorage(sharedStorage));
        assertEquals(true, state.hasSharedStorageSnapshot());
        assertEquals(0, state.getSharedStorageCount("Noxifer"));
    }

    @Test
    public void recordsCraftedPotionOnlyAfterMakeAction()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        ItemContainer inventory = mock(ItemContainer.class);

        when(inventory.getItems()).thenReturn(new Item[0]);
        assertEquals(false, state.observeInventory(inventory));
        when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.REVITALISATION_4, 1)});
        assertEquals(false, state.observeInventory(inventory));
        assertEquals(0, state.getCraftedPotions("Revitalisation"));

        state.notePotionCraftAction();
        when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.REVITALISATION_4, 1), new Item(ItemID.REVITALISATION_4, 1)});
        assertEquals(true, state.observeInventory(inventory));
        assertEquals(1, state.getCraftedPotions("Revitalisation"));
    }

    @Test
    public void keepsPrepCreditAfterIngredientsBecomeConfirmedPotions()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        ItemContainer inventory = mock(ItemContainer.class);

        when(inventory.getItems()).thenReturn(new Item[]{
            new Item(ItemID.GOLPAR, 30), new Item(ItemID.STINKHORN_MUSHROOM, 30)
        });
        assertEquals(true, state.observeInventory(inventory));

        state.notePotionCraftAction();
        when(inventory.getItems()).thenReturn(new Item[]{
            new Item(ItemID.GOLPAR, 24), new Item(ItemID.STINKHORN_MUSHROOM, 24), new Item(ItemID.ELDER_4, 6)
        });
        assertEquals(true, state.observeInventory(inventory));

        assertEquals(6, state.getCraftedPotions("Elder"));
        assertEquals(30, state.getCollected(state.getResources().get(1)));
        assertEquals(30, state.getCollected(state.getResources().get(3)));
        assertEquals(30, state.getLocalPrepResources().stream().filter(count -> "Golpar".equals(count.name)).findFirst().get().count);
        assertEquals(30, state.getLocalPrepResources().stream().filter(count -> "Stinkhorn".equals(count.name)).findFirst().get().count);
    }

    @Test
    public void infersPotionCraftWhenCoxMakeWidgetOmitsMenuEvent()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        ItemContainer inventory = mock(ItemContainer.class);

        when(inventory.getItems()).thenReturn(new Item[]{
            new Item(ItemID.GOLPAR, 10), new Item(ItemID.STINKHORN_MUSHROOM, 10)
        });
        assertEquals(true, state.observeInventory(inventory));

        // Mixing through the CoX widget may not produce MenuOptionClicked.
        // The ingredient reduction plus potion increase is sufficient to
        // identify the craft without counting a bank withdrawal/pickup.
        when(inventory.getItems()).thenReturn(new Item[]{
            new Item(ItemID.GOLPAR, 9), new Item(ItemID.STINKHORN_MUSHROOM, 9), new Item(ItemID.ELDER_4, 1)
        });
        assertEquals(true, state.observeInventory(inventory));
        assertEquals(1, state.getCraftedPotions("Elder"));
    }

    @Test
    public void doesNotCountPotionBankWithdrawalOrPickupAsCrafting()
    {
        CoxMegascaleState state = new CoxMegascaleState();
        ItemContainer inventory = mock(ItemContainer.class);

        when(inventory.getItems()).thenReturn(new Item[0]);
        state.observeInventory(inventory);
        when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.ELDER_4, 1)});
        state.observeInventory(inventory);

        assertEquals(0, state.getCraftedPotions("Elder"));
    }

    @Test
    public void raidResetPreservesFixtureOnlyWhileStillInsideCox()
    {
        assertEquals(true, CoxMegascalePlugin.shouldPreservePointFixtureOnRaidReset(true));
        assertEquals(false, CoxMegascalePlugin.shouldPreservePointFixtureOnRaidReset(false));
    }

    @Test
    public void completedFixtureCannotReopenBeforeRaidExit()
    {
        assertEquals(false, CoxMegascalePlugin.canOpenPointFixture(true, false, true,
            true, true, 100, "ABCDEF"));
        assertEquals(true, CoxMegascalePlugin.canOpenPointFixture(true, false, false,
            true, true, 100, "ABCDEF"));
    }

    @Test
    public void scoutingAloneCannotOpenFixture()
    {
        assertEquals(false, CoxMegascalePlugin.canOpenPointFixture(true, false, false,
            false, true, 100, "ABCDEF"));
        assertEquals(false, CoxMegascalePlugin.canOpenPointFixture(true, false, false,
            true, false, 100, "ABCDEF"));
    }

    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(CoxMegascalePlugin.class);
        RuneLite.main(args);
    }
}
