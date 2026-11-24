package net.minecraft.src;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;

/**
 * Beta Mob Wandering: mobs far from every player keep moving, like Beta 1.7.3.
 *
 * In 1.3.2 EntityLiving.entityAge counts up every tick, and despawnEntity() only puts it back to 0 while a
 * player is within 32 blocks. Three places stop moving a mob once it is 100 or older: EntityAIWander (cows,
 * pigs, sheep, chickens, wolves, villagers, zombies, skeletons, creepers...), the old AI's wander in
 * EntityCreature (spiders, endermen, zombie pigmen, silverfish...) and EntitySquid's swimming. So a mob
 * more than 32 blocks away stands still 5 seconds after the player leaves. Beta used the age only for
 * despawning (the same check: older than 600, 1 in 800 per tick, more than 32 blocks away) and never
 * stopped anything from moving.
 *
 * "On": right before a server world ticks its entities, the age of every mob more than 32 blocks from the
 * closest player is moved into a counter of Kirmod's before it reaches 100 (and the mob's own age goes
 * back to 0), so those checks never stop it. Kirmod then does the despawn check vanilla can no longer do
 * with the mob's age plus its counter, so mobs far away despawn as often as before. A mob that comes
 * within 32 blocks or is hurt (both put the age back to 0 in vanilla) loses its counter too.
 * Works on the integrated server only (singleplayer, a LAN world you host).
 */
public class KirModuleBetaMobWandering extends KirModule
{
    private static final String[] VALUES = new String[] {"Off", "On"};

    /** Vanilla's distance (squared) inside which the age is kept at 0: 32 blocks. */
    private static final double NEAR_SQ = 1024.0D;

    /** The age is moved into the counter once it gets here; a tick adds at most 3 (EntityMob in light). */
    private static final int FOLD_AT = 90;

    /** Vanilla's despawn check: older than this, ... */
    private static final int DESPAWN_AGE = 600;

    /** ... with a 1 in this chance each tick. */
    private static final int DESPAWN_CHANCE = 800;

    private static final Field fieldEntityAge = KirUtil.findField(EntityLiving.class, "entityAge", "bq");
    private static final Method methodCanDespawn = KirUtil.findMethod(EntityLiving.class, "canDespawn", "ba");

    /** Each far mob's counter: {age moved out of entityAge, entityAge as Kirmod last left it}. Server thread only. */
    private final Map counters = new WeakHashMap();
    private final Random rand = new Random();

    public KirModuleBetaMobWandering()
    {
        super("betaMobWandering", "Beta Mob Wandering", "Beta Mob Wandering", 0, "mobwandering", "betamobs");
    }

    public String getCategory()
    {
        return CATEGORY_WORLD;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "Whether mobs more than 32 blocks from you keep moving. Since 1.2 they stand still 5 seconds after you walk away; in Beta they kept wandering, swimming and pathing wherever they were.",
            describeValue("Off", "vanilla: far mobs stop moving."),
            describeValue("On", "like Beta 1.7.3: every mob in loaded chunks keeps moving. Far mobs still despawn as often as before."),
            note("Works in singleplayer and on a LAN world you host.")
        };
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public boolean isAvailable()
    {
        return KirWorldHook.isAvailable() && fieldEntityAge != null && methodCanDespawn != null;
    }

    public void update(Minecraft mc)
    {
        if (this.getValue() != 0)
        {
            KirWorldHook.installAll(mc);
        }
    }

    protected void onValueChanged()
    {
        // Ages moved out stay out; the mobs' own ages are low, so they move until vanilla catches up again.
        this.counters.clear();
    }

    /** Called on the server thread right before a server world ticks its entities. */
    public void onServerWorldTick(World world)
    {
        if (this.getValue() == 0 || !(world instanceof WorldServer) || world.playerEntities.isEmpty())
        {
            return;
        }

        List var2 = world.loadedEntityList;

        for (int var3 = 0; var3 < var2.size(); ++var3)
        {
            Object var4 = var2.get(var3);

            // The dragon never despawns (its despawnEntity does nothing) and has no age-based idling.
            if (var4 instanceof EntityLiving && !(var4 instanceof EntityPlayer) && !(var4 instanceof EntityDragon))
            {
                this.tickMob(world, (EntityLiving)var4);
            }
        }
    }

    private void tickMob(World world, EntityLiving mob)
    {
        if (mob.isDead)
        {
            return;
        }

        EntityPlayer var3 = world.getClosestPlayerToEntity(mob, -1.0D);

        if (var3 == null || mob.getDistanceSqToEntity(var3) < NEAR_SQ)
        {
            // Vanilla keeps the age at 0 here itself.
            this.counters.remove(mob);
            return;
        }

        int var4 = KirUtil.getInt(fieldEntityAge, mob, 0);
        int[] var5 = (int[])this.counters.get(mob);

        if (var5 == null)
        {
            var5 = new int[2];
            this.counters.put(mob, var5);
        }
        else if (var4 < var5[1])
        {
            // Hurt since the last tick: vanilla put the age back to 0, so the counter goes too.
            var5[0] = 0;
        }

        if (var4 >= FOLD_AT)
        {
            var5[0] += var4;
            var4 = 0;
            KirUtil.setInt(fieldEntityAge, mob, 0);
        }

        var5[1] = var4;

        if (var5[0] > 0 && var5[0] + var4 > DESPAWN_AGE && this.rand.nextInt(DESPAWN_CHANCE) == 0 && canDespawn(mob))
        {
            mob.setDead();
            this.counters.remove(mob);
        }
    }

    private static boolean canDespawn(EntityLiving mob)
    {
        try
        {
            return ((Boolean)methodCanDespawn.invoke(mob, new Object[0])).booleanValue();
        }
        catch (Exception var2)
        {
            return false;
        }
    }
}
