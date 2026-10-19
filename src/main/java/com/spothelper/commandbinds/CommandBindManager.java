package com.spothelper.commandbinds;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Менеджер биндов.
 *  - Однократные: команда отправляется по нажатию.
 *  - Циклические: работают как переключатель. Нажал — пошёл цикл, нажал ещё раз — остановился.
 */
public final class CommandBindManager {

    private static final List<CommandBind> binds = new ArrayList<>();

    private CommandBindManager() {}

    public static List<CommandBind> getBinds() {
        return binds;
    }

    public static void add(CommandBind bind) {
        binds.add(bind);
        CommandBindConfig.save();
    }

    public static void remove(CommandBind bind) {
        binds.remove(bind);
        CommandBindConfig.save();
    }

    /** Вызывается каждый клиентский тик из SpotHelperClient. */
    public static void tick(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.world == null) return;

        long window = mc.getWindow().getHandle();
        boolean inGame = mc.currentScreen == null;

        for (CommandBind bind : binds) {
            if (bind.command == null || bind.command.trim().isEmpty()) continue;
            if (bind.keyCode <= 0) continue;

            // --- 1) Обработка нажатия клавиши ---
            // Клавиша срабатывает только в игре (без открытых экранов: чат, инвентарь, меню).
            // Состояние клавиши отслеживаем всегда, чтобы зажатая при закрытии чата не сработала.
            boolean pressedNow = GLFW.glfwGetKey(window, bind.keyCode) == GLFW.GLFW_PRESS;
            boolean pressedEdge = inGame && pressedNow && !bind.wasPressed;

            if (pressedEdge) {
                if (bind.cyclic) {
                    // Переключатель: вкл ↔ выкл
                    bind.active = !bind.active;
                    if (bind.active) {
                        // только что включили — выполняем сразу
                        bind.ticksUntilNext = 0;
                    } else {
                        bind.ticksUntilNext = 0;
                    }
                } else {
                    // однократное — сразу
                    runCommand(mc, bind.command);
                }
            }

            bind.wasPressed = pressedNow;

            // --- 2) Циклический запуск: идёт всегда, пока active (в том числе при открытом чате) ---
            if (bind.cyclic && bind.active) {
                if (bind.ticksUntilNext > 0) {
                    bind.ticksUntilNext--;
                }
                if (bind.ticksUntilNext <= 0) {
                    runCommand(mc, bind.command);
                    int intervalTicks = Math.max(1, bind.intervalSeconds * 20);
                    bind.ticksUntilNext = intervalTicks;
                }
            }
        }
    }

    /**
     * Отправляет команду или сообщение от имени игрока.
     *  - если бинд начинается с "/" — отправляется как команда (sendChatCommand);
     *  - если без "/" — отправляется как обычное сообщение в чат (sendChatMessage).
     */
    public static void runCommand(MinecraftClient mc, String command) {
        if (mc == null || mc.player == null) return;

        String cmd = command.trim();
        if (cmd.isEmpty()) return;

        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler == null) {
            ClientPlayerEntity player = mc.player;
            if (player != null) {
                player.sendMessage(Text.literal("§cSpotHelper: нет соединения с сервером"), true);
            }
            return;
        }

        try {
            if (cmd.startsWith("/")) {
                // Команда — убираем слэш, отправляем через sendChatCommand
                handler.sendChatCommand(cmd.substring(1));
            } else {
                // Обычное сообщение в чат
                handler.sendChatMessage(cmd);
            }
        } catch (Throwable t) {
            com.spothelper.util.Log.error("command bind send", t);
        }
    }

    /** Сброс состояния (например, при выходе из мира). */
    public static void resetState() {
        for (CommandBind bind : binds) {
            bind.wasPressed = false;
            bind.ticksUntilNext = 0;
            bind.active = false;
        }
    }
}