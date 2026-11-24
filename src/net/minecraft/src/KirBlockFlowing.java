package net.minecraft.src;

import java.util.Random;

/**
 * Flowing water and lava for Lava Decay and Infinite Water (from the LavaDecay mod, which replaced
 * BlockFlowing itself). Block.waterMoving and Block.lavaMoving are swapped for these with
 * KirUtil.replaceBlock the first time either module is installed.
 *
 * While both settings are vanilla, updateTick is vanilla's (super), so the blocks behave exactly as
 * before, Forge or not. Otherwise it is a copy of vanilla's with two changes:
 * - Lava Decay "Fast": lava that is drying up (its level dropping) does so every tick, like water,
 *   instead of skipping 3 ticks in 4. Lava left behind by a removed source drains quickly.
 * - Infinite Water "Off": flowing water between two sources no longer becomes a source.
 *
 * The helpers vanilla keeps private are copied here, with their own flow arrays.
 */
public class KirBlockFlowing extends BlockFlowing
{
    private int adjacentSources = 0;
    private final boolean[] optimalFlow = new boolean[4];
    private final int[] flowCosts = new int[4];

    public KirBlockFlowing(int par1, Material par2Material)
    {
        super(KirUtil.constructSlot(par1), par2Material);
    }

    /** Swaps Block.waterMoving and Block.lavaMoving for KirBlockFlowings; once, for both modules. */
    static void installAll()
    {
        if (Block.waterMoving instanceof KirBlockFlowing || Block.lavaMoving instanceof KirBlockFlowing)
        {
            return;
        }

        replace(Block.waterMoving, "waterMoving", "A");
        replace(Block.lavaMoving, "lavaMoving", "C");
    }

    private static void replace(Block original, String mcpName, String obfName)
    {
        try
        {
            KirUtil.replaceBlock(original, new KirBlockFlowing(original.blockID, original.blockMaterial), mcpName, obfName);
        }
        catch (Exception var4)
        {
            Block.blocksList[original.blockID] = original;
            KirUtil.log("Could not replace " + mcpName, var4);
        }
    }

    public static boolean isInstalled()
    {
        return Block.blocksList[Block.waterMoving.blockID] instanceof KirBlockFlowing || Block.blocksList[Block.lavaMoving.blockID] instanceof KirBlockFlowing;
    }

    private static boolean fastLava()
    {
        return mod_KirmodExtras.LAVA_DECAY.isEnabled() && mod_KirmodExtras.LAVA_DECAY.getValue() == 1;
    }

    private static boolean infiniteWater()
    {
        return !mod_KirmodExtras.INFINITE_WATER.isEnabled() || mod_KirmodExtras.INFINITE_WATER.getValue() == 0;
    }

    public void updateTick(World par1World, int par2, int par3, int par4, Random par5Random)
    {
        boolean var6 = this.blockMaterial == Material.lava;

        if (var6 ? !fastLava() : infiniteWater())
        {
            super.updateTick(par1World, par2, par3, par4, par5Random);
            return;
        }

        int var7 = this.getFlowDecay(par1World, par2, par3, par4);
        byte var8 = 1;

        if (var6 && !par1World.provider.isHellWorld)
        {
            var8 = 2;
        }

        boolean var9 = true;
        int var10;

        if (var7 > 0)
        {
            this.adjacentSources = 0;
            int var11 = this.smallestFlowDecay(par1World, par2 - 1, par3, par4, -100);
            var11 = this.smallestFlowDecay(par1World, par2 + 1, par3, par4, var11);
            var11 = this.smallestFlowDecay(par1World, par2, par3, par4 - 1, var11);
            var11 = this.smallestFlowDecay(par1World, par2, par3, par4 + 1, var11);
            var10 = var11 + var8;

            if (var10 >= 8 || var11 < 0)
            {
                var10 = -1;
            }

            if (this.getFlowDecay(par1World, par2, par3 + 1, par4) >= 0)
            {
                int var12 = this.getFlowDecay(par1World, par2, par3 + 1, par4);
                var10 = var12 >= 8 ? var12 : var12 + 8;
            }

            if (this.adjacentSources >= 2 && this.blockMaterial == Material.water && infiniteWater())
            {
                if (par1World.getBlockMaterial(par2, par3 - 1, par4).isSolid())
                {
                    var10 = 0;
                }
                else if (par1World.getBlockMaterial(par2, par3 - 1, par4) == this.blockMaterial && par1World.getBlockMetadata(par2, par3, par4) == 0)
                {
                    var10 = 0;
                }
            }

            if (var6 && !fastLava() && var7 < 8 && var10 < 8 && var10 > var7 && par5Random.nextInt(4) != 0)
            {
                var10 = var7;
                var9 = false;
            }

            if (var10 == var7)
            {
                if (var9)
                {
                    this.makeStill(par1World, par2, par3, par4);
                }
            }
            else
            {
                var7 = var10;

                if (var10 < 0)
                {
                    par1World.setBlockWithNotify(par2, par3, par4, 0);
                }
                else
                {
                    par1World.setBlockMetadataWithNotify(par2, par3, par4, var10);
                    par1World.scheduleBlockUpdate(par2, par3, par4, this.blockID, this.tickRate());
                    par1World.notifyBlocksOfNeighborChange(par2, par3, par4, this.blockID);
                }
            }
        }
        else
        {
            this.makeStill(par1World, par2, par3, par4);
        }

        if (this.canDisplace(par1World, par2, par3 - 1, par4))
        {
            if (var6 && par1World.getBlockMaterial(par2, par3 - 1, par4) == Material.water)
            {
                par1World.setBlockWithNotify(par2, par3 - 1, par4, Block.stone.blockID);
                this.triggerLavaMixEffects(par1World, par2, par3 - 1, par4);
                return;
            }

            this.flowInto(par1World, par2, par3 - 1, par4, var7 >= 8 ? var7 : var7 + 8);
        }
        else if (var7 >= 0 && (var7 == 0 || this.blocksFlow(par1World, par2, par3 - 1, par4)))
        {
            boolean[] var13 = this.optimalDirections(par1World, par2, par3, par4);
            var10 = var7 >= 8 ? 1 : var7 + var8;

            if (var10 >= 8)
            {
                return;
            }

            if (var13[0])
            {
                this.flowInto(par1World, par2 - 1, par3, par4, var10);
            }

            if (var13[1])
            {
                this.flowInto(par1World, par2 + 1, par3, par4, var10);
            }

            if (var13[2])
            {
                this.flowInto(par1World, par2, par3, par4 - 1, var10);
            }

            if (var13[3])
            {
                this.flowInto(par1World, par2, par3, par4 + 1, var10);
            }
        }
    }

    /** Vanilla's updateFlow: turns this block into the still liquid (the next block ID). */
    private void makeStill(World world, int x, int y, int z)
    {
        int var5 = world.getBlockMetadata(x, y, z);
        world.setBlockAndMetadata(x, y, z, this.blockID + 1, var5);
        world.markBlocksDirty(x, y, z, x, y, z);
    }

    private void flowInto(World world, int x, int y, int z, int level)
    {
        if (this.canDisplace(world, x, y, z))
        {
            int var6 = world.getBlockId(x, y, z);

            if (var6 > 0)
            {
                if (this.blockMaterial == Material.lava)
                {
                    this.triggerLavaMixEffects(world, x, y, z);
                }
                else
                {
                    Block.blocksList[var6].dropBlockAsItem(world, x, y, z, world.getBlockMetadata(x, y, z), 0);
                }
            }

            world.setBlockAndMetadataWithNotify(x, y, z, this.blockID, level);
        }
    }

    private int flowCost(World world, int x, int y, int z, int depth, int from)
    {
        int var7 = 1000;

        for (int var8 = 0; var8 < 4; ++var8)
        {
            if ((var8 != 0 || from != 1) && (var8 != 1 || from != 0) && (var8 != 2 || from != 3) && (var8 != 3 || from != 2))
            {
                int var9 = x + (var8 == 0 ? -1 : (var8 == 1 ? 1 : 0));
                int var10 = z + (var8 == 2 ? -1 : (var8 == 3 ? 1 : 0));

                if (!this.blocksFlow(world, var9, y, var10) && (world.getBlockMaterial(var9, y, var10) != this.blockMaterial || world.getBlockMetadata(var9, y, var10) != 0))
                {
                    if (!this.blocksFlow(world, var9, y - 1, var10))
                    {
                        return depth;
                    }

                    if (depth < 4)
                    {
                        int var11 = this.flowCost(world, var9, y, var10, depth + 1, var8);

                        if (var11 < var7)
                        {
                            var7 = var11;
                        }
                    }
                }
            }
        }

        return var7;
    }

    private boolean[] optimalDirections(World world, int x, int y, int z)
    {
        for (int var5 = 0; var5 < 4; ++var5)
        {
            this.flowCosts[var5] = 1000;
            int var6 = x + (var5 == 0 ? -1 : (var5 == 1 ? 1 : 0));
            int var7 = z + (var5 == 2 ? -1 : (var5 == 3 ? 1 : 0));

            if (!this.blocksFlow(world, var6, y, var7) && (world.getBlockMaterial(var6, y, var7) != this.blockMaterial || world.getBlockMetadata(var6, y, var7) != 0))
            {
                this.flowCosts[var5] = this.blocksFlow(world, var6, y - 1, var7) ? this.flowCost(world, var6, y, var7, 1, var5) : 0;
            }
        }

        int var8 = this.flowCosts[0];

        for (int var9 = 1; var9 < 4; ++var9)
        {
            if (this.flowCosts[var9] < var8)
            {
                var8 = this.flowCosts[var9];
            }
        }

        for (int var10 = 0; var10 < 4; ++var10)
        {
            this.optimalFlow[var10] = this.flowCosts[var10] == var8;
        }

        return this.optimalFlow;
    }

    private boolean blocksFlow(World world, int x, int y, int z)
    {
        int var5 = world.getBlockId(x, y, z);

        if (var5 != Block.doorWood.blockID && var5 != Block.doorSteel.blockID && var5 != Block.signPost.blockID && var5 != Block.ladder.blockID && var5 != Block.reed.blockID)
        {
            if (var5 == 0 || Block.blocksList[var5] == null)
            {
                return false;
            }

            Material var6 = Block.blocksList[var5].blockMaterial;
            return var6 == Material.portal ? true : var6.blocksMovement();
        }

        return true;
    }

    private int smallestFlowDecay(World world, int x, int y, int z, int smallest)
    {
        int var6 = this.getFlowDecay(world, x, y, z);

        if (var6 < 0)
        {
            return smallest;
        }

        if (var6 == 0)
        {
            ++this.adjacentSources;
        }

        if (var6 >= 8)
        {
            var6 = 0;
        }

        return smallest >= 0 && var6 >= smallest ? smallest : var6;
    }

    private boolean canDisplace(World world, int x, int y, int z)
    {
        Material var5 = world.getBlockMaterial(x, y, z);
        return var5 == this.blockMaterial ? false : (var5 == Material.lava ? false : !this.blocksFlow(world, x, y, z));
    }
}
