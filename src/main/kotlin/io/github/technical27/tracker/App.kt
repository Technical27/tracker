package io.github.technical27.tracker

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.entity.Player
import org.bukkit.Material
import org.bukkit.ChatColor
import org.bukkit.World.Environment
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType

import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.Command

import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerPortalEvent

import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.CompassMeta
import org.bukkit.inventory.ItemFactory
import org.bukkit.inventory.Inventory

class App : JavaPlugin() {
  val COMPASS_META = server.itemFactory.getItemMeta(Material.COMPASS)
  val COMPASS_KEY = NamespacedKey(this, "compass")
  val players: HashSet<Player> = HashSet()

  var task: UpdateTask? = null

  override fun onEnable() {
    logger.info("Hello")
    COMPASS_META.setDisplayName("The Tracking Compass")
    COMPASS_META.setUnbreakable(true)
    COMPASS_META.persistentDataContainer.set(COMPASS_KEY, PersistentDataType.STRING, "yes");
    getCommand("track")!!.setExecutor(TrackCommand(this))
    getCommand("compass")!!.setExecutor(CompassCommand(this))
    server.pluginManager.registerEvents(AppListener(this), this)
  }

  override fun onDisable() {
    if (task != null && task?.isCancelled() == false) {
      task?.cancel()
    }
    logger.info("Bye")
  }

  fun isCompass(stack: ItemStack?): Boolean {
    return stack != null && stack
      .itemMeta
      .persistentDataContainer
      .has(COMPASS_KEY, PersistentDataType.STRING)
  }
}

class AppListener(val plugin: App) : Listener {
  @EventHandler
  fun onPlayerJoin(event: PlayerJoinEvent) {
    event.player.sendMessage("Welcome to the server!\nuse /compass to get a compass")
  }

  @EventHandler
  fun onPlayerDrop(event: PlayerDropItemEvent) {
    val item = event.itemDrop
    val meta = item.itemStack.itemMeta
    if (meta.persistentDataContainer.has(plugin.COMPASS_KEY, PersistentDataType.STRING)) {
      plugin.players.remove(event.player)
      item.remove()
    }
  }

  @EventHandler
  fun onPlayerPortal(event: PlayerPortalEvent) {
    val player = event.player
    if (player == plugin.task?.trackedPlayer) {
      val dim = player.world.environment
      plugin.task?.lastLocations?.set(dim, player.location);
    }
  }
}


class UpdateTask(val plugin: App, val trackedPlayer: Player) : BukkitRunnable() {
  val lastLocations: HashMap<Environment, Location> = HashMap()

  fun findCompass(inv: Inventory): ItemStack? {
    return inv.contents.find { plugin.isCompass(it) }
  }

  override fun run() {
    val loc = trackedPlayer.location
    val dim = trackedPlayer.world.environment
    plugin.players.forEach { player ->
      val inv = player.inventory
      val stack = findCompass(inv)

      if (stack == null) return;

      val idx = inv.first(stack);
      val meta = stack.itemMeta as CompassMeta
      val playerDim = player.world.environment

      if (playerDim != dim) {
        val lastLoc = lastLocations.get(playerDim)

        if (lastLoc == null) {
          player.sendMessage("the tracked player never went to your dimension")
        } else {
          meta.setLodestoneTracked(false)
          meta.lodestone = lastLoc
          stack.itemMeta = meta
          inv.setItem(idx, stack)
        }
      }
      else {
        meta.setLodestoneTracked(false)
        meta.lodestone = loc
        stack.itemMeta = meta
        inv.setItem(idx, stack)
      }
    }
  }
}

class TrackCommand(val plugin: App) : CommandExecutor {
  override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
    if (sender is Player && sender.hasPermission("tracker.setplayer")) {
      if (args.size == 1) {
        val target = plugin.server.getPlayer(args[0])
        if (target == null) {
          sender.sendMessage("that player doesn't exist or isn't online")
          return false
        } else {
          if (plugin.task != null) {
            plugin.task?.cancel();
          }
          plugin.task = UpdateTask(plugin, target)
          plugin.task!!.runTaskTimer(plugin, 20L, 100L)
          plugin.server.broadcastMessage("${target.getDisplayName()} is now the target")
          return true
        }
      }
    }
    return false
  }
}

class CompassCommand(val plugin: App) : CommandExecutor {
  override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
    if (sender is Player && sender.hasPermission("tracker.track")) {
      val compass = ItemStack(Material.COMPASS)
      compass.itemMeta = plugin.COMPASS_META

      val inv = sender.inventory

      if (inv.contents.any({ plugin.isCompass(it) })) {
        sender.sendMessage("you already have a compass")
        return false
      } else {
        inv.remove(Material.COMPASS)
        inv.addItem(compass)
        plugin.players.add(sender)
        sender.sendMessage("here's a compass")
      }

      return true
    }
    return false
  }
}
