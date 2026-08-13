package com.coxmegascale;

import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import com.coxmegascale.party.CoxTeamUpdate;
import com.coxmegascale.calc.RaidMath;
import com.coxmegascale.calc.DefenceTracker;
import com.coxmegascale.calc.SpecPlanner;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.client.party.PartyMember;

/**
 * Mutable raid model shared by client-event handlers, infoboxes, and Swing.
 * It owns room/point attribution, preparation accounting, confirmed defence
 * progress, bounded Party snapshots, and the reset boundaries between raids.
 */
public class CoxMegascaleState
{
    public static final int MAX_POTION_TARGET = 999;
    public static final int MAX_CAVE_WORM_TARGET = 2_000;
    public static final int NOXIFER_ITEM_ID = ItemID.NOXIFER;
    public static final int GOLPAR_ITEM_ID = ItemID.GOLPAR;
    public static final int BUCHU_ITEM_ID = ItemID.BUCHU_LEAF;
    public static final int OVERLOAD_POINTS_PER_SIP = 600;
    private static final int MAX_REMOTE_DEATHS = 64;
    private static final int MAX_REMOTE_ITEM_COUNTS = 32;
    private static final int MAX_REMOTE_COUNT = 1_000_000;
    private static final int MAX_REMOTE_POINTS = 100_000_000;
    private static final int MAX_DEFENCE_TRACKERS = 256;
    private static final long MAX_SHARED_STORAGE_FUTURE_MILLIS = 60_000;

    // Central item catalogs define every inventory/storage value the plugin may
    // count or share; unrelated items are ignored.
    private static final List<Resource> RESOURCES = Collections.unmodifiableList(Arrays.asList(
        new Resource("Noxifer", ItemID.NOXIFER, ItemID.GRIMY_NOXIFER),
        new Resource("Golpar", ItemID.GOLPAR, ItemID.GRIMY_GOLPAR),
        new Resource("Buchu", ItemID.BUCHU_LEAF, ItemID.GRIMY_BUCHU_LEAF),
        new Resource("Stinkhorn", ItemID.STINKHORN_MUSHROOM, ItemID.STINKHORN_MUSHROOM),
        new Resource("Endarkened", ItemID.ENDARKENED_JUICE, ItemID.ENDARKENED_JUICE),
        new Resource("Cicely", ItemID.CICELY, ItemID.CICELY),
        new Resource("Cave worms", ItemID.CAVE_WORMS, ItemID.CAVE_WORMS)
    ));
    private static final List<FishType> COX_FISH = Collections.unmodifiableList(Arrays.asList(
        new FishType(ItemID.PYSK_FISH_0, "Pysk", 30),
        new FishType(ItemID.SUPHI_FISH_1, "Suphi", 40),
        new FishType(ItemID.LECKISH_FISH_2, "Leckish", 50),
        new FishType(ItemID.BRAWK_FISH_3, "Brawk", 60),
        new FishType(ItemID.MYCIL_FISH_4, "Mycil", 70),
        new FishType(ItemID.ROQED_FISH_5, "Roqed", 80),
        new FishType(ItemID.KYREN_FISH_6, "Kyren", 90)
    ));
    private static final List<Potion> COX_POTIONS = Collections.unmodifiableList(Arrays.asList(
        new Potion("Revitalisation", ItemID.REVITALISATION_1, ItemID.REVITALISATION_2, ItemID.REVITALISATION_3, ItemID.REVITALISATION_4,
            ItemID.REVITALISATION_POTION_1, ItemID.REVITALISATION_POTION_2, ItemID.REVITALISATION_POTION_3, ItemID.REVITALISATION_POTION_4,
            ItemID.REVITALISATION_1_20957, ItemID.REVITALISATION_2_20958, ItemID.REVITALISATION_3_20959, ItemID.REVITALISATION_4_20960),
        new Potion("Prayer Enhance", ItemID.PRAYER_ENHANCE_1, ItemID.PRAYER_ENHANCE_2, ItemID.PRAYER_ENHANCE_3, ItemID.PRAYER_ENHANCE_4,
            ItemID.PRAYER_ENHANCE_1_20965, ItemID.PRAYER_ENHANCE_2_20966, ItemID.PRAYER_ENHANCE_3_20967, ItemID.PRAYER_ENHANCE_4_20968,
            ItemID.PRAYER_ENHANCE_1_20969, ItemID.PRAYER_ENHANCE_2_20970, ItemID.PRAYER_ENHANCE_3_20971, ItemID.PRAYER_ENHANCE_4_20972),
        new Potion("Xeric's Aid", ItemID.XERICS_AID_1, ItemID.XERICS_AID_2, ItemID.XERICS_AID_3, ItemID.XERICS_AID_4,
            ItemID.XERICS_AID_1_20977, ItemID.XERICS_AID_2_20978, ItemID.XERICS_AID_3_20979, ItemID.XERICS_AID_4_20980,
            ItemID.XERICS_AID_1_20981, ItemID.XERICS_AID_2_20982, ItemID.XERICS_AID_3_20983, ItemID.XERICS_AID_4_20984),
        new Potion("Overloads", ItemID.OVERLOAD_1_20985, ItemID.OVERLOAD_2_20986, ItemID.OVERLOAD_3_20987, ItemID.OVERLOAD_4_20988,
            ItemID.OVERLOAD_1_20989, ItemID.OVERLOAD_2_20990, ItemID.OVERLOAD_3_20991, ItemID.OVERLOAD_4_20992,
            ItemID.OVERLOAD_1_20993, ItemID.OVERLOAD_2_20994, ItemID.OVERLOAD_3_20995, ItemID.OVERLOAD_4_20996,
            ItemID.OVERLOAD_1, ItemID.OVERLOAD_2, ItemID.OVERLOAD_3, ItemID.OVERLOAD_4),
        new Potion("Kodai", ItemID.KODAI_1, ItemID.KODAI_2, ItemID.KODAI_3, ItemID.KODAI_4,
            ItemID.KODAI_POTION_1, ItemID.KODAI_POTION_2, ItemID.KODAI_POTION_3, ItemID.KODAI_POTION_4,
            ItemID.KODAI_1_20945, ItemID.KODAI_2_20946, ItemID.KODAI_3_20947, ItemID.KODAI_4_20948),
        new Potion("Twisted", ItemID.TWISTED_1, ItemID.TWISTED_2, ItemID.TWISTED_3, ItemID.TWISTED_4,
            ItemID.TWISTED_POTION_1, ItemID.TWISTED_POTION_2, ItemID.TWISTED_POTION_3, ItemID.TWISTED_POTION_4,
            ItemID.TWISTED_1_20933, ItemID.TWISTED_2_20934, ItemID.TWISTED_3_20935, ItemID.TWISTED_4_20936),
        new Potion("Elder", ItemID.ELDER_1, ItemID.ELDER_2, ItemID.ELDER_3, ItemID.ELDER_4,
            ItemID.ELDER_POTION_1, ItemID.ELDER_POTION_2, ItemID.ELDER_POTION_3, ItemID.ELDER_POTION_4,
            ItemID.ELDER_1_20921, ItemID.ELDER_2_20922, ItemID.ELDER_3_20923, ItemID.ELDER_4_20924)
    ));
    private static final Map<Integer, Integer> TRACKED_INVENTORY_INDEX = buildTrackedInventoryIndex();

    // Local preparation is split by provenance before being combined into the
    // displayed collected totals.
    private final Map<String, Integer> collectedCounts = new LinkedHashMap<>();
    private final Map<String, Integer> inventoryResourceCounts = new LinkedHashMap<>();
    private final Map<String, Integer> privateStorageResourceCounts = new LinkedHashMap<>();
    private final Map<String, Integer> sharedStorageCounts = new LinkedHashMap<>();
    private final Map<String, Integer> lastPotionCounts = new LinkedHashMap<>();
    private final Map<String, Integer> craftedPotionCounts = new LinkedHashMap<>();
    // Live raid histories and compatible Party state are bounded/sanitized at
    // their update methods below.
    private final List<TrackedRoomPoints> trackedRoomPoints = new java.util.ArrayList<>();
    private final List<DeathPointLoss> deathPointLosses = new java.util.ArrayList<>();
    private final List<TeamDeath> localTeamDeaths = new ArrayList<>();
    private final Map<Long, TeamMember> teamMembers = new LinkedHashMap<>();
    private final Set<Long> syncedPartyMemberIds = new HashSet<>();
    private final Map<String, Integer> localFishEaten = new LinkedHashMap<>();
    // Legacy Mystics counters coexist with the per-target defence model used by
    // menu entries and Party-shared confirmed hits.
    private final Map<String, Integer> mysticsSpecTargets = new LinkedHashMap<>();
    private final Map<String, Integer> mysticsSpecRemaining = new LinkedHashMap<>();
    private final Map<String, DefenceTracker> defenceTrackers = new LinkedHashMap<>();
    private final RaidMath raidMath = new RaidMath();
    private final SpecPlanner specPlanner = new SpecPlanner(raidMath);

    private int olmKillers = 3;
    private int sipsToCap = 0;
    private int revitalisationTarget = 12;
    private int xericsAidTarget = 16;
    private int prayerEnhanceOverride = 0;
    private boolean prayerEnhanceOverrideActive;
    private int caveWormTarget = 0;
    private int currentPersonalPoints;
    private int currentGroupPoints;
    private int previousPersonalPoints;
    private int previousGroupPoints;
    private String activeRoomName;
    private int activeRoomStartPersonalPoints;
    private int activeRoomStartGroupPoints;
    private int activeRoomStartPersonalConsumablePoints;
    private int activeRoomStartGroupConsumablePoints;
    private long activeRoomStartedAtMillis;
    private int localOverloadSips;
    private int localNonOverloadSips;
    private boolean olmStarted;
    private int olmPhase;
    private int mysticsTrackerScale = -1;
    private boolean mysticsUseEmberlight;
    private boolean mysticsChickenRoute;
    private int defenceTrackerScale = -1;
    private String activeDefenceTarget;
    private long potionCraftWindowUntil;
    private boolean prepInfoBoxesHidden;
    private String layoutCode = "Unknown";
    private final List<ScoutedRoom> scoutedRooms = new java.util.ArrayList<>();
    private PendingLocalDeath pendingLocalDeath;
    private RecentGroupLoss recentGroupLoss;
    private long sharedStorageObservedAtMillis;
    private long sharedStorageSourceMemberId = Long.MAX_VALUE;
    private volatile long prepRevision;

    public CoxMegascaleState()
    {
        for (Resource resource : RESOURCES)
        {
            collectedCounts.put(resource.key, 0);
            inventoryResourceCounts.put(resource.key, 0);
            privateStorageResourceCounts.put(resource.key, 0);
            sharedStorageCounts.put(resource.key, 0);
        }
        for (FishType fish : COX_FISH)
        {
            localFishEaten.put(fish.name, 0);
        }
        for (Potion potion : COX_POTIONS)
        {
            lastPotionCounts.put(potion.key, 0);
            craftedPotionCounts.put(potion.key, 0);
            sharedStorageCounts.put(potion.key, 0);
        }
    }

    public List<Resource> getResources()
    {
        return RESOURCES;
    }

    public int getOlmKillers()
    {
        return olmKillers;
    }

    public void setOlmKillers(int olmKillers)
    {
        int safeOlmKillers = Math.max(1, olmKillers);
        if (this.olmKillers != safeOlmKillers)
        {
            this.olmKillers = safeOlmKillers;
            markPrepChanged();
        }
    }

    public void setSipsToCap(int sipsToCap)
    {
        int safeSipsToCap = Math.max(0, sipsToCap);
        if (this.sipsToCap != safeSipsToCap)
        {
            this.sipsToCap = safeSipsToCap;
            markPrepChanged();
        }
    }

    public int getSipsToCap()
    {
        return sipsToCap;
    }

    public int getSipsPerKiller()
    {
        return olmKillers <= 0 ? 0 : (int) Math.ceil(sipsToCap / (double) olmKillers);
    }

    public int getOverloadsPerKiller()
    {
        return Math.min(MAX_POTION_TARGET, (int) Math.ceil(getSipsPerKiller() / 4.0));
    }

    public int getTotalOverloadsNeeded()
    {
        return getOverloadsPerKiller() * olmKillers;
    }

    public int getBuchuTarget()
    {
        return revitalisationTarget + xericsAidTarget + getPrayerEnhanceTarget();
    }

    public int getTarget(Resource resource)
    {
        // Secondary targets include ingredients consumed by planned overloads;
        // herb targets reflect finished potion requirements.
        if ("Noxifer".equals(resource.key))
        {
            return getOverloadsPerKiller();
        }
        if ("Golpar".equals(resource.key))
        {
            return getOverloadsPerKiller() * 3;
        }
        if ("Buchu".equals(resource.key))
        {
            return getBuchuTarget();
        }
        if ("Stinkhorn".equals(resource.key))
        {
            return revitalisationTarget + getOverloadsPerKiller();
        }
        if ("Endarkened".equals(resource.key))
        {
            return xericsAidTarget + getOverloadsPerKiller();
        }
        if ("Cicely".equals(resource.key))
        {
            return getPrayerEnhanceTarget() + getOverloadsPerKiller();
        }
        if ("Cave worms".equals(resource.key))
        {
            return caveWormTarget;
        }
        return 0;
    }

    public int getRevitalisationTarget()
    {
        return revitalisationTarget;
    }

    public void setRevitalisationTarget(int revitalisationTarget)
    {
        int safeTarget = Math.min(MAX_POTION_TARGET, Math.max(0, revitalisationTarget));
        if (this.revitalisationTarget != safeTarget)
        {
            this.revitalisationTarget = safeTarget;
            markPrepChanged();
        }
    }

    public int getXericsAidTarget()
    {
        return xericsAidTarget;
    }

    public void setXericsAidTarget(int xericsAidTarget)
    {
        int safeTarget = Math.min(MAX_POTION_TARGET, Math.max(0, xericsAidTarget));
        if (this.xericsAidTarget != safeTarget)
        {
            this.xericsAidTarget = safeTarget;
            markPrepChanged();
        }
    }

    public int getPrayerEnhanceTarget()
    {
        if (prayerEnhanceOverrideActive)
        {
            return prayerEnhanceOverride;
        }
        if (sipsToCap >= 5 * 41)
        {
            return 8;
        }
        if (sipsToCap >= 5 * 25)
        {
            return 6;
        }
        if (sipsToCap >= 5 * 16)
        {
            return 4;
        }
        return 4;
    }

    public int getPrayerEnhanceOverride()
    {
        return prayerEnhanceOverride;
    }

    public boolean isPrayerEnhanceOverrideActive()
    {
        return prayerEnhanceOverrideActive;
    }

    public void setPrayerEnhanceOverride(int prayerEnhanceOverride)
    {
        setPrayerEnhanceOverride(prayerEnhanceOverride, true);
    }

    public void setPrayerEnhanceOverride(int prayerEnhanceOverride, boolean active)
    {
        int safeOverride = Math.min(MAX_POTION_TARGET, Math.max(0, prayerEnhanceOverride));
        if (this.prayerEnhanceOverride != safeOverride || this.prayerEnhanceOverrideActive != active)
        {
            this.prayerEnhanceOverride = safeOverride;
            this.prayerEnhanceOverrideActive = active;
            markPrepChanged();
        }
    }

    public int getCaveWormTarget()
    {
        return caveWormTarget;
    }

    public void setCaveWormTarget(int caveWormTarget)
    {
        int safeTarget = Math.min(MAX_CAVE_WORM_TARGET, Math.max(0, caveWormTarget));
        if (this.caveWormTarget != safeTarget)
        {
            this.caveWormTarget = safeTarget;
            markPrepChanged();
        }
    }

    public int getCurrentPersonalPoints()
    {
        return currentPersonalPoints;
    }

    public int getCurrentGroupPoints()
    {
        return currentGroupPoints;
    }

    public boolean setCurrentPoints(int personalPoints, int groupPoints)
    {
        // ActorDeath and point varbits may arrive in either order. Store both
        // observations and correlate a nearby personal loss exactly once.
        boolean localDeathRecorded = false;
        this.currentPersonalPoints = Math.max(0, personalPoints);
        this.currentGroupPoints = Math.max(0, groupPoints);
        if (previousGroupPoints > 0 && this.currentGroupPoints > 0 && this.currentGroupPoints < previousGroupPoints)
        {
            int pointsLost = previousGroupPoints - this.currentGroupPoints;
            String room = activeRoomName == null ? "Unknown" : activeRoomName;
            deathPointLosses.add(new DeathPointLoss(room, pointsLost));
        }
        if (previousPersonalPoints > 0 && this.currentPersonalPoints < previousPersonalPoints)
        {
            int personalPointsLost = previousPersonalPoints - this.currentPersonalPoints;
            String room = activeRoomName == null ? "Unknown" : activeRoomName;
            recentGroupLoss = new RecentGroupLoss(room, personalPointsLost, System.currentTimeMillis());
            localDeathRecorded = recordPendingLocalDeath();
        }
        previousPersonalPoints = this.currentPersonalPoints;
        previousGroupPoints = this.currentGroupPoints;
        return localDeathRecorded;
    }

    public boolean noteLocalDeath()
    {
        pendingLocalDeath = new PendingLocalDeath(activeRoomName == null ? "Unknown" : activeRoomName, System.currentTimeMillis());
        return recordPendingLocalDeath();
    }

    public void resetPointTracking()
    {
        // Clear raid-local histories while retaining user preparation targets
        // and other session options.
        trackedRoomPoints.clear();
        deathPointLosses.clear();
        localTeamDeaths.clear();
        localOverloadSips = 0;
        localNonOverloadSips = 0;
        olmStarted = false;
        olmPhase = 0;
        for (FishType fish : COX_FISH)
        {
            localFishEaten.put(fish.name, 0);
        }
        activeRoomName = null;
        activeRoomStartPersonalPoints = currentPersonalPoints;
        activeRoomStartGroupPoints = currentGroupPoints;
        previousPersonalPoints = currentPersonalPoints;
        previousGroupPoints = currentGroupPoints;
        pendingLocalDeath = null;
        recentGroupLoss = null;
        activeRoomStartPersonalConsumablePoints = 0;
        activeRoomStartGroupConsumablePoints = 0;
        activeRoomStartedAtMillis = 0;
        potionCraftWindowUntil = 0;
        for (Potion potion : COX_POTIONS)
        {
            craftedPotionCounts.put(potion.key, 0);
        }
        resetMysticsSpecTracker();
        defenceTrackers.clear();
        activeDefenceTarget = null;
        markPrepChanged();
    }

    public void startRoom(String roomName)
    {
        // Closing first snapshots the prior room's final delta before new points
        // can be attributed to the incoming room.
        if (roomName == null || roomName.isEmpty() || roomName.equals(activeRoomName))
        {
            return;
        }
        closeActiveRoom();
        activeRoomName = roomName;
        defenceTrackers.clear();
        activeDefenceTarget = null;
        if ("Mystics".equals(roomName))
        {
            resetMysticsSpecTracker();
        }
        if ("Olm".equals(roomName))
        {
            olmStarted = true;
            olmPhase = 0;
        }
        activeRoomStartPersonalPoints = currentPersonalPoints;
        activeRoomStartGroupPoints = currentGroupPoints;
        activeRoomStartPersonalConsumablePoints = getLocalConsumablePoints();
        activeRoomStartGroupConsumablePoints = getTeamConsumablePoints();
        activeRoomStartedAtMillis = System.currentTimeMillis();
    }

    public void closeActiveRoom()
    {
        if (activeRoomName == null)
        {
            return;
        }
        // Remove separately tracked consumable points so eating/drinking during
        // combat does not inflate that room's contribution.
        int personal = Math.max(0, currentPersonalPoints - activeRoomStartPersonalPoints
            - (getLocalConsumablePoints() - activeRoomStartPersonalConsumablePoints));
        int group = Math.max(0, currentGroupPoints - activeRoomStartGroupPoints
            - (getTeamConsumablePoints() - activeRoomStartGroupConsumablePoints));
        long durationMillis = activeRoomStartedAtMillis <= 0
            ? 0 : Math.max(0, System.currentTimeMillis() - activeRoomStartedAtMillis);
        trackedRoomPoints.add(new TrackedRoomPoints(activeRoomName, personal, group, durationMillis));
        activeRoomName = null;
        activeRoomStartedAtMillis = 0;
    }

    public String getActiveRoomName()
    {
        return activeRoomName;
    }

    public boolean isActiveRoom(String roomName)
    {
        return roomName != null && roomName.equals(activeRoomName);
    }

    public int getActiveRoomPersonalPoints()
    {
        return activeRoomName == null ? 0 : Math.max(0, currentPersonalPoints - activeRoomStartPersonalPoints
            - (getLocalConsumablePoints() - activeRoomStartPersonalConsumablePoints));
    }

    public int getActiveRoomGroupPoints()
    {
        return activeRoomName == null ? 0 : Math.max(0, currentGroupPoints - activeRoomStartGroupPoints
            - (getTeamConsumablePoints() - activeRoomStartGroupConsumablePoints));
    }

    public List<TrackedRoomPoints> getTrackedRoomPoints()
    {
        return Collections.unmodifiableList(new ArrayList<>(trackedRoomPoints));
    }

    public List<DeathPointLoss> getDeathPointLosses()
    {
        return Collections.unmodifiableList(new ArrayList<>(deathPointLosses));
    }

    public int getTotalDeathPointLoss()
    {
        long total = 0;
        for (DeathPointLoss loss : deathPointLosses)
        {
            total += loss.pointsLost;
        }
        return saturatedInt(total);
    }

    public int getTrackedGroupPoints(String roomName)
    {
        int total = 0;
        for (TrackedRoomPoints room : trackedRoomPoints)
        {
            if (room.room.equals(roomName))
            {
                total += room.groupPoints;
            }
        }
        if (roomName.equals(activeRoomName))
        {
            total += getActiveRoomGroupPoints();
        }
        return total;
    }

    public boolean hasTrackedRoom(String roomName)
    {
        for (TrackedRoomPoints room : trackedRoomPoints)
        {
            if (room.room.equals(roomName))
            {
                return true;
            }
        }
        return roomName.equals(activeRoomName);
    }

    public boolean recordOverloadSip()
    {
        localOverloadSips++;
        return true;
    }

    public void configureMysticsSpecTracker(int scale, boolean useEmberlight, boolean chickenRoute)
    {
        // Preserve confirmed progress across ordinary UI refreshes; recompute
        // only when an input that changes the route changes.
        if (mysticsTrackerScale == scale && mysticsUseEmberlight == useEmberlight && mysticsChickenRoute == chickenRoute)
        {
            return;
        }
        mysticsTrackerScale = scale;
        mysticsUseEmberlight = useEmberlight;
        mysticsChickenRoute = chickenRoute;
        resetMysticsSpecTracker();
    }

    public void configureDefenceTracker(int scale)
    {
        if (defenceTrackerScale != scale)
        {
            defenceTrackerScale = scale;
            defenceTrackers.clear();
            activeDefenceTarget = null;
        }
    }

    public void activateDefenceTarget(String targetKey)
    {
        // Create trackers lazily and cap their count so long raids or hostile
        // target keys cannot grow state without bound.
        if (defenceTrackerScale <= 0 || !isValidDefenceTargetKey(targetKey))
        {
            return;
        }
        if (!defenceTrackers.containsKey(targetKey) && defenceTrackers.size() < MAX_DEFENCE_TRACKERS)
        {
            String profileKey = defenceProfileKey(targetKey);
            DefenceTracker tracker = DefenceTracker.forScale(targetKey, profileKey, defenceDisplayName(targetKey),
                defenceTrackerScale, specPlanner);
            if (tracker == null)
            {
                return;
            }
            defenceTrackers.put(targetKey, tracker);
        }
        activeDefenceTarget = targetKey;
    }

    static boolean isValidDefenceTargetKey(String targetKey)
    {
        if (targetKey == null || targetKey.length() > 32)
        {
            return false;
        }
        int separator = targetKey.indexOf(':');
        if (separator == -1)
        {
            return "mystics".equals(targetKey) || "shamans".equals(targetKey) || "iceDemon".equals(targetKey)
                || "ropeMager".equals(targetKey) || "ropeRanger".equals(targetKey) || "olmHead".equals(targetKey)
                || "meleeHand".equals(targetKey) || "mageHand".equals(targetKey)
                || "abyssalPortal".equals(targetKey) || "tekton".equals(targetKey) || "vasa".equals(targetKey);
        }
        if (separator == 0 || separator == targetKey.length() - 1)
        {
            return false;
        }
        String profile = targetKey.substring(0, separator);
        String suffix = targetKey.substring(separator + 1);
        boolean olm = "olmHead".equals(profile) || "meleeHand".equals(profile) || "mageHand".equals(profile);
        if (olm)
        {
            if (suffix.length() < 2 || suffix.charAt(0) != 'p')
            {
                return false;
            }
            suffix = suffix.substring(1);
        }
        else if (!("mystics".equals(profile) || "shamans".equals(profile) || "iceDemon".equals(profile)
            || "ropeMager".equals(profile) || "ropeRanger".equals(profile)
            || "abyssalPortal".equals(profile) || "tekton".equals(profile) || "vasa".equals(profile)))
        {
            return false;
        }
        int value = 0;
        for (int i = 0; i < suffix.length(); i++)
        {
            char character = suffix.charAt(i);
            if (!Character.isDigit(character))
            {
                return false;
            }
            value = value * 10 + character - '0';
            if (value > 65_535)
            {
                return false;
            }
        }
        return !olm || value <= 9;
    }

    public DefenceTracker getActiveDefenceTracker()
    {
        return activeDefenceTarget == null ? null : defenceTrackers.get(activeDefenceTarget);
    }

    public DefenceTracker getDefenceTracker(String targetKey)
    {
        return defenceTrackers.get(targetKey);
    }

    public boolean recordDefenceSpecHit(String targetKey, String weapon, int amount)
    {
        activateDefenceTarget(targetKey);
        DefenceTracker tracker = getActiveDefenceTracker();
        return tracker != null && tracker.recordSpecHit(weapon, amount);
    }

    public static String defenceProfileKey(String targetKey)
    {
        int separator = targetKey.indexOf(':');
        return separator == -1 ? targetKey : targetKey.substring(0, separator);
    }

    private static String defenceDisplayName(String targetKey)
    {
        String profileKey = defenceProfileKey(targetKey);
        String displayName = DefenceTracker.baseDisplayName(profileKey);
        int separator = targetKey.indexOf(':');
        if (separator == -1 || separator == targetKey.length() - 1)
        {
            return displayName;
        }
        String suffix = targetKey.substring(separator + 1);
        if (suffix.startsWith("p"))
        {
            return displayName + " P" + suffix.substring(1);
        }
        return displayName + " #" + suffix;
    }

    public void observeOlmHeadSpawned()
    {
        // Phase identity makes new Olm keys distinct from NPC indices reused
        // after a wipe or transition.
        if ("Olm".equals(activeRoomName))
        {
            olmPhase++;
        }
    }

    public int getOlmPhase()
    {
        return Math.max(1, olmPhase);
    }

    public String olmDefenceTargetKey(String profileKey)
    {
        return profileKey + ":p" + getOlmPhase();
    }

    public boolean isMysticsUseEmberlight()
    {
        return mysticsUseEmberlight;
    }

    public boolean isMysticsChickenRoute()
    {
        return mysticsChickenRoute;
    }

    public boolean isMysticsTrackerActive()
    {
        return "Mystics".equals(activeRoomName);
    }

    public boolean isMysticsSpecTracked(String weapon)
    {
        return mysticsSpecTargets.containsKey(weapon);
    }

    public int getMysticsSpecRemaining(String weapon)
    {
        return mysticsSpecRemaining.getOrDefault(weapon, 0);
    }

    public boolean recordMysticsSpecHit(String weapon, int amount)
    {
        if (!isMysticsTrackerActive() || amount <= 0 || !mysticsSpecRemaining.containsKey(weapon))
        {
            return false;
        }
        int remaining = mysticsSpecRemaining.get(weapon);
        int updated = Math.max(0, remaining - amount);
        if (updated == remaining)
        {
            return false;
        }
        mysticsSpecRemaining.put(weapon, updated);
        return true;
    }

    private void resetMysticsSpecTracker()
    {
        mysticsSpecTargets.clear();
        mysticsSpecRemaining.clear();
        if (mysticsTrackerScale <= 0)
        {
            return;
        }

        SpecPlanner.SpecSummary plan = null;
        for (SpecPlanner.SpecSummary summary : specPlanner.buildCoxSpecPlan(mysticsTrackerScale, Collections.emptyList()))
        {
            if ("mystics".equals(summary.roomKey))
            {
                plan = summary;
                break;
            }
        }
        if (plan == null)
        {
            return;
        }

        Map<String, Integer> planSpecs = mysticsChickenRoute ? plan.allStatSpecs : plan.specs;
        for (Map.Entry<String, Integer> entry : planSpecs.entrySet())
        {
            mysticsSpecTargets.put(entry.getKey(), entry.getValue());
        }
        if (mysticsChickenRoute && mysticsUseEmberlight)
        {
            mysticsSpecTargets.put("Emberlight", 16);
            mysticsSpecTargets.put("BGS", plan.bgsDamage);
        }
        else if (mysticsChickenRoute)
        {
            mysticsSpecTargets.put("BGS", plan.bgsOnlyDamage);
        }
        mysticsSpecRemaining.putAll(mysticsSpecTargets);
    }

    public boolean recordNonOverloadSip(int itemId)
    {
        if (!isNonOverloadPotion(itemId))
        {
            return false;
        }
        localNonOverloadSips++;
        return true;
    }

    public boolean recordFishEaten(int itemId)
    {
        FishType fish = findFish(itemId);
        if (fish == null)
        {
            return false;
        }
        localFishEaten.put(fish.name, localFishEaten.getOrDefault(fish.name, 0) + 1);
        return true;
    }

    public int getLocalOverloadSips()
    {
        return localOverloadSips;
    }

    public int getLocalOverloadPoints()
    {
        return localOverloadSips * OVERLOAD_POINTS_PER_SIP;
    }

    public int getLocalNonOverloadSips()
    {
        return localNonOverloadSips;
    }

    public int getTeamNonOverloadSips()
    {
        long reported = 0;
        for (TeamMember member : teamMembers.values())
        {
            if (member.hasCoxData)
            {
                reported += member.nonOverloadSips;
            }
        }
        return reported == 0 ? localNonOverloadSips : saturatedInt(reported);
    }

    public int getTeamNonOverloadPointsLost()
    {
        return getTeamNonOverloadSips() * OVERLOAD_POINTS_PER_SIP;
    }

    public int getEffectiveOverloadSipCap()
    {
        return Math.max(0, sipsToCap - getTeamNonOverloadSips());
    }

    public int getLocalFishCount()
    {
        int total = 0;
        for (int count : localFishEaten.values())
        {
            total += count;
        }
        return total;
    }

    public int getLocalFishPoints()
    {
        int points = 0;
        for (FishType fish : COX_FISH)
        {
            points += localFishEaten.getOrDefault(fish.name, 0) * fish.points;
        }
        return points;
    }

    public List<CoxTeamUpdate.Fish> getLocalFishEaten()
    {
        List<CoxTeamUpdate.Fish> eaten = new ArrayList<>();
        for (FishType fish : COX_FISH)
        {
            int count = localFishEaten.getOrDefault(fish.name, 0);
            if (count > 0)
            {
                eaten.add(new CoxTeamUpdate.Fish(fish.name, fish.points, count));
            }
        }
        return eaten;
    }

    public int getTeamOverloadSips()
    {
        long reported = 0;
        for (TeamMember member : teamMembers.values())
        {
            if (member.hasCoxData)
            {
                reported += member.overloadSips;
            }
        }
        return reported == 0 ? localOverloadSips : saturatedInt(reported);
    }

    public int getTeamOverloadPoints()
    {
        return saturatedInt((long) getTeamOverloadSips() * OVERLOAD_POINTS_PER_SIP);
    }

    public int getTeamFishCount()
    {
        long reported = 0;
        for (TeamMember member : teamMembers.values())
        {
            if (member.hasCoxData)
            {
                reported += member.getFishCount();
            }
        }
        return reported == 0 ? getLocalFishCount() : saturatedInt(reported);
    }

    public int getTeamFishPoints()
    {
        long reported = 0;
        for (TeamMember member : teamMembers.values())
        {
            if (member.hasCoxData)
            {
                reported += member.getFishPoints();
            }
        }
        return reported == 0 ? getLocalFishPoints() : saturatedInt(reported);
    }

    private int getLocalConsumablePoints()
    {
        return getLocalOverloadPoints() + getLocalFishPoints();
    }

    private int getTeamConsumablePoints()
    {
        return getTeamOverloadPoints() + getTeamFishPoints();
    }

    private static FishType findFish(int itemId)
    {
        for (FishType fish : COX_FISH)
        {
            if (fish.itemId == itemId)
            {
                return fish;
            }
        }
        return null;
    }

    private static boolean isNonOverloadPotion(int itemId)
    {
        Integer trackedIndex = TRACKED_INVENTORY_INDEX.get(itemId);
        if (trackedIndex == null || trackedIndex < RESOURCES.size())
        {
            return false;
        }
        int potionIndex = trackedIndex - RESOURCES.size();
        String key = COX_POTIONS.get(potionIndex).key;
        return !"Overloads".equals(key) && !"Kodai".equals(key) && !"Twisted".equals(key) && !"Elder".equals(key);
    }

    public List<TeamDeath> getLocalTeamDeaths()
    {
        return Collections.unmodifiableList(new ArrayList<>(localTeamDeaths));
    }

    public boolean syncTeamMembers(List<PartyMember> partyMembers)
    {
        // Reconcile roster identity without inventing CoX data for clients that
        // have not sent a compatible plugin snapshot.
        boolean changed = false;
        syncedPartyMemberIds.clear();
        Map<Long, TeamMember> orderedMembers = new LinkedHashMap<>();
        for (PartyMember partyMember : partyMembers)
        {
            syncedPartyMemberIds.add(partyMember.getMemberId());
            TeamMember member = teamMembers.computeIfAbsent(partyMember.getMemberId(), TeamMember::new);
            orderedMembers.put(partyMember.getMemberId(), member);
        }
        if (teamMembers.keySet().removeIf(memberId -> !syncedPartyMemberIds.contains(memberId)))
        {
            changed = true;
        }
        if (!new ArrayList<>(teamMembers.keySet()).equals(new ArrayList<>(orderedMembers.keySet())))
        {
            teamMembers.clear();
            teamMembers.putAll(orderedMembers);
            changed = true;
        }
        return changed;
    }

    static boolean isFishConsumable(int itemId)
    {
        return findFish(itemId) != null;
    }

    static boolean isOverloadConsumable(int itemId)
    {
        Potion potion = findPotion(itemId);
        return potion != null && "Overloads".equals(potion.key);
    }

    static boolean isNonOverloadConsumable(int itemId)
    {
        return isNonOverloadPotion(itemId);
    }

    static int consumableUnits(ItemContainer inventory, int itemId)
    {
        if (inventory == null || inventory.getItems() == null)
        {
            return 0;
        }
        FishType selectedFish = findFish(itemId);
        Potion selectedPotion = findPotion(itemId);
        if (selectedFish == null && selectedPotion == null)
        {
            return 0;
        }
        int units = 0;
        for (Item item : inventory.getItems())
        {
            if (item == null || item.getQuantity() <= 0)
            {
                continue;
            }
            if (selectedFish != null && item.getId() == selectedFish.itemId)
            {
                units += item.getQuantity();
            }
            else if (selectedPotion != null)
            {
                int dose = selectedPotion.doseForItem(item.getId());
                if (dose > 0)
                {
                    units += item.getQuantity() * dose;
                }
            }
        }
        return Math.max(0, units);
    }

    private static Potion findPotion(int itemId)
    {
        for (Potion potion : COX_POTIONS)
        {
            if (potion.doseForItem(itemId) > 0)
            {
                return potion;
            }
        }
        return null;
    }

    public boolean hasTeamMembers()
    {
        return !teamMembers.isEmpty();
    }

    public boolean clearTeamMemberCoxData(long memberId, String displayName)
    {
        TeamMember member = teamMembers.computeIfAbsent(memberId, TeamMember::new);
        boolean changed = false;
        if (!"Unknown".equals(member.displayName))
        {
            member.displayName = "Unknown";
            changed = true;
        }
        if (member.hasCoxData || member.personalPoints != 0 || member.activeRoom != null
            || member.overloadSips != 0 || member.nonOverloadSips != 0 || member.hasThievingLevel
            || !member.fishEaten.isEmpty() || !member.deaths.isEmpty() || !member.prepResources.isEmpty()
            || !member.craftedPotions.isEmpty())
        {
            member.hasCoxData = false;
            member.personalPoints = 0;
            member.activeRoom = null;
            member.overloadSips = 0;
            member.nonOverloadSips = 0;
            member.thievingLevel = 0;
            member.hasThievingLevel = false;
            member.fishEaten.clear();
            member.deaths.clear();
            member.prepResources.clear();
            member.craftedPotions.clear();
            changed = true;
        }
        return changed;
    }

    public boolean clearAllTeamCoxData()
    {
        boolean changed = false;
        for (TeamMember member : teamMembers.values())
        {
            changed |= clearTeamMemberCoxData(member.memberId, member.displayName);
        }
        return changed;
    }

    public boolean removeTeamMember(long memberId)
    {
        return teamMembers.remove(memberId) != null;
    }

    public void clearTeamMembers()
    {
        teamMembers.clear();
    }

    public List<TeamMember> getTeamMembers()
    {
        return Collections.unmodifiableList(new ArrayList<>(teamMembers.values()));
    }

    public boolean hasTeamThievingTotal()
    {
        boolean hasCoxMember = false;
        for (TeamMember member : teamMembers.values())
        {
            if (!member.hasCoxData)
            {
                continue;
            }
            hasCoxMember = true;
            if (!member.hasThievingLevel)
            {
                return false;
            }
        }
        return hasCoxMember;
    }

    public int getTeamThievingTotal()
    {
        int total = 0;
        for (TeamMember member : teamMembers.values())
        {
            if (member.hasCoxData && member.hasThievingLevel)
            {
                total += member.thievingLevel;
            }
        }
        return total;
    }

    public boolean updateTeamMember(long memberId, String displayName, int personalPoints, String activeRoom, List<CoxTeamUpdate.Death> deaths,
        int overloadSips, int nonOverloadSips, List<CoxTeamUpdate.Fish> fishEaten, int thievingLevel)
    {
        return updateTeamMember(memberId, displayName, personalPoints, activeRoom, deaths, overloadSips, nonOverloadSips, fishEaten,
            thievingLevel, null, null);
    }

    public boolean updateTeamMember(long memberId, String displayName, int personalPoints, String activeRoom, List<CoxTeamUpdate.Death> deaths,
        int overloadSips, int nonOverloadSips, List<CoxTeamUpdate.Fish> fishEaten, int thievingLevel,
        List<CoxTeamUpdate.ItemCount> prepResources, List<CoxTeamUpdate.ItemCount> craftedPotions)
    {
        // Normalize all network-sourced strings, counts, points, and list lengths
        // here before any panel or calculation consumes them.
        TeamMember member = teamMembers.computeIfAbsent(memberId, TeamMember::new);
        List<CoxTeamUpdate.Fish> safeFish = sanitizeFish(fishEaten);
        List<CoxTeamUpdate.Death> safeDeaths = sanitizeDeaths(deaths);
        String safeDisplayName = sanitizeDisplayName(displayName);
        String safeActiveRoom = sanitizeActiveRoom(activeRoom);
        boolean changed = !member.hasCoxData;
        if (safeDisplayName != null && !safeDisplayName.equals(member.displayName))
        {
            member.displayName = safeDisplayName;
            changed = true;
        }
        int safePersonalPoints = Math.min(MAX_REMOTE_POINTS, Math.max(0, personalPoints));
        int safeOverloadSips = Math.min(MAX_REMOTE_COUNT, Math.max(0, overloadSips));
        int safeNonOverloadSips = Math.min(MAX_REMOTE_COUNT, Math.max(0, nonOverloadSips));
        int safeThievingLevel = Math.min(99, Math.max(0, thievingLevel));
        if (member.personalPoints != safePersonalPoints || !Objects.equals(member.activeRoom, safeActiveRoom)
            || member.overloadSips != safeOverloadSips || member.nonOverloadSips != safeNonOverloadSips
            || member.thievingLevel != safeThievingLevel || !member.hasThievingLevel)
        {
            member.personalPoints = safePersonalPoints;
            member.activeRoom = safeActiveRoom;
            member.overloadSips = safeOverloadSips;
            member.nonOverloadSips = safeNonOverloadSips;
            member.thievingLevel = safeThievingLevel;
            member.hasThievingLevel = true;
            changed = true;
        }
        if (!sameFish(member.fishEaten, safeFish))
        {
            member.fishEaten.clear();
            member.fishEaten.addAll(safeFish);
            changed = true;
        }
        if (!sameDeaths(member.deaths, safeDeaths))
        {
            member.deaths.clear();
            for (CoxTeamUpdate.Death death : safeDeaths)
            {
                member.deaths.add(new TeamDeath(death.room, death.pointsLost, death.occurredAtMillis));
            }
            changed = true;
        }
        if (prepResources != null && replaceKnownCounts(member.prepResources, prepResources, true))
        {
            changed = true;
        }
        if (craftedPotions != null && replaceKnownCounts(member.craftedPotions, craftedPotions, false))
        {
            changed = true;
        }
        member.hasCoxData = true;
        return changed;
    }

    private static String sanitizeDisplayName(String displayName)
    {
        if (displayName == null || displayName.isEmpty())
        {
            return null;
        }
        StringBuilder safe = new StringBuilder(Math.min(32, displayName.length()));
        for (int i = 0; i < displayName.length() && safe.length() < 32; i++)
        {
            char character = displayName.charAt(i);
            if (character == '\u00a0')
            {
                character = ' ';
            }
            if ((character >= 'A' && character <= 'Z') || (character >= 'a' && character <= 'z')
                || (character >= '0' && character <= '9') || character == ' ' || character == '_' || character == '-')
            {
                safe.append(character);
            }
        }
        String result = safe.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private static String sanitizeActiveRoom(String activeRoom)
    {
        if (activeRoom == null)
        {
            return null;
        }
        for (String room : Arrays.asList("Guardians", "Mystics", "Shamans", "Ice Demon", "Thieving", "Tightrope",
            "Crabs", "Muttadiles", "Tekton", "Vasa", "Vespula", "Vanguards", "Olm"))
        {
            if (room.equals(activeRoom))
            {
                return room;
            }
        }
        return null;
    }

    private static List<CoxTeamUpdate.Fish> sanitizeFish(List<CoxTeamUpdate.Fish> update)
    {
        if (update == null || update.isEmpty())
        {
            return Collections.emptyList();
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        int limit = Math.min(update.size(), COX_FISH.size() * 2);
        for (int i = 0; i < limit; i++)
        {
            CoxTeamUpdate.Fish candidate = update.get(i);
            if (candidate == null || candidate.name == null)
            {
                continue;
            }
            for (FishType fish : COX_FISH)
            {
                if (fish.name.equals(candidate.name))
                {
                    long total = (long) counts.getOrDefault(fish.name, 0) + Math.max(0, candidate.count);
                    counts.put(fish.name, (int) Math.min(MAX_REMOTE_COUNT, total));
                    break;
                }
            }
        }
        List<CoxTeamUpdate.Fish> sanitized = new ArrayList<>();
        for (FishType fish : COX_FISH)
        {
            int count = counts.getOrDefault(fish.name, 0);
            if (count > 0)
            {
                sanitized.add(new CoxTeamUpdate.Fish(fish.name, fish.points, count));
            }
        }
        return sanitized;
    }

    private static List<CoxTeamUpdate.Death> sanitizeDeaths(List<CoxTeamUpdate.Death> update)
    {
        if (update == null || update.isEmpty())
        {
            return Collections.emptyList();
        }
        List<CoxTeamUpdate.Death> sanitized = new ArrayList<>();
        int limit = Math.min(update.size(), MAX_REMOTE_DEATHS);
        for (int i = 0; i < limit; i++)
        {
            CoxTeamUpdate.Death candidate = update.get(i);
            if (candidate == null)
            {
                continue;
            }
            String room = sanitizeActiveRoom(candidate.room);
            room = room == null ? "Unknown" : room;
            sanitized.add(new CoxTeamUpdate.Death(room, Math.min(MAX_REMOTE_POINTS, Math.max(0, candidate.pointsLost)),
                Math.max(0, candidate.occurredAtMillis)));
        }
        return sanitized;
    }

    private static boolean replaceKnownCounts(Map<String, Integer> current, List<CoxTeamUpdate.ItemCount> update, boolean resources)
    {
        Map<String, Integer> next = new LinkedHashMap<>();
        if (resources)
        {
            for (Resource resource : RESOURCES)
            {
                putCount(next, resource.key, findItemCount(update, resource.key));
            }
        }
        else
        {
            for (Potion potion : COX_POTIONS)
            {
                putCount(next, potion.key, findItemCount(update, potion.key));
            }
        }
        if (current.equals(next))
        {
            return false;
        }
        current.clear();
        current.putAll(next);
        return true;
    }

    private static boolean sameFish(List<CoxTeamUpdate.Fish> current, List<CoxTeamUpdate.Fish> update)
    {
        int updateSize = update == null ? 0 : update.size();
        if (current.size() != updateSize)
        {
            return false;
        }
        for (int i = 0; i < updateSize; i++)
        {
            CoxTeamUpdate.Fish left = current.get(i);
            CoxTeamUpdate.Fish right = update.get(i);
            if (!Objects.equals(left.name, right.name) || left.points != right.points || left.count != right.count)
            {
                return false;
            }
        }
        return true;
    }

    private static boolean sameDeaths(List<TeamDeath> current, List<CoxTeamUpdate.Death> update)
    {
        int updateSize = update == null ? 0 : update.size();
        if (current.size() != updateSize)
        {
            return false;
        }
        for (int i = 0; i < updateSize; i++)
        {
            TeamDeath left = current.get(i);
            CoxTeamUpdate.Death right = update.get(i);
            String room = right.room == null || right.room.isEmpty() ? "Unknown" : right.room;
            if (!left.room.equals(room) || left.pointsLost != Math.max(0, right.pointsLost)
                || left.occurredAtMillis != right.occurredAtMillis)
            {
                return false;
            }
        }
        return true;
    }

    private boolean recordPendingLocalDeath()
    {
        // The time window tolerates client-event ordering while the assigned bit
        // prevents one personal loss from being attached more than once.
        if (pendingLocalDeath == null || recentGroupLoss == null || recentGroupLoss.assigned
            || Math.abs(pendingLocalDeath.occurredAtMillis - recentGroupLoss.occurredAtMillis) > 10_000)
        {
            return false;
        }

        localTeamDeaths.add(new TeamDeath(pendingLocalDeath.room, recentGroupLoss.pointsLost, pendingLocalDeath.occurredAtMillis));
        pendingLocalDeath = null;
        recentGroupLoss.assigned = true;
        return true;
    }

    public boolean isPrepInfoBoxesHidden()
    {
        return prepInfoBoxesHidden;
    }

    public void setPrepInfoBoxesHidden(boolean prepInfoBoxesHidden)
    {
        this.prepInfoBoxesHidden = prepInfoBoxesHidden;
    }

    public String getLayoutCode()
    {
        return layoutCode;
    }

    public boolean isKnownGoodLayout()
    {
        return Arrays.asList("FSCCSPCPSF", "SFCCSPCPSF", "SCPFCCSPSF", "SPSFPCCCSF", "SCSPFCCSPF").contains(layoutCode);
    }

    public boolean isPerfectLayout()
    {
        if (!isKnownGoodLayout())
        {
            return false;
        }

        int greenRooms = 0;
        boolean shamans = false;
        boolean mystics = false;
        boolean guardians = false;
        int wantedPuzzles = 0;
        for (ScoutedRoom room : scoutedRooms)
        {
            if ("Shamans".equals(room.name))
            {
                shamans = true;
                greenRooms++;
            }
            else if ("Mystics".equals(room.name))
            {
                mystics = true;
                greenRooms++;
            }
            else if ("Guardians".equals(room.name))
            {
                guardians = true;
                greenRooms++;
            }
            else if ("Ice Demon".equals(room.name) || "Thieving".equals(room.name) || "Tightrope".equals(room.name))
            {
                wantedPuzzles++;
                greenRooms++;
            }
        }

        return shamans && mystics && guardians && wantedPuzzles >= 2 && greenRooms >= 5;
    }

    public List<ScoutedRoom> getScoutedRooms()
    {
        return Collections.unmodifiableList(new ArrayList<>(scoutedRooms));
    }

    public void setScoutedLayout(String layoutCode, List<ScoutedRoom> rooms)
    {
        this.layoutCode = layoutCode == null || layoutCode.isEmpty() ? "Unknown" : layoutCode;
        scoutedRooms.clear();
        scoutedRooms.addAll(rooms);
    }

    public int getCollected(Resource resource)
    {
        return collectedCounts.getOrDefault(resource.key, 0) + craftedResourceCredit(resource.key);
    }

    public long getPrepRevision()
    {
        return prepRevision;
    }

    public int getRemaining(Resource resource)
    {
        return Math.max(0, getTarget(resource) - getCollected(resource));
    }

    public void resetResourceTracking()
    {
        for (Resource resource : RESOURCES)
        {
            collectedCounts.put(resource.key, 0);
            inventoryResourceCounts.put(resource.key, 0);
            privateStorageResourceCounts.put(resource.key, 0);
            sharedStorageCounts.put(resource.key, 0);
        }
        for (Potion potion : COX_POTIONS)
        {
            craftedPotionCounts.put(potion.key, 0);
            lastPotionCounts.put(potion.key, 0);
            sharedStorageCounts.put(potion.key, 0);
        }
        sharedStorageObservedAtMillis = 0;
        sharedStorageSourceMemberId = Long.MAX_VALUE;
        markPrepChanged();
    }

    public void notePotionCraftAction()
    {
        potionCraftWindowUntil = System.currentTimeMillis() + 15_000;
    }

    public int getCraftedPotions(String potion)
    {
        return craftedPotionCounts.getOrDefault(potion, 0);
    }

    public List<CoxTeamUpdate.ItemCount> getLocalPrepResources()
    {
        List<CoxTeamUpdate.ItemCount> counts = new ArrayList<>();
        for (Resource resource : RESOURCES)
        {
            addItemCount(counts, resource.key, getCollected(resource));
        }
        return counts;
    }

    public List<CoxTeamUpdate.ItemCount> getLocalCraftedPotions()
    {
        List<CoxTeamUpdate.ItemCount> counts = new ArrayList<>();
        for (Potion potion : COX_POTIONS)
        {
            int count = getCraftedPotions(potion.key);
            if (count > 0)
            {
                counts.add(new CoxTeamUpdate.ItemCount(potion.key, count));
            }
        }
        return counts;
    }

    public List<CoxTeamUpdate.ItemCount> getSharedStorage()
    {
        List<CoxTeamUpdate.ItemCount> counts = new ArrayList<>();
        for (Resource resource : RESOURCES)
        {
            addItemCount(counts, resource.key, sharedStorageCounts.getOrDefault(resource.key, 0));
        }
        for (Potion potion : COX_POTIONS)
        {
            addItemCount(counts, potion.key, sharedStorageCounts.getOrDefault(potion.key, 0));
        }
        return counts;
    }

    public long getSharedStorageObservedAtMillis()
    {
        return sharedStorageObservedAtMillis;
    }

    public boolean hasSharedStorageSnapshot()
    {
        return sharedStorageObservedAtMillis > 0;
    }

    public int getSharedStorageCount(String name)
    {
        return sharedStorageCounts.getOrDefault(name, 0);
    }

    public boolean updateSharedStorage(List<CoxTeamUpdate.ItemCount> update, long observedAtMillis)
    {
        return updateSharedStorage(update, observedAtMillis, Long.MAX_VALUE);
    }

    public boolean updateSharedStorage(List<CoxTeamUpdate.ItemCount> update, long observedAtMillis, long sourceMemberId)
    {
        // Reject far-future clocks and use timestamp/member id as a deterministic
        // tie-breaker. Received snapshots are never rebroadcast by the plugin.
        if (update == null || !validSharedStorageObservationTime(observedAtMillis, System.currentTimeMillis())
            || observedAtMillis < sharedStorageObservedAtMillis
            || (observedAtMillis == sharedStorageObservedAtMillis && sourceMemberId >= sharedStorageSourceMemberId))
        {
            return false;
        }
        boolean changed = false;
        for (Resource resource : RESOURCES)
        {
            changed |= putCount(sharedStorageCounts, resource.key, findItemCount(update, resource.key));
        }
        for (Potion potion : COX_POTIONS)
        {
            changed |= putCount(sharedStorageCounts, potion.key, findItemCount(update, potion.key));
        }
        sharedStorageObservedAtMillis = observedAtMillis;
        sharedStorageSourceMemberId = sourceMemberId;
        return changed;
    }

    static boolean validSharedStorageObservationTime(long observedAtMillis, long currentTimeMillis)
    {
        return observedAtMillis > 0 && observedAtMillis <= currentTimeMillis + MAX_SHARED_STORAGE_FUTURE_MILLIS;
    }

    public void setSharedStorageSourceMemberId(long sourceMemberId)
    {
        sharedStorageSourceMemberId = sourceMemberId;
    }

    private int craftedResourceCredit(String resource)
    {
        switch (resource)
        {
            case "Noxifer":
                return getCraftedPotions("Overloads");
            case "Golpar":
                return getCraftedPotions("Elder") + getCraftedPotions("Kodai") + getCraftedPotions("Twisted");
            case "Buchu":
                return getCraftedPotions("Revitalisation") + getCraftedPotions("Xeric's Aid") + getCraftedPotions("Prayer Enhance");
            case "Stinkhorn":
                return getCraftedPotions("Elder") + getCraftedPotions("Revitalisation");
            case "Endarkened":
                return getCraftedPotions("Kodai") + getCraftedPotions("Xeric's Aid");
            case "Cicely":
                return getCraftedPotions("Twisted") + getCraftedPotions("Prayer Enhance");
            default:
                return 0;
        }
    }

    private static void addItemCount(List<CoxTeamUpdate.ItemCount> counts, String name, int count)
    {
        if (count > 0)
        {
            counts.add(new CoxTeamUpdate.ItemCount(name, count));
        }
    }

    private static int findItemCount(List<CoxTeamUpdate.ItemCount> counts, String name)
    {
        if (counts == null || counts.isEmpty())
        {
            return 0;
        }
        int total = 0;
        int limit = Math.min(counts.size(), MAX_REMOTE_ITEM_COUNTS);
        for (int i = 0; i < limit; i++)
        {
            CoxTeamUpdate.ItemCount count = counts.get(i);
            if (count != null && name.equals(count.name))
            {
                total = (int) Math.min(MAX_REMOTE_COUNT, (long) total + Math.max(0, count.count));
            }
        }
        return total;
    }

    public boolean observeInventory(ItemContainer inventory)
    {
        // Count current resources directly, then credit confirmed crafted
        // potions so converting ingredients does not make prep progress regress.
        if (inventory == null)
        {
            return false;
        }

        int[] previousResourceCounts = new int[RESOURCES.size()];
        for (int i = 0; i < RESOURCES.size(); i++)
        {
            previousResourceCounts[i] = inventoryResourceCounts.getOrDefault(RESOURCES.get(i).key, 0);
        }
        int[] trackedCounts = trackedCounts(inventory);
        boolean changed = updateResourceCounts(inventoryResourceCounts, trackedCounts);
        changed |= updateCollectedCounts();

        boolean craftingPotion = System.currentTimeMillis() <= potionCraftWindowUntil;
        for (int i = 0; i < COX_POTIONS.size(); i++)
        {
            Potion potion = COX_POTIONS.get(i);
            int count = trackedCounts[RESOURCES.size() + i];
            int lastCount = lastPotionCounts.getOrDefault(potion.key, count);
            // The CoX potion-making widget does not reliably emit a
            // MenuOptionClicked event.  Also accept an inventory transition
            // where a potion appears while at least one tracked ingredient
            // disappears.  Bank withdrawals/drops cannot satisfy the
            // ingredient-reduction check, so they are not misclassified as
            // newly crafted potions.
            boolean ingredientsConsumed = false;
            for (int resourceIndex = 0; resourceIndex < RESOURCES.size(); resourceIndex++)
            {
                if (trackedCounts[resourceIndex] < previousResourceCounts[resourceIndex])
                {
                    ingredientsConsumed = true;
                    break;
                }
            }
            if ((craftingPotion || ingredientsConsumed) && count > lastCount)
            {
                craftedPotionCounts.put(potion.key, getCraftedPotions(potion.key) + count - lastCount);
                changed = true;
            }
            lastPotionCounts.put(potion.key, count);
        }
        if (changed)
        {
            markPrepChanged();
        }
        return changed;
    }

    public boolean observePrivateStorage(ItemContainer storage)
    {
        if (storage == null)
        {
            return false;
        }
        boolean changed = updateResourceCounts(privateStorageResourceCounts, trackedCounts(storage));
        changed |= updateCollectedCounts();
        if (changed)
        {
            markPrepChanged();
        }
        return changed;
    }

    public boolean observeSharedStorage(ItemContainer storage)
    {
        if (storage == null)
        {
            return false;
        }
        return observeSharedStorageCounts(trackedCounts(storage));
    }

    public boolean hasTrackedPrepItems(ItemContainer storage)
    {
        if (storage == null)
        {
            return false;
        }
        for (int count : trackedCounts(storage))
        {
            if (count > 0)
            {
                return true;
            }
        }
        return false;
    }

    private boolean observeSharedStorageCounts(int[] counts)
    {
        boolean changed = false;
        for (int i = 0; i < RESOURCES.size(); i++)
        {
            Resource resource = RESOURCES.get(i);
            changed |= putCount(sharedStorageCounts, resource.key, counts[i]);
        }
        for (int i = 0; i < COX_POTIONS.size(); i++)
        {
            Potion potion = COX_POTIONS.get(i);
            changed |= putCount(sharedStorageCounts, potion.key, counts[RESOURCES.size() + i]);
        }
        if (changed || sharedStorageObservedAtMillis == 0)
        {
            sharedStorageObservedAtMillis = Math.max(System.currentTimeMillis(), sharedStorageObservedAtMillis + 1);
            changed = true;
        }
        return changed;
    }

    private static int[] trackedCounts(ItemContainer container)
    {
        int[] counts = new int[RESOURCES.size() + COX_POTIONS.size()];
        Item[] items = container.getItems();
        if (items == null)
        {
            return counts;
        }
        for (Item item : items)
        {
            if (item == null || item.getQuantity() <= 0)
            {
                continue;
            }
            Integer trackedIndex = TRACKED_INVENTORY_INDEX.get(item.getId());
            if (trackedIndex != null)
            {
                counts[trackedIndex] += item.getQuantity();
            }
        }
        return counts;
    }

    private boolean updateResourceCounts(Map<String, Integer> destination, int[] counts)
    {
        boolean changed = false;
        for (int i = 0; i < RESOURCES.size(); i++)
        {
            changed |= putCount(destination, RESOURCES.get(i).key, counts[i]);
        }
        return changed;
    }

    private boolean updateCollectedCounts()
    {
        boolean changed = false;
        for (Resource resource : RESOURCES)
        {
            int total = inventoryResourceCounts.getOrDefault(resource.key, 0)
                + privateStorageResourceCounts.getOrDefault(resource.key, 0);
            changed |= putCount(collectedCounts, resource.key, total);
        }
        return changed;
    }

    private static boolean putCount(Map<String, Integer> counts, String key, int value)
    {
        int safeValue = Math.max(0, value);
        if (counts.getOrDefault(key, 0) == safeValue)
        {
            return false;
        }
        counts.put(key, safeValue);
        return true;
    }

    private static int saturatedInt(long value)
    {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
    }

    private void markPrepChanged()
    {
        prepRevision++;
    }

    private static Map<Integer, Integer> buildTrackedInventoryIndex()
    {
        Map<Integer, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < RESOURCES.size(); i++)
        {
            Resource resource = RESOURCES.get(i);
            index.put(resource.cleanItemId, i);
            index.put(resource.grimyItemId, i);
        }
        for (int i = 0; i < COX_POTIONS.size(); i++)
        {
            for (int itemId : COX_POTIONS.get(i).itemIds)
            {
                index.put(itemId, RESOURCES.size() + i);
            }
        }
        return Collections.unmodifiableMap(index);
    }

    public static class Resource
    {
        public final String key;
        public final int cleanItemId;
        public final int grimyItemId;

        Resource(String key, int cleanItemId, int grimyItemId)
        {
            this.key = key;
            this.cleanItemId = cleanItemId;
            this.grimyItemId = grimyItemId;
        }
    }

    public static class ScoutedRoom
    {
        public final String type;
        public final String name;

        public ScoutedRoom(String type, String name)
        {
            this.type = type;
            this.name = name;
        }
    }

    public static class TrackedRoomPoints
    {
        public final String room;
        public final int personalPoints;
        public final int groupPoints;
        public final long durationMillis;

        TrackedRoomPoints(String room, int personalPoints, int groupPoints)
        {
            this(room, personalPoints, groupPoints, 0);
        }

        TrackedRoomPoints(String room, int personalPoints, int groupPoints, long durationMillis)
        {
            this.room = room;
            this.personalPoints = personalPoints;
            this.groupPoints = groupPoints;
            this.durationMillis = Math.max(0, durationMillis);
        }
    }

    public static class DeathPointLoss
    {
        public final String room;
        public final int pointsLost;

        DeathPointLoss(String room, int pointsLost)
        {
            this.room = room;
            this.pointsLost = pointsLost;
        }
    }

    private static class FishType
    {
        final int itemId;
        final String name;
        final int points;

        FishType(int itemId, String name, int points)
        {
            this.itemId = itemId;
            this.name = name;
            this.points = points;
        }
    }

    private static class Potion
    {
        final String key;
        final int[] itemIds;

        Potion(String key, int... itemIds)
        {
            this.key = key;
            this.itemIds = itemIds;
        }

        int doseForItem(int itemId)
        {
            for (int i = 0; i < itemIds.length; i++)
            {
                if (itemIds[i] == itemId)
                {
                    return i % 4 + 1;
                }
            }
            return 0;
        }

    }

    public static class TeamMember
    {
        public final long memberId;
        public String displayName = "Unknown";
        public boolean hasCoxData;
        public int personalPoints;
        public String activeRoom;
        public int overloadSips;
        public int nonOverloadSips;
        public int thievingLevel;
        public boolean hasThievingLevel;
        public final List<CoxTeamUpdate.Fish> fishEaten = new ArrayList<>();
        public final List<TeamDeath> deaths = new ArrayList<>();
        public final Map<String, Integer> prepResources = new LinkedHashMap<>();
        public final Map<String, Integer> craftedPotions = new LinkedHashMap<>();

        TeamMember(long memberId)
        {
            this.memberId = memberId;
        }

        public int getDeathPointsLost()
        {
            long total = 0;
            for (TeamDeath death : deaths)
            {
                total += death.pointsLost;
            }
            return saturatedInt(total);
        }

        public int getFishCount()
        {
            long total = 0;
            for (CoxTeamUpdate.Fish fish : fishEaten)
            {
                total += Math.max(0, fish.count);
            }
            return saturatedInt(total);
        }

        public int getFishPoints()
        {
            long total = 0;
            for (CoxTeamUpdate.Fish fish : fishEaten)
            {
                total += Math.max(0, fish.points) * Math.max(0, fish.count);
            }
            return saturatedInt(total);
        }

        public int getPrepResourceCount(String resource)
        {
            return prepResources.getOrDefault(resource, 0);
        }

        public int getCraftedPotionCount(String potion)
        {
            return craftedPotions.getOrDefault(potion, 0);
        }
    }

    public static class TeamDeath
    {
        public final String room;
        public final int pointsLost;
        public final long occurredAtMillis;

        TeamDeath(String room, int pointsLost, long occurredAtMillis)
        {
            this.room = room == null || room.isEmpty() ? "Unknown" : room;
            this.pointsLost = Math.max(0, pointsLost);
            this.occurredAtMillis = occurredAtMillis;
        }
    }

    private static class PendingLocalDeath
    {
        final String room;
        final long occurredAtMillis;

        PendingLocalDeath(String room, long occurredAtMillis)
        {
            this.room = room;
            this.occurredAtMillis = occurredAtMillis;
        }
    }

    private static class RecentGroupLoss
    {
        final String room;
        final int pointsLost;
        final long occurredAtMillis;
        boolean assigned;

        RecentGroupLoss(String room, int pointsLost, long occurredAtMillis)
        {
            this.room = room;
            this.pointsLost = pointsLost;
            this.occurredAtMillis = occurredAtMillis;
        }
    }
}
