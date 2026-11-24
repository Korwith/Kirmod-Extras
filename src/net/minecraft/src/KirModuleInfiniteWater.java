package net.minecraft.src;

/**
 * Infinite Water: "Off" stops flowing water between two water sources from becoming a source itself, so
 * a 2x1 pool no longer refills buckets forever. See KirBlockFlowing, which this shares with Lava Decay.
 */
public class KirModuleInfiniteWater extends KirModule
{
    private static final String[] VALUES = new String[] {"On", "Off"};

    public KirModuleInfiniteWater()
    {
        super("infiniteWater", "Infinite Water", "Infinite Water", 0, "infinitewater");
    }

    public String getCategory()
    {
        return CATEGORY_WORLD;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "Whether flowing water next to two water sources becomes a new source.",
            describeValue("On", "vanilla, a 2x2 (or 2x1 on solid ground) pool is an endless water supply."),
            describeValue("Off", "only the sources you place exist; every bucket you take is gone."),
            note("Works in singleplayer and on a LAN world you host.")
        };
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public boolean isAvailable()
    {
        return KirBlockFlowing.isInstalled();
    }

    public void install()
    {
        KirBlockFlowing.installAll();
    }
}
