package com.spothelper.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface MinecraftClientSessionMixin {

    @Mutable
    @Accessor("session")
    void spothelper$setSession(Session session);

    @Accessor("session")
    Session spothelper$getSession();
}