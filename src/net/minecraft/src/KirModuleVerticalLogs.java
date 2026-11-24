package net.minecraft.src;

import net.minecraft.client.Minecraft;

/**
 * Vertical Logs: "Sneak" places logs upright while sneaking, "On" always places them upright, instead
 * of facing them the way the player looks.
 *
 * The log block is swapped for a subclass. The integrated server uses the same block, so it places the
 * log the same way, for the host player only. On a remote server the server decides, so logs are
 * placed vanilla-style there.
 */
public class KirModuleVerticalLogs extends KirModule
{
    private static final String[] VALUES = new String[] {"Default", "Sneak", "On"};

    private boolean available = false;

    public KirModuleVerticalLogs()
    {
        super("verticalLogs", "Vertical Logs", "Vertical Logs", 0, "verticallogs", "logs");
    }

    public String getCategory()
    {
        return CATEGORY_GAMEPLAY;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "Places logs standing upright instead of facing the way you look, so you don't have to aim at the ground to build log pillars.",
            describeValue("Default", "vanilla, logs face the way you look."),
            describeValue("Sneak", "logs placed while sneaking are upright."),
            describeValue("On", "logs are always placed upright."),
            note("Works in singleplayer and on a LAN world you host.")
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

    public void install()
    {
        try
        {
            this.available = KirUtil.replaceBlock(Block.wood, new KirBlockLog(Block.wood.blockID), "wood", "J");
        }
        catch (Exception var2)
        {
            KirUtil.log("Could not install vertical logs", var2);
        }
    }

    /** Whether a log placed by entity in world should be upright. */
    public boolean placesUpright(World world, EntityLiving entity)
    {
        int var3 = this.isEnabled() ? this.getValue() : 0;

        if (var3 == 0 || !(entity instanceof EntityPlayer) || var3 == 1 && !entity.isSneaking())
        {
            return false;
        }

        // Only where Kirmod also runs the server: the client predicts what the integrated server does.
        return world.isRemote ? entity == ModLoader.getMinecraftInstance().thePlayer && KirInteraction.isOnIntegratedServer(ModLoader.getMinecraftInstance()) : KirInteraction.isHost((EntityPlayer)entity);
    }

    public static class KirBlockLog extends BlockLog
    {
        public KirBlockLog(int par1)
        {
            super(KirUtil.constructSlot(par1));
        }

        public void onBlockPlacedBy(World par1World, int par2, int par3, int par4, EntityLiving par5EntityLiving)
        {
            if (mod_KirmodExtras.VERTICAL_LOGS.placesUpright(par1World, par5EntityLiving))
            {
                // Orientation bits 0: upright. The low two bits are the wood type.
                par1World.setBlockMetadataWithNotify(par2, par3, par4, par1World.getBlockMetadata(par2, par3, par4) & 3);
            }
            else
            {
                super.onBlockPlacedBy(par1World, par2, par3, par4, par5EntityLiving);
            }
        }
    }
}
