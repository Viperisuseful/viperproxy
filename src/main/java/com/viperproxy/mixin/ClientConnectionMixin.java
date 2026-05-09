package com.viperproxy.mixin;

import com.viperproxy.ProxyRuntimeHolder;
import com.viperproxy.proxy.ProxyRuntime;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.handler.PacketSizeLogger;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.network.ClientConnection.class)
public abstract class ClientConnectionMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ViperProxy");

    @Inject(
        method = "addHandlers(Lio/netty/channel/ChannelPipeline;Lnet/minecraft/network/NetworkSide;ZLnet/minecraft/network/handler/PacketSizeLogger;)V",
        at = @At("HEAD")
    )
    private static void viperproxy$injectProxyHandlers(
        ChannelPipeline pipeline,
        NetworkSide side,
        boolean local,
        @Nullable PacketSizeLogger packetSizeLogger,
        CallbackInfo ci
    ) {
        if (local || side != NetworkSide.CLIENTBOUND) {
            return;
        }

        ProxyRuntime runtime = ProxyRuntimeHolder.getRequiredRuntime();
        try {
            runtime.injectProxyHandlers(pipeline, side, local);
            LOGGER.info("Injected proxy handlers successfully: remoteAddress={}", pipeline.channel().remoteAddress());
        } catch (RuntimeException error) {
            LOGGER.error(
                "Failed to inject proxy handlers: remoteAddress={}, status={}, reason={}",
                pipeline.channel().remoteAddress(),
                runtime.getStatus(),
                runtime.getStatusDetail(),
                error
            );
            throw ensureMessage(error);
        } catch (Error error) {
            LOGGER.error("Fatal error while injecting proxy handlers: remoteAddress={}", pipeline.channel().remoteAddress(), error);
            throw error;
        }
    }

    private static RuntimeException ensureMessage(RuntimeException error) {
        if (error.getMessage() != null && !error.getMessage().isBlank()) {
            return error;
        }

        return new IllegalStateException("Proxy injection failed: " + error.getClass().getSimpleName(), error);
    }
}
