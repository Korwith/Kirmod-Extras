package net.minecraft.src;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;

/**
 * Item Magnet: "On" makes every item within Item Magnet Range (KirModuleItemMagnetRange) fly to the
 * player, through walls, and get picked up. "Off" (the default) leaves item pickup vanilla. The Item
 * Magnet key (L by default, in Controls) switches it too.
 *
 * It runs on the integrated server, for the host player, every server tick (onServerPlayerTick, from Kirmod's KirInteraction), and
 * picks items up through the vanilla pickup, so full inventories, pickup delays and achievements work
 * as normal. On a remote server it does nothing, as the server decides what gets picked up.
 */
public class KirModuleItemMagnet extends KirModule
{
    private static final String[] VALUES = new String[] {"Off", "On"};

    /** Items closer than this are picked up straight away. */
    private static final double PICKUP_DISTANCE = 1.5D;

    /** Items being pulled, which have collisions turned off; server thread only. */
    private Set pulled = new HashSet();

    public KirModuleItemMagnet()
    {
        super("itemMagnet", "Item Magnet", "Item Magnet", 0, "magnet", "itemmagnet");
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public String getCategory()
    {
        return CATEGORY_GAMEPLAY;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "Pulls dropped items to you from further away, through walls, and picks them up. Full inventories and pickup delays work as normal.",
            describeValue("Off", "vanilla item pickup."),
            describeValue("On", "items within Magnet Range fly to you."),
            note("The Item Magnet key (L, see Controls) switches it on and off. Works in singleplayer and on a LAN world you host.")
        };
    }

    public boolean isAvailable()
    {
        return KirWorldHook.isAvailable();
    }

    /** The pull range in blocks, or 0 for the vanilla pickup only. */
    public int getRange()
    {
        return this.isEnabled() && this.getValue() == 1 ? mod_KirmodExtras.ITEM_MAGNET_RANGE.getRange() : 0;
    }

    public void update(Minecraft mc)
    {
        KirWorldHook.installAll(mc);
    }

    public boolean hasServerPlayerTick()
    {
        return true;
    }

    /** Pulls items towards player. Called on the server thread, every tick, for the host player. */
    public void onServerPlayerTick(EntityPlayer player)
    {
        int var2 = player.isDead ? 0 : this.getRange();
        HashSet var3 = new HashSet();

        if (var2 > 0)
        {
            double var4 = player.posX;
            double var6 = (player.boundingBox.minY + player.boundingBox.maxY) / 2.0D;
            double var8 = player.posZ;
            List var10 = player.worldObj.getEntitiesWithinAABB(EntityItem.class, player.boundingBox.expand((double)var2, (double)var2, (double)var2));
            Iterator var11 = var10.iterator();

            while (var11.hasNext())
            {
                EntityItem var12 = (EntityItem)var11.next();

                if (var12.isDead || var12.delayBeforeCanPickup > 0)
                {
                    continue;
                }

                double var13 = var4 - var12.posX;
                double var15 = var6 - var12.posY;
                double var17 = var8 - var12.posZ;
                double var19 = Math.sqrt(var13 * var13 + var15 * var15 + var17 * var17);

                if (var19 > (double)var2)
                {
                    continue;
                }

                if (var19 < PICKUP_DISTANCE)
                {
                    // The vanilla pickup: checks inventory space and sends the pickup animation.
                    var12.onCollideWithPlayer(player);
                    continue;
                }

                // Faster from further away: 100 blocks takes about three seconds.
                double var21 = Math.min(2.0D, Math.max(0.4D, var19 * 0.1D)) / var19;
                var12.motionX = var13 * var21;
                var12.motionY = var15 * var21;
                var12.motionZ = var17 * var21;
                var12.noClip = true;
                var3.add(var12);
            }
        }

        // Items that left the range (or were reached, or the magnet was turned down) collide again.
        Iterator var23 = this.pulled.iterator();

        while (var23.hasNext())
        {
            EntityItem var24 = (EntityItem)var23.next();

            if (!var3.contains(var24))
            {
                var24.noClip = false;
            }
        }

        this.pulled = var3;
    }
}
