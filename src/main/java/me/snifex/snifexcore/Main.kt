package me.snifex.snifexcore

import org.bukkit.ChatColor
import org.bukkit.command.CommandExecutor
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class Main : JavaPlugin() {
    override fun onEnable() {
        logger.info("${ChatColor.DARK_AQUA}${ChatColor.BOLD}Enabling SnifexCore...")
        registerEvents(CustomInventory, this)
        logger.info("${ChatColor.DARK_AQUA}${ChatColor.BOLD}Registered all events.")
        logger.info("${ChatColor.DARK_AQUA}${ChatColor.BOLD}Successfully enabled SnifexCore!")
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}

fun registerEvents(@NotNull listener: Listener, @NotNull plugin: Plugin) {
    plugin.server.pluginManager.registerEvents(listener, plugin)

}

fun registerCommand(@NotNull command: String, @Nullable executor: CommandExecutor, @NotNull plugin: JavaPlugin) {
    plugin.getCommand(command)!!.setExecutor(executor)
}