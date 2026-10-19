package com.spothelper.auth;

import com.spothelper.gui.ui.UiCard;
import com.spothelper.gui.ui.UiChip;
import com.spothelper.gui.ui.UiDraw;
import com.spothelper.gui.ui.UiField;
import com.spothelper.gui.ui.UiScreen;
import com.spothelper.gui.ui.UiTheme;
import com.spothelper.gui.ui.UiTheme.Role;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Вход / заявка на доступ. Оформление — как в главном меню (UiScreen + UiTheme). */
public class AuthScreen extends UiScreen {

    private static final int BANNER_H = 40;
    private static final int ACCOUNT_H = 48;
    private static final int FORM_H = 80;
    private static final int BTN_H = 20;
    private static final int GAP = 8;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final UiField loginField = new UiField("Логин", 32);
    private final UiField passField = new UiField("Пароль", 64);

    /** Виджет, на который уже навешен форматтер (чтобы не добавлять его повторно при ресайзе). */
    private TextFieldWidget maskedWidget;
    private boolean refreshedOnOpen;

    public AuthScreen(Screen parent) {
        super(parent, "SpotHelper · доступ");
        field(loginField);
        field(passField);
        loginField.onEnter = () -> focus(passField);
        passField.onEnter = this::doLogin;
    }

    @Override
    protected int maxWidth() {
        return 420;
    }

    @Override
    protected void afterInit() {
        if (loginField.text().isEmpty()) loginField.setText(AuthManager.savedLogin());
        maskPassword();
        if (!refreshedOnOpen) {
            refreshedOnOpen = true;
            AuthManager.refresh(); // свежий статус при открытии экрана
        }
        focus(loginField.text().isEmpty() ? loginField : passField);
    }

    /** Показываем звёздочки вместо пароля. */
    private void maskPassword() {
        TextFieldWidget w = passField.widget();
        if (w == null || w == maskedWidget) return;
        maskedWidget = w;
        w.addFormatter((text, firstCharIndex) ->
                OrderedText.styledForwardsVisitedString("*".repeat(text.length()), Style.EMPTY));
    }

    private boolean showForm() {
        return AuthManager.state() != AuthManager.State.AUTHORIZED;
    }

    private static int noticeH() {
        return AuthManager.notice().isEmpty() ? 0 : 14;
    }

    private int bodyHeight() {
        if (!showForm()) return BANNER_H + GAP + noticeH() + ACCOUNT_H + 10 + BTN_H;
        return BANNER_H + GAP + noticeH() + FORM_H + (AuthManager.insecure() ? 14 : 0) + 10 + BTN_H;
    }

    /** Окно подгоняем под содержимое. */
    @Override
    protected void layout() {
        super.layout();
        wh = Math.min(wh, 42 + bodyHeight() + 14);
        wy = (height - wh) / 2;
        viewT = wy + 42;
        viewB = wy + wh - 10 - footerHeight();
    }

    @Override
    protected boolean onKey(int key) {
        if (key == ENTER || key == KP_ENTER) {
            if (showForm()) doLogin(); else AuthManager.refresh();
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- отрисовка

    @Override
    protected int drawBody(DrawContext ctx, int x, int top, int w) {
        TextRenderer tr = textRenderer;
        AuthManager.State st = AuthManager.state();
        boolean busy = st == AuthManager.State.CHECKING;
        int y = top;

        drawBanner(ctx, tr, x, y, w, st);
        y += BANNER_H + GAP;

        String note = AuthManager.notice();
        if (!note.isEmpty()) {
            UiDraw.text(ctx, tr, UiDraw.fit(tr, note, w), x, y, UiTheme.WARNING);
            y += 14;
        }

        int cw = (w - 12) / 3;
        if (!showForm()) {
            UiCard.block(ctx, tr, in, x, y, w, ACCOUNT_H, "Аккаунт", true);
            String lg = AuthManager.login().isEmpty() ? "—" : AuthManager.login();
            kv(ctx, tr, x + 8, y + 22, "Логин", lg, w - 16);
            kv(ctx, tr, x + 8, y + 34, "Ник Minecraft", AuthManager.mcName(), w - 16);
            y += ACCOUNT_H + 10;

            UiChip.draw(ctx, tr, in, x, y, cw, BTN_H, "Проверить снова", true, !busy, AuthManager::refresh);
            UiChip.draw(ctx, tr, in, x + cw + 6, y, cw, BTN_H, "Выйти", false, true, this::doLogout);
            UiChip.draw(ctx, tr, in, x + 2 * (cw + 6), y, cw, BTN_H, "Закрыть", false, true, this::onBack);
            y += BTN_H;
        } else {
            UiCard.block(ctx, tr, in, x, y, w, FORM_H, "Вход в аккаунт", !busy);
            loginField.draw(ctx, in, x + 8, y + 22, w - 16, 22, !busy);
            passField.draw(ctx, in, x + 8, y + 50, w - 16, 22, !busy);
            y += FORM_H;
            if (AuthManager.insecure()) {
                UiDraw.text(ctx, tr, UiDraw.fit(tr, "Нет шифрования (http): пароль уйдёт открытым текстом", w),
                        x, y + 4, UiTheme.WARNING);
                y += 14;
            }
            y += 10;

            boolean filled = !loginField.text().isBlank() && !passField.text().isEmpty();
            UiChip.draw(ctx, tr, in, x, y, cw, BTN_H, "Войти", true, filled && !busy, this::doLogin);
            UiChip.draw(ctx, tr, in, x + cw + 6, y, cw, BTN_H, "Заявка на доступ", false,
                    filled && !busy, this::doRegister);
            UiChip.draw(ctx, tr, in, x + 2 * (cw + 6), y, cw, BTN_H, "Закрыть", false, true, this::onBack);
            y += BTN_H;
        }
        return y - top;
    }

    private void drawBanner(DrawContext ctx, TextRenderer tr, int x, int y, int w, AuthManager.State st) {
        int col;
        String title;
        String desc;
        String reason = AuthManager.reason();
        switch (st) {
            case AUTHORIZED -> {
                col = c(Role.OK);
                if (AuthManager.apiUnreachable() && AuthManager.expiresAt() == 0L) {
                    title = "Сервер авторизации не отвечает";
                    desc = "Доступ открыт, пока сервер не вернётся";
                } else if (AuthManager.serverOpen() && AuthManager.expiresAt() == 0L) {
                    title = "Авторизация отключена";
                    desc = "Доступ к SpotHelper открыт для всех";
                } else {
                    title = AuthManager.login().isEmpty()
                            ? "Доступ к SpotHelper активен" : "Вы вошли как " + AuthManager.login();
                    String tier = AuthManager.tier().isEmpty() ? "" : "Тариф: " + AuthManager.tier() + " · ";
                    desc = tier + "до " + DATE_FMT.format(Instant.ofEpochSecond(AuthManager.expiresAt()));
                }
            }
            case CHECKING -> {
                col = UiTheme.WARNING;
                title = "Проверка…";
                desc = "Подождите";
            }
            case PENDING -> {
                col = UiTheme.WARNING;
                title = "Заявка ожидает подтверждения";
                desc = reason.isEmpty() ? "Администратор откроет доступ в панели" : reason;
            }
            case LOGGED_OUT -> {
                col = c(Role.ERROR);
                title = "Нет доступа к SpotHelper";
                desc = "Войдите или отправьте заявку на доступ";
            }
            case UNAVAILABLE -> {
                col = c(Role.ERROR);
                title = "API недоступен";
                desc = "Нет связи. Если сервер переехал, обновите мод.";
            }
            default -> {
                col = c(Role.ERROR);
                title = "Нет доступа к SpotHelper";
                desc = "Отказано" + (reason.isEmpty() ? "" : ": " + reason);
            }
        }
        UiDraw.box(ctx, x, y, w, BANNER_H, col, UiTheme.mix(c(Role.CARD_BG), col, 0.15));
        UiDraw.text(ctx, tr, UiDraw.fit(tr, title, w - 16), x + 8, y + 10, col);
        UiDraw.text(ctx, tr, UiDraw.fit(tr, desc, w - 16), x + 8, y + 24, c(Role.TEXT_DIM));
    }

    private void kv(DrawContext ctx, TextRenderer tr, int x, int y, String label, String value, int maxW) {
        int lw = tr.getWidth(label) + 8;
        UiDraw.text(ctx, tr, label, x, y, c(Role.TEXT_DIM));
        UiDraw.text(ctx, tr, UiDraw.fit(tr, value, maxW - lw), x + lw, y, c(Role.TEXT_MAIN));
    }

    // ---------------------------------------------------------------- действия

    private void doLogin() {
        String l = loginField.text().trim();
        String p = passField.text();
        if (l.isEmpty() || p.isEmpty()) {
            toast("Введите логин и пароль", false);
            return;
        }
        AuthManager.login(l, p).thenAccept(r -> {
            switch (r.status) {
                case AUTHORIZED -> {
                    passField.setText("");
                    setFocused(null);
                    toast("Вход выполнен", true);
                }
                case PENDING -> toast("Аккаунт ещё не подтверждён администратором", false);
                case DENIED -> toast(r.reason.isEmpty() ? "Отказано" : r.reason, false);
                default -> toast("API недоступен. Проверьте соединение.", false);
            }
        });
    }

    private void doRegister() {
        String l = loginField.text().trim();
        String p = passField.text();
        if (!l.matches("[A-Za-z0-9_.\\-]{3,32}")) {
            toast("Логин: 3–32 символа, A-Z a-z 0-9 _ . -", false);
            return;
        }
        if (p.length() < 6) {
            toast("Пароль: минимум 6 символов", false);
            return;
        }
        AuthManager.register(l, p).thenAccept(r -> {
            switch (r.status) {
                case PENDING, AUTHORIZED -> toast("Заявка отправлена. Дождитесь подтверждения.", true);
                case DENIED -> toast(r.reason.isEmpty() ? "Отказано" : r.reason, false);
                default -> toast("API недоступен. Проверьте соединение.", false);
            }
        });
    }

    private void doLogout() {
        AuthManager.logout();
        toast("Вы вышли из аккаунта", true);
    }
}