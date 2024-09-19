package com.gamingmesh.jobs.config;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.container.ExploreChunk;
import com.gamingmesh.jobs.container.ExploreRegion;
import com.gamingmesh.jobs.container.ExploreRespond;
import com.gamingmesh.jobs.container.JobsWorld;
import com.gamingmesh.jobs.dao.JobsDAO.ExploreDataTableFields;
import com.gamingmesh.jobs.i18n.Language;
import com.gamingmesh.jobs.stuff.Util;

import net.Zrips.CMILib.Messages.CMIMessages;
import net.Zrips.CMILib.PersistentData.CMIChunkPersistentDataContainer;

@Deprecated
public class ExploreManager {

    private final Map<String, Map<String, ExploreRegion>> worlds = new HashMap<>();
    private boolean exploreEnabled = false;
    private int playerAmount = 1;

    public int getPlayerAmount() {
        return playerAmount;
    }

    public void setPlayerAmount(int amount) {
        if (playerAmount < amount)
            playerAmount = amount;
    }

    public boolean isExploreEnabled() {
        return exploreEnabled;
    }

    public void setExploreEnabled() {
        exploreEnabled = true;
    }

    public List<Integer> getVisitors(Chunk chunk) {

        Map<String, ExploreRegion> exploreRegion = worlds.get(chunk.getWorld().getName());

        if (exploreRegion == null)
            return null;

        int RegionX = (int) Math.floor(chunk.getX() / 32D);
        int RegionZ = (int) Math.floor(chunk.getZ() / 32D);
        ExploreRegion region = exploreRegion.get(RegionX + ":" + RegionZ);
        if (region == null)
            return null;

        ExploreChunk echunk = region.getChunk(chunk);

        if (echunk == null)
            return null;

        if (Jobs.getGCManager().ExploreCompact && echunk.isFullyExplored()) {
            return Collections.emptyList();
        }

        return echunk.getPlayers();
    }

    public void load() {
        if (!exploreEnabled)
            return;

        if (Jobs.getGeneralConfigManager().ExploreSaveIntoDatabase) {
            CMIMessages.consoleMessage("&eLoading explorer data");
            Long time = System.currentTimeMillis();
            Jobs.getJobsDAO().loadExplore();
            int size = getSize();
            CMIMessages.consoleMessage("&eLoaded explorer data" + (size != 0 ? " (&6" + size + "&e)" : " ") + " in " + (System.currentTimeMillis() - time) + " ms");
        }
    }

    public Map<String, Map<String, ExploreRegion>> getWorlds() {
        return worlds;
    }

    public int getSize() {
        int i = 0;
        for (Map<String, ExploreRegion> one : worlds.values()) {
            for (Entry<String, ExploreRegion> chunks : one.entrySet()) {
                i += chunks.getValue().getChunks().size();
            }
        }
        return i;
    }

    public ExploreRespond chunkRespond(Player player, Chunk chunk) {
        return chunkRespond(Jobs.getPlayerManager().getJobsPlayer(player).getUserId(), chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public ExploreRespond chunkRespond(int playerId, Chunk chunk) {
        return chunkRespond(playerId, chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public ExploreRespond chunkRespond(int playerId, String world, int x, int z) {
        Map<String, ExploreRegion> eRegions = worlds.getOrDefault(world, new HashMap<String, ExploreRegion>());

        int RegionX = (int) Math.floor(x / 32D);
        int RegionZ = (int) Math.floor(z / 32D);

        ExploreRegion region = eRegions.get(RegionX + ":" + RegionZ);
        if (region == null) {
            region = new ExploreRegion(RegionX, RegionZ);
        }

        int chunkRelativeX = (RegionX * 32) - x;
        int chunkRelativeZ = (RegionZ * 32) - z;

        ExploreChunk chunk = region.getChunk(chunkRelativeX, chunkRelativeZ);
        if (chunk == null) {
            chunk = new ExploreChunk();
            region.addChunk(chunkRelativeX, chunkRelativeZ, chunk);
        }

        eRegions.put(RegionX + ":" + RegionZ, region);

        worlds.put(world, eRegions);

        return chunk.addPlayer(playerId);
    }

    public void load(ResultSet res) {
        try {
            String worldName = res.getString(ExploreDataTableFields.worldname.getCollumn());

            JobsWorld jobsWorld = Util.getJobsWorld(worldName);
            if (jobsWorld == null)
                jobsWorld = Util.getJobsWorld(res.getInt(ExploreDataTableFields.worldid.getCollumn()));

            if (jobsWorld == null)
                return;

            int x = res.getInt(ExploreDataTableFields.chunkX.getCollumn());
            int z = res.getInt(ExploreDataTableFields.chunkZ.getCollumn());
            String names = res.getString(ExploreDataTableFields.playerNames.getCollumn());
            int id = res.getInt("id");

            Map<String, ExploreRegion> eRegions = worlds.getOrDefault(jobsWorld.getName(), new HashMap<String, ExploreRegion>());

            int RegionX = (int) Math.floor(x / 32D);
            int RegionZ = (int) Math.floor(z / 32D);

            int chunkRelativeX = RegionX * 32 - x;
            int chunkRelativeZ = RegionZ * 32 - z;

            ExploreRegion region = eRegions.get(RegionX + ":" + RegionZ);
            if (region == null) {
                region = new ExploreRegion(RegionX, RegionZ);
            }
            ExploreChunk chunk = region.getChunk(chunkRelativeX, chunkRelativeZ);
            if (chunk == null) {
                chunk = new ExploreChunk();
                region.addChunk(chunkRelativeX, chunkRelativeZ, chunk);
            }
            chunk.deserializeNames(names);
            chunk.setDbId(id);

            eRegions.put(RegionX + ":" + RegionZ, region);
            worlds.put(jobsWorld.getName(), eRegions);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void resetRegion(String worldname) {
        CMIMessages.consoleMessage("&eReseting explorer data. World: " + worldname);

        Map<String, Map<String, ExploreRegion>> worlds = getWorlds();
        worlds.put(worldname, new HashMap<String, ExploreRegion>());

        boolean r = Jobs.getJobsDAO().deleteExploredWorld(worldname);
        if (!r) {
            CMIMessages.consoleMessage("&eFailed in DAO.");
            return;
        }

        CMIMessages.consoleMessage("&eCompleted to reset explorer data.");
    }
}
