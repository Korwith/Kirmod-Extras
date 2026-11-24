package net.minecraft.src;

import java.util.List;
import java.util.Random;

/**
 * Tripwire Transmutation: "On" brings back the block transmutation bug of snapshots 12w22a-12w26a.
 *
 * When a string changes (placed, broken, stepped on or off), it looks for tripwire hooks along +z and -x
 * and updates them, passing its own metadata and its distance. 12w27a added a check that the hook faces
 * back along that line. Without it, a string on the -z side of a hook strung along x (or the +x side of
 * one strung along z) updates that hook, which takes the block at that distance in its own line (the
 * block next to it) for the string and writes the string's metadata into it with the "attached" bit:
 * 5 or 7 (suspended) when an entity steps on, 4 or 6 when it steps off. Any block but a hook works,
 * whatever it is, and the value stays.
 *
 * The string block is swapped for a subclass whose hook search leaves the check out. The integrated server
 * uses the same block, so it works in singleplayer and on a LAN world you host; a remote server is vanilla.
 */
public class KirModuleTripwireTransmutation extends KirModule
{
    private static final String[] VALUES = new String[] {"Off", "On"};

    private boolean available = false;

    public KirModuleTripwireTransmutation()
    {
        super("tripwireTransmutation", "Tripwire Transmutation", "Tripwire Bug", 0, "tripwirebug", "transmutation");
    }

    public String getCategory()
    {
        return CATEGORY_WORLD;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "Brings back the tripwire hook transmutation bug of snapshots 12w22a to 12w26a, which set the block between two tripwire hooks to data value 4-7.",
            describeValue("Off", "tripwires work as in 1.3.2."),
            describeValue("On", "string on the -z side of a hook strung along x (+x side along z) sets the block next to that hook to 5 or 7 when stepped on, 4 or 6 when stepped off (7/6 if the string hangs in the air)."),
            note("Works in singleplayer and on a LAN world you host. Blocks keep the data value when turned off.")
        };
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public boolean isAvailable()
    {
        return this.available;
    }

    public boolean isOn()
    {
        return this.getValue() == 1 && this.available && this.isEnabled();
    }

    public void install()
    {
        try
        {
            this.available = KirUtil.replaceBlock(Block.tripWire, new KirBlockTripWire(Block.tripWire.blockID), "tripWire", "bU");
        }
        catch (Exception var2)
        {
            KirUtil.log("Could not install tripwire transmutation", var2);
        }
    }

    /**
     * Vanilla's BlockTripWire with 12w26a's hook search. The methods that search for hooks are copies of
     * vanilla's private ones, used on the server while the module is on.
     */
    public static class KirBlockTripWire extends BlockTripWire
    {
        public KirBlockTripWire(int par1)
        {
            super(KirUtil.constructSlot(par1));
        }

        private static boolean bugged(World par0World)
        {
            return !par0World.isRemote && mod_KirmodExtras.TRIPWIRE_TRANSMUTATION.isOn();
        }

        public void onBlockAdded(World par1World, int par2, int par3, int par4)
        {
            if (!bugged(par1World))
            {
                super.onBlockAdded(par1World, par2, par3, par4);
                return;
            }

            int var5 = par1World.doesBlockHaveSolidTopSurface(par2, par3 - 1, par4) ? 0 : 2;
            par1World.setBlockMetadataWithNotify(par2, par3, par4, var5);
            this.notifyHooks(par1World, par2, par3, par4, var5);
        }

        public void breakBlock(World par1World, int par2, int par3, int par4, int par5, int par6)
        {
            if (!bugged(par1World))
            {
                super.breakBlock(par1World, par2, par3, par4, par5, par6);
                return;
            }

            this.notifyHooks(par1World, par2, par3, par4, par6 | 1);
        }

        public void onEntityCollidedWithBlock(World par1World, int par2, int par3, int par4, Entity par5Entity)
        {
            if (!bugged(par1World))
            {
                super.onEntityCollidedWithBlock(par1World, par2, par3, par4, par5Entity);
                return;
            }

            if ((par1World.getBlockMetadata(par2, par3, par4) & 1) != 1)
            {
                this.updateTripWireState(par1World, par2, par3, par4);
            }
        }

        public void updateTick(World par1World, int par2, int par3, int par4, Random par5Random)
        {
            if (!bugged(par1World))
            {
                super.updateTick(par1World, par2, par3, par4, par5Random);
                return;
            }

            if ((par1World.getBlockMetadata(par2, par3, par4) & 1) == 1)
            {
                this.updateTripWireState(par1World, par2, par3, par4);
            }
        }

        /** BlockTripWire.func_72149_e without the hook facing check (12w26a). */
        private void notifyHooks(World par1World, int par2, int par3, int par4, int par5)
        {
            for (int var6 = 0; var6 < 2; ++var6)
            {
                for (int var7 = 1; var7 < 42; ++var7)
                {
                    int var8 = par2 + Direction.offsetX[var6] * var7;
                    int var9 = par4 + Direction.offsetZ[var6] * var7;
                    int var10 = par1World.getBlockId(var8, par3, var9);

                    if (var10 == Block.tripWireSource.blockID)
                    {
                        Block.tripWireSource.func_72143_a(par1World, var8, par3, var9, var10, par1World.getBlockMetadata(var8, par3, var9), true, var7, par5);
                        break;
                    }

                    if (var10 != Block.tripWire.blockID)
                    {
                        break;
                    }
                }
            }
        }

        /** Copy of BlockTripWire.updateTripWireState, calling notifyHooks. */
        private void updateTripWireState(World par1World, int par2, int par3, int par4)
        {
            int var5 = par1World.getBlockMetadata(par2, par3, par4);
            boolean var6 = (var5 & 1) == 1;
            List var8 = par1World.getEntitiesWithinAABBExcludingEntity((Entity)null, AxisAlignedBB.getAABBPool().addOrModifyAABBInPool((double)par2 + this.minX, (double)par3 + this.minY, (double)par4 + this.minZ, (double)par2 + this.maxX, (double)par3 + this.maxY, (double)par4 + this.maxZ));
            boolean var7 = !var8.isEmpty();

            if (var7 && !var6)
            {
                var5 |= 1;
            }

            if (!var7 && var6)
            {
                var5 &= -2;
            }

            if (var7 != var6)
            {
                par1World.setBlockMetadataWithNotify(par2, par3, par4, var5);
                this.notifyHooks(par1World, par2, par3, par4, var5);
            }

            if (var7)
            {
                par1World.scheduleBlockUpdate(par2, par3, par4, this.blockID, this.tickRate());
            }
        }
    }
}
