package nl.pim16aap2.bigDoors.toolUsers;

import nl.pim16aap2.bigDoors.BigDoors;
import nl.pim16aap2.bigDoors.Door;
import nl.pim16aap2.bigDoors.util.*;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.logging.Level;

public abstract class ToolUser extends Abortable
{
    protected DoorType type;
    protected String name;
    protected final BigDoors plugin;
    protected Player player;
    protected long doorUID;
    protected final Messages messages;
    protected DoorDirection engineSide;
    protected boolean done = false;
    protected boolean isOpen = false;
    protected Location one, two, engine;
    protected boolean aborting = false;
    protected RotateDirection openDir = null;
    private boolean hasStick = false;

    public ToolUser(BigDoors plugin, Player player, String name, DoorType type)
    {
        this.plugin = plugin;
        messages = plugin.getMessages();
        this.player = player;
        this.name = name;
        one = null;
        two = null;
        engine = null;
        engineSide = null;
        this.type = type;
        plugin.addToolUser(this);
    }

    // Handle location input (player hitting a block).
    public abstract void selector(Location loc);

    // Give a tool to a player (but get correct strings etc from translation file
    // first).
    protected abstract void triggerGiveTool();

    // Finish up (but get correct strings etc from translation file first).
    protected abstract void triggerFinishUp();

    // Check if all the variables that cannot be null are not null.
    protected abstract boolean isReadyToCreateDoor();

    // Final cleanup and door creation.
    protected final void finishUp(Player player, String message) {
        if (isReadyToCreateDoor() && !aborting) {
            Location engine = new Location(
                    one.getWorld(), this.engine.getBlockX(), this.engine.getBlockY(), this.engine.getBlockZ());
            Location powerB = new Location(
                    one.getWorld(), this.engine.getBlockX(), this.engine.getBlockY() - 1, this.engine.getBlockZ());

            plugin.getCommander().addDoor(new Door(one.getWorld(), one, two, engine, name, isOpen, doorUID, false, type,
                    engineSide, powerB, openDir, -1, false));

            Util.messagePlayer(player, message);
        }

        takeToolFromPlayer();
        this.abort();
    }

    protected final void giveToolToPlayer(String[] lore, String[] message)
    {
        if (hasStick)
            return;
        hasStick = true;
        ItemStack tool = new ItemStack(Material.STICK, 1);
        tool.addUnsafeEnchantment(Enchantment.LUCK, 1);
        tool.getItemMeta().addItemFlags(ItemFlag.HIDE_ENCHANTS);

        ItemMeta itemMeta = tool.getItemMeta();
        itemMeta.setDisplayName(messages.getString("CREATOR.GENERAL.StickName"));
        itemMeta.setLore(Arrays.asList(lore));
        tool.setItemMeta(itemMeta);

        int heldSlot = player.getInventory().getHeldItemSlot();
        if (player.getInventory().getItem(heldSlot) == null)
            player.getInventory().setItem(heldSlot, tool);
        else
            player.getInventory().addItem(tool);

        Util.messagePlayer(player, message);
    }

    public final Player getPlayer()
    {
        return player;
    }

    public final void setName(String newName)
    {
        name = newName;
        triggerGiveTool();
    }

    public final String getName()
    {
        return name;
    }

    // Take any selection tools in the player's inventory from them.
    public final void takeToolFromPlayer()
    {
        player.getInventory().forEach(K ->
        {
            if (plugin.getTF().isTool(K))
                K.setAmount(0);
        });
    }

    // Make sure position "one" contains the minimum values, "two" the maximum
    // values and engine min.Y;
    protected final void minMaxFix()
    {
        int minX = one.getBlockX();
        int minY = one.getBlockY();
        int minZ = one.getBlockZ();
        int maxX = two.getBlockX();
        int maxY = two.getBlockY();
        int maxZ = two.getBlockZ();

        one.setX(minX > maxX ? maxX : minX);
        one.setY(minY > maxY ? maxY : minY);
        one.setZ(minZ > maxZ ? maxZ : minZ);
        two.setX(minX < maxX ? maxX : minX);
        two.setY(minY < maxY ? maxY : minY);
        two.setZ(minZ < maxZ ? maxZ : minZ);
    }

    // See if this class is done.
    public final boolean isDone()
    {
        return done;
    }

    // Change isDone status and
    public final void setIsDone(boolean bool)
    {
        done = bool;
        if (bool)
            triggerFinishUp();
    }

    @Override
    public final void abort(boolean onDisable)
    {
        aborting = true;
        takeToolFromPlayer();
        if (onDisable)
            return;
        cancelTask();
        plugin.removeToolUser(this);
        if (!done)
            // TODO: This is dumb. Casting player to CommandSender and then checking if it's
            // a player or console.
            plugin.getMyLogger().returnToSender(player, Level.INFO, ChatColor.RED,
                                                messages.getString("CREATOR.GENERAL.TimeUp"));
    }

    @Override
    public final void abort()
    {
        abort(false);
    }

    public final void abortSilently()
    {
        setIsDone(true);
        abort();
    }
}
