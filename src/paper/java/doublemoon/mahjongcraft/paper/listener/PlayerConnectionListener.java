package doublemoon.mahjongcraft.paper.listener;

import doublemoon.mahjongcraft.paper.game.MahjongTableManager;
import doublemoon.mahjongcraft.paper.ui.HandView;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerConnectionListener implements Listener {
    private final MahjongTableManager manager;

    public PlayerConnectionListener(MahjongTableManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        HandView.close(event.getPlayer().getUniqueId());
        manager.leave(event.getPlayer().getUniqueId());
    }
}

