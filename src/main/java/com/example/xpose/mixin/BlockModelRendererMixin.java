package com.example.xpose.mixin;

import com.example.xpose.RevealState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels vanilla baked model rendering for blocks present in RevealState (when enabled).
 * Defensive try/catch ensures unexpected errors disable the tool instead of crashing the client.
 */
@Mixin(BlockModelRenderer.class)
public class BlockModelRendererMixin {
    private static final Logger LOGGER = LogManager.getLogger("Xpose-Mixin");

    @Inject(method = "render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;JI)V",
            at = @At("HEAD"), cancellable = true)
    private void onRender(BlockRenderView world, BakedModel model, BlockState state, BlockPos pos,
                          MatrixStack matrices, VertexConsumer vertexConsumer, boolean cull,
                          Random random, long seed, int overlay, CallbackInfo ci) {
        try {
            if (state == null) return;
            Block block = state.getBlock();
            if (RevealState.shouldHide(block)) {
                ci.cancel();
            }
        } catch (Throwable t) {
            LOGGER.warn("Exception in Xpose render hook - disabling Xpose for safety", t);
            RevealState.setEnabled(false);
        }
    }
}