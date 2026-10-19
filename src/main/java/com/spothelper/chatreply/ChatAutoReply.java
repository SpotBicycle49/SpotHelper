package com.spothelper.chatreply;

import com.spothelper.config.SpotHelperConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class ChatAutoReply {
    private static final Map<String, Pattern> CACHE = new ConcurrentHashMap<>();
    private static long last = 0;

    private ChatAutoReply() {}

    public static void init() {
        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (!overlay) handle(msg.getString(), null);
        });
        ClientReceiveMessageEvents.CHAT.register((msg, signed, sender, params, ts) ->
                handle(msg.getString(), sender == null ? null : sender.name()));
    }

    private static void handle(String text, String senderName) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!cfg.isChatReplyEnabled() || mc.player == null) return;

        String me = mc.player.getName().getString();
        if (senderName != null && senderName.equalsIgnoreCase(me)) return;
        if (text.contains(me + ":") || text.contains(me + " »") || text.contains(me + ">")) return;

        long now = System.currentTimeMillis();
        if (now - last < 5000) return;

        for (String rule : cfg.chatReplyRules) {
            String[] p = rule.split("=>", 2);
            if (p.length < 2 || p[0].isBlank() || p[1].isBlank()) continue;
            Pattern pat;
            try {
                pat = CACHE.computeIfAbsent(p[0],
                        k -> Pattern.compile(k, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            } catch (Exception e) {
                continue;
            }
            if (pat.matcher(text).find()) {
                last = now;
                String reply = p[1].trim();
                if (reply.startsWith("/")) mc.player.networkHandler.sendChatCommand(reply.substring(1));
                else mc.player.networkHandler.sendChatMessage(reply);
                return;
            }
        }
    }
}
