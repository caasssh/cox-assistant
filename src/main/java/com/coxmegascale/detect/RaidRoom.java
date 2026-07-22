package com.coxmegascale.detect;

public enum RaidRoom
{
    GUARDIANS("Guardians"),
    MYSTICS("Mystics"),
    SHAMANS("Shamans"),
    TIGHTROPE("Tightrope"),
    THIEVING("Thieving"),
    ICE_DEMON("Ice Demon"),
    OLM("Olm");

    private final String displayName;

    RaidRoom(String displayName)
    {
        this.displayName = displayName;
    }

    public String getDisplayName()
    {
        return displayName;
    }
}
