package com.example.xpose;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public class Xpose implements ClientModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("Xpose");
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("xpose_blocks.json");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Xpose initializing - loading config from: {}", CONFIG_PATH);
        try {
            RevealState.load(CONFIG_PATH);
            LOGGER.info("Loaded {} block(s)", RevealState.getHiddenCount());
        } catch (Throwable t) {
            LOGGER.warn("Failed to load Xpose config, starting with empty list", t);
        }

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = ClientCommandManager.literal("xpose");

            // on
            root.then(ClientCommandManager.literal("on").executes(ctx -> {
                RevealState.setEnabled(true);
                ctx.getSource().sendFeedback(Text.of("§aXpose enabled"));
                LOGGER.info("Xpose enabled via command");
                return 1;
            }));

            // off
            root.then(ClientCommandManager.literal("off").executes(ctx -> {
                RevealState.setEnabled(false);
                ctx.getSource().sendFeedback(Text.of("§cXpose disabled"));
                LOGGER.info("Xpose disabled via command");
                return 1;
            }));

            // add <block>
            root.then(ClientCommandManager.literal("add")
                    .then(ClientCommandManager.argument("block", StringArgumentType.string())
                            .executes(ctx -> {
                                String raw = StringArgumentType.getString(ctx, "block");
                                try {
                                    boolean added = RevealState.addByString(raw);
                                    if (added) {
                                        RevealState.save(CONFIG_PATH);
                                        ctx.getSource().sendFeedback(Text.of("§aAdded: " + RevealState.normalizeIdString(raw)));
                                    } else {
                                        ctx.getSource().sendError(Text.of("§cInvalid or already present block: " + raw));
                                    }
                                } catch (Exception e) {
                                    LOGGER.warn("Error adding block: {}", raw, e);
                                    ctx.getSource().sendError(Text.of("§cError adding block: " + e.getMessage()));
                                }
                                return 1;
                            })));

            // remove <block>
            root.then(ClientCommandManager.literal("remove")
                    .then(ClientCommandManager.argument("block", StringArgumentType.string())
                            .executes(ctx -> {
                                String raw = StringArgumentType.getString(ctx, "block");
                                try {
                                    boolean removed = RevealState.removeByString(raw);
                                    if (removed) {
                                        RevealState.save(CONFIG_PATH);
                                        ctx.getSource().sendFeedback(Text.of("§eRemoved: " + RevealState.normalizeIdString(raw)));
                                    } else {
                                        ctx.getSource().sendError(Text.of("§cBlock not in list: " + raw));
                                    }
                                } catch (Exception e) {
                                    LOGGER.warn("Error removing block: {}", raw, e);
                                    ctx.getSource().sendError(Text.of("§cError removing block: " + e.getMessage()));
                                }
                                return 1;
                            })));

            // list
            root.then(ClientCommandManager.literal("list").executes(ctx -> {
                var ids = RevealState.listIds();
                if (ids.isEmpty()) {
                    ctx.getSource().sendFeedback(Text.of("§7Xpose list: (none)"));
                } else {
                    ctx.getSource().sendFeedback(Text.of("§bXpose list:"));
                    for (String id : ids) ctx.getSource().sendFeedback(Text.of(" - " + id));
                }
                return 1;
            }));

            dispatcher.register(root);
        });
    }
}