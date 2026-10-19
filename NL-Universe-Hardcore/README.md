# NL-Universe Hardcore

Paper plugin: only SMP death causes a 24-hour lockout from SMP. Players can continue using HUB, PvP and Vanilla. Cooldowns are stored across restarts.

## Build automatically (no Java installation required on your PC)
1. Create a GitHub repository and upload the **contents** of this folder (including `.github/workflows/build.yml`).
2. Open **Actions** -> **Build plugin** -> **Run workflow** (or push changes to trigger build).
3. Download the **NL-Universe-Hardcore-jar** artifact from the successful workflow, unzip it and locate `NL-Universe-Hardcore-1.0.0.jar`.
4. Stop the Minecraft server. Remove `Hardcore24.jar` to avoid conflicts. Upload the new jar to `plugins/`, start the server.
5. Edit `plugins/NLUniverseHardcore/config.yml` if your world names differ. Restart or run `/nlhardcore reload`.

## Commands
- `/nlhardcore status` — your own cooldown
- `/nlhardcore status <player>` — admin: player's cooldown
- `/nlhardcore reset <player>` — admin: clear cooldown
- `/nlhardcore set <player> <hours>` — admin: assign cooldown (0 to clear)
- `/nlhardcore reload` — admin: reload config

## Notes
- Normal Minecraft death drops remain unchanged. No inventory/world changes are made.
- Respawn goes to the hub **world spawn** (set with `/mv setspawn` while standing in hub, if Multiverse controls world spawn).
- Test with a non-operator player on a staging/backup server first.
- Paper API compile target is 1.21.4 and Java 21; test on your exact Paper build before public rollout.
