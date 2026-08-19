package com.coxmegascale;

import com.coxmegascale.detect.RaidRoomDetector;
import com.coxmegascale.party.CoxTeamUpdate;
import com.coxmegascale.party.MysticsSpecUpdate;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Actor;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.NpcID;
import net.runelite.api.ObjectComposition;
import net.runelite.api.InstanceTemplates;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.Notifier;
import net.runelite.client.RuneLite;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.messages.UserSync;
import net.runelite.client.plugins.raids.RoomType;
import net.runelite.client.plugins.raids.Raid;
import net.runelite.client.plugins.raids.RaidsPlugin;
import net.runelite.client.plugins.raids.events.RaidReset;
import net.runelite.client.plugins.raids.events.RaidScouted;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.Text;

/**
 * RuneLite lifecycle and event coordinator for CoX Assistant.
 *
 * <p>The plugin observes public client events, updates {@link CoxMegascaleState},
 * exchanges raid-scoped Party snapshots, writes explicitly documented local
 * logs, and requests targeted sidebar refreshes. It never generates gameplay
 * input or changes game state.</p>
 */
@PluginDescriptor(
    name = "CoX Assistant",
    description = "Informational Chambers of Xeric sidebar for scouting, estimates, points, prep, NPC stats, defence specs, Olm, and Party tracking.",
    tags = {"cox", "chambers", "xeric", "raid", "megascale", "assistant", "scouting", "points", "purple", "prep", "defence", "olm", "party"}
)
@PluginDependency(RaidsPlugin.class)
public class CoxMegascalePlugin extends Plugin
{
    private static final String SCALED_PARTY_SIZE_LABEL = "Scaled party size:";
    private static final int COX_SCALED_PARTY_SIZE_VARBIT = 9540;
    private static final int COX_ROOMS_PER_PLANE = 8;
    private static final int COX_ROOMS_PER_ROW = 4;
    private static final int COX_LOBBY_PLANE = 3;
    private static final int COX_SECOND_FLOOR_PLANE = 2;
    private static final int COX_ROOM_SIZE = 32;
    // Published fixed order used by Challenge Mode and regular full-layout raids.
    private static final List<String> FULL_LAYOUT_ROOM_ORDER = Arrays.asList(
        "Tekton", "Crabs", "Ice Demon", "Shamans", "Vanguards", "Thieving",
        "Vespula", "Tightrope", "Guardians", "Vasa", "Mystics", "Muttadiles");
    private static final String RAID_START_MESSAGE = "The raid has begun!";
    private static final String RAID_COMPLETE_MESSAGE = "Congratulations - your raid is complete!";
    private static final int COX_PRIVATE_STORAGE_CONTAINER = net.runelite.api.gameval.InventoryID.RAIDS_PRIVATESTORAGE;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private InfoBoxManager infoBoxManager;

    @Inject
    private ItemManager itemManager;

    @Inject
    private SkillIconManager skillIconManager;

    @Inject
    private SpriteManager spriteManager;

    @Inject
    private Notifier notifier;

    @Inject
    private CoxMegascaleConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private PartyService partyService;

    @Inject
    private WSClient partyWebsocket;

    // Long-lived models and bounded collections survive selective UI rebuilds;
    // explicit raid/logout/shutdown handlers reset their scoped contents.
    private final RaidRoomDetector detector = new RaidRoomDetector();
    private final CoxMegascaleState state = new CoxMegascaleState();
    private final List<CoxMegascaleResourceInfoBox> resourceInfoBoxes = new ArrayList<>();
    private final List<CoxMegascaleMysticsSpecInfoBox> mysticsSpecInfoBoxes = new ArrayList<>();
    // One special can emit multiple delayed hitsplats. Keep a single pending
    // attempt and collect only local hits in its bounded confirmation window.
    private final List<Integer> pendingMysticsHits = new ArrayList<>();
    private final Map<Long, Integer> lastRosterSnapshotResponseTick = new HashMap<>();
    private final LinkedHashSet<String> raidParticipantNames = new LinkedHashSet<>();
    private boolean perfectLayoutNotified;
    private Raid scoutedRaid;
    private String raidMode = "unknown";
    private int previousSpecialEnergy = -1;
    private PendingMysticsSpec pendingMysticsSpec;
    private PendingConsumable pendingConsumable;
    private Widget scaledPartySizeWidget;
    private int lastScaleWidgetSearchTick = Integer.MIN_VALUE;
    private int lastManualRefreshTick = Integer.MIN_VALUE;
    private Integer lastDetectedScale;
    private boolean previousInCoxRaid;
    private int previousRaidPartyId = Integer.MIN_VALUE;
    private int activeRaidPartyId = -1;
    private int discoveredSharedStorageContainerId = -1;
    private long locallyObservedSharedStorageAtMillis;
    private boolean infoBoxesEnabled;
    private volatile boolean infoBoxesGloballyVisible;
    private PointFixtureLogger pointFixtureLogger;
    private RaidSummaryLogger raidSummaryLogger;
    private long raidStartedAtMillis;
    private boolean raidSummaryWritten;
    private int lastRaidRosterScanTick = Integer.MIN_VALUE;
    private boolean fullLayoutAnnounced;
    private String fixturePointObservation;
    private int fixturePointObservationExpiryTick = Integer.MIN_VALUE;
    private boolean fixtureClosedForCurrentRaid;
    private String fixtureStartReason = "enabled_mid_raid";

    private CoxMegascalePanel panel;
    private NavigationButton navigationButton;

    @Override
    protected void startUp()
    {
        // Construct local writers and register Party payloads before the first
        // state snapshot can be observed or sent.
        pointFixtureLogger = new PointFixtureLogger(config.pointFixtureLogging(), RuneLite.RUNELITE_DIR.toPath());
        raidSummaryLogger = new RaidSummaryLogger(RuneLite.RUNELITE_DIR.toPath());
        infoBoxesEnabled = config.showInfoBoxes();
        clientThread.invokeLater(this::updateInfoBoxVisibility);
        partyWebsocket.registerMessage(CoxTeamUpdate.class);
        partyWebsocket.registerMessage(MysticsSpecUpdate.class);
        panel = new CoxMegascalePanel(
            config.defaultScale(),
            config.defaultThievingLevel(),
            config.defaultFishingLevel(),
            state,
            () -> clientThread.invokeLater(this::refreshScoutedRaid),
            skillIconManager,
            config.showWelcomeOnStartup(),
            show -> configManager.setConfiguration(CoxMegascaleConfig.GROUP, "showWelcomeOnStartup", show),
            itemManager,
            spriteManager
        );
        navigationButton = NavigationButton.builder()
            .tooltip("CoX Assistant")
            .icon(createIcon())
            .priority(6)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navigationButton);
        if (config.showWelcomeOnStartup())
        {
            panel.showWelcomePage();
        }
        addResourceInfoBoxes();
        addMysticsSpecInfoBoxes();
        syncTeamMembers();
        refreshPanel();
    }

    @Override
    protected void shutDown()
    {
        // RuneLite can disable/re-enable a plugin in one client session, so all
        // UI, Party registrations, pending observations, and writers are detached.
        if (raidSummaryLogger != null)
        {
            raidSummaryLogger.close();
            raidSummaryLogger = null;
        }
        if (pointFixtureLogger != null)
        {
            pointFixtureLogger.close();
            pointFixtureLogger = null;
        }
        if (navigationButton != null)
        {
            clientToolbar.removeNavigation(navigationButton);
        }
        removeResourceInfoBoxes();
        removeMysticsSpecInfoBoxes();
        partyWebsocket.unregisterMessage(CoxTeamUpdate.class);
        partyWebsocket.unregisterMessage(MysticsSpecUpdate.class);
        resetRaidSessionState();
        clearPendingMysticsSpec();
        pendingConsumable = null;
        previousSpecialEnergy = -1;
        panel = null;
        navigationButton = null;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
        {
            if (pointFixtureLogger != null)
            {
                pointFixtureLogger.endSession("client_state_change");
            }
            resetRaidSessionState();
            clearPendingMysticsSpec();
            pendingConsumable = null;
            previousSpecialEnergy = -1;
            state.setPrepInfoBoxesHidden(false);
            perfectLayoutNotified = false;
            refreshPanel();
            updateInfoBoxVisibility();
        }
        else if (event.getGameState() == GameState.LOGGED_IN)
        {
            refreshPanel();
            updateInfoBoxVisibility();
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath event)
    {
        if (event.getActor() == client.getLocalPlayer() && state.noteLocalDeath())
        {
            shareTeamUpdate();
            if (panel != null)
            {
                panel.refreshFromPlugin("Points", "Team");
            }
        }
    }

    @Subscribe
    public void onPartyChanged(PartyChanged event)
    {
        clientThread.invokeLater(() ->
        {
            if (!partyService.isInParty())
            {
                state.clearTeamMembers();
            }
            else
            {
                syncTeamMembers();
                shareTeamUpdate();
            }
            if (panel != null)
            {
                panel.refreshFromPlugin("Summary", "Options", "Points", "Team");
            }
        });
    }

    @Subscribe
    public void onUserSync(UserSync event)
    {
        clientThread.invokeLater(() -> shareTeamUpdate());
    }

    @Subscribe
    public void onCoxTeamUpdate(CoxTeamUpdate event)
    {
        clientThread.invokeLater(() -> handleCoxTeamUpdate(event));
    }

    private void handleCoxTeamUpdate(CoxTeamUpdate event)
    {
        // Schema compatibility and matching CoX party identity are both required
        // before any remote gameplay-derived field reaches shared state.
        if (!compatiblePartySchema(event.schemaVersion, CoxTeamUpdate.SCHEMA_VERSION))
        {
            if (state.clearTeamMemberCoxData(event.getMemberId(), null) && panel != null)
            {
                panel.refreshFromPlugin("Summary", "Options", "Points", "Team");
            }
            return;
        }
        if (!isSameCoxRaid(event))
        {
            if (state.clearTeamMemberCoxData(event.getMemberId(), event.displayName))
            {
                if (panel != null)
                {
                    panel.refreshFromPlugin("Summary", "Options", "Points", "Team");
                }
            }
            return;
        }
        boolean teamChanged = state.updateTeamMember(event.getMemberId(), event.displayName, event.personalPoints, event.activeRoom, event.deaths,
            event.overloadSips, event.nonOverloadSips, event.fishEaten, event.thievingLevel, event.prepResources, event.craftedPotions);
        boolean sharedStorageChanged = state.updateSharedStorage(event.sharedStorage, event.sharedStorageObservedAtMillis, event.getMemberId());
        if (event.requestRosterSnapshot && shouldRespondToRosterRequest(event.getMemberId()))
        {
            shareTeamUpdate();
        }
        if ((teamChanged || sharedStorageChanged) && panel != null)
        {
            panel.refreshFromPlugin("Summary", "Options", "Points", "Team");
        }
    }

    @Subscribe
    public void onMysticsSpecUpdate(MysticsSpecUpdate event)
    {
        clientThread.invokeLater(() ->
        {
            if (!compatiblePartySchema(event.schemaVersion, MysticsSpecUpdate.SCHEMA_VERSION)
                || !isSameCoxRaid(event.inCoxRaid, event.raidPartyId))
            {
                return;
            }
            boolean legacy = event.targetKey != null && event.targetKey.startsWith("mystics:") && state.recordMysticsSpecHit(event.weapon, event.amount);
            boolean defence = event.targetKey != null && state.recordDefenceSpecHit(event.targetKey, event.weapon, event.amount);
            if ((legacy || defence) && panel != null)
            {
                panel.refreshFromPlugin("Summary");
            }
        });
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (CoxMegascaleConfig.GROUP.equals(event.getGroup()) && "showInfoBoxes".equals(event.getKey()))
        {
            infoBoxesEnabled = config.showInfoBoxes();
            clientThread.invokeLater(this::updateInfoBoxVisibility);
        }
        if (CoxMegascaleConfig.GROUP.equals(event.getGroup()) && "pointFixtureLogging".equals(event.getKey()))
        {
            clientThread.invokeLater(this::updatePointFixtureLogging);
        }
    }

    private void updatePointFixtureLogging()
    {
        // Recreate the writer on configuration changes so disabling closes the
        // active session and enabling mid-raid starts with explicit provenance.
        if (pointFixtureLogger != null)
        {
            pointFixtureLogger.close();
        }
        pointFixtureLogger = new PointFixtureLogger(config.pointFixtureLogging(), RuneLite.RUNELITE_DIR.toPath());
        if (config.pointFixtureLogging() && client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1)
        {
            fixtureStartReason = "enabled_mid_raid";
            updateRaidModeAndLayout();
            ensurePointFixtureSession(client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSCORE),
                client.getVarpValue(VarPlayerID.RAIDS_PLAYERSCORE));
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        if (event.getVarbitId() == VarbitID.RAIDS_USINGPRIVATESTORAGE)
        {
            if (client.getVarbitValue(VarbitID.RAIDS_USINGPRIVATESTORAGE) != 0)
            {
                discoveredSharedStorageContainerId = -1;
            }
            return;
        }
        if (event.getVarpId() == VarPlayerID.RAIDS_PARTY_GROUPHOLDER || event.getVarbitId() == VarbitID.RAIDS_CLIENT_INDUNGEON)
        {
            boolean wasInCoxRaid = previousInCoxRaid;
            boolean membershipChanged = updateLocalRaidMembership();
            boolean inCoxRaid = previousInCoxRaid;
            if (wasInCoxRaid && !inCoxRaid && pointFixtureLogger != null)
            {
                pointFixtureLogger.endSession("raid_exit");
                fixtureClosedForCurrentRaid = false;
                fixtureStartReason = "enabled_mid_raid";
            }
            if (shouldClearTeamCoxData(wasInCoxRaid, inCoxRaid))
            {
                state.clearAllTeamCoxData();
            }
            syncTeamMembers();
            shareTeamUpdate(shouldRequestTeamSnapshot(membershipChanged, inCoxRaid));
            refreshPanel();
            return;
        }
        if (event.getVarpId() != VarPlayerID.SA_ENERGY)
        {
            return;
        }

        int specialEnergy = client.getVarpValue(VarPlayerID.SA_ENERGY);
        boolean usedSpecial = previousSpecialEnergy >= 0 && specialEnergy < previousSpecialEnergy;
        previousSpecialEnergy = specialEnergy;
        if (usedSpecial)
        {
            // RuneLite updates the target after special energy. Preserve this tick before deferring.
            int specialTick = client.getTickCount();
            clientThread.invokeLater(() -> queueDefenceSpec(specialTick));
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        if (pendingMysticsSpec == null || event.getActor() != pendingMysticsSpec.target
            || !acceptsPendingSpecHitsplat(client.getTickCount(), pendingMysticsSpec.hitsplatTick, pendingMysticsSpec.expiryTick)
            || !event.getHitsplat().isMine())
        {
            return;
        }
        pendingMysticsHits.add(event.getHitsplat().getAmount());
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        NPC npc = event.getMenuEntry().getNpc();
        if (npc == null || !"Attack".equals(event.getOption()))
        {
            return;
        }
        String targetKey = defenceTargetKey(npc);
        if (targetKey != null)
        {
            state.activateDefenceTarget(targetKey);
        }
        com.coxmegascale.calc.DefenceTracker tracker = targetKey == null ? null : state.getDefenceTracker(targetKey);
        if (tracker != null)
        {
            event.getMenuEntry().setTarget(event.getTarget() + " <col=" + tracker.getMenuColor() + ">(" + tracker.getCurrentDefence() + ")</col>");
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (pendingConsumable != null && client.getTickCount() > pendingConsumable.expiryTick)
        {
            pendingConsumable = null;
        }
        finishPendingDefenceSpec();
        updateRaidState();
        updateInfoBoxVisibility();
    }

    private void finishPendingDefenceSpec()
    {
        if (pendingMysticsSpec == null)
        {
            return;
        }
        int currentTick = client.getTickCount();
        if (currentTick < pendingMysticsSpec.hitsplatTick
            || shouldWaitForDelayedSpecHit(currentTick, pendingMysticsSpec.hitsplatTick, pendingMysticsSpec.expiryTick,
                pendingMysticsHits.isEmpty()))
        {
            return;
        }

        // Finalize only after every accepted same-attempt hitsplat has had time
        // to arrive; zero/expired attempts never change or share defence state.
        int amount = confirmedMysticsSpecAmount();
        String weapon = pendingMysticsSpec.weapon;
        String targetKey = pendingMysticsSpec.targetKey;
        pendingMysticsSpec = null;
        pendingMysticsHits.clear();
        if (amount <= 0)
        {
            return;
        }
        boolean legacyMysticsRecorded = targetKey.startsWith("mystics:") && state.recordMysticsSpecHit(weapon, amount);
        boolean defenceRecorded = state.recordDefenceSpecHit(targetKey, weapon, amount);
        if (!legacyMysticsRecorded && !defenceRecorded)
        {
            return;
        }
        if (defenceRecorded)
        {
            shareDefenceSpecHit(targetKey, weapon, amount);
        }
        if (panel != null)
        {
            panel.refreshFromPlugin("Summary");
        }
    }

    private void updateRaidState()
    {
        // This is the single once-per-game-tick reconciliation point. Party
        // sends and tab refreshes remain gated on specific observable changes.
        Integer scaledPartySize = findScaledPartySize();
        if (scaledPartySize != null && !Objects.equals(lastDetectedScale, scaledPartySize))
        {
            lastDetectedScale = scaledPartySize;
            state.configureDefenceTracker(scaledPartySize);
            if (panel != null)
            {
                panel.setDetectedScale(scaledPartySize);
            }
        }

        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        boolean wasInCoxRaid = previousInCoxRaid;
        boolean localRaidMembershipChanged = updateLocalRaidMembership();
        if (shouldClearTeamCoxData(wasInCoxRaid, previousInCoxRaid))
        {
            state.clearAllTeamCoxData();
        }
        updateRaidModeAndLayout();
        int personalPoints = client.getVarpValue(VarPlayerID.RAIDS_PLAYERSCORE);
        int groupPoints = client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSCORE);
        ensureRaidStartTimestamp();
        observeRaidParticipants(false);
        ensurePointFixtureSession(groupPoints, personalPoints);
        boolean personalPointsChanged = personalPoints != state.getCurrentPersonalPoints();
        boolean groupPointsChanged = groupPoints != state.getCurrentGroupPoints();
        int groupDelta = groupPoints - state.getCurrentGroupPoints();
        boolean localDeathRecorded = state.setCurrentPoints(personalPoints, groupPoints);
        String previousRoom = state.getActiveRoomName();
        updateTrackedRoomFromPlayerLocation();
        boolean roomChanged = !Objects.equals(previousRoom, state.getActiveRoomName());
        if (pointFixtureLogger != null)
        {
            if (roomChanged && previousRoom != null)
            {
                pointFixtureLogger.room("room_exit", previousRoom, groupPoints, personalPoints, "room_exit_observed");
            }
            if (roomChanged && state.getActiveRoomName() != null)
            {
                pointFixtureLogger.room("room_entry", state.getActiveRoomName(), groupPoints, personalPoints, "room_entry_observed");
            }
            else if (groupPointsChanged || personalPointsChanged)
            {
                pointFixtureLogger.room("points_update", state.getActiveRoomName(), groupPoints, personalPoints,
                    fixturePointSource(groupDelta, localDeathRecorded));
            }
        }
        boolean localSharedStateChanged = personalPointsChanged || localDeathRecorded || roomChanged || localRaidMembershipChanged;
        if (localSharedStateChanged)
        {
            shareTeamUpdate(shouldRequestTeamSnapshot(localRaidMembershipChanged, previousInCoxRaid));
        }
        if (personalPointsChanged || groupPointsChanged || localDeathRecorded || roomChanged || localRaidMembershipChanged)
        {
            if (panel != null)
            {
                List<String> tabs = new ArrayList<>();
                tabs.add("Points");
                if (personalPointsChanged || localDeathRecorded || roomChanged || localRaidMembershipChanged)
                {
                    tabs.add("Team");
                }
                if (roomChanged || localRaidMembershipChanged)
                {
                    tabs.add("Summary");
                }
                if (localRaidMembershipChanged)
                {
                    tabs.add("Options");
                }
                panel.refreshFromPlugin(tabs.toArray(new String[0]));
            }
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        // Container identity distinguishes inventory, private storage, and the
        // dynamically discovered shared-storage container without polling them.
        if (event.getContainerId() == InventoryID.INVENTORY.getId())
        {
            boolean consumed = confirmPendingConsumable(event.getItemContainer());
            boolean prepChanged = state.observeInventory(event.getItemContainer());
            if (consumed || prepChanged)
            {
                shareTeamUpdate();
                if (panel != null)
                {
                    panel.refreshFromPlugin(consumed ? new String[]{"Summary", "Points", "Team"} : new String[]{"Summary", "Team"});
                }
            }
        }
        else if (isPrivateCoxStorageContainer(event.getContainerId()))
        {
            observeCoxStorage(event.getItemContainer(), true);
        }
        else if (event.getContainerId() == discoveredSharedStorageContainerId)
        {
            observeCoxStorage(event.getItemContainer(), false);
        }
        else if (isSharedStorageTabSelected() && state.hasTrackedPrepItems(event.getItemContainer()))
        {
            discoveredSharedStorageContainerId = event.getContainerId();
            observeCoxStorage(event.getItemContainer(), false);
        }
    }

    private boolean isSharedStorageTabSelected()
    {
        return client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1
            && client.getVarbitValue(VarbitID.RAIDS_USINGPRIVATESTORAGE) == 0;
    }

    private void observeCoxStorage(ItemContainer storage, boolean privateStorage)
    {
        if (storage == null)
        {
            return;
        }
        boolean changed = privateStorage ? state.observePrivateStorage(storage) : state.observeSharedStorage(storage);
        if (!changed)
        {
            return;
        }
        if (!privateStorage)
        {
            locallyObservedSharedStorageAtMillis = state.getSharedStorageObservedAtMillis();
            PartyMember localMember = partyService.getLocalMember();
            state.setSharedStorageSourceMemberId(localMember == null ? Long.MAX_VALUE : localMember.getMemberId());
        }
        shareTeamUpdate();
        if (panel != null)
        {
            panel.refreshFromPlugin("Summary", "Team");
        }
    }

    static boolean isPrivateCoxStorageContainer(int containerId)
    {
        return containerId == COX_PRIVATE_STORAGE_CONTAINER;
    }

    @Subscribe
    public void onRaidScouted(RaidScouted event)
    {
        raidMode = raidModeFromChallengeVarbit(client.getVarbitValue(VarbitID.RAIDS_CHALLENGE_MODE));
        applyRaidScout(event.getRaid());
    }

    /**
     * Challenge Mode raids do not publish RaidScouted. Build the same lightweight
     * room map that RuneLite's raids plugin builds from instance templates so that
     * layout display and room point tracking are available in every raid mode.
     */
    private void updateRaidModeAndLayout()
    {
        if (client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) != 1)
        {
            raidMode = "unknown";
            return;
        }

        String detectedMode = raidModeFromChallengeVarbit(client.getVarbitValue(VarbitID.RAIDS_CHALLENGE_MODE));
        if (!Objects.equals(raidMode, detectedMode))
        {
            raidMode = detectedMode;
            scoutedRaid = null;
            state.setScoutedLayout("Unknown", new ArrayList<>());
            detector.clear();
        }

        if (scoutedRaid == null)
        {
            Raid scanned = buildRaidFromInstance();
            if (scanned != null)
            {
                applyRaidScout(scanned);
            }
        }
    }

    static String raidModeFromChallengeVarbit(int challengeModeValue)
    {
        return challengeModeValue == 1 ? "challenge" : "normal";
    }

    private void ensurePointFixtureSession(int groupPoints, int personalPoints)
    {
        // Delay file sessions until raid identity, scout/mode, and detected scale
        // are all known; this prevents partial or duplicate calibration files.
        boolean scoutReady = scoutedRaid != null || "challenge".equals(raidMode);
        if (!canOpenPointFixture(
            pointFixtureLogger != null,
            pointFixtureLogger != null && pointFixtureLogger.hasActiveSession(),
            fixtureClosedForCurrentRaid,
            client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1,
            scoutReady,
            lastDetectedScale,
            state.getLayoutCode()))
        {
            return;
        }
        int thievingLevel = state.hasTeamThievingTotal() ? state.getTeamThievingTotal()
            : panel == null ? config.defaultThievingLevel() : panel.getFixtureThievingLevel();
        String thievingSource = state.hasTeamThievingTotal() ? "compatible_party_sum"
            : panel == null ? "config_fallback" : panel.getFixtureThievingSource();
        int fishingLevel = panel == null ? config.defaultFishingLevel() : panel.getFixtureFishingLevel();
        String fishingSource = panel == null ? "config_fallback" : panel.getFixtureFishingSource();
        pointFixtureLogger.startRaid(new PointFixtureLogger.Metadata(
            fixtureRaidMode(),
            fixtureStartReason,
            state.getLayoutCode(),
            lastDetectedScale,
            "detected",
            thievingLevel,
            thievingSource,
            fishingLevel,
            fishingSource
        ), groupPoints, personalPoints);
    }

    static boolean canOpenPointFixture(
        boolean loggerPresent,
        boolean active,
        boolean closedForCurrentRaid,
        boolean inDungeon,
        boolean scoutReady,
        Integer detectedScale,
        String layoutCode)
    {
        return loggerPresent && !active && !closedForCurrentRaid && inDungeon && scoutReady
            && detectedScale != null && layoutCode != null && !"Unknown".equalsIgnoreCase(layoutCode);
    }

    private String fixturePointSource(int groupDelta, boolean localDeathRecorded)
    {
        if (localDeathRecorded)
        {
            return "local_death_observed";
        }
        if (fixturePointObservation != null && client.getTickCount() <= fixturePointObservationExpiryTick)
        {
            String source = fixturePointObservation;
            fixturePointObservation = null;
            fixturePointObservationExpiryTick = Integer.MIN_VALUE;
            return source;
        }
        return groupDelta < 0 ? "unclassified_loss" : "unclassified";
    }

    private String fixtureRaidMode()
    {
        if ("challenge".equals(raidMode))
        {
            return "cm";
        }
        return isFullLayout(state.getScoutedRooms()) ? "full" : "normal";
    }

    static boolean isFullLayout(List<CoxMegascaleState.ScoutedRoom> rooms)
    {
        Set<String> found = new HashSet<>();
        for (CoxMegascaleState.ScoutedRoom room : rooms)
        {
            String canonical = canonicalPointRoom(room.name);
            if (canonical != null)
            {
                found.add(canonical);
            }
        }
        return found.containsAll(FULL_LAYOUT_ROOM_ORDER);
    }

    static void orderFullLayoutRooms(List<CoxMegascaleState.ScoutedRoom> rooms)
    {
        rooms.sort((left, right) -> Integer.compare(fullLayoutOrder(left.name), fullLayoutOrder(right.name)));
    }

    private static int fullLayoutOrder(String roomName)
    {
        String canonical = canonicalPointRoom(roomName);
        int index = FULL_LAYOUT_ROOM_ORDER.indexOf(canonical);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private Raid buildRaidFromInstance()
    {
        // Mirror RuneLite's public raid-grid model from scene/template data for
        // Challenge Mode, which does not emit RaidScouted.
        Point lobbyBase = findLobbyBase();
        if (lobbyBase == null)
        {
            return null;
        }

        Integer lobbyIndex = findLobbyIndex(lobbyBase);
        if (lobbyIndex == null)
        {
            return null;
        }

        Raid raid = new Raid(new WorldPoint(client.getBaseX() + lobbyBase.getX(), client.getBaseY() + lobbyBase.getY(), COX_LOBBY_PLANE), lobbyIndex);
        int baseX = lobbyIndex % COX_ROOMS_PER_ROW;
        int baseY = lobbyIndex % COX_ROOMS_PER_PLANE > COX_ROOMS_PER_ROW - 1 ? 1 : 0;
        Tile[][][] tiles = client.getScene().getTiles();
        int[][][] chunks = client.getInstanceTemplateChunks();
        if (tiles == null || chunks == null)
        {
            return raid;
        }

        for (int index = 0; index < 16; index++)
        {
            int roomX = index % COX_ROOMS_PER_ROW;
            int roomY = index % COX_ROOMS_PER_PLANE > COX_ROOMS_PER_ROW - 1 ? 1 : 0;
            int plane = index > COX_ROOMS_PER_PLANE - 1 ? COX_SECOND_FLOOR_PLANE : COX_LOBBY_PLANE;
            int sceneX = raid.getGridBase().getX() + (roomX - baseX) * COX_ROOM_SIZE - client.getBaseX();
            int sceneY = raid.getGridBase().getY() - (roomY - baseY) * COX_ROOM_SIZE - client.getBaseY();
            if (sceneX < 1 - COX_ROOM_SIZE || sceneX >= tiles[plane].length)
            {
                continue;
            }
            if (sceneX < 1)
            {
                sceneX = 1;
            }
            if (sceneY < 1)
            {
                sceneY = 1;
            }
            if (sceneY >= tiles[plane][sceneX].length)
            {
                continue;
            }
            Tile tile = tiles[plane][sceneX][sceneY];
            if (tile != null)
            {
                raid.setRoom(determineRaidRoom(tile, chunks), index);
            }
        }
        return raid;
    }

    private Point findLobbyBase()
    {
        Tile[][][] tiles = client.getScene().getTiles();
        if (tiles == null || tiles.length <= COX_LOBBY_PLANE)
        {
            return null;
        }
        for (Tile[] column : tiles[COX_LOBBY_PLANE])
        {
            for (Tile tile : column)
            {
                if (tile != null && tile.getWallObject() != null && tile.getWallObject().getId() == ObjectID.BLACK_WALL)
                {
                    return tile.getSceneLocation();
                }
            }
        }
        return null;
    }

    private Integer findLobbyIndex(Point lobbyBase)
    {
        if (lobbyBase == null)
        {
            return null;
        }
        Tile[][][] tiles = client.getScene().getTiles();
        if (tiles == null || tiles.length <= COX_LOBBY_PLANE
            || lobbyBase.getX() + COX_ROOM_SIZE >= tiles[COX_LOBBY_PLANE].length
            || lobbyBase.getY() + COX_ROOM_SIZE >= tiles[COX_LOBBY_PLANE][lobbyBase.getX()].length)
        {
            return null;
        }
        Tile[][] lobbyTiles = tiles[COX_LOBBY_PLANE];
        int lobbyX = lobbyBase.getX();
        int lobbyY = lobbyBase.getY();
        int y = lobbyTiles[lobbyX][lobbyY + COX_ROOM_SIZE] == null ? 0 : 1;
        int x;
        if (lobbyTiles[lobbyX + COX_ROOM_SIZE][lobbyY] == null)
        {
            x = COX_ROOMS_PER_ROW - 1;
        }
        else
        {
            for (x = 0; x < COX_ROOMS_PER_ROW - 1; x++)
            {
                int westX = lobbyX - 1 - COX_ROOM_SIZE * x;
                if (westX < 0 || lobbyTiles[westX][lobbyY] == null)
                {
                    break;
                }
            }
        }
        return x + y * COX_ROOMS_PER_ROW;
    }

    private net.runelite.client.plugins.raids.RaidRoom determineRaidRoom(Tile tile, int[][][] chunks)
    {
        Point location = tile.getSceneLocation();
        int chunkData = chunks[tile.getPlane()][location.getX() / 8][location.getY() / 8];
        InstanceTemplates template = InstanceTemplates.findMatch(chunkData);
        return determineRaidRoom(template);
    }

    private net.runelite.client.plugins.raids.RaidRoom determineRaidRoom(InstanceTemplates template)
    {
        if (template == null)
        {
            return net.runelite.client.plugins.raids.RaidRoom.EMPTY;
        }
        switch (template)
        {
            case RAIDS_LOBBY:
            case RAIDS_START:
                return net.runelite.client.plugins.raids.RaidRoom.START;
            case RAIDS_END:
                return net.runelite.client.plugins.raids.RaidRoom.END;
            case RAIDS_SCAVENGERS:
            case RAIDS_SCAVENGERS2:
                return net.runelite.client.plugins.raids.RaidRoom.SCAVENGERS;
            case RAIDS_SHAMANS:
                return net.runelite.client.plugins.raids.RaidRoom.SHAMANS;
            case RAIDS_VASA:
                return net.runelite.client.plugins.raids.RaidRoom.VASA;
            case RAIDS_VANGUARDS:
                return net.runelite.client.plugins.raids.RaidRoom.VANGUARDS;
            case RAIDS_ICE_DEMON:
                return net.runelite.client.plugins.raids.RaidRoom.ICE_DEMON;
            case RAIDS_THIEVING:
                return net.runelite.client.plugins.raids.RaidRoom.THIEVING;
            case RAIDS_FARMING:
            case RAIDS_FARMING2:
                return net.runelite.client.plugins.raids.RaidRoom.FARMING;
            case RAIDS_MUTTADILES:
                return net.runelite.client.plugins.raids.RaidRoom.MUTTADILES;
            case RAIDS_MYSTICS:
                return net.runelite.client.plugins.raids.RaidRoom.MYSTICS;
            case RAIDS_TEKTON:
                return net.runelite.client.plugins.raids.RaidRoom.TEKTON;
            case RAIDS_TIGHTROPE:
                return net.runelite.client.plugins.raids.RaidRoom.TIGHTROPE;
            case RAIDS_GUARDIANS:
                return net.runelite.client.plugins.raids.RaidRoom.GUARDIANS;
            case RAIDS_CRABS:
                return net.runelite.client.plugins.raids.RaidRoom.CRABS;
            case RAIDS_VESPULA:
                return net.runelite.client.plugins.raids.RaidRoom.VESPULA;
            default:
                return net.runelite.client.plugins.raids.RaidRoom.EMPTY;
        }
    }

    private List<net.runelite.client.plugins.raids.RaidRoom> discoverRaidRoomsFromInstanceTemplates()
    {
        List<net.runelite.client.plugins.raids.RaidRoom> discovered = new ArrayList<>();
        int[][][] chunks = client.getInstanceTemplateChunks();
        if (chunks == null)
        {
            return discovered;
        }
        for (int[][] plane : chunks)
        {
            for (int[] column : plane)
            {
                for (int chunk : column)
                {
                    net.runelite.client.plugins.raids.RaidRoom room = determineRaidRoom(InstanceTemplates.findMatch(chunk));
                    if ((room.getType() == RoomType.COMBAT || room.getType() == RoomType.PUZZLE) && !discovered.contains(room))
                    {
                        discovered.add(room);
                    }
                }
            }
        }
        return discovered;
    }

    private List<CoxMegascaleState.ScoutedRoom> fullLayoutScoutedRooms()
    {
        List<CoxMegascaleState.ScoutedRoom> rooms = new ArrayList<>();
        for (String roomName : FULL_LAYOUT_ROOM_ORDER)
        {
            String type = "Crabs".equals(roomName) || "Ice Demon".equals(roomName)
                || "Thieving".equals(roomName) || "Tightrope".equals(roomName) ? "Puzzle" : "Combat";
            rooms.add(new CoxMegascaleState.ScoutedRoom(type, roomName));
        }
        return rooms;
    }

    private boolean hasFullLayoutTemplates()
    {
        List<CoxMegascaleState.ScoutedRoom> rooms = new ArrayList<>();
        for (net.runelite.client.plugins.raids.RaidRoom room : discoverRaidRoomsFromInstanceTemplates())
        {
            rooms.add(new CoxMegascaleState.ScoutedRoom(room.getType().getName(), room.getName()));
        }
        return isFullLayout(rooms);
    }

    private void applyRaidScout(Raid raid)
    {
        // Normalize both ordered solver layouts and flat room collections into
        // the same state representation consumed by detection, points, and UI.
        scoutedRaid = raid;
        state.setPrepInfoBoxesHidden(false);
        List<com.coxmegascale.detect.RaidRoom> rooms = new ArrayList<>();
        List<CoxMegascaleState.ScoutedRoom> scoutedRooms = new ArrayList<>();

        String layoutCode = raid.getLayout() == null
            ? ("challenge".equals(raidMode) ? "Challenge Mode" : "Unknown")
            : raid.getLayout().toCodeString();
        if (raid.getLayout() != null)
        {
            for (net.runelite.client.plugins.raids.solver.Room layoutRoom : raid.getLayout().getRooms())
            {
                net.runelite.client.plugins.raids.RaidRoom room = raid.getRoom(layoutRoom.getPosition());
                if (room == null)
                {
                    scoutedRooms.add(new CoxMegascaleState.ScoutedRoom(roomTypeFromSymbol(layoutRoom.getSymbol()), "Unknown"));
                    continue;
                }

                if (room.getType() == RoomType.COMBAT || room.getType() == RoomType.PUZZLE)
                {
                    scoutedRooms.add(new CoxMegascaleState.ScoutedRoom(room.getType().getName(), room.getName()));
                }

                com.coxmegascale.detect.RaidRoom mapped = mapRaidRoom(room);
                if (mapped != null && !rooms.contains(mapped))
                {
                    rooms.add(mapped);
                }
            }
        }
        else
        {
            for (net.runelite.client.plugins.raids.RaidRoom room : raid.getRooms())
            {
                if (room == null)
                {
                    continue;
                }

                if (room.getType() == RoomType.COMBAT || room.getType() == RoomType.PUZZLE)
                {
                    scoutedRooms.add(new CoxMegascaleState.ScoutedRoom(room.getType().getName(), room.getName()));
                }

                com.coxmegascale.detect.RaidRoom mapped = mapRaidRoom(room);
                if (mapped != null && !rooms.contains(mapped))
                {
                    rooms.add(mapped);
                }
            }
        }

        boolean fullLayout = "challenge".equals(raidMode) || fullLayoutAnnounced || hasFullLayoutTemplates();
        if ("challenge".equals(raidMode) || fullLayout)
        {
            scoutedRooms = fullLayoutScoutedRooms();
        }
        if (fullLayout && "normal".equals(raidMode))
        {
            layoutCode = "Full";
        }

        state.setScoutedLayout(layoutCode, scoutedRooms);
        updateInfoBoxVisibility();
        if (state.isPerfectLayout() && !perfectLayoutNotified)
        {
            perfectLayoutNotified = true;
            notifier.notify("Perfect CoX layout found: " + layoutCode);
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "CoX Assistant: Perfect layout found: " + layoutCode, null);
        }

        detector.setDetectedRooms(rooms);
        refreshPanel();
    }

    private void refreshScoutedRaid()
    {
        int tick = client.getTickCount();
        if (!manualRefreshAllowed(tick, lastManualRefreshTick))
        {
            return;
        }
        lastManualRefreshTick = tick;
        if (scoutedRaid != null)
        {
            applyRaidScout(scoutedRaid);
        }
        else
        {
            refreshPanel();
        }
    }

    static boolean manualRefreshAllowed(int currentTick, int lastRefreshTick)
    {
        return lastRefreshTick == Integer.MIN_VALUE || currentTick < lastRefreshTick || currentTick - lastRefreshTick >= 100;
    }

    @Subscribe
    public void onRaidReset(RaidReset event)
    {
        boolean inCoxRaid = client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1;
        if (shouldPreservePointFixtureOnRaidReset(inCoxRaid))
        {
            // RuneLite also emits RaidReset when its Raids plugin reloads. Preserve
            // active raid tracking and the single fixture file across that rescout.
            scoutedRaid = null;
            detector.clear();
            refreshPanel();
            return;
        }
        if (pointFixtureLogger != null)
        {
            pointFixtureLogger.endSession("raid_exit");
        }
        int retainedRaidPartyId = currentCoxRaidPartyId();
        resetRaidSessionState();
        if (inCoxRaid && retainedRaidPartyId >= 0)
        {
            previousInCoxRaid = true;
            previousRaidPartyId = retainedRaidPartyId;
            activeRaidPartyId = retainedRaidPartyId;
        }
        clearPendingMysticsSpec();
        shareTeamUpdate();
        perfectLayoutNotified = false;
        refreshPanel();
    }

    static boolean shouldPreservePointFixtureOnRaidReset(boolean inCoxRaid)
    {
        return inCoxRaid;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        String message = stripTags(event.getMessage());
        String lowerMessage = message.toLowerCase(java.util.Locale.US);
        if ("normal".equals(raidMode) && lowerMessage.contains("full")
            && (lowerMessage.contains("layout") || lowerMessage.contains("raid")))
        {
            fullLayoutAnnounced = true;
            if (scoutedRaid != null)
            {
                applyRaidScout(scoutedRaid);
            }
        }
        if (message.startsWith(RAID_START_MESSAGE))
        {
            if (pointFixtureLogger != null)
            {
                pointFixtureLogger.endSession("new_raid");
            }
            fixtureClosedForCurrentRaid = false;
            fixtureStartReason = "raid_start";
            fixturePointObservation = null;
            fixturePointObservationExpiryTick = Integer.MIN_VALUE;
            updateRaidModeAndLayout();
            raidStartedAtMillis = System.currentTimeMillis();
            raidSummaryWritten = false;
            raidParticipantNames.clear();
            lastRaidRosterScanTick = Integer.MIN_VALUE;
            observeRaidParticipants(true);
            state.resetPointTracking();
            state.resetResourceTracking();
            discoveredSharedStorageContainerId = -1;
            clearPendingMysticsSpec();
            shareTeamUpdate();
            refreshPanel();
        }
        else if (message.startsWith(RAID_COMPLETE_MESSAGE))
        {
            int scale = lastDetectedScale == null ? config.defaultScale() : lastDetectedScale;
            int groupPoints = client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSCORE);
            int personalPoints = client.getVarpValue(VarPlayerID.RAIDS_PLAYERSCORE);
            state.setCurrentPoints(personalPoints, groupPoints);
            state.closeActiveRoom();
            observeRaidParticipants(true);
            writeRaidSummary(scale, groupPoints, personalPoints);
            if (pointFixtureLogger != null)
            {
                pointFixtureLogger.raidComplete(groupPoints, personalPoints);
            }
            fixtureClosedForCurrentRaid = true;
            refreshPanel();
        }
    }

    private void ensureRaidStartTimestamp()
    {
        if (raidStartedAtMillis == 0 && client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1)
        {
            raidStartedAtMillis = System.currentTimeMillis();
            raidSummaryWritten = false;
        }
    }

    private void writeRaidSummary(int scale, int groupPoints, int personalPoints)
    {
        if (raidSummaryLogger == null || raidSummaryWritten)
        {
            return;
        }
        raidSummaryWritten = true;
        Instant completedAt = Instant.now();
        Instant startedAt = raidStartedAtMillis <= 0 ? completedAt : Instant.ofEpochMilli(raidStartedAtMillis);
        com.coxmegascale.calc.RaidMath raidMath = new com.coxmegascale.calc.RaidMath();

        LinkedHashSet<String> participants = new LinkedHashSet<>(raidParticipantNames);
        for (CoxMegascaleState.TeamMember member : state.getTeamMembers())
        {
            if (member.hasCoxData && member.displayName != null && !"Unknown".equals(member.displayName))
            {
                participants.add(member.displayName);
            }
        }
        Actor localPlayer = client.getLocalPlayer();
        if (localPlayer != null && localPlayer.getName() != null)
        {
            participants.add(localPlayer.getName());
        }

        List<String> scoutedRooms = new ArrayList<>();
        for (CoxMegascaleState.ScoutedRoom room : state.getScoutedRooms())
        {
            scoutedRooms.add(room.name);
        }
        List<RaidSummaryLogger.Room> rooms = new ArrayList<>();
        for (CoxMegascaleState.TrackedRoomPoints room : state.getTrackedRoomPoints())
        {
            rooms.add(new RaidSummaryLogger.Room(room.room, room.durationMillis, room.groupPoints, room.personalPoints));
        }
        List<RaidSummaryLogger.Death> deaths = new ArrayList<>();
        for (CoxMegascaleState.DeathPointLoss death : state.getDeathPointLosses())
        {
            deaths.add(new RaidSummaryLogger.Death(death.room, death.pointsLost));
        }

        raidSummaryLogger.write(new RaidSummaryLogger.Snapshot(
            startedAt,
            completedAt,
            fixtureRaidMode(),
            state.getLayoutCode(),
            scale,
            state.getOlmKillers(),
            new ArrayList<>(participants),
            groupPoints,
            personalPoints,
            raidMath.calculatePurpleChance(Math.max(0, groupPoints)),
            raidMath.calculatePurpleChance(Math.max(0, personalPoints)),
            state.getTotalDeathPointLoss(),
            state.getTeamOverloadPoints(),
            state.getTeamFishPoints(),
            scoutedRooms,
            rooms,
            deaths));
    }

    private void observeRaidParticipants(boolean force)
    {
        if (client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) != 1)
        {
            return;
        }

        Actor localPlayer = client.getLocalPlayer();
        if (localPlayer != null)
        {
            addRaidParticipantName(localPlayer.getName());
        }

        int expectedPartySize = Math.max(1, client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE));
        if (!force && raidParticipantNames.size() >= expectedPartySize)
        {
            return;
        }
        int tick = client.getTickCount();
        if (!force && lastRaidRosterScanTick != Integer.MIN_VALUE && tick >= lastRaidRosterScanTick
            && tick - lastRaidRosterScanTick < 10)
        {
            return;
        }
        lastRaidRosterScanTick = tick;

        Widget list = client.getWidget(InterfaceID.RaidsSidepanel.LIST);
        collectRaidParticipantNames(list);
    }

    private void collectRaidParticipantNames(Widget widget)
    {
        if (widget == null)
        {
            return;
        }
        addRaidParticipantName(widget.getText());
        collectRaidParticipantNames(widget.getDynamicChildren());
        collectRaidParticipantNames(widget.getStaticChildren());
        collectRaidParticipantNames(widget.getNestedChildren());
    }

    private void collectRaidParticipantNames(Widget[] widgets)
    {
        if (widgets == null)
        {
            return;
        }
        for (Widget widget : widgets)
        {
            collectRaidParticipantNames(widget);
        }
    }

    private void addRaidParticipantName(String rawText)
    {
        String name = raidParticipantName(rawText);
        if (name != null)
        {
            raidParticipantNames.add(name);
        }
    }

    static String raidParticipantName(String rawText)
    {
        if (rawText == null || rawText.isEmpty())
        {
            return null;
        }
        String[] lines = rawText.replace("<br>", "\n").replace("<br/>", "\n").split("\n");
        for (String line : lines)
        {
            String name = Text.removeTags(line).replace('\u00a0', ' ').trim().replaceAll("\\s+", " ");
            if (isRaidParticipantName(name))
            {
                return name;
            }
        }
        return null;
    }

    private static boolean isRaidParticipantName(String name)
    {
        if (name.isEmpty() || name.length() > 12)
        {
            return false;
        }
        boolean hasLetter = false;
        for (int i = 0; i < name.length(); i++)
        {
            char c = name.charAt(i);
            if (Character.isLetter(c))
            {
                hasLetter = true;
            }
            else if (!Character.isDigit(c) && c != ' ' && c != '-' && c != '_')
            {
                return false;
            }
        }
        if (!hasLetter)
        {
            return false;
        }
        String lower = name.toLowerCase(java.util.Locale.US);
        return !"loading".equals(lower) && !"name".equals(lower) && !"combat".equals(lower)
            && !"total".equals(lower) && !"start raid".equals(lower) && !"refresh".equals(lower);
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        String name = event.getNpc().getName();
        if (isOlmName(name))
        {
            state.setPrepInfoBoxesHidden(true);
        }
        if (isGreatOlmHead(event.getNpc()))
        {
            state.observeOlmHeadSpawned();
        }
        if (detector.observeName(name))
        {
            refreshPanel();
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        ObjectComposition object = client.getObjectDefinition(event.getGameObject().getId());
        if (object == null)
        {
            return;
        }

        if (detector.observeName(object.getName()))
        {
            refreshPanel();
        }
    }

    @Provides
    CoxMegascaleConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(CoxMegascaleConfig.class);
    }

    @Provides
    CoxMegascaleState provideState()
    {
        return state;
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        String option = event.getMenuOption();
        if ("Drink".equalsIgnoreCase(option) && (CoxMegascaleState.isOverloadConsumable(event.getItemId())
            || CoxMegascaleState.isNonOverloadConsumable(event.getItemId())))
        {
            queueConsumableConfirmation(event.getItemId(), false);
        }
        else if ("Eat".equalsIgnoreCase(option) && CoxMegascaleState.isFishConsumable(event.getItemId()))
        {
            queueConsumableConfirmation(event.getItemId(), true);
        }
        else if (option != null && option.toLowerCase(java.util.Locale.US).startsWith("make"))
        {
            state.notePotionCraftAction();
        }

    }

    private void queueConsumableConfirmation(int itemId, boolean fish)
    {
        // A click alone can be cancelled. Record the pre-click dose/unit count
        // and wait for a matching inventory reduction before awarding progress.
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        int beforeUnits = CoxMegascaleState.consumableUnits(inventory, itemId);
        if (beforeUnits > 0)
        {
            pendingConsumable = new PendingConsumable(itemId, beforeUnits, fish, client.getTickCount() + 2);
        }
    }

    private boolean confirmPendingConsumable(ItemContainer inventory)
    {
        if (pendingConsumable == null)
        {
            return false;
        }
        int afterUnits = CoxMegascaleState.consumableUnits(inventory, pendingConsumable.itemId);
        if (afterUnits >= pendingConsumable.beforeUnits)
        {
            return false;
        }
        PendingConsumable confirmed = pendingConsumable;
        pendingConsumable = null;
        if (confirmed.fish)
        {
            boolean recorded = state.recordFishEaten(confirmed.itemId);
            if (recorded)
            {
                noteFixturePointObservation("observed_fish_consumption");
            }
            return recorded;
        }
        if (CoxMegascaleState.isOverloadConsumable(confirmed.itemId))
        {
            boolean recorded = state.recordOverloadSip();
            if (recorded)
            {
                noteFixturePointObservation("observed_overload_consumption");
            }
            return recorded;
        }
        boolean recorded = state.recordNonOverloadSip(confirmed.itemId);
        if (recorded)
        {
            noteFixturePointObservation("observed_potion_consumption");
        }
        return recorded;
    }

    private void noteFixturePointObservation(String source)
    {
        fixturePointObservation = source;
        fixturePointObservationExpiryTick = client.getTickCount() + 2;
    }

    private boolean syncTeamMembers()
    {
        if (!partyService.isInParty())
        {
            if (!state.hasTeamMembers())
            {
                return false;
            }
            state.clearTeamMembers();
            return true;
        }
        return state.syncTeamMembers(partyService.getMembers());
    }

    private void shareTeamUpdate()
    {
        shareTeamUpdate(false);
    }

    private void shareTeamUpdate(boolean requestRosterSnapshot)
    {
        if (!partyService.isInParty())
        {
            return;
        }

        PartyMember localMember = partyService.getLocalMember();
        if (localMember == null)
        {
            return;
        }

        Actor localPlayer = client.getLocalPlayer();
        String displayName = localPlayer == null ? localMember.getDisplayName() : localPlayer.getName();
        boolean inCoxRaid = client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1;
        int raidPartyId = currentCoxRaidPartyId();
        // Outside a raid send only an empty versioned presence payload; names and
        // gameplay-derived fields are retained locally rather than broadcast.
        if (!inCoxRaid || raidPartyId == -1)
        {
            state.clearTeamMemberCoxData(localMember.getMemberId(), displayName);
            partyService.send(emptyCoxPresenceUpdate());
            return;
        }
        List<CoxTeamUpdate.Death> deaths = new ArrayList<>();
        for (CoxMegascaleState.TeamDeath death : state.getLocalTeamDeaths())
        {
            deaths.add(new CoxTeamUpdate.Death(death.room, death.pointsLost, death.occurredAtMillis));
        }

        int thievingLevel = client.getRealSkillLevel(Skill.THIEVING);
        // Relay shared storage only from the client that directly observed the
        // current snapshot, preventing received messages from echoing forever.
        boolean ownsSharedStorageSnapshot = locallyObservedSharedStorageAtMillis > 0
            && locallyObservedSharedStorageAtMillis == state.getSharedStorageObservedAtMillis();
        CoxTeamUpdate update = new CoxTeamUpdate(displayName, state.getCurrentPersonalPoints(), state.getActiveRoomName(), deaths,
            state.getLocalOverloadSips(), state.getLocalNonOverloadSips(), state.getLocalFishEaten(), inCoxRaid, raidPartyId, thievingLevel,
            state.getLocalPrepResources(), state.getLocalCraftedPotions(), ownsSharedStorageSnapshot ? state.getSharedStorage() : null,
            ownsSharedStorageSnapshot ? state.getSharedStorageObservedAtMillis() : 0,
            requestRosterSnapshot);
        if (isSameCoxRaid(update))
        {
            state.updateTeamMember(localMember.getMemberId(), displayName, update.personalPoints, update.activeRoom, update.deaths,
                update.overloadSips, update.nonOverloadSips, update.fishEaten, update.thievingLevel, update.prepResources, update.craftedPotions);
        }
        else
        {
            state.clearTeamMemberCoxData(localMember.getMemberId(), displayName);
        }
        partyService.send(update);
    }

    static CoxTeamUpdate emptyCoxPresenceUpdate()
    {
        return new CoxTeamUpdate(null, 0, null, java.util.Collections.emptyList(), 0, 0,
            java.util.Collections.emptyList(), false, -1, 0, java.util.Collections.emptyList(),
            java.util.Collections.emptyList(), java.util.Collections.emptyList(), 0, false);
    }

    private boolean shouldRespondToRosterRequest(long memberId)
    {
        // Rate-limit each sender and bound the deduplication map against abusive
        // or malformed request bursts.
        int tick = client.getTickCount();
        Integer lastResponseTick = lastRosterSnapshotResponseTick.get(memberId);
        if (!rosterSnapshotRequestAllowed(tick, lastResponseTick))
        {
            return false;
        }
        if (lastRosterSnapshotResponseTick.size() >= 128)
        {
            lastRosterSnapshotResponseTick.clear();
        }
        lastRosterSnapshotResponseTick.put(memberId, tick);
        return true;
    }

    static boolean rosterSnapshotRequestAllowed(int tick, Integer lastResponseTick)
    {
        return lastResponseTick == null || tick < lastResponseTick || tick - lastResponseTick >= 10;
    }

    static boolean compatiblePartySchema(int receivedVersion, int currentVersion)
    {
        return receivedVersion == currentVersion;
    }

    private boolean isSameCoxRaid(CoxTeamUpdate update)
    {
        return isSameCoxRaid(update.inCoxRaid, update.raidPartyId);
    }

    private boolean isSameCoxRaid(boolean inCoxRaid, int raidPartyId)
    {
        return inCoxRaid
            && raidPartyId != -1
            && client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1
            && raidPartyId == currentCoxRaidPartyId();
    }

    private void refreshPanel()
    {
        if (panel != null)
        {
            panel.setDetectedRooms(detector.snapshot());
        }
    }

    private boolean shouldShowInfoBoxes()
    {
        return infoBoxesGloballyVisible;
    }

    private void updateInfoBoxVisibility()
    {
        infoBoxesGloballyVisible = shouldShowInfoBoxes(infoBoxesEnabled,
            client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1,
            scoutedRaid != null && !state.getScoutedRooms().isEmpty());
    }

    static boolean shouldShowInfoBoxes(boolean enabled, boolean inCoxRaid, boolean hasScoutedLayout)
    {
        return enabled && inCoxRaid && hasScoutedLayout;
    }

    private void addResourceInfoBoxes()
    {
        removeResourceInfoBoxes();
        for (CoxMegascaleState.Resource resource : state.getResources())
        {
            CoxMegascaleResourceInfoBox infoBox = new CoxMegascaleResourceInfoBox(
                itemManager.getImage(resource.cleanItemId),
                this,
                state,
                resource,
                this::shouldShowInfoBoxes
            );
            resourceInfoBoxes.add(infoBox);
            infoBoxManager.addInfoBox(infoBox);
        }
    }

    private void addMysticsSpecInfoBoxes()
    {
        removeMysticsSpecInfoBoxes();
        addMysticsSpecInfoBox("Elder Maul", ItemID.ELDER_MAUL);
        addMysticsSpecInfoBox("Ralos", ItemID.TONALZTICS_OF_RALOS);
        addMysticsSpecInfoBox("Emberlight", ItemID.EMBERLIGHT);
        addMysticsSpecInfoBox("BGS", ItemID.BANDOS_GODSWORD);
    }

    private void addMysticsSpecInfoBox(String weapon, int itemId)
    {
        CoxMegascaleMysticsSpecInfoBox infoBox = new CoxMegascaleMysticsSpecInfoBox(
            itemManager.getImage(itemId), this, state, weapon, this::shouldShowInfoBoxes);
        mysticsSpecInfoBoxes.add(infoBox);
        infoBoxManager.addInfoBox(infoBox);
    }

    private void removeMysticsSpecInfoBoxes()
    {
        for (CoxMegascaleMysticsSpecInfoBox infoBox : mysticsSpecInfoBoxes)
        {
            infoBoxManager.removeInfoBox(infoBox);
        }
        mysticsSpecInfoBoxes.clear();
    }

    private void queueDefenceSpec(int specialTick)
    {
        pendingMysticsSpec = null;
        pendingMysticsHits.clear();
        if (client.getLocalPlayer() == null)
        {
            return;
        }

        Actor interacting = client.getLocalPlayer().getInteracting();
        if (!(interacting instanceof NPC))
        {
            return;
        }

        String targetKey = defenceTargetKey((NPC) interacting);
        if (targetKey == null)
        {
            return;
        }

        String weapon = equippedMysticsWeapon();
        String profileKey = CoxMegascaleState.defenceProfileKey(targetKey);
        if (weapon == null || (!"Elder Maul".equals(weapon) && !"DWH".equals(weapon) && !"Ralos".equals(weapon) && !"BGS".equals(weapon) && !"Eye of Ayak".equals(weapon)
            && !("mystics".equals(profileKey) && "Emberlight".equals(weapon))))
        {
            return;
        }
        // Weapon-specific travel time and expiry windows associate only the
        // resulting local hitsplats with this observed special-energy use.
        int delay = specHitsplatDelay(weapon);
        int hitsplatTick = specialTick + delay;
        state.activateDefenceTarget(targetKey);
        pendingMysticsSpec = new PendingMysticsSpec(targetKey, weapon, (NPC) interacting, hitsplatTick,
            hitsplatTick + specHitsplatExpiryDelay(weapon));
    }

    private String defenceTargetKey(NPC target)
    {
        // Ordinary bosses use profile plus NPC index; Olm adds encounter/phase
        // identity in state so reused NPC indices cannot inherit stale drains.
        String targetName = target.getName();
        if (targetName == null)
        {
            return null;
        }
        String name = targetName.toLowerCase(java.util.Locale.US);
        if (state.isActiveRoom("Mystics") && "skeletal mystic".equals(name))
        {
            return indexedDefenceTargetKey("mystics", target);
        }
        if (state.isActiveRoom("Shamans") && name.contains("lizardman shaman"))
        {
            return indexedDefenceTargetKey("shamans", target);
        }
        if (state.isActiveRoom("Ice Demon") && name.contains("ice demon"))
        {
            return indexedDefenceTargetKey("iceDemon", target);
        }
        if (state.isActiveRoom("Tightrope"))
        {
            if (name.contains("deathly mage"))
            {
                return indexedDefenceTargetKey("ropeMager", target);
            }
            if (name.contains("deathly ranger"))
            {
                return indexedDefenceTargetKey("ropeRanger", target);
            }
        }
        if (state.isActiveRoom("Vespula") && name.contains("abyssal portal"))
        {
            return indexedDefenceTargetKey("abyssalPortal", target);
        }
        if (state.isActiveRoom("Tekton") && name.contains("tekton"))
        {
            return indexedDefenceTargetKey("tekton", target);
        }
        if (state.isActiveRoom("Vasa") && (name.contains("vasa") || name.contains("glowing crystal")))
        {
            return indexedDefenceTargetKey("vasa", target);
        }
        if (state.isActiveRoom("Olm"))
        {
            switch (target.getId())
            {
                case NpcID.GREAT_OLM:
                case NpcID.GREAT_OLM_7554:
                    return state.olmDefenceTargetKey("olmHead");
                case NpcID.GREAT_OLM_RIGHT_CLAW:
                case NpcID.GREAT_OLM_RIGHT_CLAW_7553:
                    return state.olmDefenceTargetKey("mageHand");
                case NpcID.GREAT_OLM_LEFT_CLAW:
                case NpcID.GREAT_OLM_LEFT_CLAW_7555:
                    return state.olmDefenceTargetKey("meleeHand");
                default:
                    return null;
            }
        }
        return null;
    }

    private static String indexedDefenceTargetKey(String profileKey, NPC target)
    {
        return profileKey + ":" + target.getIndex();
    }

    private String equippedMysticsWeapon()
    {
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment == null)
        {
            return null;
        }
        Item weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
        if (weapon == null)
        {
            return null;
        }
        switch (weapon.getId())
        {
            case ItemID.DRAGON_WARHAMMER:
                return "DWH";
            case ItemID.ELDER_MAUL:
            case ItemID.ELDER_MAUL_21205:
            case ItemID.ELDER_MAUL_OR:
                return "Elder Maul";
            case ItemID.TONALZTICS_OF_RALOS:
                return "Ralos";
            case ItemID.EMBERLIGHT:
                return "Emberlight";
            case ItemID.EYE_OF_AYAK:
                return "Eye of Ayak";
            case ItemID.BANDOS_GODSWORD:
            case ItemID.BANDOS_GODSWORD_OR:
            case ItemID.BANDOS_GODSWORD_20782:
            case ItemID.BANDOS_GODSWORD_21060:
                return "BGS";
            default:
                return null;
        }
    }

    private int confirmedMysticsSpecAmount()
    {
        if (pendingMysticsHits.isEmpty())
        {
            return 0;
        }
        return confirmedSpecAmount(pendingMysticsSpec.weapon, pendingMysticsHits);
    }

    static int confirmedSpecAmount(String weapon, List<Integer> hits)
    {
        if (weapon == null || hits == null || hits.isEmpty())
        {
            return 0;
        }
        if ("Ralos".equals(weapon))
        {
            // Ralos projectiles roll independently, so count positive hits rather
            // than summing their damage values.
            int successfulHits = 0;
            for (int hit : hits)
            {
                if (hit > 0)
                {
                    successfulHits++;
                }
            }
            return successfulHits;
        }
        int hit = hits.get(hits.size() - 1);
        if ("BGS".equals(weapon) || "Eye of Ayak".equals(weapon))
        {
            return Math.max(0, hit);
        }
        return hit > 0 ? 1 : 0;
    }

    static int specHitsplatDelay(String weapon)
    {
        return "Ralos".equals(weapon) || "Elder Maul".equals(weapon) ? 2 : 1;
    }

    static int specHitsplatExpiryDelay(String weapon)
    {
        if ("Eye of Ayak".equals(weapon))
        {
            return 3;
        }
        return "Ralos".equals(weapon) ? 1 : 0;
    }

    private void shareDefenceSpecHit(String targetKey, String weapon, int amount)
    {
        if (partyService.isInParty())
        {
            partyService.send(new MysticsSpecUpdate(targetKey, weapon, amount,
                client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1,
                currentCoxRaidPartyId()));
        }
    }

    private void clearPendingMysticsSpec()
    {
        pendingMysticsSpec = null;
        pendingMysticsHits.clear();
    }

    static boolean acceptsPendingSpecHitsplat(int currentTick, int hitsplatTick, int expiryTick)
    {
        return currentTick >= hitsplatTick && currentTick <= expiryTick;
    }

    static boolean shouldClearTeamCoxData(boolean wasInCoxRaid, boolean isInCoxRaid)
    {
        return wasInCoxRaid && !isInCoxRaid;
    }

    static boolean shouldRequestTeamSnapshot(boolean membershipChanged, boolean isInCoxRaid)
    {
        return membershipChanged && isInCoxRaid;
    }

    static boolean shouldWaitForDelayedSpecHit(int currentTick, int hitsplatTick, int expiryTick, boolean hasNoHits)
    {
        return hasNoHits && currentTick >= hitsplatTick && currentTick < expiryTick;
    }

    private boolean updateLocalRaidMembership()
    {
        boolean inCoxRaid = client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1;
        int rawRaidPartyId = client.getVarpValue(VarPlayerID.RAIDS_PARTY_GROUPHOLDER);
        if (rawRaidPartyId != -1)
        {
            activeRaidPartyId = rawRaidPartyId;
        }
        else if (!inCoxRaid)
        {
            activeRaidPartyId = -1;
        }
        int raidPartyId = effectiveCoxRaidPartyId(rawRaidPartyId, inCoxRaid, activeRaidPartyId);
        boolean changed = inCoxRaid != previousInCoxRaid || raidPartyId != previousRaidPartyId;
        previousInCoxRaid = inCoxRaid;
        previousRaidPartyId = raidPartyId;
        return changed;
    }

    private int currentCoxRaidPartyId()
    {
        int rawRaidPartyId = client.getVarpValue(VarPlayerID.RAIDS_PARTY_GROUPHOLDER);
        return effectiveCoxRaidPartyId(rawRaidPartyId, client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1, activeRaidPartyId);
    }

    static int effectiveCoxRaidPartyId(int rawRaidPartyId, boolean inCoxRaid, int activeRaidPartyId)
    {
        if (rawRaidPartyId != -1)
        {
            return rawRaidPartyId;
        }
        return inCoxRaid ? activeRaidPartyId : -1;
    }

    private void resetLocalRaidMembership()
    {
        previousInCoxRaid = false;
        previousRaidPartyId = Integer.MIN_VALUE;
        activeRaidPartyId = -1;
        discoveredSharedStorageContainerId = -1;
        locallyObservedSharedStorageAtMillis = 0;
    }

    private static class PendingMysticsSpec
    {
        private final String targetKey;
        private final String weapon;
        private final NPC target;
        private final int hitsplatTick;
        private final int expiryTick;

        private PendingMysticsSpec(String targetKey, String weapon, NPC target, int hitsplatTick, int expiryTick)
        {
            this.targetKey = targetKey;
            this.weapon = weapon;
            this.target = target;
            this.hitsplatTick = hitsplatTick;
            this.expiryTick = expiryTick;
        }
    }

    private void removeResourceInfoBoxes()
    {
        for (CoxMegascaleResourceInfoBox infoBox : resourceInfoBoxes)
        {
            infoBoxManager.removeInfoBox(infoBox);
        }
        resourceInfoBoxes.clear();
    }

    private void updateTrackedRoomFromPlayerLocation()
    {
        if (client.getLocalPlayer() == null)
        {
            return;
        }

        String room = roomFromPlayerTemplate();
        if (room == null && scoutedRaid != null)
        {
            int roomIndex = roomIndexAt(scoutedRaid.getGridBase(), scoutedRaid.getLobbyIndex(), client.getLocalPlayer().getWorldLocation());
            if (roomIndex >= 0)
            {
                net.runelite.client.plugins.raids.RaidRoom raidRoom = scoutedRaid.getRoom(roomIndex);
                if (raidRoom != null)
                {
                    room = raidRoom.getType() == RoomType.END ? "Olm" : canonicalPointRoom(raidRoom.getName());
                }
            }
        }
        if (room == null)
        {
            return;
        }
        if (room != null && ("Olm".equals(room) || isRoomInScoutedLayout(room)))
        {
            state.startRoom(room);
        }
    }

    private String roomFromPlayerTemplate()
    {
        LocalPoint local = client.getLocalPlayer().getLocalLocation();
        int[][][] chunks = client.getInstanceTemplateChunks();
        int plane = client.getPlane();
        if (local == null || chunks == null || plane < 0 || plane >= chunks.length)
        {
            return null;
        }
        int sceneX = local.getSceneX();
        int sceneY = local.getSceneY();
        if (sceneX < 0 || sceneY < 0 || sceneX / 8 >= chunks[plane].length || sceneY / 8 >= chunks[plane][sceneX / 8].length)
        {
            return null;
        }
        net.runelite.client.plugins.raids.RaidRoom room = determineRaidRoom(InstanceTemplates.findMatch(chunks[plane][sceneX / 8][sceneY / 8]));
        return room.getType() == RoomType.END ? "Olm" : canonicalPointRoom(room.getName());
    }

    static int roomIndexAt(WorldPoint gridBase, int lobbyIndex, WorldPoint playerLocation)
    {
        if (gridBase == null || playerLocation == null)
        {
            return -1;
        }

        int lobbyX = lobbyIndex % 4;
        int lobbyY = lobbyIndex % 8 > 3 ? 1 : 0;
        for (int index = 0; index < 16; index++)
        {
            int roomPlane = index > 7 ? 2 : 3;
            if (playerLocation.getPlane() != roomPlane)
            {
                continue;
            }

            int roomX = gridBase.getX() + (index % 4 - lobbyX) * 32;
            int roomY = gridBase.getY() - ((index % 8 > 3 ? 1 : 0) - lobbyY) * 32;
            if (playerLocation.getX() >= roomX && playerLocation.getX() < roomX + 32
                && playerLocation.getY() >= roomY && playerLocation.getY() < roomY + 32)
            {
                return index;
            }
        }
        return -1;
    }

    private boolean isRoomInScoutedLayout(String canonicalRoom)
    {
        for (CoxMegascaleState.ScoutedRoom room : state.getScoutedRooms())
        {
            if (canonicalRoom.equals(canonicalPointRoom(room.name)))
            {
                return true;
            }
        }
        return false;
    }

    static String canonicalPointRoom(String rawName)
    {
        if (rawName == null)
        {
            return null;
        }

        String name = rawName.toLowerCase();
        if (name.contains("great olm") || name.contains("left claw") || name.contains("right claw"))
        {
            return "Olm";
        }
        if (name.contains("muttadile") || name.contains("meat tree"))
        {
            return "Muttadiles";
        }
        if (name.contains("tekton"))
        {
            return "Tekton";
        }
        if (name.contains("vasa") || name.contains("glowing crystal"))
        {
            return "Vasa";
        }
        if (name.contains("vespula") || name.contains("abyssal portal") || name.contains("lux grub") || name.contains("vespine"))
        {
            return "Vespula";
        }
        if (name.contains("vanguard"))
        {
            return "Vanguards";
        }
        if (name.contains("guardian"))
        {
            return "Guardians";
        }
        if (name.contains("skeletal mystic") || name.equals("mystics"))
        {
            return "Mystics";
        }
        if (name.contains("lizardman shaman") || name.equals("shamans"))
        {
            return "Shamans";
        }
        if (name.contains("deathly mage") || name.contains("deathly ranger") || name.contains("tightrope"))
        {
            return "Tightrope";
        }
        if (name.contains("ice demon") || name.contains("kindling") || name.contains("brazier"))
        {
            return "Ice Demon";
        }
        if (name.contains("jewelled crab") || name.equals("crabs"))
        {
            return "Crabs";
        }
        if (name.contains("thieving") || name.contains("cavern grub"))
        {
            return "Thieving";
        }
        return null;
    }

    private Integer findScaledPartySize()
    {
        boolean inCoxRaid = client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1;
        if (inCoxRaid)
        {
            int scaledPartySize = client.getVarbitValue(COX_SCALED_PARTY_SIZE_VARBIT);
            if (scaledPartySize > 0)
            {
                return scaledPartySize;
            }
            if (lastDetectedScale != null)
            {
                return lastDetectedScale;
            }
        }
        if (!inCoxRaid && client.getVarpValue(VarPlayerID.RAIDS_PARTY_GROUPHOLDER) == -1)
        {
            scaledPartySizeWidget = null;
            return null;
        }
        if (scaledPartySizeWidget != null && !scaledPartySizeWidget.isHidden())
        {
            Integer scale = parseScaledPartySize(scaledPartySizeWidget.getText());
            if (scale != null)
            {
                return scale;
            }
        }

        scaledPartySizeWidget = null;

        int tick = client.getTickCount();
        if (!shouldSearchScaleWidget(tick, lastScaleWidgetSearchTick))
        {
            return null;
        }
        lastScaleWidgetSearchTick = tick;

        Widget[] roots = client.getWidgetRoots();
        if (roots == null)
        {
            return null;
        }

        for (Widget root : roots)
        {
            Integer scale = findScaledPartySize(root);
            if (scale != null)
            {
                return scale;
            }
        }

        return null;
    }

    static boolean shouldSearchScaleWidget(int tick, int lastSearchTick)
    {
        return lastSearchTick == Integer.MIN_VALUE || tick - lastSearchTick >= 5;
    }

    private Integer findScaledPartySize(Widget widget)
    {
        if (widget == null || widget.isHidden())
        {
            return null;
        }

        Integer scale = parseScaledPartySize(widget.getText());
        if (scale != null)
        {
            scaledPartySizeWidget = widget;
            return scale;
        }

        Widget[] dynamicChildren = widget.getDynamicChildren();
        if (dynamicChildren != null)
        {
            for (Widget child : dynamicChildren)
            {
                scale = findScaledPartySize(child);
                if (scale != null)
                {
                    return scale;
                }
            }
        }

        Widget[] staticChildren = widget.getStaticChildren();
        if (staticChildren != null)
        {
            for (Widget child : staticChildren)
            {
                scale = findScaledPartySize(child);
                if (scale != null)
                {
                    return scale;
                }
            }
        }

        Widget[] nestedChildren = widget.getNestedChildren();
        if (nestedChildren != null)
        {
            for (Widget child : nestedChildren)
            {
                scale = findScaledPartySize(child);
                if (scale != null)
                {
                    return scale;
                }
            }
        }

        return null;
    }

    private static class PendingConsumable
    {
        private final int itemId;
        private final int beforeUnits;
        private final boolean fish;
        private final int expiryTick;

        private PendingConsumable(int itemId, int beforeUnits, boolean fish, int expiryTick)
        {
            this.itemId = itemId;
            this.beforeUnits = beforeUnits;
            this.fish = fish;
            this.expiryTick = expiryTick;
        }
    }

    private void clearDetection()
    {
        detector.clear();
        scaledPartySizeWidget = null;
        lastScaleWidgetSearchTick = Integer.MIN_VALUE;
        lastDetectedScale = null;
    }

    private void resetRaidSessionState()
    {
        clearDetection();
        scoutedRaid = null;
        raidMode = "unknown";
        state.setScoutedLayout("Unknown", new ArrayList<>());
        state.setPrepInfoBoxesHidden(false);
        state.resetPointTracking();
        state.resetResourceTracking();
        state.clearAllTeamCoxData();
        resetLocalRaidMembership();
        perfectLayoutNotified = false;
        fullLayoutAnnounced = false;
        locallyObservedSharedStorageAtMillis = 0;
        lastRosterSnapshotResponseTick.clear();
        infoBoxesGloballyVisible = false;
        raidStartedAtMillis = 0;
        raidSummaryWritten = false;
        raidParticipantNames.clear();
        lastRaidRosterScanTick = Integer.MIN_VALUE;
        fixturePointObservation = null;
        fixturePointObservationExpiryTick = Integer.MIN_VALUE;
        fixtureClosedForCurrentRaid = false;
        fixtureStartReason = "enabled_mid_raid";
    }

    static Integer parseScaledPartySize(String text)
    {
        if (text == null || text.length() < SCALED_PARTY_SIZE_LABEL.length())
        {
            return null;
        }
        int labelIndex = indexOfIgnoreCase(text, SCALED_PARTY_SIZE_LABEL);
        if (labelIndex < 0)
        {
            return null;
        }
        int index = labelIndex + SCALED_PARTY_SIZE_LABEL.length();
        while (index < text.length())
        {
            char character = text.charAt(index);
            if (Character.isWhitespace(character))
            {
                index++;
                continue;
            }
            if (character == '<')
            {
                int tagEnd = text.indexOf('>', index + 1);
                if (tagEnd < 0)
                {
                    return null;
                }
                index = tagEnd + 1;
                continue;
            }
            if (text.regionMatches(true, index, "&nbsp;", 0, 6))
            {
                index += 6;
                continue;
            }
            break;
        }
        int scale = 0;
        int digits = 0;
        while (index < text.length() && Character.isDigit(text.charAt(index)))
        {
            scale = scale * 10 + text.charAt(index) - '0';
            digits++;
            index++;
            if (scale > 100)
            {
                return null;
            }
        }
        if (digits == 0)
        {
            return null;
        }
        return scale > 0 && scale <= 100 ? scale : null;
    }

    private static int indexOfIgnoreCase(String text, String search)
    {
        int lastStart = text.length() - search.length();
        for (int i = 0; i <= lastStart; i++)
        {
            if (text.regionMatches(true, i, search, 0, search.length()))
            {
                return i;
            }
        }
        return -1;
    }

    private static boolean isOlmName(String name)
    {
        if (name == null)
        {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.contains("great olm") || lower.contains("left claw") || lower.contains("right claw");
    }

    private static boolean isGreatOlmHead(NPC npc)
    {
        switch (npc.getId())
        {
            case NpcID.GREAT_OLM:
            case NpcID.GREAT_OLM_7554:
                return true;
            default:
                return false;
        }
    }

    private static String stripTags(String text)
    {
        return text == null ? "" : Text.removeTags(text);
    }

    private static com.coxmegascale.detect.RaidRoom mapRaidRoom(net.runelite.client.plugins.raids.RaidRoom room)
    {
        switch (room)
        {
            case GUARDIANS:
                return com.coxmegascale.detect.RaidRoom.GUARDIANS;
            case MYSTICS:
                return com.coxmegascale.detect.RaidRoom.MYSTICS;
            case SHAMANS:
                return com.coxmegascale.detect.RaidRoom.SHAMANS;
            case ICE_DEMON:
                return com.coxmegascale.detect.RaidRoom.ICE_DEMON;
            case THIEVING:
                return com.coxmegascale.detect.RaidRoom.THIEVING;
            case TIGHTROPE:
                return com.coxmegascale.detect.RaidRoom.TIGHTROPE;
            default:
                return null;
        }
    }

    private static String roomTypeFromSymbol(char symbol)
    {
        if (symbol == 'C')
        {
            return "Combat";
        }
        if (symbol == 'P')
        {
            return "Puzzle";
        }
        return "Room";
    }

    private static BufferedImage createIcon()
    {
        try (InputStream stream = CoxMegascalePlugin.class.getResourceAsStream("/cox_assistant_sidebar.png"))
        {
            if (stream != null)
            {
                BufferedImage icon = ImageIO.read(stream);
                if (icon != null)
                {
                    return icon;
                }
            }
        }
        catch (IOException ignored)
        {
            // Fall through to a deterministic icon so a resource failure cannot stop startup.
        }

        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(45, 45, 45));
        graphics.fillRoundRect(1, 1, 22, 22, 5, 5);
        graphics.setColor(new Color(255, 159, 110));
        graphics.fillOval(5, 5, 14, 14);
        graphics.setColor(new Color(25, 25, 25));
        graphics.fillOval(9, 9, 6, 6);
        graphics.dispose();
        return image;
    }
}
