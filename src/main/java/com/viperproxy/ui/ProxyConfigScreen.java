package com.viperproxy.ui;

import com.viperproxy.ProxyRuntimeHolder;
import com.viperproxy.config.ProxyConfig;
import com.viperproxy.proxy.ProxyRuntime;
import com.viperproxy.proxy.ProxyStatus;
import com.viperproxy.proxy.ProxyType;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;

public final class ProxyConfigScreen extends Screen {
    private static final int LEFT_PANEL_WIDTH = 380;
    private static final int RIGHT_PANEL_WIDTH = 220;
    private static final int PANEL_GAP = 8;
    private static final int PADDING = 10;
    private static final int ROW_HEIGHT = 20;
    private static final int LIST_ROW_HEIGHT = 20;
    private static final int DELETE_BUTTON_SIZE = 12;

    private static final Identifier LOGO_TEXTURE = Identifier.of("viperproxy", "textures/gui/logo.png");
    private static final int LOGO_DISPLAY_SIZE = 64;
    private static final int LOGO_TEXTURE_SIZE = 2000;

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

    private TextFieldWidget hostField;
    private TextFieldWidget portField;
    private TextFieldWidget usernameField;
    private TextFieldWidget passwordField;

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
        super(Text.literal("Viper Proxy Configuration"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int totalWidth = LEFT_PANEL_WIDTH + PANEL_GAP + RIGHT_PANEL_WIDTH;
        int lineHeight = this.textRenderer.fontHeight;
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

        this.hostField = new CenteredTextFieldWidget(this.textRenderer, contentX, this.hostFieldY, contentWidth, ROW_HEIGHT, Text.literal("Host"));
        this.hostField.setMaxLength(255);
        this.hostField.setText(config.host);
        this.hostField.setDrawsBackground(false);
        configureSuggestionBehavior(this.hostField, "Host / IP");

        this.portField = new CenteredTextFieldWidget(this.textRenderer, contentX, this.portFieldY, contentWidth, ROW_HEIGHT, Text.literal("Port"));
        this.portField.setMaxLength(5);
        this.portField.setText(Integer.toString(config.port));
        this.portField.setDrawsBackground(false);
        configureSuggestionBehavior(this.portField, "Port");

        int sideBySideWidth = (contentWidth - 6) / 2;
        this.usernameField = new CenteredTextFieldWidget(this.textRenderer, contentX, this.credentialsFieldY, sideBySideWidth, ROW_HEIGHT, Text.literal("Username"));
        this.usernameField.setMaxLength(128);
        this.usernameField.setText(config.username);
        this.usernameField.setDrawsBackground(false);
        configureSuggestionBehavior(this.usernameField, "Username (optional)");

        this.passwordField = new CenteredTextFieldWidget(
            this.textRenderer,
            contentX + sideBySideWidth + 6,
            this.credentialsFieldY,
            sideBySideWidth,
            ROW_HEIGHT,
            Text.literal("Password")
        );
        this.passwordField.setMaxLength(128);
        this.passwordField.setText(config.password);
        this.passwordField.setDrawsBackground(false);
        this.passwordField.addFormatter((text, firstCharacterIndex) ->
            OrderedText.styledForwardsVisitedString("\u2022".repeat(text.length()), Style.EMPTY)
        );
        configureSuggestionBehavior(this.passwordField, "Password (optional)");

        this.addDrawableChild(this.hostField);
        this.addDrawableChild(this.portField);
        this.addDrawableChild(this.usernameField);
        this.addDrawableChild(this.passwordField);

        int profileButtonWidth = (int) Math.floor(contentWidth * 0.60);
        int newProfileButtonWidth = contentWidth - profileButtonWidth - 6;

        this.profileButton = this.addDrawableChild(
            buildStyledButton(contentX, this.profileRowY, profileButtonWidth, ROW_HEIGHT, profileButtonText(), this::cycleProfile, true)
        );

        this.newProfileButton = this.addDrawableChild(
            buildStyledButton(
                contentX + profileButtonWidth + 6,
                this.profileRowY,
                newProfileButtonWidth,
                ROW_HEIGHT,
                Text.literal("NEW PROFILE"),
                this::newProfile,
                false
            )
        );

        this.typeButton = this.addDrawableChild(
            buildStyledButton(contentX, this.typeButtonY, contentWidth, ROW_HEIGHT, typeButtonText(), this::cycleProxyType, false)
        );

        this.applyButton = this.addDrawableChild(
            buildStyledButton(contentX, this.applyButtonY, contentWidth, ROW_HEIGHT, Text.literal("APPLY"), this::applyConfig, true)
        );

        int bottomButtonWidth = (contentWidth - 6) / 2;
        this.closeButton = this.addDrawableChild(
            buildStyledButton(contentX, this.bottomButtonsY, bottomButtonWidth, ROW_HEIGHT, Text.literal("CLOSE"), this::close, false)
        );

        this.resetButton = this.addDrawableChild(
            buildStyledButton(
                contentX + bottomButtonWidth + 6,
                this.bottomButtonsY,
                bottomButtonWidth,
                ROW_HEIGHT,
                Text.literal("RESET"),
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

        currentY += LOGO_DISPLAY_SIZE + 8; // logo
        currentY += 2 + 8; // title separator
        currentY += lineHeight + 2; // profile label
        currentY += ROW_HEIGHT + 8; // profile row
        currentY += lineHeight + 2; // host label
        currentY += ROW_HEIGHT + 8; // host field
        currentY += lineHeight + 2; // port label
        currentY += ROW_HEIGHT + 8; // port field
        currentY += lineHeight + 2; // credentials label
        currentY += ROW_HEIGHT + 8; // credentials fields
        currentY += lineHeight + 2; // type label
        currentY += ROW_HEIGHT + 4; // type button
        currentY += 2 + 6; // separator
        currentY += lineHeight + 2; // status
        currentY += lineHeight + 2; // ip
        currentY += lineHeight + 6; // latency
        currentY += 2 + 6; // separator
        currentY += ROW_HEIGHT + 4; // apply
        currentY += ROW_HEIGHT; // close/reset

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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int contentX = this.leftPanelX + PADDING;
        int contentWidth = LEFT_PANEL_WIDTH - (PADDING * 2);
        int panelBottom = this.panelY + this.panelHeight;

        renderPanels(context);

        int logoX = this.leftPanelX + (LEFT_PANEL_WIDTH - LOGO_DISPLAY_SIZE) / 2;
        context.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            LOGO_TEXTURE,
            logoX, this.titleY,
            0, 0,
            LOGO_DISPLAY_SIZE, LOGO_DISPLAY_SIZE,
            LOGO_DISPLAY_SIZE, LOGO_DISPLAY_SIZE
        );
        context.fill(this.leftPanelX + 8, this.titleSeparatorY, this.leftPanelX + LEFT_PANEL_WIDTH - 8, this.titleSeparatorY + 2, COLOR_BORDER);

        context.drawTextWithShadow(this.textRenderer, Text.literal("PROFILE"), contentX, this.profileLabelY, COLOR_LABEL);
        context.drawTextWithShadow(this.textRenderer, Text.literal("HOST"), contentX, this.hostLabelY, COLOR_LABEL);
        context.drawTextWithShadow(this.textRenderer, Text.literal("PORT"), contentX, this.portLabelY, COLOR_LABEL);
        context.drawTextWithShadow(this.textRenderer, Text.literal("USERNAME / PASSWORD"), contentX, this.credentialsLabelY, COLOR_LABEL);
        context.drawTextWithShadow(this.textRenderer, Text.literal("TYPE"), contentX, this.typeLabelY, COLOR_LABEL);

        drawFieldChrome(context, this.hostField);
        drawFieldChrome(context, this.portField);
        drawFieldChrome(context, this.usernameField);
        drawFieldChrome(context, this.passwordField);

        this.hostField.render(context, mouseX, mouseY, delta);
        this.portField.render(context, mouseX, mouseY, delta);
        this.usernameField.render(context, mouseX, mouseY, delta);
        this.passwordField.render(context, mouseX, mouseY, delta);

        this.profileButton.render(context, mouseX, mouseY, delta);
        this.newProfileButton.render(context, mouseX, mouseY, delta);
        this.typeButton.render(context, mouseX, mouseY, delta);
        this.applyButton.render(context, mouseX, mouseY, delta);
        this.closeButton.render(context, mouseX, mouseY, delta);
        this.resetButton.render(context, mouseX, mouseY, delta);

        context.fill(contentX, this.statusSeparatorY, contentX + contentWidth, this.statusSeparatorY + 2, COLOR_BORDER);
        context.fill(contentX, this.actionSeparatorY, contentX + contentWidth, this.actionSeparatorY + 2, COLOR_BORDER);

        renderStatusBlock(context, contentX);
        renderStoredProfilesList(context, mouseX, mouseY);

        if (!this.localError.isBlank()) {
            context.drawTextWithShadow(
                this.textRenderer,
                Text.literal(this.localError),
                contentX,
                Math.min(panelBottom - 12 - this.textRenderer.fontHeight, this.latencyLineY + this.textRenderer.fontHeight + 2),
                COLOR_STATUS_ERROR
            );
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
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
        String host = this.hostField.getText().trim();
        String portText = this.portField.getText().trim();

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
        config.username = this.usernameField.getText().trim();
        config.password = this.passwordField.getText();
        config.type = this.selectedType;
        return config;
    }

    private void loadFromRuntime() {
        ProxyRuntime runtime = runtime();
        ProxyConfig config = runtime.getActiveConfigCopy().normalized();

        this.hostField.setText(config.host == null ? "" : config.host);
        this.portField.setText(config.enabled || !config.host.isBlank() ? Integer.toString(config.port) : "");
        this.usernameField.setText(config.username == null ? "" : config.username);
        this.passwordField.setText(config.password == null ? "" : config.password);
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

    private Text profileButtonText() {
        ProxyRuntime runtime = runtime();
        return Text.literal("ACTIVE: " + runtime.getActiveProfileName());
    }

    private Text typeButtonText() {
        return Text.literal("Type: ").append(this.selectedType.asText());
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
    public boolean mouseClicked(Click click, boolean doubleClick) {
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

    private void renderPanels(DrawContext context) {
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

    private void renderStatusBlock(DrawContext context, int contentX) {
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

        context.drawTextWithShadow(this.textRenderer, Text.literal(statusText), contentX, this.statusLineY, statusColor);
        context.drawTextWithShadow(this.textRenderer, Text.literal(ipText), contentX, this.ipLineY, COLOR_LABEL);
        context.drawTextWithShadow(this.textRenderer, Text.literal(latencyText), contentX, this.latencyLineY, COLOR_LABEL);
    }

    private void renderStoredProfilesList(DrawContext context, int mouseX, int mouseY) {
        int titleY = this.panelY + 14;
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("STORED PROXIES"),
            this.rightPanelX + RIGHT_PANEL_WIDTH / 2,
            titleY,
            COLOR_TITLE
        );
        context.fill(this.rightPanelX + 8, this.panelY + 26, this.rightPanelX + RIGHT_PANEL_WIDTH - 8, this.panelY + 27, COLOR_BORDER);

        List<String> profiles = runtime().getProfileNames();
        this.profileListScroll = clampScroll(this.profileListScroll, profiles);

        if (profiles.isEmpty()) {
            context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("No profiles saved"),
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

            context.drawTextWithShadow(this.textRenderer, Text.literal(profiles.get(profileIndex)), listX + 6, rowY + 6, COLOR_LABEL);

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
                int xCenterY = deleteBtnY + (DELETE_BUTTON_SIZE - this.textRenderer.fontHeight) / 2;
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("x"), xCenterX, xCenterY, xColor);
            }
        }
        context.disableScissor();
    }

    private void drawFieldChrome(DrawContext context, TextFieldWidget field) {
        int x1 = field.getX() - 1;
        int y1 = field.getY() - 1;
        int x2 = field.getX() + field.getWidth() + 1;
        int y2 = field.getY() + field.getHeight() + 1;

        context.fill(x1, y1, x2, y2, 0xFF1A1A22);
        drawPanelBorder(context, x1, y1, x2 - x1, y2 - y1, COLOR_BORDER);
    }

    private static void drawPanelBorder(DrawContext context, int x, int y, int width, int height, int color) {
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

    private StyledButtonWidget buildStyledButton(int x, int y, int w, int h, Text label, Runnable action, boolean activeAccent) {
        return new StyledButtonWidget(x, y, w, h, label, action, activeAccent);
    }

    private static final class StyledButtonWidget extends ClickableWidget {
        private final boolean activeAccent;
        private final Runnable onPress;

        protected StyledButtonWidget(int x, int y, int width, int height, Text message, Runnable onPress, boolean activeAccent) {
            super(x, y, width, height, message);
            this.activeAccent = activeAccent;
            this.onPress = onPress;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
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
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.textRenderer != null) {
                context.drawCenteredTextWithShadow(
                    client.textRenderer,
                    this.getMessage(),
                    x + this.width / 2,
                    y + (this.height - 8) / 2,
                    textColor
                );
            }
        }

        @Override
        public void onClick(Click click, boolean doubleClick) {
            if (!this.active || this.onPress == null) {
                return;
            }

            this.onPress.run();
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            this.appendDefaultNarrations(builder);
        }
    }

    private static void configureSuggestionBehavior(TextFieldWidget field, String placeholder) {
        field.setChangedListener(text -> field.setSuggestion(text == null || text.isEmpty() ? placeholder : ""));
        refreshSuggestionForCurrentText(field, placeholder);
    }

    private static void refreshSuggestionForCurrentText(TextFieldWidget field, String placeholder) {
        String text = field.getText();
        field.setSuggestion(text == null || text.isEmpty() ? placeholder : "");
    }

    private static final class CenteredTextFieldWidget extends TextFieldWidget {
        private final TextRenderer textRenderer;

        public CenteredTextFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text text) {
            super(textRenderer, x, y, width, height, text);
            this.textRenderer = textRenderer;
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int verticalOffset = (this.getHeight() - this.textRenderer.fontHeight) / 2;
            int originalY = this.getY();
            this.setY(originalY + verticalOffset);
            super.renderWidget(context, mouseX, mouseY, delta);
            this.setY(originalY);
        }
    }
}
