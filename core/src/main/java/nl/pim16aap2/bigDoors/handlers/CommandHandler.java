package nl.pim16aap2.bigDoors.handlers;

import com.cryptomorin.xseries.XMaterial;
import nl.pim16aap2.bigDoors.BigDoors;
import nl.pim16aap2.bigDoors.Door;
import nl.pim16aap2.bigDoors.GUI.GUI;
import nl.pim16aap2.bigDoors.moveBlocks.Opener;
import nl.pim16aap2.bigDoors.toolUsers.*;
import nl.pim16aap2.bigDoors.util.*;
import nl.pim16aap2.bigDoors.waitForCommand.WaitForCommand;
import nl.pim16aap2.bigDoors.waitForCommand.WaitForSetBlocksToMove;
import nl.pim16aap2.bigDoors.waitForCommand.WaitForSetTime;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class CommandHandler implements CommandExecutor {
    BigDoors plugin;

    public CommandHandler(BigDoors plugin)
    {
        this.plugin = plugin;
    }

    public void openDoorCommand(CommandSender sender, Door door, double time, boolean instant) {
        Door newDoor = plugin.getCommander().getDoor(door.getDoorUID());

        if (newDoor == null) {
            plugin.getMyLogger().returnToSender(sender, Level.INFO, ChatColor.RED, plugin.getMessages().getString("GENERAL.ToggleFailure"));
            return;
        }

        if (newDoor.isLocked()) {
            plugin.getMyLogger().returnToSender(sender, Level.INFO, ChatColor.RED, plugin.getMessages().getString("GENERAL.DoorIsLocked"));
        } else {
            Opener opener = plugin.getDoorOpener(newDoor.getType());
            CompletableFuture<DoorOpenResult> futureResult =
                opener == null ?
                CompletableFuture.completedFuture(DoorOpenResult.TYPEDISABLED) :
                opener.openDoorFuture(newDoor, time, instant);

            futureResult.thenAccept(
                result ->
                {
                    if (result != DoorOpenResult.SUCCESS)
                        plugin.getMyLogger().returnToSender(
                            sender, Level.INFO, ChatColor.RED,
                            plugin.getMessages().getString(DoorOpenResult.getMessage(result)));
                }).exceptionally(Util::exceptionally);
        }
    }


    public void openDoorCommand(Player player, Door door) {
        openDoorCommand(player, door, 0.0, false);
    }

    public void listDoors(Player player, String doorName) {
        ArrayList<Door> doors = plugin.getCommander().getDoors(doorName);
        for (Door door : doors)
            Util.messagePlayer(player, Util.getBasicDoorInfo(door));
        if (doors.isEmpty())
            Util.messagePlayer(player, plugin.getMessages().getString("GENERAL.NoDoorsFound"));
    }

    public void listDoorInfo(Player player, String name) {
        long doorUID = Util.longFromString(name, -1L);
        if (doorUID != -1) {
            Util.messagePlayer(player, Util.getFullDoorInfo(plugin.getCommander().getDoor(doorUID)));
        } else {
            ArrayList<Door> doors = plugin.getCommander().getDoors(name);
            for (Door door : doors) Util.messagePlayer(player, Util.getFullDoorInfo(door));

            if (doors.isEmpty())
                Util.messagePlayer(player, plugin.getMessages().getString("GENERAL.NoDoorsFound"));
        }
    }

    public void listDoorInfo(Player player, Door door) {
        Util.messagePlayer(player, Util.getFullDoorInfo(door));
    }

    public void listDoorInfoIgnorePermission(Player player, Door door) {
        Util.messagePlayer(player, Util.getFullDoorInfo(door));
    }

    public boolean isValidDoorName(String name) {
        try {
            Integer.parseInt(name);
            return false;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    public void startCreator(Player player, String name, DoorType type) {
        if (!player.hasPermission(DoorType.getPermission(type))) {
            Util.messagePlayer(player, ChatColor.RED, plugin.getMessages().getString("GENERAL.NoDoorTypeCreationPermission"));
            return;
        }

        if (name != null && !isValidDoorName(name)) {
            Util.messagePlayer(player, ChatColor.RED, "\"" + name + "\"" + plugin.getMessages().getString("GENERAL.InvalidDoorName"));
            return;
        }

        if (isPlayerBusy(player)) return;

        ToolUser tu = type == DoorType.DOOR ? new DoorCreator(plugin, player, name) :
                      type == DoorType.DRAWBRIDGE ? new DrawbridgeCreator(plugin, player, name) :
                      type == DoorType.PORTCULLIS ? new PortcullisCreator(plugin, player, name) :
                      type == DoorType.SLIDINGDOOR ? new SlidingDoorCreator(plugin, player, name) : null;

        startTimerForAbortable(tu, 120 * 20);
    }

    public void startTimerForAbortable(Abortable abortable, int time) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                abortable.abort(false);
            }
        }.runTaskLater(plugin, time);
        abortable.setTask(task);
    }

    public void setDoorOpenTime(long doorUID, int autoClose) {
        plugin.getCommander().updateDoorAutoClose(doorUID, autoClose);
    }

    public void setDoorBlocksToMove(long doorUID, int autoClose) {
        plugin.getCommander().updateDoorBlocksToMove(doorUID, autoClose);
    }

    public void startBlocksToMoveSetter(Player player, long doorUID) {
        if (isPlayerBusy(player)) return;

        startTimerForAbortable((new WaitForSetBlocksToMove(plugin, player, doorUID)), plugin.getConfigLoader().commandWaiterTimeout() * 20);
    }

    private void replaceWaitForCommand(Player player) {
        WaitForCommand cw = plugin.getCommandWaiter(player);
        if (cw != null)
        {
            cw.setFinished(true);
            abortAbortable(cw);
        }
    }

    public void startTimerSetter(Player player, long doorUID) {
        replaceWaitForCommand(player);
        startTimerForAbortable((new WaitForSetTime(plugin, player, doorUID)), plugin.getConfigLoader().commandWaiterTimeout() * 20);
    }

    private boolean isPlayerBusy(Player player) {
        boolean isBusy = (plugin.getToolUser(player) != null || plugin.getCommandWaiter(player) != null);
        if (isBusy) Util.messagePlayer(player, plugin.getMessages().getString("GENERAL.IsBusy"));

        return isBusy;
    }

    private void abortAbortable(Abortable abortable) {
        if (abortable instanceof ToolUser) ((ToolUser) abortable).setIsDone(true);

        abortable.abort();
    }

    public void startPowerBlockRelocator(Player player, Door door) {
        startTimerForAbortable(new PowerBlockRelocator(plugin, player, door),
                               plugin.getConfigLoader().commandWaiterTimeout() * 20);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Player player = sender instanceof Player ? (Player) sender : null;


        if (cmd.getName().equalsIgnoreCase("bigdoors")) {
            String firstCommand = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

            switch (firstCommand) {
                case "disabletoggle":
                    if (player != null && !player.hasPermission("bigdoors.admin.disabletoggle"))
                        break;
                    plugin.getCommander().setCanGo(false);
                    plugin.getCommander().stopMovers(false);
                    if (player != null)
                        plugin.getMyLogger().returnToSender(sender, Level.INFO, ChatColor.GREEN,
                                                            plugin.getMessages().getString("COMMAND.ToggleDisabled"));
                    else
                        plugin.getMyLogger().info(
                            "All door toggles have been stopped! Doors will not be able to toggle until you run '/bigdoors enabletoggle'.");
                    break;

                case "enabletoggle":
                    if (player != null && !player.hasPermission("bigdoors.admin.disabletoggle"))
                        break;
                    plugin.getCommander().setCanGo(true);
                    if (player != null)
                        plugin.getMyLogger().returnToSender(sender, Level.INFO, ChatColor.GREEN,
                                                            plugin.getMessages().getString("COMMAND.ToggleEnabled"));
                    else
                        plugin.getMyLogger().info("Door toggles have been activated again!");
                    break;

                case "menu":
                    if (player != null && player.hasPermission("bigdoors.user.gui"))
                        plugin.addGUIUser(new GUI(plugin, player));
                    break;

                case "reload":
                case "restart":
                    if (player != null && !player.hasPermission("bigdoors.admin.restart"))
                        break;
                    plugin.restart();
                    plugin.getMyLogger().returnToSender(sender, Level.INFO, ChatColor.GREEN,
                                                        "BigDoors has been restarted!");
                    break;

                case "stop":
                    if ((player != null && player.hasPermission("bigdoors.admin.stopdoors")) || player == null)
                        plugin.getCommander().stopMovers(false);
                    break;

                case "pause":
                    if ((player != null && player.hasPermission("bigdoors.admin.pausedoors")) || player == null)
                        plugin.getCommander().togglePaused();
                    break;

                case "":
                case "help":
                default:
                    showHelpInfo(sender);
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("stopdoors")) {
            plugin.getCommander().stopMovers(false);
            return true;
        } else if (cmd.getName().equalsIgnoreCase("setautoclosetime")) {
            if (args.length == 1 && player != null) {
                WaitForCommand cw = plugin.getCommandWaiter(player);
                if (cw != null)
                    return cw.executeCommand(args);
            } else if (args.length == 2) {
                WaitForCommand cw = plugin.getCommandWaiter(player);
                if (cw != null && cw.getCommand().equals("setautoclosetime"))
                    cw.abortSilently();

                Door door = plugin.getCommander().getDoor(Long.parseLong(args[0]));
                if (door == null) return false;

                try {
                    int time = Integer.parseInt(args[1]);
                    setDoorOpenTime(door.getDoorUID(), time);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }

            return false;
        } else if (cmd.getName().equalsIgnoreCase("setblockstomove")) {
            if (args.length == 1 && player != null) {
                WaitForCommand cw = plugin.getCommandWaiter(player);
                if (cw != null)
                    return cw.executeCommand(args);
            } else if (args.length == 2) {
                WaitForCommand cw = plugin.getCommandWaiter(player);
                if (cw != null && cw.getCommand().equals("setblockstomove"))
                    cw.abortSilently();

                Door door = plugin.getCommander().getDoor(Long.parseLong(args[0]));
                if (door == null) return false;

                try {
                    int blocksToMove = Integer.parseInt(args[1]);
                    if (blocksToMove > BigDoors.get().getConfigLoader().getMaxBlocksToMove())
                        plugin.getMyLogger()
                              .returnToSender(sender, Level.WARNING, ChatColor.RED,
                                              BigDoors.get().getMessages()
                                                      .getString("GENERAL.InvalidBlocksToMoveRange"));
                    else
                        setDoorBlocksToMove(door.getDoorUID(), blocksToMove);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
            return false;
        } else if (cmd.getName().equalsIgnoreCase("setdoorrotation")) {
            if (args.length != 2) return false;

            Door door = plugin.getCommander().getDoor(Long.parseLong(args[0]));
            if (door == null) return false;

            RotateDirection openDir = null;
            if (door.getType() == DoorType.SLIDINGDOOR) {
                if (args[1].equalsIgnoreCase("NORTH"))
                    openDir = RotateDirection.NORTH;
                else if (args[1].equalsIgnoreCase("EAST"))
                    openDir = RotateDirection.NORTH;
                else if (args[1].equalsIgnoreCase("SOUTH"))
                    openDir = RotateDirection.NORTH;
                else if (args[1].equalsIgnoreCase("WEST"))
                    openDir = RotateDirection.NORTH;
            } else {
                if (args[1].equalsIgnoreCase("CLOCK") || args[1].equalsIgnoreCase("CLOCKWISE"))
                    openDir = RotateDirection.CLOCKWISE;
                else if (args[1].equalsIgnoreCase("COUNTER") || args[1].equalsIgnoreCase("COUNTERCLOCKWISE"))
                    openDir = RotateDirection.COUNTERCLOCKWISE;
            }

            if (openDir == null) return false;

            plugin.getCommander().updateDoorOpenDirection(door.getDoorUID(), openDir);
            return true;
        } else if (cmd.getName().equalsIgnoreCase("filldoor")) {
            if (args.length != 1) return false;

            Door door = plugin.getCommander().getDoor(Long.parseLong(args[0]));
            if (door == null) return false;

            for (int i = door.getMinimum().getBlockX(); i <= door.getMaximum().getBlockX(); ++i)
                for (int j = door.getMinimum().getBlockY(); j <= door.getMaximum().getBlockY(); ++j)
                    for (int k = door.getMinimum().getBlockZ(); k <= door.getMaximum().getBlockZ(); ++k)
                        door.getWorld().getBlockAt(i, j, k).setType(XMaterial.STONE.parseMaterial());
            door.getPowerBlockLoc().getBlock().setType(plugin.getConfigLoader().getPowerBlockTypes().iterator().next());
            return true;
        } else if (cmd.getName().equalsIgnoreCase("recalculatepowerblocks")) {
            plugin.getCommander().recalculatePowerBlockHashes();
            plugin.getMyLogger().returnToSender(sender, Level.INFO, ChatColor.GREEN,
                                                "power block hashes have been regenerated!");
            return true;
        } else if (cmd.getName().equalsIgnoreCase("pausedoors")) {
            plugin.getCommander().togglePaused();
            return true;
        } else if (cmd.getName().equalsIgnoreCase("opendoor") || cmd.getName().equalsIgnoreCase("closedoor") || cmd.getName().equalsIgnoreCase("toggledoor")) {
            boolean instantly = false;
            int type = cmd.getName().equalsIgnoreCase("opendoor") ? 0 :
                       cmd.getName().equalsIgnoreCase("closedoor") ? 1 : 2;

            if (args.length >= 1) {
                String lastStr = args[args.length - 1];
                double time = Util.longFromString(lastStr, -1L) == -1L ? Util.doubleFromString(lastStr, 0.0D) : 0.0D;
                int endIDX = args.length;
                if (time != 0.0D) {
                    --endIDX;
                    if (time < 0)
                        instantly = true;
                }

                for (int index = 0; index < endIDX; ++index) {
                    Door door = plugin.getCommander().getDoor(Long.parseLong(args[index]));

                    if (door == null) {
                        plugin.getMyLogger().returnToSender(sender, Level.INFO, ChatColor.RED, "\"" + args[index]
                            + "\" " + plugin.getMessages().getString("GENERAL.InvalidDoorName"));
                        if (player == null)
                            plugin.getMyLogger()
                                  .returnToSender(sender, Level.INFO, ChatColor.RED,
                                                  "Don't forget that you should use the DoorUID in the console/command blocks! DoorName won't work here!");
                    } else {
                        if (type == 2 || door.getOpenDir().equals(RotateDirection.NONE))
                            openDoorCommand(sender, door, time, instantly);
                        else if (type == 1) {
                            if (door.isOpen())
                                openDoorCommand(sender, door, time, instantly);
                            else
                                plugin.getMyLogger()
                                      .returnToSender(sender, Level.INFO, ChatColor.RED,
                                                      plugin.getMessages().getString("GENERAL.Door")
                                                          + " " + door.toSimpleString() + " "
                                                          +
                                                          plugin.getMessages().getString("GENERAL.DoorAlreadyClosed"));
                        } else if (!door.isOpen())
                            openDoorCommand(sender, door, time, instantly);
                        else
                            plugin.getMyLogger()
                                    .returnToSender(sender, Level.INFO, ChatColor.RED,
                                            plugin.getMessages().getString("GENERAL.Door")
                                                    + " " + door.toSimpleString() + " "
                                                    + plugin.getMessages().getString("GENERAL.DoorAlreadyOpen"));
                    }
                }
                return true;
            }
        } else if (player != null) {
            if (cmd.getName().equalsIgnoreCase("inspectpowerblockloc")) {
                if (isPlayerBusy(player))
                    return false;
                startTimerForAbortable(new PowerBlockInspector(plugin, player, -1),
                                       plugin.getConfigLoader().commandWaiterTimeout() * 20);
                return true;
            }

            if (cmd.getName().equalsIgnoreCase("bdm") || cmd.getName().equalsIgnoreCase("doors"))
            {
                plugin.addGUIUser(new GUI(plugin, player));
                return true;
            }

            if (cmd.getName().equalsIgnoreCase("namedoor")) {
                ToolUser tu = plugin.getToolUser(player);
                if (tu != null)
                {
                    if (args.length == 1)
                        if (isValidDoorName(args[0]))
                        {
                            tu.setName(args[0]);
                            return true;
                        }
                }
                else
                    Util.messagePlayer(player, plugin.getMessages().getString("GENERAL.NotBusy"));
            }

            // /changePowerBlockLoc
            if (cmd.getName().equalsIgnoreCase("changePowerBlockLoc"))
            {
                if (args.length < 1)
                    return false;

                Door door = plugin.getCommander().getDoor(Long.parseLong(args[0]));
                if (door == null)
                {
                    Util.messagePlayer(player, ChatColor.RED, "No door found by that name!");
                    return true;
                }

                startPowerBlockRelocator(player, door);
                return true;
            }

            if (cmd.getName().equalsIgnoreCase("bdcancel"))
            {
                ToolUser tu = plugin.getToolUser(player);
                if (tu != null)
                {
                    abortAbortable(tu);
                    plugin.getMyLogger().returnToSender(player, Level.INFO, ChatColor.RED,
                                                        plugin.getMessages().getString("CREATOR.GENERAL.Cancelled"));
                }
                else
                {
                    WaitForCommand cw = plugin.getCommandWaiter(player);
                    if (cw != null)
                        abortAbortable(cw);
                }
                return true;
            }

            // deldoor <doorName>
            if (cmd.getName().equalsIgnoreCase("deldoor"))
                if (args.length == 1)
                {
                    delDoor(player, args[0]);
                    return true;
                }

            // /newdoor <doorName>
            if (cmd.getName().equalsIgnoreCase("newdoor"))
                if (args.length >= 1)
                {
                    DoorType type = DoorType.DOOR;
                    String name;
                    if (args.length == 2)
                    {
                        type = DoorType.valueOfFlag(args[0].toUpperCase());
                        if (type == null)
                            return false;
                        name = args[1];
                    }
                    else
                        name = args[0];

                    startCreator(player, name, type);
                    return true;
                }

            // /newportcullis <doorName>
            if (cmd.getName().equalsIgnoreCase("newportcullis"))
                if (args.length == 1)
                {
                    startCreator(player, args[0], DoorType.PORTCULLIS);
                    return true;
                }

            // /newslidingdoor <doorName>
            if (cmd.getName().equalsIgnoreCase("newslidingdoor"))
                if (args.length == 1)
                {
                    startCreator(player, args[0], DoorType.SLIDINGDOOR);
                    return true;
                }

            // /newdrawbridge <doorName>
            if (cmd.getName().equalsIgnoreCase("newdrawbridge"))
                if (args.length == 1)
                {
                    startCreator(player, args[0], DoorType.DRAWBRIDGE);
                    return true;
                }
        }
        return false;
    }

    /**
     * Checks if a player has bypass access for a certain attribute. See {@link DoorAttribute#getAdminPermission(DoorAttribute)}.
     * <p>
     * OPs (see {@link Player#isOp()} and non-players are considered to have bypass access to everything.
     *
     * @param player        The player to check (may be null).
     * @param doorAttribute The attribute to check.
     * @return True if the player exists and has bypass access to the provided attribute.
     */
    public boolean hasBypassAccess(final Player player, final DoorAttribute doorAttribute)
    {
        return player != null &&
            (player.isOp() ||
                (DoorAttribute.getAdminPermission(doorAttribute) != null &&
                    player.hasPermission(DoorAttribute.getAdminPermission(doorAttribute))));
    }

    public void delDoor(Player player, String doorName) {
        long doorUID = Long.parseLong(doorName);

        plugin.getCommander().removeDoor(doorUID);
        Util.messagePlayer(player, ChatColor.GREEN, "Door deleted!");
    }


    private String helpFormat(String command, String explanation) {
        String help = "";
        help += String.format(ChatColor.GREEN + "%s " + ChatColor.BLUE + "%s\n", command, explanation);
        return help;
    }

    private void showHelpInfo(CommandSender sender) {
        Player player = sender instanceof Player ? (Player) sender : null;
        String help = player == null ? "\n" : "";
        String commandPrefix = player == null ? "" : "/";
        help += ChatColor.GREEN + "====[ BigDoors Help ]====\n";
        help += helpFormat(commandPrefix + "BigDoors menu", "Opens BigDoors' GUI.");
        help += helpFormat(commandPrefix + "BigDoors version", "Get the version of this plugin.");
        help += helpFormat(commandPrefix + "BigDoors removeowner <door> <player>", "Add another owner for a door.");
        help += helpFormat(commandPrefix + "BigDoors addowner <door> <player> [permission]",
                           "Add another owner for a door.");
        if (player == null || player.hasPermission("bigdoors.admin.restart"))
            help += helpFormat("BigDoors restart", "Restart the plugin. Reinitializes almost everything.");
        if (player == null || player.hasPermission("bigdoors.admin.stopdoors"))
            help += helpFormat("BigDoors stop", "Forces all doors to finish instantly.");
        if (player == null || player.hasPermission("bigdoors.admin.pausedoors"))
            help += helpFormat("BigDoors pause", "Pauses all door movement until the command is run again.");
        if (player == null || player.hasPermission("bigdoors.admin.disabletoggle"))
        {
            help += helpFormat("BigDoors disabletoggle",
                               "Stops all active doors and prevents any doors from being toggled until this is enabled again.");
            help += helpFormat("BigDoors enabletoggle",
                               "Enables doors being toggled again. Has no effect if they are not currently disabled.");
        }
        if (player == null)
            help += helpFormat("BigDoors preparedatabaseforv2", "Prepares the database for v2 of BigDoors.");

        // Remove color codes for console.
        if (player == null)
            help = ChatColor.stripColor(help);
        plugin.getMyLogger().returnToSender(sender, Level.INFO, ChatColor.RED, help);
    }
}
