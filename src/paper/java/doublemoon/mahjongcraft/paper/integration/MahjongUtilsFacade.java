package doublemoon.mahjongcraft.paper.integration;

import doublemoon.mahjongcraft.paper.game.MahjongMeld;
import doublemoon.mahjongcraft.paper.game.MahjongTile;
import doublemoon.mahjongcraft.paper.game.MeldType;
import doublemoon.mahjongcraft.paper.game.Wind;
import mahjongutils.hora.Hora;
import mahjongutils.hora.HoraKt;
import mahjongutils.hora.HoraOptions;
import mahjongutils.models.Furo;
import mahjongutils.models.Tile;
import mahjongutils.shanten.ShantenKt;
import mahjongutils.shanten.UnionShantenResult;
import mahjongutils.yaku.Yaku;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MahjongUtilsFacade {
    private static final MahjongUtilsFacade INSTANCE = new MahjongUtilsFacade();
    private static final Set<Yaku> NO_EXTRA_YAKU = Set.of();

    private final boolean available;
    private final Method tileGetByString;
    private final Method tileBox;
    private final Method tileUnbox;
    private final Method furoCtor;
    private final Method furoBox;
    private final Method horaTilesMethod;
    private final Method parentRon;
    private final Method parentTsumoEach;
    private final Method childRon;
    private final Method childTsumoParent;
    private final Method childTsumoChild;
    private final HoraOptions defaultOptions;
    private final Map<MahjongTile, Tile> tileCache = new ConcurrentHashMap<>();
    private final Map<MahjongTile, Integer> tileCodeCache = new ConcurrentHashMap<>();

    private MahjongUtilsFacade() {
        boolean ok = false;
        Method getByString = null;
        Method boxTile = null;
        Method unboxTile = null;
        Method makeFuro = null;
        Method boxFuro = null;
        Method horaMethod = null;
        Method pRon = null;
        Method pTsumoEach = null;
        Method cRon = null;
        Method cTsumoParent = null;
        Method cTsumoChild = null;
        HoraOptions options = null;
        try {
            Method[] tileMethods = Tile.Companion.getClass().getMethods();
            for (Method method : tileMethods) {
                if (method.getName().startsWith("get-") && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == String.class
                        && method.getReturnType() == int.class) {
                    getByString = method;
                    break;
                }
            }
            boxTile = Tile.class.getMethod("box-impl", int.class);
            unboxTile = Tile.class.getMethod("unbox-impl");

            makeFuro = Class.forName("mahjongutils.models.FuroKt").getMethod("Furo", List.class, boolean.class);
            boxFuro = Furo.class.getMethod("box-impl", int.class);

            for (Method method : HoraKt.class.getMethods()) {
                if (method.getName().startsWith("hora-") && method.getParameterCount() == 9) {
                    horaMethod = method;
                    break;
                }
            }

            pRon = Class.forName("mahjongutils.hanhu.ParentPoint").getMethod("getRon-s-VKNKU");
            pTsumoEach = Class.forName("mahjongutils.hanhu.ParentPoint").getMethod("getTsumo-s-VKNKU");
            cRon = Class.forName("mahjongutils.hanhu.ChildPoint").getMethod("getRon-s-VKNKU");
            cTsumoParent = Class.forName("mahjongutils.hanhu.ChildPoint").getMethod("getTsumoParent-s-VKNKU");
            cTsumoChild = Class.forName("mahjongutils.hanhu.ChildPoint").getMethod("getTsumoChild-s-VKNKU");

            options = HoraOptions.Companion.getDefault();
            ok = getByString != null && boxTile != null && unboxTile != null && makeFuro != null && boxFuro != null
                    && horaMethod != null && pRon != null && pTsumoEach != null && cRon != null
                    && cTsumoParent != null && cTsumoChild != null && options != null;
        } catch (Throwable ignored) {
            ok = false;
        }
        this.available = ok;
        this.tileGetByString = getByString;
        this.tileBox = boxTile;
        this.tileUnbox = unboxTile;
        this.furoCtor = makeFuro;
        this.furoBox = boxFuro;
        this.horaTilesMethod = horaMethod;
        this.parentRon = pRon;
        this.parentTsumoEach = pTsumoEach;
        this.childRon = cRon;
        this.childTsumoParent = cTsumoParent;
        this.childTsumoChild = cTsumoChild;
        this.defaultOptions = options;
    }

    public static MahjongUtilsFacade get() {
        return INSTANCE;
    }

    public boolean isAvailable() {
        return available;
    }

    public Integer shantenNum(List<MahjongTile> tiles, List<MahjongMeld> melds) {
        if (!available || tiles == null) {
            return null;
        }
        try {
            List<Tile> mjTiles = toMjTiles(tiles);
            List<Furo> mjFuro = toMjFuro(melds);
            UnionShantenResult result = ShantenKt.shanten(mjTiles, mjFuro, true);
            return result.getShantenInfo().getShantenNum();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public Boolean isWinning(List<MahjongTile> tiles, List<MahjongMeld> melds) {
        Integer shanten = shantenNum(tiles, melds);
        return shanten == null ? null : shanten <= -1;
    }

    public Boolean isTenpai(List<MahjongTile> tiles, List<MahjongMeld> melds) {
        Integer shanten = shantenNum(tiles, melds);
        return shanten == null ? null : shanten == 0;
    }

    public HoraResult hora(
            List<MahjongTile> handTiles,
            List<MahjongMeld> melds,
            MahjongTile agari,
            boolean tsumo,
            Wind selfWind,
            Wind roundWind
    ) {
        if (!available || handTiles == null || agari == null) {
            return null;
        }
        try {
            List<Tile> mjTiles = toMjTiles(handTiles);
            List<Furo> mjFuro = toMjFuro(melds);
            int agariCode = toTileCode(agari);
            Object horaObj = horaTilesMethod.invoke(
                    null,
                    mjTiles,
                    mjFuro,
                    agariCode,
                    tsumo,
                    0,
                    toMjWind(selfWind),
                    toMjWind(roundWind),
                    NO_EXTRA_YAKU,
                    defaultOptions
            );
            if (!(horaObj instanceof Hora hora)) {
                return null;
            }

            List<String> yakuNames = new ArrayList<>();
            for (Yaku yaku : hora.getYaku()) {
                yakuNames.add(yaku.getName());
            }

            int ronDealer = unsignedToInt(parentRon.invoke(hora.getParentPoint()));
            int tsumoDealerEach = unsignedToInt(parentTsumoEach.invoke(hora.getParentPoint()));
            int ronChild = unsignedToInt(childRon.invoke(hora.getChildPoint()));
            int tsumoChildDealerPay = unsignedToInt(childTsumoParent.invoke(hora.getChildPoint()));
            int tsumoChildOtherPay = unsignedToInt(childTsumoChild.invoke(hora.getChildPoint()));

            return new HoraResult(
                    hora.getHan(),
                    hora.getHu(),
                    hora.getHasYakuman(),
                    yakuNames,
                    ronDealer,
                    ronChild,
                    tsumoDealerEach,
                    tsumoChildDealerPay,
                    tsumoChildOtherPay
            );
        } catch (Throwable ignored) {
            return null;
        }
    }

    private List<Tile> toMjTiles(List<MahjongTile> tiles) throws Exception {
        List<Tile> result = new ArrayList<>(tiles.size());
        for (MahjongTile tile : tiles) {
            result.add(toMjTile(tile));
        }
        return result;
    }

    private Tile toMjTile(MahjongTile tile) throws Exception {
        Tile cached = tileCache.get(tile);
        if (cached != null) {
            return cached;
        }
        String text = toTileText(tile);
        int raw = (int) tileGetByString.invoke(Tile.Companion, text);
        Tile boxed = (Tile) tileBox.invoke(null, raw);
        tileCache.put(tile, boxed);
        return boxed;
    }

    private int toTileCode(MahjongTile tile) throws Exception {
        Integer cached = tileCodeCache.get(tile);
        if (cached != null) {
            return cached;
        }
        Tile mjTile = toMjTile(tile);
        int code = (int) tileUnbox.invoke(mjTile);
        tileCodeCache.put(tile, code);
        return code;
    }

    private List<Furo> toMjFuro(List<MahjongMeld> melds) throws Exception {
        List<Furo> result = new ArrayList<>();
        if (melds == null) {
            return result;
        }
        for (MahjongMeld meld : melds) {
            List<Tile> tiles = toMjTiles(meld.tiles());
            int rawFuro = (int) furoCtor.invoke(null, tiles, meld.open());
            Furo boxed = (Furo) furoBox.invoke(null, rawFuro);
            result.add(boxed);
        }
        return result;
    }

    private mahjongutils.models.Wind toMjWind(Wind wind) {
        return switch (wind) {
            case EAST -> mahjongutils.models.Wind.East;
            case SOUTH -> mahjongutils.models.Wind.South;
            case WEST -> mahjongutils.models.Wind.West;
            case NORTH -> mahjongutils.models.Wind.North;
        };
    }

    private String toTileText(MahjongTile tile) {
        int so = tile.sortOrder();
        if (so <= 8) {
            return (so + 1) + "m";
        }
        if (so <= 17) {
            return (so - 9 + 1) + "p";
        }
        if (so <= 26) {
            return (so - 18 + 1) + "s";
        }
        return switch (so) {
            case 27 -> "1z";
            case 28 -> "2z";
            case 29 -> "3z";
            case 30 -> "4z";
            case 31 -> "5z";
            case 32 -> "6z";
            case 33 -> "7z";
            default -> throw new IllegalArgumentException("Invalid tile: " + tile);
        };
    }

    private int unsignedToInt(Object value) {
        return switch (value) {
            case Long l -> Integer.parseInt(Long.toUnsignedString(l));
            default -> 0;
        };
    }

    public record HoraResult(
            int han,
            int fu,
            boolean yakuman,
            List<String> yaku,
            int ronDealer,
            int ronChild,
            int tsumoDealerEach,
            int tsumoChildDealerPay,
            int tsumoChildOtherPay
    ) {
    }
}
