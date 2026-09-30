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
.\tools\qa\run-qa.ps1 -Scenario all -WorldTarget                     # draw the world through the world target
.\tools\qa\run-qa.ps1 -Scenario all -WorldPrograms                   # bind the world programs of the test pack
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
| `world-lifecycle` | Test pack on, with a pause screen and a rejoin. Start/end pairs match the world passes in both sessions and the pass runs once in every WORLD END. |
| `world-target-failure` | World target on. Throws inside its copy back. The feature must fail cleanly, put Minecraft's framebuffer back on that same world pass, delete its target and never redirect again. |
| `render-stages` | Test pack on, in rain next to an entity and in view of a glowing one, below and then above cloud height. Every precise stage and draw kind fires exactly once per world pass, like the three terrain layers, both entity passes and both particle kinds, except the sun and moon, which fire twice inside the sky. Each routes to the expected shader program role with nothing unclassified. |
| `world-program-binding` | World target and world programs on, with the `render-stages` tour. Every world stage and draw kind must have its role's program from `focalis-world-routes` current in every world pass, drawing into the world target, and the sun and moon must put the sky's program back. Roles that share a program must share its id, and HAND must stay unbound. |
| `world-program-failure` | World target and world programs on. Throws right after the sun's scope opened inside the sky's. The feature must fail cleanly, unwind both scopes on that same START, delete every program and never bind again, while the world target keeps running. |
| `world-program-bad-pack` | World programs on with a copy of `focalis-world-routes` where no program compiles. Each failed program is logged, the binding stops for the session with the feature still active, and nothing is ever bound. |

Every scenario also fails on GL errors raised during Focalis's world-end work, on any change to the promised GL
state, on unexpected feature failures, and on unexpected warnings, errors or exceptions in the client log. It
also fails when the WORLD START hook never fires, when a WORLD START doesn't get exactly one WORLD END before the
next one, or when the post pass renders anywhere but inside a WORLD END or more than once in one. The precise
stages like SKY, TERRAIN and HAND have to be balanced and close in order with the same draw kind they started
with, world pass stages have to happen while a world pass is open, and HAND has to happen inside the frame after
its world pass ended.

`-WorldTarget` turns on the experimental world target in every scenario. Every world pass then has to draw into it,
checked when translucent terrain starts, which is after vanilla rebinds its framebuffer for entity outlines. Each
pass has to end with the framebuffer bindings it started with, and a replaced target has to be deleted. Without it,
every pass has to draw into Minecraft's framebuffer. `post-process-state-restore` expects the pass to skip when
something else is bound for drawing, which the world target corrects, so it isn't meant to run with `-WorldTarget`.

`-WorldPrograms` turns on the experimental world programs with the `focalis-world-routes` test pack in every
scenario. Right after each world stage START the program of its role has to be current, and right after its END the
program from before the START has to be back. No Focalis program may be current at HAND or in a stage without a
scope. The programs have to be built once for the whole run, across rejoins.

## Output

Everything goes under `build/`, which Git ignores.

- `build/qa/latest/report.json` and `summary.md` cover the whole run. `source` records the commit, branch and
  whether the working tree had uncommitted or untracked files. With `-Obfuscated`, `artifact` has the SHA-256 of
  the release jar the client loaded. It stays null unless every scenario found the same jar.
- `build/qa/latest/<scenario>/probe.json` has the probe's detailed results for one scenario
- `build/qa/latest/<scenario>/screenshots/` has named screenshots such as `post-smoke-f3.png`
- `build/qa/latest/<scenario>/client.log` and `gradle.log`
- `build/qa/latest/<scenario>/client-threads.txt`, a thread dump taken when the client stops responding
- `build/qa/game` is the QA game folder. Its world is recreated with a fixed seed on every run.

## How it works

The runner passes `-PqaScenario`, `-PqaGameDir` and `-PqaOutputDir` to Gradle, which point `runClient` at the
QA game folder and set the `focalis.qa.*` system properties. Startup always checks for a scenario property.
Without one no probe is created, so there are no QA listeners, GL checks, scenario steps or screenshots, and
nothing is written.

In QA mode the probe (`diagnostics.qa`) registers one world listener before the features and one after. At WORLD
END it records and compares the GL state around their work and drains `glGetError` on both sides. At WORLD START
it only records the order of the phases and makes no GL calls. WORLD START comes from the Mixin hook at the start
of `EntityRenderer.renderWorldPass` and WORLD END from Forge's `RenderWorldLastEvent`. It drives the
scenario from client ticks with vanilla calls: it creates or loads the QA world, sets the HUD flags that F1 and
F3 toggle, opens GUI screens, turns the camera, leaves and rejoins, and saves named screenshots between frames
just like F2. Window resizes are the one thing it asks the runner for, through a request in `probe.json`.
The failure scenario throws from `PostPassMonitor.beforeDraw`, which does nothing outside QA mode.

## What still needs a person

- Judging motion. The scenarios check single frames and per-frame counters, not flicker as a person sees it.
- Real input. F1, F3 and screens are set directly, not through key presses, and the player doesn't walk.
- Anaglyph and framebuffers-off skips, spectator shaders, the Nether and the End
- Other GPUs and drivers, and modpacks
