package net.minecraft.src;

import java.lang.reflect.Field;
import java.util.Random;

/**
 * Fishing Loot (from Fishing++): "Treasure" makes fishing catch more than raw fish, and bites come a
 * bit sooner.
 *
 * Item.fishingRod is swapped for a KirFishingRod (the same way Kirmod swaps blocks), which casts a
 * KirFishHook while Treasure is chosen. The hook picks its catch from Fishing++'s weighted table and
 * gets an extra chance to bite every tick, so a bite takes 400 ticks on average instead of vanilla's 500
 * (240 instead of 300 in the rain). Fishing++ replaced EntityFishHook itself.
 *
 * Hooks are cast by the integrated server, so this works in singleplayer and on a LAN world you host.
 */
public class KirModuleFishing extends KirModule
{
    private static final String[] VALUES = new String[] {"Vanilla", "Treasure"};

    private static final Field fieldTicksCatchable = KirUtil.findField(EntityFishHook.class, "ticksCatchable", "an");
    private static final Field fieldInGround = KirUtil.findField(EntityFishHook.class, "inGround", "h");

    /** Fishing++'s loot table: item, damage, least and most of it, weight, worn (random damage). */
    private static Object[][] loot;
    private static int totalWeight;

    private boolean installed = false;

    public KirModuleFishing()
    {
        super("fishingLoot", "Fishing Loot", "Fishing Loot", 0, "fishingloot", "fishing");
    }

    public String getCategory()
    {
        return CATEGORY_ITEMS;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "What fishing catches, like the Fishing++ mod.",
            describeValue("Vanilla", "only raw fish."),
            describeValue("Treasure", "mostly fish, but also books, saddles, worn bows, fishing rods and leather boots, lily pads, sugar canes, cactus, leather, string, bones, sticks, bowls, bottles, rotten flesh, tripwire hooks and ink sacs. Fish bite a little sooner (every 20 s on average instead of 25)."),
            note("Works in singleplayer and on a LAN world you host.")
        };
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public boolean isAvailable()
    {
        return this.installed && fieldTicksCatchable != null && fieldInGround != null;
    }

    public void install()
    {
        loot = new Object[][] {
            {Item.fishRaw, 0, 1, 1, 60, false},
            {Item.book, 0, 1, 1, 3, false},
            {Block.cactus, 0, 1, 2, 2, false},
            {Item.reed, 0, 1, 3, 3, false},
            {Item.leather, 0, 1, 2, 3, false},
            {Item.saddle, 0, 1, 1, 2, false},
            {Item.bow, 0, 1, 1, 2, true},
            {Item.fishingRod, 0, 1, 1, 2, true},
            {Block.waterlily, 0, 1, 1, 3, false},
            {Item.bootsLeather, 0, 1, 1, 3, true},
            {Item.stick, 0, 1, 2, 3, false},
            {Item.silk, 0, 1, 2, 3, false},
            {Item.bone, 0, 1, 2, 3, false},
            {Item.bowlEmpty, 0, 1, 1, 2, false},
            {Item.glassBottle, 0, 1, 1, 2, false},
            {Item.rottenFlesh, 0, 1, 2, 3, false},
            {Block.tripWireSource, 0, 1, 1, 2, false},
            {Item.dyePowder, 0, 1, 3, 2, false}
        };
        totalWeight = 0;

        for (int var1 = 0; var1 < loot.length; ++var1)
        {
            totalWeight += ((Integer)loot[var1][4]).intValue();
        }

        Item var4 = Item.fishingRod;

        try
        {
            this.installed = KirUtil.replaceItem(var4, new KirFishingRod(KirUtil.constructItemSlot(var4.shiftedIndex - 256)), "fishingRod", "aR");
        }
        catch (IllegalAccessException var3)
        {
            Item.itemsList[var4.shiftedIndex] = var4;
            KirUtil.log("Could not replace the fishing rod", var3);
        }
    }

    /** A random catch from the table. */
    static ItemStack randomLoot(Random rand)
    {
        int var1 = rand.nextInt(totalWeight);

        for (int var2 = 0; var2 < loot.length; ++var2)
        {
            Object[] var3 = loot[var2];
            var1 -= ((Integer)var3[4]).intValue();

            if (var1 < 0)
            {
                Object var4 = var3[0];
                int var5 = ((Integer)var3[2]).intValue();
                int var6 = ((Integer)var3[3]).intValue();
                int var7 = var5 + (var6 > var5 ? rand.nextInt(var6 - var5 + 1) : 0);
                ItemStack var8 = var4 instanceof Block ? new ItemStack((Block)var4, var7, ((Integer)var3[1]).intValue()) : new ItemStack((Item)var4, var7, ((Integer)var3[1]).intValue());

                if (((Boolean)var3[5]).booleanValue() && var8.isItemStackDamageable())
                {
                    int var9 = var8.getMaxDamage();
                    var8.setItemDamage(MathHelper.getRandomIntegerInRange(rand, (int)((float)var9 * 0.1F), (int)((float)var9 * 0.8F)));
                }

                return var8;
            }
        }

        return new ItemStack(Item.fishRaw);
    }

    /** The fishing rod; casts a KirFishHook while Fishing Loot is Treasure. */
    public static class KirFishingRod extends ItemFishingRod
    {
        public KirFishingRod(int par1)
        {
            super(par1);
        }

        public ItemStack onItemRightClick(ItemStack par1ItemStack, World par2World, EntityPlayer par3EntityPlayer)
        {
            if (par3EntityPlayer.fishEntity != null || mod_KirmodExtras.FISHING.getValue() != 1 || !mod_KirmodExtras.FISHING.isAvailable())
            {
                return super.onItemRightClick(par1ItemStack, par2World, par3EntityPlayer);
            }

            par2World.playSoundAtEntity(par3EntityPlayer, "random.bow", 0.5F, 0.4F / (itemRand.nextFloat() * 0.4F + 0.8F));

            if (!par2World.isRemote)
            {
                par2World.spawnEntityInWorld(new KirFishHook(par2World, par3EntityPlayer));
            }

            par3EntityPlayer.swingItem();
            return par1ItemStack;
        }
    }

    /**
     * A fishing hook with Fishing++'s catch. Clients get the usual fish hook spawn packet and draw a
     * vanilla hook; only the server's copy is a KirFishHook.
     */
    public static class KirFishHook extends EntityFishHook
    {
        public KirFishHook(World par1World, EntityPlayer par2EntityPlayer)
        {
            super(par1World, par2EntityPlayer);
        }

        public void onUpdate()
        {
            super.onUpdate();

            if (this.worldObj.isRemote || this.isDead || this.bobber != null || KirUtil.getInt(fieldTicksCatchable, this, 1) > 0 || isInGround(this))
            {
                return;
            }

            if (!this.worldObj.isAABBInMaterial(this.boundingBox, Material.water))
            {
                return;
            }

            // Vanilla bites with a chance of 1/500 a tick (1/300 in rain); one more roll makes it 1/400
            // (1/240), Fishing++'s numbers.
            boolean var1 = this.worldObj.canLightningStrikeAt(MathHelper.floor_double(this.posX), MathHelper.floor_double(this.posY) + 1, MathHelper.floor_double(this.posZ));

            if (this.rand.nextInt(var1 ? 1196 : 1996) == 0)
            {
                KirUtil.setInt(fieldTicksCatchable, this, this.rand.nextInt(30) + 10);
                this.motionY -= 0.20000000298023224D;
                this.worldObj.playSoundAtEntity(this, "random.splash", 0.25F, 1.0F + (this.rand.nextFloat() - this.rand.nextFloat()) * 0.4F);
            }
        }

        public int catchFish()
        {
            if (this.worldObj.isRemote || this.bobber != null || KirUtil.getInt(fieldTicksCatchable, this, 0) <= 0 || this.angler == null)
            {
                return super.catchFish();
            }

            EntityItem var1 = new EntityItem(this.worldObj, this.posX, this.posY, this.posZ, randomLoot(this.rand));
            double var2 = this.angler.posX - this.posX;
            double var4 = this.angler.posY - this.posY;
            double var6 = this.angler.posZ - this.posZ;
            double var8 = (double)MathHelper.sqrt_double(var2 * var2 + var4 * var4 + var6 * var6);
            double var10 = 0.1D;
            var1.motionX = var2 * var10;
            var1.motionY = var4 * var10 + (double)MathHelper.sqrt_double(var8) * 0.08D;
            var1.motionZ = var6 * var10;
            this.worldObj.spawnEntityInWorld(var1);
            this.angler.addStat(StatList.fishCaughtStat, 1);
            int var12 = isInGround(this) ? 2 : 1;
            this.setDead();
            this.angler.fishEntity = null;
            return var12;
        }

        private static boolean isInGround(EntityFishHook hook)
        {
            try
            {
                return fieldInGround != null && fieldInGround.getBoolean(hook);
            }
            catch (IllegalAccessException var2)
            {
                return false;
            }
        }
    }
}
