package net.minecraft.src;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * The chunk loader Crop Catch-Up puts on each server world (see KirModuleCropCatchUp). It is vanilla's
 * AnvilChunkLoader with two additions, so chunks are read and written exactly as before:
 *
 *   - Loading: vanilla already writes the world time into every saved chunk ("LastUpdate" in the Level
 *     compound) but never reads it back. This loader notes it for each chunk it loads, as the time the
 *     chunk has to catch up from.
 *   - Saving: a chunk that is saved before it has caught up (it is waiting for its neighbours to load)
 *     keeps its old LastUpdate instead of the current time, so the growth it is owed is not lost.
 *
 * Both run on the server thread; the map is synchronized anyway because saves are handed to the file
 * IO thread right after.
 */
public class KirCatchUpChunkLoader extends AnvilChunkLoader
{
    /** Chunk key (ChunkCoordIntPair.chunkXZ2Int) to the world time it was last saved at, for chunks that still have to catch up. */
    final Map pending = new HashMap();

    /** False while Crop Catch-Up is Off: nothing is noted and saves are vanilla. */
    volatile boolean recording = true;

    final File saveLocation;

    public KirCatchUpChunkLoader(File saveLocation)
    {
        super(saveLocation);
        this.saveLocation = saveLocation;
    }

    protected Chunk checkedReadChunkFromNBT(World par1World, int par2, int par3, NBTTagCompound par4NBTTagCompound)
    {
        Chunk var5 = super.checkedReadChunkFromNBT(par1World, par2, par3, par4NBTTagCompound);

        if (var5 != null && this.recording)
        {
            NBTTagCompound var6 = par4NBTTagCompound.getCompoundTag("Level");

            if (var6.hasKey("LastUpdate"))
            {
                this.note(var5.xPosition, var5.zPosition, var6.getLong("LastUpdate"));
            }
        }

        return var5;
    }

    protected void func_75824_a(ChunkCoordIntPair par1ChunkCoordIntPair, NBTTagCompound par2NBTTagCompound)
    {
        if (this.recording)
        {
            Long var3;

            synchronized (this.pending)
            {
                var3 = (Long)this.pending.get(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(par1ChunkCoordIntPair.chunkXPos, par1ChunkCoordIntPair.chunkZPos)));
            }

            if (var3 != null)
            {
                par2NBTTagCompound.getCompoundTag("Level").setLong("LastUpdate", var3.longValue());
            }
        }

        super.func_75824_a(par1ChunkCoordIntPair, par2NBTTagCompound);
    }

    void note(int chunkX, int chunkZ, long lastUpdate)
    {
        synchronized (this.pending)
        {
            this.pending.put(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(chunkX, chunkZ)), Long.valueOf(lastUpdate));
        }
    }
}
