package net.minecraft.src;

import net.minecraft.client.Minecraft;

/**
 * Sign Editing: "On" makes right-clicking (the Use Item key) a sign open the sign screen again, so its text
 * can be changed. Vanilla 1.3.2 only shows that screen once, right after the sign is placed.
 *
 * The client's right-click hook (KirInteraction's player controller) opens GuiKirEditSign instead of sending
 * the click. A placed sign is locked on the server (TileEntitySign.isEditable is false once it is loaded or
 * written), so vanilla's Packet130UpdateSign would be refused; the new text is written by the integrated
 * server, on its own thread, for the host player (KirInteraction.runOnServer), which then sends it to every
 * player watching the sign. Singleplayer and LAN host only; on a remote server right-clicking a sign stays
 * vanilla. Sneaking skips the screen, so blocks can still be placed against a sign.
 */
public class KirModuleSignEditing extends KirModule
{
    private static final String[] VALUES = new String[] {"Off", "On"};

    /** How far the host may be from a sign to edit it, squared (the reach of a right-click, with some slack). */
    static final double MAX_DISTANCE_SQ = 64.0D;

    public KirModuleSignEditing()
    {
        super("signEditing", "Sign Editing", "Sign Editing", 0, "signediting", "editsigns");
    }

    public String getCategory()
    {
        return mod_KirmodExtras.CATEGORY_SIGNS;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "Lets you change what a sign says after it is placed.",
            describeValue("Off", "vanilla, a sign's text is fixed once placed."),
            describeValue("On", "right-click (Use Item) a sign to open the sign screen and edit its text. Sneak to place blocks against a sign instead."),
            note("Works in singleplayer and on a LAN world you host; elsewhere signs can't be edited, as usual.")
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

    static boolean isSign(World world, int x, int y, int z)
    {
        int var4 = world.getBlockId(x, y, z);
        return var4 == Block.signPost.blockID || var4 == Block.signWall.blockID;
    }

    /** Opens the sign screen for a sign that was right-clicked; true if it did (nothing is then sent). */
    public boolean onRightClickBlock(Minecraft mc, EntityPlayer player, World world, ItemStack stack, int x, int y, int z, int side)
    {
        if (this.getValue() != 1 || player.isSneaking() || !isSign(world, x, y, z) || !KirInteraction.serverHandlesSneakPlace(mc))
        {
            return false;
        }

        TileEntity var9 = world.getBlockTileEntity(x, y, z);

        if (!(var9 instanceof TileEntitySign) || !player.canPlayerEdit(x, y, z))
        {
            return false;
        }

        mc.displayGuiScreen(new GuiKirEditSign((TileEntitySign)var9, false));
        return true;
    }

    /**
     * Writes lines to the sign at x, y, z on the integrated server, for the host player, and sends it to the
     * players watching it. If the server won't take them, it sends the sign's real text back instead.
     */
    static void save(final int dimension, final int x, final int y, final int z, String[] lines)
    {
        final String[] var5 = new String[4];

        for (int var6 = 0; var6 < 4; ++var6)
        {
            var5[var6] = clean(var6 < lines.length ? lines[var6] : "");
        }

        KirInteraction.runOnServer(new KirInteraction.ServerTask()
        {
            public String run(EntityPlayer host)
            {
                World var2 = host.worldObj;

                if (host.dimension != dimension || !var2.blockExists(x, y, z))
                {
                    return null;
                }

                TileEntity var3 = var2.getBlockTileEntity(x, y, z);

                if (!(var3 instanceof TileEntitySign))
                {
                    return null;
                }

                if (!host.canPlayerEdit(x, y, z) || host.getDistanceSq((double)x + 0.5D, (double)y + 0.5D, (double)z + 0.5D) > MAX_DISTANCE_SQ)
                {
                    var2.markBlockNeedsUpdate(x, y, z);
                    return "§cSign Editing: that sign is too far away to edit.";
                }

                TileEntitySign var4 = (TileEntitySign)var3;
                System.arraycopy(var5, 0, var4.signText, 0, 4);
                var4.onInventoryChanged();
                var2.markBlockNeedsUpdate(x, y, z);
                return null;
            }
        });
    }

    /** A sign line as the server accepts it: at most 15 characters, only ones that can be typed in chat. */
    private static String clean(String line)
    {
        if (line == null)
        {
            return "";
        }

        StringBuilder var1 = new StringBuilder();

        for (int var2 = 0; var2 < line.length() && var1.length() < 15; ++var2)
        {
            char var3 = line.charAt(var2);

            if (ChatAllowedCharacters.allowedCharacters.indexOf(var3) >= 0)
            {
                var1.append(var3);
            }
        }

        return var1.toString();
    }
}
