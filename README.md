
<div align="center">

![Focalis banner](assets/Focalis%20banner.png)

<br>

[![status](https://img.shields.io/badge/status-experimental-4c6ef5)](https://github.com/SilentCall2598/Focalis)
[![minecraft](https://img.shields.io/badge/Minecraft-1.12.2-3fa34d)](https://www.minecraft.net/)
[![forge](https://img.shields.io/badge/Forge-14.23.5.2860-f08c00)](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html)
[![runtime](https://img.shields.io/badge/runtime-Java_8-1f6feb)](https://adoptium.net/)
[![license](https://img.shields.io/badge/license-LGPL--3.0--only-6f42c1)](LICENSE)
[![discord](https://img.shields.io/badge/Discord-Focalis-5865F2?logo=discord&logoColor=white)](https://discord.gg/wmJKKvkCMX)

<br>

[Discord](https://discord.gg/wmJKKvkCMX) •
[Issues](https://github.com/SilentCall2598/Focalis/issues) •
[Releases](https://github.com/SilentCall2598/Focalis/releases) •
[License](LICENSE)

</div>

---

## About

Focalis is an independent, modular graphics and optimization project for **Minecraft Forge 1.12.2**. Its goal is to bring modern rendering features, shader support, and targeted performance improvements to a version of Minecraft with a large modding community.

The project is being built with modded environments in mind, with an emphasis on compatibility, maintainable systems, and predictable rendering behavior. Shader support is the first major milestone, with broader rendering and visual improvements planned over time.

**Focalis is experimental and is not yet a replacement for OptiFine or Iris.**

## Current State

Development currently focuses on the foundations of shader support and integration with Minecraft's existing renderer. Work so far includes:

- Shaderpack loading and program compilation
- Render-stage handling and program routing
- Framebuffer and OpenGL resource management
- Texture, lightmap, and shader-input infrastructure
- Terrain inputs and entity/block-entity shader contexts
- Integration with Minecraft's existing rendering systems

These systems have been exercised in development builds and modded testing, but the complete shader pipeline is still under construction. Full shaderpack compatibility and finished shader visuals are not available yet.

## Project Direction

Focalis is being designed around four main areas:

**Shaders and Rendering**

Shaderpack support, rendering targets, and improvements to Minecraft's rendering pipeline.

**Optimization**

Reducing unnecessary rendering work while improving performance where practical.

**Visual Features**

Lighting, textures, and other graphics used in modded Minecraft.

**Compatibility**

Keeping individual systems adaptable and minimizing conflicts with other Forge mods.

The goal is for these areas to work together while remaining modular, without requiring every feature to be enabled.

Development proceeds in phases. Each major addition is reviewed and tested before it becomes part of the main development branch.

## Roadmap and Progress

The first major goal is a usable shader pipeline for modded 1.12.2. This is a public overview of that progression, followed by the project's broader direction.

| Milestone | Status | Scope |
| --- | --- | --- |
| Shader foundations | Completed milestone | Shaderpack loading, compilation, program routing, resource management, and core shader inputs. |
| Terrain and object inputs | Completed milestone | Extended terrain inputs, material IDs, and entity/block-entity shader contexts. |
| Special draw handling | Current focus | Armor glint, glowing eyes, beacon beams, block-breaking overlays, and defined handling for custom skies. |
| Render-state hardening | Planned | Stronger state restoration and enforcement of shaderpack settings at the affected draws. |
| Full frame pipeline | Planned | Multiple render targets, depth textures, composite/final passes, and hand, water, and transparency integration. |
| Shadows | Planned | Shadow rendering and integration with the world pipeline. |
| First usable shader release | Planned | Validate the initial Complementary Reimagined target, add user-facing controls, and establish tested compatibility and installation guidance. |
| Broader graphics and optimization | Longer term | Renderer improvements, dynamic lighting, resource-pack features, and wider hardware and platform validation. |

A completed milestone means its current scope has been implemented and tested; it does not mean full shaderpack or mod compatibility. Later stages will build on and refine that work.

The roadmap describes the intended order of development, without fixed release dates. Scope may change as compatibility testing reveals new requirements.

## Requirements

| Component | Requirement |
| --- | --- |
| Minecraft | 1.12.2 |
| Forge | 14.23.5.2847 or newer |
| Recommended Forge | 14.23.5.2860 |
| Java runtime | Java 8 |
| MixinBooter | [10.7](https://github.com/CleanroomMC/MixinBooter), installed separately |
| Installation | Client-side only |

Keep MixinBooter's original release filename unchanged so it loads before Focalis.

Some experimental rendering features will have additional OpenGL requirements.

Focalis is a client-side mod and does not require installation on the server.

## Building

Focalis uses Gradle and [RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle).

Use a modern JDK for the Gradle build environment. The mod itself is compiled for Java 8.

Build the project:

```bash
./gradlew build
```

The compiled JAR will be available in `build/libs`.

Launch the development client:

```bash
./gradlew runClient
```

## Testing and Contributions

Focalis is independently developed and maintained.

I am not currently looking to form an official development team, but community testing and contributions are welcome and recognized.

If you encounter a bug or compatibility issue, please report it through [GitHub Issues](https://github.com/SilentCall2598/Focalis/issues) or the [Discord server](https://discord.gg/wmJKKvkCMX).

If you are reporting an issue, try to include:

- Minecraft and Forge versions
- Modpack or mod list
- Steps to reproduce the problem
- Relevant logs or crash reports
- Screenshots if available

Pull requests are welcome. Contributions are reviewed for compatibility, performance, maintainability, code quality, and fit with the project's direction.

## Community

If you want to follow development, share feedback, or help test Focalis:

**[Join the Focalis Discord](https://discord.gg/wmJKKvkCMX)**

Development updates, announcements, and future testing opportunities will be shared there.

## License

Focalis is licensed under the **GNU Lesser General Public License, version 3 only** (`LGPL-3.0-only`).

See [LICENSE](LICENSE) and [COPYING](COPYING) for the full license text.
