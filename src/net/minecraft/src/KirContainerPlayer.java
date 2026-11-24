package net.minecraft.src;

/**
 * The player's own inventory container (2x2 crafting grid, armour, inventory) for Crafting Grid Storage:
 * closing the inventory keeps what is in the grid, and the crafting result, instead of dropping it, while
 * KirModuleCraftingGridStorage.keepsGrid says so. Otherwise it is vanilla's ContainerPlayer.
 */
public class KirContainerPlayer extends ContainerPlayer
{
    public KirContainerPlayer(InventoryPlayer par1InventoryPlayer, boolean par2)
    {
        super(par1InventoryPlayer, par2);
    }

    public void onCraftGuiClosed(EntityPlayer par1EntityPlayer)
    {
        if (!mod_KirmodExtras.CRAFTING_GRID_STORAGE.keepsGrid(par1EntityPlayer))
        {
            super.onCraftGuiClosed(par1EntityPlayer);
            return;
        }

        // Container.onCraftGuiClosed: the stack held on the cursor is still dropped.
        InventoryPlayer var2 = par1EntityPlayer.inventory;

        if (var2.getItemStack() != null)
        {
            par1EntityPlayer.dropPlayerItem(var2.getItemStack());
            var2.setItemStack((ItemStack)null);
        }
    }

    /** Whether any of the four grid slots holds something. */
    public boolean hasItems()
    {
        for (int var1 = 0; var1 < 4; ++var1)
        {
            if (this.craftMatrix.getStackInSlot(var1) != null)
            {
                return true;
            }
        }

        return false;
    }
}
