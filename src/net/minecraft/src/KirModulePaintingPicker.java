package net.minecraft.src;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Painting Picker (from PaintingSelectionGui): "On" makes placing a painting open a screen of every
 * painting that fits there, instead of hanging a random one.
 *
 * The client's right-click hook (KirInteraction's player controller) notices a painting being placed on
 * the side of a block, works out which paintings fit from its own copy of the world, and opens
 * GuiKirPaintingPicker instead of sending the click. The chosen painting is hung by the integrated
 * server, on its own thread, for the host player (KirInteraction.runOnServer), which checks again that
 * it fits. PaintingSelectionGui did this with its own packets and a replaced ItemPainting; here it is
 * singleplayer and LAN host only, and a remote server gets vanilla's random painting.
 */
public class KirModulePaintingPicker extends KirModule
{
    private static final String[] VALUES = new String[] {"Off", "On"};

    /** Block.onBlockActivated, found by its parameter types, to tell blocks that open something. */
    private static Method methodActivated;
    private static boolean searched = false;

    public KirModulePaintingPicker()
    {
        super("paintingPicker", "Painting Picker", "Painting Picker", 0, "paintingpicker");
    }

    public String getCategory()
    {
        return CATEGORY_ITEMS;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "Lets you choose which painting to hang, like the PaintingSelectionGui mod.",
            describeValue("Off", "vanilla, a random painting that fits."),
            describeValue("On", "placing a painting opens a list of every painting that fits on that wall; click one to hang it. With only one that fits, it is hung straight away."),
            note("Works in singleplayer and on a LAN world you host; elsewhere paintings are random as usual.")
        };
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public boolean isAvailable()
    {
        return KirInteraction.isClientAvailable() && KirWorldHook.isAvailable();
    }

    /** KirInteraction.serverHandlesSneakPlace needs the host's server-side click handler. */
    public boolean hasServerPlayerTick()
    {
        return this.getValue() == 1;
    }

    public void update(Minecraft mc)
    {
        if (this.getValue() == 1)
        {
            KirWorldHook.installAll(mc);
            KirInteraction.installClient(mc);
        }
    }

    /** Painting direction for a block side, as ItemPainting works it out, or -1 for the top and bottom. */
    static int getDirection(int side)
    {
        return side == 2 ? 0 : (side == 3 ? 2 : (side == 4 ? 1 : (side == 5 ? 3 : -1)));
    }

    /**
     * Called by the client's right-click hook before vanilla handles a click on a block. Opens the picker
     * (or hangs the only painting that fits) and returns true if the click was a painting being placed.
     */
    public boolean onRightClickBlock(Minecraft mc, EntityPlayer player, World world, ItemStack stack, int x, int y, int z, int side)
    {
        if (this.getValue() != 1 || stack == null || stack.itemID != Item.painting.shiftedIndex || !KirInteraction.serverHandlesSneakPlace(mc))
        {
            return false;
        }

        int var9 = getDirection(side);

        if (var9 < 0 || !player.canPlayerEdit(x, y, z) || !player.isSneaking() && opensSomething(world, x, y, z))
        {
            return false;
        }

        EnumArt[] var10 = fittingArt(world, x, y, z, var9);

        if (var10.length == 0)
        {
            // Nothing fits: vanilla does nothing either.
            return true;
        }

        if (var10.length == 1)
        {
            hang(x, y, z, var9, var10[0]);
        }
        else
        {
            mc.displayGuiScreen(new GuiKirPaintingPicker(var10, x, y, z, var9));
        }

        return true;
    }

    /** Whether right-clicking the block would activate it (open a chest, push a button...) in vanilla. */
    private static boolean opensSomething(World world, int x, int y, int z)
    {
        Block var4 = Block.blocksList[world.getBlockId(x, y, z)];

        if (var4 == null)
        {
            return false;
        }

        if (!searched)
        {
            searched = true;
            Class[] var5 = new Class[] {World.class, Integer.TYPE, Integer.TYPE, Integer.TYPE, EntityPlayer.class, Integer.TYPE, Float.TYPE, Float.TYPE, Float.TYPE};
            Method[] var6 = Block.class.getDeclaredMethods();

            for (int var7 = 0; var7 < var6.length; ++var7)
            {
                if (var6[var7].getReturnType() == Boolean.TYPE && Arrays.equals(var6[var7].getParameterTypes(), var5))
                {
                    methodActivated = var6[var7];
                }
            }
        }

        if (methodActivated == null)
        {
            // Can't tell: be safe and let vanilla activate it.
            return true;
        }

        try
        {
            return var4.getClass().getMethod(methodActivated.getName(), methodActivated.getParameterTypes()).getDeclaringClass() != Block.class;
        }
        catch (NoSuchMethodException var8)
        {
            return true;
        }
    }

    /** The paintings that fit on that side of that block, biggest first (PaintingSelectionGui's order). */
    static EnumArt[] fittingArt(World world, int x, int y, int z, int direction)
    {
        EntityPainting var5 = new EntityPainting(world, x, y, z, direction);
        EnumArt[] var6 = EnumArt.values();
        ArrayList var7 = new ArrayList();

        for (int var8 = 0; var8 < var6.length; ++var8)
        {
            var5.art = var6[var8];
            var5.setDirection(direction);

            if (var5.onValidSurface())
            {
                var7.add(var6[var8]);
            }
        }

        EnumArt[] var9 = (EnumArt[])var7.toArray(new EnumArt[var7.size()]);
        Arrays.sort(var9, new Comparator()
        {
            public int compare(Object a, Object b)
            {
                EnumArt var3 = (EnumArt)a;
                EnumArt var4 = (EnumArt)b;
                int var5 = var4.sizeX * var4.sizeY - var3.sizeX * var3.sizeY;
                return var5 != 0 ? var5 : var4.sizeY - var3.sizeY;
            }
        });
        return var9;
    }

    /** Hangs the painting on the integrated server, for the host player, taking one from their hand. */
    static void hang(final int x, final int y, final int z, final int direction, final EnumArt art)
    {
        KirInteraction.runOnServer(new KirInteraction.ServerTask()
        {
            public String run(EntityPlayer player)
            {
                ItemStack var2 = player.inventory.getCurrentItem();

                if (var2 == null || var2.itemID != Item.painting.shiftedIndex || !player.canPlayerEdit(x, y, z))
                {
                    return null;
                }

                EntityPainting var3 = new EntityPainting(player.worldObj, x, y, z, direction);
                var3.art = art;
                var3.setDirection(direction);

                if (!var3.onValidSurface())
                {
                    return "\u00a7c" + art.title + " doesn't fit there any more.";
                }

                player.worldObj.spawnEntityInWorld(var3);

                if (!player.capabilities.isCreativeMode)
                {
                    --var2.stackSize;

                    if (var2.stackSize <= 0)
                    {
                        player.inventory.mainInventory[player.inventory.currentItem] = null;
                    }
                }

                return null;
            }
        });
    }
}
