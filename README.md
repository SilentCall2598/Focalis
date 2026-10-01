# Focalis

Focalis is an open-source graphics and optimization platform for Minecraft Forge 1.12.2. The long-term aim is a
modular, compatibility-first alternative to OptiFine for large Forge modpacks.

## Status

Focalis is in early development. The current version is mostly a foundation: it loads as a client-side Forge
mod, reports diagnostic information, and provides the internal structure future rendering systems will build
on. **With default settings it does not change what Minecraft renders.** Only the experimental `shaders` and
`world_programs` features do, and both are off by default and described below.

Planned systems, none of which exist yet: OptiFine/Iris-compatible shader support, a high-performance
renderer, rendering optimizations, dynamic lights, connected textures, emissive textures, CIT, CEM, custom
skies, video settings, and compatibility tooling for large modpacks.

## What it does today

- Logs its version and the runtime environment (Java, OS, CPU, Forge, and other rendering-related mods present).
- On the first rendered frame, logs the OpenGL driver, version, limits, video memory and the capabilities
  future systems depend on.
- Adds a Focalis line with feature states to crash reports.
- Exposes precise world render stage boundaries to internal rendering systems: the world pass, sky, terrain,
  entities, particles, translucent terrain, weather, clouds and the hand, with the terrain layer, entity pass or
  particle kind being drawn. In the normal surface sky, the sun and moon are told apart from the rest of the sky,
  and inside the entities, vanilla's glowing entity outlines are marked too. Mixins in `EntityRenderer` and `RenderGlobal` only report these boundaries and don't change what is drawn.
  That context can be translated into Focalis shader program roles and matched to the programs of 1.12.2 era
  shaderpacks, and the matched programs of one shader folder can be prepared and built. Only the experimental
  `world_programs` feature binds them.
- Provides an optional `frame_stats` diagnostic feature that periodically logs frame timing.
- Provides an experimental, off-by-default `shaders` feature. It is the first step of shader support and
  only runs Focalis's own test program (`shaders/focalis_post.vsh` and `.fsh`) as a single post-process pass
  over the world image at the end of the world pass, before the hand and HUD are drawn. Regular OptiFine or Iris
  shaderpacks are not supported, and a pack without that program is refused. If the pack or its program fails to
  load or compile, the problem is logged and rendering stays vanilla. It stays unavailable when OptiFine is
  installed.
  The development test pack lives in `src/test/resources/shaderpacks/focalis-depth-view`.
- Provides an experimental, off-by-default `world_target` feature that draws the world into a Focalis-owned
  framebuffer and copies it back to Minecraft's, which looks the same as vanilla. It is groundwork for shaderpack
  rendering and doesn't run shaderpack world programs itself. It needs OpenGL 3.0 and stays unavailable when
  OptiFine is installed.
- Provides an experimental, off-by-default `world_programs` feature for testing. It binds the matching program
  from the selected pack while vanilla draws the sky, terrain, entities, particles, weather and clouds. Each
  dimension uses the pack's `shaders/world<id>` folder for its numeric dimension id when there is one, like
  `world-1` for the Nether, and the main `shaders` folder otherwise. A world folder replaces the main folder
  completely, so an empty one or one whose programs fail leaves that dimension vanilla. `dimension.properties` is
  read but not used yet. Each folder is built the first time a dimension needs it and kept, so changing dimension
  or rejoining doesn't build it again. The hand isn't bound, and vanilla's glowing entity outlines keep their own
  shaders. Entity and block entity renderers that use their own shaders can do so, and the program is bound again
  after each one. No uniforms, textures or composite passes are set up, so regular shaderpacks won't render
  correctly. Programs that fail to load or build are logged and those parts draw the vanilla way. It stays
  unavailable when OptiFine is installed. The development test packs live in
  `src/test/resources/shaderpacks/focalis-world-routes` and `focalis-dimension-routes`.

## Requirements

- Minecraft 1.12.2
- Forge 14.23.5.2860 recommended. Focalis currently accepts 14.23.5.2847 or newer.
- [MixinBooter](https://github.com/CleanroomMC/MixinBooter) 10.7 or a later 10.x release, installed separately.
  Only 10.7 is tested. Keep its release file name (`!mixinbooter-<version>.jar`) so Forge loads it before Focalis.
- Java 8
- Client only. Servers don't need Focalis installed.

## Configuration

Settings live in `config/focalis.cfg`, created on first launch. Changes take effect after a restart.

| Option | Default | Description |
| --- | --- | --- |
| `diagnostics.logGlExtensions` | `false` | Also log every OpenGL extension the driver reports. |
| `features.frame_stats.enabled` | `false` | Periodically log frame timing and render stage counts. |
| `features.frame_stats.reportIntervalSeconds` | `10` | Seconds between frame statistics reports. |
| `features.shaders.enabled` | `false` | Experimental. Run the Focalis test post-process program from the selected pack. |
| `features.shaders.pack` | empty | Name of a folder or zip directly inside the `shaderpacks` folder. |
| `features.world_target.enabled` | `false` | Experimental. Draw the world through a Focalis framebuffer. No visible change. |
| `features.world_programs.enabled` | `false` | Experimental. Bind the selected pack's world programs while the world draws. |
| `features.world_programs.pack` | empty | Name of a folder or zip directly inside the `shaderpacks` folder. |

## Building

Start Gradle with **JDK 17 or newer**. The Gradle daemon itself runs on JDK 25, which
[RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle) (the maintained ForgeGradle fork used
for 1.12.2) requires. The mod is compiled and tested with a Java 8 toolchain. Gradle finds both JDKs locally or
downloads them automatically.

```sh
./gradlew build        # compile, run unit tests, produce build/libs/focalis-<version>.jar
./gradlew runClient    # launch a development client
```

The first build downloads and decompiles Minecraft, which takes a few minutes. The jar to install is
`build/libs/focalis-<version>.jar`. The `-dev` and `-sources` jars are for development.

Rendering changes can be checked in the development client with the graphics QA runner in
[tools/qa](tools/qa/README.md).

## Project layout

All code lives under `io.github.silentcall2598.focalis`:

| Package | Responsibility |
| --- | --- |
| `core` | Startup order, system wiring, shared logging and the coremod that hands the Mixin config to MixinBooter |
| `config` | `config/focalis.cfg` access |
| `feature` | Toggleable features, availability checks and failure isolation |
| `compat` | Detection of other mods Focalis must coexist with |
| `render` | Rendering subsystem |
| `render.lifecycle` | Frame stage model and the hooks that dispatch it |
| `render.target` | Focalis-owned framebuffers and the world target |
| `mixin` | Mixins. They report render boundaries to `render.lifecycle`, and one points binds of Minecraft's framebuffer at the world target while it's in use |
| `render.state` | OpenGL context information |
| `shader` | Shader support. So far shaderpack loading (`shader.pack`), program compiling and world program binding (`shader.program`) and the experimental post pass (`shader.post`) |
| `diagnostics` | Environment and OpenGL reports, crash report section, diagnostic features |

## License

Focalis is licensed under the GNU Lesser General Public License, version 3 only (SPDX: `LGPL-3.0-only`).
See [LICENSE](LICENSE). LGPLv3 adds additional permissions on top of GPLv3, whose text is included in [COPYING](COPYING).
