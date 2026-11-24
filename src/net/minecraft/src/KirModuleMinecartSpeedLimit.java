package net.minecraft.src;

/**
 * Minecart Speed Limit: a slider from 8 blocks per second (the vanilla limit) to 100, the top speed of
 * minecarts on rails while Minecart Speed (KirModuleMinecartSpeed) is on.
 *
 * Before Kirmod 1.16 this slider was Minecart Speed itself, saved as "minecartSpeed"; a limit saved that
 * way is carried over the first time.
 */
public class KirModuleMinecartSpeedLimit extends KirModule
{
    /** The vanilla limit in blocks per second, value 0. */
    public static final int VANILLA_LIMIT = 8;
    public static final int MAX_LIMIT = 100;
    public static final int DEFAULT_LIMIT = 20;

    /** "8" to "100": value i is a limit of i + 8 blocks per second. */
    private static final String[] VALUES = new String[MAX_LIMIT - VANILLA_LIMIT + 1];

    static
    {
        for (int var0 = 0; var0 < VALUES.length; ++var0)
        {
            VALUES[var0] = String.valueOf(var0 + VANILLA_LIMIT);
        }
    }

    public KirModuleMinecartSpeedLimit()
    {
        super("minecartSpeedLimit", "Minecart Speed Limit", "Cart Limit", DEFAULT_LIMIT - VANILLA_LIMIT, "cartlimit", "minecartspeedlimit");
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
        return mod_KirmodExtras.CATEGORY_MINECARTS;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "The top speed of minecarts on rails while Minecart Speed is on. Carts gain speed from booster rails as usual, so reaching high speeds takes a long run of boosters.",
            describeValue(VANILLA_LIMIT + " blocks/s", "vanilla limit."),
            describeValue((VANILLA_LIMIT + 1) + " to " + MAX_LIMIT + " blocks/s", "carts can go up to that speed. A cart with a player in it moves 3/4 as fast, like in vanilla."),
            note("Does nothing while Minecart Speed is off.")
        };
    }

    public String getButtonString()
    {
        return super.getButtonString() + " blocks/s";
    }

    /** Accepts any limit in blocks per second, clamped to 8 to 100, as well as the value names. */
    public int findValue(String name)
    {
        try
        {
            int var2 = Integer.parseInt(name.trim());
            return Math.max(VANILLA_LIMIT, Math.min(var2, MAX_LIMIT)) - VANILLA_LIMIT;
        }
        catch (NumberFormatException var3)
        {
            return super.findValue(name);
        }
    }

    /** Carries over a limit saved by the old Minecart Speed slider, unless it was the vanilla one. */
    public void install()
    {
        String var1 = mod_Kirmod.getSetting(this.getKey(), (String)null) == null ? mod_Kirmod.getSetting("minecartSpeed", (String)null) : null;

        if (var1 != null && var1.trim().matches("\\d+") && Integer.parseInt(var1.trim()) > VANILLA_LIMIT)
        {
            this.loadValue(this.findValue(var1));
        }
    }

    /** The limit in blocks per second. */
    public int getLimit()
    {
        return this.getValue() + VANILLA_LIMIT;
    }
}
