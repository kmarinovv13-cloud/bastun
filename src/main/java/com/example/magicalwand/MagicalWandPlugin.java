package com.example.magicalwand;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MagicalWandPlugin extends JavaPlugin implements Listener {

    private static final NamespacedKey WAND_KEY = new NamespacedKey("magicalwand", "wand_stage");
    private static final NamespacedKey KILLS_KEY = new NamespacedKey("magicalwand", "kills");
    private static final NamespacedKey KILLED_PLAYERS_KEY = new NamespacedKey("magicalwand", "killed_players");
    private static final NamespacedKey COOLDOWN_KEY = new NamespacedKey("magicalwand", "cooldown");

    private int cooldownSeconds;
    private Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    private Map<UUID, Integer> playerStages = new ConcurrentHashMap<>();
    private Map<UUID, Set<UUID>> playerKilledPlayers = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("magicalwand").setExecutor(new WandCommand(this));
        getCommand("mw").setExecutor(new WandCommand(this));

        getLogger().info("MagicalWand plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("MagicalWand plugin has been disabled!");
    }

    private void loadConfigValues() {
        cooldownSeconds = getConfig().getInt("cooldown-seconds", 60);
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int seconds) {
        this.cooldownSeconds = seconds;
        getConfig().set("cooldown-seconds", seconds);
        saveConfig();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction().name().contains("RIGHT_CLICK")) {
            Player player = event.getPlayer();
            ItemStack item = player.getInventory().getItemInMainHand();

            if (isWand(item)) {
                event.setCancelled(true);
                useWand(player, item);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null && !killer.equals(victim)) {
            ItemStack item = killer.getInventory().getItemInMainHand();
            if (isWand(item)) {
                handleKill(killer, victim, item);
            } else {
                ItemStack offhand = killer.getInventory().getItemInOffHand();
                if (isWand(offhand)) {
                    handleKill(killer, victim, offhand);
                }
            }
        }
    }

    private void handleKill(Player killer, Player victim, ItemStack wand) {
        UUID killerId = killer.getUniqueId();
        UUID victimId = victim.getUniqueId();

        Set<UUID> killedSet = playerKilledPlayers.computeIfAbsent(killerId, k -> ConcurrentHashMap.newKeySet());
        killedSet.add(victimId);

        int uniqueKills = killedSet.size();
        int newStage = calculateStage(uniqueKills);

        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(KILLS_KEY, PersistentDataType.INTEGER, uniqueKills);
            meta.getPersistentDataContainer().set(KILLED_PLAYERS_KEY, PersistentDataType.STRING, String.join(",", killedSet.stream().map(UUID::toString).toArray(String[]::new)));
            meta.getPersistentDataContainer().set(WAND_KEY, PersistentDataType.INTEGER, newStage);
            wand.setItemMeta(meta);
        }

        playerStages.put(killerId, newStage);

        String stageName = getStageName(newStage);
        killer.sendMessage(ChatColor.GOLD + "Wand upgraded to " + ChatColor.AQUA + stageName + ChatColor.GOLD + "! (Unique kills: " + uniqueKills + ")");
        killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        killer.spawnParticle(Particle.ENCHANT, killer.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
    }

    private int calculateStage(int uniqueKills) {
        if (uniqueKills >= 3) return 3;
        if (uniqueKills >= 1) return 2;
        return 1;
    }

    private String getStageName(int stage) {
        return switch (stage) {
            case 1 -> "Stage I - Novice";
            case 2 -> "Stage II - Adept";
            case 3 -> "Stage III - Master";
            default -> "Unknown";
        };
    }

    private boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(WAND_KEY, PersistentDataType.INTEGER);
    }

    private int getWandStage(ItemStack wand) {
        ItemMeta meta = wand.getItemMeta();
        if (meta == null) return 1;
        return meta.getPersistentDataContainer().getOrDefault(WAND_KEY, PersistentDataType.INTEGER, 1);
    }

    private void useWand(Player player, ItemStack wand) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastUse = playerCooldowns.get(playerId);

        if (lastUse != null) {
            long elapsed = (now - lastUse) / 1000;
            if (elapsed < cooldownSeconds) {
                int remaining = cooldownSeconds - (int) elapsed;
                player.sendMessage(ChatColor.RED + "Wand is on cooldown! Wait " + remaining + " seconds.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return;
            }
        }

        int stage = getWandStage(wand);
        playerCooldowns.put(playerId, now);

        // Find target - raycast to find nearest entity in front
        LivingEntity target = getTargetEntity(player, 20);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "No target in range!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Strike lightning at target
        target.getWorld().strikeLightningEffect(target.getLocation());
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation(), 20, 0.5, 1, 0.5, 0.1);

        // Apply effects based on stage
        switch (stage) {
            case 1 -> {
                player.sendMessage(ChatColor.YELLOW + "Stage I: Struck " + target.getName() + " with lightning!");
            }
            case 2 -> {
                applyStage2Effects(target);
                player.sendMessage(ChatColor.AQUA + "Stage II: Struck " + target.getName() + " with lightning, applied Slowness IV and Blindness!");
            }
            case 3 -> {
                applyStage2Effects(target);
                applyStage3Effects(player);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Stage III: Struck " + target.getName() + " with lightning, applied Slowness IV and Blindness to target, gained Strength III and Speed III!");
            }
        }

        // Visual feedback for player
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.0f, 1.0f);
        player.spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);

        // Start cooldown display
        startCooldownDisplay(player);
    }

    private void applyStage2Effects(LivingEntity target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 3, false, false, true)); // 10 seconds, level 4 (3 = level 4)
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 0, false, false, true)); // 10 seconds
    }

    private void applyStage3Effects(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 2, false, false, true)); // 10 seconds, level 3 (2 = level 3)
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 2, false, false, true)); // 10 seconds, level 3 (2 = level 3)
    }

    private LivingEntity getTargetEntity(Player player, double range) {
        org.bukkit.util.RayTraceResult result = player.rayTraceEntities(range, entity -> {
            if (entity instanceof LivingEntity living) {
                return living != player && !living.isDead();
            }
            return false;
        });
        if (result != null && result.getHitEntity() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private void startCooldownDisplay(Player player) {
        UUID playerId = player.getUniqueId();
        new BukkitRunnable() {
            int remaining = cooldownSeconds;
            @Override
            public void run() {
                if (remaining <= 0) {
                    player.sendMessage(ChatColor.GREEN + "Wand is ready!");
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    cancel();
                    return;
                }
                if (remaining % 10 == 0 || remaining <= 5) {
                    player.sendActionBar(ChatColor.YELLOW + "Wand cooldown: " + remaining + "s");
                }
                remaining--;
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    public int getCustomModelData(int stage) {
        return getConfig().getInt("wand.custom-model-data.stage-" + stage, 1000 + stage);
    }

    public ItemStack createWand(int stage) {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Magical Wand " + ChatColor.WHITE + getStageName(stage));
            meta.setCustomModelData(getCustomModelData(stage));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(WAND_KEY, PersistentDataType.INTEGER, stage);
            meta.getPersistentDataContainer().set(KILLS_KEY, PersistentDataType.INTEGER, 0);
            meta.getPersistentDataContainer().set(KILLED_PLAYERS_KEY, PersistentDataType.STRING, "");
            wand.setItemMeta(meta);
        }
        return wand;
    }

    public void setPlayerStage(Player player, int stage) {
        UUID playerId = player.getUniqueId();
        playerStages.put(playerId, stage);
        ItemStack item = player.getInventory().getItemInMainHand();
        if (isWand(item)) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(WAND_KEY, PersistentDataType.INTEGER, stage);
                meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Magical Wand " + ChatColor.WHITE + getStageName(stage));
                meta.setCustomModelData(getCustomModelData(stage));
                item.setItemMeta(meta);
            }
        }
    }
}
