package com.maxhearts;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;

public final class MaxHeartsPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private double defaultHearts;
    private double minHearts;
    private double maxHearts;
    private DataStore store;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadLocal();

        store = new DataStore(getDataFolder(), "data.yml");
        store.load();

        migrateLegacyOverrides();

        getServer().getPluginManager().registerEvents(this, this);

        for (Player p : Bukkit.getOnlinePlayers()) {
            handlePendingForJoin(p);
            applyHeartsNextTick(p);
        }

        PluginCommand mh = getCommand("maxhearts");
        if (mh != null) {
            mh.setExecutor(this);
            mh.setTabCompleter(new MaxHeartsTabCompleter());
        }
        PluginCommand sh = getCommand("sethearts");
        if (sh != null) sh.setExecutor(this);
    }

    private void loadLocal() {
        FileConfiguration cfg = getConfig();
        defaultHearts = cfg.getDouble("defaultHearts", 5.0);
        minHearts = Math.max(1.0, cfg.getDouble("minHearts", 1.0));
        maxHearts = Math.max(minHearts, cfg.getDouble("maxHearts", 100.0));
        defaultHearts = clampHearts(defaultHearts);
    }

    private double clampHearts(double hearts) {
        if (hearts < minHearts) return minHearts;
        if (hearts > maxHearts) return maxHearts;
        return hearts;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent e) {
        handlePendingForJoin(e.getPlayer());
        applyHeartsNextTick(e.getPlayer());
    }

    private void handlePendingForJoin(Player p) {
        String name = p.getName();
        if (store.hasPendingForName(name)) {
            double pending = clampHearts(store.getPendingForName(name));
            store.setHearts(p.getUniqueId(), pending);
            store.setLastKnownName(p.getUniqueId(), name);
            store.removePendingForName(name);
            store.save();
        } else {
            store.setLastKnownName(p.getUniqueId(), name);
            store.save();
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTask(this, () ->
            applyHearts(e.getPlayer(), resolveHearts(e.getPlayer().getUniqueId()), false));
    }

    private void applyHeartsNextTick(Player p) {
        Bukkit.getScheduler().runTask(this, () ->
            applyHearts(p, resolveHearts(p.getUniqueId()), false));
    }

    private double resolveHearts(UUID uuid) {
        if (store.has(uuid)) {
            return clampHearts(store.getHearts(uuid, defaultHearts));
        }
        return defaultHearts;
    }

    /**
     * Apply hearts and optionally heal to full when hearts increased.
     * If healToMax is false, only clamp down if current > newMax.
     */
    private void applyHearts(Player p, double hearts, boolean healToMax) {
        double hp = hearts * 2.0;
        AttributeInstance attr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr != null) attr.setBaseValue(hp);

        if (healToMax) {
            p.setHealth(hp);
        } else if (p.getHealth() > hp) {
            p.setHealth(hp);
        }

        p.setHealthScaled(true);
        p.setHealthScale(hp);
    }

    private void migrateLegacyOverrides() {
        FileConfiguration cfg = getConfig();
        if (!cfg.isConfigurationSection("overrides")) return;

        ConfigurationSection sec = cfg.getConfigurationSection("overrides");
        if (sec == null) return;

        for (String name : sec.getKeys(false)) {
            double hearts = clampHearts(sec.getDouble(name, defaultHearts));
            UUID uuid = null;

            Player online = Bukkit.getPlayerExact(name);
            if (online != null) {
                uuid = online.getUniqueId();
            } else {
                OfflinePlayer off = Bukkit.getOfflinePlayer(name);
                if (off != null && off.hasPlayedBefore() && off.getUniqueId() != null) {
                    uuid = off.getUniqueId();
                }
            }

            if (uuid != null) {
                store.setHearts(uuid, hearts);
                store.setLastKnownName(uuid, name);
            } else {
                store.setPendingForName(name, hearts);
            }
        }

        store.save();
        cfg.set("overrides", null);
        saveConfig();
        getLogger().info("[MaxHearts] Migrated legacy overrides to data.yml");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);

        if (name.equals("sethearts")) {
            if (!sender.hasPermission("maxhearts.set")) {
                sender.sendMessage("§cYou don't have permission.");
                return true;
            }
            if (args.length != 2) {
                sender.sendMessage("§cUsage: /sethearts <player|uuid> <hearts>");
                return true;
            }
            Target t = resolveTarget(args[0]);
            if (t == null) {
                sender.sendMessage("§cUnknown player or UUID: " + args[0]);
                return true;
            }
            Double hearts = parseHearts(args[1], sender);
            if (hearts == null) return true;
            return handleSet(sender, t, hearts);
        }

        if (!name.equals("maxhearts")) return false;
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set": {
                if (!sender.hasPermission("maxhearts.set")) {
                    sender.sendMessage("§cNo permission: maxhearts.set");
                    return true;
                }
                if (args.length != 3) {
                    sender.sendMessage("§cUsage: /maxhearts set <player|uuid> <hearts>");
                    return true;
                }
                Target t = resolveTarget(args[1]);
                if (t == null) { sender.sendMessage("§cUnknown player or UUID: " + args[1]); return true; }
                Double hearts = parseHearts(args[2], sender);
                if (hearts == null) return true;
                return handleSet(sender, t, hearts);
            }
            case "give": {
                if (!sender.hasPermission("maxhearts.give")) {
                    sender.sendMessage("§cNo permission: maxhearts.give");
                    return true;
                }
                if (args.length != 3) {
                    sender.sendMessage("§cUsage: /maxhearts give <player|uuid> <amount>");
                    return true;
                }
                Target t = resolveTarget(args[1]);
                if (t == null) { sender.sendMessage("§cUnknown player or UUID: " + args[1]); return true; }
                Double delta = parseHearts(args[2], sender);
                if (delta == null) return true;
                return handleDelta(sender, t, +delta);
            }
            case "take": {
                if (!sender.hasPermission("maxhearts.take")) {
                    sender.sendMessage("§cNo permission: maxhearts.take");
                    return true;
                }
                if (args.length != 3) {
                    sender.sendMessage("§cUsage: /maxhearts take <player|uuid> <amount>");
                    return true;
                }
                Target t = resolveTarget(args[1]);
                if (t == null) { sender.sendMessage("§cUnknown player or UUID: " + args[1]); return true; }
                Double delta = parseHearts(args[2], sender);
                if (delta == null) return true;
                return handleDelta(sender, t, -delta);
            }
            case "donate": {
                if (!sender.hasPermission("maxhearts.donate")) {
                    sender.sendMessage("§cNo permission: maxhearts.donate");
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cOnly players can donate hearts.");
                    return true;
                }
                if (args.length != 3) {
                    sender.sendMessage("§cUsage: /maxhearts donate <player|uuid> <amount>");
                    return true;
                }
                Player donor = (Player) sender;
                Target to = resolveTarget(args[1]);
                if (to == null) { sender.sendMessage("§cUnknown player or UUID: " + args[1]); return true; }
                Double amount = parseHearts(args[2], sender);
                if (amount == null) return true;
                return handleTransfer(sender, new Target(donor), to, amount);
            }
            case "transfer": {
                if (!sender.hasPermission("maxhearts.transfer")) {
                    sender.sendMessage("§cNo permission: maxhearts.transfer");
                    return true;
                }
                if (args.length != 4) {
                    sender.sendMessage("§cUsage: /maxhearts transfer <fromPlayer|uuid> <toPlayer|uuid> <amount>");
                    return true;
                }
                Target from = resolveTarget(args[1]);
                Target to = resolveTarget(args[2]);
                if (from == null || to == null) {
                    sender.sendMessage("§cBoth players must be known (online or UUID/pending name). Unknown: " +
                            (from == null ? args[1] : args[2]));
                    return true;
                }
                Double amount = parseHearts(args[3], sender);
                if (amount == null) return true;
                return handleTransfer(sender, from, to, amount);
            }
            case "get": {
                if (args.length != 2) {
                    sender.sendMessage("§cUsage: /maxhearts get <player|uuid>");
                    return true;
                }
                Target t = resolveTarget(args[1]);
                if (t == null) { sender.sendMessage("§cUnknown player or UUID: " + args[1]); return true; }
                double h = getCurrentHearts(t);
                sender.sendMessage("§e" + prettyTargetName(t) + " has §6" + h + "§e hearts (max).");
                return true;
            }
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage("§6MaxHearts Commands:");
        s.sendMessage("§e/maxhearts set <player|uuid> <hearts>§7 — set absolute value");
        s.sendMessage("§e/maxhearts give <player|uuid> <amount>§7 — add hearts");
        s.sendMessage("§e/maxhearts take <player|uuid> <amount>§7 — remove hearts (min " + minHearts + ")");
        s.sendMessage("§e/maxhearts donate <player|uuid> <amount>§7 — give yours to them");
        s.sendMessage("§e/maxhearts transfer <from> <to> <amount>§7 — move between players");
        s.sendMessage("§e/maxhearts get <player|uuid>§7 — view stored max hearts");
        s.sendMessage("§7(Also: /sethearts <player|uuid> <hearts>)");
    }

    private Double parseHearts(String raw, CommandSender sender) {
        try {
            double val = Double.parseDouble(raw);
            if (val <= 0) {
                sender.sendMessage("§cAmount must be > 0.");
                return null;
            }
            return val;
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cInvalid number: " + raw);
            return null;
        }
    }

    // ===== Target resolution =====

    private static record Target(UUID uuid, Player online, String pendingName) {
        Target(Player online) { this(online.getUniqueId(), online, null); }
        static Target forUUID(UUID u) { return new Target(u, null, null); }
        static Target forPendingName(String name) { return new Target(null, null, name); }
        boolean isUUID() { return uuid != null; }
        boolean isOnline() { return online != null; }
        boolean isPendingName() { return pendingName != null; }
    }

    private Target resolveTarget(String token) {
        Player online = Bukkit.getPlayerExact(token);
        if (online != null) return new Target(online);

        try {
            UUID u = UUID.fromString(token);
            return Target.forUUID(u);
        } catch (IllegalArgumentException ignored) {}

        OfflinePlayer off = Bukkit.getOfflinePlayer(token);
        if (off != null && off.hasPlayedBefore() && off.getUniqueId() != null) {
            return Target.forUUID(off.getUniqueId());
        }

        return Target.forPendingName(token);
    }

    // ===== Name helpers =====

    private String prettyTargetName(Target t) {
        if (t.isOnline()) return t.online().getName();
        if (t.isPendingName()) return t.pendingName();

        // UUID case: try last-known name, then OfflinePlayer name, then short UUID
        String n = store.getLastKnownName(t.uuid());
        if (n != null && !n.isEmpty()) return n;

        OfflinePlayer off = Bukkit.getOfflinePlayer(t.uuid());
        if (off != null && off.getName() != null) return off.getName();

        return shortUuid(t.uuid());
    }

    private static String shortUuid(UUID u) {
        String s = u.toString();
        int dash = s.indexOf('-');
        return dash > 0 ? s.substring(0, dash) : s;
    }

    private boolean isSameResolvedPlayer(Target a, Target b) {
        if (a.isOnline() && b.isOnline()) return a.online().getUniqueId().equals(b.online().getUniqueId());
        if (a.isUUID() && b.isUUID()) return a.uuid().equals(b.uuid());
        // If one or both are pending names, we can't reliably tell; return false
        return false;
    }

    // ===== Core ops (set/delta/transfer) =====

    private boolean handleSet(CommandSender sender, Target target, double hearts) {
        hearts = clampHearts(hearts);

        if (target.isOnline()) {
            Player p = target.online();
            double before = resolveHearts(p.getUniqueId());
            store.setHearts(p.getUniqueId(), hearts);
            store.setLastKnownName(p.getUniqueId(), p.getName());
            store.save();
            boolean increased = hearts > before;
            applyHearts(p, hearts, increased); // heal if increased
            sender.sendMessage("§aSet " + p.getName() + " to " + hearts + " hearts.");
            if (!(sender instanceof ConsoleCommandSender)) {
                p.sendMessage("§eYour max hearts are now §6" + hearts + "§e.");
            }
            return true;
        }

        if (target.isUUID()) {
            UUID u = target.uuid();
            double before = resolveHearts(u);
            store.setHearts(u, hearts);
            store.save();
            String who = prettyTargetName(target);
            sender.sendMessage("§aSaved: " + who + " → " + hearts + " hearts.");
            Player online = Bukkit.getPlayer(u);
            if (online != null) {
                boolean increased = hearts > before;
                applyHearts(online, hearts, increased);
            }
            return true;
        }

        // pending name
        store.setPendingForName(target.pendingName(), hearts);
        store.save();
        sender.sendMessage("§aSaved (pending): " + target.pendingName() + " → " + hearts + " hearts (will apply on first join).");
        return true;
    }

    private boolean handleDelta(CommandSender sender, Target target, double delta) {
        if (target.isPendingName()) {
            double current = store.hasPendingForName(target.pendingName())
                    ? store.getPendingForName(target.pendingName())
                    : defaultHearts;
            double updated = clampHearts(current + delta);
            store.setPendingForName(target.pendingName(), updated);
            store.save();
            sender.sendMessage("§a" + target.pendingName() + " now has §6" + updated + "§a hearts (pending).");
            return true;
        }

        UUID u = target.isOnline() ? target.online().getUniqueId() : target.uuid();
        double current = store.getHearts(u, defaultHearts);
        double updated = clampHearts(current + delta);
        if (updated < minHearts) updated = minHearts;

        store.setHearts(u, updated);
        store.save();

        if (target.isOnline()) {
            Player p = target.online();
            boolean increased = updated > current;
            applyHearts(p, updated, increased); // heal if increased
            p.sendMessage((delta >= 0 ? "§a+" : "§c") + Math.abs(delta) + " §ehearts → §6" + updated);
            sender.sendMessage("§a" + p.getName() + " now has §6" + updated + "§a hearts.");
        } else {
            String who = prettyTargetName(target);
            sender.sendMessage("§a" + who + " now has §6" + updated + "§a hearts.");
        }
        return true;
    }

    private boolean handleTransfer(CommandSender sender, Target from, Target to, double amount) {
        // Prevent self-transfer when resolvable to the same UUID
        if (isSameResolvedPlayer(from, to)) {
            sender.sendMessage("§cCan't transfer to the same player.");
            return true;
        }

        if (from.isPendingName()) {
            sender.sendMessage("§cSource player must have a known UUID (has joined before).");
            return true;
        }

        UUID fromUUID = from.isOnline() ? from.online().getUniqueId() : from.uuid();
        double fromHearts = store.getHearts(fromUUID, defaultHearts);

        if (fromHearts - amount < minHearts) {
            sender.sendMessage("§c" + prettyTargetName(from) + " must keep at least " + minHearts + " heart(s).");
            return true;
        }

        double newFrom = clampHearts(fromHearts - amount);
        double newTo;

        if (to.isPendingName()) {
            double toCurrent = store.hasPendingForName(to.pendingName())
                    ? store.getPendingForName(to.pendingName())
                    : defaultHearts;
            newTo = clampHearts(toCurrent + amount);
            store.setPendingForName(to.pendingName(), newTo);
        } else {
            UUID toUUID = to.isOnline() ? to.online().getUniqueId() : to.uuid();
            double toHearts = store.getHearts(toUUID, defaultHearts);
            newTo = clampHearts(toHearts + amount);
            store.setHearts(toUUID, newTo);
        }

        store.setHearts(fromUUID, newFrom);
        store.save();

        // Apply to online players; heal recipient on increase
        if (from.isOnline()) applyHearts(from.online(), newFrom, false);
        if (to.isOnline()) applyHearts(to.online(), newTo, true);

        String fromName = prettyTargetName(from);
        String toName = prettyTargetName(to);

        sender.sendMessage("§aTransferred §6" + amount + "§a hearts: §e" + fromName + " §7→§e " + toName +
                " §7(§e" + fromName + ": " + newFrom + "§7, §e" + toName + ": " + newTo + "§7)");

        if (from.isOnline())
            from.online().sendMessage("§eYou donated §6" + amount + "§e heart(s) to §6" + toName + "§e. You now have §6" + newFrom + "§e.");
        if (to.isOnline())
            to.online().sendMessage("§eYou received §6" + amount + "§e heart(s) from §6" + fromName + "§e. You now have §6" + newTo + "§e.");
        return true;
    }

    private double getCurrentHearts(Target t) {
        if (t.isPendingName()) {
            return store.hasPendingForName(t.pendingName())
                    ? store.getPendingForName(t.pendingName())
                    : defaultHearts;
        }
        UUID u = t.isOnline() ? t.online().getUniqueId() : t.uuid();
        return store.getHearts(u, defaultHearts);
    }
}
