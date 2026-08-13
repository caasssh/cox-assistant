package com.coxmegascale;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxPriority;

/**
 * Conditional infobox for one weapon in the active Mystics defence plan. The
 * supplied visibility gate keeps global, raid, and scout requirements in the
 * plugin coordinator instead of duplicating them here.
 */
public class CoxMegascaleMysticsSpecInfoBox extends InfoBox
{
    private final CoxMegascaleState state;
    private final String weapon;
    private final BooleanSupplier isVisible;

    CoxMegascaleMysticsSpecInfoBox(BufferedImage image, Plugin plugin, CoxMegascaleState state, String weapon, BooleanSupplier isVisible)
    {
        super(image, plugin);
        this.state = state;
        this.weapon = weapon;
        this.isVisible = isVisible;
        setPriority(InfoBoxPriority.HIGH);
        setTooltip("Mystics " + weapon + " remaining");
    }

    @Override
    public String getText()
    {
        return Integer.toString(state.getMysticsSpecRemaining(weapon));
    }

    @Override
    public Color getTextColor()
    {
        return state.getMysticsSpecRemaining(weapon) == 0 ? Color.GREEN : Color.WHITE;
    }

    @Override
    public boolean render()
    {
        return isVisible.getAsBoolean() && state.isMysticsTrackerActive() && state.isMysticsSpecTracked(weapon);
    }

    @Override
    public String getName()
    {
        return super.getName() + "_mystics_" + weapon;
    }
}
