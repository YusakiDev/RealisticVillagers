package me.matsubara.realisticvillagers.util;

import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

public enum VersionMatcher {
    v1_21("1.21", "1.21.1"),
    v1_21_4("1.21.4"),
    v1_21_7("1.21.7", "1.21.8");

    private final String[] versions;

    VersionMatcher(String... versions) {
        this.versions = versions;
    }

    public static @Nullable VersionMatcher getByMinecraftVersion() {
        String current = Bukkit.getBukkitVersion().split("-")[0];
        for (VersionMatcher version : values()) {
            if (ArrayUtils.contains(version.versions, current)) {
                return version;
            }
        }
        return null;
    }
}