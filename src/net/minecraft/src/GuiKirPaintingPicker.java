package net.minecraft.src;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * Painting Picker's screen (from PaintingSelectionGui): every painting that fits, drawn from the art
 * texture at its real size in rows, scrollable with the wheel or the arrow keys. Clicking one hangs it.
 */
public class GuiKirPaintingPicker extends GuiScreen
{
    private static final int TOP = 24;
    private static final int GAP = 5;
    private static final int CANCEL_ID = 0;

    private final EnumArt[] art;
    private final int x;
    private final int y;
    private final int z;
    private final int direction;

    /** Each painting's place before scrolling: x, y. */
    private int[][] places;
    private int bottom;
    private int scroll = 0;
    private int maxScroll = 0;

    public GuiKirPaintingPicker(EnumArt[] art, int x, int y, int z, int direction)
    {
        this.art = art;
        this.x = x;
        this.y = y;
        this.z = z;
        this.direction = direction;
    }

    public void initGui()
    {
        this.controlList.clear();
        this.controlList.add(new GuiButton(CANCEL_ID, this.width / 2 - 100, this.height - 25, "Cancel"));
        this.bottom = this.height - 40;
        this.places = new int[this.art.length][2];
        int var1 = 10;
        int var2 = TOP + 6;
        int var3 = 0;

        for (int var4 = 0; var4 < this.art.length; ++var4)
        {
            EnumArt var5 = this.art[var4];

            if (var1 + var5.sizeX > this.width - 10 && var1 > 10)
            {
                var1 = 10;
                var2 += var3 + GAP;
                var3 = 0;
            }

            this.places[var4][0] = var1;
            this.places[var4][1] = var2;
            var1 += var5.sizeX + GAP;
            var3 = Math.max(var3, var5.sizeY);
        }

        this.maxScroll = Math.max(0, var2 + var3 + 4 - this.bottom);
        this.scroll = Math.min(this.scroll, this.maxScroll);
    }

    public boolean doesGuiPauseGame()
    {
        return false;
    }

    public void updateScreen()
    {
        if (this.mc.thePlayer == null || !this.mc.thePlayer.isEntityAlive())
        {
            this.mc.displayGuiScreen((GuiScreen)null);
        }
    }

    protected void actionPerformed(GuiButton par1GuiButton)
    {
        if (par1GuiButton.id == CANCEL_ID)
        {
            this.mc.displayGuiScreen((GuiScreen)null);
        }
    }

    public void handleMouseInput()
    {
        super.handleMouseInput();
        int var1 = Mouse.getEventDWheel();

        if (var1 != 0)
        {
            this.scrollBy(var1 > 0 ? -16 : 16);
        }
    }

    protected void keyTyped(char par1, int par2)
    {
        if (par2 == Keyboard.KEY_UP)
        {
            this.scrollBy(-16);
        }
        else if (par2 == Keyboard.KEY_DOWN)
        {
            this.scrollBy(16);
        }
        else
        {
            super.keyTyped(par1, par2);
        }
    }

    private void scrollBy(int amount)
    {
        this.scroll = Math.max(0, Math.min(this.maxScroll, this.scroll + amount));
    }

    /** The painting under the mouse, or -1. Only paintings fully inside the list area count. */
    private int paintingAt(int mouseX, int mouseY)
    {
        for (int var3 = 0; var3 < this.art.length; ++var3)
        {
            int var4 = this.places[var3][0];
            int var5 = this.places[var3][1] - this.scroll;

            if (this.visible(var3) && mouseX >= var4 && mouseY >= var5 && mouseX < var4 + this.art[var3].sizeX && mouseY < var5 + this.art[var3].sizeY)
            {
                return var3;
            }
        }

        return -1;
    }

    private boolean visible(int index)
    {
        int var2 = this.places[index][1] - this.scroll;
        return var2 >= TOP && var2 + this.art[index].sizeY <= this.bottom;
    }

    protected void mouseClicked(int par1, int par2, int par3)
    {
        int var4 = par3 == 0 ? this.paintingAt(par1, par2) : -1;

        if (var4 >= 0)
        {
            this.mc.sndManager.playSoundFX("random.click", 1.0F, 1.0F);
            KirModulePaintingPicker.hang(this.x, this.y, this.z, this.direction, this.art[var4]);
            this.mc.displayGuiScreen((GuiScreen)null);
            return;
        }

        super.mouseClicked(par1, par2, par3);
    }

    public void drawScreen(int par1, int par2, float par3)
    {
        this.drawDefaultBackground();
        int var4 = this.paintingAt(par1, par2);
        this.drawCenteredString(this.fontRenderer, var4 >= 0 ? this.art[var4].title + " (" + this.art[var4].sizeX / 16 + "x" + this.art[var4].sizeY / 16 + ")" : "Choose a painting (" + this.art.length + " fit here)", this.width / 2, 10, 0xFFFFFF);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.mc.renderEngine.getTexture("/art/kz.png"));
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator var5 = Tessellator.instance;

        for (int var6 = 0; var6 < this.art.length; ++var6)
        {
            if (!this.visible(var6))
            {
                continue;
            }

            EnumArt var7 = this.art[var6];
            int var8 = this.places[var6][0];
            int var9 = this.places[var6][1] - this.scroll;
            // The art texture is 256x256 at any texture pack resolution, so its UVs are /256.
            double var10 = (double)var7.offsetX / 256.0D;
            double var12 = (double)var7.offsetY / 256.0D;
            double var14 = (double)(var7.offsetX + var7.sizeX) / 256.0D;
            double var16 = (double)(var7.offsetY + var7.sizeY) / 256.0D;
            var5.startDrawingQuads();
            var5.addVertexWithUV((double)var8, (double)(var9 + var7.sizeY), (double)this.zLevel, var10, var16);
            var5.addVertexWithUV((double)(var8 + var7.sizeX), (double)(var9 + var7.sizeY), (double)this.zLevel, var14, var16);
            var5.addVertexWithUV((double)(var8 + var7.sizeX), (double)var9, (double)this.zLevel, var14, var12);
            var5.addVertexWithUV((double)var8, (double)var9, (double)this.zLevel, var10, var12);
            var5.draw();
        }

        if (var4 >= 0)
        {
            int var18 = this.places[var4][0];
            int var19 = this.places[var4][1] - this.scroll;
            int var20 = this.art[var4].sizeX;
            int var21 = this.art[var4].sizeY;
            int var22 = 0xFFFFFF00;
            drawRect(var18 - 3, var19 - 3, var18 + var20 + 3, var19, var22);
            drawRect(var18 - 3, var19 + var21, var18 + var20 + 3, var19 + var21 + 3, var22);
            drawRect(var18 - 3, var19, var18, var19 + var21, var22);
            drawRect(var18 + var20, var19, var18 + var20 + 3, var19 + var21, var22);
        }

        if (this.maxScroll > 0)
        {
            this.drawCenteredString(this.fontRenderer, "\u00a77Scroll for more", this.width / 2, this.bottom + 4, 0xFFFFFF);
        }

        super.drawScreen(par1, par2, par3);
    }
}
