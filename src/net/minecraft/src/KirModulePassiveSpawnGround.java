package net.minecraft.src;

/**
 * Passive Spawn Ground: what animals may spawn on while Passive Spawning is "Like Hostile". Grass keeps
 * vanilla's rule (the block below must be grass); Any Block lets them spawn on any solid block, in caves
 * too, like hostile mobs. Animals with their own spawn rule (ocelots: grass or leaves at y 63 or above)
 * keep it either way. See KirModulePassiveSpawning.
 */
public class KirModulePassiveSpawnGround extends KirModule
{
    private static final String[] VALUES = new String[] {"Grass", "Any Block"};

    public KirModulePassiveSpawnGround()
    {
        super("passiveSpawnGround", "Passive Spawn Ground", "Spawn Ground", 0, "passiveground", "animalground");
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public String getCategory()
    {
        return mod_KirmodExtras.CATEGORY_MOB_SPAWNING;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "What animals may spawn on while Passive Spawning is Like Hostile.",
            describeValue("Grass", "vanilla: only on grass blocks, at any light level."),
            describeValue("Any Block", "like hostile mobs: on any solid block, caves included."),
            note("Ocelots keep their own rule (grass or leaves, y 63 or higher). Does nothing while Passive Spawning is Vanilla.")
        };
    }

    public boolean isAnyBlock()
    {
        return this.getValue() == 1;
    }
}
