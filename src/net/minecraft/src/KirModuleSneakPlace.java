package net.minecraft.src;

import net.minecraft.client.Minecraft;

/**
 * Sneak Place: "On" makes sneaking while right-clicking with a block (or a sign, door, bed, redstone
 * and the like) place it, instead of opening the clicked chest, furnace, crafting table, etc. Every
 * activatable block works this way, like in later Minecraft versions.
 *
 * The integrated server does the same check on its side (see KirInteraction), so this works in
 * singleplayer and as LAN host. On a remote server, which would open the block anyway, clicks stay
 * vanilla.
 */
public class KirModuleSneakPlace extends KirModule
{
    private static final String[] VALUES = new String[] {"Off", "On"};

    public KirModuleSneakPlace()
    {
        super("sneakPlace", "Sneak Place", "Sneak Place", 0, "sneakplace");
    }

    public String getCategory()
    {
        return CATEGORY_GAMEPLAY;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "Lets you place blocks against chests, furnaces, crafting tables and other usable blocks by sneaking, like in later Minecraft versions.",
            describeValue("Off", "vanilla, right-clicking a usable block opens it, even while sneaking."),
            describeValue("On", "sneak and right-click with a block (or a sign, door, bed, redstone...) to place it instead of opening the block."),
            note("Works in singleplayer and on a LAN world you host.")
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

    /** Whether this right-click should use the held item on the block instead of activating it. */
    public boolean usesItemInsteadOfActivating(EntityPlayer player, ItemStack stack)
    {
        return this.getValue() == 1 && player.isSneaking() && placesBlock(stack);
    }

    private static boolean placesBlock(ItemStack stack)
    {
        if (stack == null)
        {
            return false;
        }

        Item var1 = stack.getItem();
        return var1 instanceof ItemBlock || var1 instanceof ItemSign || var1 instanceof ItemReed || var1 instanceof ItemRedstone || var1 instanceof ItemDoor || var1 instanceof ItemBed;
    }

    /** Gets the host player Kirmod's server-side click handler, which places on its side too. */
    public boolean hasServerPlayerTick()
    {
        return this.getValue() == 1;
    }

    public void update(Minecraft mc)
    {
        KirWorldHook.installAll(mc);
        KirInteraction.installClient(mc);
    }
}
