package itzglecni.customhitboxes.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import itzglecni.customhitboxes.config.ConfigManager;
import itzglecni.customhitboxes.config.ModConfig;
import itzglecni.customhitboxes.config.TeamNames;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// Client-side /hbteam command. Named hbteam (not team) so it never hides the server's own /team command.
public final class TeamCommands {
    private TeamCommands() {
    }

    // Names of everyone on the server (tab list) who is not on your team yet.
    private static final SuggestionProvider<FabricClientCommandSource> ONLINE_NOT_ON_TEAM = (context, builder) -> {
        List<String> team = ConfigManager.getConfig().teamPlayers;
        String own = context.getSource().getClient().getUser().getName();
        List<String> names = new ArrayList<>();
        ClientPacketListener connection = context.getSource().getClient().getConnection();
        if (connection != null) {
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                String name = info.getProfile().name();
                if (name != null && !TeamNames.contains(team, name) && !name.equalsIgnoreCase(own)) {
                    names.add(name);
                }
            }
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private static final SuggestionProvider<FabricClientCommandSource> TEAM_NAMES =
        (context, builder) -> SharedSuggestionProvider.suggest(ConfigManager.getConfig().teamPlayers, builder);

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("hbteam")
            .executes(TeamCommands::help)
            .then(literal("add")
                .then(argument("player", StringArgumentType.word())
                    .suggests(ONLINE_NOT_ON_TEAM)
                    .executes(TeamCommands::add)))
            .then(literal("remove")
                .then(argument("player", StringArgumentType.word())
                    .suggests(TEAM_NAMES)
                    .executes(TeamCommands::remove)))
            .then(literal("list").executes(TeamCommands::list))
            .then(literal("clear").executes(TeamCommands::clear)));
    }

    private static int help(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        source.sendFeedback(prefix().append(Component.literal("Team Color commands:").withStyle(ChatFormatting.WHITE)));
        source.sendFeedback(usage("/hbteam add <player>", "Add a player to your team"));
        source.sendFeedback(usage("/hbteam remove <player>", "Remove a player from your team"));
        source.sendFeedback(usage("/hbteam list", "Show everyone on your team"));
        source.sendFeedback(usage("/hbteam clear", "Remove everyone from your team"));
        return 1;
    }

    private static int add(CommandContext<FabricClientCommandSource> context) {
        String name = StringArgumentType.getString(context, "player");
        ModConfig config = ConfigManager.getConfig();
        if (name.equalsIgnoreCase(context.getSource().getClient().getUser().getName())) {
            context.getSource().sendError(prefix().append("You can't add yourself to your team."));
            return 0;
        }
        if (!TeamNames.isValid(name)) {
            context.getSource().sendError(prefix().append("\"" + name + "\" is not a valid player name."));
            return 0;
        }
        if (!TeamNames.add(config.teamPlayers, name)) {
            context.getSource().sendError(prefix().append(name + " is already on your team."));
            return 0;
        }
        ConfigManager.save();
        context.getSource().sendFeedback(prefix()
            .append(Component.literal("Added ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(name).withStyle(ChatFormatting.GREEN))
            .append(Component.literal(" to your team.").withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private static int remove(CommandContext<FabricClientCommandSource> context) {
        String name = StringArgumentType.getString(context, "player");
        String removed = TeamNames.remove(ConfigManager.getConfig().teamPlayers, name);
        if (removed == null) {
            context.getSource().sendError(prefix().append(name + " is not on your team."));
            return 0;
        }
        ConfigManager.save();
        context.getSource().sendFeedback(prefix()
            .append(Component.literal("Removed ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(removed).withStyle(ChatFormatting.RED))
            .append(Component.literal(" from your team.").withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private static int list(CommandContext<FabricClientCommandSource> context) {
        List<String> team = ConfigManager.getConfig().teamPlayers;
        if (team.isEmpty()) {
            context.getSource().sendFeedback(prefix().append(Component.literal("Your team is empty. Add someone with /hbteam add <player>.").withStyle(ChatFormatting.GRAY)));
            return 0;
        }
        context.getSource().sendFeedback(prefix()
            .append(Component.literal("Your team (" + team.size() + "): ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(String.join(", ", team)).withStyle(ChatFormatting.WHITE)));
        return team.size();
    }

    private static int clear(CommandContext<FabricClientCommandSource> context) {
        List<String> team = ConfigManager.getConfig().teamPlayers;
        int count = team.size();
        team.clear();
        ConfigManager.save();
        context.getSource().sendFeedback(prefix().append(Component.literal(
            count == 0 ? "Your team was already empty." : "Removed all " + count + " players from your team.").withStyle(ChatFormatting.GRAY)));
        return count;
    }

    private static MutableComponent prefix() {
        return Component.literal("[Hitbox+] ").withStyle(ChatFormatting.AQUA);
    }

    private static MutableComponent usage(String command, String meaning) {
        return Component.literal("  " + command).withStyle(ChatFormatting.YELLOW)
            .append(Component.literal("  " + meaning).withStyle(ChatFormatting.GRAY));
    }
}
