# Minecraft GUI Injector

Java 25 + Gradle desktop process picker with a Fabric Minecraft 26.1.2 GUI agent.

## Setup

The `java` on PATH is Java 8, which is too old. Build with the Java 25 runtime that ships with the Minecraft Launcher (`gradle.properties` already points the toolchain at it):

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime\java-runtime-epsilon\windows-x64\java-runtime-epsilon"
```

## Build and run

1. `gradle build`. This fails with a clear message if the Minecraft 26.1.2 client JAR or its libraries are missing (override with `-PminecraftClientJar=PATH` or `MINECRAFT_CLIENT_JAR`).
2. Start Minecraft (Fabric) and get to the main menu.
3. `gradle run`, select the Minecraft process and choose **Open GUI** (or **Update** if an older build is already in the game).

**Sharing:** `gradle shareZip` writes `build/distributions/SpearInjector.zip`, holding the jar, `Start.bat` and `HOW-TO-USE.txt` (both from `share/`). `Start.bat` opens the injector window with the Java 25 that comes with the Minecraft Launcher (Microsoft Store or classic install), or `JAVA_HOME` as a fallback. No Gradle or JDK is needed on the other PC.

**Versioning is automatic.** A running game cannot replace classes it has already loaded, so every changed build must use new class names to be injected next to the old one. The sources stay in package `app` with plain class names; the build compiles them into `app.b<tag>`, where the tag is a hash of the sources, and names the jar `minecraft-gui-injector-b<tag>.jar` with a matching `Agent-Class`. Unchanged sources keep their tag, any change gets a new one, and nothing is renamed by hand. Every build (and `gradle deleteOldJars` on its own) deletes older injector jars from `build/libs`; one still loaded by a running game is locked on Windows, so it stays until the game closes and goes on the next build after that.

The injector window has four commands for the selected process:

- **Open GUI** loads this build and opens its menu.
- **Update** switches off every version already in the game, waits for them, then opens this build. Use it after each rebuild instead of restarting Minecraft. A version that does not switch off within 2 s gets its own unload run directly on the game thread (older versions could stop ticking for good when you disconnected, so they never saw the request).
- **Unload** switches off every version in the game without opening a new one.
- **Status** lists every version loaded in the game, whether it is on, and for one that is on whether its loop is still running (builds from before the Status button cannot report that). It only reads; nothing changes.

Versions before v18 cannot switch off; the status line says how many need a Minecraft restart. From a terminal: `gradle run --args="attach <pid> build/libs/minecraft-gui-injector-b<tag>.jar update"` (or `open` / `unload` / `status`).

Settings are saved after every change to `.minecraft/config/spear-injector.properties` and loaded when a version starts and whenever you change server, so updates and Unload keep your setup. The keys are the same for every version.

Modules react only to your real key and mouse presses (mouse macro software counts, since it produces real clicks). The exception is **Trigger** on Shield Break, Mace Swap, Auto Weapon and W-Tap: **Click** (default) is your attack click, while **Any attack** is every attack packet your client sends, whoever sends it (your click, a server's own hit system, another mod or a script). With Any attack, Mace Swap and Auto Weapon select their item around that packet as it goes out, so it is neither delayed nor reordered; Shield Break holds the packet back and runs the break instead on the next frame (or sends it after all if the break cannot start). The modules' own hits never trigger each other. When a module's binding is also a Minecraft binding, for example Mouse 4 for the dash while Mouse 4 also selects the spear's hotbar slot, the press goes to the module when it fires and to the game when it does not, so the game never acts on a press the module used.

Numpad 8 toggles the GUI: a panel with a tab per category: **SPEAR** (Spear Dash, Shield Break, Mace Swap, Auto Totem, Auto Weapon), **UHC** (Web Escape, Web Trap, MLG Water, W-Tap, Jump Reset, Auto Gapple), **BEDWARS** (Clutch, Edge Sneak, Chest Stealer, Auto Armor), **UTIL** (Auto Tool, Auto Sprint, Fullbright, Zoom, Auto Respawn) and **HUD** (Speed HUD, Radar, Landing, Edit HUD). Click a tab to switch; the open tab is remembered. The server row sits at the top of every tab and **Unload** at the bottom. **Left-click** a module to turn it on or off; **right-click** a module marked `>` to open its settings underneath it (`v` while open). The status line at the bottom shows what the last module did.

### Delays and chance

Every combat module (Spear Dash, Shield Break, Mace Swap, Auto Totem, Auto Weapon, Quick Throw, Chest Swap, W-Tap) has a delay slider for each of its steps, so a swap, hit and swap back no longer have to go out in the same instant. A delay slider has two knobs, a min and a max, and every use picks a random time in between (for example 60–120 ms), so no two combos have exactly the same timing. Drag a knob to set it, and right-click a slider to put its default back. At 0 (shown as "Off"), that step goes at once, exactly as before; every delay starts at 0.

The steps are named the same everywhere: **Swap delay** (your click or key press, then the switch to the item), **Hit delay** / **Jab delay** / **Throw delay** / **Use delay** (the item is selected, then it is used), and **Swap back** / **Return** (the item has been used, then your own slot comes back). Modules that react on their own have **Reaction** (what they react to, then the first step). **Chance** (Shield Break, Mace Swap, Auto Weapon, W-Tap) is how many of your hits the module acts on; the rest are left as normal hits. Picking a hotbar slot yourself while a delayed step is waiting stops it and keeps your slot; opening a screen puts your slot back at once.

With **Trigger** on Any attack and a delay on, Mace Swap and Auto Weapon hold back the attack packet and send it as the hit of the delayed swap, instead of rewriting it as it goes out.

### Per-server settings

The top row shows the server you are on ("singleplayer" in your own world) and whether combat is on there. **Left-click** it to turn every module that acts for you (everything in the SPEAR, UHC and BEDWARS tabs; the UTIL modules work everywhere) off or on for that server; while off, their binds and attack clicks do nothing there and the HUD, Radar and Landing keep working. **Right-click** for **Own settings**: on gives that server its own copy of every module setting (starting from what you have now), saved under `server.<address>.` keys, and the menu loads it whenever you join that server; off goes back to your defaults (the server's copy is kept for next time). The HUD layout, toggle keys and menu position are shared by every server.

### Spear Dash

A fresh press of its binding swaps to the Lunge spear in your hotbar, jabs into open space and swaps back. Presses are read from the game's key and mouse events, so quick taps still register during lag spikes. The swap back always runs, even if the jab fails. If the server puts the hotbar back on the spear within 1.5 s of a dash (and you did not pick a slot yourself), it switches back again, up to three times, and flashes "Server undid swap, fixed" above the crosshair; "Server keeps spear!" means it gave up. Every dash and every such correction is written to `.minecraft/logs/spear-injector.log`, so a dash that did not switch back can be traced afterwards. Settings (right-click):

- **Bind**: a keyboard key or mouse button, including middle click, Mouse 4 and Mouse 5.
- **Dashes** (Single / Double / Triple): how many lunges one press fires. Spears need a full attack charge to jab and the charge resets after each jab, so each extra lunge goes out as soon as the charge is full again. Because the dash swaps straight back, that is the charge time of the item you hold (about 0.6 s with a sword). The chain stops if the spear is not charged within 2 s, if Lunge cannot push (see below), or if you open a screen. Each lunge costs durability and hunger like a normal one.
- **Swap delay**, **Jab delay**, **Return** (0–300, 0–300, 0–500 ms; see Delays and chance): all at 0, it swaps, jabs and swaps back in one step so lag cannot leave the spear selected. Return keeps the spear in hand that long after the jab.
- **Only from wind charge** (off by default): the binding dashes only while a wind charge is in your hand. Holding anything else, the press goes to the game, so a binding that is also the spear's hotbar key (Mouse 4 on hotbar 5, say) just selects the spear: wind charge then spear dashes, any other slot then spear opens the spear normally.
- **Chain gap** (0–500 ms): for Double and Triple, an extra wait once the spear is charged again before the next lunge.
- **Flat lunge** (Off / Smooth / Fast / Packet): **Packet** never moves your camera: the server is told you look level just for the jab, and your real view right after, so the lunge still pushes at full strength. Smooth and Fast glide your view to level before the jab. Lunge pushes along your look direction with the vertical part removed, so the push is full strength × cos(look angle): 45° up or down gives about 71%, 60° gives half. The glide is eased like a quick mouse flick (Smooth: about 0.2 s from 45°, 0.35 s from straight down; Fast: about half that) and the level view reaches the server in the game's normal movement update, with no extra packets. The jab goes out one game tick after you are level. Your view stays level afterwards.
- **Ready sound** (off by default): a quiet click the moment the Lunge spear becomes fully charged, so you know a dash can go without looking. Since every attack resets the charge, it clicks each time the charge refills.

### Shield Break

On its binding (R by default), if the player under your crosshair is holding up a shield toward you, it swaps to the first hotbar weapon that disables shields (axes) and hits the shield **Axe hits** times, one game tick apart. **Auto** (the default) reads your ping from the player list: at 40 ms or less it allows up to 3 hits, which are skipped as soon as the shield shows as down, and above that it uses 1, because your client cannot see the shield drop before extra hits would go out. The setting shows what Auto picked and your ping, e.g. "Auto (1, 92ms)". Then, with **Mace follow-up** on, it hits with a mace from your hotbar and swaps back to what you held. Extra axe hits are spread over ticks because several hits in one tick cannot catch a shield the first one missed; as soon as your client sees the shield go down, the remaining axe hits are skipped and the mace goes in. Each extra axe hit delays the mace by about a tick (50 ms).

Why the mace lands in full: the axe disables the shield (5 s for axes), and a blocked hit leaves the target's damage immunity at 0 damage, so the next hit deals its whole damage; the mace's fall bonus is not reduced by the attack cooldown, so the slam only needs you to be falling onto them. It never fires on its own: no shield up, no axe, or out of axe range means the press does nothing and the status line says why. Opening a screen mid-press swaps back and cancels; picking another hotbar slot mid-press stops it and keeps your slot. While it is on, "BLOCKING" appears right of the crosshair when your target is blocking toward you (red "BLOCKING (no axe)" if you have no axe). The swap back is watched and logged like Spear Dash's. "Blocking toward you" uses the game's own test: the player is blocking and you are within 90° of where their head faces, measured flat, so being above them on a mace drop counts the same as being level (straight overhead counts as blocked too). It also counts a shield that is still going up on your screen when the server will already be blocking with it by the time your hit arrives: a shield blocks once it has been raised for 0.25 s, and the server's count runs about your ping ahead of what you see. After a break, that player does not count as blocking until your client sees the shield drop, or until twice your ping plus 150 ms (at most 0.6 s) passes without it dropping; a quick second click in that window is a normal hit. Once the drop has been seen, or the window has passed, a shield on them is a real one (the break missed, or it went up again) and the next click breaks it. After each break it watches for the shield going down and shows the running score, e.g. "Shield down (5/7)" or "Shield stayed up (5/8)", and logs how long the shield took to drop. Several axe hits rarely help at normal ping: your client cannot see the shield drop within the 50–100 ms they take, and each one delays the mace, so compare the score with Axe hits at 1 and 2.

With **On attack click** (on by default), your normal attack click is a trigger too: clicking a player who is blocking toward you runs the whole shield break from that one click and the plain hit is skipped, so the two never double up; clicks on anything else are normal attacks, and extra clicks during a break are ignored. With **Trigger** on Any attack, every attack your client sends counts as that click (On attack click must stay on). **Range**: **Normal (3)** (default) uses the axe's and mace's own reach and your crosshair; **4.5 blocks** also finds a blocking player along your view past your crosshair, up to 4.5 blocks (not through blocks), and hits from there. A ranged break always goes out at once (axe, mace and swap back together, one axe hit, every delay skipped), because past normal reach the hits only register that way; it also works with Trigger on Any attack, since a click that far sends no attack packet to catch. A vanilla server takes melee hits up to a weapon's reach plus 3 blocks, but anti-cheat plugins usually flag hits past about 3 blocks. Shield Break takes the click before Reach Swap, so a spear jab never goes into a shield it could break. Settings (right-click): **Bind**, **On attack click**, **Trigger**, **Axe hits**, **Mace follow-up**, **Range**, **Chance** (of attack clicks on a blocking player; the bind always fires), and the delays **Swap delay** (click, then the axe), **Hit delay** (axe or mace selected, then its hit), **Between hits** (one axe hit to the next, on top of the game tick they always wait for), **Axe to mace** (last axe hit, then the mace) and **Swap back**. A slow combo still lands in full: the axe disables the shield for 5 s, and the blocked hit leaves the target without damage immunity.

### Mace Swap

When your attack click lands on something, it swaps to the right mace for that hit, hits with it, and swaps straight back, all in one step like Spear Dash (the plain hit with what you held is skipped). Which mace:

- **Dropping** (fallen more than 1.5 blocks and not gliding, so the hit is a mace slam): a **Density** mace, or any mace if you have no Density one, since every mace slams.
- **Otherwise**, per **On ground**: **Any mace** (default: a Breach mace if you have one, else any mace), **Breach only** (no swap without a Breach mace), or **Off**.

Missed swings, clicks on blocks, non-living things such as wind charges, and hits you already make with that mace are left alone. On a player blocking toward you, Shield Break takes the click first, and its mace follow-up uses the same pick (Density for a slam, else Breach, else any mace). The swap back is watched and logged like Spear Dash's. Settings (right-click): **Trigger** (Click / Any attack), **On ground** (Any mace / Breach only / Off), **Dropping** (Density / Off), **Chance**, **Swap delay**, **Hit delay** and **Swap back**. Extra clicks while a delayed swap is waiting are ignored. A Hit delay while you drop can let you land before the slam goes out.

### Auto Totem

Keeps a Totem of Undying in your offhand. When the offhand has none (it popped, or with **Mode** on **Always**, it holds something else), a totem from your hotbar goes there the way the swap-offhand key moves it: that slot is selected, swapped with the offhand, and your slot selected again, all at once. With your inventory open, a totem anywhere in it is moved the same way (as if you pressed the swap key over it). Whatever was in your offhand ends up where the totem was. **Mode**: **Empty offhand** (default: only refills an empty offhand, so a shield or other item there is left alone) or **Always**. **Reaction** (0–1000 ms) waits after the offhand empties before acting; **Step delay** (0–300 ms) spaces out the hotbar swap's three steps (select the totem, swap it to the offhand, select your slot again).

### Auto Weapon

When your attack click lands on something living while you hold something that is not a weapon (blocks, food, a pickaxe), it switches to the hardest-hitting weapon in your hotbar first, so that same click hits with it, and stays on it. A weapon is anything that adds attack damage over a bare hand, picked by that damage. If you already hold a weapon nothing changes, so your own sword or axe choice is kept. Shield Break and Mace Swap take the click first when they act. Settings (right-click): **Trigger** (Click / Any attack), **Chance**, **Swap delay** and **Hit delay**. With either delay on, the click is taken: the switch and the hit follow after their delays.

### Reach Swap

When you click a player or mob that is out of your weapon's reach but within a spear's (2 to 4.5 blocks along your view, not through blocks), the switch to a spear from your hotbar and its jab go out together, in one step, so the jab always registers. A spear without Lunge is preferred, so the jab does not also push you. Clicks on someone in your own reach are normal hits.

Why together: switching items resets your attack charge on the server the next time it updates you (once per game tick), and a spear only jabs at full charge, so a spear switched to ahead of time cannot hit for its whole charge time (about 0.65 to 1.15 s). Sent together, the server handles the jab before that update, so it is charged by, and deals the damage of, the weapon you held (a sword gives more than a mace), at the spear's reach, and it pierces everyone along the line. Everything else comes from the spear: its own enchantments, and no mace smash, Density or Breach. This is attribute swapping (MC-28289); Mojang is removing it in 26.2.

**Mode**: **Jab** (default) switches straight back to your weapon, so the next jab is charged by your weapon again. **Switch only** keeps the spear in your hand after the jab while your crosshair stays on someone out of your weapon's reach but in the spear's, and switches back after **Switch back** (0-500 ms) once you look away or they come into your weapon's reach. Clicks while the spear is still out are plain spear jabs, charged by the spear. Nothing is ever hit without your click. Each jab is logged with the health the target lost, and the status line shows it in hearts. Friends from the Radar are never jabbed.

### Web Escape (UHC)

When you are stuck in a cobweb, or on fire (**On fire too**, on), it places water from your hotbar and scoops it back up once the web is gone or the fire is out (**Pick up water**, on).

Water put straight into a cobweb destroys it at once; flowing water also destroys webs but spreads only every 5 ticks (0.25 s). A bucket used from inside a web always puts its water next to that web, never into it, so:

- **Web at your feet only:** the water goes in the block your head is in and flows down: 0.25 s.
- **Web at your head:** the water goes above your head and flows down through it (0.25 s), then through a web at your feet (another 0.25 s).
- **Webs at head and feet with two water buckets in your hotbar:** one goes straight into the feet web (aimed up from inside the head web, which puts it in the block below) and clears it at once, and the other goes above your head, so both are gone after 0.25 s.

If webs still touch you once the water should have reached them (a web trap), it scoops and goes again, up to three times. On fire, the water goes at your feet. Where the water will land is worked out with the same ray the server uses, and nothing is placed if there is no room. Your view really turns to where the water goes (and to each source it scoops), then back to where you were looking 0.1 s after the scoop; move the mouse meanwhile and the view stays yours. It skips the Nether, where water boils away. Each escape is logged with how long it took.

### Web Trap (UHC)

On its binding (none by default: right-click it and set **Bind**), webs the player under your crosshair. The cobweb goes in the block they are standing in, or, if they are in the air (jumping at you for a crit), in the block they will land in, up to 4 blocks below them; if their feet are already in a web it goes at their head. Where they will be is led by how they are moving: players are drawn behind where the server has them (your ping plus the game's smoothing, about 0.1 s), and a jump is followed to where it comes down, so the web lands where they will be rather than where you see them. It swaps to the cobweb, places it and swaps back in one go. Your view really snaps onto the top of the block it clicks, the web goes where the crosshair lands, and 0.15 s later the view snaps back to the target (move the mouse and it stays yours). A spot whose top you cannot see from where you stand is skipped.

With **Drain with lava** (on), if they have put water down within a block of them (to clear your web, or to land in), the same press pours lava from your hotbar into that water source instead. Lava replaces the source, and if flowing water still touches it, it turns to obsidian at once, so their water is gone either way. Your view snaps to the water and back the same way. With no lava in your hotbar, a web placed into their water also replaces it.

Your press is always the trigger, and only the player you are looking at (up to a block past your reach, not through walls) is a target; friends from the Radar are never webbed. A web is never put in your own block (Web Escape would clear it) or on a block that does something when used (chests, doors, buttons, crafting tables...). The status line says why when nothing is placed, and each web and drain is logged.

### MLG Water (UHC)

When a fall would hurt you at least **Min damage** (2, 4 or 6 hearts, or Fatal only), it places water on the ground below as soon as the ground is within the bucket's reach (4.5 blocks from your eyes, about 3 from your feet), so you land in it and take no fall damage, then scoops it back up a game tick after you land (**Pick up water**, on), once the server has handled your landing. The landing is predicted exactly like the Landing readout. Nothing is placed over lava, in the Nether, or when the landing is already safe. Your view really turns down to place and to scoop, then back to where you were looking 0.1 s after the scoop.

### Clutch (Bedwars)

When you are knocked off an edge over a drop that would hurt (more than 3 blocks, or the void), it builds you a block to land on, one block per game tick (as fast as anyone can click), so you land on it instead of falling.

First choice is a straight bridge out from the block you fell off, at its level, toward where you will come down through that level: along the longer way first, then the shorter, so there is at most one turn. Your view steps along that one line instead of jumping from side to side. That works whenever the bridge can reach you before you drop below that level, which is the usual knockback case. Otherwise it finds the nearest solid block in reach and places its way to the highest block under your fall it can still finish, sticking to that block unless your path stops crossing it.

It follows your fall with the game's own gravity and air drag (knockback carries you sideways and fades). Nothing more is placed once your path lands on something within 3 blocks of fall, which does no damage, and at most 6 blocks go down per clutch. It only acts while you are falling: it starts once you are coming down and stops the moment you are not (landed, or knocked up again), handing your view back.

Blocks come from your hotbar (the one in your hand first): full solid blocks only, nothing that falls (sand, gravel) or does something when clicked. Your view really snaps onto each face it clicks (only faces that point toward you), the block goes where the crosshair lands, and when it stops the view snaps back to where you were looking (move the mouse and it stays yours). **When**: **After a hit** (default) starts only if you were hit in the last 1.5 s, that is, pushed off; **Any fall** also catches walking off an edge. Water below counts as safe. If it cannot reach anything to build from, MLG Water still catches the landing. Each clutch is logged with how many blocks it placed.

### Edge Sneak (Bedwars)

While you walk on the ground holding blocks (**Only holding blocks**, on), it holds sneak as soon as the movement keys you hold would take you off the edge of what you stand on, and lets go once they would not. The game's own sneak then stops you at the edge, so you can bridge (walking backwards and placing blocks yourself) without falling off. The direction comes from the keys you hold rather than from your motion, so the first step toward an edge is already covered; with no movement key held it keeps what it had, so standing still at an edge stays crouched. Your own sneak key always wins, and it does nothing with Toggle Sneak on. Looking and placing stay yours.

### W-Tap (UHC)

After a hit you land (and after **Reaction**), lets go of forward for **Length** (0–500 ms, 100 by default; a game tick is 50 ms), or with **Mode** S-tap also taps back, then puts the keys back to how you are holding them. A sprint hit stops your sprint and slows you to 60%; letting go of forward and pressing it again restarts the sprint (with sprint held, or as a double-tap), so your next hit is a sprint hit with its extra knockback, and the step back keeps your distance. **Only sprint hits** (on) skips hits that were not sprint hits. It starts the game tick after your click, once the hit has gone out, and counts hits made through Shield Break and Mace Swap. With **Trigger** on Any attack, it counts every attack your client sends instead of your clicks. **Chance** sets how many hits get a tap.

### Jump Reset (UHC)

When you get hit while on the ground, it jumps on that game tick. Jumping as the knockback arrives, while sprinting toward the attacker, adds the sprint-jump push toward them and cuts part of the knockback. Jump is held for one game tick, then put back to how you are holding it. **Only sprinting** (off) limits it to hits taken while sprinting.

### Auto Gapple (UHC)

When your health drops to **Health** or below (4, 6, 8 or 10 hearts; 6 by default) and no Regeneration is running (so a gapple is not wasted on top of another), it switches to a golden apple in your hotbar (an enchanted one only if there is no plain one), holds use until it is eaten, then switches back. Picking a slot yourself, opening a screen, or 2.5 s passing stops it and gives use back to how you hold it.

### Chest Stealer (Bedwars)

When you open a chest, barrel, ender chest or shulker box, it shift-clicks the items into your inventory one at a time, **Delay** apart (50, 100, 150 or 250 ms), starting a moment after it opens so the contents have arrived. Items that do not fit are skipped. With **Close when done** (on), the screen closes once nothing more can be taken; an empty one is left open. It goes through each container you open once.

### Auto Armor (Bedwars)

Puts on better armor than you are wearing, one piece every 0.15 s. Better means more armor points plus toughness, with durability left breaking ties. With your inventory open it takes pieces from anywhere in it, using the clicks you would make: a shift-click onto an empty slot, or pick up, swap with the worn piece and put that one back. With **From hotbar** (on), a better piece in your hotbar is put on with no screen open, the way right-clicking it does (the worn piece goes to that hotbar slot). A worn elytra, or a piece with Curse of Binding, is never replaced.

### Auto Tool (Util)

While you mine a block, it switches to the hotbar tool that breaks it fastest (one that gets the block's drops comes first), and with **Swap back** (on) goes back to what you held 0.3 s after you stop, unless you picked another slot yourself. It only acts once the game is actually mining, so clicks on players and quick taps are left alone. A tool with 5 uses or fewer left is never picked.

### Auto Sprint (Util)

Holds sprint while you walk forward, the way holding the sprint key does: not while sneaking, using an item, or too hungry to sprint. It does nothing with the game's Toggle Sprint on.

### Fullbright (Util)

Turns brightness far past the slider's maximum, so caves and nights are lit. Your own brightness comes back when you turn it off or Unload. If you change video settings while it is on, the game may save the high brightness, which it resets to the default the next time it starts.

### Zoom (Util)

While its key is held (**Hold**, none by default), it narrows your field of view by **Zoom** (2x, 4x or 8x) and slows the mouse by the same amount; both come back when you let go.

### Auto Respawn (Util)

On the death screen it respawns you at once. With **Death coords** (on), where you died is shown in your chat (only you see it), and it is always written to the log.

### Speed HUD

A ring above the hotbar that turns with your view (up = the way you face):

- **Orange arrow**: your horizontal velocity, the way you are actually moving. It reaches the ring at 10 blocks/s.
- **Red arrow**: the push the next Lunge would give. Full length is the spear's full push, so a shorter arrow means a weaker lunge; it is dim while the spear is still charging and gone when Lunge cannot push (riding, gliding, in water, or under 7 food outside creative). Lunge adds 0.458 blocks/tick per level, i.e. 9.16 blocks/s per level, scaled by cos(look angle) unless Flat lunge is on.

A red arc around the ring fills clockwise as the spear charges; spears need a full charge to jab, so a full arc means a dash can go (it is dim until then).

Above the ring: your speed (orange) and the predicted push (red), both in blocks per second. Your up/down look angle (0° = level, positive = looking up) sits just right of the hotbar, because the hotbar is on the bottom edge of the screen. Settings:

- **Position**: Low (just above the hearts and food) or High (also clears action bar messages).
- **Toggle key**: a key or mouse button that turns the Speed HUD on and off without the menu (Esc while binding clears it). Radar and Landing have the same setting.
- **Warnings**: lines above the numbers. Each lunge burns one point of saturation or food per Lunge level, and Lunge stops pushing below 7 food (outside creative): "Last lunge: eat" (yellow) when the next lunge would drop you under 7, "Too hungry to lunge" (red) once you are under it. "Spear: N uses" (yellow) from 15 durability left, "Spear breaking: N" (red) from 3.

### Radar

A faint ring around the crosshair. A triangle outside it points at the nearest player in range (invisible players and spectators are ignored), with the distance below. It flashes when that player is more than 60° off where you face or is above you and falling (a mace hit), and turns red when they are within 4 blocks or dropping on you; dropping adds an ↑ to the distance. Settings:

- **Range**: 6, 10 or 16 blocks.
- **Team**: Ignore (default) skips players on your scoreboard team; Show includes them.
- **Friend / Unfriend NAME**: toggles the player you are looking at, or else the nearest player within 64 blocks, on your friends list. Friends never set off the radar. With nobody around, the row shows how many friends you have.

### Landing

While you fall, a line under the crosshair shows the distance to the ground below you and what the landing will do: "safe" (green), "water, safe", "slow falling", "LAVA", or the fall damage in hearts (yellow), marked "FATAL" (red) when it is at least your health plus absorption. It uses the game's formula, floor(fall distance − safe fall distance) × fall damage multiplier, including the distance you have already fallen; Feather Falling, Protection and soft blocks such as hay or slime lower the real damage further. Nothing is shown on the ground, while flying, gliding, riding or in water.

### Edit HUD

In the HUD tab. Opens a screen showing the Speed HUD, the look angle and the landing line as boxes at their current spots. Drag a box to move that piece; **Reset** puts everything back and **Done** (or Escape) saves and returns to the menu. Positions are kept as offsets from the default spots, so the Speed HUD's Low/High setting still applies underneath, and they are shared by every server.

### Unload

Switches off only the injected version whose menu you clicked: its key and mouse listeners, its background loop and its HUD, and it turns Spear Dash off. Other injected versions keep running. Java cannot remove loaded classes from a running game, so this is a full switch-off rather than a delete; **Open GUI** in the injector turns the same version back on. If a newer version was injected on top, the older one's listeners stay in the chain as inactive pass-throughs so the newer one keeps working. Versions before v18 have no Unload, so restart Minecraft once to get rid of them.

The HUD and radar draw through Fabric API's HUD registry, so Fabric API must be installed; the build reads its rendering module from the `fabric-api-*+26.1.2.jar` in `.minecraft/mods`.
