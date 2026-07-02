package vesence.ui.clickgui.compact;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.loot.v3.FabricLootPoolBuilder;
import net.minecraft.client.MinecraftClient;
import vesence.Vesence;
import vesence.module.api.Category;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.impl.misc.ClickGui;
import vesence.renderengine.render.Renderer2D;
import vesence.ui.clickgui.GuiClient;
import vesence.ui.clickgui.GuiScreen;
import vesence.ui.clickgui.component.render.ClickRippleEffect;
import vesence.ui.clickgui.component.render.GuiRenderModePopup;
import vesence.ui.clickgui.compact.setting.CompactGuiRenderSetting;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.text.ColorFormat;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class CompactGuiRender {

    private static final int TEXT_COLOR = ColorUtil.getColor(255, 255, 255, 220);
    private static final int SUBTEXT_COLOR = ColorUtil.getColor(180, 180, 180, 180);
    private static final int DIVIDER_COLOR = ColorUtil.getColor(255, 255, 255, 20);
    private static final int SEARCH_BG = ColorUtil.getColor(255, 255, 255, 12);
    private static final int SEARCH_ACTIVE_BG = ColorUtil.getColor(255, 255, 255, 22);
    private static final int LEFT_PANEL_BG = ColorUtil.getColor(15,255);
    private static final int RIGHT_PANEL_BG = ColorUtil.getColor(15,255);

    private static final float CPAD = 4;
    private static final int COLS = 2;

    private static final int THEME_COLS = 3;
    private static final float THEME_CELL_H = 26f;
    private static final float THEME_CELL_GAP = 6f;
    private static final float THEME_ROW_GAP = 6f;

    static float[] themeCellRect(int i, float cX, float cY, float cW, float scrollOff) {
        float cellW = (cW - THEME_CELL_GAP * (THEME_COLS - 1)) / THEME_COLS;
        int col = i % THEME_COLS;
        int row = i / THEME_COLS;
        float cellX = cX + col * (cellW + THEME_CELL_GAP);
        float cellY = cY + row * (THEME_CELL_H + THEME_ROW_GAP) + scrollOff;
        return new float[]{cellX, cellY, cellW, THEME_CELL_H};
    }

    public static List<Module> getCurrentModules() {
        if (!CompactGuiScreen.searchText.isEmpty()) {
            return CompactGuiScreen.getAllModulesForSearch();
        }
        return CompactGuiScreen.getModulesForCategory(CompactGuiScreen.selectedCategory);
    }

    private static float[] getRightPanelBounds(float gx, float gy, float gh) {
        float px = gx + CompactGuiScreen.LEFT_PANEL_W + CompactGuiScreen.LEFT_PANEL_GAP * 2;
        float py = gy + CompactGuiScreen.LEFT_PANEL_GAP;
        float pw = CompactGuiScreen.RIGHT_PANEL_W;
        float ph = gh - CompactGuiScreen.LEFT_PANEL_GAP * 2;
        return new float[]{px, py, pw, ph};
    }

    private static float getContentWidth(float panelW) {
        return panelW - CPAD * 2;
    }

    private static float getColumnWidth(float contentW) {
        return (contentW - CompactGuiScreen.MODULE_COL_GAP) / COLS;
    }

    public static float getSettingHeight(Renderer2D renderer, Setting<?> setting, float width) {
        return CompactGuiRenderSetting.getSettingHeight(renderer, setting, width);
    }

    public static void render(Renderer2D renderer, int mouseX, int mouseY, float mainAlpha) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        float sw = client.getWindow().getScaledWidth();
        float sh = client.getWindow().getScaledHeight();
        CompactGuiScreen.setGuiScaleTarget(vesence.module.impl.misc.ClickGui.guiScale.get());
        CompactGuiScreen.x = sw / 2f - CompactGuiScreen.GUI_W / 2f;
        CompactGuiScreen.y = sh / 2f - CompactGuiScreen.GUI_H / 2f;
        CompactGuiScreen.currentMouseX = (int) CompactGuiScreen.toGuiX(mouseX);
        CompactGuiScreen.currentMouseY = (int) CompactGuiScreen.toGuiY(mouseY);

        CompactGuiScreen.openAnim.update();
        CompactGuiScreen.openAnimY.update();
        CompactGuiScreen.categorySwitchAnim.update();
        CompactGuiScreen.categoryIndicatorY.update();
        CompactGuiScreen.searchAnim.update();
        CompactGuiScreen.updateModuleListTransition();

        float openVal = (float) CompactGuiScreen.openAnim.getValue();
        float openValY = (float) CompactGuiScreen.openAnimY.getValue();
        if (openVal < 0.01f) return;
        if (openValY < 0.01f) return;

        float alpha = mainAlpha * openVal;

        float gx = CompactGuiScreen.x;
        float gy = CompactGuiScreen.y;
        float gw = CompactGuiScreen.GUI_W;
        float gh = CompactGuiScreen.GUI_H;

        float userScale = (float) Math.max(0.0001, CompactGuiScreen.guiScale);
        boolean scaled = Math.abs(userScale - 1.0f) > 1.0E-4f;
        if (scaled) {
            renderer.pushScale(userScale, gx + gw / 2f, gy + gh / 2f);
        }

        renderer.pushScale(alpha, gx + gw / 2f, gy + gh / 2f);

        if(!ClickGui.squircleGui.get()) {
            if (ClickGui.blurGui.get()) renderer.blur(gx, gy, gw, gh, ClickGui.blurStrengthGui.get().floatValue(), ClickGui.getGuiCorner(), alpha);
            renderer.gradient(gx, gy, gw, gh, ClickGui.getGuiCorner(), ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.10f), ClickGui.guiAlpha.get().floatValue() * alpha),
                    ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.10f), ClickGui.guiAlpha.get().floatValue() * alpha),
                    ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.05f), ClickGui.guiAlpha.get().floatValue() * alpha),
                    ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.05f), ClickGui.guiAlpha.get().floatValue() * alpha), true);
            renderer.gradientOutline(gx, gy, gw, gh, ClickGui.getGuiCorner(), ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 1), ClickGui.guiAlpha.get().floatValue() * alpha),
                    ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 1), ClickGui.guiAlpha.get().floatValue() * alpha),
                    ColorUtil.multAlpha(ColorUtil.BLACK, (int) (0 * alpha)),
                    ColorUtil.multAlpha(ColorUtil.BLACK, (int) (0 * alpha)), 1f, true);
        } else {
            renderer.drawSquircleShadow(gx - 5, gy - 5, gw + 10, gh + 10, ClickGui.getGuiSquirt(), BorderRadius.all(ClickGui.getGuiCorner()),15, 1, ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(),(int) (15 * alpha)));
            if (ClickGui.blurGui.get()) renderer.blurSquircle(gx, gy, gw, gh, ClickGui.blurStrengthGui.get().floatValue(), ClickGui.getGuiSquirt(), BorderRadius.all(ClickGui.getGuiCorner()), alpha);
            renderer.drawSquircleGradient(gx, gy, gw, gh, ClickGui.getGuiSquirt(), BorderRadius.all(ClickGui.getGuiCorner()), ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.08f), ClickGui.guiAlpha.get().floatValue() * alpha),
                    ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.08f), ClickGui.guiAlpha.get().floatValue() * alpha),
                    ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.1f), ClickGui.guiAlpha.get().floatValue() * alpha),
                    ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.1f), ClickGui.guiAlpha.get().floatValue() * alpha));
        }
        renderLeftPanel(renderer, gx, gy, gh, alpha);
        renderRightPanel(renderer, gx, gy, gh, alpha);
        renderer.popScale();

        if (scaled) {
            renderer.popScale();
        }

        if (GuiScreen.activeModeSetting != null && GuiScreen.activeModeSetting.opened) {
            renderer.pushAlpha(mainAlpha);
            GuiRenderModePopup.renderModePopup(renderer, mouseX, mouseY, mainAlpha);
            renderer.popAlpha();
        }

    }

    private static void renderLeftPanel(Renderer2D renderer, float gx, float gy, float gh, float alpha) {
        float px = gx + CompactGuiScreen.LEFT_PANEL_GAP;
        float py = gy + CompactGuiScreen.LEFT_PANEL_GAP + 25;
        float pw = CompactGuiScreen.LEFT_PANEL_W + 5    ;
        float ph = gh - CompactGuiScreen.LEFT_PANEL_GAP * 2;

        CompactGuiScreen.searchAnim.run(CompactGuiScreen.searchActive ? 1.0 : 0.0, 0.2, Easings.CUBIC_OUT);

        float sx = px + CompactGuiScreen.LEFT_PADDING;
        float sy = py + CompactGuiScreen.LEFT_PADDING;
        float sw = CompactGuiScreen.SEARCH_W;
        float sh = CompactGuiScreen.LEFT_SEARCH_H;

        int sBg = ColorUtil.overCol(ColorUtil.getColor(255, (int) (7 * alpha)), ColorUtil.getColor(255, (int) (12 * alpha)), CompactGuiScreen.searchAnim.get());
        int sOutline = ColorUtil.overCol(ColorUtil.getColor(255, (int) (11 * alpha)), ColorUtil.getColor(255, (int) (15 * alpha)), CompactGuiScreen.searchAnim.get());
        renderer.rect(sx, sy - 7, sw, sh, 5, sBg);
        renderer.rectOutline(sx, sy - 7, sw, sh, 5, sOutline, 0.5f);

        renderer.text(FontRegistry.NUC, sx + sw - 13.5f, sy + sh / 2f + 3.5f - 7,
                14, "n", ColorUtil.overCol(ColorUtil.getColor(255, (int) (35 * alpha)), ColorUtil.getColor(255, (int) (75 * alpha)), CompactGuiScreen.searchAnim.get()));
        renderer.textCenter(FontRegistry.LOGO, px + pw / 2f, gy + 26 - 4.5f, 52, "A" + ColorFormat.color(255,255,255), new int[] {Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (255 * alpha)),
                        Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (200 * alpha))}, 15);

        renderer.pushClipRect(sx, sy - 7, sw - 19 , sh);
        String displayText = CompactGuiScreen.searchText.isEmpty() && !CompactGuiScreen.searchActive
                ? "Search for modules..." : CompactGuiScreen.searchText;
        int sTextCol = ColorUtil.overCol(ColorUtil.getColor(255, (int) (125 * alpha)), ColorUtil.getColor(255, (int) (255 * alpha)), CompactGuiScreen.searchAnim.get());
        renderer.text(FontRegistry.MONTSERRAT, sx + 5, sy + sh / 2f + 3 - 7,
                CompactGuiScreen.SEARCH_FONT, displayText, sTextCol);

        if (CompactGuiScreen.searchActive && (System.currentTimeMillis() / 500) % 2 == 0) {
            float cx = sx + 5 + renderer.measureText(FontRegistry.MONTSERRAT, CompactGuiScreen.searchText, CompactGuiScreen.SEARCH_FONT).width;
            renderer.text(FontRegistry.MONTSERRAT, cx, sy + sh / 2f + 2.5f - 7, 8, "|",
                    ColorUtil.replAlpha(TEXT_COLOR, (int) (255 * alpha)));
        }
        renderer.popClipRect();
        float catStartY = sy + sh + 2.5f;
        Category[] cats = Category.values();

        float indicatorY = catStartY + (float) CompactGuiScreen.categoryIndicatorY.getValue();
        int indicatorBg = ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (12 * alpha));
        int indicatorOutline = ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (15 * alpha));
        renderer.rect(gx + CompactGuiScreen.LEFT_PANEL_GAP + 2.5f, gy + CompactGuiScreen.LEFT_PANEL_GAP + 60, sw + 1, 110, 5, ColorUtil.replAlpha(-1, (int) (4 * alpha)));
        renderer.gradientOutline(gx + CompactGuiScreen.LEFT_PANEL_GAP + 2.5f, gy + CompactGuiScreen.LEFT_PANEL_GAP + 60, sw + 1, 110, 5, ColorUtil.replAlpha(-1, (int) (16 * alpha)),ColorUtil.replAlpha(-1, (int) (3 * alpha)),
                ColorUtil.replAlpha(-1, (int) (16 * alpha)),ColorUtil.replAlpha(-1, (int) (3 * alpha)), 1, true);

        float otherTop = catStartY + CompactGuiScreen.getCategoryYOffset(CompactGuiScreen.getCategoryIndex(Category.DISPLAY)) - 4.5f;
        float otherH = (catStartY + CompactGuiScreen.getClientYOffset() + CompactGuiScreen.LEFT_CATEGORY_H + 4.5f) - otherTop;
        renderer.rect(gx + CompactGuiScreen.LEFT_PANEL_GAP + 2.5f, otherTop, sw + 1, otherH, 5, ColorUtil.replAlpha(-1, (int) (4 * alpha)));
        renderer.gradientOutline(gx + CompactGuiScreen.LEFT_PANEL_GAP + 2.5f, otherTop, sw + 1, otherH, 5, ColorUtil.replAlpha(-1, (int) (16 * alpha)),ColorUtil.replAlpha(-1, (int) (3 * alpha)),
                ColorUtil.replAlpha(-1, (int) (16 * alpha)),ColorUtil.replAlpha(-1, (int) (3 * alpha)), 1, true);

        float basicsBoxBottom = gy + CompactGuiScreen.LEFT_PANEL_GAP + 60 + 110;
        float otherHeaderY = (basicsBoxBottom + otherTop) / 2f + 4f;

        renderer.rect(sx + 3, indicatorY, sw - 6, CompactGuiScreen.LEFT_CATEGORY_H, 4, indicatorBg);
        renderer.rect(sx + 5.5f, indicatorY + 3, 3, CompactGuiScreen.LEFT_CATEGORY_H - 6, 3, ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (100 * alpha)));
        renderer.gradientOutline(sx + 3, indicatorY, sw - 6, CompactGuiScreen.LEFT_CATEGORY_H, 4, indicatorOutline, indicatorOutline, indicatorOutline, indicatorOutline, 1);

        for (int i = 0; i < cats.length; i++) {
            Category cat = cats[i];
            Animation2 hAnim = CompactGuiScreen.getCategoryHoverAnim(cat);
            Animation2 sAnim = CompactGuiScreen.getCategorySelectAnim(cat);
            hAnim.update();
            sAnim.update();

            float cy = catStartY + CompactGuiScreen.getCategoryYOffset(i);

            int headerCol = ColorUtil.replAlpha(TEXT_COLOR, (int) (90 * alpha));
            if (i == 0) {
                renderer.text(FontRegistry.MONTSERRAT, sx + 4, cy - 13, 14,
                        "BASICS".toUpperCase(), headerCol);
            }
            if (cat == Category.DISPLAY) {
                renderer.text(FontRegistry.MONTSERRAT, sx + 4, otherHeaderY, 14,
                        "OTHER".toUpperCase(), headerCol);
            }
            boolean hov = isHovered(CompactGuiScreen.currentMouseX, CompactGuiScreen.currentMouseY, sx, cy, sw, CompactGuiScreen.LEFT_CATEGORY_H);
            boolean sel = CompactGuiScreen.isCategoryVisuallySelected(cat);

            hAnim.run(hov ? 1.0 : 0.0, 0.2, Easings.CUBIC_OUT);
            sAnim.run(sel ? 1.0 : 0.0, 0.25, Easings.CUBIC_OUT);

            float hv = (float) hAnim.getValue();
            float sv = (float) sAnim.getValue();

            int iCol = ColorUtil.overCol(
                    ColorUtil.replAlpha(-1, (int) (65 * alpha)),
                    ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (215 * alpha)),
                    sv);
            int iCol2 = ColorUtil.overCol(
                    ColorUtil.replAlpha(-1, (int) (65 * alpha)),
                    ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (2125 * alpha)),
                    sv);
            renderer.textRight(FontRegistry.MON,
                    sx + sw - 6,
                    cy + CompactGuiScreen.LEFT_CATEGORY_H / 2f + 3.75f,
                    CompactGuiScreen.LEFT_CATEGORY_ICON_FONT + 3, cat.getIcon(), new int[] {iCol, iCol2});

            renderer.text(FontRegistry.MONTSERRAT,
                    sx + 6 + sv * 8,
                    cy + CompactGuiScreen.LEFT_CATEGORY_H / 2f + 3.25f,
                    12.5f, cat.getName(), new int[] {
                            ColorUtil.overCol(ColorUtil.replAlpha(-1, (int) (65 * alpha)), ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (215 * alpha)), sv),
                            ColorUtil.overCol(ColorUtil.replAlpha(-1, (int) (45 * alpha)), ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (125 * alpha)), sv)
                    }, 10);
        }

        {
            float cy = catStartY + CompactGuiScreen.getThemeYOffset();

            boolean hov = isHovered(CompactGuiScreen.currentMouseX, CompactGuiScreen.currentMouseY, sx, cy, sw, CompactGuiScreen.LEFT_CATEGORY_H);
            boolean sel = CompactGuiScreen.isThemeVisuallySelected();
            CompactGuiScreen.themeHoverAnim.update();
            CompactGuiScreen.themeSelectAnim.update();
            CompactGuiScreen.themeHoverAnim.run(hov ? 1.0 : 0.0, 0.2, Easings.CUBIC_OUT);
            CompactGuiScreen.themeSelectAnim.run(sel ? 1.0 : 0.0, 0.25, Easings.CUBIC_OUT);
            float sv = (float) CompactGuiScreen.themeSelectAnim.getValue();

            int iCol = ColorUtil.overCol(ColorUtil.replAlpha(-1, (int) (65 * alpha)), ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (215 * alpha)), sv);
            int iCol2 = ColorUtil.overCol(ColorUtil.replAlpha(-1, (int) (65 * alpha)), ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (125 * alpha)), sv);
            renderer.textRight(FontRegistry.MON, sx + sw - 6, cy + CompactGuiScreen.LEFT_CATEGORY_H / 2f + 3.75f,
                    CompactGuiScreen.LEFT_CATEGORY_ICON_FONT + 3, "f", new int[] {iCol, iCol2});
            renderer.text(FontRegistry.MONTSERRAT, sx + 6 + sv * 8, cy + CompactGuiScreen.LEFT_CATEGORY_H / 2f + 3.25f,
                    12.5f, "Theme", new int[] {iCol, iCol2});
        }

        {
            float cy = catStartY + CompactGuiScreen.getClientYOffset();

            boolean hov = isHovered(CompactGuiScreen.currentMouseX, CompactGuiScreen.currentMouseY, sx, cy, sw, CompactGuiScreen.LEFT_CATEGORY_H);
            boolean sel = CompactGuiScreen.isClientVisuallySelected();
            CompactGuiScreen.clientHoverAnim.update();
            CompactGuiScreen.clientSelectAnim.update();
            CompactGuiScreen.clientHoverAnim.run(hov ? 1.0 : 0.0, 0.2, Easings.CUBIC_OUT);
            CompactGuiScreen.clientSelectAnim.run(sel ? 1.0 : 0.0, 0.25, Easings.CUBIC_OUT);
            float sv = (float) CompactGuiScreen.clientSelectAnim.getValue();

            int iCol = ColorUtil.overCol(ColorUtil.replAlpha(-1, (int) (65 * alpha)), ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (215 * alpha)), sv);
            int iCol2 = ColorUtil.overCol(ColorUtil.replAlpha(-1, (int) (65 * alpha)), ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (125 * alpha)), sv);
            renderer.textRight(FontRegistry.MON, sx + sw - 6, cy + CompactGuiScreen.LEFT_CATEGORY_H / 2f + 3.75f,
                    CompactGuiScreen.LEFT_CATEGORY_ICON_FONT + 3, "w", new int[] {iCol, iCol2});
            renderer.text(FontRegistry.MONTSERRAT, sx + 6 + sv * 8, cy + CompactGuiScreen.LEFT_CATEGORY_H / 2f + 3.25f,
                    12.5f, "Client", new int[] {iCol, iCol2});
        }

        float brandY = py + ph - CompactGuiScreen.BRAND_H - 5;

    }

    private static void renderRightPanel(Renderer2D renderer, float gx, float gy, float gh, float alpha) {
        float[] b = getRightPanelBounds(gx, gy, gh);
        float px = b[0], py = b[1], pw = b[2], ph = b[3];

        if (CompactGuiScreen.themeSelected && CompactGuiScreen.searchText.isEmpty()) {
            renderThemeGrid(renderer, px, py, pw, ph, alpha);
            return;
        }

        if (CompactGuiScreen.clientSelected && CompactGuiScreen.searchText.isEmpty()) {
            renderClientSettings(renderer, px, py, pw, ph, alpha);
            return;
        }

        List<Module> modules = getCurrentModules();
        float cX = px + CPAD;
        float cY = py + CPAD;
        float cW = getContentWidth(pw);
        float cH = ph - CPAD * 2;
        float colW = getColumnWidth(cW);

        float[] heights = new float[modules.size()];
        for (int i = 0; i < modules.size(); i++) {
            heights[i] = computeCardHeight(renderer, modules.get(i), colW);
        }
        float totalH = 0;
        float[] tmpColY = new float[COLS];
        for (int i = 0; i < modules.size(); i++) {
            int sc = 0;
            for (int c = 1; c < COLS; c++) {
                if (tmpColY[c] < tmpColY[sc]) sc = c;
            }
            tmpColY[sc] += heights[i] + CompactGuiScreen.MODULE_ROW_GAP;
        }
        for (int c = 0; c < COLS; c++) {
            if (tmpColY[c] > totalH) totalH = tmpColY[c];
        }

        CompactGuiScreen.moduleScroll.setSpeed(100);
        CompactGuiScreen.moduleScroll.setMax(totalH, cH);
        boolean hov = isHovered(CompactGuiScreen.currentMouseX, CompactGuiScreen.currentMouseY, px, py, pw, ph);
        CompactGuiScreen.moduleScroll.setEnabled(hov && totalH > cH);
        CompactGuiScreen.moduleScroll.update();
        float scrollOff = CompactGuiScreen.moduleScroll.getScroll();

        float listAlpha = (float) CompactGuiScreen.moduleListAlpha.getValue();
        boolean fadingOut = CompactGuiScreen.isModuleFadingOut();

        CompactGuiScreen.scrollBarAlpha.update();
        CompactGuiScreen.scrollBarAlpha.run(hov && totalH > cH ? 1.0 : 0.0, 0.25, Easings.CUBIC_OUT);
        float sbAlpha = (float) CompactGuiScreen.scrollBarAlpha.getValue();

        renderer.pushClipRect((int) cX, (int) cY, (int) cW, (int) cH);

        float[] colY = new float[COLS];
        Module newHovered = null;

        for (int i = 0; i < modules.size(); i++) {
            Module mod = modules.get(i);
            float cardH = heights[i];

            int sc = 0;
            for (int c = 1; c < COLS; c++) {
                if (colY[c] < colY[sc]) sc = c;
            }

            float cardX = cX + sc * (colW + CompactGuiScreen.MODULE_COL_GAP);
            float cardY = cY + colY[sc] + scrollOff;

            colY[sc] += cardH + CompactGuiScreen.MODULE_ROW_GAP;

            if (cardY + cardH < cY - 5 || cardY > cY + cH + 5) continue;

            Animation2 enableAnim = CompactGuiScreen.getModuleEnableAnim(mod);
            Animation2 hoverAnim = CompactGuiScreen.getModuleHoverAnim(mod);
            enableAnim.update();
            hoverAnim.update();
            enableAnim.run(mod.enable ? 1.0 : 0.0, 0.3, Easings.QUINT_OUT);

            boolean isCardHovered = isHovered(CompactGuiScreen.currentMouseX, CompactGuiScreen.currentMouseY,
                    cardX, cardY, colW, cardH);
            if (isCardHovered) newHovered = mod;
            hoverAnim.run(isCardHovered ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT);

            float enableVal = (float) enableAnim.getValue();

            float cardAlpha;
            float cardSlideY;
            if (fadingOut) {
                cardAlpha = alpha * listAlpha;
                cardSlideY = (1.0f - listAlpha) * 12f;
            } else {
                float staggerA = CompactGuiScreen.computeStaggerAlpha(i, CompactGuiScreen.moduleStaggerStart);
                cardAlpha = alpha * listAlpha * staggerA;
                cardSlideY = CompactGuiScreen.computeStaggerOffset(i, 12, CompactGuiScreen.moduleStaggerStart);
            }

            int bgColor = ColorUtil.overCol(
                    ColorUtil.replAlpha(-1, (int) (7 * cardAlpha)),
                    ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (20 * cardAlpha)),
                    enableVal);
            int bgColor2 = ColorUtil.overCol(
                    ColorUtil.replAlpha(-1, (int) (0 * cardAlpha)),
                    ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (5 * cardAlpha)),
                    enableVal);

            renderer.gradient(cardX + cardSlideY, cardY, colW, cardH, 6, bgColor, bgColor, bgColor2, bgColor2, true);
            renderer.rectOutline(cardX + cardSlideY, cardY, colW, cardH, 6,
                    ColorUtil.replAlpha(-1, (int) (10 * cardAlpha)), 0.35f);

            renderer.pushRoundedClipRect(cardX + 1 + cardSlideY, cardY, colW - 2, cardH - 2, 4, 4, 4, 4);
            renderModuleCard(renderer, mod, cardX + cardSlideY, cardY, colW, cardH, cardAlpha, enableVal);
            renderer.popClipRect();
        }

        renderer.popClipRect();

        if (newHovered != CompactGuiScreen.hoveredModule) {
            CompactGuiScreen.hoveredModule = newHovered;
            if (newHovered != null) CompactGuiScreen.tooltipDelay = 0f;
        }
    }

    private static final java.util.Map<vesence.module.Theme, Animation2> THEME_SELECT_ANIMS = new java.util.HashMap<>();

    private static Animation2 themeSelectAnim(vesence.module.Theme t, boolean selected) {
        Animation2 a = THEME_SELECT_ANIMS.computeIfAbsent(t, k -> {
            Animation2 an = new Animation2();
            an.setValue(selected ? 1.0 : 0.0);
            return an;
        });
        a.update();
        a.run(selected ? 1.0 : 0.0, 0.3, Easings.CUBIC_OUT, true);
        return a;
    }

    private static void renderClientSettings(Renderer2D renderer, float px, float py, float pw, float ph, float alpha) {
        float cX = px + CPAD;
        float cY = py + CPAD;
        float cW = pw - CPAD * 2 - 4;
        float cH = ph - CPAD * 2;

        java.util.List<vesence.module.api.setting.Setting<?>> settings =
                vesence.ui.clickgui.compact.client.ClientSettings.getSettings();

        float totalH = 0f;
        for (vesence.module.api.setting.Setting<?> s : settings) {
            if ((boolean) s.hidden.get()) continue;
            totalH += vesence.ui.clickgui.compact.client.CompactGuiRenderClientSetting
                    .getSettingHeight(renderer, s, cW) + 2.5f;
        }

        boolean hov = isHovered(CompactGuiScreen.currentMouseX, CompactGuiScreen.currentMouseY, px, py, pw, ph);
        CompactGuiScreen.clientScroll.setSpeed(100);
        CompactGuiScreen.clientScroll.setMax(totalH, cH);
        CompactGuiScreen.clientScroll.setEnabled(hov && totalH > cH);
        CompactGuiScreen.clientScroll.update();
        float scrollOff = CompactGuiScreen.clientScroll.getScroll();

        float listAlpha = (float) CompactGuiScreen.moduleListAlpha.getValue();
        boolean fadingOut = CompactGuiScreen.isModuleFadingOut();

        renderer.pushClipRect((int) cX - 2, (int) cY - 2, (int) cW + 4, (int) cH + 4);

        float curY = cY + scrollOff;
        int idx = 0;
        for (vesence.module.api.setting.Setting<?> setting : settings) {
            if ((boolean) setting.hidden.get()) continue;
            float sH = vesence.ui.clickgui.compact.client.CompactGuiRenderClientSetting
                    .getSettingHeight(renderer, setting, cW);

            float rowAlpha;
            float slideY;
            if (fadingOut) {
                rowAlpha = alpha * listAlpha;
                slideY = (1.0f - listAlpha) * 12f;
            } else {
                float staggerA = CompactGuiScreen.computeStaggerAlpha(idx, CompactGuiScreen.moduleStaggerStart);
                rowAlpha = alpha * listAlpha * staggerA;
                slideY = CompactGuiScreen.computeStaggerOffset(idx, 12, CompactGuiScreen.moduleStaggerStart);
            }

            if (curY + sH >= cY - 5 && curY <= cY + cH + 5) {
                vesence.ui.clickgui.compact.client.CompactGuiRenderClientSetting.renderSetting(
                        renderer, setting,
                        cX + slideY, curY, cW,
                        CompactGuiScreen.currentMouseX, CompactGuiScreen.currentMouseY,
                        rowAlpha
                );
            }

            curY += sH + 2.5f;
            idx++;
        }

        renderer.popClipRect();
    }

    private static void renderThemeGrid(Renderer2D renderer, float px, float py, float pw, float ph, float alpha) {
        float cX = px + CPAD;
        float cY = py + CPAD;
        float cW = pw - CPAD * 2 - 4;
        float cH = ph - CPAD * 2;

        vesence.module.Theme[] themes = vesence.module.Theme.values();
        vesence.module.Theme current = vesence.module.impl.visuals.ThemeModule.getCurrentTheme();

        int rows = (themes.length + THEME_COLS - 1) / THEME_COLS;
        float totalH = rows * (THEME_CELL_H + THEME_ROW_GAP);

        boolean hov = isHovered(CompactGuiScreen.currentMouseX, CompactGuiScreen.currentMouseY, px, py, pw, ph);
        CompactGuiScreen.moduleScroll.setSpeed(100);
        CompactGuiScreen.moduleScroll.setMax(totalH, cH);
        CompactGuiScreen.moduleScroll.setEnabled(hov && totalH > cH);
        CompactGuiScreen.moduleScroll.update();
        float scrollOff = CompactGuiScreen.moduleScroll.getScroll();

        float listAlpha = (float) CompactGuiScreen.moduleListAlpha.getValue();
        boolean fadingOut = CompactGuiScreen.isModuleFadingOut();

        renderer.pushClipRect((int) cX - 2, (int) cY - 2, (int) cW + 4, (int) cH + 1);
        for (int i = 0; i < themes.length; i++) {
            vesence.module.Theme theme = themes[i];
            float[] r = themeCellRect(i, cX, cY, cW, scrollOff);
            float cellX = r[0], cellY = r[1], cellW = r[2], cellH = r[3];
            if (cellY + cellH < cY - 5 || cellY > cY + cH + 5) continue;

            boolean selected = theme == current;
            boolean cellHov = isHovered(CompactGuiScreen.currentMouseX, CompactGuiScreen.currentMouseY, cellX, cellY, cellW, cellH);

            float cellAlpha;
            float slideY;
            if (fadingOut) {
                cellAlpha = alpha * listAlpha;
                slideY = (1.0f - listAlpha) * 12f;
            } else {
                float staggerA = CompactGuiScreen.computeStaggerAlpha(i, CompactGuiScreen.moduleStaggerStart);
                cellAlpha = alpha * listAlpha * staggerA;
                slideY = CompactGuiScreen.computeStaggerOffset(i, 12, CompactGuiScreen.moduleStaggerStart);
            }
            float cx2 = cellX;
            float cy2 = cellY + slideY;

            Animation2 hovA = CompactGuiScreen.getThemeCellAnim(theme);
            hovA.update();
            hovA.run((selected || cellHov) ? 1.0 : 0.0, 0.2, Easings.CUBIC_OUT);
            float hv = (float) hovA.getValue();

            float sel = (float) themeSelectAnim(theme, selected).getValue();

            float ca = cellAlpha;
            int cellBg = ColorUtil.overCol(ColorUtil.replAlpha(-1, (int) (7 * ca)),
                    ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (14 * ca)), hv);
            renderer.rect(cx2, cy2, cellW, cellH, 6, cellBg);

            int outlineCol = ColorUtil.overCol(
                    ColorUtil.replAlpha(-1, (int) (10 * ca)),
                    ColorUtil.replAlpha(theme.getMain().getRGB(), (int) (210 * ca)), sel);
            renderer.gradientOutline(cx2, cy2, cellW, cellH, 6, outlineCol, outlineCol, ColorUtil.multDark(outlineCol, 0.65f),ColorUtil.multDark(outlineCol, 0.6f), 1, true);

            int textCol = ColorUtil.overCol(ColorUtil.replAlpha(TEXT_COLOR, (int) (130 * ca)),
                    ColorUtil.replAlpha(-1, (int) (255 * ca)), Math.max(hv, sel));
            renderer.text(FontRegistry.MONTSERRAT, cx2 + 6, cy2 + cellH / 2f + 3.5f, 12.5f, theme.getName(), textCol);

            float prevW = 22f;
            float prevH = 11f;
            float prevX = cx2 + cellW - prevW - 6f;
            float prevY = cy2 + (cellH - prevH) / 2f;
            int c1 = theme.getMain().getRGB();
            int c2 = theme.getMain2().getRGB();
            renderer.gradient(prevX, prevY, prevW, prevH, 3.5f,
                    ColorUtil.replAlpha(c1, (int) (255 * ca)), ColorUtil.replAlpha(c2, (int) (255 * ca)),
                    ColorUtil.replAlpha(c2, (int) (255 * ca)), ColorUtil.replAlpha(c1, (int) (255 * ca)));
            renderer.rectOutline(prevX, prevY, prevW, prevH, 3.5f, ColorUtil.getColor(0, (int) (60 * ca)), 1f);
        }
        renderer.popClipRect();

        CompactGuiScreen.scrollBarAlpha.update();
        CompactGuiScreen.scrollBarAlpha.run(hov && totalH > cH ? 1.0 : 0.0, 0.25, Easings.CUBIC_OUT);
    }

    private static void renderModuleCard(Renderer2D renderer, Module mod, float cardX, float cardY,
                                          float cardW, float cardH, float alpha, float enableVal) {
        float pad = CompactGuiScreen.MODULE_CARD_PADDING;
        float cx = cardX + pad;
        float cy = cardY + pad;
        float innerW = cardW - pad * 2;

        boolean hasSettings = !mod.getSettingsForGUI().isEmpty();

        int nameCol = ColorUtil.overCol(
                ColorUtil.replAlpha(TEXT_COLOR, (int) (165 * alpha)),
                ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (255 * alpha)),
                enableVal);
        int nameCol2 = ColorUtil.overCol(
                ColorUtil.replAlpha(TEXT_COLOR, (int) (125 * alpha)),
                ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (165 * alpha)),
                enableVal);
        int iconCol = ColorUtil.overCol(
                ColorUtil.replAlpha(TEXT_COLOR, (int) (0 * alpha)),
                ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (255 * alpha)),
                enableVal);

        float togW = 16;
        float togH = 8;
        float togX = cardX + cardW - pad - togW;
        float togY = cardY + pad + 1.5f;
        int togTrackBg = ColorUtil.overCol(
                ColorUtil.replAlpha(-1, (int)(24 * alpha)),
                ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int)(100 * alpha)),
                enableVal);
        int togTrackBg2 = ColorUtil.overCol(
                ColorUtil.replAlpha(-1, (int)(24 * alpha)),
                ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int)(55 * alpha)),
                enableVal);

        float dotR = togH / 2f - 1.5f;
        float dotX = togX + 2 + enableVal * (togW - togH - 2);
        float dotY = togY + togH / 2f;
        int dotCol = ColorUtil.overCol(ColorUtil.getColor(255, (int) (50 * alpha)), ColorUtil.getColor(255, alpha), enableVal);
        int dotCol2 = ColorUtil.overCol(ColorUtil.getColor(95, alpha), ColorUtil.getColor(195, alpha), enableVal);

        float nameMaxX = togX - 4;
        renderer.text(FontRegistry.MONTSERRAT, cx + 3, cy + 10,
                14.75f, mod.name, new int[] {nameCol, nameCol2}, 0, -0.1f);

        cy += 18;

        String desc = mod.description != null ? mod.description : "";
        if (!desc.isEmpty()) {
            List<String> lines = wrapText(renderer, desc, CompactGuiScreen.MODULE_DESC_FONT, innerW);
            int dCol = ColorUtil.replAlpha(SUBTEXT_COLOR, (int) (120 * alpha));
            for (String line : lines) {
                if (cy + CompactGuiScreen.MODULE_DESC_FONT + 1 > cardY + cardH - pad) break;
                renderer.text(FontRegistry.MONTSERRAT, cx + 3, cy + CompactGuiScreen.MODULE_DESC_FONT - 8.5f,
                        CompactGuiScreen.MODULE_DESC_FONT, line, dCol);
                cy += CompactGuiScreen.MODULE_DESC_FONT - 3f;
            }
        }

        List<Setting<?>> settings = mod.getSettingsForGUI();
        cy += CompactGuiScreen.MODULE_DIVIDER_GAP;
        renderer.rect(cardX - 2, cy, cardW + 4, 0.5f, ColorUtil.overCol(ColorUtil.replAlpha(-1, (int) (10 * alpha)),
                ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (10 * alpha)), enableVal));
        cy += 3;

        {
            String bLabel = "Module bind...";
            String bText = mod.binding ? "..." : mod.bindLabel();
            float ktw = renderer.measureText(FontRegistry.MONTSERRAT, bText, 11).width;
            float bw = ktw + 7;
            float bx = cardX + cardW - bw - 3;
            renderer.rect(bx, cy + 0.75f, bw, 11, 2.5f, ColorUtil.replAlpha(-1, (int)(15 * alpha)));
            renderer.textRight(FontRegistry.MONTSERRAT, bx + bw - 3.5f, cy + 8.75f, 11, bText,
                    bText.contains("NONE") ? ColorUtil.replAlpha(-1, (int) (100 * alpha)) : ColorUtil.overCol(ColorUtil.replAlpha(-1, (int) (155 * alpha)), ColorUtil.replAlpha(-1, alpha), enableVal));
            renderer.text(FontRegistry.MONTSERRAT, cx + 3, cy + 9, 13, bLabel,
                    new int[] {ColorUtil.overCol(ColorUtil.replAlpha(-1, (int)(100 * alpha)), ColorUtil.replAlpha(-1, (int)(200 * alpha)), enableVal),
                            ColorUtil.overCol(ColorUtil.replAlpha(-1, (int)(65 * alpha)), ColorUtil.replAlpha(-1, (int)(125 * alpha)), enableVal)});
            cy += 13;
        }

        if (!settings.isEmpty()) {
            cy += 2;
            renderer.rect(cardX - 2, cy, cardW + 4, 0.5f, ColorUtil.overCol(ColorUtil.replAlpha(-1, (int) (10 * alpha)),
                    ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (10 * alpha)), enableVal));
            cy += 5;

            for (Setting<?> setting : settings) {
                if ((boolean) setting.hidden.get()) continue;
                float sH = getSettingHeight(renderer, setting, innerW);
                if (cy + sH > cardY + cardH + 1) break;

                CompactGuiRenderSetting.renderSetting(
                        renderer, setting, cx, cy, innerW,
                        CompactGuiScreen.currentMouseX, CompactGuiScreen.currentMouseY,
                        ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), (int) (15 * alpha)),
                        ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int) (255 * alpha)),
                        ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int) (12 * alpha)),
                        ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int) (80 * alpha)),
                        ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), (int) (255 * alpha)),
                        alpha
                );
                cy += sH + 2.5f;
            }
        }
    }

    public static float computeCardHeight(Renderer2D renderer, Module mod, float cardW) {
        float pad = CompactGuiScreen.MODULE_CARD_PADDING * 2;
        float h = pad + 18;

        String desc = mod.description != null ? mod.description : "";
        if (!desc.isEmpty()) {
            List<String> lines = wrapText(renderer, desc, CompactGuiScreen.MODULE_DESC_FONT, cardW - pad);
            h += lines.size() * (CompactGuiScreen.MODULE_DESC_FONT - 3f);
        }

        h += CompactGuiScreen.MODULE_DIVIDER_GAP + 3 + 13;

        List<Setting<?>> settings = mod.getSettingsForGUI();
        if (!settings.isEmpty()) {
            h += 2 + 5;
            for (Setting<?> setting : settings) {
                if ((boolean) setting.hidden.get()) continue;
                h += getSettingHeight(renderer, setting, cardW - pad) + 2.5f;
            }
            h -= 2.0f;
        }

        return Math.max(h, CompactGuiScreen.MODULE_CARD_MIN_H);
    }

    public static boolean isHovered(float mouseX, float mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
    }

    static List<String> wrapText(Renderer2D renderer, String text, float fontSize, float maxWidth) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            result.add("");
            return result;
        }
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() == 0) {
                current.append(word);
            } else {
                String test = current + " " + word;
                float w = renderer.measureText(FontRegistry.MONTSERRAT, test, fontSize).width;
                if (w > maxWidth && current.length() > 0) {
                    result.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current.append(" ").append(word);
                }
            }
        }
        if (current.length() > 0) result.add(current.toString());
        if (result.isEmpty()) result.add("");
        return result;
    }
}
