package com.coxmegascale.party;

import java.util.ArrayList;
import java.util.List;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Versioned RuneLite Party snapshot for the Team tab and shared preparation
 * state. Receivers accept the payload only when its schema and CoX raid identity
 * match, then sanitize every remote list and numeric field before display.
 */
public class CoxTeamUpdate extends PartyMemberMessage
{
    public static final int SCHEMA_VERSION = 1;
    public int schemaVersion;
    public String displayName;
    public int personalPoints;
    public String activeRoom;
    public List<Death> deaths = new ArrayList<>();
    public int overloadSips;
    public int nonOverloadSips;
    public List<Fish> fishEaten = new ArrayList<>();
    public boolean inCoxRaid;
    public int raidPartyId = -1;
    public int thievingLevel;
    public List<ItemCount> prepResources = new ArrayList<>();
    public List<ItemCount> craftedPotions = new ArrayList<>();
    public List<ItemCount> sharedStorage = new ArrayList<>();
    public long sharedStorageObservedAtMillis;
    public boolean requestRosterSnapshot;

    public CoxTeamUpdate()
    {
    }

    public CoxTeamUpdate(String displayName, int personalPoints, String activeRoom, List<Death> deaths, int overloadSips, int nonOverloadSips,
        List<Fish> fishEaten, boolean inCoxRaid, int raidPartyId, int thievingLevel)
    {
        this.schemaVersion = SCHEMA_VERSION;
        this.displayName = displayName;
        this.personalPoints = personalPoints;
        this.activeRoom = activeRoom;
        this.deaths = deaths == null ? new ArrayList<>() : new ArrayList<>(deaths);
        this.overloadSips = Math.max(0, overloadSips);
        this.nonOverloadSips = Math.max(0, nonOverloadSips);
        this.fishEaten = fishEaten == null ? new ArrayList<>() : new ArrayList<>(fishEaten);
        this.inCoxRaid = inCoxRaid;
        this.raidPartyId = raidPartyId;
        this.thievingLevel = Math.max(0, thievingLevel);
    }

    public CoxTeamUpdate(String displayName, int personalPoints, String activeRoom, List<Death> deaths, int overloadSips, int nonOverloadSips,
        List<Fish> fishEaten, boolean inCoxRaid, int raidPartyId, int thievingLevel, List<ItemCount> prepResources,
        List<ItemCount> craftedPotions, List<ItemCount> sharedStorage, long sharedStorageObservedAtMillis, boolean requestRosterSnapshot)
    {
        this(displayName, personalPoints, activeRoom, deaths, overloadSips, nonOverloadSips, fishEaten, inCoxRaid, raidPartyId, thievingLevel);
        this.prepResources = prepResources == null ? new ArrayList<>() : new ArrayList<>(prepResources);
        this.craftedPotions = craftedPotions == null ? new ArrayList<>() : new ArrayList<>(craftedPotions);
        this.sharedStorage = sharedStorage == null ? new ArrayList<>() : new ArrayList<>(sharedStorage);
        this.sharedStorageObservedAtMillis = Math.max(0, sharedStorageObservedAtMillis);
        this.requestRosterSnapshot = requestRosterSnapshot;
    }

    /** One confirmed local raid death and its observed personal-point loss. */
    public static class Death
    {
        public String room;
        public int pointsLost;
        public long occurredAtMillis;

        public Death()
        {
        }

        public Death(String room, int pointsLost, long occurredAtMillis)
        {
            this.room = room;
            this.pointsLost = pointsLost;
            this.occurredAtMillis = occurredAtMillis;
        }
    }

    /** Aggregated consumed count and point value for one CoX fish tier. */
    public static class Fish
    {
        public String name;
        public int points;
        public int count;

        public Fish()
        {
        }

        public Fish(String name, int points, int count)
        {
            this.name = name;
            this.points = points;
            this.count = count;
        }
    }

    /** Named non-negative count used by bounded prep and storage snapshots. */
    public static class ItemCount
    {
        public String name;
        public int count;

        public ItemCount()
        {
        }

        public ItemCount(String name, int count)
        {
            this.name = name;
            this.count = Math.max(0, count);
        }
    }
}
