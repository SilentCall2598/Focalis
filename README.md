
<div align="center">

# Focalis | A Minecraft 1.12.2 graphics and optimization project

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

Focalis is a project focused on taking modern rendering features and performance improvements, and applying them to older versions of Minecraft

The project is being designed to be modular for the Forge Version **1.12.2**, allowing more room for compatibility, features maintainability, and easy to understand rendering behavior in modded environments.

As of **now**, Focalis **is not a capable replacemement for OptiFine or Iris**, Focalis is still in its early phases and a work in progress

## Current State

The current state of Focalis is building basic foundations for shader support and rendering, that means:

- Shaderpack loading and program compilation
- Render-stage handling and program routing
- Framebuffer and OpenGL resource management
- Texture, lightmap, and shader-input infrastructure
- Compatibility with Minecraft's existing rendering systems

Some of these rendering features are already functional and tested in developmental builds, however, are still experimental and a work in progress.

## Project Direction

Focalis is currently being designed around four main categories:

**Shaders and Rendering**

Shaderpack support, rendering targets, and improvements to Minecraft's rendering pipeline.

**Optimization**

Reducing unnecessary rendering work while improving performance where practical.

**Visual Features**

Lighting, textures, and other graphics used in modded Minecraft.

**Compatibility**

Keeping individual systems adaptable and minimizing conflicts with other Forge mods.

The vision is to make the ares work together, while still remaining independent, and without requiring every feature to be enabled.

Development is done in phases, each major addition is reviewed, tested extensively, and approved once it passes.

## Requirements

| Component | Requirement |
| --- | --- |
| Minecraft | 1.12.2 |
| Forge | 14.23.5.2847 or newer |
| Recommended Forge | 14.23.5.2860 |
| Java runtime | Java 8 |
| MixinBooter | [10.7](https://github.com/CleanroomMC/MixinBooter), installed separately |
| Installation | Client-side only |

You have to keep MixinBooter's original release filename the same so it loads before Focalis.

Some experimental rendering features will have additional OpenGL requirements.

Focalis is a client-side mod, it doesn't require server installation.

## Building

Focalis uses Gradle and [RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle).

Use a modern JDK for the Gradle build environment, the mod itself is compiled for Java 8.

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

I am not looking for an official development team, that said, community testing and contributions are welcome and recognized.

If you encounter a bug or compatibility issue, please report it through [GitHub Issues](https://github.com/SilentCall2598/Focalis/issues) or the [Discord server](https://discord.gg/wmJKKvkCMX).

If you are reporting an issue, try to include:

- Minecraft and Forge versions
- Modpack or mod list
- Steps to reproduce the problem
- Relevant logs or crash reports
- Screenshots if available

Pull requests to the GitHub for contributions are welcome for review. Changes are individually reviewed by the criteria of compatiblity, performance, maintainability, general code quality, and how it would fit into the project.

## Community

If you want to follow development, share feedback, or help test Focalis:

**[Join the Focalis Discord](https://discord.gg/wmJKKvkCMX)**

Development progress, announcements, and future testing opportunities will be posted in there.

## License

Focalis is licensed under the **GNU Lesser General Public License, version 3 only** (`LGPL-3.0-only`).

See [LICENSE](LICENSE) and [COPYING](COPYING) for the full license text.
