package net.astronomy.multilib.compat.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.AbstractBooleanTask;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.astronomy.multilib.api.MultiLibAPI;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.state.MultiblockState;
import net.astronomy.multilib.api.state.MultiblockStateRegistry;
import net.astronomy.multilib.client.RecipeViewerLink;
import net.astronomy.multilib.compat.MultiblockRecipeDisplay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * FTB Quests task type completed once a player drives a MultiLib multiblock to a given state (or
 * simply forms it, when {@code requiredState} is unset).
 * <p>
 * Purely event-driven: completion is pushed by {@link MultiblockQuestEventListener} the instant a
 * matching {@code MultiblockFormedEvent}/{@code MultiblockStateChangedEvent} fires — never by polling a
 * persisted "ever reached" record, never on login, and never by clicking the task:
 * <ul>
 *   <li>{@link #autoSubmitOnPlayerTick()} returns 0 so FTB Quests never polls {@link #canSubmit} on its
 *       own periodic timer.</li>
 *   <li>{@link #checkOnLogin()} returns false so {@code ServerQuestFile}'s login handler (which calls
 *       {@code submitTask} on every task where it's true, for every unlocked quest) never touches this
 *       task — this was the actual cause of "the quest completes itself every time I open the world":
 *       {@code checkOnLogin} defaults to true, and this task's {@code canSubmit} only ever checked
 *       "is the quest unlocked", which is true almost immediately.</li>
 *   <li>{@link #onButtonClicked} does NOT call {@code super} (which sends a manual
 *       {@code SubmitTaskMessage} whenever {@code autoSubmitOnPlayerTick() <= 0} — exactly the click-to-
 *       complete behavior meant for {@code CheckmarkTask}, not us). It opens the recipe viewer instead.</li>
 * </ul>
 * A formation event alone only ever satisfies a task with no {@code requiredState}: reaching a specific
 * state (including {@code idle}) requires an actual {@code MultiblockStateChangedEvent}, which only
 * fires for multiblocks with a real {@code AbstractMultiblockControllerBE} controller. A JSON-only
 * multiblock (no controller block entity) has no state concept at all, so a task configured with a
 * {@code requiredState} for one will simply never complete — pick "Any" for those.
 */
public class MultiblockTask extends AbstractBooleanTask {
    private static final ResourceLocation UNSET = ResourceLocation.fromNamespaceAndPath("multilib", "unset");
    /** Dropdown sentinel for "no specific state required" — distinct from any id a real registered
     *  {@link MultiblockState} could have, so it can sit in the same enum list as actual states. */
    private static final ResourceLocation NO_STATE = ResourceLocation.fromNamespaceAndPath("multilib", "any");

    private ResourceLocation multiblockId = UNSET;
    /** Null = matches on formation alone (any state, including "no state info" from a controller-less multiblock). */
    private ResourceLocation requiredState = null;

    public MultiblockTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbQuestsCompat.MULTIBLOCK;
    }

    /** Disabled — see class javadoc. Only {@link MultiblockQuestEventListener} ever calls {@link #submitTask}. */
    @Override
    public int autoSubmitOnPlayerTick() {
        return 0;
    }

    /** Disabled — see class javadoc. Prevents {@code ServerQuestFile}'s login handler from auto-completing this task. */
    @Override
    public boolean checkOnLogin() {
        return false;
    }

    /**
     * Gate for the push from {@link MultiblockQuestEventListener}: is this task actually configured, and
     * is its quest currently workable for the team? Does NOT re-check the multiblock/state itself — the
     * caller already knows the right event just fired for the right definition/state (see {@link #matches}).
     */
    @Override
    public boolean canSubmit(TeamData teamData, ServerPlayer player) {
        return !multiblockId.equals(UNSET) && teamData.canStartTasks(getQuest());
    }

    /**
     * Whether a {@code definitionId}/{@code stateId} pair just observed in the world satisfies this
     * task's configuration. {@code stateId} is null for a plain formation event with no further state
     * info (see {@link MultiblockQuestEventListener#onFormed}) — that only satisfies a task with no
     * {@code requiredState}.
     */
    boolean matches(ResourceLocation definitionId, @Nullable ResourceLocation stateId) {
        if (!multiblockId.equals(definitionId)) return false;
        if (requiredState == null) return true;
        return stateId != null && requiredState.equals(stateId);
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putString("multiblock", multiblockId.toString());
        if (requiredState != null) {
            nbt.putString("required_state", requiredState.toString());
        }
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        ResourceLocation parsed = ResourceLocation.tryParse(nbt.getString("multiblock"));
        multiblockId = parsed != null ? parsed : UNSET;
        requiredState = nbt.contains("required_state")
                ? ResourceLocation.tryParse(nbt.getString("required_state"))
                : null;
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeResourceLocation(multiblockId);
        buffer.writeBoolean(requiredState != null);
        if (requiredState != null) {
            buffer.writeResourceLocation(requiredState);
        }
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        multiblockId = buffer.readResourceLocation();
        requiredState = buffer.readBoolean() ? buffer.readResourceLocation() : null;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public MutableComponent getAltTitle() {
        return Component.translatable("ftbquests.task.multilib.multiblock").append(": ")
                .append(multiblockTitleName(multiblockId).withStyle(ChatFormatting.YELLOW));
    }

    /** The configured multiblock's own core/activation block icon, falling back to the generic type icon if unset. */
    @Override
    @Environment(EnvType.CLIENT)
    public Icon getAltIcon() {
        return MultiLibAPI.getDefinition(multiblockId)
                .map(MultiblockRecipeDisplay::catalystStack)
                .filter(stack -> !stack.isEmpty())
                .<Icon>map(ItemIcon::getItemIcon)
                .orElseGet(super::getAltIcon);
    }

    /**
     * Opens the recipe viewer (JEI/REI/EMI, whichever is installed) on this task's configured multiblock
     * instead of the default "manual complete" click behavior — see class javadoc. No-ops (via
     * {@link RecipeViewerLink#open}) if no viewer is installed or no multiblock is configured yet.
     */
    @Override
    @Environment(EnvType.CLIENT)
    public void onButtonClicked(Button button, boolean canClick) {
        button.playClickSound();
        MultiLibAPI.getDefinition(multiblockId).ifPresent(RecipeViewerLink::open);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);

        List<ResourceLocation> definitionIds = new ArrayList<>();
        for (MultiblockDefinition def : MultiLibAPI.getAllDefinitions()) {
            definitionIds.add(def.getId());
        }
        if (definitionIds.isEmpty()) definitionIds.add(UNSET);
        config.add("multiblock", new SearchableEnumConfig<>(
                NameMap.of(definitionIds.contains(multiblockId) ? multiblockId : definitionIds.get(0), definitionIds)
                        .id(ResourceLocation::toString)
                        // Core/activation block icon per entry, same stack JEI/REI/EMI use as this
                        // structure's catalyst — ItemStack.EMPTY safely renders as no icon.
                        .icon(id -> ItemIcon.getItemIcon(MultiLibAPI.getDefinition(id)
                                .map(MultiblockRecipeDisplay::catalystStack)
                                .orElse(ItemStack.EMPTY)))
                        // "Display Name [namespace:path]" while editing, for disambiguation — the task's
                        // own title (getAltTitle) shows the name alone, see multiblockTitleName.
                        .name(MultiblockTask::multiblockListEntryName)
                        .create()
        ), multiblockId, v -> multiblockId = v, multiblockId).setNameKey("ftbquests.task.multilib.multiblock");

        List<ResourceLocation> stateIds = new ArrayList<>();
        stateIds.add(NO_STATE);
        for (MultiblockState state : MultiblockStateRegistry.getAll()) {
            stateIds.add(state.getId());
        }
        ResourceLocation currentStateId = requiredState != null && stateIds.contains(requiredState)
                ? requiredState : NO_STATE;
        // Deliberately no .icon(...) here — unlike multiblocks, states aren't visually distinguishable
        // by a block/item, so no icon is shown at all (not even a placeholder).
        config.add("required_state", new SearchableEnumConfig<>(
                NameMap.of(NO_STATE, stateIds)
                        .id(ResourceLocation::toString)
                        .name(id -> NO_STATE.equals(id)
                                ? Component.translatable("ftbquests.task.multilib.required_state.any")
                                : stateListEntryName(id))
                        .create()
        ), currentStateId, v -> requiredState = NO_STATE.equals(v) ? null : v, NO_STATE)
                .setNameKey("ftbquests.task.multilib.required_state");
    }

    /** For the task's own title: the display name alone if set, otherwise the raw id — never both. */
    private static MutableComponent multiblockTitleName(ResourceLocation id) {
        return MultiLibAPI.getDefinition(id)
                .flatMap(MultiblockDefinition::getNameTranslationKey)
                .<MutableComponent>map(Component::translatable)
                .orElseGet(() -> Component.literal(id.toString()));
    }

    /** For the picker list: "Display Name [namespace:path]" when set, else just the id — disambiguates while editing. */
    private static MutableComponent multiblockListEntryName(ResourceLocation id) {
        return MultiLibAPI.getDefinition(id)
                .flatMap(MultiblockDefinition::getNameTranslationKey)
                .<MutableComponent>map(key -> Component.translatable(key).append(
                        Component.literal(" [" + id + "]").withStyle(ChatFormatting.GRAY)))
                .orElseGet(() -> Component.literal(id.toString()));
    }

    /** Same "Display Name [namespace:path]" style as {@link #multiblockListEntryName}, for the state picker list. */
    private static MutableComponent stateListEntryName(ResourceLocation id) {
        return MultiblockStateRegistry.get(id)
                .flatMap(MultiblockState::getNameTranslationKey)
                .<MutableComponent>map(key -> Component.translatable(key).append(
                        Component.literal(" [" + id + "]").withStyle(ChatFormatting.GRAY)))
                .orElseGet(() -> Component.literal(id.toString()));
    }
}
