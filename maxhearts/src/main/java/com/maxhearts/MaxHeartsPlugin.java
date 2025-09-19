package com.maxhearts;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class MaxHeartsPlugin extends JavaPlugin implements Listener {
    private double defaultHearts;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadLocal();
        getServer().getPluginManager().registerEvents(this, this);

        // Apply to already-online players (e.g., /reload)
        for (Player p : Bukkit.getOnlinePlayers()) {
            applyHeartsNextTick(p);
        }

        // Register commands and tab completion
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
        // Hearts, not raw HP
        defaultHearts = clampHearts(cfg.getDouble("defaultHearts", 10.0));
    }

    private double clampHearts(double hearts) {
        if (hearts < 1.0) return 1.0;
        if (hearts > 100.0) return 100.0; // adjust upper bound if you like
        return hearts;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        applyHeartsNextTick(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTask(this, () -> applyHearts(e.getPlayer(), resolveHearts(e.getPlayer())));
    }

    private void applyHeartsNextTick(Player p) {
        Bukkit.getScheduler().runTask(this, () -> applyHearts(p, resolveHearts(p)));
    }

    private double resolveHearts(Player p) {
        String key = "overrides." + p.getName(); // swap to UUID if preferred
        if (getConfig().isSet(key)) {
            return clampHearts(getConfig().getDouble(key));
        }
        return defaultHearts;
    }

    private void applyHearts(Player p, double hearts) {
        double hp = hearts * 2.0; // 1 heart = 2 HP
        AttributeInstance attr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(hp);
        }
        // Clamp current health to new max
        if (p.getHealth() > hp) {
            p.setHealth(hp);
        }
        // Scale HUD to show exactly 'hearts' hearts
        p.setHealthScaled(true);
        p.setHealthScale(hp);
    }

    private void persistHearts(String playerKey, double hearts) {
        getConfig().set("overrides." + playerKey, clampHearts(hearts));
        saveConfig();
    }

    private double getCurrentHearts(String playerKey) {
        if (getConfig().isSet("overrides." + playerKey)) {
            return clampHearts(getConfig().getDouble("overrides." + playerKey));
        }
        return defaultHearts;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase();

        // Legacy: /sethearts <player> <hearts>
        if (name.equals("sethearts")) {
            if (!sender.hasPermission("maxhearts.set")) {
                sender.sendMessage("§cYou don't have permission.");
                return true;
            }
            if (args.length != 2) {
                sender.sendMessage("§cUsage: /sethearts <player> <hearts>");
                return true;
            }
            String targetName = args[0];
            Double hearts = parseHearts(args[1], sender);
            if (hearts == null) return true;
            return handleSet(sender, targetName, hearts);
        }

        // /maxhearts ...
        if (!name.equals("maxhearts")) return false;
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "set": {
                if (!sender.hasPermission("maxhearts.set")) {
                    sender.sendMessage("§cNo permission: maxhearts.set");
                    return true;
                }
                if (args.length != 3) {
                    sender.sendMessage("§cUsage: /maxhearts set <player> <hearts>");
                    return true;
                }
                String target = args[1];
                Double hearts = parseHearts(args[2], sender);
                if (hearts == null) return true;
                return handleSet(sender, target, hearts);
            }
            case "give": {
                if (!sender.hasPermission("maxhearts.give")) {
                    sender.sendMessage("§cNo permission: maxhearts.give");
                    return true;
                }
                if (args.length != 3) {
                    sender.sendMessage("§cUsage: /maxhearts give <player> <amount>");
                    return true;
                }
                String target = args[1];
                Double delta = parseHearts(args[2], sender);
                if (delta == null) return true;
                return handleDelta(sender, target, +delta);
            }
            case "take": {
                if (!sender.hasPermission("maxhearts.take")) {
                    sender.sendMessage("§cNo permission: maxhearts.take");
                    return true;
                }
                if (args.length != 3) {
                    sender.sendMessage("§cUsage: /maxhearts take <player> <amount>");
                    return true;
                }
                String target = args[1];
                Double delta = parseHearts(args[2], sender);
                if (delta == null) return true;
                return handleDelta(sender, target, -delta);
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
                    sender.sendMessage("§cUsage: /maxhearts donate <player> <amount>");
                    return true;
                }
                Player donor = (Player) sender;
                String target = args[1];
                Double amount = parseHearts(args[2], sender);
                if (amount == null) return true;
                return handleTransfer(sender, donor.getName(), target, amount);
            }
            case "transfer": {
                if (!sender.hasPermission("maxhearts.transfer")) {
                    sender.sendMessage("§cNo permission: maxhearts.transfer");
                    return true;
                }
                if (args.length != 4) {
                    sender.sendMessage("§cUsage: /maxhearts transfer <from> <to> <amount>");
                    return true;
                }
                String from = args[1];
                String to = args[2];
                Double amount = parseHearts(args[3], sender);
                if (amount == null) return true;
                return handleTransfer(sender, from, to, amount);
            }
            case "get": {
                if (args.length != 2) {
                    sender.sendMessage("§cUsage: /maxhearts get <player>");
                    return true;
                }
                String who = args[1];
                double h = getCurrentHearts(who);
                sender.sendMessage("§e" + who + " has §6" + h + "§e hearts (max).");
                return true;
            }
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage("§6MaxHearts Commands:");
        s.sendMessage("§e/maxhearts set <player> <hearts>§7 — set absolute value");
        s.sendMessage("§e/maxhearts give <player> <amount>§7 — add hearts");
        s.sendMessage("§e/maxhearts take <player> <amount>§7 — remove hearts (min 1)");
        s.sendMessage("§e/maxhearts donate <player> <amount>§7 — give yours to them");
        s.sendMessage("§e/maxhearts transfer <from> <to> <amount>§7 — move between players");
        s.sendMessage("§e/maxhearts get <player>§7 — view stored max hearts");
        s.sendMessage("§7(Also: /sethearts <player> <hearts>)");
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

    private boolean handleSet(CommandSender sender, String targetName, double hearts) {
        hearts = clampHearts(hearts);

        Player target = Bukkit.getPlayerExact(targetName);
        if (target != null) {
            applyHearts(target, hearts);
            persistHearts(target.getName(), hearts);
            sender.sendMessage("§aSet " + target.getName() + " to " + hearts + " hearts.");
            if (!(sender instanceof ConsoleCommandSender)) {
                target.sendMessage("§eYour max hearts are now §6" + hearts + "§e.");
            }
            return true;
        }

        // offline: store for next login
        persistHearts(targetName, hearts);
        sender.sendMessage("§aSaved override: " + targetName + " → " + hearts + " hearts (will apply on next join).");
        return true;
    }

    private boolean handleDelta(CommandSender sender, String targetName, double delta) {
        double current = getCurrentHearts(targetName);
        double updated = clampHearts(current + delta);
        if (updated < 1.0) updated = 1.0;

        Player target = Bukkit.getPlayerExact(targetName);
        persistHearts(targetName, updated);
        if (target != null) {
            applyHearts(target, updated);
            target.sendMessage((delta >= 0 ? "§a+" : "§c") + Math.abs(delta) + " §ehearts → §6" + updated);
        }
        sender.sendMessage("§a" + targetName + " now has §6" + updated + "§a hearts.");
        return true;
    }

    private boolean handleTransfer(CommandSender sender, String fromName, String toName, double amount) {
        if (fromName.equalsIgnoreCase(toName)) {
            sender.sendMessage("§cCan't transfer to the same player.");
            return true;
        }

        double fromHearts = getCurrentHearts(fromName);
        double toHearts = getCurrentHearts(toName);

        // Ensure donor keeps at least 1 heart
        if (fromHearts - amount < 1.0) {
            sender.sendMessage("§c" + fromName + " must keep at least 1 heart.");
            return true;
        }

        double newFrom = clampHearts(fromHearts - amount);
        double newTo = clampHearts(toHearts + amount);

        persistHearts(fromName, newFrom);
        persistHearts(toName, newTo);

        Player from = Bukkit.getPlayerExact(fromName);
        Player to = Bukkit.getPlayerExact(toName);

        if (from != null) applyHearts(from, newFrom);
        if (to != null) applyHearts(to, newTo);

        sender.sendMessage("§aTransferred §6" + amount + "§a hearts: §e" + fromName + " §7→§e " + toName +
                " §7(§e" + fromName + ": " + newFrom + "§7, §e" + toName + ": " + newTo + "§7)");
        if (from != null) from.sendMessage("§eYou donated §6" + amount + "§e hearts to §6" + toName + "§e. You now have §6" + newFrom + "§e.");
        if (to != null) to.sendMessage("§eYou received §6" + amount + "§e hearts from §6" + fromName + "§e. You now have §6" + newTo + "§e.");
        return true;
    }
}
