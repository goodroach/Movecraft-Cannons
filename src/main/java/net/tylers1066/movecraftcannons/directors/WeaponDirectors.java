package net.tylers1066.movecraftcannons.directors;

import at.pavlov.cannons.Cannons;
import at.pavlov.cannons.Enum.InteractAction;
import at.pavlov.cannons.FireCannon;
import at.pavlov.cannons.cannon.Cannon;
import at.pavlov.cannons.event.CannonFireEvent;
import at.pavlov.cannons.event.CannonRedstoneEvent;
import net.countercraft.movecraft.combat.features.directors.DirectorData;
import net.countercraft.movecraft.combat.features.directors.Directors;
import net.countercraft.movecraft.combat.localisation.I18nSupport;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.PlayerCraft;
import net.countercraft.movecraft.craft.SinkingCraft;
import net.countercraft.movecraft.craft.type.CraftType;
import net.countercraft.movecraft.craft.type.property.BooleanProperty;
import net.countercraft.movecraft.util.MathUtils;
import net.tylers1066.movecraftcannons.MovecraftCannons;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static net.countercraft.movecraft.util.ChatUtils.ERROR_PREFIX;

public class WeaponDirectors extends Directors implements Listener {
    public static final NamespacedKey ALLOW_WEAPON_DIRECTOR_SIGN = new NamespacedKey("movecraft-cannons", "allow_weapon_director_sign");;
    private static final String HEADER = "Weapon Director";
    public static int WeaponDirectorRange = 100;
    public final FireCannon fireCannon;
    public final Cannons cannonsPlugin;

    public WeaponDirectors(Cannons cannonsPlugin) {
        super();
        this.cannonsPlugin = cannonsPlugin;
        this.fireCannon = cannonsPlugin.getFireCannon();
    }

    public static void register() {
        CraftType.registerProperty(new BooleanProperty("allowWeaponDirectorSign", ALLOW_WEAPON_DIRECTOR_SIGN, type -> true));
    }

    public static void load(@NotNull FileConfiguration config) {
        WeaponDirectorRange = config.getInt("WeaponDirectorRange", 100);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeftClick(@NotNull PlayerInteractEvent event) {
        if (!event.getAction().toString().contains("LEFT_CLICK")) return;

        Player player = event.getPlayer();
        if (player == null || player.getInventory().getItemInMainHand().getType() != Directors.DirectorTool) return;

        PlayerCraft foundCraft = CraftManager.getInstance().getCraftByPlayer(player);
        if (foundCraft == null) {
            MathUtils.fastNearestCraftToLoc(CraftManager.getInstance().getCrafts(), player.getLocation());
            if (foundCraft == null || foundCraft instanceof SinkingCraft) return;
        }
        if (!(foundCraft instanceof PlayerCraft)) return;

        Set<Cannon> selectedCannons = MovecraftCannons.getInstance().getCannons(foundCraft.getHitBox(), foundCraft.getWorld());

        //This feature is currently unimplemented in movecraft-combat.
/*        HashSet<DirectorData> directorDataSet = getDirectorDataSet(foundCraft);
        DirectorData chosenData = null;
        boolean hasNoSelectedSigns = false;
        for (DirectorData data : directorDataSet) {
            if (!data.getPlayer().equals(player)) continue;
            chosenData = data;
            hasNoSelectedSigns = false;
        }
        if (chosenData == null) hasNoSelectedSigns = true;
        if (hasNoSelectedSigns) {
            selectedCannons = allCannons;
        } else {
            for (Cannon cannon : allCannons) {
                if (!chosenData.getSelectedSigns().contains(cannon.getCannonName().toLowerCase())) continue;
                selectedCannons.add(cannon);
            }
        }*/

        Location location = player.getLocation();
        for (Cannon cannon : selectedCannons) {
            if (cannon.isLoading() || cannon.isFiring()) continue;
            if (!cannon.isLoaded()) {
                cannon.reloadFromChests(player.getUniqueId(), true);
                continue;
            }
            double yawAngle = getNormalizedCannonAngle(cannon, location.getYaw());
            if (yawAngle <= cannon.getMinHorizontalAngle() || yawAngle >= cannon.getMaxHorizontalAngle()) continue;
            cannon.setHorizontalAngle(yawAngle);
            cannon.setVerticalAngle(-location.getPitch());
            fireCannon.playerFiring(cannon, player, InteractAction.fireAfterLoading);
            long cannonFuseTime = (long) (cannon.getCannonDesign().getFuseBurnTime() * 20) + 2;
            Bukkit.getScheduler().runTaskLater(MovecraftCannons.getInstance(), () -> {
                cannon.reloadFromChests(player.getUniqueId(), true);
            }, cannonFuseTime); // 20 ticks (1 second) delay
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCannonRedstoneFire(@NotNull CannonRedstoneEvent event) {
        Cannon cannon = event.getCannon();
        if (cannon == null || !cannon.isFiring()) return;

        Craft foundCraft = MathUtils.fastNearestCraftToLoc(CraftManager.getInstance().getCrafts(), cannon.getLocation());
        if (foundCraft == null || foundCraft instanceof SinkingCraft || !(foundCraft instanceof PlayerCraft)) return;
        if (!MovecraftCannons.getInstance().getCannons(foundCraft.getHitBox(), foundCraft.getWorld()).contains(cannon)) return;
        PlayerCraft craft = (PlayerCraft) foundCraft;

        Player player = craft.getPilot();
        //Unimplmented
/*        HashSet<DirectorData> directorDataSet = getDirectorDataSet(craft);
        for (DirectorData data : directorDataSet) {
            if (!data.getSelectedSigns().contains(cannon.getCannonName().toLowerCase())) continue;
            player = data.getPlayer();
        }
        if (player == null) {
            player = craft.getPilot();
        }*/

        if (player == null || player.getInventory().getItemInMainHand().getType() != Directors.DirectorTool) return;

        Location location = player.getLocation();
        double angle = getNormalizedCannonAngle(cannon, location.getYaw());
        if (angle <= cannon.getMinHorizontalAngle() || angle >= cannon.getMaxHorizontalAngle()) return;
        cannon.setHorizontalAngle(angle);
        cannon.setVerticalAngle(-location.getPitch());
        cannon.reloadFromChests(player.getUniqueId(), true);
    }

    private double getNormalizedCannonAngle(Cannon cannon, double yaw) {
        double referenceAngle;
        double normalizedYaw = (yaw %= 360) >= 0 ? yaw : (yaw + 360);
        switch(cannon.getCannonDirection()) {
            case SOUTH:
                referenceAngle = 0;
                break;
            case WEST:
                referenceAngle = 90;
                break;
            case NORTH:
                referenceAngle = 180;
                break;
            case EAST:
                referenceAngle = 270;
                break;
            default:
                referenceAngle = 0;
        }
        return (normalizedYaw - referenceAngle + 180) % 360 - 180;
    }

//    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
//    public void onSignClick(@NotNull PlayerInteractEvent e) {
//        var action = e.getAction();
//        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK)
//            return;
//
//        Block b = e.getClickedBlock();
//        if (b == null)
//            throw new IllegalStateException();
//        var state = b.getState();
//        if (!(state instanceof Sign))
//            return;
//
//        Sign sign = (Sign) state;
//        if (!ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase(HEADER))
//            return;
//
//
//        PlayerCraft foundCraft = null;
//        for (Craft c : CraftManager.getInstance()) {
//            if (!(c instanceof PlayerCraft))
//                continue;
//            if (!c.getHitBox().contains(MathUtils.bukkit2MovecraftLoc(b.getLocation())))
//                continue;
//            foundCraft = (PlayerCraft) c;
//            break;
//        }
//
//        Player p = e.getPlayer();
//        if (foundCraft == null) {
//            p.sendMessage(ERROR_PREFIX + " " + I18nSupport.getInternationalisedString("Sign - Must Be Part Of Craft"));
//            return;
//        }
//
//        if (!foundCraft.getType().getBoolProperty(ALLOW_WEAPON_DIRECTOR_SIGN)) {
//            p.sendMessage(ERROR_PREFIX + " " + I18nSupport.getInternationalisedString("WeaponDirector - Not Allowed On Craft"));
//            return;
//        }
//
//        if (action == Action.LEFT_CLICK_BLOCK) {
//            if (!isDirector(p))
//                return;
//
//            removeDirector(p);
//            p.sendMessage(I18nSupport.getInternationalisedString("WeaponDirector - No Longer Directing"));
//            e.setCancelled(true);
//            return;
//        }
//
//        clearDirector(p);
//        DirectorData data = addDirector(p, foundCraft, sign.getLine(1).toLowerCase(), sign.getLine(2).toLowerCase(), sign.getLine(3).toLowerCase());
//
//        if (isNodesShared(data)) {
//            p.sendMessage(ERROR_PREFIX + " " + I18nSupport.getInternationalisedString("WeaponDirector - Must Not Share Nodes"));
//            return;
//        }
//
//        p.sendMessage(I18nSupport.getInternationalisedString("WeaponDirector - Directing"));
//    }

}
