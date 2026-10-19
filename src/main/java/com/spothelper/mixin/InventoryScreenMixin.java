package com.spothelper.mixin;

import com.spothelper.slotlock.SlotLockManager;
import com.spothelper.slotlock.SlotLockOverlay;
import com.spothelper.config.SpotHelperConfig;
import net.minecraft.client.gui.Click;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.spothelper.util.ScreenOrigin;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Подсветка заблокированных слотов (SlotLockOverlay) во всех HandledScreen, кроме InventoryScreen:
 * его рисует событие Fabric (хвост HandledScreen.render для него в логах не срабатывал).
 */
@Mixin(HandledScreen.class)
public abstract class InventoryScreenMixin implements ScreenOrigin {

    @Shadow protected int x;
    @Shadow protected int y;

    @Override
    public int spothelper$originX() { return this.x; }

    @Override
    public int spothelper$originY() { return this.y; }

    @Inject(method = "render", at = @At("TAIL"))
    private void spothelper$drawSlotOverlays(DrawContext context, int mouseX, int mouseY, float delta,
                                            CallbackInfo ci) {
        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        if (screen instanceof InventoryScreen) return;
        SlotLockOverlay.draw(screen, context);
    }

    /** Alt + клик по слоту инвентаря игрока — поставить/снять блокировку (обычные клики не затрагиваются). */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void spothelper$lockClick(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!SpotHelperConfig.INSTANCE.isSlotLockEnabled() || !spothelper$isAltDown()) return;
        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        for (Slot slot : screen.getScreenHandler().slots) {
            int idx = SlotLockManager.playerIndex(slot);
            if (idx < 0) continue;
            double mx = click.x() - (this.x + slot.x);
            double my = click.y() - (this.y + slot.y);
            if (mx >= 0 && mx < 16 && my >= 0 && my < 16) {
                SlotLockManager.toggle(idx);
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean spothelper$isAltDown() {
        long w = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(w, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }
}
