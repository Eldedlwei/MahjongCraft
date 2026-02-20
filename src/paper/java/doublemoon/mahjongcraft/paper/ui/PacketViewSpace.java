package doublemoon.mahjongcraft.paper.ui;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class PacketViewSpace {
    private static final AtomicInteger ENTITY_ID_SEQ = new AtomicInteger(2_000_000);
    private static final Quaternion4f IDENTITY_QUATERNION = new Quaternion4f(0f, 0f, 0f, 1f);

    private final Player player;
    private final List<Integer> entities = new ArrayList<>();

    public PacketViewSpace(Player player) {
        this.player = player;
    }

    public int spawnItemDisplay(Location location, ItemStack itemStack, float scale, float yaw) {
        int entityId = nextEntityId();
        Location spawnLoc = location.clone();
        spawnLoc.setYaw(yaw);
        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
                entityId,
                UUID.randomUUID(),
                EntityTypes.ITEM_DISPLAY,
                SpigotConversionUtil.fromBukkitLocation(spawnLoc),
                spawnLoc.getYaw(),
                0,
                null
        );
        send(spawn);

        List<EntityData<?>> metadata = Arrays.asList(
                new EntityData<>(12, EntityDataTypes.VECTOR3F, new Vector3f(scale, scale, scale)),
                new EntityData<>(13, EntityDataTypes.QUATERNION, IDENTITY_QUATERNION),
                new EntityData<>(14, EntityDataTypes.QUATERNION, IDENTITY_QUATERNION),
                new EntityData<>(23, EntityDataTypes.ITEMSTACK, SpigotConversionUtil.fromBukkitItemStack(itemStack)),
                new EntityData<>(24, EntityDataTypes.BYTE, (byte) 0)
        );
        send(new WrapperPlayServerEntityMetadata(entityId, metadata));
        entities.add(entityId);
        return entityId;
    }

    public void destroy(int entityId) {
        if (entities.remove((Integer) entityId)) {
            send(new WrapperPlayServerDestroyEntities(entityId));
        }
    }

    public void close() {
        if (entities.isEmpty()) {
            return;
        }
        int[] ids = entities.stream().mapToInt(Integer::intValue).toArray();
        send(new WrapperPlayServerDestroyEntities(ids));
        entities.clear();
    }

    private void send(PacketWrapper<?> wrapper) {
        if (!player.isOnline()) {
            return;
        }
        if (PacketEvents.getAPI() == null) {
            return;
        }
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, wrapper);
    }

    private static int nextEntityId() {
        return ENTITY_ID_SEQ.incrementAndGet();
    }
}
