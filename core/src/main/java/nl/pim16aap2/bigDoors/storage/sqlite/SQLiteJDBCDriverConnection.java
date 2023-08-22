package nl.pim16aap2.bigDoors.storage.sqlite;

import nl.pim16aap2.bigDoors.BigDoors;
import nl.pim16aap2.bigDoors.Door;
import nl.pim16aap2.bigDoors.util.DoorDirection;
import nl.pim16aap2.bigDoors.util.DoorType;
import nl.pim16aap2.bigDoors.util.RotateDirection;
import nl.pim16aap2.bigDoors.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SQLiteJDBCDriverConnection {
    private static final String DRIVER = "org.sqlite.JDBC";
    private static final int DOOR_ID = 1;
    private static final int DOOR_NAME = 2;
    private static final int DOOR_WORLD = 3;
    private static final int DOOR_OPEN = 4;
    private static final int DOOR_MIN_X = 5;
    private static final int DOOR_MIN_Y = 6;
    private static final int DOOR_MIN_Z = 7;
    private static final int DOOR_MAX_X = 8;
    private static final int DOOR_MAX_Y = 9;
    private static final int DOOR_MAX_Z = 10;
    private static final int DOOR_ENG_X = 11;
    private static final int DOOR_ENG_Y = 12;
    private static final int DOOR_ENG_Z = 13;
    private static final int DOOR_LOCKED = 14;
    private static final int DOOR_TYPE = 15;
    private static final int DOOR_ENG_SIDE = 16;
    private static final int DOOR_POWER_X = 17;
    private static final int DOOR_POWER_Y = 18;
    private static final int DOOR_POWER_Z = 19;
    private static final int DOOR_OPEN_DIR = 20;
    private static final int DOOR_AUTO_CLOSE = 21;
    private static final int DOOR_CHUNK_HASH = 22;
    private static final int DOOR_BLOCKS_TO_MOVE = 23;
    private static final int DOOR_NOTIFY = 24;
    private final BigDoors plugin;
    private final File dbFile;
    private final String url;
    private boolean enabled = true;
    private boolean validVersion = true;
    private final AtomicBoolean locked = new AtomicBoolean(false);

    public SQLiteJDBCDriverConnection(final BigDoors plugin, String dbName) {
        this.plugin = plugin;
        dbFile = new File(plugin.getDataFolder(), dbName);
        url = getConnectionUrl(dbFile);
        init();
    }

    private static String getConnectionUrl(File file) {
        return "jdbc:sqlite:" + file;
    }

    static void optimizeDatabase(Connection conn)
            throws SQLException {
        conn.prepareStatement("VACUUM;").execute();
        conn.prepareStatement("PRAGMA integrity_check(1);").execute();
        conn.prepareStatement("PRAGMA foreign_key_check;").execute();
        conn.prepareStatement("PRAGMA analysis_limit=0; PRAGMA optimize;").execute();
    }

    private Connection getConnection() {
        if (locked.get()) {
            plugin.getMyLogger()
                    .logMessage("Database locked! Please try again later! Please contact pim16aap2 if the issue persists.",
                            true, false);
            return null;
        }
        if (!validVersion) {
            plugin.getMyLogger().logMessage("Database disabled! Reason: Version too high! Please update the plugin!",
                    true, false);
            return null;
        }
        if (!enabled) {
            plugin.getMyLogger()
                    .logMessage("Database disabled! This probably means an upgrade failed! Please contact pim16aap2.", true,
                            false);
            return null;
        }

        Connection conn = null;
        try {
            Class.forName(DRIVER);
            conn = DriverManager.getConnection(url);
            conn.createStatement().execute("PRAGMA foreign_keys=ON");
        } catch (SQLException | NullPointerException ex) {
            plugin.getMyLogger().logMessage("53: Failed to open connection!", true, false);
        } catch (ClassNotFoundException e) {
            plugin.getMyLogger().logMessage("57: Failed to open connection: CLass not found!!", true, false);
        }
        return conn;
    }

    private void init() {
        if (!dbFile.exists())
            try {
                dbFile.createNewFile();
                plugin.getMyLogger().logMessageToLogFile("New file created at " + dbFile);
            } catch (IOException e) {
                plugin.getMyLogger().logMessageToLogFile("File write error: " + dbFile);
            }

        try (Connection conn = getConnection()) {
            if (!conn.getMetaData().getTables(null, null, "doors", new String[]{"TABLE"}).next()) {
                Statement stmt1 = conn.createStatement();
                String sql1 = "CREATE TABLE IF NOT EXISTS doors "
                        + "(id            INTEGER    PRIMARY KEY autoincrement, "
                        + " name          TEXT       NOT NULL, "
                        + " world         TEXT       NOT NULL, "
                        + " isOpen        INTEGER    NOT NULL, "
                        + " xMin          INTEGER    NOT NULL, "
                        + " yMin          INTEGER    NOT NULL, "
                        + " zMin          INTEGER    NOT NULL, "
                        + " xMax          INTEGER    NOT NULL, "
                        + " yMax          INTEGER    NOT NULL, "
                        + " zMax          INTEGER    NOT NULL, "
                        + " engineX       INTEGER    NOT NULL, "
                        + " engineY       INTEGER    NOT NULL, "
                        + " engineZ       INTEGER    NOT NULL, "
                        + " isLocked      INTEGER    NOT NULL, "
                        + " type          INTEGER    NOT NULL, "
                        + " engineSide    INTEGER    NOT NULL, "
                        + " powerBlockX   INTEGER    NOT NULL, "
                        + " powerBlockY   INTEGER    NOT NULL, "
                        + " powerBlockZ   INTEGER    NOT NULL, "
                        + " openDirection INTEGER    NOT NULL, "
                        + " autoClose     INTEGER    NOT NULL, "
                        + " chunkHash     INTEGER    NOT NULL, "
                        + " blocksToMove  INTEGER    NOT NULL, "
                        + " notify        INTEGER    NOT NULL) ";
                stmt1.executeUpdate(sql1);
                stmt1.close();
            }
        } catch (SQLException | NullPointerException e) {
            logMessage("203", e);
        }
    }

    public Map<DoorType, Integer> getDatabaseStatistics() {
        Map<DoorType, Integer> stats = new EnumMap<>(DoorType.class);

        try (Connection conn = getConnection();
             PreparedStatement ps = conn
                     .prepareStatement("SELECT type, COUNT(type) AS count FROM doors GROUP BY type ORDER BY type;");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                DoorType type = DoorType.valueOf(rs.getInt("type"));
                if (type == null)
                    continue;

                int count = rs.getInt("count");
                stats.put(type, count);
            }
        } catch (SQLException | NullPointerException e) {
            logMessage("747", e);
        }

        return stats;
    }

    private Door newDoorFromRS(ResultSet rs, long doorUID) {
        try {
            World world = Bukkit.getServer().getWorld(UUID.fromString(rs.getString(DOOR_WORLD)));
            Location min = new Location(world, rs.getInt(DOOR_MIN_X), rs.getInt(DOOR_MIN_Y), rs.getInt(DOOR_MIN_Z));
            Location max = new Location(world, rs.getInt(DOOR_MAX_X), rs.getInt(DOOR_MAX_Y), rs.getInt(DOOR_MAX_Z));
            Location engine = new Location(world, rs.getInt(DOOR_ENG_X), rs.getInt(DOOR_ENG_Y), rs.getInt(DOOR_ENG_Z));
            Location powerB = new Location(world, rs.getInt(DOOR_POWER_X), rs.getInt(DOOR_POWER_Y),
                    rs.getInt(DOOR_POWER_Z));

            Door door = new Door(world, min, max, engine, rs.getString(DOOR_NAME),
                    (rs.getInt(DOOR_OPEN) == 1), doorUID,
                    (rs.getInt(DOOR_LOCKED) == 1),
                    DoorType.valueOf(rs.getInt(DOOR_TYPE)),
                    DoorDirection.valueOf(rs.getInt(DOOR_ENG_SIDE)), powerB,
                    RotateDirection.valueOf(rs.getInt(DOOR_OPEN_DIR)), rs.getInt(DOOR_AUTO_CLOSE), rs.getBoolean(DOOR_NOTIFY));

            door.setBlocksToMove(rs.getInt(DOOR_BLOCKS_TO_MOVE));
            return door;
        } catch (SQLException | NullPointerException e) {
            logMessage("282", e);
            return null;
        }
    }

    public @Nullable Door removeDoor(long doorID) {
        final @Nullable Door door = getDoor(doorID);
        if (door == null)
            return null;

        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            PreparedStatement ps0 = conn.prepareStatement("SELECT chunkHash FROM doors WHERE id = ?;");
            ps0.setLong(1, doorID);
            ResultSet rs0 = ps0.executeQuery();
            if (rs0.next()) {
                String deleteDoor = "DELETE FROM doors WHERE id = '" + doorID + "';";
                PreparedStatement ps = conn.prepareStatement(deleteDoor);
                ps.executeUpdate();
                ps.close();
            }

            ps0.close();
            rs0.close();
            conn.commit();
        } catch (SQLException | NullPointerException e) {
            plugin.getMyLogger().logMessageToLogFile("271: " + Util.throwableToString(e));
            return null;
        } finally {
            try {
                conn.close();
            } catch (SQLException | NullPointerException e) {
                logMessage("311", e);
            }
        }
        return door;
    }

    public Door getDoor(long doorUID) {
        Door door = null;

        Connection conn = null;
        try {
            conn = getConnection();

            PreparedStatement ps3 = conn.prepareStatement("SELECT * FROM doors WHERE id = '" + doorUID + "';");
            ResultSet rs3 = ps3.executeQuery();

            while (rs3.next())
                door = newDoorFromRS(rs3, doorUID);

            ps3.close();
            rs3.close();
        } catch (SQLException | NullPointerException e) {
            logMessage("521", e);
        } finally {
            try {
                conn.close();
            } catch (SQLException | NullPointerException e) {
                logMessage("531", e);
            }
        }
        return door;
    }

    public Set<Door> getDoors() {
        Set<Door> doors = new HashSet<>();
        try (Connection conn = getConnection();
             PreparedStatement stmp = conn.prepareStatement("SELECT DISTINCT (d.id), name, world, isopen, xmin, ymin, zmin, xmax, ymax, zmax, enginex, enginey, enginez, islocked, type, engineside, powerblockx, powerblocky, powerblockz, opendirection, autoclose, chunkhash, blockstomove, notify, p.playername, p.playeruuid from (SELECT d.*, u.playerid FROM doors d left join sqlUnion u on d.id = u.doorUID where u.permission = 0) d left join players p on p.id = d.playerid");
             ResultSet rs = stmp.executeQuery()) {
            while (rs.next()) {
                doors.add(newDoorFromRS(rs, rs.getInt(DOOR_ID)));
            }
        } catch (SQLException | NullPointerException e) {
            logMessage("541", e);
            return Collections.emptySet();
        }
        return doors;
    }

    public ArrayList<Door> getDoors(String name) {
        ArrayList<Door> doors = new ArrayList<>();

        Connection conn = null;
        try {
            conn = getConnection();

            PreparedStatement ps1 = conn.prepareStatement("SELECT * FROM doors WHERE name = '" + name + "';");
            ResultSet rs1 = ps1.executeQuery();

            while (rs1.next()) {
                long doorUID = rs1.getLong(DOOR_ID);
                doors.add(newDoorFromRS(rs1, doorUID));
            }

            ps1.close();
            rs1.close();
        } catch (SQLException | NullPointerException e) {
            logMessage("582", e);
        } finally {
            try {
                conn.close();
            } catch (SQLException | NullPointerException e) {
                logMessage("592", e);
            }
        }
        return doors;
    }

    public HashMap<Long, Long> getPowerBlockData(final long chunkHash) {
        HashMap<Long, Long> doors = new HashMap<>();

        Connection conn = null;
        try {
            conn = getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM doors WHERE chunkHash = '" + chunkHash + "';");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                long locationHash = Util.locationHash(rs.getInt(DOOR_POWER_X), rs.getInt(DOOR_POWER_Y),
                        rs.getInt(DOOR_POWER_Z),
                        UUID.fromString(rs.getString(DOOR_WORLD)));
                doors.put(locationHash, rs.getLong(DOOR_ID));
            }
            ps.close();
            rs.close();
        } catch (SQLException | NullPointerException e) {
            logMessage("828", e);
        } finally {
            try {
                conn.close();
            } catch (SQLException | NullPointerException e) {
                logMessage("838", e);
            }
        }
        return doors;
    }

    public void recalculatePowerBlockHashes() {
        Connection conn = null;
        try {
            conn = getConnection();
            PreparedStatement ps1 = conn.prepareStatement("SELECT * FROM doors;");
            ResultSet rs1 = ps1.executeQuery();
            String update;

            while (rs1.next()) {
                long UID = rs1.getLong(DOOR_ID);
                UUID worldUUID = UUID.fromString(rs1.getString(DOOR_WORLD));
                int x = rs1.getInt(DOOR_POWER_X);
                int z = rs1.getInt(DOOR_POWER_Z);

                update = "UPDATE doors SET " + "chunkHash='" + Util.chunkHashFromLocation(x, z, worldUUID)
                        + "' WHERE id = '" + UID + "';";
                conn.prepareStatement(update).executeUpdate();
            }
            ps1.close();
            rs1.close();
        } catch (SQLException | NullPointerException e) {
            logMessage("893", e);
        } finally {
            try {
                conn.close();
            } catch (SQLException | NullPointerException e) {
                logMessage("903", e);
            }
        }
    }

    public void updateDoorBlocksToMove(final long doorID, final int blocksToMove) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            String update = "UPDATE doors SET " + "blocksToMove='" + blocksToMove + "' WHERE id = '" + doorID + "';";
            conn.prepareStatement(update).executeUpdate();
            conn.commit();
        } catch (SQLException | NullPointerException e) {
            logMessage("859", e);
        } finally {
            try {
                conn.close();
            } catch (SQLException | NullPointerException e) {
                logMessage("869", e);
            }
        }
    }

    public void updateDoorCoords(final long doorID, final boolean isOpen, final int xMin, final int yMin,
                                 final int zMin, final int xMax, final int yMax, final int zMax,
                                 final DoorDirection engSide) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            String update = "UPDATE doors SET " + "xMin='" + xMin + "',yMin='" + yMin + "',zMin='" + zMin + "',xMax='"
                    + xMax + "',yMax='" + yMax + "',zMax='" + zMax + "',isOpen='" + (isOpen ? 1 : 0)
                    + "',engineSide='" + (engSide == null ? -1 : DoorDirection.getValue(engSide)) + "' WHERE id = '"
                    + doorID + "';";
            conn.prepareStatement(update).executeUpdate();
            conn.commit();
        } catch (SQLException | NullPointerException e) {
            logMessage("897", e);
        } finally {
            try {
                conn.close();
            } catch (SQLException | NullPointerException e) {
                logMessage("907", e);
            }
        }
    }

    public void updateDoorAutoClose(final long doorID, final int autoClose) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            String update = "UPDATE doors SET " + "autoClose='" + autoClose + "' WHERE id = '" + doorID + "';";
            conn.prepareStatement(update).executeUpdate();
            conn.commit();
        } catch (SQLException | NullPointerException e) {
            logMessage("928", e);
        } finally {
            try {
                conn.close();
            } catch (SQLException | NullPointerException e) {
                logMessage("938", e);
            }
        }
    }

    public void updateNotify(final long doorUID, final boolean notify) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            String update = "UPDATE doors SET notify='" + (notify ? 1 : 0) + "' WHERE id = '" + doorUID + "';";
            conn.prepareStatement(update).executeUpdate();
            conn.commit();
        } catch (SQLException | NullPointerException e) {
            logMessage("928", e);
        }
    }

    public void updateDoorOpenDirection(final long doorID, final RotateDirection openDir) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            String update = "UPDATE doors SET " + "openDirection='" + RotateDirection.getValue(openDir)
                    + "' WHERE id = '" + doorID + "';";
            conn.prepareStatement(update).executeUpdate();
            conn.commit();
        } catch (SQLException | NullPointerException e) {
            logMessage("958", e);
        } finally {
            try {
                conn.close();
            } catch (SQLException | NullPointerException e) {
                logMessage("966", e);
            }
        }
    }

    public void updateDoorPowerBlockLoc(final long doorID, final int xPos, final int yPos, final int zPos,
                                        final UUID worldUUID) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            String update = "UPDATE doors SET " + "powerBlockX='" + xPos + "',powerBlockY='" + yPos + "',powerBlockZ='"
                    + zPos + "',chunkHash='" + Util.chunkHashFromLocation(xPos, zPos, worldUUID) + "' WHERE id = '" + doorID
                    + "';";
            conn.prepareStatement(update).executeUpdate();
            conn.commit();
        } catch (SQLException | NullPointerException e) {
            logMessage("992", e);
        } finally {
            try {
                conn.close();
            } catch (SQLException | NullPointerException e) {
                logMessage("1002", e);
            }
        }
    }

    public boolean isPowerBlockLocationEmpty(final Location loc) {
        // Prepare door and connection.
        Connection conn = null;
        try {
            conn = getConnection();
            // Get the door associated with the x/y/z location of the power block block.
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM doors WHERE powerBlockX = '" + loc.getBlockX()
                    + "' AND powerBlockY = '" + loc.getBlockY() + "' AND powerBlockZ = '" + loc.getBlockZ()
                    + "' AND world = '" + loc.getWorld().getUID().toString() + "';");
            ResultSet rs = ps.executeQuery();
            boolean isAvailable = !rs.next();

            ps.close();
            rs.close();
            return isAvailable;
        } catch (SQLException | NullPointerException e) {
            logMessage("1033", e);
        } finally {
            try {
                conn.close();
            } catch (SQLException | NullPointerException e) {
                logMessage("1043", e);
            }
        }

        return false;
    }

    public void setLock(final long doorID, final boolean newLockStatus) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            String update = "UPDATE doors SET " + "isLocked='" + (newLockStatus ? 1 : 0) + "' WHERE id='"
                    + doorID + "';";
            conn.prepareStatement(update).executeUpdate();
            conn.commit();
        } catch (SQLException | NullPointerException e) {
            logMessage("1066", e);
        } finally {
            try {
                conn.close();
            } catch (SQLException | NullPointerException e) {
                logMessage("1076", e);
            }
        }
    }

    public long insert(final Door door) {
        try (Connection conn = getConnection()) {
            String doorInsertsql = "INSERT INTO doors(name,world,isOpen,xMin,yMin,zMin,xMax,yMax,zMax,engineX,engineY,engineZ,isLocked,type,engineSide,powerBlockX,powerBlockY,powerBlockZ,openDirection,autoClose,chunkHash,blocksToMove,notify) "
                    + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            PreparedStatement doorstatement = conn.prepareStatement(doorInsertsql);

            doorstatement.setString(DOOR_NAME - 1, door.getName());
            doorstatement.setString(DOOR_WORLD - 1, door.getWorld().getUID().toString());
            doorstatement.setInt(DOOR_OPEN - 1, door.isOpen() ? 1 : 0);
            doorstatement.setInt(DOOR_MIN_X - 1, door.getMinimum().getBlockX());
            doorstatement.setInt(DOOR_MIN_Y - 1, door.getMinimum().getBlockY());
            doorstatement.setInt(DOOR_MIN_Z - 1, door.getMinimum().getBlockZ());
            doorstatement.setInt(DOOR_MAX_X - 1, door.getMaximum().getBlockX());
            doorstatement.setInt(DOOR_MAX_Y - 1, door.getMaximum().getBlockY());
            doorstatement.setInt(DOOR_MAX_Z - 1, door.getMaximum().getBlockZ());
            doorstatement.setInt(DOOR_ENG_X - 1, door.getEngine().getBlockX());
            doorstatement.setInt(DOOR_ENG_Y - 1, door.getEngine().getBlockY());
            doorstatement.setInt(DOOR_ENG_Z - 1, door.getEngine().getBlockZ());
            doorstatement.setInt(DOOR_LOCKED - 1, door.isLocked() ? 1 : 0);
            doorstatement.setInt(DOOR_TYPE - 1, DoorType.getValue(door.getType()));
            doorstatement.setInt(DOOR_ENG_SIDE - 1, door.getEngSide() == null ? -1 : DoorDirection.getValue(door.getEngSide()));
            doorstatement.setInt(DOOR_POWER_X - 1, door.getEngine().getBlockX());
            doorstatement.setInt(DOOR_POWER_Y - 1, door.getEngine().getBlockY() - 1); // Power Block Location is 1 block
            doorstatement.setInt(DOOR_POWER_Z - 1, door.getEngine().getBlockZ());
            doorstatement.setInt(DOOR_OPEN_DIR - 1, RotateDirection.getValue(door.getOpenDir()));
            doorstatement.setInt(DOOR_AUTO_CLOSE - 1, door.getAutoClose());
            doorstatement.setLong(DOOR_CHUNK_HASH - 1, door.getPowerBlockChunkHash());
            doorstatement.setLong(DOOR_BLOCKS_TO_MOVE - 1, door.getBlocksToMove());
            doorstatement.setInt(DOOR_NOTIFY - 1, door.notificationEnabled() ? 1 : 0);

            doorstatement.executeUpdate();

            final long doorUID;
            try (ResultSet generatedKeys = doorstatement.getGeneratedKeys()) {
                if (!generatedKeys.next())
                    throw new SQLException("Failed to find generated keys when inserting new door!");
                doorUID = generatedKeys.getLong(1);
            }

            return doorUID;
        } catch (SQLException | NullPointerException e) {
            logMessage("1153", e);
        }
        return -1;
    }

    private int getDatabaseVersion(final Connection conn) {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("PRAGMA user_version;");
            int dbVersion = rs.getInt(1);
            stmt.close();
            rs.close();
            return dbVersion;
        } catch (SQLException | NullPointerException e) {
            logMessage("1503", e);
        }
        return Integer.MAX_VALUE;
    }

    private int getDatabaseVersion() {
        try (Connection conn = getConnection()) {
            return getDatabaseVersion(conn);
        } catch (SQLException | NullPointerException e) {
            logMessage("1498", e);
        }
        return Integer.MAX_VALUE;
    }

    private void logMessage(String str, Exception e) {
        if (!locked.get())
            plugin.getMyLogger().logMessageToLogFile(str + " " + Util.throwableToString(e));
        else if (!validVersion)
            plugin.getMyLogger()
                    .logMessageToLogFile("This version of the database is not supported by this version of the plugin!");
        else
            plugin.getMyLogger()
                    .logMessageToLogFile("Database locked! Failed at: " + str + ". Message: " + e.getMessage());

    }
}
