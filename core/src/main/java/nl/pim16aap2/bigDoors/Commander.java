package nl.pim16aap2.bigDoors;

import nl.pim16aap2.bigDoors.events.DoorDeleteEvent;
import nl.pim16aap2.bigDoors.moveBlocks.BlockMover;
import nl.pim16aap2.bigDoors.storage.sqlite.SQLiteJDBCDriverConnection;
import nl.pim16aap2.bigDoors.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

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

    public void setCanGo(boolean bool)
    {
        goOn = bool;
    }

    public void printDoors(Player player, ArrayList<Door> doors) {
        for (Door door : doors)
            Util.messagePlayer(player, door.toSimpleString());
    }

    public Door getDoor(long doorStr) {
        return db.getDoor(doorStr);
    }

    public long addDoor(Door newDoor) {
        plugin.getPBCache().invalidate(Util.chunkHashFromLocation(newDoor.getPowerBlockLoc()));
        return db.insert(newDoor);
    }

    private void onDoorDelete(Door door) {
        Bukkit.getPluginManager().callEvent(new DoorDeleteEvent(door));
        plugin.getPBCache().invalidate(door.getPowerBlockChunkHash());
    }

    public void removeDoor(long doorUID) {
        onDoorDelete(db.removeDoor(doorUID));
    }

    public Set<Door> getDoors() {
        return db.getDoors();
    }

    public ArrayList<Door> getDoors(String name) {
        return db.getDoors(name);
    }

    public boolean hasPermissionNodeForAction(Player player, DoorAttribute atr) {
        if (atr == null)
            return false;
        return player.hasPermission(DoorAttribute.getUserPermission(atr)) ||
               (DoorAttribute.getAdminPermission(atr) != null && player.hasPermission(DoorAttribute.getAdminPermission(atr)));
    }

    public void updateDoorCoords(long doorUID, boolean isOpen, int blockXMin, int blockYMin, int blockZMin, int blockXMax, int blockYMax, int blockZMax) {
        db.updateDoorCoords(doorUID, isOpen, blockXMin, blockYMin, blockZMin, blockXMax, blockYMax, blockZMax, null);
    }

    public void updateDoorCoords(long doorUID, boolean isOpen, int blockXMin, int blockYMin, int blockZMin,
                                 int blockXMax, int blockYMax, int blockZMax, DoorDirection newEngSide) {
        db.updateDoorCoords(doorUID, isOpen, blockXMin, blockYMin, blockZMin, blockXMax, blockYMax, blockZMax,
                newEngSide);
    }


    public void updateDoorOpenDirection(long doorUID, RotateDirection openDir) {
        if (openDir == null)
            return;
        db.updateDoorOpenDirection(doorUID, openDir);
    }

    public void updateDoorAutoClose(long doorUID, int autoClose) {
        db.updateDoorAutoClose(doorUID, autoClose);
    }

    public void updateDoorNotify(long doorUID, boolean notify) {
        db.updateNotify(doorUID, notify);
    }

    public void updateDoorBlocksToMove(long doorID, int blocksToMove) {
        db.updateDoorBlocksToMove(doorID, blocksToMove);
    }

    public void setLock(long doorUID, boolean newLockStatus) {
        db.setLock(doorUID, newLockStatus);
    }

    public Door doorFromPowerBlockLoc(Location loc) {
        long chunkHash = Util.chunkHashFromLocation(loc);
        HashMap<Long, Long> powerBlockData = plugin.getPBCache().get(chunkHash);

        if (powerBlockData == null)
        {
            powerBlockData = db.getPowerBlockData(chunkHash);
            plugin.getPBCache().put(chunkHash, powerBlockData);
        }

        Long doorUID = powerBlockData.get(Util.locationHash(loc));
        return doorUID == null ? null : db.getDoor(doorUID);
    }

    public void recalculatePowerBlockHashes() {
        db.recalculatePowerBlockHashes();
    }

    public void updatePowerBlockLoc(long doorUID, Location loc) {
        plugin.getPBCache().invalidate(db.getDoor(doorUID).getPowerBlockChunkHash());
        db.updateDoorPowerBlockLoc(doorUID, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getWorld().getUID());
        plugin.getPBCache().invalidate(Util.chunkHashFromLocation(loc));
    }

    public boolean isPowerBlockLocationValid(Location loc) {
        return db.isPowerBlockLocationEmpty(loc);
    }

    private static final class DummyMover extends BlockMover {
        private DummyMover() {
            super(BigDoors.get(), null, false);
        }

        @Override
        public synchronized void cancel(boolean onDisable) {
        }

        @Override
        public void putBlocks(boolean onDisable) {
        }

        @Override
        public long getDoorUID() {
            return -1;
        }

        @Override
        public Door getDoor() {
            return null;
        }

    }
}
