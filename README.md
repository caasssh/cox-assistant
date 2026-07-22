# CoX Assistant

CoX Assistant is a passive RuneLite sidebar plugin for planning and tracking
Chambers of Xeric megascale raids. It shows information already available to the
client; it never clicks, types, moves, switches gear, casts, prays, paths, or
otherwise plays the game for you.

## What It Does

- Detects the scouted CoX layout and identifies supported rooms.
- Detects scaled party size, with a manual scale option when needed.
- Shows NPC stats, magic-defence information, special-attack plans, and Olm max
  hits.
- Tracks the active room and separates personal/group room points from overload
  and fish-consumption points.
- Calculates a supported-room points estimate, current supported estimate,
  purple chance, and roll distribution. Unsupported rooms are not silently
  presented as exact estimates.
- Tracks overload sips, pre-Olm non-overload sips, and CoX fish by tier.
- Tracks preparation resources across inventory and CoX private storage, crafted
  Chambers potions, and personal prep targets. Confirmed crafted potions retain
  their consumed ingredients as prep credit, so preparation progress does not
  fall when resources are converted into potions.
- Shows expected Olm hands, head, and points per phase.
- Adds current-room NPC defence values to Attack menu entries for the supported
  CoX combat targets, using the in-raid scaled-party-size varbit and confirmed
  defence-reducing special hits. It does not add a boss list to the sidebar.
- Provides a Mystics defence planner with optional Emberlight and chicken routes,
  plus live in-game counters for confirmed Elder Maul, Ralos, Emberlight, and
  BGS progress.
- Shows a Team tab with Party-shared points, death losses, room state,
  consumables, each member's prep progress, and the most recently observed CoX
  shared-prep storage when RuneLite Party is available.

## Estimates and Community Testing

Values labelled as estimates are community-calibrated guidance, not guaranteed
game results. Some combat-room totals are based on a limited set of live raid
fixtures and extrapolate to larger scales. Purple odds calculated from an
estimated total inherit that uncertainty. Actual tracked point changes remain
separate from estimates.

After each completed raid, CoX Assistant writes one local JSON summary to
`%USERPROFILE%\.runelite\cox-assistant\raid-logs`. This is on by default and
records the raid time, mode/layout/scale, participant display names,
configured Olm killers, final points and purple odds, tracked room times and
personal/group room-point shares, deaths, and tracked consumable points. Names
are collected passively from the in-game CoX raiding-party list and retained if
someone leaves before completion. Delete files from `raid-logs` whenever they
are no longer wanted, and review them before sharing because they contain every
team display name the client observed.

Testers can improve these formulas by enabling **Log point fixtures** in the
RuneLite configuration. This setting is off by default and writes local JSONL
files under `%USERPROFILE%\.runelite\cox-assistant\point-fixtures`, separated
into `normal`, `full`, and `cm` folders. One anonymous file is kept for each
actual raid, including across RuneLite Raids-plugin reloads and re-scouts.
Records contain a schema version and ordered elapsed time, start reason, raid
mode/layout, detected scale and input sources, local room context,
the compatible-Party aggregate Thieving level and its source, the local/configured
Fishing level and its source, personal/group points and deltas, conservative
observed-event annotations, and completion/end state. Personal points and deltas
are retained to distinguish local contribution or death effects while validating
simultaneous group-point changes. They do not contain display names, member ids, worlds,
coordinates, wall-clock timestamps, login details, account/session data,
private messages, or Party payloads.

The logger keeps at most 100 files per raid-mode folder. Disable logging to stop
new records; delete the `point-fixtures` folder to remove existing records.
The plugin never uploads logs automatically. Use **Options → View logs** to open
the local CoX Assistant folder. Review every file before sharing it. Upload
**only `.jsonl` files from `point-fixtures`** to the **`#point-data`** channel in
the [CoX Assistant Discord](https://discord.gg/w5Bm6jsVZS). Never upload files
from `raid-logs`; those summaries can contain every participant display name
observed during the raid.

## RuneLite Party and Privacy

RuneLite Party is optional. Local layout, stats, preparation, points, and
spec-planning features work without it. Party sharing starts automatically only
while the player is in a RuneLite Party; there is no separate external service.

When you join a RuneLite Party, the plugin can share data with the other members
of that Party who are also using this plugin version. That enables the Team tab,
group consumable totals, automatic Thieving totals, and shared Mystics
special-progress counters. The shared data is limited to your RuneLite display
name, RuneLite Party member id, real Thieving level, whether the client is in a
raid and its raid-party id, personal raid points, active room, confirmed local
raid-death point loss and time, overload/non-overload sip counts, consumed CoX
fish type/count, confirmed per-target raid defence progress (target key, weapon,
and successful hit count or damage), personal CoX prep-resource totals
(inventory plus private raid storage), crafted Chambers-potion totals, and a
timestamped shared CoX prep-storage snapshot. A one-bit snapshot-request flag is
also sent when a client joins an active raid, along with an integer message
schema version used to reject incompatible payloads. Storage data contains only
tracked
raid herbs, secondaries, and Chambers potions; it does not include gear or
unrelated bank contents. Other Party members appear only when they run a
compatible plugin version and report the same CoX raid.

The plugin does not read or collect login details, account/session data, private
messages, or real-world personal information. Party data is sent only through
RuneLite Party; the completion summaries described above retain known raid
display names locally and are never transmitted by the plugin.

## Defence Tracker Attribution

The current-room menu tracker is an independent implementation. Its broader
CoX target coverage and party-size/defence-drain model were informed by the
public [Party Defence Tracker plugin by Hyftar](https://github.com/Hyftar/party-defence-tracker).
No source code was copied; the target profiles and calculations are maintained
in this project’s own model and remain subject to live mechanics validation.

## Configuration

The RuneLite configuration panel controls infobox visibility and startup
defaults. Raid-specific options and manual overrides are available in the
plugin's Options tab. Changed startup defaults take effect when the plugin is
next restarted; the current panel can be changed directly without restarting.

## Licence

The plugin source is released under the [BSD 2-Clause License](LICENSE). Bundled game-media
assets remain copyrighted by Jagex Ltd.; see [ASSET_SOURCES.md](ASSET_SOURCES.md).

Created using intellectual property belonging to Jagex Limited under the terms
of Jagex's Fan Content Policy. This content is not endorsed by or affiliated
with Jagex.
