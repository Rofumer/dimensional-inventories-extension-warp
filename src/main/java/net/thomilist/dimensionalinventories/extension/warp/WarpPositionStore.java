package net.thomilist.dimensionalinventories.extension.warp;

import com.google.gson.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WarpPositionStore
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SUBDIR = "dimensional-inventories-extension-warp";

    // playerUUID -> ( dimensionId -> PositionRecord )
    private final Map<UUID, Map<String, PositionRecord>> store = new ConcurrentHashMap<>();

    // Last-known position per player, updated every tick and manually before /warp
    private final Map<UUID, PositionSnapshot> cache = new ConcurrentHashMap<>();

    private Path saveDirectory;

    public void onServerStarted( final MinecraftServer server )
    {
        this.saveDirectory = server.getWorldPath( LevelResource.ROOT ).resolve( SUBDIR );

        try { Files.createDirectories( this.saveDirectory ); }
        catch ( final IOException ignored ) { }
    }

    // Call every tick (or right before teleporting) to keep the cache fresh
    public void snapshotPlayer( final ServerPlayer player )
    {
        this.cache.put( player.getUUID(), new PositionSnapshot(
            player.level().dimension(),
            player.getX(), player.getY(), player.getZ(),
            player.getYRot(), player.getXRot()
        ) );
    }

    // Call in AFTER_PLAYER_CHANGE_LEVEL — saves the origin position from the cache
    public void onPlayerChangedDimension( final ServerPlayer player, final ServerLevel origin )
    {
        final PositionSnapshot snapshot = this.cache.get( player.getUUID() );

        if ( snapshot != null && snapshot.dimension().equals( origin.dimension() ) )
        {
            this.put(
                player.getUUID(),
                origin.dimension().identifier().toString(),
                new PositionRecord(
                    snapshot.x(), snapshot.y(), snapshot.z(),
                    snapshot.yRot(), snapshot.xRot()
                )
            );
        }
    }

    public Optional<PositionRecord> get( final UUID playerUUID, final ResourceKey<Level> dimension )
    {
        final Map<String, PositionRecord> playerData = this.store.get( playerUUID );
        if ( playerData == null ) return Optional.empty();
        return Optional.ofNullable( playerData.get( dimension.identifier().toString() ) );
    }

    public void loadPlayer( final UUID playerUUID )
    {
        if ( this.saveDirectory == null ) return;
        final Path file = this.saveDirectory.resolve( playerUUID + ".json" );
        if ( !Files.exists( file ) ) return;

        try ( final Reader reader = new InputStreamReader(
            Files.newInputStream( file ), StandardCharsets.UTF_8 ) )
        {
            final JsonObject root = GSON.fromJson( reader, JsonObject.class );
            if ( root == null ) return;

            final Map<String, PositionRecord> playerMap = new HashMap<>();

            for ( final Map.Entry<String, JsonElement> entry : root.entrySet() )
            {
                final JsonObject pos = entry.getValue().getAsJsonObject();
                playerMap.put( entry.getKey(), new PositionRecord(
                    pos.get( "x" ).getAsDouble(),
                    pos.get( "y" ).getAsDouble(),
                    pos.get( "z" ).getAsDouble(),
                    pos.get( "yRot" ).getAsFloat(),
                    pos.get( "xRot" ).getAsFloat()
                ) );
            }

            this.store.put( playerUUID, playerMap );
        }
        catch ( final IOException ignored ) { }
    }

    private void put( final UUID playerUUID, final String dimensionId, final PositionRecord record )
    {
        this.store.computeIfAbsent( playerUUID, k -> new HashMap<>() ).put( dimensionId, record );
        this.persist( playerUUID );
    }

    private void persist( final UUID playerUUID )
    {
        if ( this.saveDirectory == null ) return;
        final Map<String, PositionRecord> playerData = this.store.get( playerUUID );
        if ( playerData == null ) return;

        final JsonObject root = new JsonObject();
        for ( final Map.Entry<String, PositionRecord> entry : playerData.entrySet() )
        {
            final JsonObject pos = new JsonObject();
            pos.addProperty( "x", entry.getValue().x() );
            pos.addProperty( "y", entry.getValue().y() );
            pos.addProperty( "z", entry.getValue().z() );
            pos.addProperty( "yRot", entry.getValue().yRot() );
            pos.addProperty( "xRot", entry.getValue().xRot() );
            root.add( entry.getKey(), pos );
        }

        final Path file = this.saveDirectory.resolve( playerUUID + ".json" );
        try ( final Writer writer = new OutputStreamWriter(
            Files.newOutputStream( file ), StandardCharsets.UTF_8 ) )
        {
            GSON.toJson( root, writer );
        }
        catch ( final IOException ignored ) { }
    }

    public record PositionRecord( double x, double y, double z, float yRot, float xRot ) { }

    private record PositionSnapshot(
        ResourceKey<Level> dimension,
        double x, double y, double z,
        float yRot, float xRot
    ) { }
}
