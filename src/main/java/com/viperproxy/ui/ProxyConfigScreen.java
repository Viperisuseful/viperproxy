package com.viperproxy.ui;

import com.viperproxy.ProxyRuntimeHolder;
import com.viperproxy.config.ProxyConfig;
import com.viperproxy.proxy.ProxyRuntime;
import com.viperproxy.proxy.ProxyStatus;
import com.viperproxy.proxy.ProxyType;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

public final class ProxyConfigScreen extends Screen {
    private static final int LEFT_PANEL_WIDTH = 380;
    private static final int RIGHT_PANEL_WIDTH = 220;
    private static final int PANEL_GAP = 8;
    private static final int PADDING = 10;
    private static final int ROW_HEIGHT = 20;
    private static final int LIST_ROW_HEIGHT = 20;
    private static final int DELETE_BUTTON_SIZE = 12;

    private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath("viperproxy", "textures/gui/logo.png");
    private static final int LOGO_DISPLAY_SIZE = 64;

    private static final int COLOR_SCREEN_OVERLAY = 0xCC000000;
    private static final int COLOR_PANEL_BG = 0xDD0A0A0F;
    private static final int COLOR_RIGHT_PANEL_BG = 0xDD050508;
    private static final int COLOR_BORDER = 0xFF2A0A3A;
    private static final int COLOR_GLOW = 0x44BC5CC7;
    private static final int COLOR_LABEL = 0xFFAAAAAA;
    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_STATUS_OK = 0xFF44FF44;
    private static final int COLOR_STATUS_ERROR = 0xFFFF4444;
    private static final int COLOR_STATUS_CONNECTING = 0xFFFFAA00;
    private static final int COLOR_STATUS_DISABLED = 0xFF888888;

    private final Screen parent;

    private EditBox hostField;
    private EditBox portField;
    private EditBox usernameField;
    private EditBox passwordField;

    private StyledButtonWidget profileButton;
    private StyledButtonWidget newProfileButton;
    private StyledButtonWidget typeButton;
    private StyledButtonWidget applyButton;
    private StyledButtonWidget closeButton;
    private StyledButtonWidget resetButton;

    private ProxyType selectedType = ProxyType.SOCKS5;
    private String localError = "";
    private int profileListScroll;

    private int leftPanelX;
    private int rightPanelX;
    private int panelY;
    private int panelHeight;

    private int titleY;
    private int titleSeparatorY;
    private int profileLabelY;
    private int profileRowY;
    private int hostLabelY;
    private int hostFieldY;
    private int portLabelY;
    private int portFieldY;
    private int credentialsLabelY;
    private int credentialsFieldY;
    private int typeLabelY;
    private int typeButtonY;
    private int statusSeparatorY;
    private int statusLineY;
    private int ipLineY;
    private int latencyLineY;
    private int actionSeparatorY;
    private int applyButtonY;
    private int bottomButtonsY;

    private int listStartY;
    private int listEndY;

    public ProxyConfigScreen(Screen parent) {
        super(Component.literal("Viper Proxy Configuration"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int totalWidth = LEFT_PANEL_WIDTH + PANEL_GAP + RIGHT_PANEL_WIDTH;
        int lineHeight = this.font.lineHeight;
        this.panelHeight = computePanelHeight(lineHeight);

        this.leftPanelX = this.width / 2 - totalWidth / 2;
        this.rightPanelX = this.leftPanelX + LEFT_PANEL_WIDTH + PANEL_GAP;
        this.panelY = this.height / 2 - this.panelHeight / 2;

        layoutVerticalFlow(lineHeight);

        int contentX = this.leftPanelX + PADDING;
        int contentRight = this.leftPanelX + LEFT_PANEL_WIDTH - PADDING;
        int contentWidth = contentRight - contentX;

        ProxyRuntime runtime = runtime();
        ProxyConfig config = runtime.getActiveConfigCopy().normalized();
        this.selectedType = config.type;

        this.hostField = new CenteredTextFieldWidget(this.font, contentX, this.hostFieldY, contentWidth, ROW_HEIGHT, Component.literal("Host"));
        this.hostField.setMaxLength(255);
        this.hostField.setValue(config.host);
        this.hostField.setBordered(false);
        configureSuggestionBehavior(this.hostField, "Host / IP");

        this.portField = new CenteredTextFieldWidget(this.font, contentX, this.portFieldY, contentWidth, ROW_HEIGHT, Component.literal("Port"));
        this.portField.setMaxLength(5);
        this.portField.setValue(Integer.toString(config.port));
        this.portField.setBordered(false);
        configureSuggestionBehavior(this.portField, "Port");

        int sideBySideWidth = (contentWidth - 6) / 2;
        this.usernameField = new CenteredTextFieldWidget(this.font, contentX, this.credentialsFieldY, sideBySideWidth, ROW_HEIGHT, Component.literal("Username"));
        this.usernameField.setMaxLength(128);
        this.usernameField.setValue(config.username);
        this.usernameField.setBordered(false);
        configureSuggestionBehavior(this.usernameField, "Username (optional)");

        this.passwordField = new CenteredTextFieldWidget(
            this.font,
            contentX + sideBySideWidth + 6,
            this.credentialsFieldY,
            sideBySideWidth,
            ROW_HEIGHT,
            Component.literal("Password")
        );
        this.passwordField.setMaxLength(128);
        this.passwordField.setValue(config.password);
        this.passwordField.setBordered(false);
        this.passwordField.addFormatter((text, firstCharacterIndex) ->
            FormattedCharSequence.forward("•".repeat(text.length()), Style.EMPTY)
        );
        configureSuggestionBehavior(this.passwordField, "Password (optional)");

        this.addRenderableWidget(this.hostField);
        this.addRenderableWidget(this.portField);
        this.addRenderableWidget(this.usernameField);
        this.addRenderableWidget(this.passwordField);

        int profileButtonWidth = (int) Math.floor(contentWidth * 0.60);
        int newProfileButtonWidth = contentWidth - profileButtonWidth - 6;

        this.profileButton = this.addRenderableWidget(
            buildStyledButton(contentX, this.profileRowY, profileButtonWidth, ROW_HEIGHT, profileButtonText(), this::cycleProfile, true)
        );

        this.newProfileButton = this.addRenderableWidget(
            buildStyledButton(
                contentX + profileButtonWidth + 6,
                this.profileRowY,
                newProfileButtonWidth,
                ROW_HEIGHT,
                Component.literal("NEW PROFILE"),
                this::newProfile,
                false
            )
        );

        this.typeButton = this.addRenderableWidget(
            buildStyledButton(contentX, this.typeButtonY, contentWidth, ROW_HEIGHT, typeButtonText(), this::cycleProxyType, false)
        );

        this.applyButton = this.addRenderableWidget(
            buildStyledButton(contentX, this.applyButtonY, contentWidth, ROW_HEIGHT, Component.literal("APPLY"), this::applyConfig, true)
        );

        int bottomButtonWidth = (contentWidth - 6) / 2;
        this.closeButton = this.addRenderableWidget(
            buildStyledButton(contentX, this.bottomButtonsY, bottomButtonWidth, ROW_HEIGHT, Component.literal("CLOSE"), this::onClose, false)
        );

        this.resetButton = this.addRenderableWidget(
            buildStyledButton(
                contentX + bottomButtonWidth + 6,
                this.bottomButtonsY,
                bottomButtonWidth,
                ROW_HEIGHT,
                Component.literal("RESET"),
                this::resetForm,
                false
            )
        );

        this.listStartY = this.panelY + 34;
        this.listEndY = this.panelY + this.panelHeight - 10;

        this.profileListScroll = clampScroll(this.profileListScroll, runtime.getProfileNames());
        this.setInitialFocus(this.hostField);
    }

    private int computePanelHeight(int lineHeight) {
        int currentY = 8;

        currentY += LOGO_DISPLAY_SIZE + 8;
        currentY += 2 + 8;
        currentY += lineHeight + 2;
        currentY += ROW_HEIGHT + 8;
        currentY += lineHeight + 2;
        currentY += ROW_HEIGHT + 8;
        currentY += lineHeight + 2;
        currentY += ROW_HEIGHT + 8;
        currentY += lineHeight + 2;
        currentY += ROW_HEIGHT + 8;
        currentY += lineHeight + 2;
        currentY += ROW_HEIGHT + 4;
        currentY += 2 + 6;
        currentY += lineHeight + 2;
        currentY += lineHeight + 2;
        currentY += lineHeight + 6;
        currentY += 2 + 6;
        currentY += ROW_HEIGHT + 4;
        currentY += ROW_HEIGHT;

        return currentY + 8;
    }

    private void layoutVerticalFlow(int lineHeight) {
        int currentY = this.panelY + 8;

        this.titleY = currentY;
        currentY += LOGO_DISPLAY_SIZE + 8;

        this.titleSeparatorY = currentY;
        currentY += 2 + 8;

        this.profileLabelY = currentY;
        currentY += lineHeight + 2;

        this.profileRowY = currentY;
        currentY += ROW_HEIGHT + 8;

        this.hostLabelY = currentY;
        currentY += lineHeight + 2;

        this.hostFieldY = currentY;
        currentY += ROW_HEIGHT + 8;

        this.portLabelY = currentY;
        currentY += lineHeight + 2;

        this.portFieldY = currentY;
        currentY += ROW_HEIGHT + 8;

        this.credentialsLabelY = currentY;
        currentY += lineHeight + 2;

        this.credentialsFieldY = currentY;
        currentY += ROW_HEIGHT + 8;

        this.typeLabelY = currentY;
        currentY += lineHeight + 2;

        this.typeButtonY = currentY;
        currentY += ROW_HEIGHT + 4;

        this.statusSeparatorY = currentY;
        currentY += 2 + 6;

        this.statusLineY = currentY;
        currentY += lineHeight + 2;

        this.ipLineY = currentY;
        currentY += lineHeight + 2;

        this.latencyLineY = currentY;
        currentY += lineHeight + 6;

        this.actionSeparatorY = currentY;
        currentY += 2 + 6;

        this.applyButtonY = currentY;
        currentY += ROW_HEIGHT + 4;

        this.bottomButtonsY = currentY;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Render everything manually to control z-order (panels behind widgets).
        int contentX = this.leftPanelX + PADDING;
        int contentWidth = LEFT_PANEL_WIDTH - (PADDING * 2);
        int panelBottom = this.panelY + this.panelHeight;

        renderPanels(context);

        int logoX = this.leftPanelX + (LEFT_PANEL_WIDTH - LOGO_DISPLAY_SIZE) / 2;
        context.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            LOGO_TEXTURE,
            logoX, this.titleY,
            LOGO_DISPLAY_SIZE, LOGO_DISPLAY_SIZE
        );
        context.fill(this.leftPanelX + 8, this.titleSeparatorY, this.leftPanelX + LEFT_PANEL_WIDTH - 8, this.titleSeparatorY + 2, COLOR_BORDER);

        context.text(this.font, Component.literal("PROFILE"), contentX, this.profileLabelY, COLOR_LABEL, true);
        context.text(this.font, Component.literal("HOST"), contentX, this.hostLabelY, COLOR_LABEL, true);
        context.text(this.font, Component.literal("PORT"), contentX, this.portLabelY, COLOR_LABEL, true);
        context.text(this.font, Component.literal("USERNAME / PASSWORD"), contentX, this.credentialsLabelY, COLOR_LABEL, true);
        context.text(this.font, Component.literal("TYPE"), contentX, this.typeLabelY, COLOR_LABEL, true);

        drawFieldChrome(context, this.hostField);
        drawFieldChrome(context, this.portField);
        drawFieldChrome(context, this.usernameField);
        drawFieldChrome(context, this.passwordField);

        this.hostField.extractRenderState(context, mouseX, mouseY, delta);
        this.portField.extractRenderState(context, mouseX, mouseY, delta);
        this.usernameField.extractRenderState(context, mouseX, mouseY, delta);
        this.passwordField.extractRenderState(context, mouseX, mouseY, delta);

        this.profileButton.extractRenderState(context, mouseX, mouseY, delta);
        this.newProfileButton.extractRenderState(context, mouseX, mouseY, delta);
        this.typeButton.extractRenderState(context, mouseX, mouseY, delta);
        this.applyButton.extractRenderState(context, mouseX, mouseY, delta);
        this.closeButton.extractRenderState(context, mouseX, mouseY, delta);
        this.resetButton.extractRenderState(context, mouseX, mouseY, delta);

        context.fill(contentX, this.statusSeparatorY, contentX + contentWidth, this.statusSeparatorY + 2, COLOR_BORDER);
        context.fill(contentX, this.actionSeparatorY, contentX + contentWidth, this.actionSeparatorY + 2, COLOR_BORDER);

        renderStatusBlock(context, contentX);
        renderStoredProfilesList(context, mouseX, mouseY);

        if (!this.localError.isBlank()) {
            context.text(
                this.font,
                Component.literal(this.localError),
                contentX,
                Math.min(panelBottom - 12 - this.font.lineHeight, this.latencyLineY + this.font.lineHeight + 2),
                COLOR_STATUS_ERROR,
                true
            );
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }

    private ProxyRuntime runtime() {
        return ProxyRuntimeHolder.getRequiredRuntime();
    }

    private void cycleProfile() {
        runtime().cycleActiveProfile();
        this.localError = "";
        loadFromRuntime();
    }

    private void newProfile() {
        this.localError = "";
        runtime().createProfileFromUi("", new ProxyConfig());
        loadFromRuntime();
    }

    private void cycleProxyType() {
        ProxyType[] values = ProxyType.values();
        int index = (this.selectedType.ordinal() + 1) % values.length;
        this.selectedType = values[index];
        this.typeButton.setMessage(typeButtonText());
    }

    private void applyConfig() {
        this.localError = "";

        ProxyConfig config = parseFormConfig();
        if (config == null) {
            return;
        }

        runtime().applyFromUi(config, runtime().getActiveProfileName());
        loadFromRuntime();
    }

    private void resetForm() {
        this.localError = "";
        loadFromRuntime();
    }

    private ProxyConfig parseFormConfig() {
        String host = this.hostField.getValue().trim();
        String portText = this.portField.getValue().trim();

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException exception) {
            this.localError = "Port must be a number between 1 and 65535.";
            return null;
        }

        if (host.isBlank() || port < 1 || port > 65535) {
            this.localError = "Host and port are required.";
            return null;
        }

        ProxyConfig config = new ProxyConfig();
        config.enabled = true;
        config.host = host;
        config.port = port;
        config.username = this.usernameField.getValue().trim();
        config.password = this.passwordField.getValue();
        config.type = this.selectedType;
        return config;
    }

    private void loadFromRuntime() {
        ProxyRuntime runtime = runtime();
        ProxyConfig config = runtime.getActiveConfigCopy().normalized();

        this.hostField.setValue(config.host == null ? "" : config.host);
        this.portField.setValue(config.enabled || !config.host.isBlank() ? Integer.toString(config.port) : "");
        this.usernameField.setValue(config.username == null ? "" : config.username);
        this.passwordField.setValue(config.password == null ? "" : config.password);
        refreshSuggestionForCurrentText(this.hostField, "Host / IP");
        refreshSuggestionForCurrentText(this.portField, "Port");
        refreshSuggestionForCurrentText(this.usernameField, "Username (optional)");
        refreshSuggestionForCurrentText(this.passwordField, "Password (optional)");
        this.selectedType = config.type;

        if (this.profileButton != null) {
            this.profileButton.setMessage(profileButtonText());
        }
        if (this.typeButton != null) {
            this.typeButton.setMessage(typeButtonText());
        }
    }

    private Component profileButtonText() {
        return Component.literal("ACTIVE: " + runtime().getActiveProfileName());
    }

    private Component typeButtonText() {
        return Component.literal("Type: ").append(this.selectedType.asText());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isInListBounds(mouseX, mouseY)) {
            List<String> profiles = runtime().getProfileNames();
            int maxScroll = maxProfileScroll(profiles);
            if (verticalAmount < 0) {
                this.profileListScroll = Math.min(maxScroll, this.profileListScroll + 1);
            } else if (verticalAmount > 0) {
                this.profileListScroll = Math.max(0, this.profileListScroll - 1);
            }
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (click.button() == 0 && isInListBounds(click.x(), click.y())) {
            List<String> profiles = runtime().getProfileNames();
            int visibleStart = this.profileListScroll;
            int row = (int) ((click.y() - this.listStartY) / LIST_ROW_HEIGHT);
            int selectedIndex = visibleStart + row;
            if (selectedIndex >= 0 && selectedIndex < profiles.size()) {
                int listX = this.rightPanelX + 10;
                int listWidth = RIGHT_PANEL_WIDTH - 20;
                int dotX = listX + listWidth - 10;
                int rowY = this.listStartY + row * LIST_ROW_HEIGHT;

                if (profiles.size() > 1) {
                    int deleteBtnX = dotX - DELETE_BUTTON_SIZE - 4;
                    int deleteBtnY = rowY + (LIST_ROW_HEIGHT - DELETE_BUTTON_SIZE) / 2;
                    if (click.x() >= deleteBtnX && click.x() <= deleteBtnX + DELETE_BUTTON_SIZE
                        && click.y() >= deleteBtnY && click.y() <= deleteBtnY + DELETE_BUTTON_SIZE) {
                        runtime().deleteProfile(selectedIndex);
                        this.localError = "";
                        this.profileListScroll = clampScroll(this.profileListScroll, runtime().getProfileNames());
                        loadFromRuntime();
                        return true;
                    }
                }

                runtime().selectActiveProfile(selectedIndex);
                this.localError = "";
                loadFromRuntime();
                return true;
            }
        }

        return super.mouseClicked(click, doubleClick);
    }

    private void renderPanels(GuiGraphicsExtractor context) {
        int leftPanelRight = this.leftPanelX + LEFT_PANEL_WIDTH;
        int rightPanelRight = this.rightPanelX + RIGHT_PANEL_WIDTH;
        int panelBottom = this.panelY + this.panelHeight;

        context.fill(0, 0, this.width, this.height, COLOR_SCREEN_OVERLAY);

        context.fill(this.leftPanelX, this.panelY, leftPanelRight, panelBottom, COLOR_PANEL_BG);
        drawPanelBorder(context, this.leftPanelX, this.panelY, LEFT_PANEL_WIDTH, this.panelHeight, COLOR_BORDER);

        context.fill(this.rightPanelX, this.panelY, rightPanelRight, panelBottom, COLOR_RIGHT_PANEL_BG);
        drawPanelBorder(context, this.rightPanelX, this.panelY, RIGHT_PANEL_WIDTH, this.panelHeight, COLOR_BORDER);

        context.fill(this.leftPanelX + 2, this.panelY + 1, leftPanelRight - 2, this.panelY + 2, COLOR_GLOW);
    }

    private void renderStatusBlock(GuiGraphicsExtractor context, int contentX) {
        ProxyRuntime runtime = runtime();
        ProxyStatus status = runtime.getStatus();

        int statusColor = switch (status) {
            case CONNECTED -> COLOR_STATUS_OK;
            case CONNECTING -> COLOR_STATUS_CONNECTING;
            case DISABLED -> COLOR_STATUS_DISABLED;
            case ERROR -> COLOR_STATUS_ERROR;
        };

        String statusText = switch (status) {
            case CONNECTED -> "STATUS: CONNECTED";
            case CONNECTING -> "STATUS: CONNECTING...";
            case DISABLED -> "STATUS: DISABLED";
            case ERROR -> "STATUS: ERROR";
        };

        long latencyMs = runtime.getLatencyMs();
        String latencyText = latencyMs >= 0 ? "LATENCY: " + latencyMs + "ms" : "LATENCY: —";
        String ipText = runtime.getExternalIp() == null || runtime.getExternalIp().isBlank() ? "IP: —" : "IP: " + runtime.getExternalIp();

        context.text(this.font, Component.literal(statusText), contentX, this.statusLineY, statusColor, true);
        context.text(this.font, Component.literal(ipText), contentX, this.ipLineY, COLOR_LABEL, true);
        context.text(this.font, Component.literal(latencyText), contentX, this.latencyLineY, COLOR_LABEL, true);
    }

    private void renderStoredProfilesList(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int titleY = this.panelY + 14;
        context.centeredText(
            this.font,
            Component.literal("STORED PROXIES"),
            this.rightPanelX + RIGHT_PANEL_WIDTH / 2,
            titleY,
            COLOR_TITLE
        );
        context.fill(this.rightPanelX + 8, this.panelY + 26, this.rightPanelX + RIGHT_PANEL_WIDTH - 8, this.panelY + 27, COLOR_BORDER);

        List<String> profiles = runtime().getProfileNames();
        this.profileListScroll = clampScroll(this.profileListScroll, profiles);

        if (profiles.isEmpty()) {
            context.centeredText(
                this.font,
                Component.literal("No profiles saved"),
                this.rightPanelX + RIGHT_PANEL_WIDTH / 2,
                this.panelY + this.panelHeight / 2,
                COLOR_LABEL
            );
            return;
        }

        int listX = this.rightPanelX + 10;
        int listWidth = RIGHT_PANEL_WIDTH - 20;
        int visibleRows = Math.max(1, (this.listEndY - this.listStartY) / LIST_ROW_HEIGHT);
        int activeIndex = runtime().getActiveProfileIndex();
        ProxyStatus status = runtime().getStatus();

        context.enableScissor(listX, this.listStartY, listX + listWidth, this.listEndY);
        for (int i = 0; i < visibleRows; i++) {
            int profileIndex = this.profileListScroll + i;
            if (profileIndex >= profiles.size()) {
                break;
            }

            int rowY = this.listStartY + i * LIST_ROW_HEIGHT;
            boolean hovered = mouseX >= listX && mouseX <= listX + listWidth && mouseY >= rowY && mouseY < rowY + LIST_ROW_HEIGHT;
            if (hovered) {
                context.fill(listX, rowY, listX + listWidth, rowY + LIST_ROW_HEIGHT, 0x33FFFFFF);
            }

            if (profileIndex == activeIndex) {
                context.fill(listX, rowY, listX + listWidth, rowY + LIST_ROW_HEIGHT, 0x22BC5CC7);
            }

            context.text(this.font, Component.literal(profiles.get(profileIndex)), listX + 6, rowY + 6, COLOR_LABEL, true);

            int dotColor;
            if (profileIndex != activeIndex) {
                dotColor = 0xFF777777;
            } else {
                dotColor = switch (status) {
                    case CONNECTED -> COLOR_STATUS_OK;
                    case CONNECTING -> COLOR_STATUS_CONNECTING;
                    case DISABLED -> COLOR_STATUS_DISABLED;
                    case ERROR -> COLOR_STATUS_ERROR;
                };
            }

            int dotX = listX + listWidth - 10;
            int dotY = rowY + LIST_ROW_HEIGHT / 2 - 2;
            context.fill(dotX, dotY, dotX + 4, dotY + 4, dotColor);

            if (profiles.size() > 1) {
                int deleteBtnX = dotX - DELETE_BUTTON_SIZE - 4;
                int deleteBtnY = rowY + (LIST_ROW_HEIGHT - DELETE_BUTTON_SIZE) / 2;
                boolean deleteHovered = mouseX >= deleteBtnX && mouseX <= deleteBtnX + DELETE_BUTTON_SIZE
                    && mouseY >= deleteBtnY && mouseY <= deleteBtnY + DELETE_BUTTON_SIZE;

                int deleteBg = deleteHovered ? 0x66BC5CC7 : 0x33882288;
                context.fill(deleteBtnX, deleteBtnY, deleteBtnX + DELETE_BUTTON_SIZE, deleteBtnY + DELETE_BUTTON_SIZE, deleteBg);
                drawPanelBorder(context, deleteBtnX, deleteBtnY, DELETE_BUTTON_SIZE, DELETE_BUTTON_SIZE, COLOR_BORDER);

                int xColor = deleteHovered ? 0xFFFFFFFF : 0xFFAA55BB;
                int xCenterX = deleteBtnX + DELETE_BUTTON_SIZE / 2;
                int xCenterY = deleteBtnY + (DELETE_BUTTON_SIZE - this.font.lineHeight) / 2;
                context.centeredText(this.font, Component.literal("x"), xCenterX, xCenterY, xColor);
            }
        }
        context.disableScissor();
    }

    private void drawFieldChrome(GuiGraphicsExtractor context, EditBox field) {
        int x1 = field.getX() - 1;
        int y1 = field.getY() - 1;
        int x2 = field.getX() + field.getWidth() + 1;
        int y2 = field.getY() + field.getHeight() + 1;

        context.fill(x1, y1, x2, y2, 0xFF1A1A22);
        drawPanelBorder(context, x1, y1, x2 - x1, y2 - y1, COLOR_BORDER);
    }

    private static void drawPanelBorder(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    private boolean isInListBounds(double mouseX, double mouseY) {
        int listX = this.rightPanelX + 10;
        int listWidth = RIGHT_PANEL_WIDTH - 20;
        return mouseX >= listX
            && mouseX <= listX + listWidth
            && mouseY >= this.listStartY
            && mouseY <= this.listEndY;
    }

    private int maxProfileScroll(List<String> profileNames) {
        int visibleRows = Math.max(1, (this.listEndY - this.listStartY) / LIST_ROW_HEIGHT);
        return Math.max(0, profileNames.size() - visibleRows);
    }

    private int clampScroll(int scroll, List<String> profileNames) {
        int max = maxProfileScroll(profileNames);
        if (scroll < 0) {
            return 0;
        }
        if (scroll > max) {
            return max;
        }
        return scroll;
    }

    private StyledButtonWidget buildStyledButton(int x, int y, int w, int h, Component label, Runnable action, boolean activeAccent) {
        return new StyledButtonWidget(x, y, w, h, label, action, activeAccent);
    }

    private static final class StyledButtonWidget extends AbstractButton {
        private final boolean activeAccent;
        private final Runnable onPress;

        protected StyledButtonWidget(int x, int y, int width, int height, Component message, Runnable onPress, boolean activeAccent) {
            super(x, y, width, height, message);
            this.activeAccent = activeAccent;
            this.onPress = onPress;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            int backgroundColor = this.activeAccent
                ? 0xFF1A0A2A
                : (this.isHovered() ? 0xFF1A1A2E : 0xFF0F0F18);

            int x = this.getX();
            int y = this.getY();
            int right = x + this.width;
            int bottom = y + this.height;

            context.fill(x, y, right, bottom, backgroundColor);
            context.fill(x, y, right, y + 1, COLOR_BORDER);
            context.fill(x, bottom - 1, right, bottom, COLOR_BORDER);
            context.fill(x, y, x + 1, bottom, COLOR_BORDER);
            context.fill(right - 1, y, right, bottom, COLOR_BORDER);

            int textColor = this.active ? COLOR_TITLE : 0xFF666666;
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.font != null) {
                context.centeredText(
                    client.font,
                    this.getMessage(),
                    x + this.width / 2,
                    y + (this.height - 8) / 2,
                    textColor
                );
            }
        }

        @Override
        public void onPress(InputWithModifiers ctx) {
            if (!this.active || this.onPress == null) {
                return;
            }

            this.onPress.run();
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }

    private static void configureSuggestionBehavior(EditBox field, String placeholder) {
        field.setResponder(text -> field.setSuggestion(text == null || text.isEmpty() ? placeholder : ""));
        refreshSuggestionForCurrentText(field, placeholder);
    }

    private static void refreshSuggestionForCurrentText(EditBox field, String placeholder) {
        String text = field.getValue();
        field.setSuggestion(text == null || text.isEmpty() ? placeholder : "");
    }

    private static final class CenteredTextFieldWidget extends EditBox {
        private final Font textRenderer;

        public CenteredTextFieldWidget(Font textRenderer, int x, int y, int width, int height, Component text) {
            super(textRenderer, x, y, width, height, text);
            this.textRenderer = textRenderer;
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            int verticalOffset = (this.getHeight() - this.textRenderer.lineHeight) / 2;
            int originalY = this.getY();
            this.setY(originalY + verticalOffset);
            super.extractWidgetRenderState(context, mouseX, mouseY, delta);
            this.setY(originalY);
        }
    }
}
