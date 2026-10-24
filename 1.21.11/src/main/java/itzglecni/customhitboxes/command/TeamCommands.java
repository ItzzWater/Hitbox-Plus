package itzglecni.customhitboxes.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import itzglecni.customhitboxes.config.ConfigManager;
import itzglecni.customhitboxes.config.ModConfig;
import itzglecni.customhitboxes.config.TeamNames;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.util.Formatting;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;

import java.util.ArrayList;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

// Client-side /hbteam command. Named hbteam (not team) so it never hides the server's own /team command.
public final class TeamCommands {
    private TeamCommands() {
    }

    // Names of everyone on the server (tab list) who is not on your team yet.
    private static final SuggestionProvider<FabricClientCommandSource> ONLINE_NOT_ON_TEAM = (context, builder) -> {
        List<String> team = ConfigManager.getConfig().teamPlayers;
        String own = context.getSource().getClient().getSession().getUsername();
        List<String> names = new ArrayList<>();
        ClientPlayNetworkHandler connection = context.getSource().getClient().getNetworkHandler();
        if (connection != null) {
            for (PlayerListEntry info : connection.getPlayerList()) {
                String name = info.getProfile().name();
                if (name != null && !TeamNames.contains(team, name) && !name.equalsIgnoreCase(own)) {
                    names.add(name);
                }
            }
        }
        return CommandSource.suggestMatching(names, builder);
    };

    private static final SuggestionProvider<FabricClientCommandSource> TEAM_NAMES =
        (context, builder) -> CommandSource.suggestMatching(ConfigManager.getConfig().teamPlayers, builder);

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
        source.sendFeedback(prefix().append(Text.literal("Team Color commands:").formatted(Formatting.WHITE)));
        source.sendFeedback(usage("/hbteam add <player>", "Add a player to your team"));
        source.sendFeedback(usage("/hbteam remove <player>", "Remove a player from your team"));
        source.sendFeedback(usage("/hbteam list", "Show everyone on your team"));
        source.sendFeedback(usage("/hbteam clear", "Remove everyone from your team"));
        return 1;
    }

    private static int add(CommandContext<FabricClientCommandSource> context) {
        String name = StringArgumentType.getString(context, "player");
        ModConfig config = ConfigManager.getConfig();
        if (name.equalsIgnoreCase(context.getSource().getClient().getSession().getUsername())) {
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
            .append(Text.literal("Added ").formatted(Formatting.GRAY))
            .append(Text.literal(name).formatted(Formatting.GREEN))
            .append(Text.literal(" to your team.").formatted(Formatting.GRAY)));
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
            .append(Text.literal("Removed ").formatted(Formatting.GRAY))
            .append(Text.literal(removed).formatted(Formatting.RED))
            .append(Text.literal(" from your team.").formatted(Formatting.GRAY)));
        return 1;
    }

    private static int list(CommandContext<FabricClientCommandSource> context) {
        List<String> team = ConfigManager.getConfig().teamPlayers;
        if (team.isEmpty()) {
            context.getSource().sendFeedback(prefix().append(Text.literal("Your team is empty. Add someone with /hbteam add <player>.").formatted(Formatting.GRAY)));
            return 0;
        }
        context.getSource().sendFeedback(prefix()
            .append(Text.literal("Your team (" + team.size() + "): ").formatted(Formatting.GRAY))
            .append(Text.literal(String.join(", ", team)).formatted(Formatting.WHITE)));
        return team.size();
    }

    private static int clear(CommandContext<FabricClientCommandSource> context) {
        List<String> team = ConfigManager.getConfig().teamPlayers;
        int count = team.size();
        team.clear();
        ConfigManager.save();
        context.getSource().sendFeedback(prefix().append(Text.literal(
            count == 0 ? "Your team was already empty." : "Removed all " + count + " players from your team.").formatted(Formatting.GRAY)));
        return count;
    }

    private static MutableText prefix() {
        return Text.literal("[Hitbox+] ").formatted(Formatting.AQUA);
    }

    private static MutableText usage(String command, String meaning) {
        return Text.literal("  " + command).formatted(Formatting.YELLOW)
            .append(Text.literal("  " + meaning).formatted(Formatting.GRAY));
    }
}
