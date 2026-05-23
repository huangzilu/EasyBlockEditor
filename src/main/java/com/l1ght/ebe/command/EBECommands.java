package com.l1ght.ebe.command;

import com.l1ght.ebe.network.WorkgroupSyncPayload;
import com.l1ght.ebe.server.permission.PermissionDecision;
import com.l1ght.ebe.server.permission.PermissionFeature;
import com.l1ght.ebe.server.permission.PermissionManager;
import com.l1ght.ebe.server.workgroup.WorkgroupManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class EBECommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ebe")
                .executes(context -> {
                    context.getSource().sendSuccess(
                            () -> Component.translatable("ebe.command.ebe"), false);
                    return 1;
                })
                .then(Commands.literal("permissions")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.literal("global")
                                        .then(Commands.argument("feature", StringArgumentType.word())
                                                .then(Commands.argument("decision", StringArgumentType.word())
                                                        .executes(context -> {
                                                            var feature = PermissionFeature.parse(StringArgumentType.getString(context, "feature"));
                                                            var decision = PermissionDecision.parse(StringArgumentType.getString(context, "decision"));
                                                            PermissionManager.setGlobal(feature, decision);
                                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                                    "ebe.command.permissions.set_global",
                                                                    featureComponent(feature),
                                                                    decisionComponent(decision)
                                                            ), true);
                                                            return 1;
                                                        }))))
                                .then(Commands.literal("player")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .then(Commands.argument("feature", StringArgumentType.word())
                                                        .then(Commands.argument("decision", StringArgumentType.word())
                                                                .executes(context -> {
                                                                    var player = StringArgumentType.getString(context, "player");
                                                                    var feature = PermissionFeature.parse(StringArgumentType.getString(context, "feature"));
                                                                    var decision = PermissionDecision.parse(StringArgumentType.getString(context, "decision"));
                                                                    PermissionManager.setPlayer(player, feature, decision);
                                                                    context.getSource().sendSuccess(() -> Component.translatable(
                                                                            "ebe.command.permissions.set_player",
                                                                            player,
                                                                            featureComponent(feature),
                                                                            decisionComponent(decision)
                                                                    ), true);
                                                                    return 1;
                                                                }))))))
                        .then(Commands.literal("get")
                                .then(Commands.literal("global")
                                        .then(Commands.argument("feature", StringArgumentType.word())
                                                .executes(context -> {
                                                    var feature = PermissionFeature.parse(StringArgumentType.getString(context, "feature"));
                                                    var decision = PermissionManager.getGlobal(feature);
                                                    context.getSource().sendSuccess(() -> Component.translatable(
                                                            "ebe.command.permissions.get_global",
                                                            featureComponent(feature),
                                                            decisionComponent(decision)
                                                    ), false);
                                                    return 1;
                                                })))
                                .then(Commands.literal("player")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .then(Commands.argument("feature", StringArgumentType.word())
                                                        .executes(context -> {
                                                            var player = StringArgumentType.getString(context, "player");
                                                            var feature = PermissionFeature.parse(StringArgumentType.getString(context, "feature"));
                                                            var decision = PermissionManager.getPlayer(player, feature);
                                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                                    "ebe.command.permissions.get_player",
                                                                    player,
                                                                    featureComponent(feature),
                                                                    decisionComponent(decision)
                                                            ), false);
                                                            return 1;
                                                        })))))
                )
                .then(Commands.literal("group")
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("password", StringArgumentType.string())
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    var group = WorkgroupManager.create(
                                                            StringArgumentType.getString(context, "name"),
                                                            StringArgumentType.getString(context, "password"),
                                                            player
                                                    );
                                                    if (group != null) syncWorkgroups(player);
                                                    context.getSource().sendSuccess(() -> group == null
                                                            ? Component.translatable("ebe.command.workgroup.create.failed")
                                                            : Component.translatable("ebe.command.workgroup.create.success", group.name), false);
                                                    return group == null ? 0 : 1;
                                                }))))
                        .then(Commands.literal("join")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("password", StringArgumentType.string())
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    boolean ok = WorkgroupManager.join(
                                                            StringArgumentType.getString(context, "name"),
                                                            StringArgumentType.getString(context, "password"),
                                                            player
                                                    );
                                                    if (ok) syncWorkgroups(player);
                                                    context.getSource().sendSuccess(() -> Component.translatable(ok
                                                            ? "ebe.command.workgroup.join.success"
                                                            : "ebe.command.workgroup.join.failed"), false);
                                                    return ok ? 1 : 0;
                                                }))))
                        .then(Commands.literal("leave")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            boolean ok = WorkgroupManager.leave(StringArgumentType.getString(context, "name"), player);
                                            if (ok) syncWorkgroups(player);
                                            context.getSource().sendSuccess(() -> Component.translatable(ok
                                                    ? "ebe.command.workgroup.leave.success"
                                                    : "ebe.command.workgroup.leave.failed"), false);
                                            return ok ? 1 : 0;
                                        })))
                        .then(Commands.literal("disband")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            boolean ok = WorkgroupManager.disband(StringArgumentType.getString(context, "name"), player);
                                            if (ok) syncWorkgroups(player);
                                            context.getSource().sendSuccess(() -> Component.translatable(ok
                                                    ? "ebe.command.workgroup.disband.success"
                                                    : "ebe.command.workgroup.disband.failed"), true);
                                            return ok ? 1 : 0;
                                        })))
                        .then(Commands.literal("list")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    var groups = WorkgroupManager.groupsFor(player);
                                    syncWorkgroups(player);
                                    String text = groups.stream().map(g -> g.name + " (" + g.members.size() + ")").reduce((a, b) -> a + ", " + b).orElse("");
                                    context.getSource().sendSuccess(() -> groups.isEmpty()
                                            ? Component.translatable("ebe.command.workgroup.list.empty")
                                            : Component.translatable("ebe.command.workgroup.list", text), false);
                                    return groups.size();
                                }))
                        .then(Commands.literal("kick")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    boolean ok = WorkgroupManager.kick(
                                                            StringArgumentType.getString(context, "name"),
                                                            StringArgumentType.getString(context, "player"),
                                                            player);
                                                    if (ok) syncWorkgroups(player);
                                                    context.getSource().sendSuccess(() -> Component.translatable(ok
                                                            ? "ebe.command.workgroup.kick.success"
                                                            : "ebe.command.workgroup.kick.failed"), false);
                                                    return ok ? 1 : 0;
                                                }))))
                )
        );
    }

    private static void syncWorkgroups(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new WorkgroupSyncPayload(WorkgroupManager.toClientJson(player)));
    }

    private static Component featureComponent(PermissionFeature feature) {
        return Component.translatable("ebe.permission." + feature.id());
    }

    private static Component decisionComponent(PermissionDecision decision) {
        return Component.translatable(switch (decision) {
            case ALLOW -> "ebe.permission.allow";
            case DENY -> "ebe.permission.deny";
            case DEFAULT -> "ebe.permission.default";
        });
    }
}
