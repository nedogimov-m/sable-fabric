# Upstream provenance

- Repository: https://github.com/ryanhcode/sable
- Imported commit: `227077f7f528c9beab1336a9fad2e27b754e4e70`
- Commit date: `2026-10-15T13:29:22-04:00`
- Upstream declared version: `2.0.6`
- Original Minecraft target: `1.21.1` (Fabric and NeoForge)
- Local target: Fabric Minecraft `26.3`, Java `25`
- License: original [`LICENSE.md`](LICENSE.md), PolyForm Shield License 1.0.0, retained unchanged.

The imported common, Fabric, NeoForge, Rust, native binary, and documentation
material is retained. The local build uses 566 original common/Fabric/Rapier Java
sources plus 30 production helpers (596 sources). Seven obsolete GL instancing
and staging owners were retired after native replacement and a closed reference
audit. Exact pinned upstream bytes and the pre-retirement local versions are
preserved under `upstream-retired/gl-instancing/`; `retirement.json` records
hashes, references, genuine replacement owners, and verification limits. Three
obsolete Fabric bundle workaround owners are also preserved under
`upstream-retired/native-fabric-dispatch/`, with unchanged pinned/local bytes,
a closed reference audit and real Fabric26.3 direct-dispatch execution proof.
The native networking implementation now owns synchronous payload dispatch.
No
production-source build exclusions are used. NeoForge sources are retained as
origin material; this target uses Fabric.

The upstream optional BEPU C# submodule (`bepuphysics2`) was not fetched because
the suite selects the genuine bundled Rapier backend. No replacement physics
implementation or generated Minecraft compatibility stubs are supplied.

The bundled native archive remains byte-identical to upstream. It contains
macOS, Linux, and Windows binaries for x86_64 and aarch64. Its Rapier license is
retained at `sable_rapier/src/main/resources/natives/sable_rapier/LICENSE-RAPIER`.
The underlying modified Rapier revision in Cargo.toml is
`38e92f117590862481a53df6fc69a5d893e29186` from https://github.com/ryanhcode/rapier.
