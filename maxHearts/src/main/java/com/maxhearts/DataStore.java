package com.maxhearts;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

public class DataStore {
    private final File file;
    private YamlConfiguration data;

    public DataStore(File dataFolder, String fileName) {
        this.file = new File(dataFolder, fileName);
    }

    public void load() {
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try { data.save(file); } catch (IOException ignored) {}
    }

    // ===== UUID-backed storage =====
    public boolean has(UUID uuid) {
        return data.isSet("players." + uuid + ".hearts");
    }

    public double getHearts(UUID uuid, double fallback) {
        return data.getDouble("players." + uuid + ".hearts", fallback);
    }

    public void setHearts(UUID uuid, double hearts) {
        data.set("players." + uuid + ".hearts", hearts);
    }

    public String getLastKnownName(UUID uuid) {
        return data.getString("players." + uuid + ".name");
    }

    public void setLastKnownName(UUID uuid, String name) {
        data.set("players." + uuid + ".name", name);
    }

    // ===== Pending name entries (for players who haven't joined yet) =====
    private String keyForPendingName(String name) {
        return "pendingByName." + name.toLowerCase(Locale.ROOT);
    }

    public boolean hasPendingForName(String name) {
        return data.isSet(keyForPendingName(name));
    }

    public double getPendingForName(String name) {
        return data.getDouble(keyForPendingName(name));
    }

    public void setPendingForName(String name, double hearts) {
        data.set(keyForPendingName(name), hearts);
    }

    public void removePendingForName(String name) {
        data.set(keyForPendingName(name), null);
    }
}

