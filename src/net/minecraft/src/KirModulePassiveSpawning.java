package net.minecraft.src;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * Passive Spawning: whether animals keep spawning around you the way hostile mobs do.
 *
 * Why animals stop spawning in 1.3.2 (squids are fine: they despawn): WorldServer runs the passive half
 * of SpawnerAnimals only once every 400 ticks (20 s), and only while World.countEntities(EntityAnimal)
 * (every animal in every loaded chunk, the always-loaded spawn chunks included) is at most
 * 15 * spawn chunks / 256, about 16 for one player. Animals never despawn, so once the world has that
 * many loaded (world generation alone puts far more in the spawn chunks), no new one ever spawns.
 * EntityAnimal.getCanSpawnHere also wants grass below and a light level above 8. Hostile mobs run every
 * tick, and despawn, so their cap of 70 keeps freeing up.
 *
 * "Like Hostile": right before a server world ticks its entities, Kirmod runs its own passive pass with
 * the hostile algorithm: every tick, a random spot (any height) in each chunk within 8 chunks of a player,
 * groups of up to getMaxSpawnedInChunk(), no closer than 24 blocks to a player or the spawn point. The cap
 * (Passive Mob Cap) counts only the animals in those chunks around the players, so the spawn chunks and
 * places you left don't use it up, and the light level does not matter. The ground is grass, or any
 * solid block with Passive Spawn Ground on Any Block. Animals with a spawn rule of their own (any class
 * that overrides getCanSpawnHere below EntityAnimal, e.g. ocelots) keep it. Vanilla's own 20 s pass still
 * runs as before. Works on the integrated server only (singleplayer, a LAN world you host).
 */
public class KirModulePassiveSpawning extends KirModule
{
    private static final String[] VALUES = new String[] {"Vanilla", "Like Hostile"};

    /** Vanilla's spawn radius around each player, in chunks. */
    private static final int RADIUS = 8;

    /** No spawning within 24 blocks of a player or the world spawn point (squared: 576). */
    private static final double MIN_DISTANCE = 24.0D;

    private static final Field fieldSpawnPeaceful = KirUtil.findField(World.class, "spawnPeacefulMobs", "H");
    private static final Method methodSpecificInit = findSpecificInit();

    /** Per entity class: whether it uses EntityAnimal's own getCanSpawnHere (grass + light > 8). */
    private final Map usesAnimalRule = new HashMap();

    public KirModulePassiveSpawning()
    {
        super("passiveSpawning", "Passive Spawning", "Passive Spawning", 0, "passivespawning", "animalspawning");
    }

    public String getCategory()
    {
        return mod_KirmodExtras.CATEGORY_MOB_SPAWNING;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "How animals spawn. In vanilla they all but stop once the world has been played for a while: their spawning runs only every 20 seconds, counts every animal in every loaded chunk against a cap of about 16, and needs a light level above 8. Animals never despawn, so the cap stays full.",
            describeValue("Vanilla", "the 1.3.2 rules."),
            describeValue("Like Hostile", "animals spawn like hostile mobs: every tick, anywhere within 8 chunks of you (at least 24 blocks away), at any light level, until the animals around you reach the Passive Mob Cap."),
            note("Animals still never despawn. Works in singleplayer and on a LAN world you host.")
        };
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public boolean isAvailable()
    {
        return KirWorldHook.isAvailable();
    }

    public void update(Minecraft mc)
    {
        if (this.getValue() != 0)
        {
            KirWorldHook.installAll(mc);
        }
    }

    /** Called on the server thread right before a server world ticks its entities. */
    public void onServerWorldTick(World world)
    {
        if (this.getValue() == 0 || !(world instanceof WorldServer) || world.playerEntities.isEmpty())
        {
            return;
        }

        if (fieldSpawnPeaceful != null)
        {
            try
            {
                if (!fieldSpawnPeaceful.getBoolean(world))
                {
                    return;
                }
            }
            catch (IllegalAccessException var3)
            {
                ;
            }
        }

        this.spawnAnimals((WorldServer)world);
    }

    private void spawnAnimals(WorldServer world)
    {
        EnumCreatureType var2 = EnumCreatureType.creature;

        // The spawn chunks: true on the edge of a player's 17x17, where nothing spawns (as in vanilla).
        HashMap var3 = new HashMap();

        for (int var4 = 0; var4 < world.playerEntities.size(); ++var4)
        {
            EntityPlayer var5 = (EntityPlayer)world.playerEntities.get(var4);
            int var6 = MathHelper.floor_double(var5.posX / 16.0D);
            int var7 = MathHelper.floor_double(var5.posZ / 16.0D);

            for (int var8 = -RADIUS; var8 <= RADIUS; ++var8)
            {
                for (int var9 = -RADIUS; var9 <= RADIUS; ++var9)
                {
                    boolean var10 = var8 == -RADIUS || var8 == RADIUS || var9 == -RADIUS || var9 == RADIUS;
                    ChunkCoordIntPair var11 = new ChunkCoordIntPair(var8 + var6, var9 + var7);

                    if (!var10)
                    {
                        var3.put(var11, Boolean.FALSE);
                    }
                    else if (!var3.containsKey(var11))
                    {
                        var3.put(var11, Boolean.TRUE);
                    }
                }
            }
        }

        if (countAnimals(world, var2.getCreatureClass(), var3) > var2.getMaxNumberOfCreature() * var3.size() / 256)
        {
            return;
        }

        ChunkCoordinates var12 = world.getSpawnPoint();
        ArrayList var13 = new ArrayList(var3.keySet());
        Collections.shuffle(var13, world.rand);

        for (int var14 = 0; var14 < var13.size(); ++var14)
        {
            ChunkCoordIntPair var15 = (ChunkCoordIntPair)var13.get(var14);

            if (!((Boolean)var3.get(var15)).booleanValue())
            {
                this.spawnInChunk(world, var2, var15, var12);
            }
        }
    }

    /** Animals whose chunk is one of the spawn chunks (vanilla counts every loaded one). */
    private static int countAnimals(World world, Class type, Map chunks)
    {
        int var3 = 0;
        List var4 = world.loadedEntityList;

        for (int var5 = 0; var5 < var4.size(); ++var5)
        {
            Entity var6 = (Entity)var4.get(var5);

            if (!var6.isDead && type.isAssignableFrom(var6.getClass()) && chunks.containsKey(new ChunkCoordIntPair(MathHelper.floor_double(var6.posX) >> 4, MathHelper.floor_double(var6.posZ) >> 4)))
            {
                ++var3;
            }
        }

        return var3;
    }

    /** One chunk of SpawnerAnimals.findChunksForSpawning: up to 3 groups of 4 tries around a random spot. */
    private void spawnInChunk(WorldServer world, EnumCreatureType type, ChunkCoordIntPair chunk, ChunkCoordinates spawn)
    {
        Chunk var5 = world.getChunkFromChunkCoords(chunk.chunkXPos, chunk.chunkZPos);
        int var6 = chunk.chunkXPos * 16 + world.rand.nextInt(16);
        int var7 = chunk.chunkZPos * 16 + world.rand.nextInt(16);
        int var8 = world.rand.nextInt(var5 == null ? world.getActualHeight() : var5.getTopFilledSegment() + 16 - 1);

        if (world.isBlockNormalCube(var6, var8, var7) || world.getBlockMaterial(var6, var8, var7) != type.getCreatureMaterial())
        {
            return;
        }

        int var9 = 0;

        for (int var10 = 0; var10 < 3; ++var10)
        {
            int var11 = var6;
            int var12 = var8;
            int var13 = var7;
            SpawnListEntry var14 = null;

            for (int var15 = 0; var15 < 4; ++var15)
            {
                var11 += world.rand.nextInt(6) - world.rand.nextInt(6);
                var13 += world.rand.nextInt(6) - world.rand.nextInt(6);

                if (!SpawnerAnimals.canCreatureTypeSpawnAtLocation(type, world, var11, var12, var13))
                {
                    continue;
                }

                float var16 = (float)var11 + 0.5F;
                float var17 = (float)var12;
                float var18 = (float)var13 + 0.5F;

                if (world.getClosestPlayer((double)var16, (double)var17, (double)var18, MIN_DISTANCE) != null)
                {
                    continue;
                }

                float var19 = var16 - (float)spawn.posX;
                float var20 = var17 - (float)spawn.posY;
                float var21 = var18 - (float)spawn.posZ;

                if (var19 * var19 + var20 * var20 + var21 * var21 < (float)(MIN_DISTANCE * MIN_DISTANCE))
                {
                    continue;
                }

                if (var14 == null)
                {
                    var14 = world.spawnRandomCreature(type, var11, var12, var13);

                    if (var14 == null)
                    {
                        // As vanilla: nothing for this biome here, try the next spot.
                        continue;
                    }
                }

                EntityLiving var22;

                try
                {
                    var22 = (EntityLiving)var14.entityClass.getConstructor(new Class[] {World.class}).newInstance(new Object[] {world});
                }
                catch (Exception var24)
                {
                    return;
                }

                var22.setLocationAndAngles((double)var16, (double)var17, (double)var18, world.rand.nextFloat() * 360.0F, 0.0F);

                if (this.canSpawnHere(world, var22))
                {
                    ++var9;
                    world.spawnEntityInWorld(var22);
                    creatureSpecificInit(var22, world, var16, var17, var18);

                    if (var9 >= var22.getMaxSpawnedInChunk())
                    {
                        return;
                    }
                }
            }
        }
    }

    /** EntityAnimal's spawn check without the light level (and with any ground on Any Block). */
    private boolean canSpawnHere(World world, EntityLiving mob)
    {
        if (!(mob instanceof EntityAnimal) || !this.usesAnimalRule(mob.getClass()))
        {
            return mob.getCanSpawnHere();
        }

        if (!mod_KirmodExtras.PASSIVE_SPAWN_GROUND.isAnyBlock())
        {
            int var3 = MathHelper.floor_double(mob.posX);
            int var4 = MathHelper.floor_double(mob.boundingBox.minY);
            int var5 = MathHelper.floor_double(mob.posZ);

            if (world.getBlockId(var3, var4 - 1, var5) != Block.grass.blockID)
            {
                return false;
            }
        }

        // EntityLiving.getCanSpawnHere; EntityCreature's path weight is skipped too (off grass it is the light).
        return world.checkIfAABBIsClear(mob.boundingBox) && world.getCollidingBoundingBoxes(mob, mob.boundingBox).isEmpty() && !world.isAnyLiquid(mob.boundingBox);
    }

    /** True if the class's getCanSpawnHere is EntityAnimal's own (cows, pigs, sheep, chickens, wolves...). */
    private boolean usesAnimalRule(Class type)
    {
        Boolean var2 = (Boolean)this.usesAnimalRule.get(type);

        if (var2 == null)
        {
            var2 = Boolean.FALSE;
            String[] var3 = new String[] {"getCanSpawnHere", "bi"};

            for (int var4 = 0; var4 < var3.length; ++var4)
            {
                try
                {
                    var2 = Boolean.valueOf(type.getMethod(var3[var4], new Class[0]).getDeclaringClass() == EntityAnimal.class);
                    break;
                }
                catch (NoSuchMethodException var6)
                {
                    ;
                }
            }

            this.usesAnimalRule.put(type, var2);
        }

        return var2.booleanValue();
    }

    private static Method findSpecificInit()
    {
        String[] var0 = new String[] {"creatureSpecificInit", "a"};

        for (int var1 = 0; var1 < var0.length; ++var1)
        {
            try
            {
                Method var2 = SpawnerAnimals.class.getDeclaredMethod(var0[var1], new Class[] {EntityLiving.class, World.class, Float.TYPE, Float.TYPE, Float.TYPE});
                var2.setAccessible(true);
                return var2;
            }
            catch (Exception var3)
            {
                ;
            }
        }

        return null;
    }

    /** Vanilla's (and with Forge, LivingSpecialSpawnEvent's) per-mob setup: sheep colors, ocelot kittens. */
    private static void creatureSpecificInit(EntityLiving mob, World world, float x, float y, float z)
    {
        if (methodSpecificInit != null)
        {
            try
            {
                methodSpecificInit.invoke((Object)null, new Object[] {mob, world, Float.valueOf(x), Float.valueOf(y), Float.valueOf(z)});
                return;
            }
            catch (Exception var7)
            {
                ;
            }
        }

        if (mob instanceof EntitySheep)
        {
            ((EntitySheep)mob).setFleeceColor(EntitySheep.getRandomFleeceColor(world.rand));
        }
        else if (mob instanceof EntityOcelot && world.rand.nextInt(7) == 0)
        {
            for (int var5 = 0; var5 < 2; ++var5)
            {
                EntityOcelot var6 = new EntityOcelot(world);
                var6.setLocationAndAngles((double)x, (double)y, (double)z, mob.rotationYaw, 0.0F);
                var6.setGrowingAge(-24000);
                world.spawnEntityInWorld(var6);
            }
        }
    }
}
