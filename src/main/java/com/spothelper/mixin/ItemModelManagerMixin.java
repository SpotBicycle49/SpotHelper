package com.spothelper.mixin;

import com.spothelper.config.SpotHelperConfig;
import com.spothelper.util.TotemTint;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Окраска тотемов HolyWorld. Все места, где рисуется предмет, проходят через ItemModelManager.
 * Предметы в руках других игроков идут через updateForLivingEntity, в GUI — через clearAndUpdate,
 * поэтому перехватываем все точки входа (несуществующие имена Mixin просто пропускает).
 * Для нужного тотема подменяется копия: модель spothelper:totem_* (ванильная модель + tint) и свечение.
 */
@Mixin(ItemModelManager.class)
public abstract class ItemModelManagerMixin {

    @ModifyVariable(
            method = {"update", "clearAndUpdate", "updateForLivingEntity", "updateForNonLivingEntity"},
            at = @At("HEAD"), argsOnly = true)
    private ItemStack spothelper$tintTotem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.TOTEM_OF_UNDYING)) return stack;
        if (!SpotHelperConfig.INSTANCE.isTotemColorsEnabled()) return stack;

        // уже подменён вложенным вызовом
        Identifier model = stack.get(DataComponentTypes.ITEM_MODEL);
        if (model != null && "spothelper".equals(model.getNamespace())) return stack;

        String id = TotemTint.modelFor(stack);
        if (id == null) return stack;

        ItemStack copy = stack.copy();
        copy.set(DataComponentTypes.ITEM_MODEL, Identifier.of("spothelper", id));
        copy.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        return copy;
    }
}
