package me.xmrvizzy.skyblocker.skyblock.api;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.xmrvizzy.skyblocker.skyblock.api.records.PlayerProfiles;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.LiteralText;

public class StatsCommand {
    public static void init(){
        ClientCommandManager.DISPATCHER.register(ClientCommandManager.literal("skyblocker")
                .then(ClientCommandManager.literal("debug")
                        .then(ClientCommandManager.literal("stats").then(ClientCommandManager.argument("username", StringArgumentType.string())
                                .executes(context -> {
                                    new Thread(() -> {
                                        PlayerProfiles playerProfiles = ProfileUtils.getProfiles(StringArgumentType.getString(context, "username"));
                                        for (String profileId : playerProfiles.profiles().keySet()){
                                            MinecraftClient.getInstance().player.sendMessage(new LiteralText(playerProfiles.profiles().get(profileId).cuteName())
                                                    .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(playerProfiles.profiles().get(profileId))))), false);
                                        }
                                    }).start();
                                    return 1;
                                })))));
    }
}
