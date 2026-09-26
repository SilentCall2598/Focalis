# Focalis

Focalis is an open-source graphics and optimization platform for Minecraft Forge 1.12.2. The long-term aim is a
modular, compatibility-first alternative to OptiFine for large Forge modpacks.

## Status

Focalis is in early development. The current version is a foundation only: it loads as a client-side Forge
mod, reports diagnostic information, and provides the internal structure future rendering systems will build
on. **It does not change what Minecraft renders.**

Planned systems, none of which exist yet: OptiFine/Iris-compatible shader support, a high-performance
renderer, rendering optimizations, dynamic lights, connected textures, emissive textures, CIT, CEM, custom
skies, video settings, and compatibility tooling for large modpacks.

## What it does today

- Logs its version and the runtime environment (Java, OS, CPU, Forge, and other rendering-related mods present).
- On the first rendered frame, logs the OpenGL driver, version, limits, video memory and the capabilities
  future systems depend on.
- Adds a Focalis line with feature states to crash reports.
- Provides an optional `frame_stats` diagnostic feature that periodically logs frame timing.

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
| `shader` | Shader support. So far shaderpack loading (`shader.pack`) and program compiling (`shader.program`), nothing renders yet |
| `diagnostics` | Environment and OpenGL reports, crash report section, diagnostic features |

## License

Focalis is licensed under the GNU Lesser General Public License, version 3 only (SPDX: `LGPL-3.0-only`).
See [LICENSE](LICENSE). LGPLv3 adds additional permissions on top of GPLv3, whose text is included in [COPYING](COPYING).

Focalis does not contain OptiFine code, decompiled or otherwise. Any code adapted from other open-source projects
is used only after its license has been checked for compatibility, and its origin is recorded.
