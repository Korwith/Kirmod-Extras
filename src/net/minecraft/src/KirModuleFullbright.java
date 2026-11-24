package net.minecraft.src;

import java.util.Arrays;
import net.minecraft.client.Minecraft;

/**
 * Fullbright: "On" draws everything as if it were lit at light level 15.
 *
 * The client world's WorldProvider.lightBrightnessTable (how bright each light level looks) is set to
 * full brightness for every level, so the lightmap is white day and night, in caves and in every
 * dimension. The client world has its own provider, so the server's light levels (mob spawning, crop
 * growth) are untouched. The table is put back when the setting is turned off.
 */
public class KirModuleFullbright extends KirModule
{
    private static final String[] VALUES = new String[] {"Off", "On"};

    /** The provider whose table is at full brightness, and its own table. */
    private WorldProvider litProvider;
    private float[] savedTable;

    public KirModuleFullbright()
    {
        super("fullbright", "Fullbright", "Fullbright", 0, "fullbright");
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
            "Removes darkness: every block is drawn as if lit at light level 15, at night, underground and in every dimension.",
            describeValue("Off", "vanilla lighting."),
            describeValue("On", "full brightness everywhere. Mobs still spawn in the dark, as only the way it looks changes."),
            note("The Fullbright key (U, see Controls) switches it on and off.")
        };
    }

    public void update(Minecraft mc)
    {
        WorldProvider var2 = mc.theWorld == null ? null : mc.theWorld.provider;
        boolean var3 = this.getValue() == 1;

        if (this.litProvider != null && (this.litProvider != var2 || !var3))
        {
            System.arraycopy(this.savedTable, 0, this.litProvider.lightBrightnessTable, 0, this.savedTable.length);
            this.litProvider = null;

            if (!var3)
            {
                KirUtil.reloadRenderers();
            }
        }

        if (var3 && var2 != null && this.litProvider == null)
        {
            this.savedTable = (float[])var2.lightBrightnessTable.clone();
            Arrays.fill(var2.lightBrightnessTable, 1.0F);
            this.litProvider = var2;
            // A few blocks bake the light level into their chunk mesh instead of using the lightmap.
            KirUtil.reloadRenderers();
        }
    }
}
