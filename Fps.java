package org.fc.a;

import org.bukkit.command.TabCompleter;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

import net.kyori.adventure.text.Component;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntitySpawnEvent;

public final class A extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // ===== Target =====
    private BlockDisplay display;
    private Shulker hitbox;
    private Location spawn;
    private World world;
    private boolean targetAlive = false;

    // ===== Player Data =====
    private final Map<UUID, Integer> score = new HashMap<>();
    private final Map<UUID, Integer> ammo = new HashMap<>();
    private final Map<UUID, Boolean> reloading = new HashMap<>();
    private final Map<UUID, Boolean> auto = new HashMap<>();
    private final Map<UUID, Boolean> firing = new HashMap<>();
    private final Map<UUID, Float> recoil = new HashMap<>();
    private final Map<UUID, Boolean> ads = new HashMap<>();

    private static final int MAG_SIZE = 30;

    //hp
    private int targetLevel = 1;

    private int targetMaxHp = 10;
    private int targetHp = targetMaxHp;

    private double moveSpeed = 0.15;
    private String movePattern = "left_right";

    private String gameMode = "classic";
    private int timeRemaining = 60;
    private boolean gameRunning = true;

    private TextDisplay hpDisplay;


    private static final int HP_BAR_LENGTH = 20;
    private double displayHp = targetHp;


    // =====================================================
// 武器判定（銃）
// =====================================================
    private boolean isGun(Player p) {
        return p.getInventory().getItemInMainHand().getType() == Material.WOODEN_HOE;
    }

    // =====================================================
// 工具判定（全クワ）
// =====================================================
    private boolean isHoe(Material m) {
        return m == Material.WOODEN_HOE ||
                m == Material.STONE_HOE ||
                m == Material.IRON_HOE ||
                m == Material.GOLDEN_HOE ||
                m == Material.DIAMOND_HOE ||
                m == Material.NETHERITE_HOE;
    }

    @Override
    public void onEnable() {

        Bukkit.getPluginManager().registerEvents(this, this);
        world = Bukkit.getWorlds().get(0);

        new BukkitRunnable() {
            @Override
            public void run() {

                if (!gameRunning) return;
                if (!gameMode.equals("time")) return;

                timeRemaining--;

                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateUI(p);
                }

                if (timeRemaining <= 0) {

                    gameRunning = false;

                    for (Player p : Bukkit.getOnlinePlayers()) {

                        p.sendTitle(
                                "§cTIME UP",
                                "§eScore: " + score.getOrDefault(
                                        p.getUniqueId(), 0
                                ),
                                10,
                                40,
                                10
                        );

                        p.playSound(
                                p.getLocation(),
                                Sound.BLOCK_NOTE_BLOCK_BASS,
                                1f,
                                0.5f
                        );
                    }
                }
            }
        }.runTaskTimer(this, 20L, 20L);

        // ===== Target Move =====
        new BukkitRunnable() {
            double t = 0;

            @Override
            public void run() {
                if (!targetAlive) return;

                t += moveSpeed;

                double x = spawn.getX();
                double y = spawn.getY();
                double z = spawn.getZ();

                switch (movePattern) {

                    case "left_right":
                        x += Math.sin(t) * 4.0;
                        break;

                    case "up_down":
                        y += Math.sin(t) * 2.0;
                        break;

                    case "circle":
                        x += Math.cos(t) * 3.0;
                        y += Math.sin(t) * 3.0;
                        break;

                    case "random":
                        x += Math.sin(t * 1.7) * 3.0;
                        y += Math.sin(t * 2.3) * 1.5;
                        z += Math.cos(t * 1.3) * 2.0;
                        break;

                    case "stop":
                        break;
                }

                Location loc = new Location(
                        world,
                        x,
                        y,
                        z
                );

                if (display != null && hitbox != null) {
                    display.teleport(loc);
                    hitbox.teleport(loc);

                    if (hpDisplay != null) {
                        hpDisplay.teleport(loc.clone().add(0, 1.8, 0));
                    }
                }
            }
        }.runTaskTimer(this, 0L, 1L);

        // ===== Auto Fire =====
        new BukkitRunnable() {
            @Override
            public void run() {
                //if (!targetAlive) return;

                for (UUID id : new HashSet<>(firing.keySet())) {

                    if (!firing.getOrDefault(id, false)) continue;

                    Player p = Bukkit.getPlayer(id);
                    if (p == null) continue;

                    if (!isGun(p)) continue;
                    if (reloading.getOrDefault(id, false)) continue;

                    shoot(p);
                }
            }
        }.runTaskTimer(this, 0L, 2L);

        // ===== Recoil decay =====
        new BukkitRunnable() {
            @Override
            public void run() {

                for (UUID id : new HashSet<>(recoil.keySet())) {

                    float r = recoil.getOrDefault(id, 0f);
                    r *= 0.9f;

                    recoil.put(id, r < 0.01f ? 0f : r);
                }
            }
        }.runTaskTimer(this, 0L, 1L);

        getCommand("lv").setExecutor(this);
        getCommand("spawn").setExecutor(this);
        getCommand("delete").setExecutor(this);
        getCommand("move").setExecutor(this);
        getCommand("mode").setExecutor(this);

        getCommand("lv").setTabCompleter(this);
        getCommand("spawn").setTabCompleter(this);
        getCommand("delete").setTabCompleter(this);
        getCommand("move").setTabCompleter(this);
        getCommand("mode").setTabCompleter(this);

        new BukkitRunnable() {
            @Override
            public void run() {

                if (!targetAlive) return;

                if (displayHp > targetHp) {
                    displayHp -= 0.2;

                    if (displayHp < targetHp)
                        displayHp = targetHp;

                    updateHpDisplay();
                }

                if (displayHp < targetHp) {
                    displayHp = targetHp;
                    updateHpDisplay();
                }

            }
        }.runTaskTimer(this,0L,1L);

    }


    // =====================================================
// QUIT → 全員いないなら削除
// =====================================================
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {

        firing.remove(e.getPlayer().getUniqueId());

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                removeTarget();
            }
        }, 1L);
    }

    // =====================================================
// 銃 + スコアリセット
// =====================================================
    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {

        if (e.getAction() != Action.RIGHT_CLICK_AIR &&
                e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        Material item = p.getInventory().getItemInMainHand().getType();

        if (item == Material.STONE_HOE) {

            score.put(id, 0);

            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            p.sendActionBar("§cScore Reset!");

            updateUI(p);
            return;
        }

        if (item == Material.GOLDEN_HOE) {

            targetLevel = 1;
            targetMaxHp = 10;
            targetHp = targetMaxHp;
            displayHp = targetHp;
            moveSpeed = 0.15;

            updateHpDisplay();

            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1.0f);
            p.sendActionBar("§eTarget Level Reset!");

            updateUI(p);
            return;
        }

        if (!isGun(p)) return;
        if (reloading.getOrDefault(id, false)) return;

        boolean now = ads.getOrDefault(id, false);
        ads.put(id, !now);

        applyAds(p, !now);
        updateUI(p);
    }

    // =====================================================
// 射撃
// =====================================================
    @EventHandler
    public void onLeftClick(PlayerInteractEvent e) {

        if (e.getAction() != Action.LEFT_CLICK_AIR &&
                e.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        if (!isGun(p)) return;
        if (reloading.getOrDefault(id, false)) return;

        if (!auto.getOrDefault(id, false)) {
            shoot(p);
            return;
        }

        firing.put(id, !firing.getOrDefault(id, false));
        updateUI(p);
    }

    // =====================================================
// セミ / フル
// =====================================================
    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {

        Player p = e.getPlayer();

        if (!isGun(p)) {
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true);

        UUID id = p.getUniqueId();

        boolean now = auto.getOrDefault(id, false);
        auto.put(id, !now);

        firing.put(id, false);

        p.sendActionBar(now ? "§cSEMI MODE" : "§aFULL MODE");
        updateUI(p);
    }

    // =====================================================
// リロード（+1仕様）
// =====================================================
    @EventHandler
    public void onReload(PlayerToggleSneakEvent e) {

        Player p = e.getPlayer();

        if (!p.isSneaking()) return;
        if (!isGun(p)) return;

        UUID id = p.getUniqueId();

        if (reloading.getOrDefault(id, false)) return;

        reloading.put(id, true);
        firing.put(id, false);

        p.sendActionBar("§eReloading...");

        Bukkit.getScheduler().runTaskLater(this, () -> {

            int current = ammo.getOrDefault(id, MAG_SIZE + 1);

            if (current == 0) {
                ammo.put(id, MAG_SIZE);      // 0 → 30
            } else {
                ammo.put(id, MAG_SIZE + 1);  // 1～30 → 31
            }

            reloading.put(id, false);

            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1.2f);

            updateUI(p);

        }, 30L);
    }

    // =====================================================
// クワ系キャンセル
// =====================================================
    @EventHandler
    public void onAttack(PlayerInteractEvent e) {

        if (e.getAction() != Action.LEFT_CLICK_AIR &&
                e.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Player p = e.getPlayer();

        if (!isHoe(p.getInventory().getItemInMainHand().getType())) return;

        e.setCancelled(true);
    }

    @EventHandler
    public void onHoeTill(PlayerInteractEvent e) {

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();

        if (!isHoe(p.getInventory().getItemInMainHand().getType())) return;

        Block b = e.getClickedBlock();
        if (b == null) return;

        Material type = b.getType();

        if (type == Material.DIRT ||
                type == Material.GRASS_BLOCK ||
                type == Material.DIRT_PATH ||
                type == Material.COARSE_DIRT) {

            e.setCancelled(true);
        }
    }

    // =====================================================
// 射撃処理
// =====================================================
    private void shoot(Player p) {

        if (!gameRunning) return;

        UUID id = p.getUniqueId();


        int a = ammo.getOrDefault(id, MAG_SIZE + 1);
        if (a <= 0) return;

        ammo.put(id, a - 1);

        Vector dir = p.getLocation().getDirection();

        dir.add(new Vector(
                (Math.random() - 0.5) * 0.02,
                (Math.random() - 0.5) * 0.02,
                (Math.random() - 0.5) * 0.02
        ));

        dir.normalize().multiply(4.6);

        Arrow arrow = p.launchProjectile(Arrow.class);

// スコア10ごとに攻撃力+1
        int sc = score.getOrDefault(id, 0);
        arrow.setDamage(2.0 + (sc / 10));

        arrow.setVelocity(dir);
        arrow.setGravity(false);

        float r = recoil.getOrDefault(id, 0f) + 0.12f;
        recoil.put(id, Math.min(r, 1f));

        applyRecoil(p, r);

        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.15f, 0.6f);

        updateUI(p);
    }

    private void applyRecoil(Player p, float r) {

        if (!isGun(p)) return;

        float m = ads.getOrDefault(p.getUniqueId(), false) ? 0.35f : 1.0f;

        Location loc = p.getLocation();

        loc.setPitch(loc.getPitch() - r * 0.2f * m);
        loc.setYaw(loc.getYaw() + (float)((Math.random() - 0.5) * r * 0.05f * m));

        p.teleport(loc);
    }

    private void applyAds(Player p, boolean enable) {

        if (enable) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 999999, 3));
        } else {
            p.removePotionEffect(PotionEffectType.SLOWNESS);
        }
    }

    private void updateHpDisplay() {

        if (hpDisplay == null) return;

        double percent = displayHp / targetMaxHp;
        int filled = (int) Math.round(percent * HP_BAR_LENGTH);

        String color;

        if (percent > 0.6) {
            color = "§a";
        } else if (percent > 0.3) {
            color = "§e";
        } else {
            color = "§c";
        }

        StringBuilder bar = new StringBuilder();

        for (int i = 0; i < HP_BAR_LENGTH; i++) {
            if (i < filled) {
                bar.append(color).append("█");
            } else {
                bar.append("§8█");
            }
        }

        hpDisplay.text(Component.text(
                "§6Lv." + targetLevel +
                        "\n" + bar +
                        "\n§f" + targetHp + "§7/§f" + targetMaxHp
        ));
    }

    // =====================================================
// TARGET
// =====================================================
    private void spawnTarget(Location loc) {

        spawn = loc.clone();

        display = spawn.getWorld().spawn(spawn, BlockDisplay.class);
        display.setBlock(Material.TARGET.createBlockData());

        hitbox = spawn.getWorld().spawn(spawn, Shulker.class, s -> {
            s.setAI(false);
            s.setInvisible(true);
            s.setInvulnerable(true);
            s.setSilent(true);
            s.setGravity(false);
        });

        hpDisplay = spawn.getWorld().spawn(
                spawn.clone().add(0, 1.8, 0),
                TextDisplay.class
        );

        hpDisplay.setBillboard(Display.Billboard.CENTER);
        hpDisplay.setSeeThrough(false);
        hpDisplay.setShadowed(true);

        targetAlive = true;
        targetHp = targetMaxHp;
        displayHp = targetHp;

        updateHpDisplay();
    }

    private void removeTarget() {

        if (display != null) display.remove();
        if (hitbox != null) hitbox.remove();
        if (hpDisplay != null) hpDisplay.remove();

        display = null;
        hitbox = null;
        hpDisplay = null;

        targetAlive = false;
    }

    // =====================================================
// HIT
// =====================================================
    @EventHandler
    public void onHit(ProjectileHitEvent e) {

        if (!(e.getEntity() instanceof Arrow arrow)) return;

        // 着弾したら必ず削除
        arrow.remove();

        if (!targetAlive || display == null) return;
        if (e.getHitEntity() != hitbox) return;

        Player p = (Player) arrow.getShooter();
        if (p == null) return;

        UUID id = p.getUniqueId();

        score.put(id, score.getOrDefault(id, 0) + 1);

        targetHp--;
        updateHpDisplay();

        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING,
                SoundCategory.PLAYERS, 0.6f, 2.0f);

        display.getWorld().spawnParticle(Particle.CRIT,
                display.getLocation(), 12, 0.2, 0.2, 0.2, 0.02);

        display.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                display.getLocation(), 5, 0.08, 0.08, 0.08, 0.01);

        if (targetHp <= 0 && targetAlive) {

            removeTarget();

            targetLevel++;

            targetMaxHp = 10 + (targetLevel - 1) / 2;

            if (targetLevel >= 50) {
                moveSpeed += 0.003;
            }

            Bukkit.getScheduler().runTaskLater(this, () -> {
                spawnTarget(spawn);
            }, 20L);
        }

        updateUI(p);
    }

    // =====================================================
// UI
// =====================================================
    private void updateUI(Player p) {

        UUID id = p.getUniqueId();

        String modeText;

        if (gameMode.equals("classic")) {
            modeText = "§aCLASSIC";
        } else {
            modeText = "§cTIME: " + timeRemaining + "s";
        }

        p.sendActionBar(
                "§aScore: §6" + score.getOrDefault(id, 0)
                        + " §7| §eAmmo: " + ammo.getOrDefault(id, MAG_SIZE + 1)
                        + (auto.getOrDefault(id, false) ? " §bFULL" : " §7SEMI")
                        + (ads.getOrDefault(id, false) ? " §dADS" : "")
                        + (reloading.getOrDefault(id, false) ? " §6RELOAD" : "")
                        + " §7| §6LV: " + targetLevel
                        + " §7| §cHP: " + targetHp + "/" + targetMaxHp
                        + " §7| " + modeText
        );
    }
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {

        Player player = e.getPlayer();
        UUID id = player.getUniqueId();

        // インベントリを初期化
        player.getInventory().clear();

        // 武器を配布
        player.getInventory().addItem(new ItemStack(Material.WOODEN_HOE));
        player.getInventory().addItem(new ItemStack(Material.STONE_HOE));
        player.getInventory().addItem(new ItemStack(Material.GOLDEN_HOE));

        // プレイヤーデータ初期化
        score.put(id, 0);
        ammo.put(id, MAG_SIZE + 1);
        auto.put(id, false);
        firing.put(id, false);
        recoil.put(id, 0f);
        ads.put(id, false);
        reloading.put(id, false);

        updateUI(player);


    }



    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {


        if (command.getName().equalsIgnoreCase("mode")) {

            if (args.length != 1) {
                sender.sendMessage(
                        "§c使い方: /mode <classic|time>"
                );
                return true;
            }

            String mode = args[0].toLowerCase();

            if (!mode.equals("classic") &&
                    !mode.equals("time")) {

                sender.sendMessage(
                        "§cモードは classic / time から選択してください"
                );
                return true;
            }

            gameMode = mode;
            gameRunning = true;

            if (mode.equals("time")) {
                timeRemaining = 60;
            }

            sender.sendMessage(
                    "§aゲームモードを §e" + mode + "§a に変更しました"
            );

            for (Player p : Bukkit.getOnlinePlayers()) {
                updateUI(p);
            }

            return true;
        }

        if (command.getName().equalsIgnoreCase("move")) {

            if (args.length != 1) {
                sender.sendMessage(
                        "§c使い方: /move <left_right|up_down|circle|random|stop>"
                );
                return true;
            }

            String pattern = args[0].toLowerCase();

            if (!pattern.equals("left_right") &&
                    !pattern.equals("up_down") &&
                    !pattern.equals("circle") &&
                    !pattern.equals("random") &&
                    !pattern.equals("stop")) {

                sender.sendMessage("§cその移動パターンは存在しません");
                return true;
            }

            movePattern = pattern;

            sender.sendMessage(
                    "§a移動パターンを §e" + pattern + "§a に変更しました"
            );

            return true;
        }

        if (command.getName().equalsIgnoreCase("delete")) {

            if (args.length == 1 && args[0].equalsIgnoreCase("target")) {

                if (!targetAlive) {
                    sender.sendMessage("§c的がありません");
                    return true;
                }

                removeTarget();

                sender.sendMessage("§a的を削除しました");
                return true;
            }

            sender.sendMessage("§c使い方: /delete target");
            return true;
        }

        if (command.getName().equalsIgnoreCase("spawn")) {

            if (!(sender instanceof Player p)) {
                sender.sendMessage("プレイヤーのみ使用できます");
                return true;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("target")) {

                if (targetAlive) {
                    p.sendMessage("§cすでに的があります");
                    return true;
                }

                spawnTarget(p.getLocation());

                p.sendMessage("§a的をスポーンしました");
                return true;
            }

            p.sendMessage("§c使い方: /spawn target");
            return true;
        }


        if (command.getName().equalsIgnoreCase("lv")) {

            if (args.length != 1) {
                sender.sendMessage("§c使い方: /lv <数字>");
                return true;
            }

            int lv;

            try {
                lv = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§c数字を入力してください");
                return true;
            }

            if (lv < 1) lv = 1;

            targetLevel = lv;
            targetMaxHp = 10 + (targetLevel - 1) / 2;
            targetHp = targetMaxHp;
            displayHp = targetHp;


            if (targetLevel < 50) {
                moveSpeed = 0.15;
            } else {
                moveSpeed = 0.15 + (targetLevel - 50) * 0.003;
            }

            updateHpDisplay();

            sender.sendMessage(
                    "§aTarget LVを §e" + targetLevel + "§a に設定しました。"
            );

            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args) {

        // =========================
        // /move
        // =========================
        if (command.getName().equalsIgnoreCase("move")) {

            if (args.length == 1) {

                List<String> patterns = Arrays.asList(
                        "left_right",
                        "up_down",
                        "circle",
                        "random",
                        "stop"
                );

                return filterTabComplete(patterns, args[0]);
            }

            return Collections.emptyList();
        }


        // =========================
        // /mode
        // =========================
        if (command.getName().equalsIgnoreCase("mode")) {

            if (args.length == 1) {

                List<String> modes = Arrays.asList(
                        "classic",
                        "time"
                );

                return filterTabComplete(modes, args[0]);
            }

            return Collections.emptyList();
        }


        // =========================
        // /spawn
        // =========================
        if (command.getName().equalsIgnoreCase("spawn")) {

            if (args.length == 1) {

                List<String> targets = Arrays.asList(
                        "target"
                );

                return filterTabComplete(targets, args[0]);
            }

            return Collections.emptyList();
        }


        // =========================
        // /delete
        // =========================
        if (command.getName().equalsIgnoreCase("delete")) {

            if (args.length == 1) {

                List<String> targets = Arrays.asList(
                        "target"
                );

                return filterTabComplete(targets, args[0]);
            }

            return Collections.emptyList();
        }


        return Collections.emptyList();
    }

    private List<String> filterTabComplete(
            List<String> options,
            String input) {

        List<String> result = new ArrayList<>();

        String lowerInput = input.toLowerCase();

        for (String option : options) {

            if (option.toLowerCase().startsWith(lowerInput)) {
                result.add(option);
            }
        }

        return result;
    }

}
