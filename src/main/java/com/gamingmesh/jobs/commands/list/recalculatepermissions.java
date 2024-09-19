package com.gamingmesh.jobs.commands.list;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.commands.Cmd;
import com.gamingmesh.jobs.container.ActionType;
import com.gamingmesh.jobs.container.Job;
import com.gamingmesh.jobs.container.JobsPlayer;
import com.gamingmesh.jobs.i18n.Language;

import net.Zrips.CMILib.Locale.LC;
import net.Zrips.CMILib.Messages.CMIMessages;

public class recalculatepermissions implements Cmd {

    @Override
    public Boolean perform(Jobs plugin, final CommandSender sender, final String[] args) {

        JobsPlayer jPlayer = null;
        if (args.length >= 1) {
            jPlayer = Jobs.getPlayerManager().getJobsPlayer(args[0]);
        } else if (sender instanceof Player)
            jPlayer = Jobs.getPlayerManager().getJobsPlayer((Player) sender);

        if (jPlayer == null) {
            LC.info_NoPlayer.sendMessage(sender, "[name]", args.length >= 1 ? args[0] : sender.getName());
            return true;
        }

        jPlayer.getPermissionsCache().clear();

        return true;
    }

}
