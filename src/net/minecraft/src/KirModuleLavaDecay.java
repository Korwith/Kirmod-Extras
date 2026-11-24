package net.minecraft.src;

/**
 * Lava Decay (from LavaDecay): "Fast" makes flowing lava that has lost its source drain like water,
 * instead of lingering (vanilla skips 3 of every 4 ticks while lava dries up). See KirBlockFlowing,
 * which this shares with Infinite Water.
 */
public class KirModuleLavaDecay extends KirModule
{
    private static final String[] VALUES = new String[] {"Vanilla", "Fast"};

    public KirModuleLavaDecay()
    {
        super("lavaDecay", "Lava Decay", "Lava Decay", 0, "lavadecay");
    }

    public String getCategory()
    {
        return CATEGORY_WORLD;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "How fast flowing lava dries up once its source is gone.",
            describeValue("Vanilla", "lava lingers: each step of drying up waits 4 times as long as water's."),
            describeValue("Fast", "lava drains as quickly as water, like the LavaDecay mod."),
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
