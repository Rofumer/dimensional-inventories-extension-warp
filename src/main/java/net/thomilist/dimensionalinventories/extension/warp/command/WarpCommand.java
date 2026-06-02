package net.thomilist.dimensionalinventories.extension.warp.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.arguments.DimensionArgument.dimension;
import static net.minecraft.commands.arguments.EntityArgument.players;

public class WarpCommand
{
    private static final String ARG_DIMENSION = "dimension";
    private static final String ARG_PLAYER = "player";

    public void register()
    {
        CommandRegistrationCallback.EVENT.register( ( dispatcher, registryAccess, environment ) ->
            this.register( dispatcher ) );
    }

    private void register( final CommandDispatcher<CommandSourceStack> dispatcher )
    {
        final var warpNode = literal( "warp" )
            .requires( net.minecraft.commands.Commands.hasPermission(
                net.minecraft.commands.Commands.LEVEL_OWNERS ) )
            // warp <dimension>  — warp self
            .then( argument( ARG_DIMENSION, dimension() )
                .executes( this::warpSelf ) )
            // warp player <player> <dimension>  — warp another player
            .then( literal( "player" )
                .then( argument( ARG_PLAYER, players() )
                    .then( argument( ARG_DIMENSION, dimension() )
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
        final ServerLevel target = DimensionArgument.getDimension( context, ARG_DIMENSION );

        if ( ((ServerLevel) player.level()).equals( target ) )
        {
            context.getSource().sendFailure( Component.literal(
                "You are already in dimension '" + dimensionName( target ) + '\'' ) );
            return -1;
        }

        this.teleport( player, target );

        context.getSource().sendSuccess( () -> Component.literal(
            "Warped to dimension '" + dimensionName( target ) + '\'' ), false );

        return Command.SINGLE_SUCCESS;
    }

    private int warpPlayers( final CommandContext<CommandSourceStack> context )
        throws CommandSyntaxException
    {
        final Collection<ServerPlayer> targets = EntityArgument.getPlayers( context, ARG_PLAYER );
        final ServerLevel target = DimensionArgument.getDimension( context, ARG_DIMENSION );

        int count = 0;

        for ( final ServerPlayer player : targets )
        {
            if ( ((ServerLevel) player.level()).equals( target ) )
            {
                context.getSource().sendFailure( Component.literal(
                    '\'' + player.getName().getString() + "' is already in dimension '" +
                    dimensionName( target ) + '\'' ) );
                continue;
            }

            this.teleport( player, target );
            count++;
        }

        if ( count == 0 )
        {
            return -1;
        }

        final int warped = count;
        context.getSource().sendSuccess( () -> Component.literal(
            "Warped " + warped + " player(s) to dimension '" + dimensionName( target ) + '\'' ), true );

        return Command.SINGLE_SUCCESS;
    }

    private void teleport( final ServerPlayer player, final ServerLevel target )
    {
        final BlockPos spawnPos = target.getRespawnData().pos();
        final double x = spawnPos.getX() + 0.5;
        final double y = target.getHeight( Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnPos.getX(), spawnPos.getZ() );
        final double z = spawnPos.getZ() + 0.5;

        player.teleport( new TeleportTransition(
            target,
            new Vec3( x, y, z ),
            Vec3.ZERO,
            player.getYRot(),
            player.getXRot(),
            TeleportTransition.DO_NOTHING
        ) );
    }

    private static String dimensionName( final ServerLevel level )
    {
        return level.dimension().identifier().toString();
    }
}
