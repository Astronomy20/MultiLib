package net.astronomy.multilib.compat.ftbquests;

import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import net.astronomy.multilib.compat.MultiblockPreviewPanel;

/**
 * Gate + registration point for the FTB Quests compat module. This is the only class in
 * {@code compat/ftbquests} referenced from outside this package (via reflection, from
 * {@code MultiLib.java} - see the comment there for why reflection is used instead of a direct
 * import). Nothing outside this package should import FTB Quests classes.
 * <p>
 * Design verified against the real FTB Quests source on the {@code 1.21.1/main} branch of
 * {@code FTBTeam/FTB-Quests} (NOT {@code main}, which is a future multi-loader refactor using
 * {@code Identifier}/Json5 instead of {@code ResourceLocation}/{@code CompoundTag}).
 */
public final class FtbQuestsCompat {
    /** Named "multiblock", not "multiblock_formed" - the task doesn't necessarily complete on formation
     *  alone, it can require a specific {@code requiredState} reached well after formation. */
    public static TaskType MULTIBLOCK;

    private FtbQuestsCompat() {}

    /** Called once from {@code MultiLib}'s {@code FMLLoadCompleteEvent} listener, only if FTB Quests is loaded. */
    public static void init() {
        MULTIBLOCK = TaskTypes.register(
                FTBQuestsAPI.rl("multiblock"),
                MultiblockTask::new,
                // Same source as JEI/REI/EMI's recipe category icon (ClientConfig#CATEGORY_ICON via
                // MultiblockPreviewPanel#categoryIconStack) rather than a hardcoded item, so a modpack
                // that reconfigures the category icon gets a matching task-type icon for free. This is
                // only the generic "no multiblock configured yet" icon - once a task picks one,
                // MultiblockTask#getAltIcon shows that specific structure's core/activation block instead.
                () -> ItemIcon.getItemIcon(MultiblockPreviewPanel.categoryIconStack())
        );
        MultiblockQuestEventListener.register();
    }
}
