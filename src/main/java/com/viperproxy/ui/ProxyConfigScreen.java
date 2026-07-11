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
    private static final int PANEL_MAX_WIDTH = 700;
    private static final int PANEL_MAX_HEIGHT = 360;
    private static final int HEADER_HEIGHT = 36;
    private static final int FOOTER_HEIGHT = 36;
    private static final int FIELD_HEIGHT = 22;
    private static final int BUTTON_HEIGHT = 22;
    private static final int ROW_HEIGHT = 26;
    private static final int ROW_GAP = 3;

    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("viperproxy", "textures/gui/logo.png");
    private static final int LOGO_TEX_SIZE = 2000;
    private static final int LOGO_X = 59;
    private static final int LOGO_Y = 603;
    private static final int LOGO_W = 1483;
    private static final int LOGO_H = 395;

    private static final int OVERLAY = 0xB9000000;
    private static final int PANEL = 0xF21B1C20;
    private static final int SIDEBAR = 0xF316171B;
    private static final int SURFACE = 0xFF232429;
    private static final int SURFACE_HOVER = 0xFF2B2C32;
    private static final int SURFACE_SELECTED = 0xFF302126;
    private static final int FIELD = 0xFF1B1C21;
    private static final int BORDER = 0xFF36373E;
    private static final int BORDER_SOFT = 0xFF2D2E34;
    private static final int TEXT = 0xFFF1F1F2;
    private static final int MUTED = 0xFFAAAAB0;
    private static final int DIM = 0xFF75767E;
    private static final int ACCENT = 0xFFE84F5B;
    private static final int ACCENT_DARK = 0xFF32171C;
    private static final int SUCCESS = 0xFF78E35A;
    private static final int WARNING = 0xFFFFC857;
    private static final int ERROR = 0xFFFF6B70;
    private static final int DISABLED = 0xFF7B858F;

    private final Screen parent;
    private Page page = Page.CONNECTION;
    private ProxyType selectedType = ProxyType.SOCKS5;
    private String localError = "";
    private int profileScroll;

    private EditBox hostField;
    private EditBox portField;
    private EditBox usernameField;
    private EditBox passwordField;
    private EditBox profileNameField;

    private FlatButton connectionTab;
    private FlatButton authTab;
    private FlatButton profilesTab;
    private FlatButton socks5Button;
    private FlatButton httpButton;
    private FlatButton httpsButton;
    private FlatButton saveNameButton;
    private FlatButton newProfileButton;
    private FlatButton deleteProfileButton;
    private FlatButton closeButton;
    private FlatButton resetButton;
    private FlatButton applyButton;

    private int panelX;
    private int panelY;
    private int panelRight;
    private int panelBottom;
    private int panelWidth;
    private int panelHeight;
    private int sidebarWidth;
    private int contentX;
    private int contentRight;
    private int contentTop;
    private int contentBottom;
    private int profileListY;
    private int profileListBottom;

    public ProxyConfigScreen(Screen parent) {
        super(Component.literal("Viper Proxy"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout();
        ProxyRuntime runtime = runtime();
        ProxyConfig config = runtime.getActiveConfigCopy().normalized();
        this.selectedType = config.type;

        int contentWidth = this.contentRight - this.contentX;
        int hostCardY = this.contentTop + 30;
        int hostFieldY = hostCardY + 32;
        int portWidth = Math.min(86, Math.max(62, contentWidth / 4));
        this.hostField = field(this.contentX + 10, hostFieldY, contentWidth - portWidth - 26, 255, config.host, "Proxy hostname or IP");
        this.portField = field(this.contentRight - portWidth - 10, hostFieldY, portWidth, 5,
            config.enabled || !config.host.isBlank() ? Integer.toString(config.port) : "", "Port");

        int protocolY = hostCardY + 70;
        int segmentGap = 6;
        int segmentWidth = (contentWidth - 20 - segmentGap * 2) / 3;
        this.socks5Button = button(this.contentX + 10, protocolY + 22, segmentWidth, BUTTON_HEIGHT, "SOCKS5", () -> selectType(ProxyType.SOCKS5), Tone.SEGMENT);
        this.httpButton = button(this.contentX + 10 + segmentWidth + segmentGap, protocolY + 22, segmentWidth, BUTTON_HEIGHT, "HTTP", () -> selectType(ProxyType.HTTP), Tone.SEGMENT);
        this.httpsButton = button(this.contentRight - 10 - segmentWidth, protocolY + 22, segmentWidth, BUTTON_HEIGHT, "HTTPS", () -> selectType(ProxyType.HTTPS), Tone.SEGMENT);

        int authCardY = this.contentTop + 30;
        int credentialGap = 8;
        int credentialWidth = (contentWidth - 28) / 2;
        this.usernameField = field(this.contentX + 10, authCardY + 32, credentialWidth, 128, config.username, "Optional username");
        this.passwordField = field(this.contentRight - 10 - credentialWidth, authCardY + 32, credentialWidth, 128, config.password, "Optional password");
        this.passwordField.addFormatter((text, firstCharacterIndex) ->
            FormattedCharSequence.forward("•".repeat(text.length()), Style.EMPTY));

        int profileCardY = this.contentTop + 30;
        int saveWidth = 74;
        this.profileNameField = field(this.contentX + 10, profileCardY + 24, contentWidth - saveWidth - 26, 48,
            runtime.getActiveProfileName(), "Profile name");
        this.saveNameButton = button(this.contentRight - saveWidth - 10, profileCardY + 24, saveWidth, FIELD_HEIGHT,
            "RENAME", this::renameProfile, Tone.SECONDARY);

        this.profileListY = profileCardY + 58;
        int profileActionsY = this.contentBottom - BUTTON_HEIGHT;
        this.profileListBottom = profileActionsY - 7;
        int halfAction = (contentWidth - 6) / 2;
        this.newProfileButton = button(this.contentX, profileActionsY, halfAction, BUTTON_HEIGHT, "NEW PROFILE", this::newProfile, Tone.SECONDARY);
        this.deleteProfileButton = button(this.contentRight - halfAction, profileActionsY, halfAction, BUTTON_HEIGHT, "DELETE ACTIVE", this::deleteProfile, Tone.DANGER);

        int navX = this.panelX + 10;
        int navWidth = this.sidebarWidth - 20;
        int navY = this.panelY + HEADER_HEIGHT + 14;
        this.connectionTab = button(navX, navY, navWidth, 24, "CONNECTION", () -> setPage(Page.CONNECTION), Tone.NAV);
        this.authTab = button(navX, navY + 30, navWidth, 24, "AUTHENTICATION", () -> setPage(Page.AUTHENTICATION), Tone.NAV);
        this.profilesTab = button(navX, navY + 60, navWidth, 24, "PROFILES", () -> setPage(Page.PROFILES), Tone.NAV);

        int footerY = this.panelBottom - FOOTER_HEIGHT + 7;
        int primaryWidth = 112;
        this.closeButton = button(this.contentX, footerY, 66, BUTTON_HEIGHT, "CLOSE", this::onClose, Tone.SECONDARY);
        this.resetButton = button(this.contentX + 72, footerY, 76, BUTTON_HEIGHT, "RESET", this::resetForm, Tone.SECONDARY);
        this.applyButton = button(this.contentRight - primaryWidth, footerY, primaryWidth, BUTTON_HEIGHT, "SAVE & TEST", this::applyConfig, Tone.PRIMARY);

        refreshControls();
        setPage(this.page);
        this.setInitialFocus(this.hostField);
    }

    private void layout() {
        this.panelWidth = Math.min(PANEL_MAX_WIDTH, Math.max(470, this.width - 16));
        this.panelWidth = Math.min(this.panelWidth, this.width - 4);
        this.panelHeight = Math.min(PANEL_MAX_HEIGHT, Math.max(238, this.height - 16));
        this.panelHeight = Math.min(this.panelHeight, this.height - 4);
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;
        this.panelRight = this.panelX + this.panelWidth;
        this.panelBottom = this.panelY + this.panelHeight;
        this.sidebarWidth = Math.max(126, Math.min(154, this.panelWidth / 4));
        this.contentX = this.panelX + this.sidebarWidth + 14;
        this.contentRight = this.panelRight - 14;
        this.contentTop = this.panelY + HEADER_HEIGHT + 10;
        this.contentBottom = this.panelBottom - FOOTER_HEIGHT - 6;
    }

    private EditBox field(int x, int y, int width, int maxLength, String value, String placeholder) {
        EditBox field = new CenteredEditBox(this.font, x, y, width, FIELD_HEIGHT, Component.literal(placeholder));
        field.setMaxLength(maxLength);
        field.setValue(value == null ? "" : value);
        field.setBordered(false);
        configureSuggestion(field, placeholder);
        return this.addRenderableWidget(field);
    }

    private FlatButton button(int x, int y, int width, int height, String text, Runnable action, Tone tone) {
        return this.addRenderableWidget(new FlatButton(x, y, width, height, Component.literal(text), action, tone));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        renderShell(graphics);
        renderHeader(graphics);
        renderSidebar(graphics);
        renderPage(graphics, mouseX, mouseY);
        renderVisibleWidgets(graphics, mouseX, mouseY, delta);
    }

    private void renderShell(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, this.width, this.height, OVERLAY);
        graphics.fill(this.panelX, this.panelY, this.panelRight, this.panelBottom, PANEL);
        border(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, BORDER);
        graphics.fill(this.panelX + 1, this.panelY + HEADER_HEIGHT, this.panelX + this.sidebarWidth, this.panelBottom - 1, SIDEBAR);
        graphics.fill(this.panelX, this.panelY + HEADER_HEIGHT - 1, this.panelRight, this.panelY + HEADER_HEIGHT, BORDER);
        graphics.fill(this.panelX + this.sidebarWidth, this.panelY + HEADER_HEIGHT, this.panelX + this.sidebarWidth + 1, this.panelBottom, BORDER);
        graphics.fill(this.panelX + this.sidebarWidth, this.panelBottom - FOOTER_HEIGHT, this.panelRight, this.panelBottom - FOOTER_HEIGHT + 1, BORDER);
    }

    private void renderHeader(GuiGraphicsExtractor graphics) {
        int logoW = 68;
        int logoH = 18;
        int logoX = this.panelX + 12;
        int logoY = this.panelY + 9;
        graphics.fill(logoX - 3, logoY - 3, logoX + logoW + 3, logoY + logoH + 3, ACCENT);
        graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, logoX, logoY, LOGO_X, LOGO_Y,
            logoW, logoH, LOGO_W, LOGO_H, LOGO_TEX_SIZE, LOGO_TEX_SIZE);

        ProxyStatus status = runtime().getStatus();
        String label = switch (status) {
            case CONNECTED -> "CONNECTED";
            case CONNECTING -> "TESTING";
            case ERROR -> "ROUTE ERROR";
            case DISABLED -> "DISABLED";
        };
        int color = statusColor(status);
        int chipW = Math.max(76, this.font.width(label) + 16);
        int chipX = this.panelRight - chipW - 12;
        int chipY = this.panelY + 8;
        graphics.fill(chipX, chipY, chipX + chipW, chipY + 20, SURFACE);
        border(graphics, chipX, chipY, chipW, 20, color);
        graphics.centeredText(this.font, Component.literal(label), chipX + chipW / 2, chipY + 6, color);
    }

    private void renderSidebar(GuiGraphicsExtractor graphics) {
        graphics.text(this.font, Component.literal("SETTINGS"), this.panelX + 12, this.panelY + HEADER_HEIGHT + 3, DIM, false);
        String active = fit(runtime().getActiveProfileName(), this.sidebarWidth - 24);
        graphics.text(this.font, Component.literal(active), this.panelX + 12, this.panelBottom - 17, MUTED, false);
    }

    private void renderPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        switch (this.page) {
            case CONNECTION -> renderConnectionPage(graphics);
            case AUTHENTICATION -> renderAuthenticationPage(graphics);
            case PROFILES -> renderProfilesPage(graphics, mouseX, mouseY);
        }
        if (!this.localError.isBlank()) {
            graphics.text(this.font, Component.literal(fit(this.localError, this.contentRight - this.contentX - 160)),
                this.contentX + 154, this.panelBottom - 23, ERROR, false);
        }
    }

    private void renderPageTitle(GuiGraphicsExtractor graphics, String title, String subtitle) {
        graphics.text(this.font, Component.literal(title), this.contentX, this.contentTop, TEXT, true);
        graphics.text(this.font, Component.literal(fit(subtitle, this.contentRight - this.contentX)), this.contentX, this.contentTop + 13, MUTED, false);
    }

    private void renderConnectionPage(GuiGraphicsExtractor graphics) {
        renderPageTitle(graphics, "Connection", "Choose the proxy endpoint and protocol used for Minecraft traffic.");
        int cardY = this.contentTop + 30;
        card(graphics, cardY, 64);
        graphics.text(this.font, Component.literal("ENDPOINT"), this.contentX + 10, cardY + 8, DIM, false);
        graphics.text(this.font, Component.literal("HOST / IP"), this.hostField.getX(), cardY + 19, MUTED, false);
        graphics.text(this.font, Component.literal("PORT"), this.portField.getX(), cardY + 19, MUTED, false);
        int protocolY = cardY + 70;
        card(graphics, protocolY, 50);
        graphics.text(this.font, Component.literal("PROTOCOL"), this.contentX + 10, protocolY + 8, DIM, false);
    }

    private void renderAuthenticationPage(GuiGraphicsExtractor graphics) {
        renderPageTitle(graphics, "Authentication", "Credentials are optional and stored with the active profile.");
        int cardY = this.contentTop + 30;
        card(graphics, cardY, 64);
        graphics.text(this.font, Component.literal("PROXY CREDENTIALS"), this.contentX + 10, cardY + 8, DIM, false);
        graphics.text(this.font, Component.literal("USERNAME"), this.usernameField.getX(), cardY + 19, MUTED, false);
        graphics.text(this.font, Component.literal("PASSWORD"), this.passwordField.getX(), cardY + 19, MUTED, false);
    }

    private void renderProfilesPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        renderPageTitle(graphics, "Profiles", "Create, rename, and switch between saved proxy configurations.");
        int renameY = this.contentTop + 30;
        card(graphics, renameY, 52);
        graphics.text(this.font, Component.literal("ACTIVE PROFILE NAME"), this.contentX + 10, renameY + 8, DIM, false);

        List<String> profiles = runtime().getProfileNames();
        this.profileScroll = clampScroll(this.profileScroll, profiles);
        int activeIndex = runtime().getActiveProfileIndex();
        graphics.enableScissor(this.contentX, this.profileListY, this.contentRight, this.profileListBottom);
        int visible = visibleRows();
        for (int row = 0; row < visible; row++) {
            int index = this.profileScroll + row;
            if (index >= profiles.size()) break;
            int y = this.profileListY + row * (ROW_HEIGHT + ROW_GAP);
            boolean selected = index == activeIndex;
            boolean hovered = mouseX >= this.contentX && mouseX <= this.contentRight && mouseY >= y && mouseY < y + ROW_HEIGHT;
            graphics.fill(this.contentX, y, this.contentRight, y + ROW_HEIGHT,
                selected ? SURFACE_SELECTED : (hovered ? SURFACE_HOVER : SURFACE));
            if (selected) graphics.fill(this.contentX, y, this.contentX + 2, y + ROW_HEIGHT, ACCENT);
            graphics.text(this.font, Component.literal(fit(profiles.get(index), this.contentRight - this.contentX - 70)),
                this.contentX + 9, y + 8, selected ? TEXT : MUTED, false);
            if (selected) {
                graphics.text(this.font, Component.literal("ACTIVE"), this.contentRight - 43, y + 8, ACCENT, false);
            }
        }
        graphics.disableScissor();
    }

    private void card(GuiGraphicsExtractor graphics, int y, int height) {
        graphics.fill(this.contentX, y, this.contentRight, y + height, SURFACE);
        border(graphics, this.contentX, y, this.contentRight - this.contentX, height, BORDER_SOFT);
    }

    private void renderVisibleWidgets(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (this.page == Page.CONNECTION) {
            drawField(graphics, this.hostField, mouseX, mouseY, delta);
            drawField(graphics, this.portField, mouseX, mouseY, delta);
            this.socks5Button.extractRenderState(graphics, mouseX, mouseY, delta);
            this.httpButton.extractRenderState(graphics, mouseX, mouseY, delta);
            this.httpsButton.extractRenderState(graphics, mouseX, mouseY, delta);
        } else if (this.page == Page.AUTHENTICATION) {
            drawField(graphics, this.usernameField, mouseX, mouseY, delta);
            drawField(graphics, this.passwordField, mouseX, mouseY, delta);
        } else {
            drawField(graphics, this.profileNameField, mouseX, mouseY, delta);
            this.saveNameButton.extractRenderState(graphics, mouseX, mouseY, delta);
            this.newProfileButton.extractRenderState(graphics, mouseX, mouseY, delta);
            this.deleteProfileButton.extractRenderState(graphics, mouseX, mouseY, delta);
        }
        this.connectionTab.extractRenderState(graphics, mouseX, mouseY, delta);
        this.authTab.extractRenderState(graphics, mouseX, mouseY, delta);
        this.profilesTab.extractRenderState(graphics, mouseX, mouseY, delta);
        this.closeButton.extractRenderState(graphics, mouseX, mouseY, delta);
        this.resetButton.extractRenderState(graphics, mouseX, mouseY, delta);
        this.applyButton.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void drawField(GuiGraphicsExtractor graphics, EditBox field, int mouseX, int mouseY, float delta) {
        graphics.fill(field.getX(), field.getY(), field.getX() + field.getWidth(), field.getY() + field.getHeight(), FIELD);
        border(graphics, field.getX(), field.getY(), field.getWidth(), field.getHeight(), field.isFocused() ? ACCENT : BORDER);
        field.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void setPage(Page page) {
        this.page = page;
        boolean connection = page == Page.CONNECTION;
        boolean auth = page == Page.AUTHENTICATION;
        boolean profiles = page == Page.PROFILES;
        this.hostField.visible = connection;
        this.portField.visible = connection;
        this.socks5Button.visible = connection;
        this.httpButton.visible = connection;
        this.httpsButton.visible = connection;
        this.usernameField.visible = auth;
        this.passwordField.visible = auth;
        this.profileNameField.visible = profiles;
        this.saveNameButton.visible = profiles;
        this.newProfileButton.visible = profiles;
        this.deleteProfileButton.visible = profiles;
        this.connectionTab.setSelected(connection);
        this.authTab.setSelected(auth);
        this.profilesTab.setSelected(profiles);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (this.page == Page.PROFILES && click.button() == 0 && insideProfileList(click.x(), click.y())) {
            int row = (int) ((click.y() - this.profileListY) / (ROW_HEIGHT + ROW_GAP));
            int offset = (int) ((click.y() - this.profileListY) % (ROW_HEIGHT + ROW_GAP));
            int index = this.profileScroll + row;
            List<String> profiles = runtime().getProfileNames();
            if (offset < ROW_HEIGHT && index >= 0 && index < profiles.size()) {
                runtime().selectActiveProfile(index);
                loadFromRuntime();
                return true;
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.page == Page.PROFILES && insideProfileList(mouseX, mouseY)) {
            int max = maxScroll(runtime().getProfileNames());
            if (verticalAmount < 0) this.profileScroll = Math.min(max, this.profileScroll + 1);
            if (verticalAmount > 0) this.profileScroll = Math.max(0, this.profileScroll - 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean insideProfileList(double x, double y) {
        return x >= this.contentX && x <= this.contentRight && y >= this.profileListY && y < this.profileListBottom;
    }

    private int visibleRows() {
        return Math.max(1, (this.profileListBottom - this.profileListY) / (ROW_HEIGHT + ROW_GAP));
    }

    private int maxScroll(List<String> profiles) {
        return Math.max(0, profiles.size() - visibleRows());
    }

    private int clampScroll(int scroll, List<String> profiles) {
        return Math.max(0, Math.min(scroll, maxScroll(profiles)));
    }

    private void newProfile() {
        runtime().createProfileFromUi("", new ProxyConfig());
        this.profileScroll = maxScroll(runtime().getProfileNames());
        loadFromRuntime();
    }

    private void deleteProfile() {
        ProxyRuntime runtime = runtime();
        if (runtime.getProfileCount() <= 1) return;
        runtime.deleteProfile(runtime.getActiveProfileIndex());
        this.profileScroll = clampScroll(this.profileScroll, runtime.getProfileNames());
        loadFromRuntime();
    }

    private void renameProfile() {
        runtime().renameActiveProfile(this.profileNameField.getValue());
        loadFromRuntime();
    }

    private void selectType(ProxyType type) {
        this.selectedType = type;
        refreshControls();
    }

    private void applyConfig() {
        this.localError = "";
        ProxyConfig config = parseForm();
        if (config == null) return;
        runtime().applyFromUi(config, this.profileNameField.getValue());
        loadFromRuntime();
    }

    private ProxyConfig parseForm() {
        String host = this.hostField.getValue().trim();
        int port;
        try {
            port = Integer.parseInt(this.portField.getValue().trim());
        } catch (NumberFormatException exception) {
            this.localError = "Port must be 1–65535.";
            setPage(Page.CONNECTION);
            return null;
        }
        if (host.isBlank() || port < 1 || port > 65535) {
            this.localError = "Host and port are required.";
            setPage(Page.CONNECTION);
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

    private void resetForm() {
        this.localError = "";
        loadFromRuntime();
    }

    private void loadFromRuntime() {
        ProxyRuntime runtime = runtime();
        ProxyConfig config = runtime.getActiveConfigCopy().normalized();
        this.hostField.setValue(config.host == null ? "" : config.host);
        this.portField.setValue(config.enabled || !config.host.isBlank() ? Integer.toString(config.port) : "");
        this.usernameField.setValue(config.username == null ? "" : config.username);
        this.passwordField.setValue(config.password == null ? "" : config.password);
        this.profileNameField.setValue(runtime.getActiveProfileName());
        configureSuggestion(this.hostField, "Proxy hostname or IP");
        configureSuggestion(this.portField, "Port");
        configureSuggestion(this.usernameField, "Optional username");
        configureSuggestion(this.passwordField, "Optional password");
        configureSuggestion(this.profileNameField, "Profile name");
        this.selectedType = config.type;
        refreshControls();
    }

    private void refreshControls() {
        if (this.socks5Button != null) {
            this.socks5Button.setSelected(this.selectedType == ProxyType.SOCKS5);
            this.httpButton.setSelected(this.selectedType == ProxyType.HTTP);
            this.httpsButton.setSelected(this.selectedType == ProxyType.HTTPS);
        }
        if (this.deleteProfileButton != null) this.deleteProfileButton.active = runtime().getProfileCount() > 1;
    }

    private ProxyRuntime runtime() {
        return ProxyRuntimeHolder.getRequiredRuntime();
    }

    private int statusColor(ProxyStatus status) {
        return switch (status) {
            case CONNECTED -> SUCCESS;
            case CONNECTING -> WARNING;
            case ERROR -> ERROR;
            case DISABLED -> DISABLED;
        };
    }

    private String fit(String value, int width) {
        if (value == null || value.isBlank()) return "—";
        if (this.font.width(value) <= width) return value;
        return this.font.plainSubstrByWidth(value, Math.max(0, width - this.font.width("..."))) + "...";
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreenAndShow(this.parent);
    }

    private static void configureSuggestion(EditBox field, String placeholder) {
        field.setResponder(text -> field.setSuggestion(text == null || text.isEmpty() ? placeholder : ""));
        String text = field.getValue();
        field.setSuggestion(text == null || text.isEmpty() ? placeholder : "");
    }

    private static void border(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private enum Page { CONNECTION, AUTHENTICATION, PROFILES }
    private enum Tone { PRIMARY, SECONDARY, SEGMENT, NAV, DANGER }

    private static final class FlatButton extends AbstractButton {
        private final Runnable action;
        private final Tone tone;
        private boolean selected;

        private FlatButton(int x, int y, int width, int height, Component message, Runnable action, Tone tone) {
            super(x, y, width, height, message);
            this.action = action;
            this.tone = tone;
        }

        private void setSelected(boolean selected) {
            this.selected = selected;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            int background;
            int outline;
            int textColor;
            if (!this.active) {
                background = FIELD;
                outline = BORDER_SOFT;
                textColor = DIM;
            } else if (this.tone == Tone.PRIMARY) {
                background = isHovered() ? 0xFFF05E68 : ACCENT;
                outline = background;
                textColor = ACCENT_DARK;
            } else if (this.selected) {
                background = SURFACE_SELECTED;
                outline = ACCENT;
                textColor = ACCENT;
            } else if (this.tone == Tone.DANGER) {
                background = isHovered() ? 0xFF2B191D : FIELD;
                outline = isHovered() ? ERROR : BORDER;
                textColor = isHovered() ? ERROR : MUTED;
            } else {
                background = isHovered() ? SURFACE_HOVER : FIELD;
                outline = this.tone == Tone.NAV ? BORDER_SOFT : BORDER;
                textColor = isHovered() ? TEXT : MUTED;
            }
            graphics.fill(getX(), getY(), getX() + this.width, getY() + this.height, background);
            border(graphics, getX(), getY(), this.width, this.height, outline);
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.font != null) {
                graphics.centeredText(client.font, getMessage(), getX() + this.width / 2,
                    getY() + (this.height - client.font.lineHeight) / 2, textColor);
            }
        }

        @Override
        public void onPress(InputWithModifiers context) {
            if (this.active && this.action != null) this.action.run();
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    private static final class CenteredEditBox extends EditBox {
        private final Font font;

        private CenteredEditBox(Font font, int x, int y, int width, int height, Component message) {
            super(font, x, y, width, height, message);
            this.font = font;
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            int originalY = getY();
            setY(originalY + Math.max(0, (getHeight() - this.font.lineHeight) / 2 - 1));
            super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
            setY(originalY);
        }
    }
}
