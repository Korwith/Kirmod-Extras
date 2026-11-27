package net.minecraft.src;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Matrix4f;

/**
 * Sign Item Display: "On" makes a sign whose whole text is an item's name ("Coal", or "Redstone" / "Torch"
 * over two lines) show that item's picture in its middle, as it looks in the inventory, instead of the text.
 *
 * Signs are drawn by KirSignRenderer, put in TileEntityRenderer's map in place of vanilla's sign renderer.
 * Each sign can be set back to showing its text with the "Display: Item / Text" button of the sign screen
 * (GuiKirEditSign: opened by Sign Editing, and swapped in for vanilla's screen after placing a sign). Those
 * choices are saved with the host player into level.dat (tag KirmodSignText, through onSaveHostPlayer), so
 * they are for singleplayer and LAN worlds you host; on a remote server every such sign shows its item.
 */
public class KirModuleSignItems extends KirModule
{
    private static final String[] VALUES = new String[] {"Off", "On"};
    private static final String TAG = "KirmodSignText";

    private static final Field fieldSpecialRenderers = KirUtil.findField(TileEntityRenderer.class, "specialRendererMap", "m");

    /** GuiEditSign.entitySign, found by its type. */
    private static Field fieldEditSign;

    private boolean available = false;

    /** Signs set to show their text, as key(dimension, x, y, z). Replaced whole when a world's are read. */
    private volatile Set textSigns = Collections.synchronizedSet(new HashSet());

    /** Bumped whenever textSigns changes, so the renderer's cached answers know to look again. */
    private volatile int textSignsVersion = 0;

    /** Whether this client is on a world whose server reads and saves textSigns (set every frame). */
    private boolean choicesActive = false;

    /** The sign of the sign screen that is open (vanilla's too), which shows its text while it is typed. */
    private TileEntitySign screenSign;

    /** The server whose level.dat signs have been read (server thread). */
    private WeakReference loadedServer = new WeakReference((Object)null);

    /** Normalized item name to the item it names; built when first needed, in the game's language. */
    private Map itemsByName;
    private String itemsLanguageProbe;

    /** What each sign was last found to show (render thread). */
    private final Map cache = new WeakHashMap();

    private static class SignState
    {
        String[] lines = new String[4];
        boolean looked;
        ItemStack item;
        int version = -1;
        boolean text;
    }

    public KirModuleSignItems()
    {
        super("signItems", "Sign Item Display", "Sign Items", 0, "signitems", "signitem");
    }

    public String getCategory()
    {
        return mod_KirmodExtras.CATEGORY_SIGNS;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "Signs that only say an item's name show a picture of that item instead.",
            describeValue("Off", "vanilla, signs show their text."),
            describeValue("On", "a sign whose whole text is an item's name (\"Coal\", or \"Redstone\" and \"Torch\" on two lines) shows that item in its middle. The sign screen's Display button switches a sign back to its text."),
            note("Choosing Item or Text for each sign works in singleplayer and on a LAN world you host; elsewhere every such sign shows its item.")
        };
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public boolean isAvailable()
    {
        return this.available;
    }

    public void install()
    {
        if (fieldSpecialRenderers == null)
        {
            KirUtil.log("Sign Item Display is unavailable (tile entity renderers not found)", (Throwable)null);
            return;
        }

        try
        {
            Map var1 = (Map)fieldSpecialRenderers.get(TileEntityRenderer.instance);
            KirSignRenderer var2 = new KirSignRenderer();
            var2.setTileEntityRenderer(TileEntityRenderer.instance);
            var1.put(TileEntitySign.class, var2);
            this.available = true;
        }
        catch (Exception var3)
        {
            KirUtil.log("Could not install Sign Item Display", var3);
        }

        Field[] var4 = GuiEditSign.class.getDeclaredFields();

        for (int var5 = 0; var5 < var4.length; ++var5)
        {
            if (var4[var5].getType() == TileEntitySign.class)
            {
                fieldEditSign = var4[var5];
                fieldEditSign.setAccessible(true);
            }
        }
    }

    /** Kept on while the module is installed, so the signs' choices are read and saved even while Off. */
    public boolean hasServerPlayerTick()
    {
        return true;
    }

    public void update(Minecraft mc)
    {
        KirWorldHook.installAll(mc);
        this.choicesActive = mc.theWorld != null && (KirInteraction.isOnIntegratedServer(mc) || KirLocalGame.isLocal(mc));
        this.screenSign = null;

        if (mc.currentScreen instanceof GuiEditSign && fieldEditSign != null)
        {
            try
            {
                this.screenSign = (TileEntitySign)fieldEditSign.get(mc.currentScreen);
            }
            catch (Exception var6)
            {
                this.screenSign = null;
            }
        }

        // Vanilla's screen after placing a sign: the same screen with the Display button.
        if (this.hasSignChoices(mc) && mc.currentScreen != null && mc.currentScreen.getClass() == GuiEditSign.class && fieldEditSign != null)
        {
            try
            {
                TileEntitySign var2 = (TileEntitySign)fieldEditSign.get(mc.currentScreen);

                if (var2 != null)
                {
                    // Not displayGuiScreen: closing vanilla's screen would send the empty text and lock the sign.
                    GuiKirEditSign var3 = new GuiKirEditSign(var2, true);
                    mc.currentScreen = var3;
                    ScaledResolution var4 = new ScaledResolution(mc.gameSettings, mc.displayWidth, mc.displayHeight);
                    var3.setWorldAndResolution(mc, var4.getScaledWidth(), var4.getScaledHeight());
                }
            }
            catch (Exception var5)
            {
                KirUtil.log("Could not open Kirmod's sign screen", var5);
                fieldEditSign = null;
            }
        }
    }

    /** Whether the sign screen shows the Display button: On, and the choice can be saved on this world. */
    public boolean hasSignChoices(Minecraft mc)
    {
        return this.enabled && this.available && this.getValue() == 1 && this.choicesActive;
    }

    private static String key(int dimension, int x, int y, int z)
    {
        return dimension + " " + x + " " + y + " " + z;
    }

    private static String key(TileEntitySign sign)
    {
        World var1 = sign.worldObj;
        return key(var1 != null && var1.provider != null ? var1.provider.worldType : 0, sign.xCoord, sign.yCoord, sign.zCoord);
    }

    /** Whether this sign was set to show its text (only counts where choices are saved). */
    public boolean showsText(TileEntitySign sign)
    {
        return this.choicesActive && this.textSigns.contains(key(sign));
    }

    public void setShowsText(TileEntitySign sign, boolean text)
    {
        if (text)
        {
            this.textSigns.add(key(sign));
        }
        else
        {
            this.textSigns.remove(key(sign));
        }

        ++this.textSignsVersion;
    }

    /** The item this sign's whole text names, or null. Render thread. */
    public ItemStack findItem(TileEntitySign sign)
    {
        SignState var2 = this.getState(sign);
        return var2.item;
    }

    /** The item KirSignRenderer draws on this sign instead of its text, or null to draw the text. */
    ItemStack getDisplayedItem(TileEntitySign sign)
    {
        if (!this.enabled || this.getValue() != 1 || sign == GuiKirEditSign.editing || sign == this.screenSign || sign.lineBeingEdited >= 0)
        {
            return null;
        }

        SignState var2 = this.getState(sign);

        if (var2.item == null)
        {
            return null;
        }

        int var3 = this.textSignsVersion;

        if (var2.version != var3)
        {
            var2.version = var3;
            var2.text = this.showsText(sign);
        }

        return var2.text && this.choicesActive ? null : var2.item;
    }

    private SignState getState(TileEntitySign sign)
    {
        SignState var2 = (SignState)this.cache.get(sign);

        if (var2 == null)
        {
            var2 = new SignState();
            this.cache.put(sign, var2);
        }

        boolean var3 = !var2.looked;

        for (int var4 = 0; var4 < 4; ++var4)
        {
            // A sign's lines are replaced, not changed, so comparing the references is enough.
            if (var2.lines[var4] != sign.signText[var4])
            {
                var3 = true;
            }
        }

        if (var3)
        {
            System.arraycopy(sign.signText, 0, var2.lines, 0, 4);
            var2.item = this.lookUp(sign.signText);
            var2.looked = true;
            var2.version = -1;
        }

        return var2;
    }

    /** The item that lines name together (the non-empty ones, joined by spaces), or null. */
    private ItemStack lookUp(String[] lines)
    {
        StringBuilder var2 = new StringBuilder();

        for (int var3 = 0; var3 < lines.length; ++var3)
        {
            String var4 = lines[var3] == null ? "" : lines[var3].replaceAll("§.", "").trim();

            if (var4.length() > 0)
            {
                if (var2.length() > 0)
                {
                    var2.append(' ');
                }

                var2.append(var4);
            }
        }

        if (var2.length() == 0)
        {
            return null;
        }

        String var5 = KirUtil.normalize(var2.toString());
        return var5.length() == 0 ? null : (ItemStack)this.getItemsByName().get(var5);
    }

    private Map getItemsByName()
    {
        String var1 = StringTranslate.getInstance().translateKey("item.coal.name");

        if (this.itemsByName != null && var1.equals(this.itemsLanguageProbe))
        {
            return this.itemsByName;
        }

        HashMap var2 = new HashMap();

        // Items that are in the creative inventory first, so "Wooden Door" is the door item, not the door block.
        for (int var3 = 0; var3 < 2; ++var3)
        {
            for (int var4 = 0; var4 < Item.itemsList.length; ++var4)
            {
                Item var5 = Item.itemsList[var4];

                if (var5 == null || var4 < 256 && Block.blocksList[var4] == null)
                {
                    continue;
                }

                CreativeTabs var6 = var5.getCreativeTab();

                if ((var3 == 0) != (var6 != null))
                {
                    continue;
                }

                ArrayList var7 = new ArrayList();

                try
                {
                    if (var6 != null && var5.getHasSubtypes())
                    {
                        var5.getSubItems(var4, var6, var7);
                    }
                }
                catch (RuntimeException var11)
                {
                    var7.clear();
                }

                if (var7.isEmpty())
                {
                    var7.add(new ItemStack(var4, 1, 0));
                }

                for (int var8 = 0; var8 < var7.size(); ++var8)
                {
                    ItemStack var9 = (ItemStack)var7.get(var8);

                    try
                    {
                        String var10 = var5.getItemDisplayName(var9);

                        // Untranslated names ("tile.foo.name") aren't anything a player would write.
                        if (var10 != null && !var10.endsWith(".name"))
                        {
                            String var12 = KirUtil.normalize(var10);

                            if (var12.length() > 0 && !var2.containsKey(var12))
                            {
                                var2.put(var12, var9);
                            }
                        }
                    }
                    catch (RuntimeException var13)
                    {
                        // An item from another mod that can't name this stack: skip it.
                    }
                }
            }
        }

        this.itemsByName = var2;
        this.itemsLanguageProbe = var1;
        this.cache.clear();
        return var2;
    }

    public void onServerPlayerTick(EntityPlayer player)
    {
        MinecraftServer var2 = MinecraftServer.getServer();

        if (this.loadedServer.get() == var2)
        {
            return;
        }

        this.loadedServer = new WeakReference(var2);
        Set var3 = Collections.synchronizedSet(new HashSet());
        NBTTagCompound var4 = player.worldObj.getWorldInfo().getPlayerNBTTagCompound();

        if (var4 != null && var4.hasKey(TAG))
        {
            NBTTagList var5 = var4.getTagList(TAG);

            for (int var6 = 0; var6 < var5.tagCount(); ++var6)
            {
                NBTTagCompound var7 = (NBTTagCompound)var5.tagAt(var6);
                var3.add(key(var7.getInteger("D"), var7.getInteger("X"), var7.getInteger("Y"), var7.getInteger("Z")));
            }
        }

        this.textSigns = var3;
        ++this.textSignsVersion;
    }

    public void onSaveHostPlayer(EntityPlayer player, NBTTagCompound tag)
    {
        Set var3 = this.textSigns;
        NBTTagList var4 = new NBTTagList();

        synchronized (var3)
        {
            Iterator var5 = var3.iterator();

            while (var5.hasNext())
            {
                String[] var6 = ((String)var5.next()).split(" ");
                int var7 = Integer.parseInt(var6[0]);
                int var8 = Integer.parseInt(var6[1]);
                int var9 = Integer.parseInt(var6[2]);
                int var10 = Integer.parseInt(var6[3]);

                // A sign that is gone (in a loaded chunk of the host's world) loses its choice.
                if (var7 == player.dimension && player.worldObj.blockExists(var8, var9, var10) && !(player.worldObj.getBlockTileEntity(var8, var9, var10) instanceof TileEntitySign))
                {
                    var5.remove();
                    continue;
                }

                NBTTagCompound var11 = new NBTTagCompound();
                var11.setInteger("D", var7);
                var11.setInteger("X", var8);
                var11.setInteger("Y", var9);
                var11.setInteger("Z", var10);
                var4.appendTag(var11);
            }
        }

        if (var4.tagCount() > 0)
        {
            tag.setTag(TAG, var4);
        }
    }

    /**
     * Vanilla's sign renderer, which draws a sign's item instead of its text when Sign Item Display says so
     * (KirModuleSignItems.getDisplayedItem). The item is drawn like in the inventory: a flat icon, or a block
     * as a small cube, pressed flat onto the sign.
     */
    public static class KirSignRenderer extends TileEntitySignRenderer
    {
        private final ModelSign modelSign = new ModelSign();
        private final RenderBlocks renderBlocks = new RenderBlocks();
        private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

        /** How much of its depth a block keeps when pressed onto a sign. */
        private static final float FLAT = 0.02F;

        public void renderTileEntityAt(TileEntity par1TileEntity, double par2, double par4, double par6, float par8)
        {
            TileEntitySign var9 = (TileEntitySign)par1TileEntity;
            ItemStack var10 = mod_KirmodExtras.SIGN_ITEMS.getDisplayedItem(var9);

            if (var10 == null)
            {
                super.renderTileEntityAt(par1TileEntity, par2, par4, par6, par8);
                return;
            }

            // As TileEntitySignRenderer, up to where it draws the text.
            Block var11 = var9.getBlockType();
            GL11.glPushMatrix();
            float var12 = 0.6666667F;

            if (var11 == Block.signPost)
            {
                GL11.glTranslatef((float)par2 + 0.5F, (float)par4 + 0.75F * var12, (float)par6 + 0.5F);
                float var13 = (float)(var9.getBlockMetadata() * 360) / 16.0F;
                GL11.glRotatef(-var13, 0.0F, 1.0F, 0.0F);
                this.modelSign.signStick.showModel = true;
            }
            else
            {
                int var14 = var9.getBlockMetadata();
                float var15 = var14 == 2 ? 180.0F : (var14 == 4 ? 90.0F : (var14 == 5 ? -90.0F : 0.0F));
                GL11.glTranslatef((float)par2 + 0.5F, (float)par4 + 0.75F * var12, (float)par6 + 0.5F);
                GL11.glRotatef(-var15, 0.0F, 1.0F, 0.0F);
                GL11.glTranslatef(0.0F, -0.3125F, -0.4375F);
                this.modelSign.signStick.showModel = false;
            }

            this.bindTextureByName("/item/sign.png");
            GL11.glPushMatrix();
            GL11.glScalef(var12, -var12, -var12);
            this.modelSign.renderSign();
            GL11.glPopMatrix();

            // The text's space: x right, y down, z out of the sign, one unit a pixel of the font.
            float var16 = 0.016666668F * var12;
            GL11.glTranslatef(0.0F, 0.5F * var12, 0.07F * var12);
            GL11.glScalef(var16, -var16, var16);
            GL11.glNormal3f(0.0F, 0.0F, -1.0F * var16);

            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT);

            try
            {
                // Centred where the four lines are, 32 font pixels across (the sign is 90 by 45).
                GL11.glTranslatef(0.0F, -1.0F, 0.0F);
                GL11.glScalef(2.0F, 2.0F, 2.0F);
                this.drawItem(var10, -8, -8);
            }
            finally
            {
                GL11.glPopAttrib();
            }

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopMatrix();
        }

        /** RenderItem.drawItemIntoGui's drawing, at x, y of the current space (16 by 16, y down). */
        private void drawItem(ItemStack stack, int x, int y)
        {
            Item var4 = Item.itemsList[stack.itemID];

            if (var4 == null)
            {
                return;
            }

            RenderEngine var5 = ModLoader.getMinecraftInstance().renderEngine;
            int var6 = stack.getItemDamage();
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);

            if (stack.itemID < 256 && RenderBlocks.renderItemIn3d(Block.blocksList[stack.itemID].getRenderType()))
            {
                var5.bindTexture(var5.getTexture("/terrain.png"));

                // Lit like in the inventory: the GUI's lights, set in this space, which is turned like the GUI's.
                RenderHelper.enableGUIStandardItemLighting();
                GL11.glEnable(GL11.GL_NORMALIZE);
                boolean var7 = this.pushFlattening();

                GL11.glPushMatrix();
                // In front of the sign by a little more than the cube's depth, so none of it sinks into the board.
                GL11.glTranslatef((float)(x - 2), (float)(y + 3), 12.0F);
                GL11.glScalef(10.0F, 10.0F, 10.0F);
                GL11.glTranslatef(1.0F, 0.5F, 1.0F);
                GL11.glScalef(1.0F, 1.0F, -1.0F);
                GL11.glRotatef(210.0F, 1.0F, 0.0F, 0.0F);
                GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
                setColor(var4.getColorFromDamage(var6, 0));
                GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);
                this.renderBlocks.useInventoryTint = true;
                this.renderBlocks.renderBlockAsItem(Block.blocksList[stack.itemID], var6, 1.0F);
                GL11.glPopMatrix();

                if (var7)
                {
                    GL11.glMatrixMode(GL11.GL_PROJECTION);
                    GL11.glPopMatrix();
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                }
            }
            else
            {
                GL11.glDisable(GL11.GL_LIGHTING);
                var5.bindTexture(var5.getTexture(stack.itemID < 256 ? "/terrain.png" : "/gui/items.png"));

                if (var4.requiresMultipleRenderPasses())
                {
                    for (int var8 = 0; var8 <= 1; ++var8)
                    {
                        setColor(var4.getColorFromDamage(var6, var8));
                        drawIcon(x, y, var4.getIconFromDamageForRenderPass(var6, var8), (float)var8 * 0.05F);
                    }
                }
                else
                {
                    int var9 = stack.getIconIndex();

                    if (var9 >= 0)
                    {
                        setColor(var4.getColorFromDamage(var6, 0));
                        drawIcon(x, y, var9, 0.0F);
                    }
                }
            }
        }

        /**
         * Presses what is drawn next flat onto the sign (scales the current space's z by FLAT), in the projection
         * matrix so the lighting still sees the block's real normals. True if it did; the caller pops it.
         */
        private boolean pushFlattening()
        {
            this.matrixBuffer.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, this.matrixBuffer);
            Matrix4f var1 = new Matrix4f();
            var1.load(this.matrixBuffer);
            Matrix4f var2 = Matrix4f.invert(var1, (Matrix4f)null);

            if (var2 == null)
            {
                return false;
            }

            Matrix4f var3 = new Matrix4f();
            var3.m22 = FLAT;
            Matrix4f var4 = Matrix4f.mul(var1, Matrix4f.mul(var3, var2, (Matrix4f)null), (Matrix4f)null);
            this.matrixBuffer.clear();
            var4.store(this.matrixBuffer);
            this.matrixBuffer.flip();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glMultMatrix(this.matrixBuffer);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            return true;
        }

        private static void setColor(int rgb)
        {
            GL11.glColor4f((float)(rgb >> 16 & 255) / 255.0F, (float)(rgb >> 8 & 255) / 255.0F, (float)(rgb & 255) / 255.0F, 1.0F);
        }

        /** A 16 by 16 icon of the bound 256 by 256 sheet, like RenderItem.renderTexturedQuad. */
        private static void drawIcon(int x, int y, int icon, float z)
        {
            float var4 = 0.00390625F;
            int var5 = icon % 16 * 16;
            int var6 = icon / 16 * 16;
            Tessellator var7 = Tessellator.instance;
            var7.startDrawingQuads();
            var7.addVertexWithUV((double)x, (double)(y + 16), (double)z, (double)((float)var5 * var4), (double)((float)(var6 + 16) * var4));
            var7.addVertexWithUV((double)(x + 16), (double)(y + 16), (double)z, (double)((float)(var5 + 16) * var4), (double)((float)(var6 + 16) * var4));
            var7.addVertexWithUV((double)(x + 16), (double)y, (double)z, (double)((float)(var5 + 16) * var4), (double)((float)var6 * var4));
            var7.addVertexWithUV((double)x, (double)y, (double)z, (double)((float)var5 * var4), (double)((float)var6 * var4));
            var7.draw();
        }
    }
}
