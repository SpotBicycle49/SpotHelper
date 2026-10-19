package com.spothelper.gui.ui;

import com.spothelper.gui.ui.UiTheme.Role;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * Текстовое поле в стиле меню. Внутри — обычный TextFieldWidget (ввод, курсор, выделение),
 * но без своего фона: рамку и заливку рисует UiDraw. Значение живёт в самом UiField,
 * поэтому не теряется при пересоздании экрана (resize).
 */
public final class UiField {
    private final String placeholder;
    private final int maxLen;
    private String text = "";
    private boolean numeric;
    private boolean signed;
    private TextFieldWidget widget;

    public Runnable onEnter;
    public Consumer<String> onChange = s -> {};
    /** Выставляется экраном: передаёт фокус виджету. */
    Consumer<TextFieldWidget> focuser = w -> {};

    public UiField(String placeholder, int maxLen) {
        this.placeholder = placeholder;
        this.maxLen = maxLen;
    }

    /** Разрешить только цифры. */
    public UiField numeric() {
        this.numeric = true;
        return this;
    }

    /** Разрешить целые числа со знаком (например, координаты). */
    public UiField signed() {
        this.signed = true;
        return this;
    }

    public String text() {
        return text;
    }

    public void setText(String s) {
        text = s == null ? "" : s;
        if (widget != null) widget.setText(text);
    }

    public boolean isFocused() {
        return widget != null && widget.isFocused();
    }

    public TextFieldWidget widget() {
        return widget;
    }

    TextFieldWidget create(TextRenderer tr) {
        widget = new TextFieldWidget(tr, 0, 0, 100, 14, Text.literal(placeholder));
        widget.setDrawsBackground(false);
        widget.setMaxLength(maxLen);
        if (signed) {
            widget.setTextPredicate(s -> s.matches("-?\\d{0,8}"));
        } else if (numeric) {
            widget.setTextPredicate(s -> s.chars().allMatch(Character::isDigit));
        }
        widget.setPlaceholder(Text.literal(placeholder));
        widget.setText(text);
        widget.setChangedListener(s -> {
            text = s;
            onChange.accept(s);
        });
        return widget;
    }

    public void draw(DrawContext c, UiInput in, int x, int y, int w, int h, boolean enabled) {
        if (widget == null) return;
        boolean focus = widget.isFocused();
        boolean hov = enabled && in.mouseOver(x, y, w, h);

        int border = UiTheme.get(focus || hov ? Role.HIGHLIGHT : Role.CARD_BORDER);
        int fill = UiTheme.get(Role.CARD_BG);
        if (!enabled) {
            border = UiTheme.dim(border);
            fill = UiTheme.dim(fill);
        }
        UiDraw.box(c, x, y, w, h, border, fill);

        widget.setX(x + 7);
        widget.setY(y + 2);
        widget.setWidth(Math.max(10, w - 14));
        widget.setHeight(Math.max(8, h - 4));
        widget.setEditableColor(UiTheme.get(enabled ? Role.TEXT_MAIN : Role.TEXT_DIM));
        widget.setEditable(enabled);
        widget.render(c, (int) in.mx, (int) in.my, 0f);

        if (enabled) in.addClick(x, y, w, h, () -> focuser.accept(widget));
    }
}
