# Sable Companion for Fabric 26.3

Upstream: https://github.com/ryanhcode/sable-companion

Pinned source revision: `652ecf0e051846f3a433dd20126dd4d64ec3793a` (upstream version 1.6.0).
All 12 upstream Java source files are retained under `common/src/main/java`.
The upstream MIT license and copyright notices remain in `LICENSE`; the binary JAR includes that license.

## Port changes

- Replaced the upstream multi-project Minecraft 1.21.1 build with a standalone Fabric Loom 1.18.4 build, Gradle 9.7.0 and Java 25.
- Targets unobfuscated Minecraft 26.3 directly, without a mappings dependency; uses Fabric Loader 0.19.5 and Fabric API 0.163.0+26.3.
- Updated both `ChunkPos` forwarding overloads in `SableCompanion` to the 26.3 record accessors `x()` and `z()`.
- Preserved upstream service discovery, public API, geometry/math code and default implementation. `DefaultSableCompanion` is the original optional-Sable implementation supplied by upstream; it does not replace the Sable physics mod.
- Replaced Fabric metadata with the actual 26.3/Java 25 requirements. Removed the NeoForge metadata and unused upstream build-convention projects.

## Build and validation

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./gradlew build
```

Validated on 2026-10-21: `build` passed; 3 targeted JUnit tests passed. Tests cover forwarding signed chunk coordinates through both migrated accessors and loading the original service provider without client initialization. The produced JAR was inspected for its Fabric metadata, service descriptor, license and Java 25 class version. No game/client smoke test has been performed for this library in isolation; integration with the real Sable port remains required.

Artifact: `build/libs/sable-companion-fabric-26.3-1.6.0-port.1+mc26.3.jar`

Fabric mod id: `sablecompanion`

Gradle coordinates: `dev.ryanhcode.sable-companion:sable-companion-fabric-26.3:1.6.0-port.1+mc26.3`

Artifact SHA-256 at validation: `c8b73d3b27f952d8e21273679ac721c04b92eaf913f9d49e127e0b7049261698`
