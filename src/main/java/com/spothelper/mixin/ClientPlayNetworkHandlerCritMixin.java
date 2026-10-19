package com.spothelper.mixin;

import com.spothelper.critsounds.AttackSounds;
import com.spothelper.critsounds.AttackTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Подтверждения от сервера для пользовательских звуков боя: крит (анимация и звук), обычный удар,
 * сплеш-атака и удар без урона. Обработчики пакетов вызываются дважды: сначала в сетевом потоке, затем
 * (после forceMainThread) в главном. Работаем и отменяем только в главном, иначе отмена в сетевом потоке
 * потеряла бы пакет.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerCritMixin {
    private static final Identifier SPOTHELPER$CRIT = Identifier.ofVanilla("entity.player.attack.crit");
    private static final Identifier SPOTHELPER$STRONG = Identifier.ofVanilla("entity.player.attack.strong");
    private static final Identifier SPOTHELPER$WEAK = Identifier.ofVanilla("entity.player.attack.weak");
    private static final Identifier SPOTHELPER$SWEEP = Identifier.ofVanilla("entity.player.attack.sweep");
    private static final Identifier SPOTHELPER$NODAMAGE = Identifier.ofVanilla("entity.player.attack.nodamage");

    @Inject(method = "onEntityAnimation", at = @At("HEAD"))
    private void spothelper$onEntityAnimation(EntityAnimationS2CPacket packet, CallbackInfo ci) {
        if (!MinecraftClient.getInstance().isOnThread()) return;
        try {
            AttackSounds.onAnimation(packet.getEntityId(), packet.getAnimationId());
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "onPlaySound", at = @At("HEAD"), cancellable = true)
    private void spothelper$onPlaySound(PlaySoundS2CPacket packet, CallbackInfo ci) {
        if (!MinecraftClient.getInstance().isOnThread()) return;
        try {
            var sound = packet.getSound();
            AttackTracker.Vanilla kind;
            if (sound.matchesId(SPOTHELPER$CRIT)) kind = AttackTracker.Vanilla.CRIT;
            else if (sound.matchesId(SPOTHELPER$STRONG) || sound.matchesId(SPOTHELPER$WEAK)) kind = AttackTracker.Vanilla.STRONG_OR_WEAK;
            else if (sound.matchesId(SPOTHELPER$SWEEP)) kind = AttackTracker.Vanilla.SWEEP;
            else if (sound.matchesId(SPOTHELPER$NODAMAGE)) kind = AttackTracker.Vanilla.NODAMAGE;
            else return;
            if (AttackSounds.onAttackSound(kind, packet.getX(), packet.getY(), packet.getZ())) ci.cancel();
        } catch (Throwable ignored) {
        }
    }
}
