# Migration verification

Verified on 2026-10-28 with Minecraft 26.3, Java 25.0.3,
Fabric Loader 0.19.5, server Fabric API 0.162.0+26.3, and Polymer 0.18.5+26.3.
The original Fabric project still uses its unchanged dependency defaults.

Recipe-sync correction verified on 2026-10-29:

* The probe now explicitly requests `fabric:recipe_sync` and the shaped recipe
  serializer during configuration, and decodes the actual full recipe payload.
  Earlier verification did not negotiate this optional Fabric sync path and
  missed the reported non-empty recipe-result encoding failure.
* Companion clients receive the real randomizer recipe, with its remapped item
  ID checked. Fabric clients without the companion receive the vanilla shaped
  recipes with the randomizer recipe omitted. Vanilla clients receive no Fabric
  recipe-sync payload.
* Initial configuration-phase discovery and late play-phase opt-in are checked
  separately. Late opt-in refreshes previously filtered recipes. `/reload`
  preserves recipe filtering for each connected client.
* Registry mapping is queued in an explicit event phase after Fabric's channel
  tasks, independent of mod initializer order.
* All fixes remain in `fabric-server/`; upstream `common/` and `fabric/` are
  unchanged. Regression logs are under `build/recipe-sync-fix/`.

* `git diff --exit-code -- common fabric` passes: both upstream trees are restored.
* `:fabric-server:build :fabric:build :bukkit-server:build` passes. The third
  project is a placeholder and produces no Bukkit/Paper plugin.
* Dedicated backend startup and clean RCON shutdown pass.
* The existing-protocol play-channel probe passes all 16 gameplay/isolation
  assertions without sending or receiving `backend_hello`.
* The configuration-phase probe passes the gameplay/isolation checks with
  deliberately shifted local client item IDs and standard Fabric registry
  remapping. The tool, custom menu, editor changes, and crafting all work after
  remapping, without a Polymer client or a changed client protocol.
* Vanilla, original-client-protocol, and Fabric-without-companion connections
  remain connected simultaneously. Only the companion receives Effortless
  Building registry mappings and feature packets.
* The upstream compatibility guard rejects a deliberately incorrect server-owned
  fingerprint with the affected upstream filename. Restoring the manifest makes
  verification pass. Neither upstream source tree was changed for this test.
* The preserved pre-migration patch passes `git apply --check` against a separate
  extraction of its recorded base commit.
* The standalone server JAR contains no upstream client UI/controller classes or
  direct `net.minecraft.client` references in its own classes. The upstream client
  JAR contains no added backend classes or Polymer dependency.

| Test area | Verified behavior |
| --- | --- |
| Capability detection | Existing sync channels enable the session; no new hello packet is needed. Requests before channel discovery are ignored. |
| Registry mapping | Standard Fabric item/menu mapping handles different client-local item IDs. Vanilla and non-companion Fabric clients receive no Effortless Building mappings. |
| Building | Server-generated line placement and breaking work. |
| History/modifiers | Undo, redo, and array expansion work. |
| Permissions/materials | Spectator building is rejected; survival placement consumes only available materials. |
| Randomizer | The real item and server-backed editor work, including persisted ratio changes and placement. |
| Crafting | Modded players can craft the tool; identical vanilla crafting grids have an empty result. |
| Fabric full recipe sync | Companion receives 828 shaped recipes including the real tool; non-companion Fabric receives 827 vanilla shaped recipes. Filtering survives reload and late opt-in. |
| Vanilla isolation | Dropped tools cannot be picked up, hidden chest tools cannot be transferred, and normal chest items can be transferred. |
| Broadcasts | Operator configuration updates reach modded sessions while vanilla players stay connected without feature packets. |

The mapped run decoded 843 packets for the companion, 2,535 for vanilla, and
2,674 for Fabric without the companion. Local logs are preserved under
`fabric-server/build/reports/backend-compatibility/`.

The distributables are separate:

* Server: `fabric-server/build/libs/effortlessbuilding-fabric-server-26.3-4.3.jar`
* Unchanged client: `fabric/build/libs/effortlessbuilding-fabric-26.3-4.3.jar`

These are headless network integration tests using Mojang codecs and the original
client wire protocol, not a graphical client play-through. UI rendering/input,
AE2, third-party claim integration, and Bukkit/Paper functionality were not tested.
Existing Javadoc/deprecation warnings remain. See [README.md](README.md) for
installation, source-generation details, limitations, and reproduction commands.
