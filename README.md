# Focalis

Focalis is an open-source graphics and optimization platform for Minecraft Forge 1.12.2. The long-term aim is a
modular, compatibility-first alternative to OptiFine for large Forge modpacks.

## Status

Focalis is in early development. The current version is mostly a foundation: it loads as a client-side Forge
mod, reports diagnostic information, and provides the internal structure future rendering systems will build
on. **With default settings it does not change what Minecraft renders.** The only exception is the
experimental `shaders` feature, which is off by default and described below.

Planned systems, none of which exist yet: OptiFine/Iris-compatible shader support, a high-performance
renderer, rendering optimizations, dynamic lights, connected textures, emissive textures, CIT, CEM, custom
skies, video settings, and compatibility tooling for large modpacks.

## What it does today

- Logs its version and the runtime environment (Java, OS, CPU, Forge, and other rendering-related mods present).
- On the first rendered frame, logs the OpenGL driver, version, limits, video memory and the capabilities
  future systems depend on.
- Adds a Focalis line with feature states to crash reports.
- Provides an optional `frame_stats` diagnostic feature that periodically logs frame timing.
- Provides an experimental, off-by-default `shaders` feature. It is the first step of shader support and
  only runs Focalis's own test program (`shaders/focalis_post.vsh` and `.fsh`) as a single post-process pass
  over the world image at the end of the world pass, before the hand and HUD are drawn. Regular OptiFine or Iris
  shaderpacks are not supported, and a pack without that program is refused. If the pack or its program fails to
  load or compile, the problem is logged and rendering stays vanilla. It stays unavailable when OptiFine is
  installed.
  The development test pack lives in `src/test/resources/shaderpacks/focalis-depth-view`.

## Requirements

- Minecraft 1.12.2
- Forge 14.23.5.2860 recommended. Focalis currently accepts 14.23.5.2847 or newer.
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
| `core` | Startup order, system wiring and shared logging |
| `config` | `config/focalis.cfg` access |
| `feature` | Toggleable features, availability checks and failure isolation |
| `compat` | Detection of other mods Focalis must coexist with |
| `render` | Rendering subsystem |
| `render.lifecycle` | Frame stage model and the hooks that dispatch it |
| `render.state` | OpenGL context information |
| `shader` | Shader support. So far shaderpack loading (`shader.pack`), program compiling (`shader.program`) and the experimental post pass (`shader.post`) |
| `diagnostics` | Environment and OpenGL reports, crash report section, diagnostic features |

## License

Focalis is licensed under the GNU Lesser General Public License, version 3 only (SPDX: `LGPL-3.0-only`).
See [LICENSE](LICENSE). LGPLv3 adds additional permissions on top of GPLv3, whose text is included in [COPYING](COPYING).

Focalis does not contain OptiFine code, decompiled or otherwise. Any code adapted from other open-source projects
is used only after its license has been checked for compatibility, and its origin is recorded.
