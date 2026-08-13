package com.coxmegascale;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxPriority;

/**
 * Preparation infobox for one tracked herb or secondary. It renders remaining
 * work from shared state and hides itself when prep information is not relevant.
 */
public class CoxMegascaleResourceInfoBox extends InfoBox
{
    private static final Color RED = new Color(255, 64, 64);
    private static final Color WHITE = Color.WHITE;
    private static final Color GREEN = new Color(0, 255, 0);

    private final CoxMegascaleState state;
    private final CoxMegascaleState.Resource resource;
    private final BooleanSupplier isVisible;

    CoxMegascaleResourceInfoBox(BufferedImage image, Plugin plugin, CoxMegascaleState state, CoxMegascaleState.Resource resource,
        BooleanSupplier isVisible)
    {
        super(image, plugin);
        this.state = state;
        this.resource = resource;
        this.isVisible = isVisible;
        setPriority(InfoBoxPriority.MED);
        setTooltip(resource.key + " remaining");
    }

    @Override
    public String getText()
    {
        return Integer.toString(state.getRemaining(resource));
    }

    @Override
    public Color getTextColor()
    {
        if (state.getRemaining(resource) == 0)
        {
            return GREEN;
        }
        return state.getCollected(resource) == 0 ? RED : WHITE;
    }

    @Override
    public boolean render()
    {
        return isVisible.getAsBoolean() && !state.isPrepInfoBoxesHidden() && state.getTarget(resource) > 0;
    }

    @Override
    public String getName()
    {
        return super.getName() + "_" + resource.key;
    }
}
