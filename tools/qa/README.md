# Graphics QA

Checks Focalis rendering in the real development client. A run launches `runClient` in QA mode, an in-game
probe drives one scenario and checks the GL state around Focalis's render work, and the runner collects a
report with screenshots and logs.

## Prerequisites

- Windows 10 1607 or newer, with Windows PowerShell 5.1 or PowerShell 7
- JDK 17 or newer for the Gradle wrapper, through `JAVA_HOME` or `java` on `PATH`, same as a normal build
- A desktop session. The client opens a normal window but doesn't need focus.

## Commands

```powershell
.\tools\qa\run-qa.ps1 -Scenario post-process-smoke
.\tools\qa\run-qa.ps1 -Scenario post-process-smoke,world-reload
.\tools\qa\run-qa.ps1 -Scenario all
.\tools\qa\run-qa.ps1 -Scenario post-process-resize -AllowFullscreen # include a fullscreen round trip
.\tools\qa\run-qa.ps1 -Scenario all -Obfuscated                      # use the reobfuscated jar
.\tools\qa\run-qa.ps1 -List
```

The exit code is 0 when every scenario passed, 1 when one failed and 2 when the run couldn't start.

## Scenarios

| Scenario | What it checks |
| --- | --- |
| `vanilla-baseline` | Shaders off. The post pass never runs and Focalis leaves the world-end GL state and GL errors alone. |
| `post-process-smoke` | The pass runs on every world frame through camera turns, F3, F1, inventory, pause and chat, with one program and one capture. |
| `post-process-state-restore` | Blend, alpha test, depth test, active unit, read framebuffer and viewport are changed before the pass, one kind per frame, and must come back unchanged. A different draw framebuffer must make the pass skip without touching anything. |
| `post-process-high-texture-unit` | Units 8, 12 and the highest usable one are left active through raw GL before the pass. Afterwards GL must be back on that unit, units 2 and 3 untouched, and GlStateManager switches must really reach GL. |
| `post-process-resize` | Four window sizes and back. Each size gives exactly one capture of that size and the old framebuffer and textures are deleted. `-AllowFullscreen` adds a fullscreen round trip. |
| `post-process-failure` | The pass throws after changing its state. The feature must fail cleanly, the state must be restored on that same frame and the capture and program must be deleted. |
| `post-process-bad-pack` | A pack that doesn't compile. Rendering stays vanilla, nothing is created and the feature stays active. |
| `world-reload` | Leaves and rejoins twice. The pass keeps running and the capture isn't recreated. |

Every scenario also fails on GL errors raised during Focalis's world-end work, on any change to the promised GL
state, on unexpected feature failures, and on unexpected warnings, errors or exceptions in the client log.

## Output

Everything goes under `build/`, which Git ignores.

- `build/qa/latest/report.json` and `summary.md` cover the whole run. `source` records the commit, branch and
  whether the working tree had uncommitted or untracked files. With `-Obfuscated`, `artifact` has the SHA-256 of
  the release jar the client loaded.
- `build/qa/latest/<scenario>/probe.json` has the probe's detailed results for one scenario
- `build/qa/latest/<scenario>/screenshots/` has named screenshots such as `post-smoke-f3.png`
- `build/qa/latest/<scenario>/client.log` and `gradle.log`
- `build/qa/latest/<scenario>/client-threads.txt`, a thread dump taken when the client stops responding
- `build/qa/game` is the QA game folder. Its world is recreated with a fixed seed on every run.

## How it works

The runner passes `-PqaScenario`, `-PqaGameDir` and `-PqaOutputDir` to Gradle, which point `runClient` at the
QA game folder and set the `focalis.qa.*` system properties. Without those properties no QA code runs at all.

In QA mode the probe (`diagnostics.qa`) registers one world-end listener before the features and one after, so it
can record and compare the GL state around their work and drain `glGetError` on both sides. It drives the
scenario from client ticks with vanilla calls: it creates or loads the QA world, sets the HUD flags that F1 and
F3 toggle, opens GUI screens, turns the camera, leaves and rejoins, and saves named screenshots between frames
just like F2. Window resizes are the one thing it asks the runner for, through a request in `probe.json`.
The failure scenario throws from `PostPassMonitor.beforeDraw`, which does nothing outside QA mode.

## What still needs a person

- Judging motion. The scenarios check single frames and per-frame counters, not flicker as a person sees it.
- Real input. F1, F3 and screens are set directly, not through key presses, and the player doesn't walk.
- Anaglyph and framebuffers-off skips, spectator shaders, the Nether and the End
- Other GPUs and drivers, and modpacks
