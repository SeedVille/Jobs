package com.gamingmesh.jobs.Signs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.config.CommentedYamlConfiguration;
import com.gamingmesh.jobs.container.TopList;

public class SignUtil {

    public SignInfo Signs = new SignInfo();
    private Jobs plugin;

    public SignUtil(Jobs plugin) {
	this.plugin = plugin;
    }

    public SignInfo getSigns() {
	return Signs;
    }

    // Sign file
    public void LoadSigns() {
	Signs.GetAllSigns().clear();
	File file = new File(plugin.getDataFolder(), "Signs.yml");
	YamlConfiguration f = YamlConfiguration.loadConfiguration(file);

	if (!f.isConfigurationSection("Signs"))
	    return;

	ConfigurationSection ConfCategory = f.getConfigurationSection("Signs");
	ArrayList<String> categoriesList = new ArrayList<>(ConfCategory.getKeys(false));
	if (categoriesList.size() == 0)
	    return;
	for (String category : categoriesList) {
	    ConfigurationSection NameSection = ConfCategory.getConfigurationSection(category);
	    com.gamingmesh.jobs.Signs.Sign newTemp = new com.gamingmesh.jobs.Signs.Sign();
	    newTemp.setCategory(Integer.valueOf(category));
	    newTemp.setWorld(NameSection.getString("World"));
	    newTemp.setX(NameSection.getDouble("X"));
	    newTemp.setY(NameSection.getDouble("Y"));
	    newTemp.setZ(NameSection.getDouble("Z"));
	    newTemp.setNumber(NameSection.getInt("Number"));
	    newTemp.setJobName(NameSection.getString("JobName"));
	    newTemp.setSpecial(NameSection.getBoolean("Special"));
	    Signs.addSign(newTemp);
	}
	return;
    }

    // Signs save file
    public void saveSigns() {
	File f = new File(plugin.getDataFolder(), "Signs.yml");
	YamlConfiguration conf = YamlConfiguration.loadConfiguration(f);

	CommentedYamlConfiguration writer = new CommentedYamlConfiguration();
	conf.options().copyDefaults(true);

	writer.addComment("Signs", "DO NOT EDIT THIS FILE BY HAND!");

	if (!conf.isConfigurationSection("Signs"))
	    conf.createSection("Signs");

	for (com.gamingmesh.jobs.Signs.Sign one : Signs.GetAllSigns()) {
	    String path = "Signs." + String.valueOf(one.GetCategory());
	    writer.set(path + ".World", one.GetWorld());
	    writer.set(path + ".X", one.GetX());
	    writer.set(path + ".Y", one.GetY());
	    writer.set(path + ".Z", one.GetZ());
	    writer.set(path + ".Number", one.GetNumber());
	    writer.set(path + ".JobName", one.GetJobName());
	    writer.set(path + ".Special", one.isSpecial());
	}

	try {
	    writer.save(f);
	} catch (IOException e) {
	    e.printStackTrace();
	}
	return;
    }

    public boolean SignUpdate(String JobName) {
	List<com.gamingmesh.jobs.Signs.Sign> Copy = new ArrayList<>(Signs.GetAllSigns().size());
	for (com.gamingmesh.jobs.Signs.Sign foo : Signs.GetAllSigns()) {
	    Copy.add(foo);
	}
	int timelapse = 1;
	for (com.gamingmesh.jobs.Signs.Sign one : Copy) {
	    String SignJobName = one.GetJobName();

	    if (JobName.equalsIgnoreCase(SignJobName)) {
		String SignsWorld = one.GetWorld();
		double SignsX = one.GetX();
		double SignsY = one.GetY();
		double SignsZ = one.GetZ();
		int number = one.GetNumber() - 1;

		List<TopList> PlayerList = new ArrayList<>();
		if (!JobName.equalsIgnoreCase("gtoplist")) {
		    PlayerList = Jobs.getJobsDAO().toplist(SignJobName, number);
		} else {
		    PlayerList = Jobs.getJobsDAO().getGlobalTopList(number);
		}
		if (PlayerList.size() != 0) {
		    World world = Bukkit.getWorld(SignsWorld);
		    if (world == null)
			continue;
		    Location nloc = new Location(world, SignsX, SignsY, SignsZ);
		    Block block = nloc.getBlock();
		    if (!(block.getState() instanceof org.bukkit.block.Sign)) {
			Signs.GetAllSigns().remove(one);
			saveSigns();
		    } else {
			org.bukkit.block.Sign sign = (org.bukkit.block.Sign) block.getState();
			if (!one.isSpecial()) {
			    for (int i = 0; i < 4; i++) {
				if (i >= PlayerList.size()) {
				    break;
				}
				String PlayerName = PlayerList.get(i).getPlayerName();

				if (PlayerName != null && PlayerName.length() > 8) {
				    String PlayerNameStrip = PlayerName.split("(?<=\\G.{7})")[0];
				    PlayerName = PlayerNameStrip + "~";
				}

				if (PlayerName == null)
				    PlayerName = "Unknown";

				String line = Jobs.getLanguage().getMessage("signs.List");
				line = line.replace("[number]", String.valueOf(i + number + 1));
				line = line.replace("[player]", PlayerName);
				line = line.replace("[level]", String.valueOf(PlayerList.get(i).getLevel()));

				sign.setLine(i, line);
			    }
			    sign.update();
			    UpdateHead(sign, PlayerList.get(0).getPlayerName(), timelapse);
			} else {
			    String PlayerName = PlayerList.get(0).getPlayerName();
			    if (PlayerName.length() > 8) {
				String PlayerNameStrip = PlayerName.split("(?<=\\G.{7})")[0];
				PlayerName = PlayerNameStrip + "~";
			    }
			    String line1 = Jobs.getLanguage().getMessage("signs.SpecialList.p" + one.GetNumber(),
				"[number]", one.GetNumber() + number + 1,
				"[player]", PlayerName,
				"[level]", PlayerList.get(0).getLevel(),
				"[job]", JobName);

			    sign.setLine(0, line1);

			    line1 = Jobs.getLanguage().getMessage("signs.SpecialList.name",
				"[number]", one.GetNumber() + number + 1,
				"[player]", PlayerName,
				"[level]", PlayerList.get(0).getLevel(),
				"[job]", JobName);

			    sign.setLine(1, line1);

			    line1 = Jobs.getLanguage().getMessage("signs.SpecialList.level",
				"[number]", one.GetNumber() + number + 1,
				"[player]", PlayerName,
				"[level]", PlayerList.get(0).getLevel(),
				"[job]", JobName);

			    sign.setLine(2, line1);

			    line1 = Jobs.getLanguage().getMessage("signs.SpecialList.bottom",
				"[number]", one.GetNumber() + number + 1,
				"[player]", PlayerName,
				"[level]", PlayerList.get(0).getLevel(),
				"[job]", JobName);

			    sign.setLine(3, line1);
			    sign.update();
			    UpdateHead(sign, PlayerList.get(0).getPlayerName(), timelapse);
			}

			timelapse++;
		    }
		}
	    }
	}
	return true;
    }

    public void UpdateHead(final org.bukkit.block.Sign sign, final String Playername, final int timelapse) {

	Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
	    @Override
	    public void run() {

		org.bukkit.material.Sign signMat = (org.bukkit.material.Sign) sign.getData();
		BlockFace directionFacing = signMat.getFacing();

		Location loc = sign.getLocation().clone();
		loc.add(0, 1, 0);

		if (Playername == null)
		    return;

		Block block = loc.getBlock();

		if (block == null || !(block.getState() instanceof Skull))
		    loc.add(directionFacing.getOppositeFace().getModX(), 0, directionFacing.getOppositeFace().getModZ());

		block = loc.getBlock();

		if (block == null || !(block.getState() instanceof Skull))
		    return;

		Skull skull = (Skull) block.getState();

		if (skull == null)
		    return;

		skull.setOwner(Playername);
		skull.update();

		return;
	    }
	}, timelapse * Jobs.getGCManager().InfoUpdateInterval * 20L);
    }
}
