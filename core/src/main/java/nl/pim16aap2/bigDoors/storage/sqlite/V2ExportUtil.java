package nl.pim16aap2.bigDoors.storage.sqlite;

import nl.pim16aap2.bigDoors.BigDoors;
import nl.pim16aap2.bigDoors.util.DoorDirection;
import nl.pim16aap2.bigDoors.util.DoorType;
import nl.pim16aap2.bigDoors.util.RotateDirection;
import nl.pim16aap2.bigDoors.util.Vector3D;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class V2ExportUtil
{
    private final BigDoors plugin;
    private final long seqPlayers;
    private final long seqDoors;

    private final Map<Long, Long> remappedDoors = new HashMap<>();
    private final Map<Long, Long> remappedPlayers = new HashMap<>();

    V2ExportUtil(BigDoors plugin, long seqPlayers, long seqDoors)
    {
        this.plugin = plugin;
        this.seqPlayers = seqPlayers;
        this.seqDoors = seqDoors;
    }

    public void export(Connection connV1, Connection connV2)
    {
        try
        {
            createV2Tables(connV2);
            exportDoors(connV1, connV2);
            exportPlayers(connV1, connV2);
            exportUnion(connV1, connV2);

            connV2.prepareStatement("PRAGMA user_version = 100;").execute();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to export database to v2!", e);
        }
    }

    private void exportUnion(Connection connV1, Connection connV2)
        throws SQLException
    {
        final String insertStr =
            "INSERT INTO StructureOwnerPlayer (permission, playerID, structureUID) VALUES (?,?,?);";

        try (ResultSet rs = connV1.prepareStatement("SELECT * FROM sqlUnion").executeQuery())
        {
            while (rs.next())
            {
                final @Nullable Long playerID =
                    IndexRemapper.findRemappedId(plugin, remappedPlayers, rs.getLong("playerID"), "Player");
                final @Nullable Long doorUid =
                    IndexRemapper.findRemappedId(plugin, remappedDoors, rs.getLong("doorUID"), "Door");

                if (playerID == null || doorUid == null)
                    continue;

                final int permission = rs.getInt("permission");
                try (PreparedStatement ps = connV2.prepareStatement(insertStr))
                {
                    int idx = 0;

                    ps.setInt(++idx, permission * 100);
                    ps.setLong(++idx, playerID);
                    ps.setLong(++idx, doorUid);

                    ps.executeUpdate();
                }
            }
        }
    }

    private void exportPlayers(Connection connV1, Connection connV2)
        throws SQLException
    {
        final String insertStr =
            "INSERT INTO Player " +
            "(id, playerUUID, playerName, sizeLimit, countLimit, permissions) " +
            "VALUES(?, ?, ?, ?, ?, ?);";

        final IndexRemapper remapper = new IndexRemapper(plugin, seqPlayers, "player", remappedPlayers);

        try (ResultSet rs = connV1.prepareStatement("SELECT * FROM players;").executeQuery())
        {
            while (rs.next())
            {
                final String uuidStr = rs.getString("playerUUID");
                final OfflinePlayer player = Bukkit.getPlayer(UUID.fromString(uuidStr));

                if (player == null)
                {
                    plugin.getMyLogger().severe("Failed to export player: " + uuidStr);
                }
                final long uid = remapper.getRemappedId(rs.getInt("id"));

                try (PreparedStatement ps = connV2.prepareStatement(insertStr))
                {
                    int idx = 0;

                    ps.setLong(++idx, uid);
                    ps.setString(++idx, uuidStr);
                    ps.setString(++idx, rs.getString("playerName"));
                    ps.setInt(++idx, plugin.getConfigLoader().maxDoorSize());
                    ps.setInt(++idx, plugin.getConfigLoader().maxdoorCount());
                    ps.setLong(++idx, 0L);

                    ps.executeUpdate();
                }
            }
        }
    }

    private void exportDoors(Connection connV1, Connection connV2)
        throws SQLException
    {
        final String insertStr =
            "INSERT INTO Structure " +
                "(id, name, world, xMin, yMin, zMin, xMax, yMax, zMax, rotationPointX, rotationPointY, rotationPointZ, " +
                "rotationPointChunkId, powerBlockX, powerBlockY, powerBlockZ, powerBlockChunkId, openDirection, " +
                "bitflag, type, typeVersion, typeData) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

        final IndexRemapper remapper = new IndexRemapper(plugin, seqDoors, "Door", remappedDoors);

        try (ResultSet rs = connV1.prepareStatement("SELECT * FROM doors;").executeQuery())
        {
            while (rs.next())
            {
                final DoorType doorType = DoorType.valueOf(rs.getInt("type"));
                final long originalUid = rs.getLong("id");
                if (doorType == null)
                {
                    plugin.getMyLogger().severe("Failed to export door '" + originalUid + "': Type does not exist");
                    return;
                }

                final World world = Bukkit.getWorld(UUID.fromString(rs.getString("world")));
                if (world == null)
                {
                    plugin.getMyLogger().severe("Failed to export door '" + originalUid + "': World does not exist");
                    continue;
                }

                final String name = rs.getString("name");

                final long uid = remapper.getRemappedId(originalUid);

                try (PreparedStatement insert = connV2.prepareStatement(insertStr))
                {
                    int idx = 0;

                    insert.setLong(++idx, uid);
                    insert.setString(++idx, name);
                    insert.setString(++idx, world.getName());

                    Vector3D min = new Vector3D(rs.getInt("xMin"), rs.getInt("yMin"), rs.getInt("zMin"));
                    Vector3D max = new Vector3D(rs.getInt("xMax"), rs.getInt("yMax"), rs.getInt("zMax"));
                    Vector3D eng = new Vector3D(rs.getInt("engineX"), rs.getInt("engineY"), rs.getInt("engineZ"));

                    insert.setInt(++idx, min.getX());
                    insert.setInt(++idx, min.getY());
                    insert.setInt(++idx, min.getZ());

                    insert.setInt(++idx, max.getX());
                    insert.setInt(++idx, max.getY());
                    insert.setInt(++idx, max.getZ());

                    insert.setInt(++idx, eng.getX());
                    insert.setInt(++idx, eng.getY());
                    insert.setInt(++idx, eng.getZ());

                    insert.setLong(++idx, getV2ChunkId(eng.getX(), eng.getZ()));

                    insert.setInt(++idx, rs.getInt("powerBlockX"));
                    insert.setInt(++idx, rs.getInt("powerBlockY"));
                    insert.setInt(++idx, rs.getInt("powerBlockZ"));
                    insert.setLong(++idx, getV2ChunkId(rs.getInt("powerBlockX"), rs.getInt("powerBlockZ")));

                    final int engineSide = rs.getInt("engineSide");
                    final int currentOpenDirection = rs.getInt("openDirection");
                    insert.setInt(++idx, remapOpenDirection(uid, currentOpenDirection, doorType, min, max, engineSide));

                    int flag = 0;
                    if (rs.getBoolean("isOpen"))
                        flag |= 1;
                    if (rs.getBoolean("isLocked"))
                        flag |= 2;
                    insert.setLong(++idx, flag);

                    insert.setString(++idx, getV2TypeName(doorType));
                    insert.setInt(++idx, 1);
                    insert.setString(++idx, getV2TypeData(doorType, rs.getInt("blocksToMove")));

                    insert.executeUpdate();
                }
            }
            plugin.getMyLogger().info("All doors have been processed! Onto the next step!");
        }
    }

    private int remapOpenDirection(
        long uid, int currentOpenDirection, DoorType doorType, Vector3D min, Vector3D max, int engineSide)
    {
        if (doorType == DoorType.DOOR)
        {
            if (currentOpenDirection == RotateDirection.CLOCKWISE.getVal() ||
                currentOpenDirection == RotateDirection.COUNTERCLOCKWISE.getVal())
                return currentOpenDirection;
            return RotateDirection.CLOCKWISE.getVal();
        }

        if (doorType == DoorType.DRAWBRIDGE)
        {
            if (currentOpenDirection == RotateDirection.NORTH.getVal() ||
                currentOpenDirection == RotateDirection.EAST.getVal() ||
                currentOpenDirection == RotateDirection.SOUTH.getVal() ||
                currentOpenDirection == RotateDirection.WEST.getVal())
                return currentOpenDirection;
            return findNewDrawbridgeOpenDirection(uid, min, max, engineSide);
        }

        if (doorType == DoorType.PORTCULLIS)
        {
            if (currentOpenDirection == RotateDirection.UP.getVal() ||
                currentOpenDirection == RotateDirection.DOWN.getVal())
                return currentOpenDirection;
            return RotateDirection.UP.getVal();
        }

        if (doorType == DoorType.SLIDINGDOOR)
        {
            if (currentOpenDirection == RotateDirection.NORTH.getVal() ||
                currentOpenDirection == RotateDirection.EAST.getVal() ||
                currentOpenDirection == RotateDirection.SOUTH.getVal() ||
                currentOpenDirection == RotateDirection.WEST.getVal())
                return currentOpenDirection;
            return RotateDirection.NORTH.getVal();
        }

        plugin.getMyLogger().severe(
            "Failed to remap open direction of door " + uid + " (remapped)! Type: " +
                doorType.name() + ", old open direction: " + currentOpenDirection);
        return RotateDirection.NONE.getVal();
    }

    private int findNewDrawbridgeOpenDirection(long uid, Vector3D min, Vector3D max, int engineSide)
    {
        final boolean isUp = min.getY() < max.getY();
        if (isUp)
        {
            final boolean alongNorthSouthAxis = (max.getZ() - min.getZ()) > 0;
            if (alongNorthSouthAxis)
                return RotateDirection.EAST.getVal();
            return RotateDirection.NORTH.getVal();
        }
        else
        {
            if (engineSide == DoorDirection.NORTH.getVal())
                return RotateDirection.SOUTH.getVal();
            else if (engineSide == DoorDirection.EAST.getVal())
                return RotateDirection.WEST.getVal();
            else if (engineSide == DoorDirection.SOUTH.getVal())
                return RotateDirection.NORTH.getVal();
            else if (engineSide == DoorDirection.WEST.getVal())
                return RotateDirection.EAST.getVal();
        }

        plugin.getMyLogger().severe(
            "Failed to find new open direction of flat drawbridge " + uid + " (remapped)! " +
                "engine side: " + engineSide + "!");
        return RotateDirection.NONE.getVal();
    }

    private long getV2ChunkId(int x, int z)
    {
        final int chunkX = x >> 4;
        final int chunkZ = z >> 4;
        return (((long) chunkX) << 32) | (chunkZ & 0xffffffffL);
    }

    private String getV2TypeData(DoorType doorType, int blocksToMove)
    {
        switch (doorType)
        {
            case DOOR:
            case DRAWBRIDGE:
                return "{\"quarterCircles\":1}";
            case PORTCULLIS:
            case SLIDINGDOOR:
                return "{\"blocksToMove\":" + blocksToMove + "}";
        }
        throw new IllegalArgumentException("Received unexpected door type: " + doorType);
    }

    private String getV2TypeName(DoorType doorType)
    {
        switch (doorType)
        {
            case DOOR:
                return "animatedarchitecture:bigdoor";
            case DRAWBRIDGE:
                return "animatedarchitecture:drawbridge";
            case PORTCULLIS:
                return "animatedarchitecture:portcullis";
            case SLIDINGDOOR:
                return "animatedarchitecture:slidingdoor";
        }
        throw new IllegalArgumentException("Received unexpected door type: " + doorType);
    }

    private void createV2Tables(Connection conn)
        throws SQLException
    {
        conn.prepareStatement(
            "CREATE TABLE IF NOT EXISTS Structure " +
                "(id                   INTEGER    PRIMARY KEY AUTOINCREMENT, " +
                "name                  TEXT       NOT NULL, " +
                "world                 TEXT       NOT NULL, " +
                "xMin                  INTEGER    NOT NULL, " +
                "yMin                  INTEGER    NOT NULL, " +
                "zMin                  INTEGER    NOT NULL, " +
                "xMax                  INTEGER    NOT NULL, " +
                "yMax                  INTEGER    NOT NULL, " +
                "zMax                  INTEGER    NOT NULL, " +
                "rotationPointX        INTEGER    NOT NULL, " +
                "rotationPointY        INTEGER    NOT NULL, " +
                "rotationPointZ        INTEGER    NOT NULL, " +
                "rotationPointChunkId  INTEGER    NOT NULL, " +
                "powerBlockX           INTEGER    NOT NULL, " +
                "powerBlockY           INTEGER    NOT NULL, " +
                "powerBlockZ           INTEGER    NOT NULL, " +
                "powerBlockChunkId     INTEGER    NOT NULL, " +
                "openDirection         INTEGER    NOT NULL, " +
                "type                  TEXT       NOT NULL, " +
                "typeVersion           INTEGER    NOT NULL, " +
                "typeData              TEXT       NOT NULL, " +
                "bitflag               INTEGER    NOT NULL);"
        ).executeUpdate();
        conn.prepareStatement(
            "INSERT OR IGNORE INTO SQLITE_SEQUENCE (name, seq) VALUES ('Structure', 10);").executeUpdate();

        conn.prepareStatement(
            "CREATE TABLE IF NOT EXISTS Player " +
                "(id            INTEGER    PRIMARY KEY AUTOINCREMENT, " +
                "playerUUID     TEXT       NOT NULL, " +
                "playerName     TEXT       NOT NULL, " +
                "sizeLimit      INTEGER    NOT NULL, " +
                "countLimit     INTEGER    NOT NULL, " +
                "permissions    INTEGER    NOT NULL, " +
                "unique(playerUUID));"
        ).executeUpdate();
        conn.prepareStatement(
            "INSERT OR IGNORE INTO SQLITE_SEQUENCE (name, seq) VALUES ('Player', 10);").executeUpdate();

        conn.prepareStatement(
            "CREATE TABLE IF NOT EXISTS StructureOwnerPlayer " +
                "(id           INTEGER    PRIMARY KEY AUTOINCREMENT, " +
                "permission    INTEGER    NOT NULL, " +
                "playerID      REFERENCES Player(id)   ON UPDATE CASCADE ON DELETE CASCADE, " +
                "structureUID  REFERENCES Structure(id) ON UPDATE CASCADE ON DELETE CASCADE, " +
                "unique (playerID, structureUID));"
        ).executeUpdate();
        conn.prepareStatement(
            "INSERT OR IGNORE INTO SQLITE_SEQUENCE (name, seq) VALUES ('StructureOwnerPlayer', 10);").executeUpdate();
    }

    private static final class IndexRemapper
    {
        private final BigDoors plugin;
        private final long seq;
        private final String typeName;
        private final Map<Long, Long> map;

        private int offset = 0;

        IndexRemapper(BigDoors plugin, long seq, String typeName, Map<Long, Long> map)
        {
            this.plugin = plugin;
            this.seq = seq;
            this.typeName = typeName;
            this.map = map;
        }

        long getRemappedId(long input)
        {
            // v2 starts counting at 11; not 1.
            if (input >= 11)
                return input;

            final long output = seq + (++offset);
            plugin.getMyLogger().severe("Changed UID of " + typeName + " " + input + " to new UID " + output);
            map.put(input, output);
            return output;
        }

        static @Nullable Long findRemappedId(BigDoors plugin, Map<Long, Long> map, long input, String typeName)
        {
            if (input > 10)
                return input;
            final @Nullable Long result = map.get(input);
            if (result == null)
                plugin.getMyLogger().severe("Could not find remapped ID for " + typeName + " : " + input);
            return result;
        }
    }
}
