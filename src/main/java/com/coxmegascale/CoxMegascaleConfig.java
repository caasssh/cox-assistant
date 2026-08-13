package com.coxmegascale;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

/**
 * Persistent RuneLite settings for global visibility, local fixture logging,
 * welcome-page behavior, and the defaults used to seed a new sidebar session.
 */
@ConfigGroup(CoxMegascaleConfig.GROUP)
public interface CoxMegascaleConfig extends Config
{
	String GROUP = "coxmegascale";

	@ConfigItem(
		keyName = "showInfoBoxes",
		name = "Show infoboxes",
		description = "Show CoX Assistant infoboxes while inside a scouted raid."
	)
	default boolean showInfoBoxes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pointFixtureLogging",
		name = "Log point fixtures",
		description = "Opt-in anonymous local JSONL logging. Review and manually share only point-fixtures files in Discord #point-data."
	)
	default boolean pointFixtureLogging()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showWelcomeOnStartup",
		name = "Show welcome on startup",
		description = "Open the CoX Assistant welcome, usage, and estimate notice whenever the plugin starts."
	)
	default boolean showWelcomeOnStartup()
	{
		return true;
	}

    @Range(min = 1, max = 100)
    @ConfigItem(
        keyName = "defaultScale",
        name = "Default scale",
        description = "Default raid scale used when the panel opens."
    )
    default int defaultScale()
    {
        return 100;
    }

    @Range(min = 0, max = 5000)
    @ConfigItem(
        keyName = "defaultThievingLevel",
        name = "Default total thieving",
        description = "Default total thieving level for thieving room point estimates."
    )
    default int defaultThievingLevel()
    {
        return 0;
    }

    @Range(min = 60, max = 99)
    @ConfigItem(
        keyName = "defaultFishingLevel",
        name = "Default fishing level",
        description = "Default fishing level for fishing room point estimates."
    )
    default int defaultFishingLevel()
    {
        return 75;
    }
}
