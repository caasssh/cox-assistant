package com.coxmegascale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.InputStream;
import java.awt.image.BufferedImage;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import org.junit.Test;

public class CoxMegascalePanelTest
{
    @Test
    public void requestsSpecialAttackSpriteAsynchronouslyDuringSwingStartup() throws Exception
    {
        SpriteManager spriteManager = mock(SpriteManager.class);
        ItemManager itemManager = mock(ItemManager.class);

        SwingUtilities.invokeAndWait(() -> new CoxMegascalePanel(
            100,
            0,
            75,
            new CoxMegascaleState(),
            () -> { },
            mock(SkillIconManager.class),
            true,
            show -> { },
            () -> { },
            itemManager,
            spriteManager
        ));

        verify(spriteManager).getSpriteAsync(
            eq(SpriteID.ICON_SWORDS), eq(0), org.mockito.ArgumentMatchers.<Consumer<BufferedImage>>any());
        verify(spriteManager, never()).getSprite(eq(SpriteID.ICON_SWORDS), eq(0));
    }

    @Test
    public void coalescesBurstRefreshesDuringRoomLoading() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            CoxMegascalePanel panel = new CoxMegascalePanel(100, 0, 75, new CoxMegascaleState(),
                () -> { }, mock(SkillIconManager.class));
            try
            {
                Field refreshTimer = CoxMegascalePanel.class.getDeclaredField("refreshTimer");
                refreshTimer.setAccessible(true);
                assertEquals(CoxMegascalePanel.REFRESH_DEBOUNCE_MILLIS,
                    ((Timer) refreshTimer.get(panel)).getDelay());
            }
            catch (ReflectiveOperationException exception)
            {
                throw new AssertionError(exception);
            }
        });
    }

    @Test
    public void buildsOnlyTheSelectedTabWithTheFourConsolidatedTabs() throws Exception
    {
        AtomicReference<Throwable> failure = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                CoxMegascalePanel panel = new CoxMegascalePanel(100, 0, 75, new CoxMegascaleState(),
                    () -> { }, mock(SkillIconManager.class));
                Method refresh = CoxMegascalePanel.class.getDeclaredMethod("refresh");
                refresh.setAccessible(true);
                Field selectedTab = CoxMegascalePanel.class.getDeclaredField("selectedTab");
                selectedTab.setAccessible(true);
                Field tabPanels = CoxMegascalePanel.class.getDeclaredField("tabPanels");
                tabPanels.setAccessible(true);
                Field resourceIcons = CoxMegascalePanel.class.getDeclaredField("resourceIcons");
                resourceIcons.setAccessible(true);

                refresh.invoke(panel);
                Map<String, JPanel> tabs = (Map<String, JPanel>) tabPanels.get(panel);
                assertEquals(Arrays.asList("Summary", "Points", "Team", "Options"), Arrays.asList(tabs.keySet().toArray()));
                assertTrue(tabs.get("Summary").getComponentCount() > 0);
                assertEquals(0, tabs.get("Points").getComponentCount());

                selectedTab.set(panel, "Points");
                refresh.invoke(panel);
                assertTrue(tabs.get("Points").getComponentCount() > 0);
                JPanel pointsPanel = tabs.get("Points");
                int resourceIconCount = ((Map<?, ?>) resourceIcons.get(panel)).size();

                refresh.invoke(panel);
                assertEquals(resourceIconCount, ((Map<?, ?>) resourceIcons.get(panel)).size());
                assertTrue("Refresh should preserve the visible tab container", pointsPanel == tabs.get("Points"));
            }
            catch (Throwable throwable)
            {
                failure.set(throwable);
            }
        });

        if (failure.get() != null)
        {
            throw new AssertionError(failure.get());
        }
    }

    @Test
    public void ordersStatsRoomsForTheThreeColumnSelectorAndBundlesItsSprite() throws Exception
    {
        assertEquals(Arrays.asList("Mystics", "Shamans", "Guardians", "Rope Mager", "Rope Ranger", "Ice Demon", "Mage Hand", "Olm Head", "Melee Hand"),
            CoxMegascalePanel.orderedStatsRooms(Arrays.asList("Guardians", "Ice Demon", "Olm Head", "Melee Hand", "Mage Hand", "Rope Ranger", "Mystics", "Rope Mager", "Shamans")));
        try (InputStream stream = CoxMegascalePanel.class.getClassLoader().getResourceAsStream("room_icons/ice_demon.png"))
        {
            assertNotNull(stream);
        }
        for (String potionIcon : Arrays.asList("elder.png", "kodai.png", "twisted.png"))
        {
            try (InputStream stream = CoxMegascalePanel.class.getClassLoader().getResourceAsStream("point_icons/" + potionIcon))
            {
                assertNotNull(stream);
            }
        }
    }

    @Test
    public void formatsCompactTeamPointsAndDeathLosses()
    {
        assertEquals("25k - 2.9%", CoxMegascalePanel.formatTeamPoints(25_000));
        assertEquals("(-2.5k)", CoxMegascalePanel.formatTeamDeathLoss(2_500));
        assertEquals("(-900)", CoxMegascalePanel.formatTeamDeathLoss(900));
    }

    @Test
    public void fixedLayoutsUseOneRoomColorWhileNormalLayoutsRetainTheirColors()
    {
        assertTrue(CoxMegascalePanel.usesUniformFixedLayoutRoomColor("Full"));
        assertTrue(CoxMegascalePanel.usesUniformFixedLayoutRoomColor("Challenge Mode"));
        assertEquals(false, CoxMegascalePanel.usesUniformFixedLayoutRoomColor("Unknown"));
        assertEquals(false, CoxMegascalePanel.usesUniformFixedLayoutRoomColor("normal"));
    }

    @Test
    public void laysOutPurpleDistributionForOneThroughFourRolls()
    {
        assertEquals(1, CoxMegascalePanel.purpleDistributionColumns(1));
        assertEquals(1, CoxMegascalePanel.purpleDistributionColumns(2));
        assertEquals(3, CoxMegascalePanel.purpleDistributionColumns(3));
        assertEquals(2, CoxMegascalePanel.purpleDistributionColumns(4));
    }

    @Test
    public void trimsTransparentPaddingBeforeNormalizingNumericGridSprites()
    {
        BufferedImage padded = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        padded.setRGB(2, 3, 0xFFFFFFFF);
        padded.setRGB(4, 5, 0xFFFFFFFF);

        BufferedImage trimmed = CoxMegascalePanel.trimTransparentPadding(padded);
        assertEquals(3, trimmed.getWidth());
        assertEquals(3, trimmed.getHeight());
    }

    @Test
    public void leftAnchorsIconOnlyStatsMarkers() throws Exception
    {
        CoxMegascalePanel panel = new CoxMegascalePanel(100, 0, 75, new CoxMegascaleState(), () -> { }, mock(SkillIconManager.class));
        Method iconOnlyCell = CoxMegascalePanel.class.getDeclaredMethod("iconOnlyCell", ImageIcon.class, String.class);
        iconOnlyCell.setAccessible(true);
        JPanel cell = (JPanel) iconOnlyCell.invoke(panel,
            new ImageIcon(new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB)), "Special attack requirements");
        cell.setSize(70, 30);
        cell.doLayout();

        assertEquals(5, cell.getComponent(0).getX());
        assertEquals(SwingConstants.LEFT, ((JLabel) cell.getComponent(0)).getHorizontalAlignment());
    }

    @Test
    public void useAutoRestoresEveryAutomaticOption() throws Exception
    {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                CoxMegascaleState state = new CoxMegascaleState();
                int defaultOlmKillers = state.getOlmKillers();
                CoxMegascalePanel panel = new CoxMegascalePanel(100, 0, 75, state, () -> { }, mock(SkillIconManager.class));
                setSpinnerValue(panel, "scaleSpinner", 50);
                setSpinnerValue(panel, "thievingSpinner", 200);
                setSpinnerValue(panel, "fishingSpinner", 99);
                setSpinnerValue(panel, "olmKillersSpinner", 10);
                setSpinnerValue(panel, "overloadOverrideSpinner", 12);
                setSpinnerValue(panel, "revitalisationTargetSpinner", 1);
                setSpinnerValue(panel, "xericsAidTargetSpinner", 1);
                setSpinnerValue(panel, "prayerEnhanceTargetSpinner", 12);
                setSpinnerValue(panel, "caveWormTargetSpinner", 99);

                Method reset = CoxMegascalePanel.class.getDeclaredMethod("resetAutomaticOptions");
                reset.setAccessible(true);
                reset.invoke(panel);

                assertEquals(100, spinnerValue(panel, "scaleSpinner"));
                assertEquals(0, spinnerValue(panel, "thievingSpinner"));
                assertEquals(75, spinnerValue(panel, "fishingSpinner"));
                assertEquals(defaultOlmKillers, spinnerValue(panel, "olmKillersSpinner"));
                assertEquals(state.getOverloadsPerKiller(), spinnerValue(panel, "overloadOverrideSpinner"));
                assertEquals(state.getRevitalisationTarget(), spinnerValue(panel, "revitalisationTargetSpinner"));
                assertEquals(state.getXericsAidTarget(), spinnerValue(panel, "xericsAidTargetSpinner"));
                assertEquals(state.getPrayerEnhanceTarget(), spinnerValue(panel, "prayerEnhanceTargetSpinner"));
                assertEquals(state.getCaveWormTarget(), spinnerValue(panel, "caveWormTargetSpinner"));
            }
            catch (Throwable throwable)
            {
                failure.set(throwable);
            }
        });

        if (failure.get() != null)
        {
            throw new AssertionError(failure.get());
        }
    }

    @Test
    public void unknownSummaryDoesNotPresentSpeculativeExpectedPoints() throws Exception
    {
        CoxMegascalePanel panel = new CoxMegascalePanel(100, 0, 75, new CoxMegascaleState(), () -> { }, mock(SkillIconManager.class));
        Method refresh = CoxMegascalePanel.class.getDeclaredMethod("refreshSelectedTab");
        refresh.setAccessible(true);
        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                refresh.invoke(panel);
            }
            catch (Exception exception)
            {
                throw new RuntimeException(exception);
            }
        });

        assertTrue(hasExactLabel(panel, "Waiting for layout"));
        assertEquals(false, hasExactLabel(panel, "Expected"));
        assertEquals(false, hasExactLabel(panel, "Rolls"));
    }

    @Test
    public void prepGridUsesThreeColumnsAtNormalWidthAndFallsBackWhenNarrow() throws Exception
    {
        Method prepGrid = CoxMegascalePanel.class.getDeclaredMethod("prepGrid");
        prepGrid.setAccessible(true);
        JPanel grid = (JPanel) prepGrid.invoke(null);
        assertEquals(3, ((GridLayout) grid.getLayout()).getColumns());
        assertEquals(3, CoxMegascalePanel.prepGridColumns(225));
        assertEquals(2, CoxMegascalePanel.prepGridColumns(219));
    }

    @Test
    public void prepSpinnersUseThreeDigitPotionBoundsAndFishingException() throws Exception
    {
        CoxMegascalePanel panel = new CoxMegascalePanel(100, 0, 75, new CoxMegascaleState(), () -> { }, mock(SkillIconManager.class));
        assertEquals(999, spinnerMaximum(panel, "overloadOverrideSpinner"));
        assertEquals(999, spinnerMaximum(panel, "revitalisationTargetSpinner"));
        assertEquals(999, spinnerMaximum(panel, "xericsAidTargetSpinner"));
        assertEquals(999, spinnerMaximum(panel, "prayerEnhanceTargetSpinner"));
        assertEquals(CoxMegascaleState.MAX_CAVE_WORM_TARGET, spinnerMaximum(panel, "caveWormTargetSpinner"));
    }

    @Test
    public void zeroPotionTargetsAreManualTargetsRatherThanAutomaticFallbacks() throws Exception
    {
        CoxMegascaleState state = new CoxMegascaleState();
        CoxMegascalePanel panel = new CoxMegascalePanel(1, 0, 75, state, () -> { }, mock(SkillIconManager.class));
        Method refresh = CoxMegascalePanel.class.getDeclaredMethod("refreshSelectedTab");
        refresh.setAccessible(true);
        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                refresh.invoke(panel);
                setSpinnerValue(panel, "overloadOverrideSpinner", 0);
                setSpinnerValue(panel, "prayerEnhanceTargetSpinner", 0);
                refresh.invoke(panel);
            }
            catch (Exception exception)
            {
                throw new RuntimeException(exception);
            }
        });

        assertEquals(0, state.getOverloadsPerKiller());
        assertEquals(0, state.getPrayerEnhanceTarget());
        CoxMegascaleState.Resource golpar = state.getResources().stream()
            .filter(resource -> "Golpar".equals(resource.key))
            .findFirst()
            .orElseThrow(AssertionError::new);
        assertEquals(0, state.getTarget(golpar));
    }

    @Test
    public void caveWormPrepRowIsConditional() throws Exception
    {
        CoxMegascaleState state = new CoxMegascaleState();
        CoxMegascalePanel panel = new CoxMegascalePanel(100, 0, 75, state, () -> { }, mock(SkillIconManager.class));
        assertEquals(false, hasToolTip(panel, "Cave worms collected / target"));

        state.setCaveWormTarget(1_000);
        setSpinnerValue(panel, "caveWormTargetSpinner", 1_000);
        Method refresh = CoxMegascalePanel.class.getDeclaredMethod("refreshSelectedTab");
        refresh.setAccessible(true);
        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                refresh.invoke(panel);
            }
            catch (Exception exception)
            {
                throw new RuntimeException(exception);
            }
        });
        assertEquals(true, hasToolTip(panel, "Cave worms collected / target"));
    }

    @Test
    public void tabButtonsRemainKeyboardFocusable() throws Exception
    {
        CoxMegascalePanel panel = new CoxMegascalePanel(100, 0, 75, new CoxMegascaleState(), () -> { }, mock(SkillIconManager.class));
        Field field = CoxMegascalePanel.class.getDeclaredField("tabButtons");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, JButton> buttons = (Map<String, JButton>) field.get(panel);
        assertEquals(4, buttons.size());
        assertTrue(buttons.values().stream().allMatch(Component::isFocusable));
        assertTrue(buttons.values().stream().allMatch(button -> button.getText().isEmpty()));
        assertTrue(buttons.values().stream().allMatch(button -> button.getIcon() != null));
        assertTrue(buttons.values().stream().allMatch(button -> button.getToolTipText() != null));
        assertTrue(buttons.values().stream().allMatch(button -> button.getAccessibleContext().getAccessibleName() != null));
        for (String icon : Arrays.asList("summary.png", "points.png", "team.png", "options.png"))
        {
            try (InputStream stream = CoxMegascalePanel.class.getClassLoader().getResourceAsStream("tab_icons/" + icon))
            {
                assertNotNull(stream);
            }
        }
    }

    @Test
    public void welcomeStartupCheckboxUsesPersistedValueAndWritesChanges() throws Exception
    {
        AtomicBoolean savedValue = new AtomicBoolean(true);
        CoxMegascalePanel panel = new CoxMegascalePanel(100, 0, 75, new CoxMegascaleState(),
            () -> { }, mock(SkillIconManager.class), false, savedValue::set);
        JCheckBox checkBox = findCheckBox(panel, "Show on startup");

        assertNotNull(checkBox);
        assertEquals(false, checkBox.isSelected());
        SwingUtilities.invokeAndWait(checkBox::doClick);
        assertTrue(savedValue.get());
    }

    @Test
    public void welcomePageSurvivesQueuedRefreshUntilDismissed() throws Exception
    {
        CoxMegascalePanel panel = new CoxMegascalePanel(100, 0, 75, new CoxMegascaleState(), () -> { }, mock(SkillIconManager.class));
        Field cardsField = CoxMegascalePanel.class.getDeclaredField("cards");
        cardsField.setAccessible(true);
        JPanel cards = (JPanel) cardsField.get(panel);

        SwingUtilities.invokeAndWait(() ->
        {
            panel.showWelcomePage();
            try
            {
                Method refresh = CoxMegascalePanel.class.getDeclaredMethod("refreshSelectedTab");
                refresh.setAccessible(true);
                refresh.invoke(panel);
            }
            catch (Exception exception)
            {
                throw new RuntimeException(exception);
            }
        });

        assertTrue(cards.getComponent(4).isVisible());
    }

    @Test
    public void optionsViewLogsButtonRunsFolderAction() throws Exception
    {
        AtomicBoolean opened = new AtomicBoolean();
        CoxMegascalePanel panel = new CoxMegascalePanel(100, 0, 75, new CoxMegascaleState(),
            () -> { }, mock(SkillIconManager.class), true, show -> { }, () -> opened.set(true));

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                Field selectedTab = CoxMegascalePanel.class.getDeclaredField("selectedTab");
                selectedTab.setAccessible(true);
                selectedTab.set(panel, "Options");
                Method refresh = CoxMegascalePanel.class.getDeclaredMethod("refresh");
                refresh.setAccessible(true);
                refresh.invoke(panel);
                JButton button = findButton(panel, "View logs");
                assertNotNull(button);
                button.doClick();
            }
            catch (Exception exception)
            {
                throw new RuntimeException(exception);
            }
        });
        assertTrue(opened.get());
    }

    private static void setSpinnerValue(CoxMegascalePanel panel, String fieldName, int value) throws Exception
    {
        Field field = CoxMegascalePanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((JSpinner) field.get(panel)).setValue(value);
    }

    private static boolean hasExactLabel(Container parent, String text)
    {
        for (Component component : parent.getComponents())
        {
            if (component instanceof JLabel && text.equals(((JLabel) component).getText()))
            {
                return true;
            }
            if (component instanceof Container && hasExactLabel((Container) component, text))
            {
                return true;
            }
        }
        return false;
    }

    private static JCheckBox findCheckBox(Container parent, String text)
    {
        for (Component component : parent.getComponents())
        {
            if (component instanceof JCheckBox && text.equals(((JCheckBox) component).getText()))
            {
                return (JCheckBox) component;
            }
            if (component instanceof Container)
            {
                JCheckBox nested = findCheckBox((Container) component, text);
                if (nested != null)
                {
                    return nested;
                }
            }
        }
        return null;
    }

    private static JButton findButton(Container parent, String text)
    {
        for (Component component : parent.getComponents())
        {
            if (component instanceof JButton && text.equals(((JButton) component).getText()))
            {
                return (JButton) component;
            }
            if (component instanceof Container)
            {
                JButton nested = findButton((Container) component, text);
                if (nested != null)
                {
                    return nested;
                }
            }
        }
        return null;
    }

    private static boolean hasToolTip(Container parent, String text)
    {
        if (parent instanceof JComponent && text.equals(((JComponent) parent).getToolTipText()))
        {
            return true;
        }
        for (Component component : parent.getComponents())
        {
            if (component instanceof JComponent && text.equals(((JComponent) component).getToolTipText()))
            {
                return true;
            }
            if (component instanceof Container && hasToolTip((Container) component, text))
            {
                return true;
            }
        }
        return false;
    }


    private static int spinnerValue(CoxMegascalePanel panel, String fieldName) throws Exception
    {
        Field field = CoxMegascalePanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Integer) ((JSpinner) field.get(panel)).getValue();
    }

    private static int spinnerMaximum(CoxMegascalePanel panel, String fieldName) throws Exception
    {
        Field field = CoxMegascalePanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Integer) ((javax.swing.SpinnerNumberModel) ((JSpinner) field.get(panel)).getModel()).getMaximum();
    }
}
