package com.coxmegascale.party;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Versioned Party message for one confirmed defence-reduction result. The
 * legacy name remains for compatibility although target keys cover multiple
 * supported CoX NPC profiles.
 */
public class MysticsSpecUpdate extends PartyMemberMessage
{
    public static final int SCHEMA_VERSION = 1;
    public int schemaVersion;
    public String targetKey;
    public String weapon;
    public int amount;
    public boolean inCoxRaid;
    public int raidPartyId = -1;

    public MysticsSpecUpdate()
    {
    }

    public MysticsSpecUpdate(String targetKey, String weapon, int amount, boolean inCoxRaid, int raidPartyId)
    {
        this.schemaVersion = SCHEMA_VERSION;
        this.targetKey = targetKey;
        this.weapon = weapon;
        this.amount = Math.max(0, amount);
        this.inCoxRaid = inCoxRaid;
        this.raidPartyId = raidPartyId;
    }
}
