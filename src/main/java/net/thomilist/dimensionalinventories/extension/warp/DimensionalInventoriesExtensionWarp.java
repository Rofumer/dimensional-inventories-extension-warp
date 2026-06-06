package net.thomilist.dimensionalinventories.extension.warp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.thomilist.dimensionalinventories.DimensionalInventories;
import net.thomilist.dimensionalinventories.exception.ModuleNotRegisteredException;
import net.thomilist.dimensionalinventories.extension.warp.command.WarpCommand;
import net.thomilist.dimensionalinventories.module.builtin.pool.DimensionPoolConfigModule;
import net.thomilist.dimensionalinventories.util.ModProperties;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DimensionalInventoriesExtensionWarp
    implements ModInitializer
{
    public static final ModProperties PROPERTIES = new ModProperties( "dimensional-inventories-extension-warp" );

    private final WarpPositionStore positionStore = new WarpPositionStore();

    @Override
    public void onInitialize()
    {
        final Set<UUID> warpingPlayers = new HashSet<>();

        if ( FabricLoader.getInstance().isModLoaded( "yawp" ) )
        {
            YawpCompat.register( warpingPlayers );
        }

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
        // and update which dimension in the pool they were last in
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
            ( player, origin, destination ) ->
            {
                // Clear the warp-in-progress marker now that the dimension change has landed;
                // this is the earliest safe point — YAWP's ON_FLAG_RESULT fires before this.
                warpingPlayers.remove( player.getUUID() );

                this.positionStore.onPlayerChangedDimension( player, origin );

                final String originDimId = origin.dimension().identifier().toString();
                try
                {
                    DimensionalInventories.INSTANCE.configModules
                        .get( DimensionPoolConfigModule.class )
                        .state()
                        .poolWithDimension( originDimId )
                        .ifPresent( pool -> this.positionStore.setLastDimensionInPool(
                            player.getUUID(), pool.getId(), originDimId ) );
                }
                catch ( final ModuleNotRegisteredException ignored ) { }
            } );

        new WarpCommand( this.positionStore, warpingPlayers ).register();
    }
}
