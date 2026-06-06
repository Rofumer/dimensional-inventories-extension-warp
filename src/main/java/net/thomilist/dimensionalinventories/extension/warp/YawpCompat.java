package net.thomilist.dimensionalinventories.extension.warp;

import de.z0rdak.yawp.api.events.flag.FlagEvents;
import de.z0rdak.yawp.core.flag.FlagState;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.UUID;

// Registered only when YAWP is loaded (checked in DimensionalInventoriesExtensionWarp before
// this class is touched — do not reference it unconditionally or classloading will fail without YAWP).
public final class YawpCompat
{
    private YawpCompat() {}

    public static void register( final Set<UUID> warpingPlayers )
    {
        FlagEvents.ON_FLAG_RESULT.register( result ->
        {
            if ( result.getFlagState() == FlagState.DENIED )
            {
                final Player player = result.getFlagCheck().getPlayer();
                if ( player != null && warpingPlayers.contains( player.getUUID() ) )
                {
                    result.setFlagState( FlagState.ALLOWED );
                }
            }
            return result;
        } );
    }
}
