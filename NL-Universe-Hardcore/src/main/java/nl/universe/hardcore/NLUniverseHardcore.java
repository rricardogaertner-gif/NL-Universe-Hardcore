package nl.universe.hardcore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class NLUniverseHardcore extends JavaPlugin implements Listener {
    private final Map<UUID, Long> blockedUntil = new HashMap<>();
    private File dataFile;

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "cooldowns.yml");
        loadCooldowns();
        Bukkit.getPluginManager().registerEvents(this, this);
        if (Bukkit.getWorld(smp()) == null || Bukkit.getWorld(hub()) == null) {
            getLogger().severe("World '" + smp() + "' or '" + hub() + "' not found! Check config.yml.");
        }
    }
    @Override public void onDisable() { saveCooldowns(); }
    private String smp() { return getConfig().getString("smp-world", "smp"); }
    private String hub() { return getConfig().getString("hub-world", "hub"); }
    private boolean isSmp(World world) { return world != null && world.getName().equalsIgnoreCase(smp()); }
    private boolean blocked(UUID id) { return blockedUntil.getOrDefault(id, 0L) > System.currentTimeMillis(); }
    private long remaining(UUID id) { return Math.max(0, blockedUntil.getOrDefault(id, 0L) - System.currentTimeMillis()); }
    private String remainingText(UUID id) {
        long seconds = (remaining(id) + 999) / 1000;
        return (seconds / 3600) + "u " + ((seconds % 3600) / 60) + "m " + (seconds % 60) + "s";
    }
    private void inform(Player player) { player.sendMessage(ChatColor.RED + "Je bent overleden in SMP. Je kunt over " + remainingText(player.getUniqueId()) + " terug naar SMP."); }
    private Location hubSpawn() {
        World world = Bukkit.getWorld(hub());
        return world == null ? null : world.getSpawnLocation();
    }
    private void sendHub(Player player) {
        Location spawn = hubSpawn();
        if (spawn != null) player.teleport(spawn);
        else getLogger().severe("Cannot send " + player.getName() + " to hub: world not found.");
    }
    @EventHandler(priority = EventPriority.MONITOR) public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!isSmp(player.getWorld())) return;
        long hours = Math.max(1L, getConfig().getLong("cooldown-hours", 24));
        blockedUntil.put(player.getUniqueId(), System.currentTimeMillis() + TimeUnit.HOURS.toMillis(hours));
        saveCooldowns();
        inform(player);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onRespawn(PlayerRespawnEvent event) {
        if (!blocked(event.getPlayer().getUniqueId())) return;
        if (isSmp(event.getPlayer().getWorld()) || isSmp(event.getRespawnLocation().getWorld())) {
            Location spawn = hubSpawn();
            if (spawn != null) event.setRespawnLocation(spawn);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || !isSmp(event.getTo().getWorld()) || !blocked(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        inform(event.getPlayer());
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onPortal(PlayerPortalEvent event) {
        if (event.getTo() != null && isSmp(event.getTo().getWorld()) && blocked(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            inform(event.getPlayer());
        }
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (blocked(player.getUniqueId()) && isSmp(player.getWorld())) {
            Bukkit.getScheduler().runTask(this, () -> { if (player.isOnline()) { sendHub(player); inform(player); } });
        }
    }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (blocked(player.getUniqueId()) && isSmp(player.getWorld())) {
            Bukkit.getScheduler().runTask(this, () -> { if (player.isOnline() && isSmp(player.getWorld())) { sendHub(player); inform(player); } });
        }
    }
    private void loadCooldowns() {
        blockedUntil.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        if (!yaml.isConfigurationSection("players")) return;
        for (String key : yaml.getConfigurationSection("players").getKeys(false)) {
            try { blockedUntil.put(UUID.fromString(key), yaml.getLong("players." + key)); }
            catch (IllegalArgumentException ignored) { getLogger().warning("Invalid UUID in cooldowns.yml: " + key); }
        }
    }
    private void saveCooldowns() {
        YamlConfiguration yaml = new YamlConfiguration();
        long now = System.currentTimeMillis();
        blockedUntil.entrySet().removeIf(e -> e.getValue() <= now);
        for (Map.Entry<UUID, Long> entry : blockedUntil.entrySet()) yaml.set("players." + entry.getKey(), entry.getValue());
        try { yaml.save(dataFile); } catch (IOException e) { getLogger().severe("Could not save cooldowns.yml: " + e.getMessage()); }
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "NL-Universe Hardcore: /nlhardcore status [speler]");
            if (sender.hasPermission("nluniversehardcore.admin")) sender.sendMessage("Admin: /nlhardcore reset <speler> | set <speler> <uren> | reload");
            return true;
        }
        if (args[0].equalsIgnoreCase("status") && args.length <= 2) {
            UUID id;
            if (args.length == 1 && sender instanceof Player player) id = player.getUniqueId();
            else if (args.length == 2 && sender.hasPermission("nluniversehardcore.admin")) id = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
            else { sender.sendMessage("Gebruik: /nlhardcore status [speler]"); return true; }
            sender.sendMessage(blocked(id) ? "SMP cooldown: " + remainingText(id) : "Geen actieve SMP cooldown.");
            return true;
        }
        if (!sender.hasPermission("nluniversehardcore.admin")) { sender.sendMessage(ChatColor.RED + "Geen toestemming."); return true; }
        if (args[0].equalsIgnoreCase("reload") && args.length == 1) {
            reloadConfig(); sender.sendMessage("NL-Universe Hardcore configuratie herladen."); return true;
        }
        if (args[0].equalsIgnoreCase("reset") && args.length == 2) {
            blockedUntil.remove(Bukkit.getOfflinePlayer(args[1]).getUniqueId()); saveCooldowns();
            sender.sendMessage("Cooldown verwijderd voor " + args[1]); return true;
        }
        if (args[0].equalsIgnoreCase("set") && args.length == 3) {
            double hours;
            try { hours = Double.parseDouble(args[2]); } catch (NumberFormatException e) { sender.sendMessage("Voer geldige uren in."); return true; }
            if (!Double.isFinite(hours) || hours < 0 || hours > 8760) { sender.sendMessage("Uren moeten tussen 0 en 8760 liggen."); return true; }
            UUID id = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
            if (hours == 0) blockedUntil.remove(id);
            else blockedUntil.put(id, System.currentTimeMillis() + (long) (hours * 3600000));
            saveCooldowns(); sender.sendMessage("Cooldown voor " + args[1] + " ingesteld op " + hours + " uur."); return true;
        }
        sender.sendMessage("Gebruik: /nlhardcore status [speler] | reset <speler> | set <speler> <uren> | reload");
        return true;
    }
}
