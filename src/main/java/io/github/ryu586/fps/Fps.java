package io.github.ryu586.fps;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.util.*;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class Fps extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // ===== Random movement settings =====
    private double randomX = 1.0;
    private double randomY = 1.0;
    private double randomZ = 1.0;
    private long randomLastChange = -100;

    // ===== Player Data =====
    private final Map<UUID, Integer> score = new HashMap<>();
    private final Map<UUID, Integer> ammo = new HashMap<>();
    private final Map<UUID, Boolean> reloading = new HashMap<>();
    private final Map<UUID, Boolean> auto = new HashMap<>();
    private final Map<UUID, Boolean> firing = new HashMap<>();
    private final Map<UUID, Float> recoil = new HashMap<>();
    private final Map<UUID, Boolean> ads = new HashMap<>();

    private static final int MAG_SIZE = 30;

    // ===== Game =====
    private String gameMode = "classic";
    private int timeRemaining = 60;
    private boolean gameRunning = true;

    // ===== Targets =====
    private static final int HP_BAR_LENGTH = 20;

    /*
     * 1つの TargetData が「1体の的」の全データを持つ。
     * これで複数の的を同時に管理できる。
     */
    private static final class TargetData {
        final String name;

        BlockDisplay display;
        Shulker hitbox;
        TextDisplay hpDisplay;

        Location spawn;

        int level = 1;
        int maxHp = 10;
        int hp = 10;
        double displayHp = 10;

        double moveSpeed = 0.15;
        String movePattern = "left_right";

        double t = 0;
        int randomCounter = 0;
        double randomRange = 4.5;

        TargetData(String name, Location spawn) {
            this.name = name;
            this.spawn = spawn.clone();
        }
    }

    // 名前は小文字をキーにする。表示名は TargetData.name をそのまま使う。
    private final Map<String, TargetData> targets = new LinkedHashMap<>();

    // 倒した直後の20tick待ち中のターゲット名
    private final Set<String> respawningTargets = new HashSet<>();

    // ===== Weapon =====
    private boolean isGun(Player p) {
        return p.getInventory().getItemInMainHand().getType() == Material.WOODEN_HOE;
    }

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

        // ===== Time mode =====
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
                                "§eScore: " + score.getOrDefault(p.getUniqueId(), 0),
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
            @Override
            public void run() {
                if (targets.isEmpty()) return;

                // コピーしてから回すので、移動中の削除でも安全
                for (TargetData target : new ArrayList<>(targets.values())) {
                    if (target.display == null || target.hitbox == null) continue;

                    target.t += target.moveSpeed;

                    Location base = target.spawn.clone();

                    double x = base.getX();
                    double y = base.getY();
                    double z = base.getZ();

                    switch (target.movePattern) {
                        case "left_right":
                            x += Math.sin(target.t) * 4.0;
                            break;

                        case "up_down":
                            y += Math.sin(target.t) * 2.0;
                            break;

                        case "circle":
                            x += Math.cos(target.t) * 3.0;
                            y += Math.sin(target.t) * 3.0;
                            break;

                        case "random":
                            /*
                             * 100tickごとに幅だけを4.5～5.0の範囲で変更。
                             * 速さ(moveSpeed)は変えない。
                             */
                            target.randomCounter++;

                            if (target.randomCounter >= 100) {
                                target.randomRange = 4.5 + Math.random() * 0.5;
                                target.randomCounter = 0;
                            }

                            x += Math.sin(target.t * 1.7) * target.randomRange;
                            y += Math.sin(target.t * 2.3) * target.randomRange;
                            z += Math.cos(target.t * 1.3) * target.randomRange;
                            break;

                        case "stop":
                            break;

                        default:
                            break;
                    }

                    Location loc = new Location(
                            base.getWorld(),
                            x,
                            y,
                            z
                    );

                    target.display.teleport(loc);
                    target.hitbox.teleport(loc);

                    if (target.hpDisplay != null) {
                        target.hpDisplay.teleport(loc.clone().add(0, 1.8, 0));
                    }
                }
            }
        }.runTaskTimer(this, 0L, 1L);

        // ===== Auto Fire =====
        new BukkitRunnable() {
            @Override
            public void run() {
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

        registerCommand("lv");
        registerCommand("spawn");
        registerCommand("delete");
        registerCommand("move");
        registerCommand("mode");
        registerCommand("targetname");

        // ===== Smooth HP display =====
        new BukkitRunnable() {
            @Override
            public void run() {
                for (TargetData target : new ArrayList<>(targets.values())) {
                    if (target.hpDisplay == null) continue;

                    if (target.displayHp > target.hp) {
                        target.displayHp -= 0.2;

                        if (target.displayHp < target.hp) {
                            target.displayHp = target.hp;
                        }

                        updateHpDisplay(target);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void registerCommand(String name) {
        org.bukkit.command.PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        } else {
            getLogger().warning("plugin.yml に /" + name + " が登録されていません。");
        }
    }

    // =====================================================
    // QUIT
    // =====================================================
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        firing.remove(e.getPlayer().getUniqueId());

        Bukkit.getScheduler().runTaskLater(this, () -> {
            // ここではターゲットを消さない。
            // 複数ターゲットはサーバー上でそのまま維持する。
        }, 1L);
    }

    // =====================================================
    // 右クリック
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
            for (TargetData target : targets.values()) {
                setTargetLevel(target, 1);
            }

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
    // リロード
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
                ammo.put(id, MAG_SIZE);
            } else {
                ammo.put(id, MAG_SIZE + 1);
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

        // 元コードのスコア連動ダメージを維持
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

        loc.setPitch(loc.getPitch() - r * 0.2f);
        loc.setYaw(loc.getYaw() +
                (float) ((Math.random() - 0.5) * r * 0.05f * m));

        p.teleport(loc);
    }

    private void applyAds(Player p, boolean enable) {
        if (enable) {
            p.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS,
                    999999,
                    3
            ));
        } else {
            p.removePotionEffect(PotionEffectType.SLOWNESS);
        }
    }

    // =====================================================
    // Target HP display
    // =====================================================
    private void updateHpDisplay(TargetData target) {
        if (target.hpDisplay == null) return;

        double percent = target.maxHp <= 0
                ? 0
                : target.displayHp / target.maxHp;

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

        target.hpDisplay.text(Component.text(
                "§e" + target.name +
                        "\n§6Lv." + target.level +
                        "\n" + bar +
                        "\n§f" + target.hp + "§7/§f" + target.maxHp
        ));
    }

    // =====================================================
    // TARGET
    // =====================================================
    private void spawnTarget(String name, Location loc) {
        String key = targetKey(name);

        if (targets.containsKey(key)) return;

        // スポーン時の位置と角度をそのまま保存する
        Location spawnLoc = loc.clone();
        TargetData target = new TargetData(name, spawnLoc);

        createTargetEntities(target);
        targets.put(key, target);

        updateHpDisplay(target);
    }

    private void createTargetEntities(TargetData target) {
        Location spawn = target.spawn.clone();

        target.display = spawn.getWorld().spawn(spawn, BlockDisplay.class);
        target.display.setBlock(Material.TARGET.createBlockData());

        target.hitbox = spawn.getWorld().spawn(spawn, Shulker.class, s -> {
            s.setAI(false);
            s.setInvisible(true);
            s.setInvulnerable(true);
            s.setSilent(true);
            s.setGravity(false);
        });

        target.hpDisplay = spawn.getWorld().spawn(
                spawn.clone().add(0, 1.8, 0),
                TextDisplay.class
        );

        target.hpDisplay.setBillboard(Display.Billboard.CENTER);
        target.hpDisplay.setSeeThrough(false);
        target.hpDisplay.setShadowed(true);

        target.hp = target.maxHp;
        target.displayHp = target.hp;
    }

    private void removeTarget(String name) {
        String key = targetKey(name);

        TargetData target = targets.remove(key);

        // 待機中の自動復活もキャンセル
        respawningTargets.remove(key);

        if (target == null) return;

        removeTargetEntities(target);
    }

    private void removeTargetEntities(TargetData target) {
        if (target.display != null) target.display.remove();
        if (target.hitbox != null) target.hitbox.remove();
        if (target.hpDisplay != null) target.hpDisplay.remove();

        target.display = null;
        target.hitbox = null;
        target.hpDisplay = null;
    }

    private void setTargetLevel(TargetData target, int level) {
        if (level < 1) level = 1;

        target.level = level;
        target.maxHp = 10 + (target.level - 1) / 2;
        target.hp = target.maxHp;
        target.displayHp = target.hp;

        if (target.level < 50) {
            target.moveSpeed = 0.15;
        } else {
            target.moveSpeed =
                    0.15 + (target.level - 50) * 0.003;
        }

        updateHpDisplay(target);
    }

    // =====================================================
    // HIT
    // =====================================================
    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow arrow)) return;

        // 着弾したら必ず削除
        arrow.remove();

        if (!(e.getHitEntity() instanceof Shulker hitShulker)) return;

        TargetData target = findTargetByHitbox(hitShulker);

        if (target == null) return;

        if (!(arrow.getShooter() instanceof Player p)) return;

        UUID id = p.getUniqueId();

        score.put(id, score.getOrDefault(id, 0) + 1);

        target.hp--;
        updateHpDisplay(target);

        p.playSound(
                p.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_PLING,
                SoundCategory.PLAYERS,
                0.6f,
                2.0f
        );

        if (target.display != null) {
            target.display.getWorld().spawnParticle(
                    Particle.CRIT,
                    target.display.getLocation(),
                    12,
                    0.2,
                    0.2,
                    0.2,
                    0.02
            );

            target.display.getWorld().spawnParticle(
                    Particle.ELECTRIC_SPARK,
                    target.display.getLocation(),
                    5,
                    0.08,
                    0.08,
                    0.08,
                    0.01
            );
        }

        if (target.hp <= 0) {
            handleTargetDeath(target);
        }

        updateUI(p);
    }

    private TargetData findTargetByHitbox(Shulker hitbox) {
        for (TargetData target : targets.values()) {
            if (target.hitbox == hitbox) {
                return target;
            }
        }

        return null;
    }

    private void handleTargetDeath(TargetData target) {
        String key = targetKey(target.name);
        Location nextSpawn = target.spawn.clone();

        // 現在の的を削除
        targets.remove(key);
        removeTargetEntities(target);

        // 次のレベルへ
        target.level++;

        target.maxHp = 10 + (target.level - 1) / 2;
        target.hp = target.maxHp;
        target.displayHp = target.hp;

        if (target.level < 50) {
            target.moveSpeed = 0.15;
        } else {
            target.moveSpeed =
                    0.15 + (target.level - 50) * 0.003;
        }

        respawningTargets.add(key);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!respawningTargets.remove(key)) return;

            // /spawn や /delete で状態が変わっていた場合は復活させない
            if (targets.containsKey(key)) return;

            target.spawn = nextSpawn.clone();
            target.t = 0;
            target.randomCounter = 0;

            createTargetEntities(target);
            targets.put(key, target);

            updateHpDisplay(target);
        }, 20L);
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
                        + " §7| §eTargets: " + targets.size()
                        + " §7| " + modeText
        );
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        UUID id = player.getUniqueId();

        player.getInventory().clear();

        player.getInventory().addItem(new ItemStack(Material.WOODEN_HOE));
        player.getInventory().addItem(new ItemStack(Material.STONE_HOE));
        player.getInventory().addItem(new ItemStack(Material.GOLDEN_HOE));

        score.put(id, 0);
        ammo.put(id, MAG_SIZE + 1);
        auto.put(id, false);
        firing.put(id, false);
        recoil.put(id, 0f);
        ads.put(id, false);
        reloading.put(id, false);

        updateUI(player);
    }

    // =====================================================
    // COMMANDS
    // =====================================================
    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args) {

        String cmd = command.getName().toLowerCase(Locale.ROOT);

        // =========================
        // /targetname
        // =========================
        if (cmd.equals("targetname")) {
            if (targets.isEmpty()) {
                sender.sendMessage("§c現在出ているターゲットはありません");
                return true;
            }

            sender.sendMessage("§e===== 現在のターゲット =====");

            for (TargetData target : targets.values()) {
                sender.sendMessage("§a・ §f" + target.name);
            }

            sender.sendMessage("§e==========================");
            return true;
        }

        // =========================
        // /mode
        // =========================
        if (cmd.equals("mode")) {
            if (args.length != 1) {
                sender.sendMessage("§c使い方: /mode <classic|time>");
                return true;
            }

            String mode = args[0].toLowerCase(Locale.ROOT);

            if (!mode.equals("classic") && !mode.equals("time")) {
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

        // =========================
        // /move target <name> <pattern>
        // =========================
        if (cmd.equals("move")) {
            if (args.length != 3 ||
                    !args[0].equalsIgnoreCase("target")) {

                sender.sendMessage(
                        "§c使い方: /move target <名前> <left_right|up_down|circle|random|stop>"
                );
                return true;
            }

            TargetData target = targets.get(targetKey(args[1]));

            if (target == null) {
                sender.sendMessage(
                        "§cその名前のターゲットは存在しません: §e" + args[1]
                );
                return true;
            }

            String pattern = args[2].toLowerCase(Locale.ROOT);

            if (!isValidMovePattern(pattern)) {
                sender.sendMessage("§cその移動パターンは存在しません");
                return true;
            }

            target.movePattern = pattern;

            // random開始時は最初の幅を4.5～5.0にしておく
            if (pattern.equals("random")) {
                target.randomRange = 4.5 + Math.random() * 0.5;
                target.randomCounter = 0;
            }

            sender.sendMessage(
                    "§aターゲット §e" + target.name +
                            " §aの移動を §e" + pattern + "§a に変更しました"
            );

            return true;
        }

        // =========================
        // /delete target <name>
        // =========================
        if (cmd.equals("delete")) {
            if (args.length != 2 ||
                    !args[0].equalsIgnoreCase("target")) {

                sender.sendMessage(
                        "§c使い方: /delete target <名前>"
                );
                return true;
            }

            String key = targetKey(args[1]);

            if (!targets.containsKey(key) &&
                    !respawningTargets.contains(key)) {

                sender.sendMessage(
                        "§cその名前のターゲットは存在しません: §e" + args[1]
                );
                return true;
            }

            removeTarget(args[1]);

            sender.sendMessage(
                    "§aターゲット §e" + args[1] + " §aを削除しました"
            );

            for (Player p : Bukkit.getOnlinePlayers()) {
                updateUI(p);
            }

            return true;
        }

        // =========================
        // /spawn target <name>
        // =========================
        if (cmd.equals("spawn")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cプレイヤーのみ使用できます");
                return true;
            }

            if (args.length != 2 ||
                    !args[0].equalsIgnoreCase("target")) {

                p.sendMessage("§c使い方: /spawn target <名前>");
                return true;
            }

            String name = args[1];
            String key = targetKey(name);

            if (targets.containsKey(key) ||
                    respawningTargets.contains(key)) {

                p.sendMessage(
                        "§cその名前のターゲットはすでに存在します: §e" + name
                );
                return true;
            }

            spawnTarget(name, p.getLocation());

            p.sendMessage(
                    "§aターゲット §e" + name + " §aをスポーンしました"
            );

            for (Player online : Bukkit.getOnlinePlayers()) {
                updateUI(online);
            }

            return true;
        }

        // =========================
        // /lv target <name> <number>
        // =========================
        if (cmd.equals("lv")) {
            if (args.length != 3 ||
                    !args[0].equalsIgnoreCase("target")) {

                sender.sendMessage(
                        "§c使い方: /lv target <名前> <数字>"
                );
                return true;
            }

            TargetData target = targets.get(targetKey(args[1]));

            if (target == null) {
                sender.sendMessage(
                        "§cその名前のターゲットは存在しません: §e" + args[1]
                );
                return true;
            }

            int lv;

            try {
                lv = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cレベルには数字を入力してください");
                return true;
            }

            if (lv < 1) lv = 1;

            setTargetLevel(target, lv);

            sender.sendMessage(
                    "§aターゲット §e" + target.name +
                            " §aのLVを §e" + target.level + "§a に設定しました"
            );

            for (Player p : Bukkit.getOnlinePlayers()) {
                updateUI(p);
            }

            return true;
        }

        return false;
    }

    // =====================================================
    // TAB COMPLETE
    // =====================================================
    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args) {

        String cmd = command.getName().toLowerCase(Locale.ROOT);

        // /targetname は引数なし
        if (cmd.equals("targetname")) {
            return Collections.emptyList();
        }

        // =========================
        // /move target <name> <pattern>
        // =========================
        if (cmd.equals("move")) {
            if (args.length == 1) {
                return filterTabComplete(
                        Collections.singletonList("target"),
                        args[0]
                );
            }

            if (args.length == 2 &&
                    args[0].equalsIgnoreCase("target")) {

                return filterTabComplete(
                        getTargetNames(),
                        args[1]
                );
            }

            if (args.length == 3 &&
                    args[0].equalsIgnoreCase("target")) {

                return filterTabComplete(
                        Arrays.asList(
                                "left_right",
                                "up_down",
                                "circle",
                                "random",
                                "stop"
                        ),
                        args[2]
                );
            }

            return Collections.emptyList();
        }

        // =========================
        // /mode
        // =========================
        if (cmd.equals("mode")) {
            if (args.length == 1) {
                return filterTabComplete(
                        Arrays.asList("classic", "time"),
                        args[0]
                );
            }

            return Collections.emptyList();
        }

        // =========================
        // /spawn target <name>
        // =========================
        if (cmd.equals("spawn")) {
            if (args.length == 1) {
                return filterTabComplete(
                        Collections.singletonList("target"),
                        args[0]
                );
            }

            // 名前は自由入力なので、ここでは候補を出さない
            return Collections.emptyList();
        }

        // =========================
        // /delete target <name>
        // =========================
        if (cmd.equals("delete")) {
            if (args.length == 1) {
                return filterTabComplete(
                        Collections.singletonList("target"),
                        args[0]
                );
            }

            if (args.length == 2 &&
                    args[0].equalsIgnoreCase("target")) {

                return filterTabComplete(
                        getAllTargetNamesIncludingRespawning(),
                        args[1]
                );
            }

            return Collections.emptyList();
        }

        // =========================
        // /lv target <name> <number>
        // =========================
        if (cmd.equals("lv")) {
            if (args.length == 1) {
                return filterTabComplete(
                        Collections.singletonList("target"),
                        args[0]
                );
            }

            if (args.length == 2 &&
                    args[0].equalsIgnoreCase("target")) {

                return filterTabComplete(
                        getTargetNames(),
                        args[1]
                );
            }

            return Collections.emptyList();
        }

        return Collections.emptyList();
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private String targetKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private boolean isValidMovePattern(String pattern) {
        return pattern.equals("left_right") ||
                pattern.equals("up_down") ||
                pattern.equals("circle") ||
                pattern.equals("random") ||
                pattern.equals("stop");
    }

    private List<String> getTargetNames() {
        List<String> names = new ArrayList<>();

        for (TargetData target : targets.values()) {
            names.add(target.name);
        }

        return names;
    }

    private List<String> getAllTargetNamesIncludingRespawning() {
        List<String> names = getTargetNames();

        for (String key : respawningTargets) {
            if (!names.contains(key)) {
                names.add(key);
            }
        }

        return names;
    }

    private List<String> filterTabComplete(
            List<String> options,
            String input) {

        List<String> result = new ArrayList<>();

        String lowerInput = input.toLowerCase(Locale.ROOT);

        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lowerInput)) {
                result.add(option);
            }
        }

        return result;
    }
}
