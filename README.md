<div align="center">

![Focalis banner](assets/Focalis%20banner.png)

[![status](https://img.shields.io/badge/status-experimental-4c6ef5)](https://github.com/SilentCall2598/Focalis)
[![minecraft](https://img.shields.io/badge/Minecraft-1.12.2-3fa34d)](https://www.minecraft.net/)
[![forge](https://img.shields.io/badge/Forge-14.23.5.2860-f08c00)](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html)
[![runtime](https://img.shields.io/badge/runtime-Java_8-1f6feb)](https://adoptium.net/)
[![license](https://img.shields.io/badge/license-LGPL--3.0--only-6f42c1)](LICENSE)
[![discord](https://img.shields.io/badge/Discord-Focalis-5865F2?logo=discord&logoColor=white)](https://discord.gg/wmJKKvkCMX)

[Discord](https://discord.gg/wmJKKvkCMX) •
[Issues](https://github.com/SilentCall2598/Focalis/issues) •
[Releases](https://github.com/SilentCall2598/Focalis/releases) •
[License](LICENSE)

</div>

---

## About

**Focalis** is an open-source rendering and optimization project for **Minecraft Forge 1.12.2**. I originally started working on it while playing Divine Journey 2, mostly because I wanted shader support without having to rely on OptiFine. It's grown into a larger project since then.

The first goal is to get shaderpacks working reliably, particularly in larger modpacks where rendering compatibility can be a problem. Beyond that, I want Focalis to be a foundation for other graphics and rendering improvements, rather than just another way to load shaders.

**Focalis is still experimental.** It isn't a drop-in OptiFine or Iris replacement, and full shaderpack rendering isn't ready yet.

## Design Philosophy

Focalis is being built as its own rendering platform, not a recreation of an existing shader mod. I study other projects and public shader-format documentation to understand what needs to work, but the actual implementation is designed around Focalis and its own requirements.

Legacy shaderpacks are important because they're what people already use on 1.12.2. I want to support those packs without making their old formats and limitations dictate how the rest of Focalis has to work.

That means keeping the different systems modular, being careful about resource ownership and compatibility, and measuring performance changes instead of assuming they'll help. There's room to explore more advanced rendering features later, but getting the current shader pipeline working properly comes first.

## Current State

A fair amount of the groundwork is already in place. Focalis currently has experimental support for:

- Finding shaderpacks, reading supported settings, and compiling shader programs.
- Routing terrain, entities, block entities, and certain special draws to shader programs.
- Supplying terrain attributes such as normals, material IDs, texture midpoints, and tangents.
- Shader uniforms, textures, lightmaps, and dimension-specific program handling.
- Focalis-owned render targets and a basic experimental post-processing path.
- Restoring shader-related state after vanilla and modded rendering, including selected alpha and blend directives.

These parts have been tested in development and in modded Minecraft, including DJ2. That doesn't mean full shaderpack support is working yet. Multiple render targets, depth handling, the full pass sequence, and several other effects still need to be integrated.

Right now I'm working on the **integrated shader session**, bringing the existing systems together so configuration, programs, inputs, render targets, and their lifecycles are managed more consistently.

## Roadmap and Progress

I'm developing Focalis in phases, with testing and review between major changes. There isn't a fixed release date yet.

| Milestone | Status | What it covers |
| --- | --- | --- |
| Shader foundations | Complete | Shaderpack loading, compilation, program routing, and initial shader inputs. |
| Terrain and object integration | Complete | Terrain attributes, material IDs, entity and block-entity contexts, and special draws. |
| Render-state handling | Complete | Restoring relevant rendering state and handling selected draw-time directives. |
| Integrated shader session | In progress | Bringing program caches, configuration, inputs, targets, and resource lifecycles together. |
| Full rendering pipeline | Planned | Multiple render targets, depth, composite/final passes, hand rendering, water, and transparency. |
| First external shaderpack milestone | Planned | Getting a supported external shaderpack working through the integrated pipeline. |
| First usable shader release | Planned | Wider compatibility testing, user-facing controls, installation guidance, and a build people can actually use. |
| Other graphics and optimization work | Longer term | Renderer improvements, lighting, visual features, and broader mod and hardware compatibility. |

"Complete" here means the planned work for that milestone has passed its checks. It doesn't mean every shaderpack or mod is supported. The first successful external pack also won't automatically mean Focalis is ready for a public beta.

## Requirements

| Component | Requirement |
| --- | --- |
| Minecraft | 1.12.2 |
| Forge | 14.23.5.2847 or newer |
| Recommended Forge | 14.23.5.2860 |
| Java runtime | Java 8 |
| MixinBooter | [10.7](https://github.com/CleanroomMC/MixinBooter), installed separately |
| Installation | Client-side only |

Keep MixinBooter's original release filename unchanged so it loads before Focalis. Some experimental features also have additional OpenGL requirements.

Focalis doesn't need to be installed on the server. These are the requirements for the project and its experimental builds, not a claim that a general shader release is ready.

## Building

Focalis uses Gradle and [RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle). You'll need a modern JDK for the build tools, although Focalis itself targets Java 8.

Build the mod:

```bash
./gradlew build
```

The JAR will be in `build/libs`.

To start the development client:

```bash
./gradlew runClient
```

Development builds can contain unfinished rendering features. I wouldn't recommend treating them as stable shader releases or dropping them into a random modpack expecting everything to work.

## Testing and Contributions

I'm developing and maintaining Focalis independently. I'm not recruiting an official development team or running a public testing program right now, but technical feedback, bug reports, and pull requests are welcome.

I've been testing it in development clients and real modpacks, with DJ2 as the main compatibility environment. Those results only cover the setups actually tested, not every GPU, driver, or collection of mods.

If you run into a reproducible issue, you can report it through [GitHub Issues](https://github.com/SilentCall2598/Focalis/issues) or the [Discord server](https://discord.gg/wmJKKvkCMX). It helps to include:

- Your Minecraft, Forge, and Java versions.
- The modpack, relevant mods, shaderpack, and GPU.
- What happened and how to reproduce it.
- Logs, crash reports, or screenshots if you have them.

Pull requests are welcome too. I review contributions with compatibility, correctness, performance, and maintainability in mind.

## Community

If you're interested in following the project or talking about its development, there's a **[Focalis Discord](https://discord.gg/wmJKKvkCMX)**. I'll also use it for updates and future testing opportunities when the project is ready for them.

## License

Focalis is licensed under **LGPL-3.0-only** (GNU Lesser General Public License, version 3 only).

See [LICENSE](LICENSE) and [COPYING](COPYING) for the full license text.
