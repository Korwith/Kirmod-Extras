package net.minecraft.src;

/**
 * Stack Sizes (from StackMaster): "64" lets boats, minecarts, doors, signs, saddles, eggs, snowballs and
 * ender pearls stack to 64.
 *
 * Left out on purpose: potions, every bucket (empty, water, lava, milk) and mushroom stew. Using one of
 * those swaps the whole held stack for an empty bottle, bucket or bowl, so a stack of them would lose
 * all but one.
 *
 * Item.maxStackSize is shared by the client and the integrated server, so this works in singleplayer
 * and on a LAN world you host. A remote server keeps its own stack sizes and corrects the client.
 */
public class KirModuleStackSizes extends KirModule
{
    private static final String[] VALUES = new String[] {"Vanilla", "64"};

    private Item[] items;
    private int[] vanillaSizes;

    public KirModuleStackSizes()
    {
        super("stackSizes", "Stack Sizes", "Stack Sizes", 0, "stacksizes");
    }

    public String getCategory()
    {
        return CATEGORY_ITEMS;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "Lets items that normally stack to 1 or 16 stack to 64.",
            describeValue("Vanilla", "vanilla stack sizes."),
            describeValue("64", "boats, minecarts (all three), wooden and iron doors, signs, saddles, eggs, snowballs and ender pearls stack to 64."),
            note("Potions, buckets and mushroom stew are left alone: using one empties the whole stack. Works in singleplayer and on a LAN world you host.")
        };
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public void install()
    {
        this.items = new Item[] {Item.boat, Item.minecartEmpty, Item.minecartCrate, Item.minecartPowered, Item.doorWood, Item.doorSteel, Item.sign, Item.saddle, Item.egg, Item.snowball, Item.enderPearl};
        this.vanillaSizes = new int[this.items.length];

        for (int var1 = 0; var1 < this.items.length; ++var1)
        {
            this.vanillaSizes[var1] = this.items[var1].getItemStackLimit();
        }

        this.apply();
    }

    protected void onValueChanged()
    {
        this.apply();
    }

    private void apply()
    {
        if (this.items == null)
        {
            return;
        }

        for (int var1 = 0; var1 < this.items.length; ++var1)
        {
            this.items[var1].setMaxStackSize(this.getValue() == 1 ? 64 : this.vanillaSizes[var1]);
        }
    }
}
