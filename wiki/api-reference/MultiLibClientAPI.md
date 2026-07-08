[← Back to Home](../index.md)

# `MultiLibClientAPI`

Package: `net.astronomy.multilib.api.client` — **`@OnlyIn(Dist.CLIENT)`**

Client-only configuration surface for consuming mods. Call these from your own client-setup code (e.g. `FMLClientSetupEvent`), never from common/server code. All methods are static.

MultiLib is a library consumed by other mods, not a gameplay mod played directly, so it deliberately exposes **no Controls-menu keybind and no in-game settings screen** here — the integrating mod decides what (if anything) to surface to players.

## Auto-place modifier key

The [auto-place](../Advanced-Features.md#auto-place) feature triggers on a held modifier key plus right-click. It defaults to **Left Ctrl** and is *not* a vanilla `KeyMapping` (so it never appears in the Controls menu and players can't rebind it there). A consuming mod can change it:

### `setAutoPlaceModifierKey(int glfwKeyCode)`
```java
public static void setAutoPlaceModifierKey(int glfwKeyCode)
```
Sets the raw GLFW key code used as the modifier — e.g. `InputConstants.KEY_LCONTROL`. Call from your client setup to pick a different key for your mod's use of auto-place.

### `getAutoPlaceModifierKey()` / `isAutoPlaceModifierDown()`
```java
public static int getAutoPlaceModifierKey()
public static boolean isAutoPlaceModifierDown()
```
Reads the configured key code, or whether it's currently held (using raw input state, the same source `KeyMapping#isDown()` delegates to — no registered keybind needed).

## Recipe category icon

Every mod using MultiLib shares one **"Multiblock Structure"** recipe category in JEI/REI/EMI/FTB Quests. Its icon defaults to `minecraft:structure_block`.

### `setCategoryIcon(ItemLike item)`
```java
public static void setCategoryIcon(ItemLike item)
```
Sets the shared category icon. Because the category is shared, resolution is **first-registered-wins**: the first mod to call this during startup sets the icon; later calls from other mods are ignored (logged at debug) rather than fighting over it. A pack/player can always override via the [`categoryIcon`](../Configuration.md#clientconfig-configmultilibclienttoml) client config, which takes priority over any mod's call.

### `getCategoryIconStack()`
```java
public static ItemStack getCategoryIconStack()
```
The icon actually used, in priority order: the `categoryIcon` config value (if non-empty) → the mod-registered icon → a plain structure block.

## See also

- [Configuration](../Configuration.md) — the `categoryIcon` client config that overrides `setCategoryIcon`
- [Advanced Features § Auto-place](../Advanced-Features.md#auto-place) — the feature the modifier key drives
- [MultiLibAPI](MultiLibAPI.md) — the common-side (non-client) entry point
