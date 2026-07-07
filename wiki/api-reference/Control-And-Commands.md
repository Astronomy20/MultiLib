[← Back to Home](../index.md)

# Control Helpers & Admin Commands

Packages: `net.astronomy.multilib.api.control` (`RedstoneControlComponent`, `ComparatorOutputs`, `OwnershipComponent`), `net.astronomy.multilib.command` (`MultiblockCommands`)

Small self-contained helpers for the machine-control patterns every mod re-derives (redstone gating, comparator scaling, ownership), plus the `/multilib` admin command tree. The API classes are query-only mechanisms - none of them auto-wires events, blocks players, or emits feedback on its own.

## `RedstoneControlComponent`

```java
public final class RedstoneControlComponent {
    public enum RedstoneMode { IGNORE, REQUIRE_HIGH, REQUIRE_LOW, PULSE }

    public RedstoneMode getMode();
    public void setMode(RedstoneMode mode);
    public boolean shouldRun(ServerLevel level, BlockPos pos);
    public void onNeighborChanged(boolean powered);
    public boolean isPulsePending();
    public boolean consumePulse();
    public void save(CompoundTag tag);
    public void load(CompoundTag tag);
}
```

Embed one in your controller BE and ask `shouldRun(level, pos)` from your tick logic - it evaluates `level.hasNeighborSignal(pos)` per mode. Nothing is wired automatically: **you** call `onNeighborChanged(powered)` from your block's `neighborChanged` override.

- `IGNORE` - always runs. `REQUIRE_HIGH` / `REQUIRE_LOW` - runs only while powered / unpowered.
- `PULSE` - `onNeighborChanged` rising-edge-detects and latches one pending pulse; `shouldRun` (or your own logic via `consumePulse()`) consumes it. `isPulsePending()` peeks without consuming.
- `save`/`load` persist mode + pulse latch. Pairs naturally with [`RecipeProcessor.setEnabled`](Process-Engine.md).

## `ComparatorOutputs`

```java
public final class ComparatorOutputs {
    public static int fromFraction(double fraction);
    public static int fromStoredEnergy(long stored, long capacity);
    public static int fromFluid(int amount, int capacity);
    public static int scaled(int value, int max);
}
```

Vanilla 0-15 comparator levels from common sources, all following the vanilla container convention (`AbstractContainerMenu.getRedstoneSignalFromContainer` semantics): **0 only when truly empty, at least 1 otherwise, 15 only when full** - the off-by-one everyone gets wrong once. Return these from your block's `getAnalogOutputSignal`.

## `OwnershipComponent`

```java
public final class OwnershipComponent {
    public Optional<UUID> getOwner();
    public void setOwner(UUID owner);
    public void setOwner(Optional<UUID> owner);
    public void clearOwner();
    public void setOwnerFromFormedBy(MultiblockInstance instance);
    public boolean isOwner(Player player);
    public void setAccessPolicy(BiPredicate<OwnershipComponent, Player> accessPolicy);
    public boolean canAccess(Player player);
    public void save(CompoundTag tag);
    public void load(CompoundTag tag);
}
```

Owner tracking with a pluggable access predicate. `setOwnerFromFormedBy(instance)` adopts the player who formed the structure (from the instance's `formedBy`). The default access policy is **allow everyone** - MultiLib never gates access on its own; if you want owner-only interaction, set a policy and check `canAccess(player)` in your own interaction code.

## `/multilib` admin commands

`MultiblockCommands` self-registers on `RegisterCommandsEvent` (no init call needed). Requires permission level 2. All feedback is translatable (`command.multilib.*` keys). Every subcommand works from the console/command blocks except radius filtering, which is relative to a player.

| Command | What it does |
|---|---|
| `/multilib list` | Every registered definition id with its source (`JAVA`/`JSON`/`KUBEJS`) and a count. |
| `/multilib info <id>` | One definition's details: formation mode, rotation mode, dimensions/layer count, candidate blocks, priority. Suggests registered ids. |
| `/multilib instances [radius]` | Formed instances in the sender's level (id, definition, origin), optionally within `radius` blocks of the sender (player-only). |
| `/multilib form <pos>` | Attempts formation at `pos`, trying every candidate definition for the block there. Distinguishes "already formed" / "formed" / "no candidate matched". |
| `/multilib unform <pos>` | Tears down every tracked instance at `pos` (runs `onBroken` callbacks; does not remove blocks). |

Limitation, by design of the underlying API: `form` cannot target one specific definition id - `BlockActivationHandler.triggerFormationAt` tries all candidates for the block at `pos` and forms the first match.

## See also

- [Process Engine](Process-Engine.md) - `setEnabled` is the natural sink for `shouldRun`.
- [Components](Components.md) - buffers whose fill levels feed `ComparatorOutputs`.
- [HUD Providers](HUD-Providers.md) - `RedstoneControlHudProvider`/`OwnershipHudProvider` surface these on hover.
- [MultiblockInstance & Registry](MultiblockInstance-And-Registry.md) - where `formedBy` comes from.
