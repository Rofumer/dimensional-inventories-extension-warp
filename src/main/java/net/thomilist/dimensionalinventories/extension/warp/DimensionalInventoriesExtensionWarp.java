package net.thomilist.dimensionalinventories.extension.warp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.thomilist.dimensionalinventories.extension.warp.command.WarpCommand;
import net.thomilist.dimensionalinventories.util.ModProperties;

public class DimensionalInventoriesExtensionWarp
    implements ModInitializer
{
    public static final ModProperties PROPERTIES = new ModProperties( "dimensional-inventories-extension-warp" );

    private final WarpPositionStore positionStore = new WarpPositionStore();

    @Override
    public void onInitialize()
    {
        ServerLifecycleEvents.SERVER_STARTED.register( server ->
            this.positionStore.onServerStarted( server ) );

        // Load stored positions when a player joins
        ServerPlayConnectionEvents.JOIN.register( ( handler, sender, server ) ->
            this.positionStore.loadPlayer( handler.getPlayer().getUUID() ) );

        // Keep position cache fresh every tick so AFTER_PLAYER_CHANGE_LEVEL can read origin coords
        ServerTickEvents.END_SERVER_TICK.register( server ->
        {
            for ( final var player : server.getPlayerList().getPlayers() )
            {
                this.positionStore.snapshotPlayer( player );
            }
        } );

        // When a player changes dimension (portal or /warp), save their origin position
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
            ( player, origin, destination ) ->
                this.positionStore.onPlayerChangedDimension( player, origin ) );

        new WarpCommand( this.positionStore ).register();
    }
}
