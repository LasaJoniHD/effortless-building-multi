# Preserved implementation before module migration

`initial-backend.patch` preserves the complete first backend implementation,
including client-side adaptations and experimental protocol changes that are
not part of the standalone server module. It is a reference archive, not a build input.

It applies to upstream commit `f31a5b5012f5d8ae2d717d12d96a50dd13476fc3` in a separate clean checkout:

```text
git apply /path/to/fabric-server/migration/initial-backend.patch
```

Do not apply it over the migrated working tree. The active server adaptations
are the smaller, guarded patch set in `fabric-server/patches/`.
