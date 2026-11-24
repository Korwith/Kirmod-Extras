package net.minecraft.src;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;

/**
 * Minecart Speed: "On" lets minecarts on rails go faster than vanilla, up to Minecart Speed Limit
 * (KirModuleMinecartSpeedLimit). "Off" (the default) leaves minecarts completely vanilla.
 *
 * Vanilla moves a cart on rails at most 0.4 blocks a tick and follows the track one rail block per
 * tick, so a plain higher cap would make carts skip curves and slope steps. Instead, right before the
 * world ticks its entities, a cart going faster than vanilla allows is moved ahead along the track by
 * the extra distance, in steps of at most 0.4 blocks. Each step does what vanilla does with a tick's
 * move: puts the cart on the rail's line, turns it along curves, steps it up and down slopes, speeds it
 * up or slows it down by the height it gained, and triggers detector rails. The cart's own tick then
 * moves it the last 0.4 blocks as usual, with all the once-a-tick physics (friction, booster and brake
 * rails, the slope pull, furnace carts, cart collisions), so a cart within the vanilla limit behaves
 * exactly as in vanilla. Carts stop moving ahead at a brake (unpowered powered rail), the end of the
 * track, a wall or another cart, and leave those to vanilla.
 *
 * The limit is the cart's real top speed: anything that moved the cart since the world's last update
 * counts towards it. That matters for a cart with a player in it, which the server does not update with
 * the world at all but once for each movement packet the player sends. Like vanilla, a ridden cart moves
 * 3/4 of its speed. Off the rails the vanilla limit still applies.
 * It runs on the integrated server only (for every cart in the world); on a remote server carts move
 * as the server decides.
 */
public class KirModuleMinecartSpeed extends KirModule
{
    private static final String[] VALUES = new String[] {"Off", "On"};

    /** Vanilla's longest move on rails per tick, along each axis; also the longest step moving ahead. */
    private static final double STEP = 0.4D;

    /** The world only ticks entities with loaded chunks this far around them; moving ahead does the same. */
    private static final int CHUNK_CHECK_RANGE = 32;

    /**
     * The two ends of each rail shape (by metadata), relative to the rail block: x, y (1 for the raised
     * end of a slope) and z. A copy of EntityMinecart's private table.
     */
    private static final int[][][] RAIL_ENDS = new int[][][] {{{0, 0, -1}, {0, 0, 1}}, {{ -1, 0, 0}, {1, 0, 0}}, {{ -1, -1, 0}, {1, 0, 0}}, {{ -1, 0, 0}, {1, -1, 0}}, {{0, 0, -1}, {0, -1, 1}}, {{0, -1, -1}, {0, 0, 1}}, {{0, 0, 1}, {1, 0, 0}}, {{0, 0, 1}, { -1, 0, 0}}, {{0, 0, -1}, { -1, 0, 0}}, {{0, 0, -1}, {1, 0, 0}}};

    /** Where each cart was when the world's last entity update ended; server thread only. */
    private final Map endOfTick = new WeakHashMap();

    public KirModuleMinecartSpeed()
    {
        super("minecartSpeed", "Minecart Speed", "Minecart Speed", 0, "cartspeed", "minecartspeed");
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public String getCategory()
    {
        return mod_KirmodExtras.CATEGORY_MINECARTS;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "Lets minecarts on rails go faster than vanilla, up to Minecart Speed Limit. Fast carts still follow curves and slopes and trigger detector rails.",
            describeValue("Off", "vanilla minecarts."),
            describeValue("On", "carts can go up to Minecart Speed Limit."),
            note("Works in singleplayer and on a LAN world you host.")
        };
    }

    public boolean isAvailable()
    {
        return KirWorldHook.isAvailable();
    }

    /** The limit in blocks per tick; vanilla's while off. */
    public double getLimitPerTick()
    {
        return this.isEnabled() && this.getValue() == 1 ? (double)mod_KirmodExtras.MINECART_SPEED_LIMIT.getLimit() / 20.0D : STEP;
    }

    public void update(Minecraft mc)
    {
        KirWorldHook.installAll(mc);
    }

    /** Moves fast carts ahead. Called on the server thread, every tick, right before entities tick. */
    public void onServerWorldTick(World world)
    {
        this.moveMinecarts(world);
    }

    public void onServerWorldTickEnd(World world)
    {
        this.recordMinecarts(world);
    }

    void moveMinecarts(World world)
    {
        double var2 = this.getLimitPerTick();

        if (var2 <= STEP)
        {
            return;
        }

        List var4 = world.loadedEntityList;

        for (int var5 = 0; var5 < var4.size(); ++var5)
        {
            Object var6 = var4.get(var5);

            if (var6 instanceof EntityMinecart)
            {
                this.moveAhead((EntityMinecart)var6, var2);
            }
        }
    }

    /** Notes where the carts are. Called on the server thread, every tick, right after entities tick. */
    void recordMinecarts(World world)
    {
        if (this.getLimitPerTick() <= STEP)
        {
            this.endOfTick.clear();
            return;
        }

        List var2 = world.loadedEntityList;

        for (int var3 = 0; var3 < var2.size(); ++var3)
        {
            Object var4 = var2.get(var3);

            if (var4 instanceof EntityMinecart)
            {
                EntityMinecart var5 = (EntityMinecart)var4;
                this.endOfTick.put(var5, new double[] {var5.posX, var5.posZ});
            }
        }
    }

    /** Moves cart along the track by however far it goes this tick beyond the vanilla tick's own move. */
    private void moveAhead(EntityMinecart cart, double limit)
    {
        if (cart.isDead || cart.ridingEntity != null)
        {
            return;
        }

        int var4 = MathHelper.floor_double(cart.posX);
        int var5 = MathHelper.floor_double(cart.posZ);

        if (!cart.worldObj.checkChunksExist(var4 - CHUNK_CHECK_RANGE, 0, var5 - CHUNK_CHECK_RANGE, var4 + CHUNK_CHECK_RANGE, 0, var5 + CHUNK_CHECK_RANGE))
        {
            return;
        }

        // The distance vanilla would move this tick without its limit (3/4 of the speed when ridden),
        // less the world update's own move and what the cart moved since the last one. The world does
        // not update a cart with a player in it; the player's movement packets do (see WorldServer).
        double var6 = Math.sqrt(cart.motionX * cart.motionX + cart.motionZ * cart.motionZ) * (cart.riddenByEntity != null ? 0.75D : 1.0D);
        double var8 = Math.min(var6, limit) - (cart.riddenByEntity instanceof EntityPlayer ? 0.0D : STEP);
        double[] var10 = (double[])this.endOfTick.get(cart);

        if (var10 != null)
        {
            double var11 = cart.posX - var10[0];
            double var13 = cart.posZ - var10[1];
            var8 -= Math.sqrt(var11 * var11 + var13 * var13);
        }

        for (; var8 > 1.0E-4D; var8 -= STEP)
        {
            if (!this.step(cart, Math.min(var8, STEP)))
            {
                break;
            }
        }
    }

    /**
     * One step of at most STEP blocks along the rail under the cart, the way EntityMinecart.onUpdate
     * moves a cart on rails. Returns false (having done nothing, or having been stopped) where the cart
     * should go no further this tick.
     */
    private boolean step(EntityMinecart cart, double distance)
    {
        World var4 = cart.worldObj;
        int var5 = MathHelper.floor_double(cart.posX);
        int var6 = MathHelper.floor_double(cart.posY);
        int var7 = MathHelper.floor_double(cart.posZ);

        if (BlockRail.isRailBlockAt(var4, var5, var6 - 1, var7))
        {
            --var6;
        }

        int var8 = var4.getBlockId(var5, var6, var7);

        if (!BlockRail.isRailBlock(var8))
        {
            return false;
        }

        int var9 = var4.getBlockMetadata(var5, var6, var7);

        if (((BlockRail)Block.blocksList[var8]).isPowered())
        {
            // A brake: vanilla slows the cart down on it, once per tick.
            if (var8 == Block.railPowered.blockID && (var9 & 8) == 0)
            {
                return false;
            }

            var9 &= 7;
        }

        if (var9 >= RAIL_ENDS.length)
        {
            return false;
        }

        double var10 = Math.sqrt(cart.motionX * cart.motionX + cart.motionZ * cart.motionZ);

        if (var10 < 1.0E-4D)
        {
            return false;
        }

        Vec3 var12 = cart.func_70489_a(cart.posX, cart.posY, cart.posZ);
        int[][] var13 = RAIL_ENDS[var9];

        // Point the motion along the rail, whichever way the cart is going.
        double var14 = (double)(var13[1][0] - var13[0][0]);
        double var16 = (double)(var13[1][2] - var13[0][2]);
        double var18 = Math.sqrt(var14 * var14 + var16 * var16);

        if (cart.motionX * var14 + cart.motionZ * var16 < 0.0D)
        {
            var14 = -var14;
            var16 = -var16;
        }

        cart.motionX = var10 * var14 / var18;
        cart.motionZ = var10 * var16 / var18;

        // Put the cart on the rail's line, at the height of its lower end.
        double var20 = (double)var5 + 0.5D + (double)var13[0][0] * 0.5D;
        double var22 = (double)var7 + 0.5D + (double)var13[0][2] * 0.5D;
        double var24 = (double)var5 + 0.5D + (double)var13[1][0] * 0.5D - var20;
        double var26 = (double)var7 + 0.5D + (double)var13[1][2] * 0.5D - var22;
        double var28;

        if (var24 == 0.0D)
        {
            var28 = cart.posZ - (double)var7;
        }
        else if (var26 == 0.0D)
        {
            var28 = cart.posX - (double)var5;
        }
        else
        {
            var28 = ((cart.posX - var20) * var24 + (cart.posZ - var22) * var26) * 2.0D;
        }

        double var30 = var9 >= 2 && var9 <= 5 ? (double)(var6 + 1) : (double)var6;
        cart.setPosition(var20 + var24 * var28, var30 + (double)cart.yOffset, var22 + var26 * var28);
        double var32 = cart.posX;
        double var34 = cart.posZ;
        cart.moveEntity(cart.motionX / var10 * distance, 0.0D, cart.motionZ / var10 * distance);

        // Up a slope: onto the next block's rail.
        if (var13[0][1] != 0 && MathHelper.floor_double(cart.posX) - var5 == var13[0][0] && MathHelper.floor_double(cart.posZ) - var7 == var13[0][2])
        {
            cart.setPosition(cart.posX, cart.posY + (double)var13[0][1], cart.posZ);
        }
        else if (var13[1][1] != 0 && MathHelper.floor_double(cart.posX) - var5 == var13[1][0] && MathHelper.floor_double(cart.posZ) - var7 == var13[1][2])
        {
            cart.setPosition(cart.posX, cart.posY + (double)var13[1][1], cart.posZ);
        }

        // Faster for each block down, slower for each block up, as in vanilla.
        Vec3 var36 = cart.func_70489_a(cart.posX, cart.posY, cart.posZ);

        if (var36 != null && var12 != null)
        {
            double var37 = (var12.yCoord - var36.yCoord) * 0.05D;
            var10 = Math.sqrt(cart.motionX * cart.motionX + cart.motionZ * cart.motionZ);

            if (var10 > 0.0D)
            {
                cart.motionX = cart.motionX / var10 * (var10 + var37);
                cart.motionZ = cart.motionZ / var10 * (var10 + var37);
            }

            cart.setPosition(cart.posX, var36.yCoord, cart.posZ);
        }

        // Into the next block: go the way it leads (this is what takes curves).
        int var39 = MathHelper.floor_double(cart.posX);
        int var40 = MathHelper.floor_double(cart.posZ);

        if (var39 != var5 || var40 != var7)
        {
            var10 = Math.sqrt(cart.motionX * cart.motionX + cart.motionZ * cart.motionZ);
            cart.motionX = var10 * (double)(var39 - var5);
            cart.motionZ = var10 * (double)(var40 - var7);
        }

        // Detector rails, pressure plates and the like.
        cart.doBlockCollisions();

        // The rider goes along, as in every vanilla update; left behind, it would be in the cart's way.
        if (cart.riddenByEntity != null)
        {
            cart.updateRiderPosition();
        }

        double var41 = cart.posX - var32;
        double var43 = cart.posZ - var34;

        // Blocked by a wall: the vanilla tick takes it from here.
        if (var41 * var41 + var43 * var43 < distance * distance * 0.25D)
        {
            return false;
        }

        return !this.touchesOtherCart(cart);
    }

    /** Whether another cart is where vanilla would bump into it, so the vanilla tick can do the collision. */
    private boolean touchesOtherCart(EntityMinecart cart)
    {
        List var2 = cart.worldObj.getEntitiesWithinAABBExcludingEntity(cart, cart.boundingBox.expand(0.20000000298023224D, 0.0D, 0.20000000298023224D));

        for (int var3 = 0; var3 < var2.size(); ++var3)
        {
            if (var2.get(var3) instanceof EntityMinecart)
            {
                return true;
            }
        }

        return false;
    }
}
