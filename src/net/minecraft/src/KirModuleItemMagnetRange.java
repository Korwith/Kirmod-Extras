package net.minecraft.src;

/**
 * Item Magnet Range: a slider from 2 to 100 blocks, how far Item Magnet (KirModuleItemMagnet) reaches
 * while it is on.
 *
 * Before Kirmod 1.16 this slider was Item Magnet itself, saved as "itemMagnet", with "Default" for off;
 * a range saved that way is carried over the first time.
 */
public class KirModuleItemMagnetRange extends KirModule
{
    public static final int MIN_RANGE = 2;
    public static final int MAX_RANGE = 100;
    public static final int DEFAULT_RANGE = 16;

    /** "2" to "100": value i is a range of i + 2 blocks. */
    private static final String[] VALUES = new String[MAX_RANGE - MIN_RANGE + 1];

    static
    {
        for (int var0 = 0; var0 < VALUES.length; ++var0)
        {
            VALUES[var0] = String.valueOf(var0 + MIN_RANGE);
        }
    }

    public KirModuleItemMagnetRange()
    {
        super("magnetRange", "Item Magnet Range", "Magnet Range", DEFAULT_RANGE - MIN_RANGE, "magnetrange");
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public boolean isSlider()
    {
        return true;
    }

    public String getCategory()
    {
        return CATEGORY_GAMEPLAY;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "How far Item Magnet reaches while it is on.",
            describeValue(MIN_RANGE + " to " + MAX_RANGE + " blocks", "items within that many blocks fly to you."),
            note("Does nothing while Item Magnet is off.")
        };
    }

    public String getButtonString()
    {
        return super.getButtonString() + " blocks";
    }

    /** Accepts any range in blocks, clamped to 2 to 100, as well as the value names. */
    public int findValue(String name)
    {
        try
        {
            int var2 = Integer.parseInt(name.trim());
            return Math.max(MIN_RANGE, Math.min(var2, MAX_RANGE)) - MIN_RANGE;
        }
        catch (NumberFormatException var3)
        {
            return super.findValue(name);
        }
    }

    /** Carries over a range saved by the old Item Magnet slider. */
    public void install()
    {
        String var1 = mod_Kirmod.getSetting(this.getKey(), (String)null) == null ? mod_Kirmod.getSetting("itemMagnet", (String)null) : null;

        if (var1 != null && var1.trim().matches("\\d+"))
        {
            this.loadValue(this.findValue(var1));
        }
    }

    /** The pull range in blocks. */
    public int getRange()
    {
        return this.getValue() + MIN_RANGE;
    }
}
