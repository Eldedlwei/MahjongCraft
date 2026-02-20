package doublemoon.mahjongcraft.paper.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public enum MahjongTile {
    M1("1m", "m1", 0),
    M2("2m", "m2", 1),
    M3("3m", "m3", 2),
    M4("4m", "m4", 3),
    M5("5m", "m5", 4),
    M5_RED("5mr", "m5_red", 4),
    M6("6m", "m6", 5),
    M7("7m", "m7", 6),
    M8("8m", "m8", 7),
    M9("9m", "m9", 8),

    P1("1p", "p1", 9),
    P2("2p", "p2", 10),
    P3("3p", "p3", 11),
    P4("4p", "p4", 12),
    P5("5p", "p5", 13),
    P5_RED("5pr", "p5_red", 13),
    P6("6p", "p6", 14),
    P7("7p", "p7", 15),
    P8("8p", "p8", 16),
    P9("9p", "p9", 17),

    S1("1s", "s1", 18),
    S2("2s", "s2", 19),
    S3("3s", "s3", 20),
    S4("4s", "s4", 21),
    S5("5s", "s5", 22),
    S5_RED("5sr", "s5_red", 22),
    S6("6s", "s6", 23),
    S7("7s", "s7", 24),
    S8("8s", "s8", 25),
    S9("9s", "s9", 26),

    EAST("east", "east", 27),
    SOUTH("south", "south", 28),
    WEST("west", "west", 29),
    NORTH("north", "north", 30),
    WHITE("white", "white_dragon", 31),
    GREEN("green", "green_dragon", 32),
    RED("red", "red_dragon", 33);

    public static final Comparator<MahjongTile> SORTER =
            Comparator.comparingInt((MahjongTile tile) -> tile.sortOrder).thenComparing(tile -> tile.red ? 0 : 1);
    private static final MahjongTile[] BY_SORT_ORDER = new MahjongTile[34];
    private static final java.util.Map<String, MahjongTile> PARSE_MAP = new java.util.HashMap<>();

    static {
        for (MahjongTile tile : values()) {
            if (!tile.red) {
                BY_SORT_ORDER[tile.sortOrder] = tile;
            }
            PARSE_MAP.put(tile.shortName, tile);
            PARSE_MAP.put(tile.name().toLowerCase(Locale.ROOT), tile);
        }
    }

    private final String shortName;
    private final String textureKey;
    private final int sortOrder;
    private final boolean red;

    MahjongTile(String shortName, String textureKey, int sortOrder) {
        this.shortName = shortName;
        this.textureKey = textureKey;
        this.sortOrder = sortOrder;
        this.red = shortName.endsWith("r");
    }

    public String shortName() {
        return shortName;
    }

    public boolean isRed() {
        return red;
    }

    public String textureKey() {
        return textureKey;
    }

    public int sortOrder() {
        return sortOrder;
    }

    public boolean isHonor() {
        return sortOrder >= 27;
    }

    public boolean isSuit() {
        return sortOrder <= 26;
    }

    public int suitIndex() {
        return isSuit() ? sortOrder / 9 : -1;
    }

    public int number() {
        return isSuit() ? (sortOrder % 9) + 1 : -1;
    }

    public boolean isTerminal() {
        return isSuit() && (number() == 1 || number() == 9);
    }

    public boolean isSimple() {
        return isSuit() && number() >= 2 && number() <= 8;
    }

    public MahjongTile normalized() {
        return fromSortOrder(sortOrder);
    }

    public boolean sameType(MahjongTile other) {
        return other != null && this.sortOrder == other.sortOrder;
    }

    public static MahjongTile fromSortOrder(int sortOrder) {
        if (sortOrder < 0 || sortOrder >= BY_SORT_ORDER.length) {
            throw new IllegalArgumentException("Invalid sort order: " + sortOrder);
        }
        MahjongTile tile = BY_SORT_ORDER[sortOrder];
        if (tile == null) {
            throw new IllegalArgumentException("Invalid sort order: " + sortOrder);
        }
        return tile;
    }

    public static MahjongTile parse(String input) {
        String key = input.toLowerCase(Locale.ROOT);
        return PARSE_MAP.get(key);
    }

    public static List<MahjongTile> buildWallWithThreeRedFives() {
        List<MahjongTile> wall = new ArrayList<>(136);
        for (MahjongTile tile : values()) {
            int copies = switch (tile) {
                case M5_RED, P5_RED, S5_RED -> 0;
                case M5, P5, S5 -> 3;
                default -> 4;
            };
            if (copies == 0) {
                continue;
            }
            for (int i = 0; i < copies; i++) {
                wall.add(tile);
            }
        }
        wall.add(M5_RED);
        wall.add(P5_RED);
        wall.add(S5_RED);
        return wall;
    }
}
