package com.viperproxy.mixin;

import com.viperproxy.ProxyRuntimeHolder;
import com.viperproxy.proxy.ProxyRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ViperProxy");

    // NOTE: If TransferState doesn't exist in MC 26.1, check the 6th parameter type of
    // ConnectScreen.startConnecting from a decompiled jar and update the import + descriptor.
    @Inject(
        method = "startConnecting(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/multiplayer/resolver/ServerAddress;Lnet/minecraft/client/multiplayer/ServerData;ZLnet/minecraft/client/multiplayer/TransferState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void viperproxy$enforceKillSwitch(
        Screen screen,
        Minecraft client,
        ServerAddress address,
        ServerData info,
        boolean quickPlay,
        @Nullable TransferState transferState,
        CallbackInfo ci
    ) {
        ProxyRuntime runtime = ProxyRuntimeHolder.getRequiredRuntime();
        if (runtime.isReadyForMultiplayerConnection()) {
            return;
        }

        LOGGER.warn(
            "Blocked multiplayer connection by kill switch: address={}, status={}, reason={}",
            address,
            runtime.getStatus(),
            runtime.getStatusDetail()
        );

        client.setScreenAndShow(new DisconnectedScreen(
            screen,
            Component.literal("Viper Proxy"),
            Component.literal(runtime.getKillSwitchReason())
        ));

        ci.cancel();
    }
}
