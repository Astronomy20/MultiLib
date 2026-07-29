[← Back to Home](index.md)

# Maven Releases

MultiLib publishes a static Maven repository from this site:

```
https://astronomy20.github.io/MultiLib/maven/
```

Rebuilt on every push to `1.21.1`, not just on CurseForge/Modrinth releases — the Maven coordinate tracks the latest commit; player-facing releases stay their own, less frequent milestones. Published versions are never pruned. The version itself only changes when `mod_version` in `gradle.properties` is bumped.

## Coordinates

| | |
|---|---|
| Group | `net.astronomy.multilib` |
| Artifact | `multilib` |
| Version | see [`gradle.properties`](https://github.com/Astronomy20/MultiLib/blob/1.21.1/gradle.properties) (`mod_version`) |

## Embedding via `jarJar`

For a fundamental, non-optional dependency, embed MultiLib in your mod's jar (NeoForge's jar-in-jar) so players don't need to install it separately:

```groovy
repositories {
    maven { url = "https://astronomy20.github.io/MultiLib/maven/" }
}

dependencies {
    implementation jarJar("net.astronomy.multilib:multilib:1.1.0") {
        jarJar.ranged(it, "[1.1.0,)")
    }
}
```

For an optional/normal dependency, use a plain `implementation(...)` instead of `jarJar(...)`, and declare it in `neoforge.mods.toml`.

## Local development

For zero-publish iteration against a MultiLib checkout, see [Getting Started](Getting-Started.md#1-add-the-dependency) for a Gradle composite build (`includeBuild`). `./gradlew publish` also generates the same repository layout locally, under `repo/`.
