# Sable — Fabric 26.3

Interactive moving block structures and Rapier physics for Minecraft 26.3. This branch targets Fabric only. Original project: [RyanHCode/Sable](https://github.com/ryanhcode/sable). Licensing remains in `LICENSE.md`.

## Workspace

Clone the Fabric 26.3 branches into sibling directories:

```sh
git clone -b 26.3 https://github.com/nedogimov-m/imguimc-fabric.git imguimc
git clone -b 26.3 https://github.com/nedogimov-m/Veil-Fabric.git veil
git clone -b 26.3 https://github.com/nedogimov-m/sable-fabric.git sable
git clone -b 26.3 https://github.com/nedogimov-m/Simulated-Fabric.git Aeronautics
```

Use JDK 25. Gradle 9.7 and Fabric Loom 1.18.4 are pinned by the wrapper/build files.

## Build

```sh
./gradlew assemble --no-configuration-cache
./gradlew test --tests dev.ryanhcode.sable.config.ConfigSpecTest --no-configuration-cache
```

The Companion source project is included in `companion/`. Gradle composite builds compile the sibling Veil and ImGuiMC projects. Runtime dependencies are nested in the output `build/libs/sable-fabric-26.3-*.jar`.

## Configuration

Common/client files remain `config/sable-common.toml` and `config/sable-client.toml`. Server settings belong to `<world>/serverconfig/sable-server.toml`, seeded from `defaultconfigs/sable-server.toml` for new worlds. Existing keys and values are preserved. Disk edits are checked once per second; server shutdown detaches the world and restores defaults.

Configuration uses the native Fabric lifecycle and validated TOML values. No ForgeConfigApiPort or other loader API is required. The four focused configuration tests cover persistence, invalid-value correction, live reload, world isolation and unbounded removal limits. A native integrated-world check also verified a disk edit updates every live physics pipeline and shutdown clears world settings.

Optional integration APIs are genuine Fabric artifacts, pinned in `tools/fabric-compile-apis.json`. Older intermediary class descriptors are mapped only for compilation; these JARs are never bundled or treated as 26.3 runtime ports.
