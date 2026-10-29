# Fabric server backend (Minecraft 26.3)

Install the standalone JAR from `fabric-server/build/libs/` on the dedicated
server. Modded players install the unchanged upstream JAR from `fabric/build/libs/`.
Do not install both artifacts on the server; their registrations would conflict. The server needs Java 25, Fabric Loader
0.19.5 or newer, and Fabric API 0.162.0+26.3 or newer. Polymer Core and its support
libraries are bundled in the mod JAR; no resource pack is required.

Vanilla Minecraft 26.3 players can join the same server. They do not need this
mod, Fabric, Polymer, or a resource pack. The backend uses the original client
protocol; the client does not need a new hello packet or a Polymer installation.
Use a client built for the same upstream Minecraft/protocol version.

## Features and authority

The existing shape generators, mirrors, arrays, radials, replace filters,
weighted randomizer, survival material consumption, tool constraints, and
undo/redo run through the shared server pipeline. The client keeps its radial
menu, previews, shortcuts, and configuration screens. The server regenerates
positions from the requested mode and corners; clients cannot supply the final
block set.

The randomizer remains a real custom item for modded players, with its existing
recipe, palette, ratio editor, model, and tooltip. Shift-right-click opens its
server-backed editor. Palette slots do not consume their sample items; survival
building consumes the selected materials from inventory.

World configuration and per-player modifiers retain their existing files under
`<world>/effortlessbuilding/`. Only operators can update world configuration.
Undo/redo and placed-block tracking remain session-scoped, as before.

## Connection and content isolation

* The server recognizes the original client's three existing synchronization
  channels. It enables the session once all are advertised, with support for
  both configuration-phase and later play-phase registrations.
* Standard Fabric registry synchronization maps the real tool/menu IDs for
  unchanged clients. The backend defers this decision until Fabric's normal
  common-channel tasks finish; vanilla and Fabric clients without Effortless
  Building receive no Effortless Building registry entries.
* Vanilla connections never have to answer a mod handshake or complete a custom
  login/configuration task. Fabric/Polymer's optional discovery messages are
  safely ignored by vanilla clients.
* Every backend operation requires an enabled session. Every outgoing mod sync,
  including configuration broadcasts and AE2 replies, checks both session state
  and support for that particular channel.
* Polymer marks the randomizer item and menu as server-only registry entries.
  Item encoding returns the original stack to clients advertising the companion's
  channels (including initial synchronization before JOIN) and an **empty
  stack** to other recipients. There is no vanilla substitute tool. This applies
  to equipment, dropped items, shared containers, and nested item components.
* Vanilla players cannot pick up dropped tools, transfer tools from containers,
  use the tool, or craft it. The tool recipe is hidden from their recipe books
  and from Fabric's optional full recipe synchronization. Recipes are omitted
  entirely rather than encoding an empty result, which the non-empty recipe
  codec cannot represent. Companion clients retain the real recipe; late channel
  registration refreshes both recipe books and negotiated Fabric recipe data.
  Shaped recipes are offered to Fabric recipe viewers that request them; clients
  that do not negotiate recipe synchronization receive no such payload.
  Automated crafting of the tool is disabled because it has no modded player
  context.
* Disconnect removes server capabilities and session state. Client behavior is
  exactly the upstream behavior; this module does not modify client cleanup,
  rendering, click interception, or singleplayer initialization.

[Polymer's item API](https://polymer.pb4.eu/latest/polymer-core/items/) and
[registry filtering implementation](https://github.com/Patbox/polymer/tree/dev/26.3/polymer-reg-sync-manipulator)
provide the per-recipient content translation. The mod does not implement
vanilla versions of its features.

## Server validation

The backend rejects spectator/adventure building, out-of-reach initial anchors,
unloaded chunks, out-of-height/border positions, and vanilla spawn protection.
Survival also rejects unbreakable blocks. Undo/redo checks game mode, dimension,
loaded chunks, borders, and spawn protection before changing the world.

Calculation safety limits supplement the normal server configuration: at most
32 modifiers, 200,000 generated positions, and 1,000,000 position-generation
operations. Selection bounds may cover at most 1,000,000 cells, with at most
1,000 cells per axis; centered circular shapes may scan a larger derived area.
Requests exceeding these budgets are rejected before world mutation. Radial
slices respect the configured array count limit. Connections are limited to
40 backend requests per 20 server ticks.

## Architecture and keeping up with upstream

`common/` and `fabric/` are unchanged from the original fork. Root wiring adds
`fabric-server` and the `bukkit-server` placeholder; root dependency defaults are
also unchanged. No `common-server` module is introduced.

`fabric-server/src/main/java` owns the backend, networking adapter, bootstrap,
Polymer filtering, and compatibility mixins. It compiles shared gameplay sources
from `common/` into its separate server JAR. It does not package the upstream
Fabric initializer, client UI, renderer, or client networking implementation.

Server-specific changes to shared code are represented by
`fabric-server/patches/common-server.patch`. The build copies source inputs into
`fabric-server/build/generated/upstream-java/` and applies that patch only there.
It never writes to the original source tree. A normalized SHA-256 manifest covers
the 24 adapted classes. An upstream change in one of those classes stops the
server build with the changed filename and an instruction to review its patch.
The patch applier also checks every context line; there is no fuzzy application.

After merging upstream:

1. Run `:fabric-server:verifyUpstreamCompatibility`.
2. Review changed classes and their corresponding patch hunks.
3. Update the server patch and fingerprints after reviewing the upstream behavior.
4. Build both artifacts and run the compatibility probes below.
5. Confirm `git diff --exit-code -- common fabric` remains clean.

The complete implementation from before migration, including client changes
that are intentionally absent from the new module, is preserved as a reference
in [migration/initial-backend.patch](migration/initial-backend.patch). It is not a
build input and should only be applied to a separate clean checkout at its
recorded base commit.

`bukkit-server/` is a Gradle placeholder using only the `base` plugin; it produces
no Bukkit plugin. Shared server abstractions can be extracted once the Bukkit
adapter establishes what is reusable. Existing server code still depends on
Minecraft implementation classes and is not a portable Bukkit library.

## Reproducing verification

Build the distributable:

```powershell
.\gradlew.bat :fabric-server:build :fabric:build
```

Start the isolated test server in one terminal (the EULA flag applies only to
`fabric-server/build/backend-test/server`):

```powershell
.\gradlew.bat :fabric-server:runBackendTestServer -PacceptMinecraftEula=true
```

After the server reports `Done`, run the mixed-client integration probe in a
second terminal:

```powershell
.\gradlew.bat :fabric-server:backendProbe -PprobeMode=mixed
```

Stop the test server cleanly:

```powershell
.\gradlew.bat :fabric-server:backendProbe -PprobeMode=stop
```

The fixture binds Minecraft to **127.0.0.1:25576** and test-only RCON to
**127.0.0.1:25577**. It uses an offline test world and the fixed test-only RCON
password in `fabric-server/gradle/backend-probe.gradle`. The probe changes only that world's blocks,
configuration, inventories, recipes, and the named test accounts. These
settings are not suitable for a public server.

For full configuration-phase registry mapping, run:

```powershell
.\gradlew.bat :fabric-server:backendProbe -PprobeMode=mapped
```

This mode starts vanilla, original-client-protocol, and Fabric-without-companion
connections together. It deliberately changes the companion's local item IDs,
decodes Fabric's standard registry sync, and verifies the real randomizer and
menu still work. Neither companion probe loads Polymer on the client.

The probe opens real TCP connections using Mojang's 26.3 networking and registry
decoders without launching Fabric or mods in the client processes. One process
keeps strictly vanilla registries and sends no mod opt-in; the other emulates
the companion's registries and packets. Test code is a separate source set and
is excluded from the mod JAR. `:fabric-server:check` also compiles the probe; the network
tests require the explicitly started fixture server.

See [verification results](VERIFICATION.md) for the completed run.

## Limits and remaining manual checks

This is compatibility for Effortless Building plus Fabric API and its bundled
libraries. Installing other mods with mandatory client content, such as AE2,
can independently make vanilla clients incompatible. The optional AE2 integration
is retained but requires its own compatible environment and was not exercised
in these mixed-client tests.

Third-party claim/protection integrations are not implemented. Vanilla spawn
protection is enforced; a claim plugin/mod needs an additional authorization
adapter. Existing undo records contain block states, not complete block-entity
snapshots; undo/redo does not promise restoration of container contents.

The automated clients exercise networking and server behavior. They do not
launch the graphical Minecraft client, so visual previews, textures, input,
and singleplayer UI still need a manual play-through. No Bukkit/Paper or
NeoForge support is claimed by this implementation.
