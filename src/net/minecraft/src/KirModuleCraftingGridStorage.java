package net.minecraft.src;

import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

/**
 * Crafting Grid Storage: "On" makes the inventory's 2x2 crafting grid keep its items when the inventory
 * closes, like four more inventory slots, instead of dropping them. Items are only put there by hand;
 * pickups and shift-clicks never fill it.
 *
 * The host player's (and the client's own) ContainerPlayer is swapped for a KirContainerPlayer. The grid is
 * saved with the host player into level.dat (tag KirmodCraftingGrid, through KirModule.onSaveHostPlayer), read
 * back when the world loads, dropped on death and carried over when leaving the End. Singleplayer and LAN host
 * only; on a remote server the grid stays vanilla. Switching it Off puts the grid's items back in the inventory.
 */
public class KirModuleCraftingGridStorage extends KirModule
{
    private static final String[] VALUES = new String[] {"Off", "On"};
    private static final String TAG = "KirmodCraftingGrid";

    /** The server whose level.dat grid has been read, and the host player last seen on it. */
    private WeakReference loadedServer = new WeakReference((Object)null);
    private WeakReference lastHost = new WeakReference((Object)null);
    private NBTTagList pendingLoad;

    public KirModuleCraftingGridStorage()
    {
        super("craftingGridStorage", "Crafting Grid Storage", "Grid Storage", 0, "craftgridstorage", "craftgrid");
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public String getCategory()
    {
        return CATEGORY_ITEMS;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "Makes the inventory's 2x2 crafting grid keep its items, like four extra slots.",
            describeValue("Off", "vanilla, closing the inventory drops what is in the grid."),
            describeValue("On", "the grid keeps its items (and is saved with the world). Items are never picked up into it; put them there by hand."),
            note("Works in singleplayer and on a LAN world you host. Dropped on death. Switching it Off puts the items back in your inventory.")
        };
    }

    public boolean isAvailable()
    {
        return KirWorldHook.isAvailable();
    }

    /** Kept on while the module is installed: loading, saving and death drops work even while Off. */
    public boolean hasServerPlayerTick()
    {
        return true;
    }

    public void update(Minecraft mc)
    {
        KirWorldHook.installAll(mc);

        if (mc.thePlayer != null && !(mc.currentScreen instanceof GuiContainer))
        {
            swap(mc.thePlayer);
        }
    }

    /** Whether closing player's inventory should keep the grid (on whichever side this runs). */
    public boolean keepsGrid(EntityPlayer player)
    {
        if (!this.isEnabled() || this.getValue() != 1)
        {
            return false;
        }

        if (!player.worldObj.isRemote)
        {
            return KirInteraction.isHost(player);
        }

        Minecraft var2 = ModLoader.getMinecraftInstance();

        if (KirLocalGame.isLocal(var2))
        {
            return true;
        }

        if (!KirInteraction.isOnIntegratedServer(var2))
        {
            return false;
        }

        try
        {
            IntegratedServer var3 = var2.getIntegratedServer();
            EntityPlayerMP var4 = var3.getConfigurationManager().getPlayerForUsername(var3.getServerOwner());
            return var4 != null && var4.inventorySlots instanceof KirContainerPlayer;
        }
        catch (RuntimeException var5)
        {
            return false;
        }
    }

    /** Gives player a KirContainerPlayer with the same grid; returns the new one, or null if it already had one. */
    private static KirContainerPlayer swap(EntityPlayer player)
    {
        if (player.inventorySlots == null || player.inventorySlots.getClass() != ContainerPlayer.class)
        {
            return null;
        }

        ContainerPlayer var1 = (ContainerPlayer)player.inventorySlots;
        KirContainerPlayer var2 = new KirContainerPlayer(player.inventory, var1.isLocalWorld);

        for (int var3 = 0; var3 < 4; ++var3)
        {
            var2.craftMatrix.setInventorySlotContents(var3, var1.craftMatrix.getStackInSlot(var3));
        }

        if (player.craftingInventory == var1)
        {
            player.craftingInventory = var2;
        }

        player.inventorySlots = var2;
        return var2;
    }

    public void onServerPlayerTick(EntityPlayer player)
    {
        MinecraftServer var2 = MinecraftServer.getServer();

        if (this.loadedServer.get() != var2)
        {
            this.loadedServer = new WeakReference(var2);
            this.lastHost = new WeakReference((Object)null);
            NBTTagCompound var3 = player.worldObj.getWorldInfo().getPlayerNBTTagCompound();
            this.pendingLoad = var3 != null && var3.hasKey(TAG) ? var3.getTagList(TAG) : null;
        }

        KirContainerPlayer var4 = swap(player);

        if (var4 != null)
        {
            EntityPlayer var5 = (EntityPlayer)this.lastHost.get();

            if (this.pendingLoad != null)
            {
                for (int var6 = 0; var6 < this.pendingLoad.tagCount(); ++var6)
                {
                    NBTTagCompound var7 = (NBTTagCompound)this.pendingLoad.tagAt(var6);
                    int var8 = var7.getByte("Slot") & 255;

                    if (var8 < 4 && var4.craftMatrix.getStackInSlot(var8) == null)
                    {
                        var4.craftMatrix.setInventorySlotContents(var8, ItemStack.loadItemStackFromNBT(var7));
                    }
                }

                this.pendingLoad = null;
            }
            else if (var5 != null && var5 != player && var5.inventorySlots instanceof KirContainerPlayer)
            {
                // A new player object for the same host: leaving the End (alive) carries the grid over.
                ContainerPlayer var9 = (ContainerPlayer)var5.inventorySlots;

                for (int var10 = 0; var10 < 4; ++var10)
                {
                    ItemStack var11 = var9.craftMatrix.getStackInSlot(var10);

                    if (var11 != null)
                    {
                        var9.craftMatrix.setInventorySlotContents(var10, (ItemStack)null);

                        if (var5.getHealth() > 0)
                        {
                            var4.craftMatrix.setInventorySlotContents(var10, var11);
                        }
                        else
                        {
                            var5.dropPlayerItemWithRandomChoice(var11, true);
                        }
                    }
                }
            }

            if (player instanceof EntityPlayerMP)
            {
                var4.addCraftingToCrafters((EntityPlayerMP)player);
            }
        }

        this.lastHost = new WeakReference(player);

        if (player.getHealth() <= 0 && player.inventorySlots instanceof KirContainerPlayer)
        {
            empty(player, true);
        }
    }

    /** Takes everything out of player's grid: dropped (death), or back into the inventory where it fits. */
    private static void empty(EntityPlayer player, boolean drop)
    {
        ContainerPlayer var2 = (ContainerPlayer)player.inventorySlots;

        for (int var3 = 0; var3 < 4; ++var3)
        {
            ItemStack var4 = var2.craftMatrix.getStackInSlot(var3);

            if (var4 != null)
            {
                var2.craftMatrix.setInventorySlotContents(var3, (ItemStack)null);

                if (drop || !player.inventory.addItemStackToInventory(var4) || var4.stackSize > 0)
                {
                    if (var4.stackSize > 0)
                    {
                        player.dropPlayerItemWithRandomChoice(var4, drop);
                    }
                }
            }
        }
    }

    public void onSaveHostPlayer(EntityPlayer player, NBTTagCompound tag)
    {
        if (!(player.inventorySlots instanceof KirContainerPlayer) || !((KirContainerPlayer)player.inventorySlots).hasItems())
        {
            return;
        }

        ContainerPlayer var3 = (ContainerPlayer)player.inventorySlots;
        NBTTagList var4 = new NBTTagList();

        for (int var5 = 0; var5 < 4; ++var5)
        {
            ItemStack var6 = var3.craftMatrix.getStackInSlot(var5);

            if (var6 != null)
            {
                NBTTagCompound var7 = new NBTTagCompound();
                var7.setByte("Slot", (byte)var5);
                var6.writeToNBT(var7);
                var4.appendTag(var7);
            }
        }

        tag.setTag(TAG, var4);
    }

    protected void onValueChanged()
    {
        if (this.getValue() == 0)
        {
            KirInteraction.runOnServer(new KirInteraction.ServerTask()
            {
                public String run(EntityPlayer host)
                {
                    if (host.inventorySlots instanceof KirContainerPlayer && ((KirContainerPlayer)host.inventorySlots).hasItems())
                    {
                        KirModuleCraftingGridStorage.empty(host, false);
                        return "Crafting Grid Storage: the grid's items are back in your inventory.";
                    }

                    return null;
                }
            });
        }
    }
}
