package doublemoon.mahjongcraft.paper.game;

public record WinContext(
        boolean ippatsu,
        boolean haitei,
        boolean houtei,
        boolean rinshan,
        boolean chankan
) {
    public static WinContext none() {
        return new WinContext(false, false, false, false, false);
    }
}
