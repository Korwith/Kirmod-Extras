package net.minecraft.src;

import java.io.DataInputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

/**
 * Crop Catch-Up: crops in chunks that were unloaded grow, when the chunk loads again, as much as they
 * would have grown if it had stayed loaded next to the player.
 *
 * The time comes from the chunk itself: vanilla saves the world time into every chunk ("LastUpdate") and
 * never reads it. KirCatchUpChunkLoader notes it when a chunk loads. Once that chunk and its eight
 * neighbours are loaded (as around the player, where vanilla random-ticks blocks), every crop in it gets
 * the random ticks it missed: vanilla gives each block of a chunk section a 3 in 4096 chance per tick, so
 * a block that was away N ticks gets a Poisson(N * 3 / 4096) number of them, each through the block's own
 * updateTick, so farmland water, light and neighbouring crops count as in vanilla. For the light, each
 * missed tick is given a random time of day from the gap: crops under the open sky do not grow at night.
 *
 * Values: Off (vanilla), Crops (wheat, melon and pumpkin stems and their fruit, nether wart, cocoa
 * beans), All Plants (also sugar cane, cactus and saplings). It uses the in-game time, so time only
 * counts while the world is open, /time set back gives nothing, and a skipped night counts. Works on the
 * integrated server only (singleplayer, a LAN world you host).
 */
public class KirModuleCropCatchUp extends KirModule
{
    private static final String[] VALUES = new String[] {"Off", "Crops", "All Plants"};

    /** Vanilla's random ticks: 3 per chunk section (16x16x16 blocks) per tick. */
    private static final double RANDOM_TICK_CHANCE = 3.0D / 4096.0D;

    /** Gaps shorter than this are ignored (chunks loading and unloading at the view distance). */
    private static final long MIN_GAP = 20L;

    /** At most this many missed random ticks per block; a crop needs far fewer to grow up. */
    private static final int MAX_TICKS_PER_BLOCK = 1024;

    /** Time spent catching up per world tick, so loading many chunks at once spreads over a few ticks. */
    private static final long BUDGET_NANOS = 4000000L;

    /** A world first seen this many server ticks after the server started was made in game (see backfill). */
    private static final int NEW_WORLD_AFTER_TICKS = 200;

    private static final int KIND_NONE = 0;
    private static final int KIND_CROPS = 1;
    private static final int KIND_STEM = 2;
    private static final int KIND_NETHER_WART = 3;
    private static final int KIND_COCOA = 4;
    private static final int KIND_COLUMN = 5;
    private static final int KIND_SAPLING = 6;

    private static final Field fieldChunkLoader = KirUtil.findField(ChunkProviderServer.class, "currentChunkLoader", "e");
    private static final Field fieldSaveLocation = KirUtil.findField(AnvilChunkLoader.class, "chunkSaveLocation", "d");
    private static final Field fieldLoadedChunks = KirUtil.findField(ChunkProviderServer.class, "loadedChunks", "g");

    /** Worlds seen so far (any value), and those whose chunk loader is someone else's. Server thread only. */
    private final Map seenWorlds = new WeakHashMap();
    private final Random rand = new Random();

    /** Total chunks and blocks caught up, for the log and tests. */
    public volatile int chunksCaughtUp;
    public volatile int blocksGrown;

    public KirModuleCropCatchUp()
    {
        super("cropCatchUp", "Crop Catch-Up", "Crop Catch-Up", 0, "cropcatchup", "catchup");
    }

    public String getCategory()
    {
        return CATEGORY_WORLD;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "Whether crops in chunks you were away from grow for the time you were away. In vanilla, crops only grow while their chunk is loaded near you.",
            describeValue("Off", "vanilla."),
            describeValue("Crops", "wheat, melon and pumpkin stems, nether wart and cocoa beans catch up when their chunk loads again, as if you had stayed nearby."),
            describeValue("All Plants", "sugar cane, cactus and saplings too."),
            note("Uses the in-game time saved in each chunk, so it counts only while the world is open. Light, water and nearby crops count as in vanilla. Works in singleplayer and on a LAN world you host.")
        };
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public boolean isAvailable()
    {
        return KirWorldHook.isAvailable() && fieldChunkLoader != null && fieldSaveLocation != null && fieldLoadedChunks != null;
    }

    public void update(Minecraft mc)
    {
        if (this.getValue() != 0)
        {
            KirWorldHook.installAll(mc);
        }
    }

    /** Called on the server thread right before a server world ticks its entities. */
    public void onServerWorldTick(World world)
    {
        if (!(world instanceof WorldServer))
        {
            return;
        }

        WorldServer var2 = (WorldServer)world;
        Object var3 = this.seenWorlds.get(var2);

        if (var3 == null)
        {
            // A world made while the server was already running (Forge loads dimensions on demand, and
            // vanilla's Nether and End first tick here when a player arrives) loaded its first chunks
            // before this hook ran; those are read from disk once below so they catch up too.
            MinecraftServer var4 = MinecraftServer.getServer();
            var3 = var4 != null && var4.getTickCounter() > NEW_WORLD_AFTER_TICKS ? "new" : "old";
            this.seenWorlds.put(var2, var3);
        }

        if ("unsupported".equals(var3))
        {
            return;
        }

        Object var5 = getLoader(var2);

        if (this.getValue() == 0)
        {
            if (var5 instanceof KirCatchUpChunkLoader)
            {
                KirCatchUpChunkLoader var6 = (KirCatchUpChunkLoader)var5;
                var6.recording = false;

                synchronized (var6.pending)
                {
                    var6.pending.clear();
                }
            }

            this.seenWorlds.put(var2, "old");
            return;
        }

        KirCatchUpChunkLoader var7;

        if (var5 instanceof KirCatchUpChunkLoader)
        {
            var7 = (KirCatchUpChunkLoader)var5;
            var7.recording = true;
        }
        else
        {
            var7 = this.install(var2, var5);

            if (var7 == null)
            {
                this.seenWorlds.put(var2, "unsupported");
                return;
            }

            if ("new".equals(var3))
            {
                this.backfill(var2, var7);
            }

            this.seenWorlds.put(var2, "old");
        }

        this.catchUp(var2, var7);
    }

    private static Object getLoader(WorldServer world)
    {
        try
        {
            return world.theChunkProviderServer != null ? fieldChunkLoader.get(world.theChunkProviderServer) : null;
        }
        catch (IllegalAccessException var2)
        {
            return null;
        }
    }

    /** Puts a KirCatchUpChunkLoader in place of the world's vanilla AnvilChunkLoader. */
    private KirCatchUpChunkLoader install(WorldServer world, Object loader)
    {
        if (loader == null || loader.getClass() != AnvilChunkLoader.class)
        {
            KirUtil.log("Crop Catch-Up: the chunk loader of dimension " + world.provider.worldType + " is " + (loader == null ? "missing" : loader.getClass().getName()) + ", leaving it alone", (Throwable)null);
            return null;
        }

        try
        {
            KirCatchUpChunkLoader var3 = new KirCatchUpChunkLoader((java.io.File)fieldSaveLocation.get(loader));

            // Chunks the old loader is still writing must be on disk before this one reads them.
            ThreadedFileIOBase.threadedIOInstance.waitForFinish();
            fieldChunkLoader.set(world.theChunkProviderServer, var3);
            return var3;
        }
        catch (Exception var4)
        {
            KirUtil.log("Crop Catch-Up could not hook into chunk loading", var4);
            return null;
        }
    }

    /** Notes the save time of every chunk the world loaded before its loader was swapped, from disk. */
    private void backfill(WorldServer world, KirCatchUpChunkLoader loader)
    {
        List var3;

        try
        {
            var3 = new ArrayList((List)fieldLoadedChunks.get(world.theChunkProviderServer));
        }
        catch (Exception var9)
        {
            return;
        }

        for (int var4 = 0; var4 < var3.size(); ++var4)
        {
            Chunk var5 = (Chunk)var3.get(var4);

            try
            {
                DataInputStream var6 = RegionFileCache.getChunkInputStream(loader.saveLocation, var5.xPosition, var5.zPosition);

                if (var6 != null)
                {
                    NBTTagCompound var7 = CompressedStreamTools.read(var6).getCompoundTag("Level");
                    var6.close();

                    if (var7.hasKey("LastUpdate"))
                    {
                        loader.note(var5.xPosition, var5.zPosition, var7.getLong("LastUpdate"));
                    }
                }
            }
            catch (Exception var8)
            {
                ;
            }
        }
    }

    /** Catches up the waiting chunks whose neighbours are loaded, within the time budget. */
    private void catchUp(WorldServer world, KirCatchUpChunkLoader loader)
    {
        List var3;

        synchronized (loader.pending)
        {
            if (loader.pending.isEmpty())
            {
                return;
            }

            var3 = new ArrayList(loader.pending.entrySet());
        }

        ChunkProviderServer var4 = world.theChunkProviderServer;
        long var5 = System.nanoTime();
        long var7 = world.getWorldTime();
        int var9 = this.getValue();

        for (int var10 = 0; var10 < var3.size(); ++var10)
        {
            Map.Entry var11 = (Map.Entry)var3.get(var10);
            long var12 = ((Long)var11.getKey()).longValue();
            int var14 = (int)var12;
            int var15 = (int)(var12 >> 32);

            if (!var4.chunkExists(var14, var15))
            {
                // Unloaded again before catching up; its save kept the old time.
                this.remove(loader, var11.getKey(), var11.getValue());
                continue;
            }

            if (System.nanoTime() - var5 > BUDGET_NANOS || !neighboursLoaded(var4, var14, var15))
            {
                continue;
            }

            long var16 = ((Long)var11.getValue()).longValue();
            this.remove(loader, var11.getKey(), var11.getValue());

            if (var7 - var16 >= MIN_GAP)
            {
                this.catchUpChunk(world, var4.provideChunk(var14, var15), var16, var7 - var16, var9);
            }
        }
    }

    private void remove(KirCatchUpChunkLoader loader, Object key, Object value)
    {
        synchronized (loader.pending)
        {
            // Only if it was not loaded (and noted) again meanwhile.
            if (value.equals(loader.pending.get(key)))
            {
                loader.pending.remove(key);
            }
        }
    }

    private static boolean neighboursLoaded(ChunkProviderServer provider, int chunkX, int chunkZ)
    {
        for (int var3 = -1; var3 <= 1; ++var3)
        {
            for (int var4 = -1; var4 <= 1; ++var4)
            {
                if (!provider.chunkExists(chunkX + var3, chunkZ + var4))
                {
                    return false;
                }
            }
        }

        return true;
    }

    /** Gives every crop in the chunk the random ticks it missed in the gap of the given ticks starting at from. */
    public void catchUpChunk(World world, Chunk chunk, long from, long gap, int value)
    {
        ExtendedBlockStorage[] var7 = chunk.getBlockStorageArray();
        List var8 = new ArrayList();

        // Collected first: growing places blocks (fruit, sugar cane, trees) in the sections being read.
        for (int var9 = 0; var9 < var7.length; ++var9)
        {
            ExtendedBlockStorage var10 = var7[var9];

            if (var10 == null || var10.isEmpty() || !var10.getNeedsRandomTick())
            {
                continue;
            }

            for (int var11 = 0; var11 < 16; ++var11)
            {
                for (int var12 = 0; var12 < 16; ++var12)
                {
                    for (int var13 = 0; var13 < 16; ++var13)
                    {
                        int var14 = var10.getExtBlockID(var13, var11, var12);

                        if (var14 > 0 && kindOf(Block.blocksList[var14], value) != KIND_NONE)
                        {
                            var8.add(new int[] {(chunk.xPosition << 4) + var13, var10.getYLocation() + var11, (chunk.zPosition << 4) + var12});
                        }
                    }
                }
            }
        }

        if (var8.isEmpty())
        {
            return;
        }

        int var15 = world.skylightSubtracted;
        double var16 = (double)gap * RANDOM_TICK_CHANCE;
        int var18 = 0;

        try
        {
            for (int var19 = 0; var19 < var8.size(); ++var19)
            {
                int[] var20 = (int[])var8.get(var19);

                if (this.catchUpBlock(world, var20[0], var20[1], var20[2], this.poisson(var16), from, gap, value))
                {
                    ++var18;
                }
            }
        }
        finally
        {
            world.skylightSubtracted = var15;
        }

        ++this.chunksCaughtUp;
        this.blocksGrown += var18;
    }

    /** Runs a block's missed random ticks; returns whether it changed. */
    private boolean catchUpBlock(World world, int x, int y, int z, int ticks, long from, long gap, int value)
    {
        int var10 = world.getBlockId(x, y, z);
        int var11 = world.getBlockMetadata(x, y, z);
        Block var12 = Block.blocksList[var10];
        int var13 = kindOf(var12, value);
        boolean var14 = false;
        ticks = Math.min(ticks, MAX_TICKS_PER_BLOCK);

        if (var13 == KIND_COLUMN && world.getBlockId(x, y - 1, z) == var10)
        {
            // A sugar cane or cactus column is caught up once, from its bottom block (only the top grows).
            return false;
        }

        for (int var15 = 0; var15 < ticks; ++var15)
        {
            if (world.getBlockId(x, y, z) != var10)
            {
                break;
            }

            if (var13 == KIND_COLUMN)
            {
                // Follow the top as it grows, up to vanilla's three blocks.
                while (world.getBlockId(x, y + 1, z) == var10 && y < 255)
                {
                    ++y;
                }

                int var16;

                for (var16 = 1; world.getBlockId(x, y - var16, z) == var10; ++var16)
                {
                    ;
                }

                if (var16 >= 3)
                {
                    break;
                }
            }
            else if (isGrown(world, var13, x, y, z))
            {
                break;
            }

            world.skylightSubtracted = skylightSubtractedAt(world, from + (long)(this.rand.nextDouble() * (double)gap));
            var12.updateTick(world, x, y, z, this.rand);
            var14 = true;
        }

        return var14 && (world.getBlockId(x, y, z) != var10 || world.getBlockMetadata(x, y, z) != var11 || var13 == KIND_COLUMN);
    }

    private static boolean isGrown(World world, int kind, int x, int y, int z)
    {
        int var5 = world.getBlockMetadata(x, y, z);

        switch (kind)
        {
            case KIND_CROPS:
                return var5 >= 7;

            case KIND_NETHER_WART:
                return var5 >= 3;

            case KIND_COCOA:
                return BlockCocoa.func_72219_c(var5) >= 2;

            case KIND_STEM:
                return var5 >= 7 && (isFruit(world.getBlockId(x - 1, y, z)) || isFruit(world.getBlockId(x + 1, y, z)) || isFruit(world.getBlockId(x, y, z - 1)) || isFruit(world.getBlockId(x, y, z + 1)));

            default:
                return false;
        }
    }

    private static boolean isFruit(int blockId)
    {
        return blockId == Block.melon.blockID || blockId == Block.pumpkin.blockID;
    }

    private static int kindOf(Block block, int value)
    {
        if (block == null)
        {
            return KIND_NONE;
        }
        else if (block instanceof BlockCrops)
        {
            return KIND_CROPS;
        }
        else if (block instanceof BlockStem)
        {
            return KIND_STEM;
        }
        else if (block instanceof BlockNetherStalk)
        {
            return KIND_NETHER_WART;
        }
        else if (block instanceof BlockCocoa)
        {
            return KIND_COCOA;
        }
        else if (value < 2)
        {
            return KIND_NONE;
        }
        else if (block instanceof BlockReed || block instanceof BlockCactus)
        {
            return KIND_COLUMN;
        }
        else
        {
            return block instanceof BlockSapling ? KIND_SAPLING : KIND_NONE;
        }
    }

    /** World.calculateSkylightSubtracted for another time of day, in clear weather. */
    private static int skylightSubtractedAt(World world, long time)
    {
        float var3 = world.provider.calculateCelestialAngle(time, 1.0F);
        float var4 = 1.0F - (MathHelper.cos(var3 * (float)Math.PI * 2.0F) * 2.0F + 0.5F);

        if (var4 < 0.0F)
        {
            var4 = 0.0F;
        }

        if (var4 > 1.0F)
        {
            var4 = 1.0F;
        }

        return (int)(var4 * 11.0F);
    }

    private int poisson(double mean)
    {
        if (mean > 50.0D)
        {
            return Math.max(0, (int)Math.round(mean + Math.sqrt(mean) * this.rand.nextGaussian()));
        }

        double var3 = Math.exp(-mean);
        double var5 = this.rand.nextDouble();
        int var7 = 0;

        while (var5 > var3)
        {
            ++var7;
            var5 *= this.rand.nextDouble();
        }

        return var7;
    }
}
