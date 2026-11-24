package net.minecraft.src;

import net.minecraft.client.Minecraft;

/**
 * Bridge Assist (from BridgeAssist): while the use button is held with a block in hand and the
 * crosshair is not on a block, a block is placed in front of your feet, against whichever of its
 * neighbours is solid, at most every 140 ms. Walking backwards off an edge, or forwards while looking
 * up, builds a bridge without sneaking to the edge.
 *
 * Placement goes through mc.playerController.onPlayerRightClick like a normal click, so it works on
 * servers too, and Sneak Place and the Tile Suppressor see it like any other click.
 */
public class KirModuleBridgeAssist extends KirModule
{
    private static final String[] VALUES = new String[] {"Off", "On"};
    private static final long COOLDOWN_MS = 140L;

    /** Neighbour offset and the side of the neighbour that faces the target: dx, dy, dz, side. */
    private static final int[][] NEIGHBOURS = new int[][] {{0, -1, 0, 1}, {0, 1, 0, 0}, {0, 0, -1, 3}, {0, 0, 1, 2}, {-1, 0, 0, 5}, {1, 0, 0, 4}};

    private long lastPlacement = 0L;

    public KirModuleBridgeAssist()
    {
        super("bridgeAssist", "Bridge Assist", "Bridge Assist", 0, "bridgeassist");
    }

    public String getCategory()
    {
        return CATEGORY_GAMEPLAY;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "Helps building bridges: holding right-click with a block while not pointing at one places blocks in front of your feet.",
            describeValue("Off", "vanilla, right-click only places against the block you point at."),
            describeValue("On", "with a block in hand, hold right-click while pointing at air (or water or lava): a block goes in front of your feet whenever that spot is empty, up to 7 a second."),
            note("Placing against a block you point at stays vanilla. Works on servers too.")
        };
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public void update(Minecraft mc)
    {
        if (this.getValue() != 1 || mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null || mc.playerController == null || !mc.gameSettings.keyBindUseItem.pressed)
        {
            return;
        }

        MovingObjectPosition var2 = mc.objectMouseOver;

        if (var2 != null && var2.typeOfHit == EnumMovingObjectType.TILE && !isReplaceable(mc.theWorld, var2.blockX, var2.blockY, var2.blockZ))
        {
            // Pointing at a block: vanilla places against it.
            return;
        }

        long var3 = System.currentTimeMillis();

        if (var3 - this.lastPlacement < COOLDOWN_MS)
        {
            return;
        }

        ItemStack var5 = mc.thePlayer.inventory.getCurrentItem();

        if (var5 == null || !(var5.getItem() instanceof ItemBlock))
        {
            return;
        }

        double var6 = Math.toRadians((double)mc.thePlayer.rotationYaw);
        int var8 = MathHelper.floor_double(mc.thePlayer.posX - Math.sin(var6) * 0.7D);
        int var9 = MathHelper.floor_double(mc.thePlayer.boundingBox.minY - 0.5D);
        int var10 = MathHelper.floor_double(mc.thePlayer.posZ + Math.cos(var6) * 0.7D);

        if (isReplaceable(mc.theWorld, var8, var9, var10) && this.place(mc, var8, var9, var10, var5))
        {
            this.lastPlacement = var3;
            mc.thePlayer.swingItem();
        }
    }

    private boolean place(Minecraft mc, int x, int y, int z, ItemStack stack)
    {
        for (int var6 = 0; var6 < NEIGHBOURS.length; ++var6)
        {
            int[] var7 = NEIGHBOURS[var6];
            int var8 = x + var7[0];
            int var9 = y + var7[1];
            int var10 = z + var7[2];

            if (!isReplaceable(mc.theWorld, var8, var9, var10))
            {
                // The hit point is the middle of the neighbour's face that touches the target.
                Vec3 var11 = Vec3.getVec3Pool().getVecFromPool((double)var8 + 0.5D - (double)var7[0] * 0.5D, (double)var9 + 0.5D - (double)var7[1] * 0.5D, (double)var10 + 0.5D - (double)var7[2] * 0.5D);

                if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, stack, var8, var9, var10, var7[3], var11))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isReplaceable(World world, int x, int y, int z)
    {
        int var4 = world.getBlockId(x, y, z);

        if (var4 == 0)
        {
            return true;
        }

        Block var5 = Block.blocksList[var4];
        return var5 == null || var5.blockMaterial == Material.air || var5.blockMaterial == Material.water || var5.blockMaterial == Material.lava;
    }
}
