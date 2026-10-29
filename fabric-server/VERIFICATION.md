# Server backend verification

Verified on 2026-10-29 with Minecraft 26.3, Java 25.0.3,
Fabric Loader 0.19.5 and server Fabric API 0.162.0+26.3.
The upstream client project and its dependency defaults remain unchanged.

The randomizer is now removed from this backend. These results supersede the
earlier migration and recipe-sync checks that still supported that item.

* `:fabric-server:build :fabric:build :bukkit-server:build` passes. Bukkit remains
  a placeholder and produces no plugin.
* Dedicated startup and clean RCON shutdown pass without Polymer.
* Three headless clients (vanilla, companion protocol, Fabric without companion)
  remain connected simultaneously. Only the companion receives backend sync
  packets. The companion retains its upstream item/menu entries locally,
  including a deliberately shifted local item ID; no custom server mappings
  are needed or sent.
* Configuration-phase discovery and late play-phase opt-in both pass. Requests
  before capability discovery are rejected.
* Neither `/give` nor `/recipe give` recognizes the randomizer item/recipe for
  either player type. Identical former randomizer crafting grids have an empty
  result for both companion and vanilla players.
* `/reload` completes, sends normal recipe updates, and leaves the randomizer
  item/recipe absent.
* The server JAR contains no randomizer classes, editor, recipe, custom registry
  mapping, Polymer JARs, or randomizer recipe-sync mixins.
* `git diff --exit-code -- common fabric` passes. Shared adaptations exist only
  in server-owned generation rules and the checked patch, covering 23 classes.

| Test area | Verified behavior |
| --- | --- |
| Building | Server-generated line placement and breaking work. |
| History/modifiers | Undo, redo, and array expansion work. |
| Permissions/materials | Spectator building is rejected; survival uses only available materials. |
| Removed content | Randomizer is absent from item registration, recipes, crafting results, and server JAR. |
| Vanilla gameplay | Ordinary chest items transfer normally; vanilla and non-companion clients receive no backend feature payloads. |
| Broadcasts | Operator configuration updates reach companion sessions while vanilla stays connected. |
| Reload | Normal recipe synchronization succeeds; removed content remains absent. |

Logs are under `build/remove-randomizer/` (ignored build artifacts).
The existing test world also exercised loading older randomizer data: Minecraft
logs unknown item/recipe/statistic entries and drops the unsupported content.
Existing randomizer stacks are not migrated or preserved. The unchanged client
may still show its local creative entry; the server cannot supply that item.

Server artifact: `fabric-server/build/libs/effortlessbuilding-fabric-server-26.3-4.3.jar`.
Client artifact: `fabric/build/libs/effortlessbuilding-fabric-26.3-4.3.jar`.

These are real TCP integration tests using Mojang codecs and the companion wire
protocol, without a graphical Fabric client. UI rendering/input, AE2, third-party
claim integration, and Bukkit/Paper functionality were not tested. Recipe viewers
from other mods were not launched; the backend no longer enables or intercepts
Fabric full recipe sync. Existing Javadoc/deprecation warnings remain.
