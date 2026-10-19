package com.spothelper.mixin;

import com.spothelper.negeffects.NegativeEffects;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Доступ к интенсивности тошноты/портала для временного обнуления на время кадра. */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityNauseaMixin implements NegativeEffects.NauseaAccess {
    @Shadow public float nauseaIntensity;
    @Shadow public float lastNauseaIntensity;

    @Override public float spothelper$getNausea() { return nauseaIntensity; }
    @Override public float spothelper$getLastNausea() { return lastNauseaIntensity; }
    @Override public void spothelper$setNausea(float intensity, float last) {
        this.nauseaIntensity = intensity;
        this.lastNauseaIntensity = last;
    }
}
