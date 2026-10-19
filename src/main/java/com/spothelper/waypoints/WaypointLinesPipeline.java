package com.spothelper.waypoints;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BuiltBuffer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Отправка буфера линий в рендер (1.21.11: через RenderPipeline + RenderPass). */
public final class WaypointLinesPipeline {

    private static final Vector4f WHITE = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    private static final Vector3f NO_MODEL_OFFSET = new Vector3f(0.0F, 0.0F, 0.0F);
    private static final Matrix4f IDENTITY = new Matrix4f();

    private WaypointLinesPipeline() {
    }

    /** Takes ownership of {@code buffer} and closes it. */
    public static void draw(BuiltBuffer buffer, RenderPipeline pipeline) {
        if (buffer == null) {
            return;
        }
        if (pipeline == null) {
            buffer.close();
            return;
        }

        try (buffer) {
            BuiltBuffer.DrawParameters parameters = buffer.getDrawParameters();
            VertexFormat format = parameters.format();

            // DynamicTransforms is a ring buffer, so the slice must be allocated
            // immediately before the pass that uses it.
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().write(
                    RenderSystem.getModelViewMatrix(), WHITE, NO_MODEL_OFFSET, IDENTITY
            );
            GpuBuffer vertexBuffer = format.uploadImmediateVertexBuffer(buffer.getBuffer());
            RenderSystem.ShapeIndexBuffer sequential = RenderSystem.getSequentialBuffer(parameters.mode());
            GpuBuffer indexBuffer = sequential.getIndexBuffer(parameters.indexCount());
            VertexFormat.IndexType indexType = sequential.getIndexType();

            Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
            if (framebuffer == null) {
                return;
            }

            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "SpotHelper waypoints",
                    framebuffer.getColorAttachmentView(),
                    OptionalInt.empty(),
                    framebuffer.useDepthAttachment ? framebuffer.getDepthAttachmentView() : null,
                    OptionalDouble.empty()
            )) {
                pass.setPipeline(pipeline);
                pass.disableScissor();
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                pass.setVertexBuffer(0, vertexBuffer);
                pass.setIndexBuffer(indexBuffer, indexType);
                pass.drawIndexed(0, 0, parameters.indexCount(), 1);
            }
        }
    }
}
