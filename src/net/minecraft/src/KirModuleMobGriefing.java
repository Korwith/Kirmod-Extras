package net.minecraft.src;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Mob Griefing (from Friendsss): what creepers, ghasts and endermen may do to the landscape.
 *
 * - "On": vanilla.
 * - "Off": like later versions' mobGriefing gamerule. Creepers and ghast fireballs still explode and hurt
 *   and knock back whatever is near, but break no blocks and light no fires; endermen pick nothing up.
 * - "Harmless": what Friendsss did. Creepers hiss and flash but never go off, ghasts never fire, and
 *   endermen only pick up flowers.
 *
 * Creepers and ghasts are handled on the integrated server's own world tick (KirWorldHook's "regular"
 * section, right before the entities tick, on the server thread). A creeper whose fuse would run out in
 * this tick is blown up there by Kirmod with an explosion whose block list is emptied, and removed; a
 * ghast's fireball is swapped for a KirFireball, whose impact does the same. Endermen read a static
 * table of carriable blocks, which is changed in place. So this works in singleplayer and on a LAN world
 * you host, and never on a remote server.
 */
public class KirModuleMobGriefing extends KirModule
{
    private static final String[] VALUES = new String[] {"On", "Off", "Harmless"};
    private static final int ON = 0;
    private static final int OFF = 1;
    private static final int HARMLESS = 2;

    /** A creeper explodes when timeSinceIgnited reaches this. */
    private static final int FUSE = 30;

    private static final Field fieldTimeSinceIgnited = KirUtil.findField(EntityCreeper.class, "timeSinceIgnited", "d");
    private static final Field fieldCarriable = KirUtil.findField(EntityEnderman.class, "carriableBlocks", "d");

    private boolean[] carriable;
    private boolean[] vanillaCarriable;

    public KirModuleMobGriefing()
    {
        super("mobGriefing", "Mob Griefing", "Mob Griefing", 0, "mobgriefing");
    }

    public String getCategory()
    {
        return CATEGORY_GAMEPLAY;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "Whether creepers, ghasts and endermen can change the landscape.",
            describeValue("On", "vanilla."),
            describeValue("Off", "like later versions' mobGriefing rule: creeper and ghast explosions still hurt you and push things, but break no blocks and start no fires; endermen pick up nothing."),
            describeValue("Harmless", "like the Friendsss mod: creepers flash but never explode, ghasts never shoot, and endermen only pick up flowers."),
            note("Works in singleplayer and on a LAN world you host.")
        };
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public boolean isAvailable()
    {
        return KirWorldHook.isAvailable() && fieldTimeSinceIgnited != null;
    }

    public void install()
    {
        try
        {
            this.carriable = fieldCarriable != null ? (boolean[])fieldCarriable.get((Object)null) : null;
        }
        catch (IllegalAccessException var2)
        {
            this.carriable = null;
        }

        if (this.carriable != null)
        {
            this.vanillaCarriable = (boolean[])this.carriable.clone();
        }

        this.applyEndermen();
    }

    protected void onValueChanged()
    {
        this.applyEndermen();
    }

    public void update(Minecraft mc)
    {
        if (this.getValue() != ON)
        {
            KirWorldHook.installAll(mc);
        }
    }

    /** Changes the endermen's carriable block table in place; Forge and other mods keep the same array. */
    private void applyEndermen()
    {
        if (this.carriable == null)
        {
            return;
        }

        for (int var1 = 0; var1 < this.carriable.length; ++var1)
        {
            boolean var2 = this.vanillaCarriable[var1];

            if (this.getValue() == OFF)
            {
                var2 = false;
            }
            else if (this.getValue() == HARMLESS)
            {
                var2 = var2 && (var1 == Block.plantYellow.blockID || var1 == Block.plantRed.blockID);
            }

            this.carriable[var1] = var2;
        }
    }

    /** Called on the server thread right before a server world ticks its entities (KirWorldHook). */
    public void onServerWorldTick(World world)
    {
        int var2 = this.getValue();

        if (var2 == ON || !(world instanceof WorldServer))
        {
            return;
        }

        List var3 = world.loadedEntityList;
        List var4 = null;

        for (int var5 = 0; var5 < var3.size(); ++var5)
        {
            Entity var6 = (Entity)var3.get(var5);

            if (var6.isDead)
            {
                continue;
            }

            if (var6 instanceof EntityCreeper)
            {
                this.tickCreeper((WorldServer)world, (EntityCreeper)var6, var2);
            }
            else if (var6 instanceof EntityGhast && var2 == HARMLESS)
            {
                EntityGhast var7 = (EntityGhast)var6;

                // The ghast fires when its counter reaches 20; it never gets there.
                if (var7.attackCounter >= 18)
                {
                    var7.attackCounter = -42;
                }
            }
            else if (var6.getClass() == EntityFireball.class && var2 == OFF)
            {
                if (var4 == null)
                {
                    var4 = new ArrayList();
                }

                var4.add(var6);
            }
        }

        if (var4 != null)
        {
            Iterator var8 = var4.iterator();

            while (var8.hasNext())
            {
                swapFireball(world, (EntityFireball)var8.next());
            }
        }
    }

    private void tickCreeper(WorldServer world, EntityCreeper creeper, int value)
    {
        int var4 = KirUtil.getInt(fieldTimeSinceIgnited, creeper, 0);

        if (value == HARMLESS)
        {
            // Keeps hissing and flashing, but this tick's step can't reach the end of the fuse.
            if (var4 > FUSE - 5)
            {
                KirUtil.setInt(fieldTimeSinceIgnited, creeper, FUSE - 5);
            }
        }
        else if (creeper.isEntityAlive() && creeper.getCreeperState() > 0 && var4 + creeper.getCreeperState() >= FUSE)
        {
            // It would explode during this tick: explode it here without the block damage instead.
            explode(world, creeper, creeper.posX, creeper.posY, creeper.posZ, creeper.getPowered() ? 6.0F : 3.0F, false);
            creeper.setDead();
        }
    }

    /** Replaces a ghast's fireball with a KirFireball, which flies the same but breaks no blocks. */
    private static void swapFireball(World world, EntityFireball fireball)
    {
        try
        {
            KirFireball var2 = new KirFireball(world);
            int var3 = var2.entityId;
            KirUtil.copyFields(fireball, var2);
            var2.entityId = var3;
            var2.isDead = false;
            fireball.setDead();
            world.spawnEntityInWorld(var2);
        }
        catch (Exception var4)
        {
            KirUtil.log("Could not replace a ghast fireball", var4);
        }
    }

    /**
     * WorldServer.newExplosion without breaking or burning blocks: entities are still hurt and pushed, and
     * nearby players get the explosion packet (with no blocks in it) for the sound, particles and push.
     */
    static void explode(WorldServer world, Entity exploder, double x, double y, double z, float size, boolean flaming)
    {
        Explosion var9 = new Explosion(world, exploder, x, y, z, size);
        var9.isFlaming = flaming;
        var9.doExplosionA();
        var9.field_77281_g.clear();
        var9.doExplosionB(false);
        Iterator var10 = world.playerEntities.iterator();

        while (var10.hasNext())
        {
            EntityPlayer var11 = (EntityPlayer)var10.next();

            if (var11 instanceof EntityPlayerMP && var11.getDistanceSq(x, y, z) < 4096.0D)
            {
                ((EntityPlayerMP)var11).serverForThisPlayer.sendPacketToPlayer(new Packet60Explosion(x, y, z, size, var9.field_77281_g, (Vec3)var9.func_77277_b().get(var11)));
            }
        }
    }

    /** A ghast fireball whose explosion breaks no blocks (Mob Griefing: Off). */
    public static class KirFireball extends EntityFireball
    {
        public KirFireball(World par1World)
        {
            super(par1World);
        }

        protected void onImpact(MovingObjectPosition par1MovingObjectPosition)
        {
            if (!this.worldObj.isRemote && this.worldObj instanceof WorldServer && mod_KirmodExtras.MOB_GRIEFING.getValue() == OFF)
            {
                if (par1MovingObjectPosition.entityHit != null)
                {
                    par1MovingObjectPosition.entityHit.attackEntityFrom(DamageSource.causeFireballDamage(this, this.shootingEntity), 6);
                }

                explode((WorldServer)this.worldObj, (Entity)null, this.posX, this.posY, this.posZ, 1.0F, true);
                this.setDead();
            }
            else
            {
                super.onImpact(par1MovingObjectPosition);
            }
        }
    }
}
