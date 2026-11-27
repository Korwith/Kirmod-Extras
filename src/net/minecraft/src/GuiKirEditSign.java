package net.minecraft.src;

import org.lwjgl.input.Keyboard;

/**
 * The sign screen for Sign Editing and Sign Item Display: vanilla's GuiEditSign (same typing, same preview)
 * with a "Display: Item / Text" button when Sign Item Display can remember a choice for this sign, and Esc
 * closing it like Done.
 *
 * A sign that was just placed (placed = true, the vanilla screen swapped for this one) is sent to the server
 * the vanilla way, which accepts it as the sign is still editable there. A sign opened again by Sign Editing
 * is written by the integrated server instead (KirModuleSignEditing.save).
 */
public class GuiKirEditSign extends GuiEditSign
{
    /** The sign whose screen is open; it shows its text, not its item, so it can be read while typing. */
    static TileEntitySign editing;

    private final TileEntitySign sign;
    private final boolean placed;
    private final boolean wasEditable;
    private GuiButton displayButton;

    public GuiKirEditSign(TileEntitySign sign, boolean placed)
    {
        super(sign);
        this.sign = sign;
        this.placed = placed;
        this.wasEditable = sign.isEditable();
    }

    public void initGui()
    {
        super.initGui();
        editing = this.sign;
        this.displayButton = null;

        if (mod_KirmodExtras.SIGN_ITEMS.hasSignChoices(this.mc))
        {
            this.displayButton = new GuiButton(1, this.width / 2 - 100, this.height / 4 + 144, "");
            this.controlList.add(this.displayButton);
            this.updateDisplayButton();
        }
    }

    public void updateScreen()
    {
        super.updateScreen();
        this.updateDisplayButton();
    }

    private void updateDisplayButton()
    {
        if (this.displayButton == null)
        {
            return;
        }

        KirModuleSignItems var1 = mod_KirmodExtras.SIGN_ITEMS;

        if (var1.showsText(this.sign))
        {
            this.displayButton.displayString = "Display: Text";
        }
        else
        {
            this.displayButton.displayString = var1.findItem(this.sign) != null ? "Display: Item" : "Display: Item (no item named)";
        }
    }

    protected void actionPerformed(GuiButton par1GuiButton)
    {
        if (par1GuiButton.enabled && par1GuiButton.id == 1)
        {
            KirModuleSignItems var2 = mod_KirmodExtras.SIGN_ITEMS;
            var2.setShowsText(this.sign, !var2.showsText(this.sign));
            this.updateDisplayButton();
            return;
        }

        super.actionPerformed(par1GuiButton);
    }

    protected void keyTyped(char par1, int par2)
    {
        if (par2 == 1)
        {
            this.sign.onInventoryChanged();
            this.mc.displayGuiScreen((GuiScreen)null);
            return;
        }

        super.keyTyped(par1, par2);
    }

    public void onGuiClosed()
    {
        if (editing == this.sign)
        {
            editing = null;
        }

        if (this.placed)
        {
            // Vanilla: Packet130UpdateSign, and the client's copy editable again.
            super.onGuiClosed();
            return;
        }

        Keyboard.enableRepeatEvents(false);
        this.sign.setEditable(this.wasEditable);
        KirModuleSignEditing.save(this.mc.thePlayer != null ? this.mc.thePlayer.dimension : 0, this.sign.xCoord, this.sign.yCoord, this.sign.zCoord, this.sign.signText);
    }
}
