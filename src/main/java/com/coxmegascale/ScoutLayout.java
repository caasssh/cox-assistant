package com.coxmegascale;

import java.util.Locale;

/**
 * User-selectable CoX layout families for scout notifications.
 */
public enum ScoutLayout
{
    FSCC("FSCC"),
    FS("FS"),
    SF("SF");

    private final String prefix;

    ScoutLayout(String prefix)
    {
        this.prefix = prefix;
    }

    public boolean matches(String layoutCode)
    {
        return layoutCode != null && layoutCode.toUpperCase(Locale.US).startsWith(prefix);
    }
}
