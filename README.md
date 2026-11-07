# Supernova – Re:AcademyCraft 1.5.0 add-on (Minecraft 1.20.1, Forge 47.x)

One ability, **Supernova**, in its own category, **Stellar**. A separate mod: it does not change Re:AcademyCraft.

## What it does

Aim anywhere within 40 blocks and press the skill key.

1. **Formation (1 s):** a small star forms above the aimed spot while the corona pulses.
2. **Explosion (4 rings over ~1.2 s):** expanding rings of fire and force blast outward to **24 blocks**.
   Each ring damages and knocks back hostile enemies. Damage and knockback decrease with each ring.
3. **Final burst:** a shockwave shell expands to finish.

Safety and balance: you and your allies are never hurt; only targets the server's PvP / team / protection rules let you attack are affected (checked through Re:AcademyCraft's safety service). Bosses take 35% damage and 30% knockback. No blocks are changed. Death, logout or a dimension change end it cleanly.
Cost: **8000 CP and 200 overload** (level-4 players have about 9000 CP / 350 overload by default), cooldown **180 s**.
Every number is in `config/supernova-common.toml` (created on first launch).

## Visuals – zero particles

Drawn entirely as custom 3D geometry on the client:
- Formation: growing star core with pulsing corona rings
- Explosion: 4 expanding rings (fire outer edge, force inner edge) with ember sparks
- Final: expanding shockwave shell

The source contains no particle API at all (checked by search).

## Honest status

| Stage | State |
|---|---|
| Code written | Yes (server ability, network, client rendering, config) |
| Syntax / API check | Done in a sandbox: parses cleanly, Re:AcademyCraft API types resolve. Minecraft/Forge classes were NOT available there |
| Compiled with Forge | **Not yet** – the GitHub build below is the first real compile |
| Tested in Minecraft | **No** |

If the GitHub build fails, copy the red error text and send it back; most likely a one-line fix to a vanilla name.
Known unknowns that only a real test can settle: how the visuals look and perform; whether a one-skill level-4 category can be levelled up and learned smoothly in the Ability Developer; and whether the cost, damage and ring timing feel right.

## Build it on GitHub (no Java needed)

1. Sign in at github.com. Click **+** (top right) → **New repository**. Name it `supernova-addon`, choose **Private**, click **Create repository**.
2. Unzip `supernova-addon.zip` on your PC. In the new repository click **uploading an existing file** (or **Add file → Upload files**) and drag in everything INSIDE the `supernova-addon` folder (the `src`, `libs`, `gradle` folders and the files next to them). Click **Commit changes**.
3. Click the **Actions** tab. If it asks you to choose a workflow ("Get started with GitHub Actions"), click **set up a workflow yourself**, select everything in the editor, delete it, and paste the contents of `.github/workflows/build.yml` from the zip. Click **Commit changes…** twice. (Drag-and-drop often skips the hidden `.github` folder, which is why this step may be needed.)
4. A run starts by itself (or press **Run workflow**). The first build takes 10–15 minutes. When it shows a green tick, open the run, scroll to **Artifacts**, download **supernova-mod**, and unzip it: the `.jar` inside is the mod (`supernova-forge-1.20.1-1.0.0.jar`).
5. If the run is red, open it, click **build**, copy the red error text and send it back.

## Try it (use a separate test instance, not your live server)

1. Make a new Minecraft 1.20.1 instance with Forge 47.4.10 or newer.
2. Put two jars in its `mods` folder: the normal **Re:AcademyCraft 1.5.0** jar from CurseForge, and `supernova-forge-1.20.1-1.0.0.jar`.
3. Start a creative world. Take the **Stellar Factor** from the Ingredients creative tab and use the Ability Developer machine (see the mod's tutorial) to get the category, learn **Supernova**, and bind it in the skill preset editor.
4. Aim at an open area with mobs around and press the key.
   On a server, install the same jars on the server **and every client**.

## Files

* `Supernova.java` – the ability (formation, rings, damage, knockback); `SupernovaMod.java` – registration; `Cfg.java` – all balance numbers
* `net/` – the small network messages; `client/` – the 3D rendering (`FxRenderer`, `Geo`, `BhRenderTypes`)
* `.github/workflows/build.yml` – the GitHub build

## Test commands (creative world, cheats on)
```
/aim cat supernova:stellar
/aim level 4
/aim learn supernova:supernova
/aim fullcp
```
Bind it in **Skills and presets**, press **Toggle ability**, then the skill key. Press the key only once: a second press cancels it.

## Step 1 – cinematic phases (reviewed and corrected)
Supernova now runs in five phases: gathering (2 s), compression (2 s), silence (1 s), detonation (1 s, four damaging rings),
afterglow (2 s). Each phase length is in `config/supernova-common.toml`. Visuals are custom geometry only (no particles).
Corrections made after review: duplicate config fields, a missing render type (DISTORTION), duplicate helper methods,
broken helper code, wrong argument lists, float/double mix-ups, unfinished stream drawing, and phase timing that was
measured from the wrong starting point.
Status: written and syntax-checked; needs the GitHub build (compile) and an in-game test.
Note: the client reads phase lengths from its own config file. If you change them on a server, change them on the clients too.
