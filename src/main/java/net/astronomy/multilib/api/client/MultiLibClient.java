package net.astronomy.multilib.api.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side API surface for consuming mods to configure MultiLib's auto-place modifier key.
 * <p>
 * This is deliberately <b>not</b> a vanilla {@code KeyMapping} and is never registered via
 * {@code RegisterKeyMappingsEvent} - it does not appear as an entry in the Controls menu, and
 * end-players cannot rebind it there. MultiLib is a library mod consumed by other mods, not a
 * gameplay mod played directly, so exposing a Controls-menu option here would be end-user-facing
 * surface that doesn't belong to any single game the player is playing.
 * <p>
 * Instead, the modifier key defaults to Left Ctrl and only changes if the <i>integrating mod's</i>
 * own client-side code explicitly calls {@link #setAutoPlaceModifierKey(int)} - e.g. during client
 * setup - to pick a different key for its own use of MultiLib's auto-place feature.
 */
@OnlyIn(Dist.CLIENT)
public class MultiLibClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiLibClient.class);

    private static int autoPlaceModifierKey = InputConstants.KEY_LCONTROL;

    /** {@code null} until a consuming mod calls {@link #setCategoryIcon(ItemLike)}. */
    private static Item categoryIcon = null;

    private MultiLibClient() {
    }

    /**
     * Sets the raw GLFW/LWJGL key code used as the auto-place modifier key. Intended to be called
     * by consuming mods from their own client-side setup code, not by MultiLib itself.
     *
     * @param glfwKeyCode a GLFW key constant, e.g. {@link InputConstants#KEY_LCONTROL}
     */
    public static void setAutoPlaceModifierKey(int glfwKeyCode) {
        autoPlaceModifierKey = glfwKeyCode;
    }

    /**
     * @return the raw GLFW/LWJGL key code currently configured as the auto-place modifier key.
     */
    public static int getAutoPlaceModifierKey() {
        return autoPlaceModifierKey;
    }

    /**
     * Checks whether the auto-place modifier key is currently held down, using raw input state
     * rather than a registered {@code KeyMapping}. This reads the same underlying static method
     * that {@code KeyMapping#isDown()} itself delegates to, so it correctly reflects real-time
     * held state without requiring a Controls-menu keybind.
     *
     * @return {@code true} if the configured modifier key is currently pressed.
     */
    public static boolean isAutoPlaceModifierDown() {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), autoPlaceModifierKey);
    }

    /**
     * Sets the item shown as the icon for MultiLib's single shared "Multiblock Structure" recipe
     * category in JEI/REI/EMI/FTB Quests. Intended to be called by consuming mods from their own
     * client-side setup code - MultiLib itself defaults to {@link Items#STRUCTURE_BLOCK}.
     * <p>
     * Every mod using MultiLib shares the <b>same</b> recipe category (see {@code MultiblockRecipeCategory}
     * in MultiLib's JEI compat), so only one icon can win if multiple mods call this with different
     * items. Resolution is first-registered-wins: whichever mod calls this first during startup sets
     * the icon, and later calls from other mods are ignored (logged at debug level) rather than picked
     * randomly or rotated - a stable icon across restarts matters more than any one mod's preference.
     * A modpack/player can always force a specific icon regardless of load order via the
     * {@code categoryIcon} client config option, which takes priority over whatever any mod sets here.
     *
     * @param item the item to use as the category icon
     */
    public static void setCategoryIcon(ItemLike item) {
        Item resolved = item.asItem();
        if (categoryIcon != null) {
            LOGGER.debug("Ignoring category icon override to {} - already set to {}",
                    ownerLabel(resolved), ownerLabel(categoryIcon));
            return;
        }
        categoryIcon = resolved;
    }

    private static String ownerLabel(Item item) {
        ResourceLocation loc = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        return loc.toString();
    }

    /**
     * @return the item stack currently configured as the shared multiblock category icon: the
     * {@code categoryIcon} client config value if non-empty (always wins), otherwise whatever a
     * consuming mod registered via {@link #setCategoryIcon(ItemLike)}, otherwise a plain structure
     * block.
     */
    public static ItemStack getCategoryIconStack() {
        String configId = net.astronomy.multilib.client.ClientConfig.CATEGORY_ICON.get();
        if (configId != null && !configId.isBlank()) {
            ResourceLocation loc = ResourceLocation.tryParse(configId);
            Item item = loc != null
                    ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(loc).orElse(null)
                    : null;
            if (item != null) return new ItemStack(item);
        }
        if (categoryIcon != null) return new ItemStack(categoryIcon);
        return new ItemStack(Items.STRUCTURE_BLOCK);
    }
}
