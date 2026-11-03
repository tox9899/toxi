package com.toxi.toxiauth;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.mindrot.jbcrypt.BCrypt;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TOXIAuth extends JavaPlugin implements Listener {
    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private File accountsFile;
    private YamlConfiguration accounts;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        accountsFile = new File(getDataFolder(), "accounts.yml");
        if (!accountsFile.exists()) {
            try {
                getDataFolder().mkdirs();
                accountsFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create accounts.yml: " + e.getMessage());
            }
        }
        accounts = YamlConfiguration.loadConfiguration(accountsFile);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("TOXIAuth enabled. Geyser players can use /register and /login through chat.");
    }

    @Override
    public void onDisable() {
        authenticated.clear();
        busy.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        authenticated.remove(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline() || player.hasPermission("toxiauth.admin")) return;
            if (hasAccount(player)) send(player, "login");
            else send(player, "register");
            send(player, "password-commands-note");
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        authenticated.remove(event.getPlayer().getUniqueId());
        busy.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        String cmd = command.getName().toLowerCase();
        UUID id = player.getUniqueId();

        if (cmd.equals("register")) {
            if (args.length != 2) { send(player, "usage-register"); return true; }
            if (hasAccount(player)) { send(player, "already-registered"); return true; }
            if (!validLength(args[0])) { sendLength(player); return true; }
            if (!args[0].equals(args[1])) { send(player, "password-mismatch"); return true; }
            if (!busy.add(id)) { send(player, "busy"); return true; }

            String rawPassword = args[0];
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                String hash = BCrypt.hashpw(rawPassword, BCrypt.gensalt(getConfig().getInt("bcrypt-cost", 12)));
                Bukkit.getScheduler().runTask(this, () -> {
                    busy.remove(id);
                    if (!player.isOnline()) return;
                    if (hasAccount(player)) { send(player, "already-registered"); return; }
                    accounts.set(id.toString(), hash);
                    saveAccounts();
                    authenticated.add(id);
                    send(player, "registered");
                });
            });
            return true;
        }

        if (cmd.equals("login")) {
            if (args.length != 1) { send(player, "usage-login"); return true; }
            if (!hasAccount(player)) { send(player, "not-registered"); return true; }
            if (authenticated.contains(id)) { send(player, "logged-in"); return true; }
            if (!busy.add(id)) { send(player, "busy"); return true; }

            String storedHash = accounts.getString(id.toString());
            String rawPassword = args[0];
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                boolean matches = false;
                try { matches = storedHash != null && BCrypt.checkpw(rawPassword, storedHash); }
                catch (IllegalArgumentException ignored) {}
                boolean result = matches;
                Bukkit.getScheduler().runTask(this, () -> {
                    busy.remove(id);
                    if (!player.isOnline()) return;
                    if (result) { authenticated.add(id); send(player, "logged-in"); }
                    else send(player, "wrong-password");
                });
            });
            return true;
        }

        if (cmd.equals("changepassword")) {
            if (args.length != 2) { send(player, "usage-change"); return true; }
            if (!authenticated.contains(id)) { send(player, "logged-out"); return true; }
            if (!validLength(args[1])) { sendLength(player); return true; }
            if (!busy.add(id)) { send(player, "busy"); return true; }

            String storedHash = accounts.getString(id.toString());
            String oldPassword = args[0];
            String newPassword = args[1];
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                boolean oldMatches = false;
                try { oldMatches = storedHash != null && BCrypt.checkpw(oldPassword, storedHash); }
                catch (IllegalArgumentException ignored) {}
                String newHash = oldMatches
                        ? BCrypt.hashpw(newPassword, BCrypt.gensalt(getConfig().getInt("bcrypt-cost", 12)))
                        : null;
                boolean verified = oldMatches;
                Bukkit.getScheduler().runTask(this, () -> {
                    busy.remove(id);
                    if (!player.isOnline()) return;
                    if (!verified) { send(player, "old-wrong"); return; }
                    accounts.set(id.toString(), newHash);
                    saveAccounts();
                    send(player, "changed");
                });
            });
            return true;
        }
        return false;
    }

    private boolean hasAccount(Player player) {
        return accounts.contains(player.getUniqueId().toString());
    }

    private boolean validLength(String password) {
        int min = getConfig().getInt("min-password-length", 8);
        int max = getConfig().getInt("max-password-length", 64);
        return password.length() >= min && password.length() <= max;
    }

    private void sendLength(Player player) {
        String message = getMessage("password-length")
                .replace("%min%", String.valueOf(getConfig().getInt("min-password-length", 8)))
                .replace("%max%", String.valueOf(getConfig().getInt("max-password-length", 64)));
        player.sendMessage(message);
    }

    private void send(Player player, String key) {
        player.sendMessage(getMessage(key));
    }

    private String getMessage(String key) {
        String prefix = getConfig().getString("messages.prefix", "&8[&bTOXIAuth&8] ");
        String body = getConfig().getString("messages." + key, "&cMissing message: " + key);
        return ChatColor.translateAlternateColorCodes('&', prefix + body);
    }

    private void saveAccounts() {
        try { accounts.save(accountsFile); }
        catch (IOException e) { getLogger().severe("Could not save accounts.yml: " + e.getMessage()); }
    }

    private boolean isAuthenticated(Player player) {
        return authenticated.contains(player.getUniqueId()) || player.hasPermission("toxiauth.admin");
    }

    private boolean isAllowedCommand(String message) {
        String lower = message.toLowerCase();
        return lower.startsWith("/register ") || lower.equals("/register")
                || lower.startsWith("/reg ") || lower.equals("/reg")
                || lower.startsWith("/login ") || lower.equals("/login")
                || lower.startsWith("/l ") || lower.equals("/l")
                || lower.startsWith("/changepassword ") || lower.startsWith("/changepass ");
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (isAuthenticated(event.getPlayer()) || event.getTo() == null) return;
        Location from = event.getFrom(), to = event.getTo();
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) event.setTo(from);
    }

    @EventHandler public void onInteract(PlayerInteractEvent e) { if (!isAuthenticated(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onBreak(BlockBreakEvent e) { if (!isAuthenticated(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onPlace(BlockPlaceEvent e) { if (!isAuthenticated(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onInventory(InventoryClickEvent e) { if (e.getWhoClicked() instanceof Player p && !isAuthenticated(p)) e.setCancelled(true); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { if (!isAuthenticated(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onPickup(EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p && !isAuthenticated(p)) e.setCancelled(true); }
    @EventHandler public void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p && !isAuthenticated(p)) e.setCancelled(true); }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!isAuthenticated(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isAuthenticated(player) && !isAllowedCommand(event.getMessage())) {
            event.setCancelled(true);
            send(player, "logged-out");
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && !isAuthenticated(p)) event.setCancelled(true);
    }
}
