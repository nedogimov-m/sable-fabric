# Sable → Fabric 26.3

Состояние: **полная production Loom-сборка и выделенный сервер прошли проверку**.
Проверены настоящий запуск Create+Sable+Simulated+Aeronautics+Offroad, применение
миксинов в 146 обязательных server target classes, создание мира, Rapier и 20 тиков. Полный клиент,
multiplayer и полный игровой цикл plot остаются отдельными проверками.

## Зафиксированная основа

- Upstream Sable `2.0.6`, commit `227077f7f528c9beab1336a9fad2e27b754e4e70`.
- Источник и лицензии: [UPSTREAM.md](UPSTREAM.md), [LICENSE.md](LICENSE.md).
- Minecraft `26.3`, Fabric Loader `0.19.5`, Fabric API `0.163.0+26.3`,
  Loom `1.18.4`, Gradle `9.7.0`, Java `25`.
- Локальные composite builds: `../companion` (`1.6.0-port.1+mc26.3`) и
  `../veil` (`4.3.2-port.1+mc26.3`). ForgeConfigAPIPort `26.3.1`.
- В сборке 566 upstream Java-файлов common/Fabric/Rapier и 30 настоящих
  production helper/mixin классов: 596 production sources. Семь неиспользуемых
  GL instancing/staging owners выведены после аудита ссылок и native replacement;
  точные upstream и локальные версии сохранены в
  `upstream-retired/gl-instancing/`, основания — в `retirement.json`. Ещё три
  Fabric bundle workaround owners архивированы в `upstream-retired/native-fabric-dispatch/`
  после проверки настоящего синхронного `ClientPlayNetworkAddon.receive` 26.3 и
  нулевого аудита внешних ссылок. Заглушки
  Minecraft/Veil и build exclusions не применяются. NeoForge/Rust сохранены.

## Выполненная миграция

1. Отдельная Fabric-сборка с реальными зависимостями, оригинальными Rapier
   natives, metadata/mixins для Java 25 и без старого NeoForm/Parchment pipeline.
   Перенесены идентификаторы, перемещённые классы, record accessors,
   Optional NBT, registry/config/reload interfaces и server registrations.
2. Настоящие билеты нового `TicketStorage`. Владельцы UUID агрегируются в один
   билет type/level; удаление одного владельца сохраняет билет остальных.
   Независимые сроки жизни UUID сохранены, включая допустимую границу 20 тиков.
   Тип билета регистрируется до заморозки реестров с флагами загрузки,
   симуляции и активности измерения; билет не сохраняется на диск.
3. Plot palette/block/fluid tick data используют `PalettedContainerFactory`,
   `PackedTicks`/`SavedTick` и codecs. Старые palette `Name`/`Properties`
   преобразуются в `id`/`properties`, не изменяя исходный NBT. Неизвестные
   типы тиков и чужие чанки фильтруются; задержки и приоритеты сохраняются.
4. `SableSavedDataStorage` переносит старые файлы Sable в namespaced пути
   26.3, сохраняет оригинал, отдаёт приоритет уже существующему новому файлу
   и выбрасывает ошибку при неудачной миграции. Перенесены реальные storage,
   chunk holder/scheduling, plot save/load, entity sections и attachment
   `ValueInput`/`ValueOutput`. Добавлен явный Identifier namespace для addon SavedData;
   прежний Sable overload делегирует ему без изменения миграции. Событие chunk load различает новый и загруженный
   чанк. Полный игровой цикл plot пока не проверен.
5. Dimension physics datapack/network codecs используют актуальные
   `Identifier`/`Vec3` codecs. Перенесены collision contexts, propeller vectors,
   UDP frame handlers, permissions, player identity и Rapier section constants.
6. Server/common mixin hooks перенесены на `ServerExplosion`, новый fluid
   tracker, entity restitution, lazy SavedData world border, interaction ranges,
   respawn/tracking/teleport и новые save descriptors. Дополнительно исправлен
   тип furnace recipe map shadow. Статический common/server audit проверил 129
   Minecraft mixins; расширенный client audit — 218 mixins, 304 selectors,
   134 instruction targets и 248 shadow names. Все проверенные selectors/targets/shadows и AW entries найдены; header AW
   перенесён в `official` для Loom 26.3.
   Это не доказывает injection cardinality, типы всех shadows, accessor targets
   или игровой runtime.
7. Четыре movement packet mixins передают Sable coordinate-space flag через
   настоящие codecs, включая новый `ClientboundEntityPositionSyncPacket`.
   Vanilla interpolation handler владеет body rotation; plot interpolation
   сохраняет intermediate steps, fractional ticks и retarget continuity,
   выполняется один раз в `Entity.tick`. Клиентская интеграция положения,
   камеры, sound delegates, particles/weather, hitboxes и native path callbacks
   перенесена на текущих владельцев API.
8. Entities и block entities используют native extract/submit render states:
   ориентация, riding/sleeping pivots, shadows, Conduit eye, outlines и breaking
   decals копируются при extraction. Camera-relative origin вычисляется в double
   до `PoseStack` float translation, включая дальние plot coordinates. GUI input,
   client gizmos, F3 info и loaded-chunk debug перенесены на native extraction.
9. Основной terrain backend использует настоящие `ModelBlockRenderer` и
   `FluidRenderer`, хранит immutable quad snapshots с position/UV/color/AO/tint/
   overlay/light/normal и отправляет их через native submit collector и moving-block
   RenderTypes. Vanilla, Sodium reacharound и experimental renderer entry points
   используют этот функциональный CPU backend. Старый GL instancing больше не
   является активным renderer path. Семь закрытых GL command/section/occlusion/
   staging owners архивированы после нулевого аудита внешних ссылок; native
   `BucketRenderBuffer`, mesh/task/shader/texture helpers остаются live. Архив
   сохраняет точные pinned upstream и локальные bytes. Ускорение instancing не заявляется.
   Blocks получают directional lighting на CPU из copied native `CardinalLighting`
   после поворота plot; fluids сохраняют native baked shading. Стандартный 26.3
   `BLOCK` vertex format не содержит normals: готовый GPU normal pipeline здесь
   не заявляется. Native batching, culling и GPU storage ещё требуют оптимизации;
   фактические terrain pixels/мод целиком пока не проверены.

10. Water regions используют native CPU mesh ownership и retained GPU draws. Extraction
    копирует plot transforms и camera-inside; masks записываются в отдельные D32 depth
    passes до vanilla frame graph, с reverse-Z и без global GL. Точная native projection
    после hurt/view bob и portal/nausea effects копируется перед level render. Удаление
    region не уничтожает mesh, пока его удерживает extracted snapshot; reset/reload/stop
    освобождают владельцев. Sky map рисует copied plot meshes сверху, учитывает atlas
    cutout alpha и пропускает translucent casters. Typed UBO/depth-view/sampler providers
    зарегистрированы в настоящем Veil native-pipeline bridge. Проверены production
    processors, vanilla block/OIT compilation, water discard, sky lightmap reduction,
    disabled1×1 defaults и reload/recreate на Vulkan. Полный Sable initializer/world
    в этой компонентной проверке не загружается; полный игровой запуск ещё не заявляется. Native sprite-cache helper теперь
    имеет явные shader-definition/uniform owners вместо global Veil/GL binds.

11. Production JAR загружает все обязательные серверные миксины и entrypoints,
    создаёт настоящий мир, запускает Rapier и проходит 20 тиков с сохранением и
    остановкой. Реальные hooks перенесены на `kill(ServerLevel)`, native movement/
    flight, отдельные методы step sounds/thunder, `PositionMoveRotation`,
    `SignTextSlot`, `ValueInput` и integer block-update flags. Поводки сохраняют
    нативные dampened spring/quad interactions с проекцией endpoints/movement
    в мировую систему и impulse обратно в локальную. Контейнер существует при
    построении chunk source; физика и ready event запускаются после завершения
    конструктора уровня. Это не является проверкой всех перечисленных gameplay
    сценариев. Артефакт и журнал запуска зафиксированы в
    `../../build/porting/packaged-smoke/suite/server/{inputs,activation,runtime}.json`;
    `inputs.json` содержит SHA-256 фактически замороженных JAR. Более ранний
    standalone Sable PASS сохранён отдельно. В suite server также прошёл настоящий
    загруженный portable-engine dye recipe с сохранением component patch и
    проверкой отклонённых inputs. Эти результаты не переносятся автоматически на
    последующие client-only исправления.
    Старые optional APIs остаются compile-only; локальный настоящий CC:Tweaked
    JAR/POM/module и Sodium Extras target имеют отдельные provenance manifests
    в `diagnostics/`. Ни один из этих optional runtime не сертифицирован.

## Доказанные проверки — не повторять без изменения соответствующего кода

macOS arm64, OpenJDK `25.0.4.1`, настоящий Minecraft `26.3`. JUnit группы
используют настоящий `Bootstrap.bootStrap()`, реестры, NBT и storage objects.

| Проверка | Результат | Доказательство в `build/` |
| --- | --- | --- |
| Rapier JNI: scene, cube pose, 20 gravity steps, remove/dispose | PASS, y: `10 → 5.033686637878418` | `native-smoke.log` |
| UUID ticket ownership, 20-tick boundary, refresh/reoccupation | 3/3 PASS | `core-ticket-check.log` |
| 4096 block states/properties, legacy palette, block/fluid ticks | 4 случая PASS | `plot-persistence-check.log`, `plot-persistence-legacy-fix.log` |
| Настоящий SavedDataStorage: legacy migration/cache/save/reopen, existing-file priority, migration failure | 3/3 PASS | `saved-data-check.log` |
| Dimension physics datapack values/defaults/network roundtrip | 2/2 PASS | `dimension-config-check.log` |
| Настоящий Fabric/Knot SERVER применяет 4 production packet mixins; оба flag values, vanilla payload, wire boundary | 8/8 PASS | `packet-hook-check.log`, `packet-hook-check-inputs.json` |
| Plot interpolation: native stepped paths/corners, fractional/pause ticks, retarget, zero duration/cancel | 4/4 PASS | `plot-interpolation-check.log`, `plot-interpolation-check-inputs.json` |
| Entity transforms: containing/riding pivots, noncommuting billboard cancellation, copied rotations | 3/3 PASS | `entity-render-transform-check.log`, `entity-render-transform-check-inputs.json` |
| Block-entity transforms: large plot/world precision, rotation, copied Conduit eye orientation | 3/3 PASS | `block-entity-transform-check.log`, `block-entity-transform-check-inputs.json` |
| Addon namespace: native legacy disk migration/cache/save/reopen, original bytes preserved | 1/1 PASS | `saved-data-namespace-check.log`, `saved-data-namespace-check-inputs.json` |
| Dynamic shade normals: native BakedQuad/BufferBuilder position/UV/color/light bytes and rotated normals | 2/2 PASS | `dynamic-shade-check.log`, `dynamic-shade-check-inputs.json` |
| Native terrain quad capture/deferred callback, copied pose, sky/block-light bytes, CPU cardinal shading | 3/3 PASS | `plot-mesh-check.log`, `plot-mesh-check-inputs.json` |
| Packed instance buffer: native bytes survive growth, adjacent/reused ranges, retired lifetimes | 3/3 PASS | `bucket-buffer-check.log`, `bucket-buffer-check-inputs.json` |
| Native water-mask GPU: reverse-depth entry/exit, copied region matrices, camera-inside initialization | 3/3 PASS, Apple M4/Vulkan | `water-mask-gpu-check.log`, `water-mask-gpu-check-inputs.json` |
| Native sky-shadow GPU: atlas alpha cutout, solid alpha rule, copied section matrices, reverse-depth ortho | 4/4 PASS, Apple M4/Vulkan | `sky-shadow-gpu-check.log`, `sky-shadow-gpu-check-inputs.json` |
| Actual native world-pipeline bridge: production water/sky processors, eager vanilla/OIT, exact pixels, disabled defaults, reload | 5 checks PASS, Apple M4/Vulkan | `native-world-shaders-gpu-check.log`, `native-world-shaders-gpu-check/inputs.json` |

Legacy palette первоначально упала; после исправления повторён только этот
случай, он PASS. Native/ticket и остальные неизменённые группы не повторялись.
Packet check запускает настоящий Minecraft/Fabric/Mixin, затем завершает
инициализатор до открытия server socket и создания мира. Это **не** запуск
полного Sable, проверка client packet application или multiplayer.
Render math/quad suites используют настоящие Minecraft/JOML/BufferBuilder
objects, но не доказывают применение client mixins, полный world tessellation
или GPU pixels. Namespace case добавлен отдельно; прежние 3 SavedData contracts
не повторялись, migration body не изменён. Water/sky GPU checks запускают
изолированный test mod с настоящими production pass classes и Veil native GPU API,
читают фактические D32_FLOAT bytes из Vulkan textures и завершают клиент. Они не
загружают полный Sable initializer/world и не доказывают vanilla shader binding.

Команды для изменённого участка:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
python3 scripts/check_rapier_native.py
python3 scripts/check_core_tickets.py
python3 scripts/check_plot_persistence.py legacySingletonPaletteLoadsAndInvalidPaletteFails
python3 scripts/check_saved_data.py
python3 scripts/check_dimension_config.py
python3 scripts/check_packet_hooks.py
python3 scripts/check_plot_interpolation.py
python3 scripts/check_entity_render_transforms.py
python3 scripts/check_block_entity_transforms.py
python3 scripts/check_saved_data_namespace.py
python3 scripts/check_dynamic_shade.py
python3 scripts/check_plot_mesh.py
python3 scripts/check_bucket_buffer.py
python3 scripts/check_water_mask_gpu.py
python3 scripts/check_sky_shadow_gpu.py
python3 scripts/check_native_world_shaders_gpu.py
```

Скрипты компилируют реальные production-классы выбранного участка и обычные
проверки. Для них нужны загруженные Gradle Minecraft/JUnit/Loader артефакты,
Python 3; для распаковки original native archive также `lz4` CLI.

## Rapier native prerequisites

Оригинальный архив содержит JNI для macOS/Linux/Windows, x86_64/aarch64.
macOS arm64 библиотека реально симулировала движение на Java 25; остальные
пять бинарников в этом сеансе не запускались. Пересборка неизменного ABI не нужна.

- Архив SHA-256: `986d1edc78a943ec46cc4aab27844cff92c49f85cd3d7d3e6fb4341da3dd03ae`.
- macOS aarch64 SHA-256: `d006232bf40e993a5825d7b2edc101a7fad3ab28b997377434cf88341a9ae7bf`.
- Rapier revision: `38e92f117590862481a53df6fc69a5d893e29186`.
- Архив и `LICENSE-RAPIER` сохранены. При изменении JNI нужны перенос Rust
  build tasks и пересборка платформ; старая Rust/Docker инфраструктура сохранена.

## Оставшаяся работа

Историческая диагностика **601 production sources** на Java 25 выполнена против
настоящего Minecraft с настоящим Sable access widener, применённым Fabric
ClassTweaker, и актуальными модулями Fabric API. На снимке bridge-26 genuine Veil GPU/context/Inspector,
render-type и core class outputs предшествуют старому Veil diagnostic API. Veil
развивается независимо. Старый Veil не установлен и не упаковывается как 26.3 мод.

Исторический полный javac до retirement: **PASS, 606 production sources / 0 ошибок**.
Текущая inventory после retirement — 596; полный production Loom assemble прошёл,
новый отдельный полный javac этим аудитом не запускался.
Прогресс: 1087 → 958 → 534 → 529 → 498 → 409 → 390 → 375 → 357 → 338 → 268
→ 235 → 116 → 72 → 48 → 39 → 10 → 1 → 0. Первый PASS — #23;
bridge-26 включает native water/sky registration/default lifecycle, точную water projection и полный SHA-256 input manifest.
Это diagnostic compile, **не полноценный Loom artifact/startup**. Manifest сохраняет
все 606 source hashes, точный classpath order, JAR hashes и каждый native boundary
class hash; во время bridge-26 source/class-directory drift не было. Сохранённый historical
Veil fallback и старые optional compile APIs явно отмечены как historical.
Genuine Iris compile API теперь `1.11.7+26.3-fabric`; runtime integration ещё не проверена.
Остальные configured optional APIs разрешены по SHA-256; каталоги/resolvers:
`diagnostics/{optional,target}-compile-apis.json`,
`scripts/resolve_{optional,target}_compile_apis.py`. Старые optional артефакты
не представлены как проверенные 26.3 integrations.
Логи/manifest: `build/diagnostic/bridge-26-{javac,driver}.log`,
`bridge-26-javac.args`, `bridge-26-javac.inputs.json`.
Краткий снимок: [compiler-snapshot-2026-10-21.md](diagnostics/compiler-snapshot-2026-10-21.md).

1. Завершить Veil full source build/runtime lifecycle. Native water/sky bridge,
   disabled defaults и depth passes проверены на Vulkan;
   AW/member audit clean. Подтвердить
   terrain world tessellation/submit pixels и client injection в настоящем запуске.
2. Подключить настоящие APIs optional integrations и проверить поддерживаемые
   26.3 версии. Старые optional артефакты сейчас только compile-only.
3. Проверить plot load/unload/reload с реальными блоками, жидкостями,
   тиками и физикой, client rendering и multiplayer. Полная production Loom-сборка
   и dedicated startup уже PASS; полный игровой цикл plot и multiplayer пока
   **не проверены**.

```sh
./gradlew compileJava --console=plain --no-configuration-cache
```
