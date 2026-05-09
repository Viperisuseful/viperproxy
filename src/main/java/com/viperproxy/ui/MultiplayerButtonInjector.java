package com.viperproxy.ui;

import com.viperproxy.ProxyRuntimeHolder;
import com.viperproxy.proxy.ProxyRuntime;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;

public final class MultiplayerButtonInjector {
    private static boolean registered;

    private MultiplayerButtonInjector() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof MultiplayerScreen)) {
                return;
            }

            int buttonWidth = 170;
            int buttonHeight = 20;
            int x = scaledWidth - buttonWidth - 8;
            int y = 8;

                ProxyRuntime runtime = ProxyRuntimeHolder.getRequiredRuntime();

            ButtonWidget button = ButtonWidget.builder(
                    runtime.getMultiplayerButtonText(),
                    clicked -> client.setScreen(new ProxyConfigScreen(screen))
                )
                .dimensions(x, y, buttonWidth, buttonHeight)
                .build();

            Screens.getButtons(screen).add(button);

            ScreenEvents.afterRender(screen).register((current, drawContext, mouseX, mouseY, tickDelta) -> {
                button.setMessage(runtime.getMultiplayerButtonText());
            });
        });

        registered = true;
    }
}
