package nl.pim16aap2.bigDoors.waitForCommand;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import nl.pim16aap2.bigDoors.BigDoors;
import nl.pim16aap2.bigDoors.util.DoorAttribute;
import nl.pim16aap2.bigDoors.util.Util;

public class WaitForSetBlocksToMove extends WaitForCommand
{
    private long doorUID;

    public WaitForSetBlocksToMove(BigDoors plugin, Player player, long doorUID)
    {
        super(plugin);
        this.player = player;
        command = "setblockstomove";
        this.doorUID = doorUID;
        Util.messagePlayer(player, plugin.getMessages().getString("COMMAND.SetBlocksToMove.Init"));
        plugin.addCommandWaiter(this);
    }

    @Override
    public boolean executeCommand(String[] args)
    {
        if (args.length == 1)
            try
            {
                int blocksToMove = Integer.parseInt(args[0]);

                if (blocksToMove > BigDoors.get().getConfigLoader().getMaxBlocksToMove())
                {
                    player.sendMessage(ChatColor.RED
                        + BigDoors.get().getMessages().getString("GENERAL.InvalidBlocksToMoveRange"));
                    return true;
                }

                plugin.getCommandHandler().setDoorBlocksToMove(doorUID, blocksToMove);
                plugin.removeCommandWaiter(this);
                if (blocksToMove > 0)
                    Util.messagePlayer(player, plugin.getMessages().getString("COMMAND.SetBlocksToMove.Success")
                        + blocksToMove);
                else
                    Util.messagePlayer(player, plugin.getMessages().getString("COMMAND.SetBlocksToMove.Disabled"));
                isFinished = true;
                abort();
                return true;
            }
            catch (Exception e)
            {
                Util.messagePlayer(player, plugin.getMessages().getString("GENERAL.InvalidInput.Integer"));
            }
        abort();
        return false;
    }
}
