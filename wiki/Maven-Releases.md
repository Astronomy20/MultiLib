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

## Dependency

Embed via `jarJar` (NeoForge's jar-in-jar) when MultiLib is a fundamental, non-optional dependency, so players don't need to install it separately:

```groovy
dependencies {
    implementation jarJar("net.astronomy.multilib:multilib:1.1.0") {
        jarJar.ranged(it, "[1.1.0,)")
    }
}
```

For an optional dependency, use a plain `implementation(...)` instead of `jarJar(...)`, and declare it in `neoforge.mods.toml`.
