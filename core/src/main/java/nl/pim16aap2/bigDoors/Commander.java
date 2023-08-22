package nl.pim16aap2.bigDoors;

import nl.pim16aap2.bigDoors.events.DoorDeleteEvent;
import nl.pim16aap2.bigDoors.moveBlocks.BlockMover;
import nl.pim16aap2.bigDoors.storage.sqlite.SQLiteJDBCDriverConnection;
import nl.pim16aap2.bigDoors.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class Commander
{
    private final BigDoors plugin;
    private Map<Long, BlockMover> busyDoors;
    private HashMap<UUID, String> players;
    private boolean goOn = true;
    private boolean paused = false;
    private SQLiteJDBCDriverConnection db;
    private Messages messages;
    private static final DummyMover DUMMYMOVER = new DummyMover();

    public Commander(BigDoors plugin, SQLiteJDBCDriverConnection db)
    {
        this.plugin = plugin;
        this.db = db;
        busyDoors = new ConcurrentHashMap<>();
        messages = plugin.getMessages();
        players = new HashMap<>();
    }

    public void stopMovers(boolean onDisable) {
        for (BlockMover blockMover : busyDoors.values()) blockMover.cancel(onDisable);
    }

    public boolean isDoorBusy(long doorUID)
    {
        return busyDoors.containsKey(doorUID);
    }

    public boolean isDoorBusyRegisterIfNot(long doorUID) {
        return busyDoors.putIfAbsent(doorUID, DUMMYMOVER) != null;
    }

    // Set the availability of the door.
    public void setDoorAvailable(long doorUID)
    {
        busyDoors.remove(doorUID);
    }

    public void addBlockMover(BlockMover mover)
    {
        busyDoors.replace(mover.getDoorUID(), mover);
    }

    public Stream<BlockMover> getBlockMovers() {
        return busyDoors.values().stream().filter(BM -> !(BM instanceof DummyMover));
    }

    public boolean isPaused()
    {
        return paused;
    }
    
    public void togglePaused()
    {
        paused = !paused;
    }

    public boolean canGo()
    {
        return goOn;
    }

    // Change the canGo status of all doors.
    public void setCanGo(boolean bool)
    {
        goOn = bool;
    }

    // Print an ArrayList of doors to a player.
    public void printDoors(Player player, ArrayList<Door> doors)
    {
        for (Door door : doors)
            Util.messagePlayer(player, door.toSimpleString());
    }

    /**
     * See {@link #getDoor(String, Player, boolean)} with the bypass disabled.
     */
    public Door getDoor(String doorStr, @Nullable Player player)
    {
        return getDoor(doorStr, player, false);
    }

    /**
     * Gets the door with the given name/UID. If a player object is provided,
     * it will restrict itself to only doors (co-)owned by this player.
     * <p>
     * If bypass is true, door lookups using the door's UID also includes
     * doors that are not owned by the player.
     *
     * @param doorStr The ID of the door, represented either by its name or its UID.
     * @param player  The player for which to retrieve the door. May be null.
     * @param bypass  Whether the player has bypass access to the door.
     *                This has no effect if the player is null.
     * @return The door with the provided name, if exactly one could be found.
     */
    public Door getDoor(String doorStr, @Nullable Player player, final boolean bypass)
    {
        // First try converting the doorStr to a doorUID.
        try
        {
            long doorUID = Long.parseLong(doorStr);
            return db.getDoor(player == null ? null : player.getUniqueId(), doorUID, bypass);
        }
        // If it can't convert to a long, get all doors from the player with the
        // provided name.
        // If there is more than one, tell the player that they are going to have to
        // make a choice.
        catch (NumberFormatException e)
        {
            if (player == null)
                return null;
            ArrayList<Door> doors = new ArrayList<>();
            doors = db.getDoors(player.getUniqueId().toString(), doorStr);
            if (doors.size() == 1)
                return doors.get(0);

            if (doors.size() == 0)
                Util.messagePlayer(player, messages.getString("GENERAL.NoDoorsFound"));
            else
                Util.messagePlayer(player, messages.getString("GENERAL.MoreThan1DoorFound"));
            printDoors(player, doors);
            return null;
        }
    }

    public long addDoor(Door newDoor)
    {
        plugin.getPBCache().invalidate(Util.chunkHashFromLocation(newDoor.getPowerBlockLoc()));
        return db.insert(newDoor);
    }

    public long addDoor(Door newDoor, int permission) {
        if (newDoor.getPermission() != permission)
            newDoor.setPermission(permission);
        plugin.getPBCache().invalidate(Util.chunkHashFromLocation(newDoor.getPowerBlockLoc()));
        return db.insert(newDoor);
    }

    private void onDoorDelete(@Nullable Door door)
    {
        if (door == null)
            return;

        Bukkit.getPluginManager().callEvent(new DoorDeleteEvent(door));
        plugin.getPBCache().invalidate(door.getPowerBlockChunkHash());
    }

    public boolean removeDoor(Player player, long doorUID)
    {
        if (!hasPermissionForAction(player, doorUID, DoorAttribute.DELETE))
            return false;
        removeDoor(doorUID);
        return true;
    }

    public void removeDoor(long doorUID)
    {
        onDoorDelete(db.removeDoor(doorUID));
    }

    public void removeDoorsFromWorld(World world)
    {
        db.removeDoorsFromWorld(world).forEach(this::onDoorDelete);
    }

    // Returns the number of doors owner by a player and with a specific name, if
    // provided (can be null).
    public long countDoors(String playerUUID, @Nullable String doorName)
    {
        return db.countDoors(playerUUID, doorName);
    }

    // Returns an ArrayList of doors owner by a player and with a specific name, if
    // provided (can be null).
    public ArrayList<Door> getDoors(String playerUUID, @Nullable String name)
    {
        if (playerUUID == null && name == null)
            return new ArrayList<>();
        return playerUUID == null ? getDoors(name) : db.getDoors(playerUUID, name);
    }

    // Returns an Set of doors.
    // The Set is defined by door UID.
    // The door player is the creator of the door.
    public Set<Door> getDoors()
    {
        return db.getDoors();
    }

    // Returns an ArrayList of doors with a specific name.
    private ArrayList<Door> getDoors(String name)
    {
        return db.getDoors(name);
    }

    // Returns an ArrayList of doors that exist in a specific world.
    public ArrayList<Door> getDoorsInWorld(World world)
    {
        return db.getDoorsInWorld(world);
    }

    // Returns an ArrayList of doors owner by a player and with a specific name, if
    // provided (can be null).
    public ArrayList<Door> getDoorsInRange(String playerUUID, @Nullable String name, int start, int end)
    {
        return db.getDoors(playerUUID, name, start, end);
    }

    public UUID playerUUIDFromName(String playerName)
    {
        UUID uuid = players.entrySet().stream().filter(e -> e.getValue().equals(playerName)).map(Map.Entry::getKey)
            .findFirst().orElse(null);
        if (uuid != null)
            return uuid;

        uuid = db.getUUIDFromName(playerName);
        if (uuid != null)
            return uuid;

        uuid = Util.playerUUIDFromString(playerName);
        if (uuid != null)
            updatePlayer(uuid, playerName);
        return uuid;
    }

    public String playerNameFromUUID(UUID playerUUID)
    {
        // Try from HashSet first; it's the fastest.
        if (players.containsKey(playerUUID))
            return players.get(playerUUID);
        // Then try to get it from an online player.
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null)
            return player.getName();
        // First try to get the player name from the database.
        String name = db.getPlayerName(playerUUID);
        if (name != null)
            return name;
        // As a last resort, try to get the name from an offline player. This is slow
        // af, so last resort.
        name = Util.nameFromUUID(playerUUID);
        // Then place the UUID/String combo in the db. Need moar data!
        updatePlayer(playerUUID, name);
        return name;
    }

    public void updatePlayer(UUID uuid, String playerName)
    {
        db.updatePlayerName(uuid, playerName);
        players.put(uuid, playerName);
    }

    public void updatePlayer(Player player)
    {
        updatePlayer(player.getUniqueId(), player.getName());
    }

    public void removePlayer(Player player)
    {
        players.remove(player.getUniqueId());
    }

    // Get a door with a specific doorUID.
    public Door getDoor(@Nullable UUID playerUUID, long doorUID)
    {
        return getDoor(playerUUID, doorUID, false);
    }

    public Door getDoor(@Nullable UUID playerUUID, long doorUID, final boolean bypass)
    {
        return db.getDoor(playerUUID, doorUID, bypass);
    }

    public boolean hasPermissionNodeForAction(Player player, DoorAttribute atr)
    {
        if (atr == null)
            return false;
        return player.hasPermission(DoorAttribute.getUserPermission(atr)) ||
               (DoorAttribute.getAdminPermission(atr) != null && player.hasPermission(DoorAttribute.getAdminPermission(atr)));
    }

    public boolean hasPermissionForAction(Player player, long doorUID, DoorAttribute atr)
    {
        return hasPermissionForAction(player, doorUID, atr, true);
    }

    public boolean hasPermissionForAction(Player player, long doorUID, DoorAttribute atr, boolean printMessage)
    {
        if (player.isOp() || hasPermissionNodeForAction(player, atr))
            return true;

        int playerPermission = getPermission(player.getUniqueId().toString(), doorUID);
        boolean hasPermission = playerPermission >= 0 && playerPermission <= DoorAttribute.getPermissionLevel(atr);
        if (!hasPermission && printMessage)
            Util.messagePlayer(player, plugin.getMessages().getString("GENERAL.NoPermissionForAction"));
        return hasPermission;
    }

    // Get the permission of a player on a door.
    public int getPermission(String playerUUID, long doorUID)
    {
        return db.getPermission(playerUUID, doorUID);
    }

    // Update the coordinates of a given door.
    public void updateDoorCoords(long doorUID, boolean isOpen, int blockXMin, int blockYMin, int blockZMin,
                                 int blockXMax, int blockYMax, int blockZMax)
    {
        db.updateDoorCoords(doorUID, isOpen, blockXMin, blockYMin, blockZMin, blockXMax, blockYMax, blockZMax, null);
    }

    // Update the coordinates of a given door.
    public void updateDoorCoords(long doorUID, boolean isOpen, int blockXMin, int blockYMin, int blockZMin,
                                 int blockXMax, int blockYMax, int blockZMax, DoorDirection newEngSide) {
        db.updateDoorCoords(doorUID, isOpen, blockXMin, blockYMin, blockZMin, blockXMax, blockYMax, blockZMax,
                newEngSide);
    }


    public void updateDoorOpenDirection(long doorUID, RotateDirection openDir)
    {
        if (openDir == null)
            return;
        db.updateDoorOpenDirection(doorUID, openDir);
    }

    public void updateDoorAutoClose(long doorUID, int autoClose)
    {
        db.updateDoorAutoClose(doorUID, autoClose);
    }

    public void updateDoorNotify(long doorUID, boolean notify)
    {
        db.updateNotify(doorUID, notify);
    }

    public void updateDoorBlocksToMove(long doorID, int blocksToMove)
    {
        db.updateDoorBlocksToMove(doorID, blocksToMove);
    }

    // Change the "locked" status of a door.
    public void setLock(long doorUID, boolean newLockStatus)
    {
        db.setLock(doorUID, newLockStatus);
    }

    // Get a door from the x,y,z coordinates of its power block.
    public Door doorFromPowerBlockLoc(Location loc)
    {
        long chunkHash = Util.chunkHashFromLocation(loc);
        HashMap<Long, Long> powerBlockData = plugin.getPBCache().get(chunkHash);

        if (powerBlockData == null)
        {
            powerBlockData = db.getPowerBlockData(chunkHash);
            plugin.getPBCache().put(chunkHash, powerBlockData);
        }

        Long doorUID = powerBlockData.get(Util.locationHash(loc));
        return doorUID == null ? null : db.getDoor(null, doorUID);
    }

    public void recalculatePowerBlockHashes()
    {
        db.recalculatePowerBlockHashes();
    }

    // Change the location of a powerblock.
    public void updatePowerBlockLoc(long doorUID, Location loc)
    {
        plugin.getPBCache().invalidate(db.getDoor(null, doorUID).getPowerBlockChunkHash());
        db.updateDoorPowerBlockLoc(doorUID, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getWorld().getUID());
        plugin.getPBCache().invalidate(Util.chunkHashFromLocation(loc));
    }

    public boolean isPowerBlockLocationValid(Location loc)
    {
        return db.isPowerBlockLocationEmpty(loc);
    }

    private static final class DummyMover extends BlockMover
    {
        private DummyMover()
        {
            super(BigDoors.get(), null, false);
        }

        @Override
        public synchronized void cancel(boolean onDisable)
        {
        }

        @Override
        public void putBlocks(boolean onDisable)
        {
        }

        @Override
        public long getDoorUID()
        {
            return -1;
        }

        @Override
        public Door getDoor()
        {
            return null;
        }

    }
}
