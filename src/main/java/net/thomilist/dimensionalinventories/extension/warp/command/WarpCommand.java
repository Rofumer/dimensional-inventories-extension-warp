package net.thomilist.dimensionalinventories.extension.warp.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.thomilist.dimensionalinventories.DimensionalInventories;
import net.thomilist.dimensionalinventories.exception.ModuleNotRegisteredException;
import net.thomilist.dimensionalinventories.module.builtin.pool.DimensionPool;
import net.thomilist.dimensionalinventories.module.builtin.pool.DimensionPoolConfigModule;
import net.thomilist.dimensionalinventories.module.builtin.pool.DimensionPoolConfigModuleState;
import net.thomilist.dimensionalinventories.extension.warp.WarpPositionStore;
import me.lucko.fabric.api.permissions.v0.Permissions;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.arguments.EntityArgument.players;

public class WarpCommand
{
    private static final String ARG_POOL = "pool";
    private static final String ARG_PLAYER = "player";

    private final WarpPositionStore positionStore;
    private final Set<UUID> warpingPlayers;

    public WarpCommand( final WarpPositionStore positionStore, final Set<UUID> warpingPlayers )
    {
        this.positionStore = positionStore;
        this.warpingPlayers = warpingPlayers;
    }

    public void register()
    {
        CommandRegistrationCallback.EVENT.register( ( dispatcher, registryAccess, environment ) ->
            this.register( dispatcher ) );
    }

    private void register( final CommandDispatcher<CommandSourceStack> dispatcher )
    {
        final var warpNode = literal( "warp" )
            // warp <pool>  — warp self to pool
            .then( argument( ARG_POOL, StringArgumentType.word() )
                .requires( Permissions.require( "diminv.warp.self", 4 ) )
                .suggests( ( ctx, builder ) -> { poolIds().forEach( builder::suggest ); return builder.buildFuture(); } )
                .executes( this::warpSelf ) )
            // warp player <player> <pool>  — warp another player to pool
            .then( literal( "player" )
                .requires( Permissions.require( "diminv.warp.others", 4 ) )
                .then( argument( ARG_PLAYER, players() )
                    .then( argument( ARG_POOL, StringArgumentType.word() )
                        .suggests( ( ctx, builder ) -> { poolIds().forEach( builder::suggest ); return builder.buildFuture(); } )
                        .executes( this::warpPlayers ) ) ) );

        // Standalone /warp command
        dispatcher.register( warpNode );

        // Also merged into /diminv warp for consistency with the main mod's command tree
        dispatcher.register( literal( "diminv" ).then( warpNode ) );
    }

    private int warpSelf( final CommandContext<CommandSourceStack> context )
        throws CommandSyntaxException
    {
        final ServerPlayer player = context.getSource().getPlayerOrException();
        final String poolId = StringArgumentType.getString( context, ARG_POOL );
        return this.warp( context.getSource(), player, poolId, true );
    }

    private int warpPlayers( final CommandContext<CommandSourceStack> context )
        throws CommandSyntaxException
    {
        final Collection<ServerPlayer> targets = EntityArgument.getPlayers( context, ARG_PLAYER );
        final String poolId = StringArgumentType.getString( context, ARG_POOL );

        int count = 0;

        for ( final ServerPlayer player : targets )
        {
            if ( this.warp( context.getSource(), player, poolId, false ) == Command.SINGLE_SUCCESS )
            {
                count++;
            }
        }

        if ( count == 0 )
        {
            return -1;
        }

        final int warped = count;
        context.getSource().sendSuccess( () -> Component.literal(
            "Warped " + warped + " player(s) to pool '" + poolId + '\'' ), true );

        return Command.SINGLE_SUCCESS;
    }

    // Core warp logic. Returns SINGLE_SUCCESS or -1. Sends failure messages directly.
    // If sendSuccess is true, sends the success message (for /warp self).
    private int warp( final CommandSourceStack source, final ServerPlayer player,
                      final String poolId, final boolean sendSuccess )
    {
        final Optional<DimensionPoolConfigModuleState> configOpt = poolConfig();
        if ( configOpt.isEmpty() )
        {
            source.sendFailure( Component.literal( "Dimensional Inventories pool module is not available" ) );
            return -1;
        }
        final DimensionPoolConfigModuleState config = configOpt.get();

        final Optional<DimensionPool> poolOpt = config.poolWithId( poolId );
        if ( poolOpt.isEmpty() )
        {
            source.sendFailure( Component.literal( "Pool '" + poolId + "' does not exist" ) );
            return -1;
        }
        final DimensionPool pool = poolOpt.get();

        if ( pool.getDimensions().isEmpty() )
        {
            source.sendFailure( Component.literal( "Pool '" + poolId + "' has no dimensions configured" ) );
            return -1;
        }

        // Fail if the player is already in a dimension that belongs to the target pool
        final String currentDimId = ( (ServerLevel) player.level() ).dimension().identifier().toString();
        if ( pool.getDimensions().contains( currentDimId ) )
        {
            final String name = player.getName().getString();
            source.sendFailure( Component.literal(
                ( isSelf( source, player ) ? "You are" : "'" + name + "' is" ) +
                " already in pool '" + poolId + '\'' ) );
            return -1;
        }

        // Fail if the player's current game mode doesn't match the required mode of their current pool
        final Optional<DimensionPool> sourcePoolOpt = config.dimensionPools.values().stream()
            .filter( p -> p.getDimensions().contains( currentDimId ) )
            .findFirst();
        if ( sourcePoolOpt.isPresent() )
        {
            final GameType requiredMode = sourcePoolOpt.get().getGameMode();
            final GameType playerMode = player.gameMode.getGameModeForPlayer();
            if ( requiredMode != null && playerMode != requiredMode )
            {
                final String name = player.getName().getString();
                source.sendFailure( Component.literal(
                    ( isSelf( source, player ) ? "You must" : "'" + name + "' must" ) +
                    " be in " + requiredMode.getSerializedName() + " mode to warp from this pool" ) );
                return -1;
            }
        }

        // Determine the target dimension: last remembered for this pool, or first dimension as fallback
        final Optional<String> lastDim = this.positionStore.getLastDimensionInPool( player.getUUID(), poolId );
        final String targetDimId;
        if ( lastDim.isPresent() && pool.getDimensions().contains( lastDim.get() ) )
        {
            targetDimId = lastDim.get();
        }
        else
        {
            targetDimId = pool.getDimensions().first();
        }

        final ResourceKey<Level> dimKey = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.parse( targetDimId )
        );
        final ServerLevel targetLevel = source.getServer().getLevel( dimKey );
        if ( targetLevel == null )
        {
            source.sendFailure( Component.literal( "Dimension '" + targetDimId + "' in pool '" + poolId + "' is not loaded" ) );
            return -1;
        }

        this.teleport( player, targetLevel, pool );

        if ( sendSuccess )
        {
            source.sendSuccess( () -> Component.literal(
                "Warped to pool '" + poolId + "' (" + targetDimId + ')' ), false );
        }

        return Command.SINGLE_SUCCESS;
    }

    private void teleport( final ServerPlayer player, final ServerLevel target, final DimensionPool pool )
    {
        // Snapshot exact position right now so AFTER_PLAYER_CHANGE_LEVEL gets accurate origin coords
        this.positionStore.snapshotPlayer( player );

        final Optional<WarpPositionStore.PositionRecord> stored =
            this.positionStore.get( player.getUUID(), target.dimension() );

        final double x, y, z;
        final float yRot, xRot;

        if ( stored.isPresent() )
        {
            x    = stored.get().x();
            y    = pool.getGameMode() == GameType.CREATIVE ? 100.0 : stored.get().y();
            z    = stored.get().z();
            yRot = stored.get().yRot();
            xRot = stored.get().xRot();
        }
        else
        {
            // No recorded position — fall back to world spawn projected onto the surface.
            // getRespawnData() is non-null in practice but the contract is abstract, so guard it.
            final var respawnData = target.getRespawnData();
            final BlockPos spawnPos = respawnData != null ? respawnData.pos() : BlockPos.ZERO;

            // getHeight(MOTION_BLOCKING_NO_LEAVES) returns the first free Y above the highest
            // solid (non-leaf) block — i.e. the Y the player should stand at.
            // It returns getMinY() when the chunk is not yet generated; fall back to
            // spawn's own Y + 1 in that case so the player doesn't end up underground.
            final int surfaceY = target.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnPos.getX(), spawnPos.getZ() );

            x    = spawnPos.getX() + 0.5;
            y    = surfaceY > target.getMinY() ? surfaceY : spawnPos.getY() + 1;
            z    = spawnPos.getZ() + 0.5;
            yRot = player.getYRot();
            xRot = 0.0f;
        }

        this.warpingPlayers.add( player.getUUID() );
        try
        {
            player.teleport( new TeleportTransition(
                target,
                new Vec3( x, y, z ),
                Vec3.ZERO,
                yRot,
                xRot,
                TeleportTransition.DO_NOTHING
            ) );
        }
        finally
        {
            this.warpingPlayers.remove( player.getUUID() );
        }
    }

    private static Optional<DimensionPoolConfigModuleState> poolConfig()
    {
        try
        {
            return Optional.of( DimensionalInventories.INSTANCE.configModules
                .get( DimensionPoolConfigModule.class )
                .state() );
        }
        catch ( final ModuleNotRegisteredException e )
        {
            return Optional.empty();
        }
    }

    private static List<String> poolIds()
    {
        return poolConfig()
            .map( c -> List.copyOf( c.dimensionPools.keySet() ) )
            .orElse( List.of() );
    }

    private static boolean isSelf( final CommandSourceStack source, final ServerPlayer player )
    {
        return source.getPlayer() != null && source.getPlayer() == player;
    }
}
