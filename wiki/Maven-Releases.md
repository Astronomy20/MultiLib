[← Back to Home](index.md)

# Maven Releases

MultiLib publishes a static Maven repository straight from this site, hosted at:

```
https://astronomy20.github.io/MultiLib/maven/
```

It's rebuilt on every push to the `1.21.1` branch — not just when a build is cut for CurseForge/Modrinth. As a library mod, MultiLib is meant to be *embedded* (jar-in-jar) by downstream mods, whose own release cadence rarely lines up with MultiLib's: a downstream dev may need a fix or a new `PatternProvider` the day it lands, well before it's worth cutting a full player-facing release. Decoupling the two lets the Maven coordinate always track the latest `1.21.1` commit, while CurseForge/Modrinth releases stay curated, less frequent milestones for players. Bump `mod_version` in `gradle.properties` (normally alongside a real release) whenever you want the Maven version itself to move — the repository keeps whatever versions have been published so far, it doesn't prune older ones.

## Coordinates

| | |
|---|---|
| Group | `net.astronomy.multilib` |
| Artifact | `multilib` |
| Version | see [`gradle.properties`](https://github.com/Astronomy20/MultiLib/blob/1.21.1/gradle.properties) (`mod_version`) |

## Using it from a NeoForge mod (embedded via `jarJar`)

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

`jarJar` embeds MultiLib's jar inside your own mod's jar at build time (NeoForge's jar-in-jar mechanism), so players installing your mod don't need to install MultiLib separately — the right approach when MultiLib is a fundamental, non-optional dependency rather than an optional integration.

If you'd rather require MultiLib as a normal, separately-installed dependency instead (e.g. it's already commonly installed alongside other mods that need it), swap `jarJar(...)` for a plain `implementation(...)` and list it as a dependency in `neoforge.mods.toml` instead of embedding it.

## Building it yourself instead

You don't need this hosted repository at all for local development — see [Getting Started](Getting-Started.md#1-add-the-dependency) for a Gradle composite build (`includeBuild`) against a local MultiLib checkout, or run `./gradlew publish` in a MultiLib checkout to generate the same repository layout under `repo/` locally.
