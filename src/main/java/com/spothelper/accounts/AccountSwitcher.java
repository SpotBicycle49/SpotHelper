package com.spothelper.accounts;

import com.spothelper.config.SpotHelperConfig;
import com.spothelper.mixin.MinecraftClientSessionMixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

public final class AccountSwitcher {

    private AccountSwitcher() {}

    public static String switchTo(String username) {
        username = username == null ? "" : username.trim();
        if (username.isEmpty() || username.length() > 16) {
            return "§cНик должен быть от 1 до 16 символов.";
        }
        if (!username.matches("[A-Za-z0-9_]+")) {
            return "§cНик может содержать только A-Z, a-z, 0-9 и _.";
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        UUID uuid = offlineUUID(username);

        Session newSession;
        try {
            // 1.21.11: Session(String, UUID, String, Optional<String>, Optional<String>)
            newSession = new Session(
                    username,
                    uuid,
                    "",
                    Optional.empty(),
                    Optional.empty()
            );
        } catch (Throwable t) {
            return "§cНе удалось создать сессию: " + t.getMessage();
        }

        try {
            ((MinecraftClientSessionMixin) mc).spothelper$setSession(newSession);
        } catch (Throwable t) {
            return "§cНе удалось применить сессию: " + t.getMessage();
        }

        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        cfg.currentNick = username;
        if (cfg.savedNicks == null) cfg.savedNicks = new java.util.ArrayList<>();
        if (!cfg.savedNicks.contains(username)) cfg.savedNicks.add(username);
        cfg.save();

        return "§aНик изменён на: §f" + username
                + "\n§7UUID: §8" + uuid
                + "\n§7Применится при следующем подключении.";
    }

    public static UUID offlineUUID(String username) {
        return UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    public static String currentName() {
        try {
            return MinecraftClient.getInstance().getSession().getUsername();
        } catch (Throwable t) {
            return "—";
        }
    }
}