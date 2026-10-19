package com.spotcore.impl.v1_21_11;

import com.spotcore.api.WorldAccess;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Minecraft 1.21.11 implementation. Game access must be called from a thread
 * permitted by the relevant Minecraft API; background workers should process
 * immutable snapshots instead of querying live world objects.
 */
public final class MinecraftWorldAccess implements WorldAccess {
    private MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    @Override
    public boolean isWorldAvailable() {
        MinecraftClient client = client();
        return client.world != null && client.player != null;
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        MinecraftClient client = client();
        World world = client.world;
        return world != null && world.isChunkLoaded(chunkX, chunkZ);
    }

    @Override
    public double playerX() {
        MinecraftClient client = client();
        return client.player == null ? 0.0 : client.player.getX();
    }

    @Override
    public double playerY() {
        MinecraftClient client = client();
        return client.player == null ? 0.0 : client.player.getY();
    }

    @Override
    public double playerZ() {
        MinecraftClient client = client();
        return client.player == null ? 0.0 : client.player.getZ();
    }

    @Override
    public int playerBlockX() {
        MinecraftClient client = client();
        return client.player == null ? 0 : client.player.getBlockX();
    }

    @Override
    public int playerBlockY() {
        MinecraftClient client = client();
        return client.player == null ? 0 : client.player.getBlockY();
    }

    @Override
    public int playerBlockZ() {
        MinecraftClient client = client();
        return client.player == null ? 0 : client.player.getBlockZ();
    }

    @Override
    public int surfaceY(int blockX, int blockZ) {
        MinecraftClient client = client();
        World world = client.world;
        if (world == null || !world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
            return Integer.MIN_VALUE;
        }
        return world.getTopY(Heightmap.Type.WORLD_SURFACE, blockX, blockZ) - 1;
    }

    @Override
    public String blockId(int blockX, int blockY, int blockZ) {
        MinecraftClient client = client();
        World world = client.world;
        if (world == null || !world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
            return "";
        }
        BlockState state = world.getBlockState(new BlockPos(blockX, blockY, blockZ));
        return Registries.BLOCK.getId(state.getBlock()).toString();
    }
}
