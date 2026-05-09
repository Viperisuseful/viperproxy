package com.viperproxy.mixin;

import com.viperproxy.ProxyRuntimeHolder;
import com.viperproxy.proxy.ProxyRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
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

    @Inject(
        method = "connect(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;ZLnet/minecraft/client/network/CookieStorage;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void viperproxy$enforceKillSwitch(
        Screen screen,
        MinecraftClient client,
        ServerAddress address,
        ServerInfo info,
        boolean quickPlay,
        @Nullable CookieStorage cookieStorage,
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

        client.setScreen(new DisconnectedScreen(
            screen,
            Text.literal("Viper Proxy"),
            Text.literal(runtime.getKillSwitchReason())
        ));

        ci.cancel();
    }
}
