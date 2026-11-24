package net.minecraft.src;

import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * XP Pickup: "Instant" removes the wait between absorbing experience orbs.
 *
 * Vanilla lets a player absorb one orb every 2 ticks (EntityPlayer.xpCooldown), so a pile of orbs
 * trickles in. On the integrated server, every tick, the host player absorbs every orb touching them at
 * once, through the vanilla pickup (sound, pickup animation, experience). On a remote server it does
 * nothing, as the server decides when orbs are picked up.
 */
public class KirModuleXpPickup extends KirModule
{
    private static final String[] VALUES = new String[] {"Default", "Instant"};

    public KirModuleXpPickup()
    {
        super("xpPickup", "XP Pickup", "XP Pickup", 0, "xppickup", "xp");
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
            "How fast you absorb experience orbs.",
            describeValue("Default", "vanilla, one orb every tenth of a second, so a big pile takes a while."),
            describeValue("Instant", "every orb touching you is absorbed at once."),
            note("The Instant XP Pickup key (K, see Controls) switches it. Works in singleplayer and on a LAN world you host.")
        };
    }

    public boolean isAvailable()
    {
        return KirWorldHook.isAvailable();
    }

    public void update(Minecraft mc)
    {
        KirWorldHook.installAll(mc);
    }

    public boolean hasServerPlayerTick()
    {
        return this.getValue() == 1;
    }

    /** Absorbs every orb touching player. Called on the server thread, every tick, for the host player. */
    public void onServerPlayerTick(EntityPlayer player)
    {
        if (this.getValue() != 1 || player.isDead || player.getHealth() <= 0)
        {
            return;
        }

        // The area vanilla's EntityPlayer.onLivingUpdate collides with.
        List var2 = player.worldObj.getEntitiesWithinAABB(EntityXPOrb.class, player.boundingBox.expand(1.0D, 0.0D, 1.0D));

        for (int var3 = 0; var3 < var2.size(); ++var3)
        {
            EntityXPOrb var4 = (EntityXPOrb)var2.get(var3);

            if (!var4.isDead)
            {
                var4.field_70532_c = 0;
                player.xpCooldown = 0;
                var4.onCollideWithPlayer(player);
            }
        }

        player.xpCooldown = 0;
    }
}
