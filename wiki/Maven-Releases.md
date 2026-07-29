[← Back to Home](index.md)

# Maven Releases

## Repository

```groovy
repositories {
    maven { url = "https://astronomy20.github.io/MultiLib/maven/" }
}
```

Rebuilt on every push to `1.21.1`, not just on CurseForge/Modrinth releases — the Maven coordinate tracks the latest commit; player-facing releases stay their own, less frequent milestones.

## Coordinates

[![Maven metadata URL](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fastronomy20.github.io%2FMultiLib%2Fmaven%2Fnet%2Fastronomy%2Fmultilib%2Fmultilib%2Fmaven-metadata.xml&label=multilib)](Maven-Versions.md)

| | |
|---|---|
| Group | `net.astronomy.multilib` |
| Artifact | `multilib` |

See the [Maven Version Index](Maven-Versions.md) for every version published.

## Versions

Every push publishes up to three version strings for the same content, so a version number is never silently overwritten with different bytes under the same name:

| Version | Meaning |
|---|---|
| `1.1.0-SNAPSHOT` | Always the latest push to `1.21.1`. Mutable by Maven convention — Gradle re-checks it instead of caching it forever, unlike a bare release. Use this to track development without waiting for a release. |
| `1.1.0-<build number>` | A new, immutable version every push (`MULTILIB_BUILD_NUMBER` = the CI run number). Pin this for a reproducible build tied to one exact push. |
| `1.1.0` | A real release — published once, the first time that `mod_version` is seen, then never touched again. |

## Dependency

Embed via `jarJar` (NeoForge's jar-in-jar) when MultiLib is a fundamental, non-optional dependency, so players don't need to install it separately. Tracking development (`-SNAPSHOT`):

```groovy
dependencies {
    implementation jarJar("net.astronomy.multilib:multilib:1.1.0-SNAPSHOT") {
        jarJar.ranged(it, "[1.1.0-SNAPSHOT,)")
    }
}
```

Pinning a release instead, once one exists:

```groovy
dependencies {
    implementation jarJar("net.astronomy.multilib:multilib:1.1.0") {
        jarJar.ranged(it, "[1.1.0,)")
    }
}
```

For an optional dependency, use a plain `implementation(...)` instead of `jarJar(...)`, and declare it in `neoforge.mods.toml`.
