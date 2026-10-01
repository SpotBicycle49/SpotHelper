package com.spothelper.client;

import com.spothelper.SpotHelperConfig;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public class AutoToolHandler {

    private static BlockState lastState = null;
    private static int cooldown = 0;

    public static void tick(MinecraftClient client) {
        if (!SpotHelperConfig.INSTANCE.isAutoToolEnabled()) return;
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;
        if (!client.options.keyAttack.isPressed()) return;
        if (client.interactionManager == null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        HitResult hit = client.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult blockHit = (BlockHitResult) hit;
        BlockState state = client.world.getBlockState(blockHit.getBlockPos());

        if (state == lastState) return;
        lastState = state;

        int bestInvSlot = findBestSlot(client.player.inventory, state);
        if (bestInvSlot == -1) return;

        ClientPlayerEntity player = client.player;
        PlayerInventory inv = player.inventory;

        if (bestInvSlot < 9) {
            if (inv.selectedSlot != bestInvSlot) {
                inv.selectedSlot = bestInvSlot;
            }
            return;
        }

        int hotbarSlot = inv.selectedSlot;
        int handlerSlot = toHandlerSlot(bestInvSlot);

        client.interactionManager.clickSlot(
                player.playerScreenHandler.syncId,
                handlerSlot,
                hotbarSlot,
                SlotActionType.SWAP,
                player
        );

        cooldown = 3;
        lastState = null;
    }

    private static int findBestSlot(PlayerInventory inv, BlockState state) {
        int bestSlot = -1;
        float bestSpeed = 1.0f;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;

            float speed = getSpeedWithEfficiency(stack, state);

            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            } else if (Math.abs(speed - bestSpeed) < 0.001f && speed > 1.0f) {
                if (bestSlot >= 9 && i < 9) {
                    bestSlot = i;
                }
            }
        }

        if (bestSpeed <= 1.0f) {
            return -1;
        }

        return bestSlot;
    }

    private static float getSpeedWithEfficiency(ItemStack stack, BlockState state) {
        float speed = stack.getMiningSpeedMultiplier(state);

        if (speed > 1.0f) {
            int eff = EnchantmentHelper.getLevel(Enchantments.EFFICIENCY, stack);
            if (eff > 0) {
                speed += (float) (eff * eff + 1);
            }
        }

        return speed;
    }

    private static int toHandlerSlot(int invSlot) {
        if (invSlot < 9) {
            return 36 + invSlot;
        }
        return invSlot;
    }

    public static void reset() {
        lastState = null;
        cooldown = 0;
    }
}