package net.minecraft.src;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/**
 * Auto Trader (from AutoTrader): faster villager trading.
 *
 * - The trading screen gets a panel next to it: how many times to make the selected trade (-, +, Max,
 *   which is as many as the items in your inventory pay for), and a Trade button. The amount is
 *   remembered per villager and per trade until the game closes.
 * - Holding the Auto Trade key (N by default; AutoTrader's V is Block Gradient's) in the trading screen
 *   trades over and over, slowly at first and faster the longer it is held.
 *
 * A trade is done the way a player would do it, with window clicks: the price is moved from the
 * inventory into the two input slots (topping them up from as many stacks as needed), the result is
 * picked up and put into the inventory. So it works on servers too. It stops when the inventory can't
 * pay for another trade or has no room for the result.
 *
 * GuiMerchant is swapped for GuiKirMerchant (keeping its container, so the server's window stays the
 * same) the first time it is seen, like Kirmod's other screen swaps.
 */
public class KirModuleAutoTrader extends KirModule
{
    private static final String[] VALUES = new String[] {"Off", "On"};

    public static final KeyBinding KEY = new KeyBinding("Auto Trade", Keyboard.KEY_N);

    private static final Field fieldRecipeIndex = KirUtil.findField(GuiMerchant.class, "currentRecipeIndex", "r");

    /** Container slots of ContainerMerchant: the two inputs, the result, then the player's 36. */
    private static final int RESULT = 2;
    private static final int FIRST_INVENTORY = 3;
    private static final int END_INVENTORY = 39;

    /** Time between trades: from START_MS, easing down to MIN_MS over RAMP_MS. */
    private static final long START_MS = 400L;
    private static final long MIN_MS = 50L;
    private static final long RAMP_MS = 3000L;

    /** Amount chosen per villager (entity ID) and trade: "ID:INDEX" -> Integer. */
    private static final Map amounts = new HashMap();

    /** Trades still to make for the Trade button, and the trade they are for. */
    static int queued = 0;
    static int done = 0;
    private static int queuedRecipe = -1;
    private static long runStart;
    private static long nextTrade;
    private static boolean keyWasDown = false;
    static String status = null;

    public KirModuleAutoTrader()
    {
        super("autoTrader", "Auto Trader", "Auto Trader", 0, "autotrader");
    }

    public String getCategory()
    {
        return CATEGORY_ITEMS;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "Faster villager trading, like the AutoTrader mod.",
            describeValue("Off", "vanilla trading screen."),
            describeValue("On", "the trading screen gets a panel to make the selected trade several times at once (up to all the items you carry pay for), and holding the Auto Trade key (N, see Controls) trades over and over, faster the longer you hold it."),
            note("Works on servers too: it only clicks for you.")
        };
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public void update(Minecraft mc)
    {
        if (this.getValue() != 1)
        {
            queued = 0;
            return;
        }

        GuiScreen var2 = mc.currentScreen;

        if (var2 != null && var2.getClass() == GuiMerchant.class)
        {
            GuiKirMerchant.replace(mc, (GuiMerchant)var2);
            return;
        }

        if (!(var2 instanceof GuiKirMerchant) || mc.thePlayer == null)
        {
            queued = 0;
            keyWasDown = false;
            return;
        }

        GuiKirMerchant var3 = (GuiKirMerchant)var2;
        boolean var4 = KEY.keyCode > 0 && Keyboard.isKeyDown(KEY.keyCode);
        long var5 = System.currentTimeMillis();

        if (var4 && !keyWasDown)
        {
            // The key starts an open-ended run, which the key's release ends.
            startRun(var3, Integer.MAX_VALUE);
        }
        else if (!var4 && keyWasDown && queued == Integer.MAX_VALUE)
        {
            stop(null);
        }

        keyWasDown = var4;

        if (queued > 0 && var5 >= nextTrade)
        {
            if (var3.getRecipeIndex() != queuedRecipe)
            {
                stop("Stopped: a different trade was selected.");
                return;
            }

            String var7 = trade(mc, var3);

            if (var7 != null)
            {
                stop(done > 0 ? "Made " + done + " trade" + (done == 1 ? "" : "s") + ". " + var7 : var7);
                return;
            }

            ++done;

            if (queued != Integer.MAX_VALUE && --queued == 0)
            {
                stop("Made " + done + " trade" + (done == 1 ? "." : "s."));
                return;
            }

            nextTrade = var5 + interval(var5 - runStart);
        }
    }

    /** Ease-in: slow while the run is young, then quickly down to MIN_MS. */
    private static long interval(long elapsed)
    {
        double var2 = Math.min(1.0D, (double)elapsed / (double)RAMP_MS);
        double var4 = var2 * var2 * (3.0D - 2.0D * var2);
        return START_MS - (long)((double)(START_MS - MIN_MS) * var4);
    }

    static void startRun(GuiKirMerchant gui, int trades)
    {
        queued = trades;
        done = 0;
        queuedRecipe = gui.getRecipeIndex();
        runStart = System.currentTimeMillis();
        nextTrade = runStart;
        status = null;
    }

    static void stop(String message)
    {
        queued = 0;

        if (message != null)
        {
            status = message;
        }
    }

    static int getAmount(int villager, int recipe)
    {
        Integer var2 = (Integer)amounts.get(villager + ":" + recipe);
        return var2 != null ? var2.intValue() : 1;
    }

    static void setAmount(int villager, int recipe, int amount)
    {
        amounts.put(villager + ":" + recipe, Integer.valueOf(amount));
    }

    /* ------------------------------------------------------------------ One trade */

    private static boolean same(ItemStack a, ItemStack b)
    {
        return a != null && b != null && a.itemID == b.itemID && a.getItemDamage() == b.getItemDamage();
    }

    private static void click(Minecraft mc, Container container, int slot, boolean shift)
    {
        mc.playerController.windowClick(container.windowId, slot, 0, shift, mc.thePlayer);
    }

    /** How many of an item the inventory part of the container holds. */
    private static int countInInventory(Container container, ItemStack item)
    {
        int var2 = 0;

        for (int var3 = FIRST_INVENTORY; var3 < END_INVENTORY; ++var3)
        {
            ItemStack var4 = container.getSlot(var3).getStack();

            if (same(var4, item))
            {
                var2 += var4.stackSize;
            }
        }

        return var2;
    }

    /** How many times the inventory (and what is already in the input slots) can pay for a trade. */
    static int affordable(Container container, MerchantRecipe recipe)
    {
        ItemStack var2 = recipe.getItemToBuy();
        ItemStack var3 = recipe.hasSecondItemToBuy() ? recipe.getSecondItemToBuy() : null;
        int[] var4 = new int[2];

        for (int var5 = 0; var5 < 2; ++var5)
        {
            ItemStack var6 = container.getSlot(var5).getStack();
            var4[var5] = var6 == null ? 0 : var6.stackSize;
        }

        if (var3 != null && same(var2, var3))
        {
            int var7 = countInInventory(container, var2) + (same(container.getSlot(0).getStack(), var2) ? var4[0] : 0) + (same(container.getSlot(1).getStack(), var2) ? var4[1] : 0);
            return var7 / (var2.stackSize + var3.stackSize);
        }

        int var8 = (countInInventory(container, var2) + (same(container.getSlot(0).getStack(), var2) ? var4[0] : 0)) / var2.stackSize;

        if (var3 != null)
        {
            var8 = Math.min(var8, (countInInventory(container, var3) + (same(container.getSlot(1).getStack(), var3) ? var4[1] : 0)) / var3.stackSize);
        }

        return var8;
    }

    /** Makes the selected trade once. Returns null, or why it couldn't. */
    private static String trade(Minecraft mc, GuiKirMerchant gui)
    {
        Container var2 = gui.inventorySlots;
        MerchantRecipe var3 = gui.getRecipe();

        if (var3 == null)
        {
            return "No trade selected.";
        }

        if (mc.thePlayer.inventory.getItemStack() != null)
        {
            return "Put down the item on your cursor first.";
        }

        if (!fill(mc, var2, var3.getItemToBuy(), 0))
        {
            return "Not enough " + var3.getItemToBuy().getItem().getItemDisplayName(var3.getItemToBuy()) + ".";
        }

        if (var3.hasSecondItemToBuy() && !fill(mc, var2, var3.getSecondItemToBuy(), 1))
        {
            return "Not enough " + var3.getSecondItemToBuy().getItem().getItemDisplayName(var3.getSecondItemToBuy()) + ".";
        }

        ItemStack var4 = var2.getSlot(RESULT).getStack();

        if (var4 == null)
        {
            return "The villager won't make this trade right now.";
        }

        if (!hasRoom(var2, var4))
        {
            return "Your inventory is full.";
        }

        click(mc, var2, RESULT, false);

        // Put the result from the cursor into the inventory: onto stacks of it first, then empty slots.
        for (int var5 = 0; var5 < 2 && mc.thePlayer.inventory.getItemStack() != null; ++var5)
        {
            for (int var6 = FIRST_INVENTORY; var6 < END_INVENTORY && mc.thePlayer.inventory.getItemStack() != null; ++var6)
            {
                ItemStack var7 = var2.getSlot(var6).getStack();
                ItemStack var8 = mc.thePlayer.inventory.getItemStack();

                if (var5 == 0 ? same(var7, var8) && ItemStack.func_77970_a(var7, var8) && var7.stackSize < var7.getMaxStackSize() : var7 == null)
                {
                    click(mc, var2, var6, false);
                }
            }
        }

        return mc.thePlayer.inventory.getItemStack() != null ? "Couldn't put the result away." : null;
    }

    /** Whether the inventory has room for the whole result. */
    private static boolean hasRoom(Container container, ItemStack result)
    {
        int var2 = 0;

        for (int var3 = FIRST_INVENTORY; var3 < END_INVENTORY && var2 < result.stackSize; ++var3)
        {
            ItemStack var4 = container.getSlot(var3).getStack();

            if (var4 == null)
            {
                var2 += result.getMaxStackSize();
            }
            else if (same(var4, result) && ItemStack.func_77970_a(var4, result))
            {
                var2 += var4.getMaxStackSize() - var4.stackSize;
            }
        }

        return var2 >= result.stackSize;
    }

    /** Makes an input slot hold at least the price, from the inventory. False if it can't. */
    private static boolean fill(Minecraft mc, Container container, ItemStack price, int slot)
    {
        ItemStack var4 = container.getSlot(slot).getStack();

        if (var4 != null && !same(var4, price))
        {
            // Something else is in the slot: move it out first.
            click(mc, container, slot, true);

            if (container.getSlot(slot).getHasStack())
            {
                return false;
            }
        }

        for (int var5 = 0; var5 < 40; ++var5)
        {
            var4 = container.getSlot(slot).getStack();

            if (var4 != null && var4.stackSize >= price.stackSize)
            {
                return true;
            }

            int var6 = -1;

            for (int var7 = FIRST_INVENTORY; var7 < END_INVENTORY && var6 < 0; ++var7)
            {
                if (same(container.getSlot(var7).getStack(), price))
                {
                    var6 = var7;
                }
            }

            if (var6 < 0)
            {
                return false;
            }

            click(mc, container, var6, false);
            click(mc, container, slot, false);

            if (mc.thePlayer.inventory.getItemStack() != null)
            {
                // The slot is full: the rest goes back where it came from.
                click(mc, container, var6, false);
            }
        }

        return false;
    }

    /* ------------------------------------------------------------------ The screen */

    /** The trading screen with Auto Trader's panel. */
    public static class GuiKirMerchant extends GuiMerchant
    {
        private static final int MINUS = 100;
        private static final int PLUS = 101;
        private static final int MAX = 102;
        private static final int TRADE = 103;

        private final IMerchant merchant;
        private final int villager;
        private GuiButton tradeButton;
        private int panelX;
        private int panelY;

        public GuiKirMerchant(InventoryPlayer inventory, IMerchant merchant, World world, int villager)
        {
            super(inventory, merchant, world);
            this.merchant = merchant;
            this.villager = villager;
        }

        /** Swaps an open vanilla trading screen for this one, keeping its container (and window ID). */
        static void replace(Minecraft mc, GuiMerchant screen)
        {
            int var2 = -1;

            if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit instanceof EntityVillager)
            {
                var2 = mc.objectMouseOver.entityHit.entityId;
            }

            GuiKirMerchant var3 = new GuiKirMerchant(mc.thePlayer.inventory, screen.func_74199_h(), mc.theWorld, var2);
            var3.inventorySlots = screen.inventorySlots;
            // Not displayGuiScreen: that would close the old screen's container, which is still in use.
            mc.currentScreen = var3;
            ScaledResolution var4 = new ScaledResolution(mc.gameSettings, mc.displayWidth, mc.displayHeight);
            var3.setWorldAndResolution(mc, var4.getScaledWidth(), var4.getScaledHeight());
            queued = 0;
            status = null;
        }

        int getRecipeIndex()
        {
            return KirUtil.getInt(fieldRecipeIndex, this, 0);
        }

        MerchantRecipe getRecipe()
        {
            MerchantRecipeList var1 = this.merchant.getRecipes(this.mc.thePlayer);
            int var2 = this.getRecipeIndex();
            return var1 != null && var2 >= 0 && var2 < var1.size() ? (MerchantRecipe)var1.get(var2) : null;
        }

        private int getAmount()
        {
            return KirModuleAutoTrader.getAmount(this.villager, this.getRecipeIndex());
        }

        public void initGui()
        {
            super.initGui();
            int var1 = (this.width - this.xSize) / 2;
            int var2 = (this.height - this.ySize) / 2;
            // Right of the screen if it fits, else left of it.
            this.panelX = var1 + this.xSize + 64 + 4 <= this.width ? var1 + this.xSize + 4 : Math.max(2, var1 - 68);
            this.panelY = var2 + 4;
            this.controlList.add(new GuiButton(MINUS, this.panelX, this.panelY + 12, 20, 20, "-"));
            this.controlList.add(new GuiButton(PLUS, this.panelX + 44, this.panelY + 12, 20, 20, "+"));
            this.controlList.add(new GuiButton(MAX, this.panelX, this.panelY + 36, 64, 20, "Max"));
            this.controlList.add(this.tradeButton = new GuiButton(TRADE, this.panelX, this.panelY + 60, 64, 20, "Trade"));
        }

        protected void actionPerformed(GuiButton par1GuiButton)
        {
            if (par1GuiButton.id < MINUS || par1GuiButton.id > TRADE)
            {
                super.actionPerformed(par1GuiButton);
                return;
            }

            MerchantRecipe var2 = this.getRecipe();

            if (var2 == null)
            {
                return;
            }

            int var3 = this.getRecipeIndex();
            int var4 = this.getAmount();
            int var5 = Math.max(1, affordable(this.inventorySlots, var2));
            int var6 = isShiftKeyDown() ? 10 : 1;

            if (par1GuiButton.id == MINUS)
            {
                setAmount(this.villager, var3, Math.max(1, var4 - var6));
            }
            else if (par1GuiButton.id == PLUS)
            {
                setAmount(this.villager, var3, Math.min(var5, var4 + var6));
            }
            else if (par1GuiButton.id == MAX)
            {
                setAmount(this.villager, var3, var5);
            }
            else if (queued > 0)
            {
                stop("Stopped after " + done + " trade" + (done == 1 ? "." : "s."));
            }
            else
            {
                startRun(this, Math.max(1, Math.min(var4, affordable(this.inventorySlots, var2))));
            }
        }

        public void drawScreen(int par1, int par2, float par3)
        {
            MerchantRecipe var4 = this.getRecipe();
            int var5 = var4 != null ? affordable(this.inventorySlots, var4) : 0;
            int var6 = this.getAmount();
            this.tradeButton.displayString = queued > 0 ? "Stop" : "Trade";
            this.tradeButton.enabled = var4 != null && (queued > 0 || var5 > 0);
            super.drawScreen(par1, par2, par3);
            this.drawCenteredString(this.fontRenderer, "Trades", this.panelX + 32, this.panelY, 0xFFFFFF);
            this.drawCenteredString(this.fontRenderer, (var6 > var5 && queued == 0 ? "\u00a7c" : "") + var6, this.panelX + 32, this.panelY + 18, 0xFFFFFF);
            this.fontRenderer.drawStringWithShadow("\u00a77Can pay:", this.panelX, this.panelY + 84, 0xFFFFFF);
            this.fontRenderer.drawStringWithShadow("\u00a77" + var5 + " trade" + (var5 == 1 ? "" : "s"), this.panelX, this.panelY + 94, 0xFFFFFF);

            if (queued > 0)
            {
                this.fontRenderer.drawStringWithShadow("\u00a7aTrading... " + done, this.panelX, this.panelY + 108, 0xFFFFFF);
            }
            else if (status != null)
            {
                this.fontRenderer.drawSplitString(status, this.panelX, this.panelY + 108, 64, 0xFFFF55);
            }

            String var7 = Keyboard.getKeyName(KEY.keyCode);

            if (var7 != null && KEY.keyCode > 0)
            {
                this.fontRenderer.drawSplitString("\u00a77Hold " + var7 + " to keep trading", this.panelX, this.panelY + 136, 64, 0xA0A0A0);
            }
        }
    }
}
