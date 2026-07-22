# Bundled Asset Sources

All bundled images are static display assets. They are not downloaded at runtime.
Except for the RuneLite cache sprite noted below, the tracked source for each is
the Old School RuneScape Wiki file page for the matching game asset.

## Licence and Attribution

Created using intellectual property belonging to Jagex Limited under the terms
of Jagex's Fan Content Policy. This content is not endorsed by or affiliated
with Jagex.

The OSRS Wiki labels these inventory, NPC, interface, and item images as licensed
media of a copyrighted video game: copyright Jagex Ltd., used with permission.
The Wiki is the host/source page; it does not own the underlying game media.

Licence reference: https://oldschool.runescape.wiki/w/Project:Copyrights

The source ledger was verified on 2026-07-22 through the OSRS Wiki MediaWiki API
and direct image downloads. Inventory/interface files matched the downloaded
source either byte-for-byte or pixel-for-pixel; the six fixed-canvas room icons
listed below are proportional downscaled derivatives of the named Wiki renders.
The Wiki copyright page continued to identify these images as Jagex game media.
Do not replace an asset with art from an unknown or unlicensed source.

## Project Branding Art

| Bundled file | Original source | Licence |
| --- | --- | --- |
| `icon.png` | 48x48 derivative of `docs/branding/cox-assistant-logo-source.png`, supplied by the project owner from a ChatGPT image generation dated 2026-07-11. | Project branding used with the project owner's permission; Jagex-inspired fan content. |
| `src/main/resources/cox_assistant_sidebar.png` | 24x24 derivative of `docs/branding/cox-assistant-sidebar-source.png`, generated with OpenAI image generation from the owner-supplied logo as a visual reference on 2026-07-22. | Project branding used with the project owner's permission; Jagex-inspired fan content. |
| `src/main/resources/tab_icons/*.png` | Cropped 28x28 derivatives of `docs/branding/cox-assistant-tab-sprites-source.png`, generated with OpenAI image generation for this project on 2026-07-22. The four sprites depict a summary scroll, purple reward chest, raid team, and options cog. | Project UI art used with the project owner's permission; original fantasy pixel art. |

## External Brand Marks

These marks are used only on buttons that link directly to the named service.
They are kept in their official white form and resized proportionally without
redrawing, recolouring, or distortion.

| Bundled file | Official source | Usage |
| --- | --- | --- |
| `src/main/resources/brand_icons/github.png` | GitHub's official `GitHub_Invertocat_White.png` from https://brand.github.com/GitHub_Logos.zip | Social button linking to the project repository, as permitted by GitHub's logo guidance. |
| `src/main/resources/brand_icons/discord.png` | Discord's official white Symbol SVG from https://discord.com/branding | Digital button linking to the project's Discord server, following Discord's brand guidelines. |

## Runtime RuneLite Cache Sprites

The plugin no longer bundles copies of the two previously unresolved images.
It requests the following canonical game-cache sprites through RuneLite's
supported `ItemManager` and `SpriteManager` services at runtime:

| Display | RuneLite constant | Authoritative source |
| --- | --- | --- |
| Buchu leaf prep icon | `ItemID.RAIDS_BUCHULEAF` (`20908`) | https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/gameval/ItemID.java |
| Special-attack marker | `SpriteID.ICON_SWORDS` (`3029`), file `0` | https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/gameval/SpriteID.java |

These images remain game media supplied by the local RuneLite/OSRS cache. No
binary copy is distributed by this repository and no runtime network download
is performed by the plugin.

## OSRS Wiki Game-Media Files

| Bundled file | Original OSRS Wiki file page | Licence |
| --- | --- | --- |
| `point_icons/cave_worms.png` | https://oldschool.runescape.wiki/w/File:Cave_worms.png | Jagex game media, used with permission. |
| `point_icons/cavern_grubs.png` | https://oldschool.runescape.wiki/w/File:Cavern_grubs.png | Jagex game media, used with permission. |
| `point_icons/cicely.png` | https://oldschool.runescape.wiki/w/File:Cicely.png | Jagex game media, used with permission. |
| `point_icons/endarkened.png` | https://oldschool.runescape.wiki/w/File:Endarkened_juice.png | Jagex game media, used with permission. |
| `point_icons/elder.png` | https://oldschool.runescape.wiki/w/File:Elder_potion_(4).png | Jagex game media, used with permission. |
| `point_icons/fishing.png` | https://oldschool.runescape.wiki/w/File:Fishing_icon.png | Jagex game media, used with permission. |
| `point_icons/golpar.png` | https://oldschool.runescape.wiki/w/File:Golpar.png | Jagex game media, used with permission. |
| `point_icons/kindling.png` | https://oldschool.runescape.wiki/w/File:Kindling_(Chambers_of_Xeric).png | Jagex game media, used with permission. |
| `point_icons/kodai.png` | https://oldschool.runescape.wiki/w/File:Kodai_potion_(4).png | Jagex game media, used with permission. |
| `point_icons/magic_defence.png` | https://oldschool.runescape.wiki/w/File:Magic_defence_icon.png | Jagex game media, used with permission. |
| `point_icons/noxifer.png` | https://oldschool.runescape.wiki/w/File:Noxifer.png | Jagex game media, used with permission. |
| `point_icons/overload.png` | https://oldschool.runescape.wiki/w/File:Overload_(4)_(Chambers_of_Xeric).png | Jagex game media, used with permission. |
| `point_icons/prayer_enhance.png` | https://oldschool.runescape.wiki/w/File:Prayer_enhance_(4).png | Jagex game media, used with permission. |
| `point_icons/revitalisation.png` | https://oldschool.runescape.wiki/w/File:Revitalisation_potion_(4).png | Jagex game media, used with permission. |
| `point_icons/stinkhorn.png` | https://oldschool.runescape.wiki/w/File:Stinkhorn_mushroom.png | Jagex game media, used with permission. |
| `point_icons/twisted.png` | https://oldschool.runescape.wiki/w/File:Twisted_potion_(4).png | Jagex game media, used with permission. |
| `point_icons/xerics_aid.png` | https://oldschool.runescape.wiki/w/File:Xeric%27s_aid_(4).png | Jagex game media, used with permission. |
| `room_icons/guardian.png` | https://oldschool.runescape.wiki/w/File:Guardian_(Chambers_of_Xeric,_male).png | Proportional downscale of Jagex game media, used with permission. |
| `room_icons/ice_demon.png` | https://oldschool.runescape.wiki/w/File:Ice_demon.png | Jagex game media, used with permission. |
| `room_icons/mystics.png` | https://oldschool.runescape.wiki/w/File:Skeletal_mystic_(1).png | Proportional downscale of Jagex game media, used with permission. |
| `room_icons/olm.png` | https://oldschool.runescape.wiki/w/File:Great_Olm_icon_(mobile).png | Proportional downscale of Jagex game media, used with permission. |
| `room_icons/rope_mager.png` | https://oldschool.runescape.wiki/w/File:Deathly_mage.png | Proportional downscale of Jagex game media, used with permission. |
| `room_icons/rope_ranger.png` | https://oldschool.runescape.wiki/w/File:Deathly_ranger.png | Proportional downscale of Jagex game media, used with permission. |
| `room_icons/shamans.png` | https://oldschool.runescape.wiki/w/File:Lizardman_shaman_(1).png | Proportional downscale of Jagex game media, used with permission. |
| `spec_icons/bgs.png` | https://oldschool.runescape.wiki/w/File:Bandos_godsword.png | Jagex game media, used with permission. |
| `spec_icons/chicken.png` | https://oldschool.runescape.wiki/w/File:Raw_chicken.png | Jagex game media, used with permission. |
| `spec_icons/elder_maul.png` | https://oldschool.runescape.wiki/w/File:Elder_maul.png | Jagex game media, used with permission. |
| `spec_icons/emberlight.png` | https://oldschool.runescape.wiki/w/File:Emberlight.png | Jagex game media, used with permission. |
| `spec_icons/eye_of_ayak.png` | https://oldschool.runescape.wiki/w/File:Eye_of_ayak.png | Jagex game media, used with permission. |
| `spec_icons/ralos.png` | https://oldschool.runescape.wiki/w/File:Tonalztics_of_ralos.png | Jagex game media, used with permission. |
