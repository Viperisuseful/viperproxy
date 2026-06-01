package com.viperproxy.ui;

import com.viperproxy.ProxyRuntimeHolder;
import com.viperproxy.proxy.ProxyRuntime;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;

public final class MultiplayerButtonInjector {
    private static boolean registered;

    private MultiplayerButtonInjector() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof JoinMultiplayerScreen)) {
                return;
            }

            int buttonWidth = 170;
            int buttonHeight = 20;
            int x = scaledWidth - buttonWidth - 8;
            int y = 8;

            ProxyRuntime runtime = ProxyRuntimeHolder.getRequiredRuntime();

            Button button = Button.builder(
                    runtime.getMultiplayerButtonText(),
                    clicked -> client.setScreenAndShow(new ProxyConfigScreen(screen))
                )
                .pos(x, y)
                .size(buttonWidth, buttonHeight)
                .build();

            Screens.getWidgets(screen).add(button);

            ScreenEvents.afterExtract(screen).register((current, drawContext, mouseX, mouseY, tickDelta) -> {
                button.setMessage(runtime.getMultiplayerButtonText());
            });
        });

        registered = true;
    }
}
