# Bukkit/Paper server backend placeholder

This Gradle subproject uses only the `base` plugin. It builds no plugin JAR and
contains no Bukkit/Paper functionality or runtime dependencies.

The implemented server backend is in `fabric-server/`. A shared `common-server`
module will be considered when Bukkit implementation reveals concrete shared
requirements; it is intentionally absent for now.
