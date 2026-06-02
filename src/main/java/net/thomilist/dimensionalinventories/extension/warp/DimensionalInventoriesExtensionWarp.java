package net.thomilist.dimensionalinventories.extension.warp;

import net.fabricmc.api.ModInitializer;
import net.thomilist.dimensionalinventories.extension.warp.command.WarpCommand;
import net.thomilist.dimensionalinventories.util.ModProperties;

public class DimensionalInventoriesExtensionWarp
    implements ModInitializer
{
    public static final ModProperties PROPERTIES = new ModProperties( "dimensional-inventories-extension-warp" );

    @Override
    public void onInitialize()
    {
        new WarpCommand().register();
    }
}
