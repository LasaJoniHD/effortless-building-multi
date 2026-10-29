# Fabric server backend (Minecraft 26.3)

Install the standalone JAR from `fabric-server/build/libs/` on the dedicated
server. Modded players install the unchanged upstream JAR from `fabric/build/libs/`.
Do not install both artifacts on the server; their registrations would conflict. The server needs Java 25, Fabric Loader
0.19.5 or newer, and Fabric API 0.162.0+26.3 or newer. No Polymer dependency or
resource pack is required.

Vanilla Minecraft 26.3 players can join the same server. They do not need this
mod, Fabric, Polymer, or a resource pack. The backend uses the original client
protocol; the client does not need a new hello packet.
Use a client built for the same upstream Minecraft/protocol version.

## Features and authority

The existing shape generators, mirrors, arrays, radials, replace filters,
survival material consumption, tool constraints, and
undo/redo run through the shared server pipeline. The client keeps its radial
menu, previews, shortcuts, and configuration screens. The server regenerates
positions from the requested mode and corners; clients cannot supply the final
block set.

The randomizer item, palette, editor, and crafting recipe are intentionally absent
from the backend. No custom item or menu is registered, including for companion
clients and operator commands. Shared server sources exclude the randomizer
classes and pipeline stage. The upstream client stays untouched and may still
show its local randomizer creative entry; that entry is unavailable on this
server. Existing saved stacks of the removed item are not preserved.

World configuration and per-player modifiers retain their existing files under
`<world>/effortlessbuilding/`. Only operators can update world configuration.
Undo/redo and placed-block tracking remain session-scoped, as before.

## Connection and content isolation

* The server recognizes the original client's three existing synchronization
  channels. It enables the session once all are advertised, with support for
  both configuration-phase and later play-phase registrations.
* The backend uses vanilla item and menu registries. It does not add custom
  registry entries, registry-remapping tasks, item translations, or recipes.
* Vanilla connections never have to answer a mod handshake or complete a custom
  login/configuration task. Fabric's optional discovery messages are safely
  ignored by vanilla clients.
* Every backend operation requires an enabled session. Every outgoing mod sync,
  including configuration broadcasts and AE2 replies, checks both session state
  and support for that particular channel.
* The backend does not enable or intercept Fabric's full recipe synchronization.
  Fabric and other mods retain their normal recipe-sync behavior. The removed
  randomizer recipe is excluded from the server JAR.
* Disconnect removes server capabilities and session state. Client behavior is
  exactly the upstream behavior; this module does not modify client cleanup,
  rendering, click interception, or singleplayer initialization.

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
and the server block-placement mixin. It compiles shared gameplay sources
from `common/` into its separate server JAR. It does not package the upstream
Fabric initializer, client UI, renderer, or client networking implementation.

Server-specific changes to shared code are represented by
`fabric-server/patches/common-server.patch`. The build copies source inputs into
`fabric-server/build/generated/upstream-java/` and applies that patch only there.
It never writes to the original source tree. A normalized SHA-256 manifest covers
the 23 adapted classes. An upstream change in one of those classes stops the
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

To test configuration-phase capability discovery, run:

```powershell
.\gradlew.bat :fabric-server:backendProbe -PprobeMode=mapped
```

This mode starts vanilla, original-client-protocol, and Fabric-without-companion
connections together. The companion retains its original client-only item/menu
registrations, with deliberately shifted local item IDs. The server must send
only vanilla content and no Effortless Building item/menu registry entries.
Both modes check that the removed randomizer cannot be given or crafted by
either player type, including after `/reload`.

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
