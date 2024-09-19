package com.gamingmesh.jobs.config;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.container.*;
import net.Zrips.CMILib.ActionBar.CMIActionBar;
import net.Zrips.CMILib.Container.CMIBlock;
import net.Zrips.CMILib.Container.CMIBlock.Bisect;
import net.Zrips.CMILib.Items.CMIMaterial;
import net.Zrips.CMILib.Version.Schedulers.CMIScheduler;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class BlockProtectionManager {

    private final Map<String, BlockProtection> newMap = new HashMap<>();
    private final ConcurrentHashMap<World, ConcurrentHashMap<String, BlockProtection>> tempCache = new ConcurrentHashMap<>();

    public Map<String, BlockProtection> getMap2() {
        return newMap;
    }

    public int getSize() {
        return this.newMap.size();
    }

    public void add(Block block, Integer cd) {

        if (cd == null || cd == 0)
            return;

        // Assuming that block is bottom part of flower we will add top part to the record too
        CMIMaterial cmat = CMIMaterial.get(block);
        switch (cmat) {
            case LILAC:
            case SUNFLOWER:
            case ROSE_BUSH:
            case PEONY:
                CMIBlock cmb = new CMIBlock(block);
                // We are only interested in this being bottom block as this should never trigger for top part of placed block
                if (cmb.getBisect().equals(Bisect.BOTTOM))
                    add(block.getLocation().clone().add(0, 1, 0), cd, true);
                break;
        }

        add(block, cd, true);
    }

    public void add(Block block, Integer cd, boolean paid) {
        add(block.getLocation(), cd, paid);
    }

    public void add(Location loc, Integer cd) {
        add(loc, cd, true);
    }

    public void add(Location loc, Integer cd, boolean paid) {
        if (cd == null)
            return;
        if (cd != -1)
            addP(loc, System.currentTimeMillis() + (cd * 1000), paid, true);
        else
            addP(loc, -1L, paid, true);
    }

    public BlockProtection addP(Location loc, Long time, boolean paid, boolean cache) {

        if (time == null || time == 0)
            return null;

        BlockProtection Bp = newMap.get(locToKey(loc));

        if (Bp == null)
            Bp = new BlockProtection(DBAction.INSERT, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        else {
            Bp.setAction(DBAction.UPDATE);
            if (Bp.getScheduler() != null)
                Bp.getScheduler().cancel();
        }

        Bp.setPaid(paid);
        Bp.setTime(time);

        // If timer is under 5 min, we can run scheduler to remove it when time comes
        if (time > -1 && (time - System.currentTimeMillis()) / 1000 < 60 * 5)
            Bp.setScheduler(CMIScheduler.runAtLocationLater(loc, () -> remove(loc), (time - System.currentTimeMillis()) / 50));

        newMap.put(locToKey(loc), Bp);

        // Only saving into save cache if timer is higher than 5 minutes
        if (cache && ((time - System.currentTimeMillis()) / 1000 > 60 * 5 || time < 0))
            addToCache(loc, Bp);
        return Bp;
    }

    private void addToCache(Location loc, BlockProtection Bp) {
        if (!Jobs.getGCManager().useBlockProtection)
            return;
        String v = loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        ConcurrentHashMap<String, BlockProtection> locations = tempCache.get(loc.getWorld());
        if (locations == null) {
            locations = new ConcurrentHashMap<>();
            tempCache.put(loc.getWorld(), locations);
        }

        locations.put(v, Bp);
    }

    public void saveCache() {
        if (!Jobs.getGCManager().useBlockProtection)
            return;
        for (Entry<World, ConcurrentHashMap<String, BlockProtection>> one : tempCache.entrySet()) {
            Jobs.getJobsDAO().saveBlockProtection(one.getKey().getName(), one.getValue());
        }
        tempCache.clear();
    }

    public BlockProtection remove(Block block) {
        // In case double plant was destroyed we should remove both blocks from records
        CMIMaterial cmat = CMIMaterial.get(block);
        switch (cmat) {
            case LILAC:
            case SUNFLOWER:
            case ROSE_BUSH:
            case PEONY:
                CMIBlock cmb = new CMIBlock(block);
                if (cmb.getBisect().equals(Bisect.BOTTOM))
                    remove(block.getLocation().clone().add(0, 1, 0));
                else
                    remove(block.getLocation().clone().add(0, -1, 0));
                break;
        }

        return remove(block.getLocation());
    }

    public BlockProtection remove(Location loc) {
        String v = locToKey(loc);
        BlockProtection bp = newMap.get(v);
        if (bp != null)
            bp.setAction(DBAction.DELETE);
        if (bp != null && bp.getId() < 0) {
            newMap.remove(v);
        }
        return bp;
    }

    public Long getTime(Block block) {
        return getTime(block.getLocation());
    }

    public Long getTime(Location loc) {
        BlockProtection Bp = getBp(loc);
        return Bp == null ? null : Bp.getTime();
    }

    public BlockProtection getBp(Location loc) {
        return newMap.get(locToKey(loc));
    }

    private static String locToKey(Location loc) {
        return (loc.getWorld() == null ? "unknown" : loc.getWorld().getName()) + ":" + loc.getBlockX() + "." + loc.getBlockY() + "." + loc.getBlockZ();
    }

    private static String locToChunk(Location loc) {
        return (int) Math.floor(loc.getBlockX() / 16D) + ":" + (int) Math.floor(loc.getBlockZ() / 16D);
    }

    private static String locToRegion(Location loc) {
        int x = (int) Math.floor(loc.getBlockX() / 16D);
        int z = (int) Math.floor(loc.getBlockZ() / 16D);
        return (int) Math.floor(x / 32D) + ":" + (int) Math.floor(z / 32D);
    }

    @Deprecated
    public Integer getBlockDelayTime(Block block) {
        return Jobs.getExploitManager().getBlockProtectionTime(block);
    }

    @Deprecated
    public boolean isInBp(Block block) {
        return Jobs.getRestrictedBlockManager().restrictedBlocksTimer.containsKey(CMIMaterial.get(block));
    }

    public boolean isBpOk(JobsPlayer player, ActionInfo info, Block block, boolean inform) {
        if (block == null || !Jobs.getGCManager().useBlockProtection)
            return true;

        if (info.getType() == ActionType.BREAK) {
            if (block.hasMetadata("JobsExploit")) {
                //player.sendMessage("This block is protected using Rukes' system!");
                return false;
            }

            BlockProtection bp = getBp(block.getLocation());
            if (bp != null) {
                long time = bp.getTime();
                Integer cd = Jobs.getExploitManager().getBlockProtectionTime(info.getType(), block);

                if (time == -1L) {
                    remove(block);
                    return false;
                }

                if (time < System.currentTimeMillis() && bp.getAction() != DBAction.DELETE) {
                    remove(block);
                    return true;
                }

                if ((time > System.currentTimeMillis() || bp.isPaid()) && bp.getAction() != DBAction.DELETE) {
                    if (inform && player.canGetPaid(info)) {
                        int sec = Math.round((time - System.currentTimeMillis()) / 1000L);
                        CMIActionBar.send(player.getPlayer(), Jobs.getLanguage().getMessage("message.blocktimer", "[time]", sec));
                    }

                    return false;
                }

                add(block, cd);

            } else
                add(block, Jobs.getExploitManager().getBlockProtectionTime(info.getType(), block));

        } else if (info.getType() == ActionType.PLACE) {
            BlockProtection bp = getBp(block.getLocation());
            if (bp != null) {
                Long time = bp.getTime();
                Integer cd = Jobs.getExploitManager().getBlockProtectionTime(info.getType(), block);
                if (time != -1L) {
                    if (time < System.currentTimeMillis() && bp.getAction() != DBAction.DELETE) {
                        add(block, cd);
                        return true;
                    }

                    if ((time > System.currentTimeMillis() || bp.isPaid()) && bp.getAction() != DBAction.DELETE) {
                        if (inform && player.canGetPaid(info)) {
                            int sec = Math.round((time - System.currentTimeMillis()) / 1000L);
                            CMIActionBar.send(player.getPlayer(), Jobs.getLanguage().getMessage("message.blocktimer", "[time]", sec));
                        }

                        add(block, cd);
                        return false;
                    }

                    // Lets add protection in any case
                    add(block, cd);
                } else if (bp.isPaid() && bp.getTime() == -1L && cd != null && cd == -1) {
                    add(block, cd);
                    return false;
                } else
                    add(block, cd);
            } else
                add(block, Jobs.getExploitManager().getBlockProtectionTime(info.getType(), block));
        }

        return true;
    }
}
