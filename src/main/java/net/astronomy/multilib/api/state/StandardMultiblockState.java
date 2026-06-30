package net.astronomy.multilib.api.state;

public final class StandardMultiblockState implements MultiblockState {
    public static final StandardMultiblockState UNFORMED = new StandardMultiblockState("multilib:unformed");
    public static final StandardMultiblockState IDLE     = new StandardMultiblockState("multilib:idle");
    public static final StandardMultiblockState RUNNING  = new StandardMultiblockState("multilib:running");
    public static final StandardMultiblockState ERROR    = new StandardMultiblockState("multilib:error");

    private final String id;

    private StandardMultiblockState(String id) {
        this.id = id;
    }

    @Override
    public String getId() { return id; }

    @Override
    public String toString() { return id; }
}
