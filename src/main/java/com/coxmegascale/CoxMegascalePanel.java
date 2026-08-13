package com.coxmegascale;

import com.coxmegascale.calc.RaidMath;
import com.coxmegascale.calc.DefenceTracker;
import com.coxmegascale.calc.SpecPlanner;
import com.coxmegascale.detect.RaidRoom;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.imageio.ImageIO;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * Responsive RuneLite sidebar for Summary, Points, Team, Options, and Welcome.
 * Public update methods marshal state-driven work onto Swing's event-dispatch
 * thread; the panel renders supplied state and never reads the game client.
 */
public class CoxMegascalePanel extends PluginPanel
{
    /**
     * Coalesce bursts of room/NPC/object events while a raid room is loading.
     * Rebuilding a tab removes and recreates its Swing hierarchy, so a very
     * short delay makes room transitions visibly flash and wastes EDT work.
     */
    static final int REFRESH_DEBOUNCE_MILLIS = 100;
    private static final Color PEACH = new Color(255, 159, 110);
    private static final Color BACKGROUND = new Color(32, 32, 32);
    private static final Color SECTION = new Color(48, 48, 48);
    private static final Color OPEN_SECTION = new Color(63, 63, 63);
    private static final Color ROW = new Color(38, 38, 38);
    private static final Color TEXT = Color.WHITE;
    private static final Color LABEL_TEXT = new Color(190, 190, 190);
    private static final Color VALUE_TEXT = Color.WHITE;
    private static final Color GREEN = new Color(0, 255, 0);
    private static final Color YELLOW = new Color(255, 215, 0);
    private static final Color RED = new Color(255, 64, 64);
    private static final Color UNKNOWN = new Color(235, 235, 235);
    private static final String GITHUB_URL = "https://github.com/caasssh/cox-assistant";
    private static final String DISCORD_URL = "https://discord.gg/w5Bm6jsVZS";
    private static final Font BODY_FONT = FontManager.getRunescapeFont();
    private static final Font WELCOME_BODY_FONT = FontManager.getRunescapeSmallFont().deriveFont(16f);
    private static final Font TITLE_FONT = FontManager.getRunescapeFont();
    private static final int STATS_GRID_ICON_SIZE = 20;
    private static final ImageIcon EMPTY_ICON = new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
    private static final List<String> GOOD_ROOM_NAMES = Arrays.asList(
        "Shamans",
        "Mystics",
        "Guardians",
        "Tightrope",
        "Ice Demon",
        "Thieving"
    );

    // Pure calculators and shared state are reused across tab rebuilds.
    private final RaidMath raidMath = new RaidMath();
    private final SpecPlanner specPlanner = new SpecPlanner(raidMath);
    private final CoxMegascaleState state;
    private final Runnable clearDetectedRooms;
    private final SkillIconManager skillIconManager;
    private final BufferedImage buchuItemImage;
    private volatile BufferedImage specialAttackSprite;
    private final boolean showWelcomeOnStartup;
    private final Consumer<Boolean> showWelcomeOnStartupChanged;
    private final Runnable openLogsDirectory;
    private final JPanel tabBar = new JPanel(new GridLayout(1, 4, 3, 0));
    private final JPanel cards = new JPanel();
    private final CardLayout cardLayout = new CardLayout();
    private final Map<String, JPanel> tabPanels = new LinkedHashMap<>();
    private final Map<String, JButton> tabButtons = new LinkedHashMap<>();
    // Image and layout caches keep frequent selected-tab refreshes allocation-
    // light and avoid repeated classpath/cache-backed resource decoding.
    private final Map<String, ImageIcon> selectedTabIcons = new HashMap<>();
    private final Map<String, ImageIcon> unselectedTabIcons = new HashMap<>();
    private final Map<String, ImageIcon> statIcons = new HashMap<>();
    private final Map<String, ImageIcon> roomIcons = new HashMap<>();
    private final Map<String, ImageIcon> resourceIcons = new HashMap<>();
    private final Map<ImageIcon, ImageIcon> numericGridIcons = new IdentityHashMap<>();
    private final Map<String, Boolean> collapsedSections = new HashMap<>();
    private final List<Long> localTeamMemberOrder = new ArrayList<>();
    private final TransferHandler teamMemberTransferHandler = new TeamMemberTransferHandler();
    // Dirty-tab tracking lets client events refresh only affected views; the
    // timer coalesces bursts before one off-screen Swing rebuild.
    private final Object refreshLock = new Object();
    private final Set<String> dirtyTabs = new HashSet<>();
    private final Timer refreshTimer;
    private final JSpinner scaleSpinner;
    private final JSpinner thievingSpinner;
    private final JSpinner fishingSpinner;
    private final JSpinner olmKillersSpinner;
    private final JSpinner overloadOverrideSpinner;
    private final JSpinner revitalisationTargetSpinner;
    private final JSpinner xericsAidTargetSpinner;
    private final JSpinner prayerEnhanceTargetSpinner;
    private final JSpinner caveWormTargetSpinner;
    private final JCheckBox emberlightCheckBox;
    private final JCheckBox chickenCheckBox;
    private final int defaultScale;
    private final int defaultThievingLevel;
    private final int defaultFishingLevel;
    private final int defaultOlmKillers;
    private final int defaultRevitalisationTarget;
    private final int defaultXericsAidTarget;
    private final int defaultCaveWormTarget;

    private Set<RaidRoom> detectedRooms;
    private volatile String selectedTab = "Summary";
    private boolean welcomeVisible;
    private String selectedStatsRoom = "Mystics";
    private Integer detectedScale;
    private boolean scaleOverrideActive;
    private boolean applyingDetectedScale;
    private boolean prayerEnhanceOverrideActive;
    private boolean applyingPrayerEnhanceTarget;
    private boolean overloadOverrideActive;
    private boolean applyingOverloadTarget;
    private boolean applyingTeamThievingTotal;
    private boolean batchingOptionChanges;
    private boolean refreshRequestQueued;
    private volatile int fixtureThievingLevel;
    private volatile int fixtureFishingLevel;
    private volatile boolean fixtureThievingManuallyEdited;
    private volatile boolean fixtureFishingManuallyEdited;
    private RaidMath.RaidOptions cachedRaidOptions;
    private RaidMath.RoomPoints cachedRoomPoints;
    private RaidMath.PurpleChance cachedPurpleChance;

    public CoxMegascalePanel(
        int defaultScale,
        int defaultThievingLevel,
        int defaultFishingLevel,
        CoxMegascaleState state,
        Runnable clearDetectedRooms,
        SkillIconManager skillIconManager
    )
    {
        this(defaultScale, defaultThievingLevel, defaultFishingLevel, state, clearDetectedRooms,
            skillIconManager, true, show -> { }, () -> { }, null, null);
    }

    public CoxMegascalePanel(
        int defaultScale,
        int defaultThievingLevel,
        int defaultFishingLevel,
        CoxMegascaleState state,
        Runnable clearDetectedRooms,
        SkillIconManager skillIconManager,
        boolean showWelcomeOnStartup,
        Consumer<Boolean> showWelcomeOnStartupChanged
    )
    {
        this(defaultScale, defaultThievingLevel, defaultFishingLevel, state, clearDetectedRooms,
            skillIconManager, showWelcomeOnStartup, showWelcomeOnStartupChanged, () -> { }, null, null);
    }

    public CoxMegascalePanel(
        int defaultScale,
        int defaultThievingLevel,
        int defaultFishingLevel,
        CoxMegascaleState state,
        Runnable clearDetectedRooms,
        SkillIconManager skillIconManager,
        boolean showWelcomeOnStartup,
        Consumer<Boolean> showWelcomeOnStartupChanged,
        Runnable openLogsDirectory
    )
    {
        this(defaultScale, defaultThievingLevel, defaultFishingLevel, state, clearDetectedRooms,
            skillIconManager, showWelcomeOnStartup, showWelcomeOnStartupChanged, openLogsDirectory, null, null);
    }

    public CoxMegascalePanel(
        int defaultScale,
        int defaultThievingLevel,
        int defaultFishingLevel,
        CoxMegascaleState state,
        Runnable clearDetectedRooms,
        SkillIconManager skillIconManager,
        boolean showWelcomeOnStartup,
        Consumer<Boolean> showWelcomeOnStartupChanged,
        Runnable openLogsDirectory,
        ItemManager itemManager,
        SpriteManager spriteManager
    )
    {
        super(false);
        this.state = state;
        this.clearDetectedRooms = clearDetectedRooms;
        this.skillIconManager = skillIconManager;
        // The panel is constructed during plugin startup. Capture the RuneLite
        // cache-backed image handles once so repeated Swing rebuilds perform no
        // cache lookup; AsyncBufferedImage fills itself if loading is deferred.
        this.buchuItemImage = itemManager == null ? null : itemManager.getImage(ItemID.RAIDS_BUCHULEAF);
        this.specialAttackSprite = null;
        this.showWelcomeOnStartup = showWelcomeOnStartup;
        this.showWelcomeOnStartupChanged = showWelcomeOnStartupChanged;
        this.openLogsDirectory = openLogsDirectory;
        this.defaultScale = defaultScale;
        this.defaultThievingLevel = defaultThievingLevel;
        this.defaultFishingLevel = defaultFishingLevel;
        this.defaultOlmKillers = state.getOlmKillers();
        this.defaultRevitalisationTarget = state.getRevitalisationTarget();
        this.defaultXericsAidTarget = state.getXericsAidTarget();
        this.defaultCaveWormTarget = state.getCaveWormTarget();
        this.fixtureThievingLevel = defaultThievingLevel;
        this.fixtureFishingLevel = defaultFishingLevel;
        this.refreshTimer = new Timer(REFRESH_DEBOUNCE_MILLIS, event -> refreshSelectedTab());
        this.refreshTimer.setRepeats(false);
        this.prayerEnhanceOverrideActive = state.isPrayerEnhanceOverrideActive();
        this.scaleSpinner = new JSpinner(new SpinnerNumberModel(defaultScale, 1, 100, 1));
        this.thievingSpinner = new JSpinner(new SpinnerNumberModel(defaultThievingLevel, 0, 5000, 1));
        this.fishingSpinner = new JSpinner(new SpinnerNumberModel(defaultFishingLevel, 60, 99, 1));
        this.olmKillersSpinner = new JSpinner(new SpinnerNumberModel(state.getOlmKillers(), 1, 100, 1));
        this.overloadOverrideSpinner = new JSpinner(new SpinnerNumberModel(0, 0, CoxMegascaleState.MAX_POTION_TARGET, 1));
        this.revitalisationTargetSpinner = new JSpinner(new SpinnerNumberModel(state.getRevitalisationTarget(), 0, CoxMegascaleState.MAX_POTION_TARGET, 1));
        this.xericsAidTargetSpinner = new JSpinner(new SpinnerNumberModel(state.getXericsAidTarget(), 0, CoxMegascaleState.MAX_POTION_TARGET, 1));
        this.prayerEnhanceTargetSpinner = new JSpinner(new SpinnerNumberModel(state.getPrayerEnhanceTarget(), 0, CoxMegascaleState.MAX_POTION_TARGET, 1));
        this.caveWormTargetSpinner = new JSpinner(new SpinnerNumberModel(state.getCaveWormTarget(), 0, CoxMegascaleState.MAX_CAVE_WORM_TARGET, 1));
        this.emberlightCheckBox = new JCheckBox();
        this.chickenCheckBox = new JCheckBox();
        this.emberlightCheckBox.getAccessibleContext().setAccessibleName("Use Emberlight route");
        this.chickenCheckBox.getAccessibleContext().setAccessibleName("Use chicken route");

        styleSpinner(scaleSpinner);
        styleSpinner(thievingSpinner);
        styleSpinner(fishingSpinner);
        styleSpinner(olmKillersSpinner);
        styleSpinner(overloadOverrideSpinner);
        styleSpinner(revitalisationTargetSpinner);
        styleSpinner(xericsAidTargetSpinner);
        styleSpinner(prayerEnhanceTargetSpinner);
        styleSpinner(caveWormTargetSpinner);
        styleInput(emberlightCheckBox);
        styleInput(chickenCheckBox);

        thievingSpinner.setToolTipText("Total Thieving level of everyone in the raid.");
        fishingSpinner.setToolTipText("Fishing level of the player doing fishing points.");
        olmKillersSpinner.setToolTipText("How many people are contributing to the Olm kill.");
        scaleSpinner.setToolTipText("Override the auto-detected scaled party size.");
        overloadOverrideSpinner.setToolTipText("Full overload potions per Olm killer. Edit to override; enter 0 for no overloads. Use auto restores the calculated target.");
        revitalisationTargetSpinner.setToolTipText("Target number of Revitalisation potions to prepare for the raid.");
        xericsAidTargetSpinner.setToolTipText("Target number of Xeric's Aid potions to prepare for the raid.");
        prayerEnhanceTargetSpinner.setToolTipText("Automatically shows 4 through scale 23, 6 through 39, and 8 at 40+. Edit to override; enter 0 for no Prayer Enhance. Use auto restores the calculated target.");
        caveWormTargetSpinner.setToolTipText("Target number of cave worms to prepare for Fishing points.");
        emberlightCheckBox.setToolTipText("Use Emberlight before BGS in the Mystics defence plan.");
        emberlightCheckBox.setOpaque(false);
        chickenCheckBox.setToolTipText("Show the full all-stat Mystics chicken route.");
        chickenCheckBox.setOpaque(false);

        setLayout(new BorderLayout());
        setBackground(BACKGROUND);
        add(buildTopPanel(), BorderLayout.NORTH);

        cards.setLayout(cardLayout);
        cards.setBackground(BACKGROUND);
        addTab("Summary");
        addTab("Points");
        addTab("Team");
        addTab("Options");
        JPanel welcomePanel = buildWelcomePanel();
        JScrollPane welcomeScrollPane = new JScrollPane(
            welcomePanel,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        welcomeScrollPane.setBorder(null);
        welcomeScrollPane.getViewport().setBackground(BACKGROUND);
        welcomeScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        cards.add(welcomeScrollPane, "Welcome");
        add(cards, BorderLayout.CENTER);

        Runnable refresh = this::refreshUnlessBatchingOptions;
        scaleSpinner.addChangeListener(event ->
        {
            if (!applyingDetectedScale && detectedScale != null && ((Integer) scaleSpinner.getValue()) != detectedScale)
            {
                scaleOverrideActive = true;
            }
            refresh.run();
        });
        thievingSpinner.addChangeListener(event ->
        {
            if (!applyingTeamThievingTotal)
            {
                fixtureThievingLevel = (Integer) thievingSpinner.getValue();
                if (!batchingOptionChanges)
                {
                    fixtureThievingManuallyEdited = true;
                }
                refresh.run();
            }
        });
        fishingSpinner.addChangeListener(event ->
        {
            fixtureFishingLevel = (Integer) fishingSpinner.getValue();
            if (!batchingOptionChanges)
            {
                fixtureFishingManuallyEdited = true;
            }
            refresh.run();
        });
        olmKillersSpinner.addChangeListener(event -> refresh.run());
        overloadOverrideSpinner.addChangeListener(event ->
        {
            if (!applyingOverloadTarget)
            {
                overloadOverrideActive = true;
                refresh.run();
            }
        });
        revitalisationTargetSpinner.addChangeListener(event -> refresh.run());
        xericsAidTargetSpinner.addChangeListener(event -> refresh.run());
        prayerEnhanceTargetSpinner.addChangeListener(event ->
        {
            if (!applyingPrayerEnhanceTarget)
            {
                prayerEnhanceOverrideActive = true;
                refresh.run();
            }
        });
        caveWormTargetSpinner.addChangeListener(event -> refresh.run());
        emberlightCheckBox.addActionListener(event ->
        {
            if (emberlightCheckBox.isSelected())
            {
                chickenCheckBox.setSelected(true);
            }
            refresh.run();
        });
        chickenCheckBox.addActionListener(event ->
        {
            if (!chickenCheckBox.isSelected() && emberlightCheckBox.isSelected())
            {
                emberlightCheckBox.setSelected(false);
            }
            refresh.run();
        });

        synchronized (refreshLock)
        {
            dirtyTabs.addAll(tabPanels.keySet());
        }

        // SpriteManager's synchronous lookup asserts that it is running on the
        // client thread, while this panel is constructed on Swing's EDT. The
        // asynchronous API performs the lookup on the correct thread and then
        // refreshes Summary once the sprite is available.
        if (spriteManager != null)
        {
            spriteManager.getSpriteAsync(SpriteID.ICON_SWORDS, 0, image ->
            {
                specialAttackSprite = image;
                SwingUtilities.invokeLater(() -> requestRefresh("Summary"));
            });
        }
    }

    public void setDetectedRooms(Set<RaidRoom> detectedRooms)
    {
        this.detectedRooms = detectedRooms;
        requestRefresh();
    }

    public void setDetectedScale(int scale)
    {
        // Preserve a user override while remembering the latest automatic value
        // for the Options tab's Use auto action.
        if (detectedScale != null && detectedScale == scale && (scaleOverrideActive || ((Integer) scaleSpinner.getValue()) == scale))
        {
            return;
        }

        detectedScale = scale;
        SwingUtilities.invokeLater(() ->
        {
            if (!scaleOverrideActive && ((Integer) scaleSpinner.getValue()) != scale)
            {
                applyingDetectedScale = true;
                scaleSpinner.setValue(scale);
                applyingDetectedScale = false;
            }
            else
            {
                requestRefresh();
            }
        });
    }

    public void refreshFromPlugin()
    {
        requestRefresh();
    }

    public void refreshFromPlugin(String... tabs)
    {
        requestRefresh(tabs);
    }

    private JPanel buildTopPanel()
    {
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(BACKGROUND);
        top.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        tabBar.setBackground(BACKGROUND);
        top.add(tabBar, BorderLayout.CENTER);
        return top;
    }

    private void addTab(String name)
    {
        JPanel panel = new ScrollableTabPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BACKGROUND);
        tabPanels.put(name, panel);

        JScrollPane scrollPane = new JScrollPane(
            panel,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BACKGROUND);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        cards.add(scrollPane, name);

        JButton button = smallButton("");
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name + " tab");
        button.getAccessibleContext().setAccessibleDescription("Open the " + name + " tab");
        ImageIcon icon = bundledIcon("tab_icons/" + name.toLowerCase(Locale.ROOT) + ".png");
        selectedTabIcons.put(name, icon);
        unselectedTabIcons.put(name, icon);
        button.addActionListener(event ->
        {
            welcomeVisible = false;
            selectedTab = name;
            synchronized (refreshLock)
            {
                dirtyTabs.add(name);
            }
            refreshSelectedTab();
        });
        tabButtons.put(name, button);
        tabBar.add(button);
        updateTabButtons();
    }

    private void refresh()
    {
        synchronized (refreshLock)
        {
            dirtyTabs.addAll(tabPanels.keySet());
        }
        refreshTimer.stop();
        refreshSelectedTab();
    }

    private void requestRefresh(String... tabs)
    {
        // Calls may originate outside the EDT. Mutate only the small dirty set
        // under lock, then schedule the actual Swing work with invokeLater.
        synchronized (refreshLock)
        {
            if (tabs == null || tabs.length == 0)
            {
                dirtyTabs.addAll(tabPanels.keySet());
            }
            else
            {
                Collections.addAll(dirtyTabs, tabs);
            }
            if (!dirtyTabs.contains(selectedTab) || refreshRequestQueued)
            {
                return;
            }
            refreshRequestQueued = true;
        }
        SwingUtilities.invokeLater(() ->
        {
            synchronized (refreshLock)
            {
                refreshRequestQueued = false;
            }
            refreshTimer.restart();
        });
    }

    private void refreshSelectedTab()
    {
        if (welcomeVisible)
        {
            cardLayout.show(cards, "Welcome");
            updateTabButtons();
            return;
        }

        synchronized (refreshLock)
        {
            if (!dirtyTabs.remove(selectedTab))
            {
                return;
            }
        }

        JPanel panel = tabPanels.get(selectedTab);
        if (panel == null)
        {
            return;
        }
        // Render into an off-screen panel first.  Room transitions can queue
        // several updates while widgets/NPCs are loading; rebuilding the
        // visible hierarchy in-place briefly exposes an empty panel and causes
        // the flash reported during traversal.  Swapping the children only
        // after the new hierarchy is complete keeps the old frame visible.
        JPanel renderPanel = new ScrollableTabPanel();
        renderPanel.setLayout(new BoxLayout(renderPanel, BoxLayout.Y_AXIS));
        renderPanel.setBackground(BACKGROUND);
        tabPanels.put(selectedTab, renderPanel);

        int scale = (Integer) scaleSpinner.getValue();
        state.configureDefenceTracker(scale);
        // Auto-fill Thieving only when every eligible Party member supplied a
        // compatible level; otherwise keep the manual input enabled.
        boolean automaticThievingTotal = hasScoutedRoom("Thieving") && state.hasTeamThievingTotal();
        thievingSpinner.setEnabled(!automaticThievingTotal);
        thievingSpinner.setToolTipText(automaticThievingTotal
            ? "Calculated from eligible RuneLite Party members in this CoX raid."
            : "Total Thieving level of everyone in the raid.");
        if (automaticThievingTotal && ((Integer) thievingSpinner.getValue()) != state.getTeamThievingTotal())
        {
            applyingTeamThievingTotal = true;
            thievingSpinner.setValue(state.getTeamThievingTotal());
            applyingTeamThievingTotal = false;
        }
        state.setOlmKillers((Integer) olmKillersSpinner.getValue());
        int automaticOverloadSips = raidMath.calculateOverloadPoints(scale).sipsToCap;
        int automaticOverloadsPerKiller = (int) Math.ceil(automaticOverloadSips / (4.0 * state.getOlmKillers()));
        int overloadsPerKiller = overloadOverrideActive ? (Integer) overloadOverrideSpinner.getValue() : automaticOverloadsPerKiller;
        state.setSipsToCap(overloadOverrideActive ? overloadsPerKiller * 4 * state.getOlmKillers() : automaticOverloadSips);
        state.configureMysticsSpecTracker(scale, emberlightCheckBox.isSelected(), chickenCheckBox.isSelected());
        state.setRevitalisationTarget((Integer) revitalisationTargetSpinner.getValue());
        state.setXericsAidTarget((Integer) xericsAidTargetSpinner.getValue());
        state.setPrayerEnhanceOverride((Integer) prayerEnhanceTargetSpinner.getValue(), prayerEnhanceOverrideActive);
        state.setCaveWormTarget((Integer) caveWormTargetSpinner.getValue());

        if (!prayerEnhanceOverrideActive)
        {
            applyingPrayerEnhanceTarget = true;
            prayerEnhanceTargetSpinner.setValue(state.getPrayerEnhanceTarget());
            applyingPrayerEnhanceTarget = false;
        }
        if (!overloadOverrideActive)
        {
            applyingOverloadTarget = true;
            overloadOverrideSpinner.setValue(automaticOverloadsPerKiller);
            applyingOverloadTarget = false;
        }

        try
        {
            switch (selectedTab)
            {
                case "Summary":
                {
                    RaidMath.RoomPoints points = calculatedRoomPoints(buildRaidOptions(scale));
                    buildSummaryTab(scale, points, cachedPurpleChance);
                    break;
                }
                case "Options":
                    buildOptionsTab();
                    break;
                case "Points":
                {
                    RaidMath.RoomPoints points = calculatedRoomPoints(buildRaidOptions(scale));
                    buildPointsTab(scale, points, cachedPurpleChance);
                    break;
                }
                case "Team":
                    buildTeamTab();
                    break;
                default:
                    break;
            }
            addFooter(renderPanel);
        }
        finally
        {
            tabPanels.put(selectedTab, panel);
        }

        panel.removeAll();
        while (renderPanel.getComponentCount() > 0)
        {
            panel.add(renderPanel.getComponent(0));
        }

        cardLayout.show(cards, selectedTab);
        updateTabButtons();
        panel.revalidate();
        panel.repaint();
    }

    private RaidMath.RoomPoints calculatedRoomPoints(RaidMath.RaidOptions options)
    {
        // Calculations depend solely on RaidOptions. Reuse the prior immutable
        // result when unrelated Team or UI state triggers a refresh.
        if (cachedRaidOptions == null || !sameRaidOptions(cachedRaidOptions, options))
        {
            cachedRaidOptions = options;
            cachedRoomPoints = raidMath.calculateRoomPoints(options.scale, options);
            cachedPurpleChance = raidMath.calculatePurpleChance(cachedRoomPoints.grandTotal);
        }
        return cachedRoomPoints;
    }

    private static boolean sameRaidOptions(RaidMath.RaidOptions left, RaidMath.RaidOptions right)
    {
        return left.scale == right.scale
            && left.totalThievingLevel == right.totalThievingLevel
            && left.fishingLevel == right.fishingLevel
            && java.util.Objects.equals(left.puzzle1, right.puzzle1)
            && java.util.Objects.equals(left.puzzle2, right.puzzle2)
            && java.util.Objects.equals(left.puzzle3, right.puzzle3)
            && left.suppressOlmPointNotes == right.suppressOlmPointNotes
            && left.includeCrabs == right.includeCrabs
            && left.overloadSips == right.overloadSips
            && left.includeGuardians == right.includeGuardians
            && left.includeMystics == right.includeMystics
            && left.includeShamans == right.includeShamans
            && left.includeMuttadiles == right.includeMuttadiles
            && left.includeTekton == right.includeTekton
            && left.includeVasa == right.includeVasa
            && left.includeVespula == right.includeVespula
            && left.includeVanguards == right.includeVanguards
            && left.useDefaultPuzzles == right.useDefaultPuzzles;
    }

    private RaidMath.RaidOptions buildRaidOptions(int scale)
    {
        List<String> puzzles = detectedPuzzleKeys();
        String first = puzzles.size() > 0 ? puzzles.get(0) : "";
        String second = puzzles.size() > 1 ? puzzles.get(1) : "";
        String third = puzzles.size() > 2 ? puzzles.get(2) : "";
        boolean layoutUnknown = state.getScoutedRooms().isEmpty();
        return new RaidMath.RaidOptions(
            scale,
            (Integer) thievingSpinner.getValue(),
            (Integer) fishingSpinner.getValue(),
            first,
            second,
            third,
            false,
            hasScoutedRoom("Crabs"),
            state.getSipsToCap(),
            layoutUnknown || hasScoutedRoom("Guardians"),
            layoutUnknown || hasScoutedRoom("Mystics"),
            layoutUnknown || hasScoutedRoom("Shamans"),
            layoutUnknown || hasScoutedRoom("Muttadiles"),
            layoutUnknown || hasScoutedRoom("Tekton"),
            layoutUnknown || hasScoutedRoom("Vasa"),
            layoutUnknown || hasScoutedRoom("Vespula"),
            layoutUnknown || hasScoutedRoom("Vanguards"),
            layoutUnknown
        );
    }

    private boolean hasScoutedRoom(String roomName)
    {
        for (CoxMegascaleState.ScoutedRoom room : state.getScoutedRooms())
        {
            if (roomName.equalsIgnoreCase(room.name))
            {
                return true;
            }
        }
        return false;
    }

    private void buildSummaryTab(int scale, RaidMath.RoomPoints points, RaidMath.PurpleChance purple)
    {
        JPanel panel = tabPanels.get("Summary");
        JPanel raid = addCollapsibleSection(panel, "Raid");
        addRow(raid, "Scale", RaidMath.formatInteger(scale));
        addRow(raid, "Olm", RaidMath.formatInteger(raidMath.calculateOlmPhaseCount(scale)) + " phases + head");
        if (state.getScoutedRooms().isEmpty())
        {
            addRow(raid, "Status", "Waiting for layout");
        }
        else
        {
            addRow(raid, "Supported estimate", RaidMath.formatInteger(points.grandTotal));
            addRow(raid, "Est. purples", String.format(Locale.US, "%.3f", purple.expectedPurples));
            addRow(raid, "Est. rolls", formatRolls(purple));
        }

        buildPrepSection(panel);

        JPanel layout = addCollapsibleSection(panel, "Layout");
        addColoredRow(layout, "Layout", state.getLayoutCode(), layoutCodeColor());
        addLayoutRows(layout);

        JPanel stats = addCollapsibleSection(panel, "Stats");
        buildStatsContent(stats, scale, buildRaidOptions(scale));
    }

    private void addMonsterDefence(JPanel panel)
    {
        DefenceTracker tracker = state.getActiveDefenceTracker();
        if (tracker == null)
        {
            return;
        }
        addSection(panel, "Monster Defence");
        addRow(panel, "Target", tracker.getDisplayName());
        addRow(panel, tracker.getDefenceLabel(), RaidMath.formatInteger(tracker.getCurrentDefence()));
        addSpecItems(panel, tracker.getRemainingSpecs());
    }

    private void buildOptionsTab()
    {
        JPanel panel = tabPanels.get("Options");
        addSection(panel, "Raid");
        addFullWidth(panel, labeledControl("Scale override", scaleSpinner), 36);
        addFullWidth(panel, labeledControl("Fishing", fishingSpinner), 36);
        addFullWidth(panel, labeledControl("Olm Killers", olmKillersSpinner), 36);
        addSection(panel, "Mystic Config");
        addFullWidth(panel, raidToggleControls(), 32);

        addSection(panel, "Potion Targets");
        addFullWidth(panel, labeledControl("Overload override", overloadOverrideSpinner), 36);
        addFullWidth(panel, labeledControl("Revitalisation", revitalisationTargetSpinner), 36);
        addFullWidth(panel, labeledControl("Xeric's Aid", xericsAidTargetSpinner), 36);
        addFullWidth(panel, labeledControl("Prayer Enhance", prayerEnhanceTargetSpinner), 36);
        addFullWidth(panel, labeledControl("Cave worms", caveWormTargetSpinner), 36);

        JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
        buttons.setBackground(BACKGROUND);
        JButton useAutoScale = smallButton("Use auto");
        useAutoScale.addActionListener(event -> resetAutomaticOptions());
        JButton refreshData = smallButton("Refresh data");
        refreshData.setToolTipText("Re-read the current layout and refresh derived raid data. Limited to once every 100 game ticks.");
        refreshData.addActionListener(event ->
        {
            if (clearDetectedRooms != null)
            {
                clearDetectedRooms.run();
            }
        });
        buttons.add(useAutoScale);
        buttons.add(refreshData);
        addFullWidth(panel, buttons, 32);

        addSection(panel, "Help");
        JButton welcome = smallButton("Welcome & how to use");
        welcome.setToolTipText("Open the CoX Assistant welcome, usage, estimates, and testing information.");
        welcome.addActionListener(event -> showWelcomePage());
        addFullWidth(panel, welcome, 32);
        JButton viewLogs = smallButton("View logs");
        viewLogs.setToolTipText("Open the CoX Assistant folder containing personal raid logs and anonymous point fixtures.");
        viewLogs.getAccessibleContext().setAccessibleName("View CoX Assistant logs");
        viewLogs.addActionListener(event -> openLogsDirectory.run());
        addFullWidth(panel, viewLogs, 32);
    }

    int getFixtureThievingLevel()
    {
        return fixtureThievingLevel;
    }

    int getFixtureFishingLevel()
    {
        return fixtureFishingLevel;
    }

    String getFixtureThievingSource()
    {
        return fixtureThievingManuallyEdited ? "manual_override" : "config_fallback";
    }

    String getFixtureFishingSource()
    {
        return fixtureFishingManuallyEdited ? "manual_override" : "config_fallback";
    }

    private void resetAutomaticOptions()
    {
        batchingOptionChanges = true;
        try
        {
            scaleOverrideActive = false;
            applyingDetectedScale = true;
            scaleSpinner.setValue(detectedScale == null ? defaultScale : detectedScale);
            applyingDetectedScale = false;
            thievingSpinner.setValue(defaultThievingLevel);
            fishingSpinner.setValue(defaultFishingLevel);
            fixtureThievingManuallyEdited = false;
            fixtureFishingManuallyEdited = false;
            olmKillersSpinner.setValue(defaultOlmKillers);
            overloadOverrideActive = false;
            applyingOverloadTarget = true;
            overloadOverrideSpinner.setValue(0);
            applyingOverloadTarget = false;
            revitalisationTargetSpinner.setValue(defaultRevitalisationTarget);
            xericsAidTargetSpinner.setValue(defaultXericsAidTarget);
            prayerEnhanceOverrideActive = false;
            applyingPrayerEnhanceTarget = true;
            prayerEnhanceTargetSpinner.setValue(0);
            applyingPrayerEnhanceTarget = false;
            caveWormTargetSpinner.setValue(defaultCaveWormTarget);
        }
        finally
        {
            applyingDetectedScale = false;
            batchingOptionChanges = false;
        }
        refresh();
    }

    private void refreshUnlessBatchingOptions()
    {
        if (!batchingOptionChanges)
        {
            refresh();
        }
    }

    private void buildPrepSection(JPanel panel)
    {
        JPanel prep = addCollapsibleSection(panel, "Prep");
        JPanel items = prepGrid();
        for (CoxMegascaleState.Resource resource : state.getResources())
        {
            if ("Cave worms".equals(resource.key))
            {
                continue;
            }
            String count = RaidMath.formatInteger(state.getCollected(resource)) + "/" + RaidMath.formatInteger(state.getTarget(resource));
            addPrepIconCell(items, pointIcon(resourceIconFile(resource.key)), count,
                resource.key + " prep credit (raw supplies plus confirmed potion ingredients)");
        }
        addPrepIconCell(items, pointIcon("revitalisation.png"), potionPrepCount("Revitalisation", state.getRevitalisationTarget()), "Revitalisation made / target");
        addPrepIconCell(items, pointIcon("xerics_aid.png"), potionPrepCount("Xeric's Aid", state.getXericsAidTarget()), "Xeric's Aid made / target");
        addPrepIconCell(items, pointIcon("prayer_enhance.png"), potionPrepCount("Prayer Enhance", state.getPrayerEnhanceTarget()), "Prayer Enhance made / target");
        addPrepIconCell(items, pointIcon("elder.png"), potionPrepCount("Elder", state.getOverloadsPerKiller()), "Elder potions made / personal overload target");
        addPrepIconCell(items, pointIcon("kodai.png"), potionPrepCount("Kodai", state.getOverloadsPerKiller()), "Kodai potions made / personal overload target");
        addPrepIconCell(items, pointIcon("twisted.png"), potionPrepCount("Twisted", state.getOverloadsPerKiller()), "Twisted potions made / personal overload target");
        addPrepIconCell(items, pointIcon("overload.png"), potionPrepCount("Overloads", state.getOverloadsPerKiller()), "Overloads made / personal target");
        int itemCount = 13;
        if (state.getCaveWormTarget() > 0)
        {
            addPrepIconCell(items, pointIcon(resourceIconFile("Cave worms")),
                RaidMath.formatInteger(state.getCollected(state.getResources().get(state.getResources().size() - 1))) + "/"
                    + RaidMath.formatInteger(state.getCaveWormTarget()), "Cave worms collected / target");
            itemCount++;
        }
        addPrepGrid(prep, items, itemCount);
    }

    private String potionPrepCount(String potion, int target)
    {
        return RaidMath.formatInteger(state.getCraftedPotions(potion)) + "/" + RaidMath.formatInteger(target);
    }

    private void buildPointsTab(int scale, RaidMath.RoomPoints points, RaidMath.PurpleChance purple)
    {
        JPanel panel = tabPanels.get("Points");
        JPanel current = addCollapsibleSection(panel, "Current");
        addRow(current, "Personal", pointsWithPurpleChance(state.getCurrentPersonalPoints()));
        addRow(current, "Group", pointsWithPurpleChance(state.getCurrentGroupPoints()));

        Integer currentExpected = calculateCurrentExpected(points);
        if (currentExpected == null)
        {
            addTooltipRow(current, "Current expected", "Waiting for layout",
                "Current group points plus remaining expected room and overload points.");
            addRow(current, "Estimated rolls", "Waiting for layout");
        }
        else
        {
            addTooltipRow(current, "Current expected", RaidMath.formatInteger(currentExpected),
                "Current group points plus each room's remaining expected points and remaining overload points. Completed room points are not added again.");
            addRow(current, "Estimated rolls", formatRolls(raidMath.calculatePurpleChance(currentExpected)));
        }

        addColoredRow(current, "Deaths lost", "-" + RaidMath.formatInteger(state.getTotalDeathPointLoss()), RED);
        addRow(current, "Overloads", RaidMath.formatInteger(state.getTeamOverloadPoints()));
        addRow(current, "Fishing", RaidMath.formatInteger(state.getTeamFishPoints()));

        JPanel expected = addCollapsibleSection(panel, "Expected");
        boolean layoutKnown = !state.getScoutedRooms().isEmpty();
        if (layoutKnown)
        {
            addRow(expected, "Expected Estimate", RaidMath.formatInteger(points.grandTotal));
            addRow(expected, "Expected no fish", RaidMath.formatInteger(expectedWithoutFishing(points)));
            addRow(expected, "Expected rolls", formatRolls(purple));
        }
        else
        {
            addRow(expected, "Expected Estimate", "Waiting for layout");
            addRow(expected, "Expected no fish", "Waiting for layout");
            addRow(expected, "Expected rolls", "Waiting for layout");
        }

        JPanel expectedDistribution = addCollapsibleSection(expected, "Expected:distribution", "Distribution Chance");
        if (layoutKnown)
        {
            addPurpleDistribution(expectedDistribution, purple);
        }
        else
        {
            addRow(expectedDistribution, "Status", "Waiting for layout");
        }

        JPanel tracked = addCollapsibleSection(panel, "Tracked Rooms");
        if (state.getActiveRoomName() != null)
        {
            addRow(tracked, state.getActiveRoomName() + " (live)",
                personalGroupPoints(state.getActiveRoomPersonalPoints(), state.getActiveRoomGroupPoints()));
        }
        for (CoxMegascaleState.TrackedRoomPoints room : state.getTrackedRoomPoints())
        {
            addRow(tracked, room.room, personalGroupPoints(room.personalPoints, room.groupPoints));
        }
        addRow(tracked, "Overloads", personalGroupPoints(state.getLocalOverloadPoints(), state.getTeamOverloadPoints()));
        addRow(tracked, "Fishing", personalGroupPoints(state.getLocalFishPoints(), state.getTeamFishPoints()));
        if (state.getTrackedRoomPoints().isEmpty() && state.getActiveRoomName() == null)
        {
            addRow(tracked, "Status", "Waiting");
        }

        JPanel expectedRooms = addCollapsibleSection(panel, "Expected Rooms");
        addExpectedRoomRows(expectedRooms, scale, points);

    }

    private int expectedWithoutFishing(RaidMath.RoomPoints points)
    {
        for (RaidMath.PointRow point : points.rooms)
        {
            if ("fishing".equals(point.key))
            {
                return points.grandTotal - point.points;
            }
        }
        return points.grandTotal;
    }

    private Integer calculateCurrentExpected(RaidMath.RoomPoints points)
    {
        if (state.getScoutedRooms().isEmpty())
        {
            return null;
        }
        int expected = state.getCurrentGroupPoints();
        for (ExpectedRoom room : expectedRoomPoints(points))
        {
            expected += Math.max(0, room.points - state.getTrackedGroupPoints(room.name));
        }
        expected += raidMath.calculateRemainingOverloadPoints(state.getTeamOverloadSips(), state.getEffectiveOverloadSipCap());
        return Math.max(1, expected);
    }

    private void addExpectedRoomRows(JPanel panel, int scale, RaidMath.RoomPoints points)
    {
        for (ExpectedRoom room : expectedRoomPoints(points))
        {
            if ("Olm".equals(room.name))
            {
                continue;
            }
            addRow(panel, room.name, RaidMath.formatInteger(state.getTrackedGroupPoints(room.name))
                + " / " + RaidMath.formatInteger(room.points));
        }

        RaidMath.OverloadPoints overloads = raidMath.calculateOverloadPointsForSips(state.getSipsToCap());
        addConsumableExpectedRow(panel, "Overload",
            state.getTeamOverloadSips(), overloads.sipsToCap, state.getEffectiveOverloadSipCap(),
            state.getTeamOverloadPoints(), overloads.points, true);
        RaidMath.FishingPoints fishing = raidMath.calculateFishingPoints(scale, (Integer) fishingSpinner.getValue());
        addConsumableExpectedRow(panel, "Fishing",
            state.getTeamFishCount(), fishing.fishToCap, fishing.fishToCap, state.getTeamFishPoints(), fishing.points, false);

        RaidMath.OlmPoints olm = raidMath.calculateOlmPoints(scale);
        addRow(panel, "Olm", RaidMath.formatInteger(state.getTrackedGroupPoints("Olm")) + " / " + RaidMath.formatInteger(olm.total));
        addRow(panel, "Olm head", RaidMath.formatInteger(olm.head));
        addRow(panel, "Olm hands", RaidMath.formatInteger(olm.hands));
        addRow(panel, "Per Phase", RaidMath.formatInteger(olm.hands / olm.phases));
    }

    private List<ExpectedRoom> expectedRoomPoints(RaidMath.RoomPoints points)
    {
        Map<String, Integer> knownPoints = new LinkedHashMap<>();
        for (RaidMath.PointRow point : points.rooms)
        {
            if ("olmHead".equals(point.key))
            {
                knownPoints.put("Olm", point.points);
            }
            else if ("olmHands".equals(point.key))
            {
                knownPoints.put("Olm", knownPoints.getOrDefault("Olm", 0) + point.points);
            }
            else if (!"overloads".equals(point.key) && !"fishing".equals(point.key) && shouldShowExpectedPoint(point.key))
            {
                knownPoints.put(point.name, knownPoints.getOrDefault(point.name, 0) + point.points);
            }
        }

        if (state.getScoutedRooms().isEmpty())
        {
            List<ExpectedRoom> rooms = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : knownPoints.entrySet())
            {
                rooms.add(new ExpectedRoom(entry.getKey(), entry.getValue()));
            }
            return rooms;
        }

        List<ExpectedRoom> rooms = new ArrayList<>();
        for (CoxMegascaleState.ScoutedRoom scoutedRoom : state.getScoutedRooms())
        {
            String roomName = cleanRoomName(scoutedRoom.name);
            String canonicalName = CoxMegascalePlugin.canonicalPointRoom(roomName);
            if (canonicalName == null)
            {
                rooms.add(new ExpectedRoom(isUnknownRoomName(roomName) ? "Unknown" : roomName, 0));
            }
            else
            {
                rooms.add(new ExpectedRoom(canonicalName, knownPoints.getOrDefault(canonicalName, 0)));
            }
        }
        return rooms;
    }

    private static final class ExpectedRoom
    {
        private final String name;
        private final int points;

        private ExpectedRoom(String name, int points)
        {
            this.name = name;
            this.points = points;
        }
    }

    private static boolean isUnknownRoomName(String roomName)
    {
        return roomName == null || roomName.trim().isEmpty()
            || roomName.toLowerCase(Locale.US).contains("unknown");
    }

    private static String personalGroupPoints(int personal, int group)
    {
        String percentage = group <= 0 ? "0.00%" : RaidMath.formatPercent(personal / (double) group);
        return RaidMath.formatInteger(personal) + " / " + RaidMath.formatInteger(group) + " (" + percentage + ")";
    }

    private void addConsumableExpectedRow(JPanel panel, String name, int currentCount, int intendedCap,
        int effectiveCap, int currentPoints, int intendedPoints, boolean showEffectiveCap)
    {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(ROW);
        row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        JLabel label = label(name);
        label.setForeground(LABEL_TEXT);
        label.setVerticalAlignment(SwingConstants.TOP);
        row.add(label, BorderLayout.WEST);
        Color progressColor = progressColor(currentCount, effectiveCap);
        JPanel values = new JPanel(new GridLayout(2, 1, 0, 0));
        values.setOpaque(false);
        JPanel points = rightAlignedValueRow();
        if (showEffectiveCap)
        {
            String pointsBase = compactPoints(currentPoints) + "/" + compactPoints(intendedPoints);
            String pointsAdjusted = "(" + compactPoints(effectiveCap * CoxMegascaleState.OVERLOAD_POINTS_PER_SIP) + ")";
            addAdjustedCapValue(points, pointsBase, pointsAdjusted, progressColor);
        }
        else
        {
            JLabel value = label(RaidMath.formatInteger(currentPoints) + " / " + RaidMath.formatInteger(intendedPoints));
            value.setForeground(progressColor);
            points.add(value);
        }
        JPanel count = rightAlignedValueRow();
        if (showEffectiveCap)
        {
            addAdjustedCapValue(count, RaidMath.formatInteger(currentCount) + "/" + RaidMath.formatInteger(intendedCap),
                "(" + RaidMath.formatInteger(effectiveCap) + ")", progressColor);
        }
        else
        {
            JLabel value = label(RaidMath.formatInteger(currentCount) + "/" + RaidMath.formatInteger(intendedCap));
            value.setForeground(progressColor);
            count.add(value);
        }
        values.add(points);
        values.add(count);
        row.add(values, BorderLayout.EAST);
        addFullWidth(panel, row, 44);
    }

    private static JPanel rightAlignedValueRow()
    {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.RIGHT_ALIGNMENT);
        return row;
    }

    private static void addAdjustedCapValue(JPanel panel, String base, String adjusted, Color baseColor)
    {
        JLabel baseLabel = label(base);
        baseLabel.setForeground(baseColor);
        JLabel adjustedLabel = label(adjusted);
        adjustedLabel.setForeground(RED);
        panel.add(baseLabel);
        panel.add(adjustedLabel);
    }

    private static String compactPoints(int points)
    {
        if (points < 1_000)
        {
            return RaidMath.formatInteger(points);
        }
        if (points < 1_000_000)
        {
            return (points / 1_000) + "k";
        }
        return String.format(Locale.US, "%.1fm", points / 1_000_000.0).replace(".0m", "m");
    }

    private static Color progressColor(int current, int cap)
    {
        if (current >= cap)
        {
            return GREEN;
        }
        return cap > 0 && current * 10 >= cap * 9 ? YELLOW : TEXT;
    }

    private void buildTeamTab()
    {
        // State has already rejected incompatible/out-of-raid payloads. This tab
        // renders only compatible raid members in a local display order.
        JPanel panel = tabPanels.get("Team");
        JPanel team = addCollapsibleSection(panel, "Team");
        List<CoxMegascaleState.TeamMember> members = state.getTeamMembers();
        if (members.isEmpty())
        {
            addRow(team, "Status", "No Party");
        }
        else
        {
            List<CoxMegascaleState.TeamMember> raidMembers = new ArrayList<>();
            for (CoxMegascaleState.TeamMember member : members)
            {
                if (!member.hasCoxData)
                {
                    continue;
                }
                raidMembers.add(member);
            }
            raidMembers = orderedTeamMembers(raidMembers);
            for (CoxMegascaleState.TeamMember member : raidMembers)
            {
                addTeamMemberSummaryRow(team, member);
            }
            if (raidMembers.isEmpty())
            {
                addRow(team, "Status", "Waiting for CoX data");
            }

            JPanel prep = addCollapsibleSection(panel, "Prep");
            JPanel shared = addCollapsibleSection(prep, "Prep:shared", "Shared");
            if (!state.hasSharedStorageSnapshot())
            {
                addRow(shared, "Status", "Open Shared Storage");
            }
            else
            {
                buildSharedStorageGrid(shared);
            }
            for (CoxMegascaleState.TeamMember member : raidMembers)
            {
                JPanel memberPrep = addCollapsibleSection(prep, "Prep:member:" + member.memberId, member.displayName, member.memberId);
                buildMemberPrepSection(memberPrep, member);
            }
        }
    }

    private List<CoxMegascaleState.TeamMember> orderedTeamMembers(List<CoxMegascaleState.TeamMember> members)
    {
        Set<Long> visibleIds = new HashSet<>();
        for (CoxMegascaleState.TeamMember member : members)
        {
            visibleIds.add(member.memberId);
            if (!localTeamMemberOrder.contains(member.memberId))
            {
                localTeamMemberOrder.add(member.memberId);
            }
        }
        localTeamMemberOrder.removeIf(memberId -> !visibleIds.contains(memberId));
        members.sort((left, right) -> Integer.compare(localTeamMemberOrder.indexOf(left.memberId), localTeamMemberOrder.indexOf(right.memberId)));
        return members;
    }

    private void addTeamMemberSummaryRow(JPanel panel, CoxMegascaleState.TeamMember member)
    {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(ROW);
        row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        row.putClientProperty("teamMemberId", member.memberId);
        row.setToolTipText("Drag to reorder Team and Prep");
        JLabel name = label(member.displayName);
        name.setForeground(LABEL_TEXT);
        row.add(name, BorderLayout.WEST);
        JPanel values = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        values.setOpaque(false);
        values.add(label(formatTeamPoints(member.personalPoints)));
        JLabel deaths = label(formatTeamDeathLoss(member.getDeathPointsLost()));
        deaths.setForeground(RED);
        values.add(deaths);
        row.add(values, BorderLayout.EAST);
        installTeamMemberDragSource(row, row);
        addFullWidth(panel, row, 28);
    }

    private void installTeamMemberDragSource(Component component, JComponent teamRow)
    {
        component.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        if (component instanceof JComponent)
        {
            ((JComponent) component).setTransferHandler(teamMemberTransferHandler);
        }
        component.addMouseMotionListener(new MouseMotionAdapter()
        {
            @Override
            public void mouseDragged(MouseEvent event)
            {
                teamMemberTransferHandler.exportAsDrag(teamRow, event, TransferHandler.MOVE);
            }
        });
        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents())
            {
                installTeamMemberDragSource(child, teamRow);
            }
        }
    }

    private void moveTeamMemberTo(long memberId, int destination)
    {
        int index = localTeamMemberOrder.indexOf(memberId);
        if (index < 0)
        {
            return;
        }
        localTeamMemberOrder.remove(index);
        if (index < destination)
        {
            destination--;
        }
        localTeamMemberOrder.add(Math.max(0, Math.min(destination, localTeamMemberOrder.size())), memberId);
        refresh();
    }

    private static JComponent teamRowFor(Component component)
    {
        Component current = component;
        while (current != null)
        {
            if (current instanceof JComponent && ((JComponent) current).getClientProperty("teamMemberId") != null)
            {
                return (JComponent) current;
            }
            current = current.getParent();
        }
        return null;
    }

    private final class TeamMemberTransferHandler extends TransferHandler
    {
        @Override
        protected Transferable createTransferable(JComponent component)
        {
            Object memberId = component.getClientProperty("teamMemberId");
            return memberId == null ? null : new StringSelection(memberId.toString());
        }

        @Override
        public int getSourceActions(JComponent component)
        {
            return MOVE;
        }

        @Override
        public boolean canImport(TransferSupport support)
        {
            return support.isDrop() && support.isDataFlavorSupported(DataFlavor.stringFlavor)
                && teamRowFor(support.getComponent()) != null;
        }

        @Override
        public boolean importData(TransferSupport support)
        {
            JComponent target = teamRowFor(support.getComponent());
            if (target == null)
            {
                return false;
            }
            try
            {
                long memberId = Long.parseLong((String) support.getTransferable().getTransferData(DataFlavor.stringFlavor));
                long targetMemberId = (Long) target.getClientProperty("teamMemberId");
                if (memberId == targetMemberId)
                {
                    return false;
                }
                int destination = localTeamMemberOrder.indexOf(targetMemberId);
                if (support.getDropLocation().getDropPoint().y > target.getHeight() / 2)
                {
                    destination++;
                }
                moveTeamMemberTo(memberId, destination);
                return true;
            }
            catch (Exception ignored)
            {
                return false;
            }
        }
    }

    static String formatTeamPoints(int points)
    {
        int roundedThousands = (int) Math.round(Math.max(0, points) / 1_000.0);
        double purplePercent = Math.max(0, points) * 100.0 / 867_500.0;
        return roundedThousands + "k - " + String.format(Locale.US, "%.1f%%", purplePercent);
    }

    static String formatTeamDeathLoss(int pointsLost)
    {
        int loss = Math.max(0, pointsLost);
        if (loss < 1_000)
        {
            return "(-" + RaidMath.formatInteger(loss) + ")";
        }
        return String.format(Locale.US, "(-%.1fk)", loss / 1_000.0).replace(".0k)", "k)");
    }

    private void buildMemberPrepSection(JPanel prep, CoxMegascaleState.TeamMember member)
    {
        JPanel items = prepGrid();
        for (CoxMegascaleState.Resource resource : state.getResources())
        {
            if ("Cave worms".equals(resource.key))
            {
                continue;
            }
            String count = RaidMath.formatInteger(member.getPrepResourceCount(resource.key)) + "/"
                + RaidMath.formatInteger(state.getTarget(resource));
            addPrepIconCell(items, pointIcon(resourceIconFile(resource.key)), count, resource.key + " herbs or secondaries / target");
        }
        addMemberPotionCell(items, member, "revitalisation.png", "Revitalisation", state.getRevitalisationTarget());
        addMemberPotionCell(items, member, "xerics_aid.png", "Xeric's Aid", state.getXericsAidTarget());
        addMemberPotionCell(items, member, "prayer_enhance.png", "Prayer Enhance", state.getPrayerEnhanceTarget());
        addMemberPotionCell(items, member, "elder.png", "Elder", state.getOverloadsPerKiller());
        addMemberPotionCell(items, member, "kodai.png", "Kodai", state.getOverloadsPerKiller());
        addMemberPotionCell(items, member, "twisted.png", "Twisted", state.getOverloadsPerKiller());
        addMemberPotionCell(items, member, "overload.png", "Overloads", state.getOverloadsPerKiller());
        int itemCount = 13;
        if (state.getCaveWormTarget() > 0)
        {
            addPrepIconCell(items, pointIcon(resourceIconFile("Cave worms")),
                RaidMath.formatInteger(member.getPrepResourceCount("Cave worms")) + "/"
                    + RaidMath.formatInteger(state.getCaveWormTarget()), "Cave worms collected / target");
            itemCount++;
        }
        addPrepGrid(prep, items, itemCount);
    }

    private void addMemberPotionCell(JPanel items, CoxMegascaleState.TeamMember member, String icon, String potion, int target)
    {
        addPrepIconCell(items, pointIcon(icon), RaidMath.formatInteger(member.getCraftedPotionCount(potion)) + "/"
            + RaidMath.formatInteger(target), potion + " made / target");
    }

    private void buildSharedStorageGrid(JPanel shared)
    {
        // Shared storage is an observational snapshot kept separate from each
        // member's private preparation totals.
        JPanel items = prepGrid();
        for (CoxMegascaleState.Resource resource : state.getResources())
        {
            if ("Cave worms".equals(resource.key))
            {
                continue;
            }
            addPrepIconCell(items, pointIcon(resourceIconFile(resource.key)), RaidMath.formatInteger(state.getSharedStorageCount(resource.key)),
                resource.key + " in shared storage");
        }
        addSharedPotionCell(items, "revitalisation.png", "Revitalisation");
        addSharedPotionCell(items, "xerics_aid.png", "Xeric's Aid");
        addSharedPotionCell(items, "prayer_enhance.png", "Prayer Enhance");
        addSharedPotionCell(items, "elder.png", "Elder");
        addSharedPotionCell(items, "kodai.png", "Kodai");
        addSharedPotionCell(items, "twisted.png", "Twisted");
        addSharedPotionCell(items, "overload.png", "Overloads");
        addPrepGrid(shared, items, 13);
    }

    private void addSharedPotionCell(JPanel items, String icon, String potion)
    {
        addPrepIconCell(items, pointIcon(icon), RaidMath.formatInteger(state.getSharedStorageCount(potion)), potion + " in shared storage");
    }

    private void addPurpleDistribution(JPanel panel, RaidMath.PurpleChance purple)
    {
        int entries = Math.max(0, purple.distribution.size() - 1);
        if (entries == 0)
        {
            addRow(panel, "Status", "Waiting");
            return;
        }

        int columns = purpleDistributionColumns(entries);
        JPanel grid = new JPanel(new GridLayout(0, columns, 4, 3));
        grid.setBackground(ROW);
        for (int i = 1; i < purple.distribution.size(); i++)
        {
            JLabel chance = label(i + " - " + RaidMath.formatPercent(purple.distribution.get(i)));
            chance.setHorizontalAlignment(JLabel.CENTER);
            grid.add(chance);
        }
        int rows = (entries + columns - 1) / columns;
        addFullWidth(panel, grid, rows * 24);
    }

    static int purpleDistributionColumns(int entries)
    {
        if (entries <= 2)
        {
            return 1;
        }
        if (entries == 3)
        {
            return 3;
        }
        return 2;
    }

    private void buildStatsContent(JPanel panel, int scale, RaidMath.RaidOptions options)
    {
        Map<String, SpecPlanner.SpecSummary> specsByName = new HashMap<>();
        for (SpecPlanner.SpecSummary summary : specPlanner.buildCoxSpecPlan(scale, options.selectedPuzzles()))
        {
            specsByName.put(summary.name, summary);
        }

        Map<String, RaidMath.StatGroup> groupsByName = new LinkedHashMap<>();
        for (RaidMath.StatSection section : raidMath.calculateNpcStats(scale))
        {
            for (RaidMath.StatGroup group : section.groups)
            {
                groupsByName.put(group.name, group);
            }
        }

        addRoomSelector(panel, groupsByName);

        RaidMath.StatGroup group = groupsByName.get(selectedStatsRoom);
        if (group == null && !groupsByName.isEmpty())
        {
            selectedStatsRoom = groupsByName.keySet().iterator().next();
            group = groupsByName.get(selectedStatsRoom);
        }

        if (group == null)
        {
            return;
        }

        addSection(panel, selectedStatsRoom);
        addStatsGrid(panel, group);
        addSpecPlan(panel, specsByName.get(group.name));
        addMonsterDefence(panel);
        addSection(panel, "Olm Max Hits");
        addMaxHitsGrid(panel, olmMaxHits(scale));
    }

    private void addRoomSelector(JPanel panel, Map<String, RaidMath.StatGroup> groupsByName)
    {
        JPanel selector = new JPanel(new GridLayout(0, 3, 3, 3));
        selector.setBackground(BACKGROUND);
        selector.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        List<String> roomNames = orderedStatsRooms(groupsByName.keySet());
        for (String roomName : roomNames)
        {
            JButton button = smallButton(shortRoomLabel(roomName));
            button.setIcon(roomIcon(roomName, 34));
            button.setToolTipText(roomName);
            button.setHorizontalTextPosition(JButton.CENTER);
            button.setVerticalTextPosition(JButton.BOTTOM);
            button.setFont(TITLE_FONT.deriveFont(16f));
            button.setForeground(PEACH);
            button.setBackground(roomName.equals(selectedStatsRoom) ? OPEN_SECTION : SECTION);
            button.addActionListener(event ->
            {
                selectedStatsRoom = roomName;
                refresh();
            });
            selector.add(button);
        }
        int rows = Math.max(1, (groupsByName.size() + 2) / 3);
        addFullWidth(panel, selector, rows * 72 + 8);
    }

    static List<String> orderedStatsRooms(Iterable<String> roomNames)
    {
        List<String> ordered = new ArrayList<>();
        List<String> availableRooms = new ArrayList<>();
        for (String roomName : roomNames)
        {
            availableRooms.add(roomName);
        }
        List<String> preferredOrder = Arrays.asList(
            "Mystics",
            "Shamans",
            "Guardians",
            "Rope Mager",
            "Rope Ranger",
            "Ice Demon",
            "Mage Hand",
            "Olm Head",
            "Melee Hand"
        );
        for (String roomName : preferredOrder)
        {
            if (availableRooms.contains(roomName))
            {
                ordered.add(roomName);
            }
        }
        for (String roomName : availableRooms)
        {
            if (!ordered.contains(roomName))
            {
                ordered.add(roomName);
            }
        }
        return ordered;
    }

    private void addStatsGrid(JPanel panel, RaidMath.StatGroup group)
    {
        JPanel stats = new JPanel(new GridLayout(2, 3, 4, 3));
        stats.setBackground(ROW);
        stats.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        stats.add(statCell("hitpoints", group.stats.get("hitpoints")));
        stats.add(statCell("attack", group.stats.get("attack")));
        stats.add(statCell("strength", group.stats.get("strength")));
        stats.add(statCell("defence", group.stats.get("defence")));
        stats.add(statCell("magic", group.stats.get("magic")));
        stats.add(statCell("magic_defence", magicDefenceBonus(group.name)));
        addFullWidth(panel, stats, 66);
    }

    private void addSpecPlan(JPanel panel, SpecPlanner.SpecSummary spec)
    {
        if (spec == null)
        {
            return;
        }

        if ("mystics".equals(spec.roomKey))
        {
            if (state.isMysticsChickenRoute())
            {
                addMysticsAllStatSpecs(panel, spec);
            }
            else
            {
                addSpecItems(panel, spec.specs);
            }
            return;
        }

        addSpecItems(panel, spec.specs);
    }

    private void addMysticsAllStatSpecs(JPanel panel, SpecPlanner.SpecSummary spec)
    {
        JPanel route = new JPanel(new GridLayout(0, 3, 4, 3));
        route.setBackground(ROW);
        route.add(iconOnlyCell(specialAttackIcon(26, 26), "Special attack requirements"));
        route.add(iconOnlyCell(resourceIcon("spec_icons/chicken.png", 26, 26), "Chicken method"));
        route.add(iconValueCell(weaponIcon("Elder Maul"), spec.allStatSpecs.get("Elder Maul"), weaponTooltip("Elder Maul")));
        route.add(iconValueCell(weaponIcon("Ralos"), spec.allStatSpecs.get("Ralos"), weaponTooltip("Ralos")));
        if (state.isMysticsUseEmberlight())
        {
            route.add(iconValueCell(weaponIcon("Emberlight"), 16, weaponTooltip("Emberlight")));
            route.add(iconValueCell(resourceIcon("spec_icons/bgs.png", 26, 26), spec.bgsDamage, "BGS damage after Emberlight"));
        }
        else
        {
            route.add(iconValueCell(resourceIcon("spec_icons/bgs.png", 26, 26), spec.bgsOnlyDamage, "BGS damage without Emberlight"));
        }
        int entries = state.isMysticsUseEmberlight() ? 6 : 5;
        int rows = (entries + 2) / 3;
        addFullWidth(panel, route, rows * 38);
    }

    private void addSpecItems(JPanel panel, Map<String, Integer> specs)
    {
        if (specs.isEmpty())
        {
            return;
        }

        JPanel grid = new JPanel(new GridLayout(0, 3, 4, 3));
        grid.setBackground(ROW);
        grid.add(iconOnlyCell(specialAttackIcon(26, 26), "Special attack requirements"));
        for (Map.Entry<String, Integer> entry : specs.entrySet())
        {
            grid.add(iconValueCell(weaponIcon(entry.getKey()), entry.getValue(), weaponTooltip(entry.getKey())));
        }
        int rows = Math.max(1, (specs.size() + 3) / 3);
        addFullWidth(panel, grid, rows * 38);
    }

    private void addMaxHitsGrid(JPanel panel, Map<String, Integer> maxHits)
    {
        JPanel hits = new JPanel(new GridLayout(2, 2, 4, 3));
        hits.setBackground(ROW);
        hits.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        hits.add(maxHitCell("Normal", maxHits.get("normal")));
        hits.add(maxHitCell("Crippled", maxHits.get("crippled")));
        hits.add(maxHitCell("Enraged", maxHits.get("enraged")));
        hits.add(maxHitCell("Head", maxHits.get("head")));
        addFullWidth(panel, hits, 56);
    }

    private JPanel maxHitCell(String name, Integer hit)
    {
        JPanel cell = new JPanel(new BorderLayout());
        cell.setOpaque(false);
        JLabel nameLabel = label(name);
        nameLabel.setForeground(LABEL_TEXT);
        cell.add(nameLabel, BorderLayout.WEST);
        JLabel value = label(RaidMath.formatInteger(hit));
        value.setHorizontalAlignment(JLabel.RIGHT);
        cell.add(value, BorderLayout.EAST);
        return cell;
    }

    private Map<String, Integer> olmMaxHits(int scale)
    {
        for (RaidMath.StatSection section : raidMath.calculateNpcStats(scale))
        {
            if (!section.maxHits.isEmpty())
            {
                return section.maxHits;
            }
        }
        return Collections.emptyMap();
    }

    private JPanel statCell(String key, int value)
    {
        return fixedIconValueCell(statIcon(key), RaidMath.formatInteger(value), statLabel(key), false);
    }

    private ImageIcon statIcon(String key)
    {
        // Cache normalized icons by semantic key because every NPC-stat grid
        // requests the same small set during rebuilds.
        ImageIcon cached = statIcons.get(key);
        if (cached != null)
        {
            return cached;
        }

        Skill skill;
        switch (key)
        {
            case "hitpoints":
                skill = Skill.HITPOINTS;
                break;
            case "attack":
                skill = Skill.ATTACK;
                break;
            case "strength":
                skill = Skill.STRENGTH;
                break;
            case "defence":
                skill = Skill.DEFENCE;
                break;
            case "magic":
                skill = Skill.MAGIC;
                break;
            case "magic_defence":
                ImageIcon magicDefence = resourceIcon("point_icons/magic_defence.png", 20, 20);
                statIcons.put(key, magicDefence);
                return magicDefence;
            default:
                skill = null;
        }

        ImageIcon icon = skill == null ? emptyIcon() : skillIcon(skill);
        statIcons.put(key, icon);
        return icon;
    }

    private ImageIcon skillIcon(Skill skill)
    {
        return scaleIcon(skillIconManager.getSkillImage(skill), 20, 20);
    }

    private ImageIcon weaponIcon(String weapon)
    {
        switch (weapon)
        {
            case "Elder Maul":
                return resourceIcon("spec_icons/elder_maul.png", 26, 26);
            case "Ralos":
                return resourceIcon("spec_icons/ralos.png", 26, 26);
            case "Emberlight":
                return resourceIcon("spec_icons/emberlight.png", 26, 26);
            case "Eye of Ayak":
                return resourceIcon("spec_icons/eye_of_ayak.png", 26, 26);
            default:
                return specialAttackIcon(24, 24);
        }
    }

    private JPanel iconValueCell(ImageIcon icon, int value, String tooltip)
    {
        return fixedIconValueCell(icon, RaidMath.formatInteger(value), tooltip, true);
    }

    private JPanel fixedIconValueCell(ImageIcon icon, String value, String tooltip, boolean inset)
    {
        JPanel cell = new JPanel(new GridBagLayout());
        cell.setBackground(ROW);
        if (inset)
        {
            cell.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
        }
        JLabel image = new JLabel(normalizeNumericGridIcon(icon));
        JLabel number = label(value);
        image.setToolTipText(tooltip);
        number.setToolTipText(tooltip);
        cell.setToolTipText(tooltip);
        GridBagConstraints iconConstraints = new GridBagConstraints();
        iconConstraints.gridx = 0;
        iconConstraints.gridy = 0;
        iconConstraints.anchor = GridBagConstraints.LINE_START;
        cell.add(image, iconConstraints);
        GridBagConstraints valueConstraints = new GridBagConstraints();
        valueConstraints.gridx = 1;
        valueConstraints.gridy = 0;
        valueConstraints.weightx = 1;
        valueConstraints.fill = GridBagConstraints.HORIZONTAL;
        valueConstraints.anchor = GridBagConstraints.LINE_START;
        valueConstraints.insets = new Insets(0, inset ? 4 : 3, 0, 0);
        cell.add(number, valueConstraints);
        return cell;
    }

    private ImageIcon normalizeNumericGridIcon(ImageIcon icon)
    {
        if (icon == null || icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0)
        {
            return emptyIcon();
        }
        ImageIcon cached = numericGridIcons.get(icon);
        if (cached != null)
        {
            return cached;
        }

        int width = icon.getIconWidth();
        int height = icon.getIconHeight();
        BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = source.createGraphics();
        icon.paintIcon(null, graphics, 0, 0);
        graphics.dispose();
        ImageIcon normalized = scaleIcon(trimTransparentPadding(source), STATS_GRID_ICON_SIZE, STATS_GRID_ICON_SIZE);
        numericGridIcons.put(icon, normalized);
        return normalized;
    }

    private JPanel iconOnlyCell(ImageIcon icon, String tooltip)
    {
        JPanel cell = new JPanel(new GridBagLayout());
        cell.setBackground(ROW);
        cell.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
        JLabel image = new JLabel(normalizeNumericGridIcon(icon));
        image.setHorizontalAlignment(SwingConstants.LEFT);
        image.setToolTipText(tooltip);
        cell.setToolTipText(tooltip);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.LINE_START;
        cell.add(image, constraints);
        return cell;
    }

    private ImageIcon roomIcon(String roomName, int size)
    {
        String cacheKey = roomName + '@' + size;
        ImageIcon cached = roomIcons.get(cacheKey);
        if (cached != null)
        {
            return cached;
        }

        String file;
        switch (roomName)
        {
            case "Guardians":
                file = "guardian.png";
                break;
            case "Shamans":
                file = "shamans.png";
                break;
            case "Mystics":
                file = "mystics.png";
                break;
            case "Rope Mager":
                file = "rope_mager.png";
                break;
            case "Rope Ranger":
                file = "rope_ranger.png";
                break;
            case "Ice Demon":
                file = "ice_demon.png";
                break;
            default:
                file = "olm.png";
        }

        ImageIcon icon = resourceIcon("room_icons/" + file, size, size);
        roomIcons.put(cacheKey, icon);
        return icon;
    }

    private ImageIcon resourceIcon(String path, int width, int height)
    {
        String cacheKey = path + '@' + width + 'x' + height;
        ImageIcon cached = resourceIcons.get(cacheKey);
        if (cached != null)
        {
            return cached;
        }

        ImageIcon icon = EMPTY_ICON;
        try (InputStream stream = CoxMegascalePanel.class.getClassLoader().getResourceAsStream(path))
        {
            if (stream != null)
            {
                icon = scaleIcon(ImageIO.read(stream), width, height);
            }
        }
        catch (Exception ignored)
        {
            // A missing cosmetic icon should not prevent the panel from loading.
        }
        resourceIcons.put(cacheKey, icon);
        return icon;
    }

    private static ImageIcon scaleIcon(BufferedImage source, int maxWidth, int maxHeight)
    {
        if (source == null)
        {
            return emptyIcon();
        }
        double scale = Math.min(maxWidth / (double) source.getWidth(), maxHeight / (double) source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(maxWidth, maxHeight, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, (maxWidth - width) / 2, (maxHeight - height) / 2, width, height, null);
        graphics.dispose();
        return new ImageIcon(scaled);
    }

    static BufferedImage trimTransparentPadding(BufferedImage source)
    {
        int left = source.getWidth();
        int top = source.getHeight();
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < source.getHeight(); y++)
        {
            for (int x = 0; x < source.getWidth(); x++)
            {
                if ((source.getRGB(x, y) >>> 24) == 0)
                {
                    continue;
                }
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        if (right < left || bottom < top)
        {
            return source;
        }
        return source.getSubimage(left, top, right - left + 1, bottom - top + 1);
    }

    private static ImageIcon emptyIcon()
    {
        return EMPTY_ICON;
    }

    private static String statLabel(String key)
    {
        switch (key)
        {
            case "hitpoints":
                return "Hitpoints";
            case "attack":
                return "Attack";
            case "strength":
                return "Strength";
            case "defence":
                return "Defence";
            case "magic":
                return "Magic";
            case "ranged":
                return "Ranged";
            case "magic_defence":
                return "Magic defence";
            default:
                return key;
        }
    }

    private static int magicDefenceBonus(String roomName)
    {
        switch (roomName)
        {
            case "Shamans":
                return 160;
            case "Mystics":
                return 140;
            case "Olm Head":
            case "Melee Hand":
                return 200;
            case "Mage Hand":
                return 50;
            case "Ice Demon":
                return 40;
            default:
                return 0;
        }
    }

    private static String shortRoomLabel(String roomName)
    {
        switch (roomName)
        {
            case "Guardians":
                return "Guardian";
            case "Shamans":
                return "Shaman";
            case "Mystics":
                return "Mystic";
            case "Rope Mager":
                return "Mager";
            case "Rope Ranger":
                return "Ranger";
            case "Olm Head":
                return "Head";
            case "Melee Hand":
                return "Melee";
            case "Mage Hand":
                return "Mage";
            default:
                return roomName;
        }
    }

    private static String weaponTooltip(String weapon)
    {
        return "Ralos".equals(weapon) ? "Ralos successful projectile hits" : weapon;
    }

    private void addLayoutRows(JPanel panel)
    {
        if (!state.getScoutedRooms().isEmpty())
        {
            boolean fixedLayout = usesUniformFixedLayoutRoomColor(state.getLayoutCode());
            for (CoxMegascaleState.ScoutedRoom room : state.getScoutedRooms())
            {
                String name = cleanRoomName(room.name);
                addLayoutRow(panel, room.type, name, fixedLayout ? GREEN : roomColor(name));
            }
            return;
        }

        for (RaidRoom room : Arrays.asList(RaidRoom.SHAMANS, RaidRoom.MYSTICS, RaidRoom.GUARDIANS))
        {
            addLayoutRow(panel, "Combat", hasDetected(room) ? room.getDisplayName() : "Unknown", hasDetected(room) ? GREEN : UNKNOWN);
        }

        List<RaidRoom> puzzles = new ArrayList<>();
        for (RaidRoom room : Arrays.asList(RaidRoom.ICE_DEMON, RaidRoom.THIEVING, RaidRoom.TIGHTROPE))
        {
            if (hasDetected(room))
            {
                puzzles.add(room);
            }
        }
        for (int index = 0; index < 2; index++)
        {
            if (index < puzzles.size())
            {
                addLayoutRow(panel, "Puzzle", puzzles.get(index).getDisplayName(), GREEN);
            }
            else
            {
                addLayoutRow(panel, "Puzzle", "Unknown", UNKNOWN);
            }
        }
    }

    private List<String> detectedPuzzleKeys()
    {
        List<String> puzzles = new ArrayList<>();
        for (CoxMegascaleState.ScoutedRoom room : state.getScoutedRooms())
        {
            String key = puzzleKey(room.name);
            if (key != null)
            {
                puzzles.add(key);
            }
        }
        if (state.getScoutedRooms().isEmpty())
        {
            for (RaidRoom room : Arrays.asList(RaidRoom.ICE_DEMON, RaidRoom.THIEVING, RaidRoom.TIGHTROPE))
            {
                String key = puzzleKey(room.getDisplayName());
                if (hasDetected(room) && key != null)
                {
                    puzzles.add(key);
                }
            }
        }
        return puzzles;
    }

    private boolean hasDetected(RaidRoom room)
    {
        return detectedRooms != null && detectedRooms.contains(room);
    }

    private Color layoutCodeColor()
    {
        if ("Unknown".equalsIgnoreCase(state.getLayoutCode()))
        {
            return UNKNOWN;
        }
        return state.isPerfectLayout() ? GREEN : RED;
    }

    private static String puzzleKey(String name)
    {
        if ("Ice Demon".equals(name))
        {
            return RaidMath.PUZZLE_ICE_DEMON;
        }
        if ("Tightrope".equals(name))
        {
            return RaidMath.PUZZLE_TIGHTROPE;
        }
        if ("Thieving".equals(name))
        {
            return RaidMath.PUZZLE_THIEVING;
        }
        return null;
    }

    private static String cleanRoomName(String name)
    {
        return name == null || name.trim().isEmpty() ? "Unknown" : name;
    }

    private static Color roomColor(String roomName)
    {
        String name = cleanRoomName(roomName);
        if (GOOD_ROOM_NAMES.contains(name))
        {
            return GREEN;
        }
        if ("Crabs".equals(name) || name.toLowerCase(Locale.US).contains("unknown"))
        {
            return UNKNOWN;
        }
        return RED;
    }

    static boolean usesUniformFixedLayoutRoomColor(String layoutCode)
    {
        return "Full".equalsIgnoreCase(layoutCode) || "Challenge Mode".equalsIgnoreCase(layoutCode);
    }

    private JPanel addCollapsibleSection(JPanel panel, String title)
    {
        return addCollapsibleSection(panel, title, title);
    }

    private JPanel addCollapsibleSection(JPanel panel, String collapseKey, String title)
    {
        return addCollapsibleSection(panel, collapseKey, title, null);
    }

    private JPanel addCollapsibleSection(JPanel panel, String collapseKey, String title, Long teamMemberId)
    {
        String key = selectedTab + ":" + collapseKey;
        boolean collapsed = collapsedSections.getOrDefault(key, false);
        JButton header = sectionHeaderButton(title);
        header.setForeground(collapseKey.startsWith("Prep:") ? VALUE_TEXT : PEACH);
        header.setBackground(collapsed ? SECTION : OPEN_SECTION);
        header.setHorizontalAlignment(SwingConstants.CENTER);
        header.setFont(TITLE_FONT.deriveFont(16f));
        if (teamMemberId != null)
        {
            header.putClientProperty("teamMemberId", teamMemberId);
            header.setToolTipText("Drag to reorder Team and Prep");
            installTeamMemberDragSource(header, header);
        }
        header.addActionListener(event ->
        {
            collapsedSections.put(key, !collapsed);
            refresh();
        });
        addFullWidth(panel, header, 26);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BACKGROUND);
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        if (!collapsed)
        {
            panel.add(content);
        }
        return content;
    }

    private static void addSection(JPanel panel, String title)
    {
        JLabel label = new JLabel(title);
        label.setForeground(PEACH);
        label.setFont(TITLE_FONT.deriveFont(16f));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(SECTION);
        wrapper.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        wrapper.add(label, BorderLayout.CENTER);
        addFullWidth(panel, wrapper, 26);
    }

    private static void addRow(JPanel panel, String name, String value)
    {
        addColoredRow(panel, name, value, VALUE_TEXT);
    }

    private static void addTooltipRow(JPanel panel, String name, String value, String tooltip)
    {
        addColoredRow(panel, name, value, VALUE_TEXT, tooltip);
    }

    private static void addColoredRow(JPanel panel, String name, String value, Color valueColor)
    {
        addColoredRow(panel, name, value, valueColor, null);
    }

    private static void addColoredRow(JPanel panel, String name, String value, Color valueColor, String tooltip)
    {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(ROW);
        row.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        JLabel left = label(name);
        left.setForeground(LABEL_TEXT);
        JLabel right = label(value);
        right.setForeground(valueColor);
        right.setHorizontalAlignment(JLabel.RIGHT);
        if (tooltip != null)
        {
            left.setToolTipText(tooltip);
            right.setToolTipText(tooltip);
            row.setToolTipText(tooltip);
        }
        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.CENTER);
        addFullWidth(panel, row, 25);
    }

    private static void addLayoutRow(JPanel panel, String type, String room, Color roomColor)
    {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(ROW);
        row.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        JLabel left = label(type);
        left.setForeground(LABEL_TEXT);
        JLabel right = label(room);
        right.setForeground(roomColor);
        right.setHorizontalAlignment(JLabel.RIGHT);
        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.CENTER);
        addFullWidth(panel, row, 25);
    }

    private static JPanel labeledControl(String labelText, Component input)
    {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(ROW);
        row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        JLabel label = label(labelText);
        label.setToolTipText(input instanceof JComponent ? ((JComponent) input).getToolTipText() : null);
        label.setLabelFor(input);
        row.add(label, BorderLayout.WEST);
        row.add(input, BorderLayout.EAST);
        return row;
    }

    private JPanel raidToggleControls()
    {
        JPanel input = new JPanel(new GridLayout(1, 2, 4, 0));
        input.setBackground(ROW);
        input.add(mysticToggleCell("spec_icons/chicken.png", chickenCheckBox));
        input.add(mysticToggleCell("spec_icons/emberlight.png", emberlightCheckBox));
        return input;
    }

    private JPanel mysticToggleCell(String iconPath, JCheckBox checkBox)
    {
        JPanel cell = new JPanel(new GridBagLayout());
        cell.setBackground(ROW);
        JLabel icon = new JLabel(resourceIcon(iconPath, 24, 24));
        icon.setToolTipText(checkBox.getToolTipText());
        cell.setToolTipText(checkBox.getToolTipText());
        GridBagConstraints iconConstraints = new GridBagConstraints();
        iconConstraints.gridx = 0;
        iconConstraints.gridy = 0;
        iconConstraints.anchor = GridBagConstraints.CENTER;
        iconConstraints.insets = new Insets(0, 2, 0, 2);
        cell.add(icon, iconConstraints);
        GridBagConstraints checkConstraints = new GridBagConstraints();
        checkConstraints.gridx = 1;
        checkConstraints.gridy = 0;
        checkConstraints.anchor = GridBagConstraints.CENTER;
        checkConstraints.insets = new Insets(0, 2, 0, 2);
        cell.add(checkBox, checkConstraints);
        return cell;
    }

    private static void addFullWidth(JPanel panel, Component component, int height)
    {
        component.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 10, height));
        component.setMinimumSize(new Dimension(0, height));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        if (component instanceof JComponent)
        {
            ((JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        panel.add(component);
    }

	private static JButton smallButton(String text)
	{
		JButton button = new JButton(text);
		button.setMargin(new Insets(4, 6, 4, 6));
		button.setFont(BODY_FONT.deriveFont(16f));
		return button;
	}

    private static JButton sectionHeaderButton(String text)
    {
        JButton button = new JButton(text);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setMargin(new Insets(4, 6, 4, 6));
		button.setFont(BODY_FONT.deriveFont(16f));
        return button;
    }

    private void updateTabButtons()
    {
        for (Map.Entry<String, JButton> entry : tabButtons.entrySet())
        {
            boolean selected = entry.getKey().equals(selectedTab);
            entry.getValue().setForeground(TEXT);
            entry.getValue().setBackground(SECTION);
            entry.getValue().setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0,
                selected ? PEACH : BACKGROUND));
            entry.getValue().setIcon(selected ? selectedTabIcons.get(entry.getKey()) : unselectedTabIcons.get(entry.getKey()));
        }
    }

    void showWelcomePage()
    {
        welcomeVisible = true;
        cardLayout.show(cards, "Welcome");
    }

    private JPanel buildWelcomePanel()
    {
        JPanel welcome = new ScrollableTabPanel();
        welcome.setLayout(new BoxLayout(welcome, BoxLayout.Y_AXIS));
        welcome.setBackground(BACKGROUND);

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(BACKGROUND);
        titleBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(105, 105, 105)));
        JLabel title = label("CoX Assistant");
        title.setForeground(PEACH);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        titleBar.add(title, BorderLayout.CENTER);
        addFullWidth(welcome, titleBar, 28);

        addWelcomeHeading(welcome, "Welcome to CoX Assistant");
        addWelcomeText(welcome, welcomeText(
            "Plan and track your Chambers of Xeric raid from one sidebar. Check your layout and scale, room and purple estimates, preparation, NPC stats, defence specs, Olm phases, and compatible RuneLite Party data."
        ));

        addWelcomeHeading(welcome, "How to use it");
        addWelcomeText(welcome, welcomeText(
            "Summary — layout, current progress, and raid stats.\n"
                + "Points — estimates, purple odds, preparation, and point changes.\n"
                + "Team — compatible Party members and shared raid data.\n"
                + "Options — scale, levels, Olm killers, potion targets, and Mystic routes."
        ));

        addWelcomeHeading(welcome, "Estimates and testing");
        JTextArea disclaimer = welcomeText(
            "CoX Assistant is still being calibrated with community raid data. Some estimates, especially at larger scales, are provisional. Treat point and purple estimates as guidance; tracked point changes are shown separately."
        );
        disclaimer.setForeground(YELLOW);
        addWelcomeText(welcome, disclaimer);

        addWelcomeText(welcome, welcomeText(
            "Want to help? Turn on Log point fixtures in RuneLite configuration. Files stay on your computer at .runelite\\cox-assistant\\point-fixtures. Review them first, then upload only .jsonl files from point-fixtures to the #point-data channel in Discord. Never upload raid-logs; those summaries can contain participant names."
        ));

        addFooter(welcome);

        JCheckBox showOnStartup = new JCheckBox("Show on startup", showWelcomeOnStartup);
        showOnStartup.setOpaque(false);
        showOnStartup.setForeground(TEXT);
        showOnStartup.setFont(WELCOME_BODY_FONT);
        showOnStartup.setToolTipText("Show this welcome page whenever CoX Assistant starts.");
        showOnStartup.getAccessibleContext().setAccessibleName("Show welcome on startup");
        showOnStartup.addActionListener(event -> showWelcomeOnStartupChanged.accept(showOnStartup.isSelected()));
        addFullWidth(welcome, showOnStartup, 28);

        JButton close = smallButton("Close");
        close.setToolTipText("Close the welcome page and open Summary.");
        close.getAccessibleContext().setAccessibleName("Close welcome page");
        close.addActionListener(event -> closeWelcomePage());
        addFullWidth(welcome, close, 34);
        return welcome;
    }

    private static void addWelcomeHeading(JPanel panel, String text)
    {
        JLabel heading = label(text);
        heading.setFont(BODY_FONT.deriveFont(Font.BOLD, 16f));
        heading.setBorder(BorderFactory.createEmptyBorder(5, 7, 0, 7));
        addFullWidth(panel, heading, 24);
    }

    private void closeWelcomePage()
    {
        welcomeVisible = false;
        selectedTab = "Summary";
        synchronized (refreshLock)
        {
            dirtyTabs.add("Summary");
        }
        refreshSelectedTab();
    }

    private static JTextArea welcomeText(String text)
    {
        JTextArea area = new JTextArea(text);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setForeground(LABEL_TEXT);
        area.setFont(WELCOME_BODY_FONT);
        area.setBorder(BorderFactory.createEmptyBorder(2, 8, 3, 8));
        return area;
    }

    private static void addWelcomeText(JPanel panel, JTextArea area)
    {
        int width = PluginPanel.PANEL_WIDTH - 22;
        area.setSize(width, Short.MAX_VALUE);
        int height = Math.max(30, area.getPreferredSize().height + 2);
        addFullWidth(panel, area, height);
    }

    private static void addFooter(JPanel panel)
    {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 2));
        footer.setBackground(BACKGROUND);
        footer.add(linkButton("GitHub", "Open the CoX Assistant GitHub repository", GITHUB_URL, "GitHub"));
        footer.add(linkButton("Discord", "Open the CoX Assistant Discord", DISCORD_URL, "Discord"));
        addFullWidth(panel, footer, 32);
    }

    private static JButton linkButton(String text, String tooltip, String url, String iconName)
    {
        JButton button = smallButton(text);
        button.setIcon(bundledIcon("brand_icons/" + iconName.toLowerCase(Locale.ROOT) + ".png"));
        button.setToolTipText(tooltip);
        button.getAccessibleContext().setAccessibleName(text + " link");
        button.addActionListener(event -> LinkBrowser.browse(url));
        return button;
    }

    private static ImageIcon bundledIcon(String path)
    {
        try (InputStream stream = CoxMegascalePanel.class.getClassLoader().getResourceAsStream(path))
        {
            if (stream != null)
            {
                return new ImageIcon(ImageIO.read(stream));
            }
        }
        catch (Exception ignored)
        {
            // An empty icon keeps the layout stable if a packaged resource is unexpectedly absent.
        }
        return EMPTY_ICON;
    }

    private static JLabel label(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
		label.setFont(BODY_FONT.deriveFont(16f));
        return label;
    }

    private static void styleInput(JComponent component)
    {
		component.setFont(BODY_FONT.deriveFont(16f));
    }

    private static void styleSpinner(JSpinner spinner)
    {
        styleInput(spinner);
        Dimension size = new Dimension(76, 28);
        spinner.setPreferredSize(size);
        spinner.setMinimumSize(size);
        spinner.setMaximumSize(size);
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor)
        {
            JTextField textField = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
            textField.setColumns(4);
            textField.setHorizontalAlignment(JTextField.RIGHT);
            if (textField instanceof JFormattedTextField)
            {
                ((JFormattedTextField) textField).setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
            }
        }
    }

    private static String signedInteger(int value)
    {
        return (value > 0 ? "+" : "") + RaidMath.formatInteger(value);
    }

    private String pointsWithPurpleChance(int points)
    {
        if (points <= 0)
        {
            return RaidMath.formatInteger(points) + " (0.00%)";
        }
        return RaidMath.formatInteger(points) + " (" + formatRolls(raidMath.calculatePurpleChance(points)) + ")";
    }

    private static String formatRolls(RaidMath.PurpleChance purple)
    {
        List<String> rolls = new ArrayList<>();
        String previous = null;
        int count = 0;
        for (double probability : purple.rollProbabilities)
        {
            String formatted = RaidMath.formatPercent(probability);
            if (formatted.equals(previous))
            {
                count++;
                continue;
            }
            addRollGroup(rolls, previous, count);
            previous = formatted;
            count = 1;
        }
        addRollGroup(rolls, previous, count);
        return String.join(" + ", rolls);
    }

    private static void addRollGroup(List<String> rolls, String roll, int count)
    {
        if (roll == null || count <= 0)
        {
            return;
        }
        rolls.add(count == 1 ? roll : count + "x " + roll);
    }

    private static String escape(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String shortPointNote(String note)
    {
        return note
            .replace(" Olm phases", "p")
            .replace("HP scale capped at ", "cap ")
            .replace(" Overloads", " ovl")
            .replace(" from kindling", " kindling")
            .replace(" Grubs", " grubs")
            .replace(" Fish", " fish")
            .replace('\n', ' ');
    }

    private static final class ScrollableTabPanel extends JPanel implements Scrollable
    {
        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return orientation == SwingConstants.VERTICAL
                ? Math.max(16, visibleRect.height - 16)
                : Math.max(16, visibleRect.width - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }

    private static final int PREP_GRID_THREE_COLUMN_MIN_WIDTH = 220;

    private static JPanel prepGrid()
    {
        JPanel grid = new ResponsivePrepGrid();
        grid.setBackground(ROW);
        return grid;
    }

    private static void addPrepGrid(JPanel panel, JPanel grid, int itemCount)
    {
        if (grid instanceof ResponsivePrepGrid)
        {
            ((ResponsivePrepGrid) grid).setItemCount(itemCount);
            addFullWidth(panel, grid, ((ResponsivePrepGrid) grid).getRowCount() * 38);
            return;
        }
        int rows = Math.max(1, (itemCount + 2) / 3);
        addFullWidth(panel, grid, rows * 38);
    }

    static int prepGridColumns(int width)
    {
        return width >= PREP_GRID_THREE_COLUMN_MIN_WIDTH ? 3 : 2;
    }

    private static final class ResponsivePrepGrid extends JPanel
    {
        private int itemCount;

        private ResponsivePrepGrid()
        {
            super(new GridLayout(0, 3, 4, 3));
            addComponentListener(new ComponentAdapter()
            {
                @Override
                public void componentResized(ComponentEvent event)
                {
                    updateColumns(event.getComponent().getWidth());
                }
            });
        }

        private void setItemCount(int itemCount)
        {
            this.itemCount = Math.max(0, itemCount);
            updateColumns(PluginPanel.PANEL_WIDTH - 10);
        }

        private int getRowCount()
        {
            int columns = ((GridLayout) getLayout()).getColumns();
            return Math.max(1, (itemCount + columns - 1) / columns);
        }

        private void updateColumns(int width)
        {
            int columns = prepGridColumns(width);
            GridLayout layout = (GridLayout) getLayout();
            if (layout.getColumns() != columns)
            {
                setLayout(new GridLayout(0, columns, 4, 3));
            }
            int height = getRowCount() * 38;
            Dimension size = new Dimension(PluginPanel.PANEL_WIDTH - 10, height);
            setPreferredSize(size);
            setMinimumSize(new Dimension(0, height));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
            revalidate();
        }
    }

    private static void addPrepIconCell(JPanel grid, ImageIcon icon, String value, String tooltip)
    {
        grid.add(prepIconCell(icon, value, tooltip));
    }

    private static JPanel prepIconCell(ImageIcon icon, String value, String tooltip)
    {
        JPanel cell = new JPanel(new GridBagLayout());
        cell.setBackground(ROW);
        cell.setBorder(BorderFactory.createEmptyBorder(3, 2, 3, 2));
        JLabel image = new JLabel(icon);
        JLabel count = label(value);
        image.setToolTipText(tooltip);
        count.setToolTipText(tooltip);
        cell.setToolTipText(tooltip);
        GridBagConstraints iconConstraints = new GridBagConstraints();
        iconConstraints.gridx = 0;
        iconConstraints.gridy = 0;
        iconConstraints.anchor = GridBagConstraints.LINE_START;
        cell.add(image, iconConstraints);
        GridBagConstraints countConstraints = new GridBagConstraints();
        countConstraints.gridx = 1;
        countConstraints.gridy = 0;
        countConstraints.weightx = 1;
        countConstraints.fill = GridBagConstraints.HORIZONTAL;
        countConstraints.anchor = GridBagConstraints.LINE_START;
        countConstraints.insets = new Insets(0, 3, 0, 0);
        cell.add(count, countConstraints);
        return cell;
    }

    private ImageIcon pointIcon(String file)
    {
        if ("buchu.png".equals(file) && buchuItemImage != null)
        {
            return scaleIcon(buchuItemImage, 24, 24);
        }
        return resourceIcon("point_icons/" + file, 24, 24);
    }

    private ImageIcon specialAttackIcon(int width, int height)
    {
        BufferedImage image = specialAttackSprite;
        if (image == null)
        {
            image = skillIconManager == null ? null : skillIconManager.getSkillImage(Skill.ATTACK);
        }
        return scaleIcon(image, width, height);
    }

    private static String resourceIconFile(String resourceKey)
    {
        switch (resourceKey)
        {
            case "Noxifer":
                return "noxifer.png";
            case "Golpar":
                return "golpar.png";
            case "Buchu":
                return "buchu.png";
            case "Stinkhorn":
                return "stinkhorn.png";
            case "Endarkened":
                return "endarkened.png";
            case "Cicely":
                return "cicely.png";
            case "Cave worms":
                return "cave_worms.png";
            default:
                return "cave_worms.png";
        }
    }

    private boolean shouldShowExpectedPoint(String key)
    {
        if (state.getScoutedRooms().isEmpty())
        {
            return true;
        }

        String roomName;
        switch (key)
        {
            case "guardians":
                roomName = "Guardians";
                break;
            case "mystics":
                roomName = "Mystics";
                break;
            case "shamans":
                roomName = "Shamans";
                break;
            case "muttadiles":
                roomName = "Muttadiles";
                break;
            case "tekton":
                roomName = "Tekton";
                break;
            case "vasa":
                roomName = "Vasa";
                break;
            case "vespula":
                roomName = "Vespula";
                break;
            case "vanguards":
                roomName = "Vanguards";
                break;
            case "tightrope":
                roomName = "Tightrope";
                break;
            case "thieving":
                roomName = "Thieving";
                break;
            case "iceDemon":
                roomName = "Ice Demon";
                break;
            case "crabs":
                roomName = "Crabs";
                break;
            default:
                return true;
        }

        for (CoxMegascaleState.ScoutedRoom room : state.getScoutedRooms())
        {
            if (roomName.equalsIgnoreCase(room.name))
            {
                return true;
            }
        }
        return false;
    }

    private static String pointIconPath(String key)
    {
        switch (key)
        {
            case "thieving":
                return "cavern_grubs.png";
            case "iceDemon":
                return "kindling.png";
            case "overloads":
                return "overload.png";
            case "fishing":
                return "fishing.png";
            default:
                return null;
        }
    }

    private static String pointIconTooltip(String key)
    {
        switch (key)
        {
            case "thieving":
                return "Cavern grubs";
            case "iceDemon":
                return "Kindling points";
            case "overloads":
                return "Overloads";
            case "fishing":
                return "Fishing";
            default:
                return key;
        }
    }

    private static String pointNoteValue(String key, String note)
    {
        switch (key)
        {
            case "thieving":
                return note.replace(" Grubs", "");
            case "iceDemon":
                return note.replace(" from kindling", "");
            case "overloads":
                return note.replace(" Overloads", "");
            case "fishing":
                return note.replace(" Fish", "");
            default:
                return note;
        }
    }
}
