package nl.pim16aap2.bigDoors;

import nl.pim16aap2.bigDoors.util.ConfigLoader;
import nl.pim16aap2.bigDoors.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

public class MyLogger
{
    private final BigDoors plugin;

    public MyLogger(BigDoors plugin)
    {
        this.plugin = plugin;
    }

    // Print a string to the console.
    public void myLogger(Level level, String str)
    {
        Bukkit.getLogger().log(level, "[" + plugin.getName() + "] " + str);
    }

    // Send a message to whomever issued a command.
    public void returnToSender(CommandSender sender, Level level, ChatColor color, String str)
    {
        if (sender instanceof Player)
            Util.messagePlayer((Player) sender, color + str);
        else
            myLogger(level, ChatColor.stripColor(str));
    }

    public void logMessage(Level level, String msg)
    {
        logMessage(msg, true, false, level);
    }

    // Log a message to the log file. Can print to console and/or
    // add some new lines before the message in the logfile to make it stand out.
    public void logMessage(String msg, boolean printToConsole, boolean startSkip, final Level level) {
        if (printToConsole)
            myLogger(level, msg);
    }

    // Log a message to the log file. Can print to console and/or
    // add some new lines before the message in the logfile to make it stand out.
    public void logMessage(String msg, boolean printToConsole, boolean startSkip)
    {
        logMessage(msg, printToConsole, startSkip, Level.WARNING);
    }

    // Log a message to the logfile. Does not print to console or add newlines in
    // front of the actual message.
    public void logMessageToLogFile(String msg)
    {
        logMessage(msg, false, false);
    }

    public void logMessageToConsole(String msg)
    {
        logMessage(msg, true, false);
    }

    public void logMessageToConsoleOnly(String msg)
    {
        info(msg);
    }

    public void debug(String str)
    {
        if (ConfigLoader.DEBUG)
            // Log at INFO level because lower levels are filtered by Spigot.
            myLogger(Level.INFO, str);
    }

    public void info(String str)
    {
        myLogger(Level.INFO, str);
    }

    public void warn(String str)
    {
        myLogger(Level.WARNING, str);
        logMessage(str, false, false);
    }

    public void severe(String str)
    {
        myLogger(Level.SEVERE, str);
        logMessage(str, false, false);
    }

    public static void logMessage(Level level, String pluginName, String message)
    {
        Bukkit.getLogger().log(level, "[" + pluginName + "] " + message);
    }

    public void log(Throwable throwable)
    {
        severe(Util.throwableToString(throwable));
    }

    public void log(String message, Throwable throwable)
    {
        severe(message + "\n" + Util.throwableToString(throwable));
    }
}
