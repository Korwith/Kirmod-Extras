package net.minecraft.src;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;

/**
 * Passive Mob Cap: a slider from 5 to 200 (vanilla 15), how many animals may be around the players
 * before passive spawning stops, the same way the hostile cap (70) works.
 *
 * Vanilla's rule is "animals counted <= cap * spawn chunks / 256"; with one player there are 17x17 = 289
 * spawn chunks, so the cap is about the number of animals. This module writes the value into
 * EnumCreatureType.creature itself (its private final maxNumberOfCreature, obf "e"), so vanilla's own
 * passive spawning (and Forge's copy of it) uses it too, and Passive Spawning's "Like Hostile" pass reads
 * it from there. Works on the integrated server only (singleplayer, a LAN world you host).
 */
public class KirModulePassiveMobCap extends KirModule
{
    public static final int STEP = 5;
    public static final int MIN_CAP = 5;
    public static final int MAX_CAP = 200;
    public static final int VANILLA_CAP = 15;

    /** "5" to "200" in steps of 5: value i is a cap of (i + 1) * 5. */
    private static final String[] VALUES = new String[MAX_CAP / STEP];

    static
    {
        for (int var0 = 0; var0 < VALUES.length; ++var0)
        {
            VALUES[var0] = String.valueOf((var0 + 1) * STEP);
        }
    }

    private static final Field fieldMaxCreatures = KirUtil.findField(EnumCreatureType.class, "maxNumberOfCreature", "e");

    public KirModulePassiveMobCap()
    {
        super("passiveMobCap", "Passive Mob Cap", "Passive Cap", VANILLA_CAP / STEP - 1, "passivecap", "animalcap");
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
        return mod_KirmodExtras.CATEGORY_MOB_SPAWNING;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "How many animals (cows, pigs, sheep, chickens, wolves, ocelots) may be around you before new ones stop spawning. Hostile mobs have a cap of 70.",
            describeValue("15", "vanilla."),
            describeValue(MIN_CAP + " to " + MAX_CAP, "that many animals in the 17x17 chunks around you."),
            note("With Passive Spawning on Vanilla, every loaded animal counts (the spawn chunks are full of them), so a higher cap helps far less than with Like Hostile."),
            note("Works in singleplayer and on a LAN world you host.")
        };
    }

    public boolean isAvailable()
    {
        return fieldMaxCreatures != null;
    }

    /** Accepts any cap, rounded to a step of 5 and clamped to 5 to 200, as well as the value names. */
    public int findValue(String name)
    {
        try
        {
            int var2 = Integer.parseInt(name.trim());
            var2 = Math.max(MIN_CAP, Math.min(var2, MAX_CAP));
            return (var2 + STEP / 2) / STEP - 1;
        }
        catch (NumberFormatException var3)
        {
            return super.findValue(name);
        }
    }

    public void install()
    {
        this.apply();
    }

    protected void onValueChanged()
    {
        this.apply();
    }

    public void update(Minecraft mc)
    {
        // Cheap, and puts the value back if something reset the enum.
        if (KirUtil.getInt(fieldMaxCreatures, EnumCreatureType.creature, this.getCap()) != this.getCap())
        {
            this.apply();
        }
    }

    private void apply()
    {
        KirUtil.setInt(fieldMaxCreatures, EnumCreatureType.creature, this.getCap());
    }

    /** The cap: 5 to 200, vanilla 15. */
    public int getCap()
    {
        return (this.getValue() + 1) * STEP;
    }
}
