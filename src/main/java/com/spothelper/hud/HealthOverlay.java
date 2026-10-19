package com.spothelper.hud;

import com.spothelper.config.SpotHelperConfig;
import com.spothelper.util.WorldToScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3x2fStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Подписи у сущности (в радиусе и в прямой видимости, не сквозь стены), только у игроков и мобов:
 *  • броня игроков — НАД ником: предметы с полоской прочности, как в инвентаре, и числом прочности;
 *  • здоровье — сразу под ником;
 *  • в HUD — панель с HP цели под прицелом (тоже только игрок или моб).
 * Стойки для брони, голограммы и прочие сущности без AI не подписываются.
 * Список сущностей обновляется раз в 100 мс, а не каждый кадр.
 */
public final class HealthOverlay {
    private HealthOverlay() {}

    private static final int MAX_LABELS = 40;
    private static final long REFRESH_NS = 100_000_000L;

    private static long lastRefresh = 0L;
    private static final List<LivingEntity> cached = new ArrayList<>();

    public static void render(DrawContext ctx, float tickDelta) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        boolean health = cfg.showHealth == 1;
        boolean armor = cfg.isArmorDurabilityEnabled();
        if (!health && !armor) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;

        TextRenderer tr = mc.textRenderer;
        boolean healthWorld = health && cfg.showHealthInWorld == 1;
        if (healthWorld || armor) renderWorld(ctx, mc, tr, cfg, tickDelta, healthWorld, armor);
        if (health && cfg.showHealthInHud == 1) renderHud(ctx, mc, tr);
    }

    /** Высота ряда брони в пикселях шрифта (предмет 16 + число прочности под ним). */
    private static final float ARMOR_ROW_H = 26f;

    /** HP и броню показываем только у игроков и мобов (не у стоек для брони, NPC-голограмм и т. п.). */
    private static boolean isTracked(LivingEntity e) {
        return e instanceof PlayerEntity || e instanceof MobEntity;
    }

    private static void refresh(MinecraftClient mc, int range) {
        long now = System.nanoTime();
        if (now - lastRefresh < REFRESH_NS) return;
        lastRefresh = now;

        cached.clear();
        Box box = mc.player.getBoundingBox().expand(range);
        List<LivingEntity> found = mc.world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != mc.player && isTracked(e) && e.isAlive() && !e.isSpectator()
                        && !e.isInvisible()
                        && e.squaredDistanceTo(mc.player) <= (double) range * range
                        && mc.player.canSee(e)); // только в прямой видимости, не сквозь стены
        found.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(mc.player)));
        for (int i = 0; i < found.size() && i < MAX_LABELS; i++) cached.add(found.get(i));
    }

    private static void renderWorld(DrawContext ctx, MinecraftClient mc, TextRenderer tr,
                                    SpotHelperConfig cfg, float tickDelta,
                                    boolean wantHealth, boolean wantArmor) {
        int range = Math.max(8, Math.min(64, cfg.healthOverlayRange));
        refresh(mc, range);
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        for (LivingEntity e : cached) {
            if (e.isRemoved() || !e.isAlive()) continue;
            Vec3d pos = e.getLerpedPos(tickDelta);

            // верх подписи с ником (ваниль: высота сущности + 0.5), строка ника = 10 px шрифта = 0.25 блока
            double anchorY = pos.y + e.getHeight() + 0.5;
            float[] a = WorldToScreen.project(mc, new Vec3d(pos.x, anchorY, pos.z));
            float[] b = WorldToScreen.project(mc, new Vec3d(pos.x, anchorY - 0.25, pos.z));
            if (a == null || b == null) continue;
            if (a[0] < -60 || a[0] > sw + 60 || a[1] < -40 || a[1] > sh + 40) continue;

            float s = (b[1] - a[1]) / 10f; // GUI-пикселей на пиксель шрифта на этой дистанции
            if (s < 0.3f) continue;        // слишком далеко — не рисуем
            s = Math.min(s, 4f);

            Matrix3x2fStack m = ctx.getMatrices();

            // броня игроков — НАД ником: нижний край ряда прижат к верху строки с ником
            if (wantArmor && e instanceof PlayerEntity pl) {
                drawArmorRow(ctx, tr, pl, a[0], a[1] - ARMOR_ROW_H * s, s);
            }

            if (wantHealth) {
                float hp = Math.max(0f, e.getHealth());
                float ratio = Math.min(1f, hp / Math.max(1f, e.getMaxHealth()));
                String text = (hp == Math.floor(hp) ? String.valueOf((int) hp) : String.format("%.1f", hp)) + " HP";
                m.pushMatrix();
                m.translate(a[0], b[1]); // верх следующей строки — сразу под ником
                m.scale(s, s);
                ctx.drawCenteredTextWithShadow(tr, text, 0, 0, healthColor(ratio));
                m.popMatrix();
            }
        }
    }

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Ряд брони: предмет + полоска прочности (как в инвентаре) + число оставшейся прочности под ним. */
    private static void drawArmorRow(DrawContext ctx, TextRenderer tr, PlayerEntity p, float cx, float y, float s) {
        List<ItemStack> pieces = new ArrayList<>();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack st = p.getEquippedStack(slot);
            if (!st.isEmpty()) pieces.add(st);
        }
        if (pieces.isEmpty()) return;

        final int cell = 20; // ширина ячейки в пикселях шрифта
        int total = pieces.size() * cell;
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, y + 1f * s);
        m.scale(s, s);
        int x0 = -total / 2 + 2;
        for (int i = 0; i < pieces.size(); i++) {
            ItemStack st = pieces.get(i);
            int x = x0 + i * cell;
            ctx.drawItem(st, x, 0);
            drawDurabilityBar(ctx, st, x, 0);
            if (st.isDamageable()) {
                int left = st.getMaxDamage() - st.getDamage();
                float pct = left / (float) st.getMaxDamage();
                m.pushMatrix();
                m.translate(x + 8f, 17f);
                m.scale(0.7f, 0.7f);
                ctx.drawCenteredTextWithShadow(tr, String.valueOf(left), 0, 0, healthColor(pct));
                m.popMatrix();
            }
        }
        m.popMatrix();
    }

    /** Полоска прочности под предметом — как в слоте инвентаря (видна только у повреждённых). */
    public static void drawDurabilityBar(DrawContext ctx, ItemStack st, int x, int y) {
        if (!st.isItemBarVisible()) return;
        int step = st.getItemBarStep();
        int color = st.getItemBarColor();
        ctx.fill(x + 2, y + 13, x + 15, y + 15, 0xFF000000);
        ctx.fill(x + 2, y + 13, x + 2 + step, y + 14, 0xFF000000 | color);
    }

    private static int healthColor(float ratio) {
        ratio = Math.max(0f, Math.min(1f, ratio));
        int r = ratio > 0.5f ? Math.round(255 * (1f - ratio) * 2f) : 255;
        int g = ratio > 0.5f ? 255 : Math.round(255 * ratio * 2f);
        return 0xFF000000 | (r << 16) | (g << 8) | 0x30;
    }

    private static void renderHud(DrawContext ctx, MinecraftClient mc, TextRenderer tr) {
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof EntityHitResult eh) || !(eh.getEntity() instanceof LivingEntity target)) return;
        if (!target.isAlive() || !isTracked(target)) return;

        int sw = mc.getWindow().getScaledWidth();
        int cx = sw / 2;
        int y = 18;
        String name = target.getName().getString();
        ctx.drawCenteredTextWithShadow(tr, name, cx, y, 0xFFFFFFFF);
        drawBar(ctx, tr, cx, y + 22, target, 90);
    }

    /** Полоска здоровья шириной width с числом HP над ней; (cx, y) — центр по X и верх полоски минус 10. */
    private static void drawBar(DrawContext ctx, TextRenderer tr, int cx, int y, LivingEntity e, int width) {
        float hp = Math.max(0f, e.getHealth());
        float max = Math.max(1f, e.getMaxHealth());
        float ratio = Math.min(1f, hp / max);

        int x1 = cx - width / 2;
        int x2 = x1 + width;
        int by = y - 4;

        // цвет: зелёный -> жёлтый -> красный
        int r = ratio > 0.5f ? Math.round(255 * (1f - ratio) * 2f) : 255;
        int g = ratio > 0.5f ? 255 : Math.round(255 * ratio * 2f);
        int fill = 0xFF000000 | (r << 16) | (g << 8) | 0x30;

        ctx.fill(x1 - 1, by - 1, x2 + 1, by + 5, 0xFF000000);
        ctx.fill(x1, by, x2, by + 4, 0xFF3A3A3A);
        ctx.fill(x1, by, x1 + Math.round(width * ratio), by + 4, fill);

        String text = (hp == Math.floor(hp) ? String.valueOf((int) hp) : String.format("%.1f", hp)) + " HP";
        ctx.drawCenteredTextWithShadow(tr, text, cx, by - 10, 0xFFFFFFFF);
    }
}