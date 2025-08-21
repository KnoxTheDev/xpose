package com.example.xpose;

import com.google.gson.*;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe hold of enabled flag + set of block Identifiers to hide.
 * Persistence to simple JSON array of strings at provided path.
 */
public final class RevealState {
    private static final Logger LOGGER = LogManager.getLogger("Xpose-RevealState");
    private static volatile boolean enabled = false;
    private static final Set<Identifier> IDS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Identifier.class, new IdentifierGsonAdapter())
            .setPrettyPrinting()
            .create();

    private RevealState() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean v) { enabled = v; }

    public static int getHiddenCount() { return IDS.size(); }

    public static boolean shouldHide(Block block) {
        if (!enabled || block == null) return false;
        try {
            Identifier id = Registries.BLOCK.getId(block);
            return id != null && IDS.contains(id);
        } catch (Throwable t) {
            LOGGER.warn("Error resolving identifier for block {}", block, t);
            return false;
        }
    }

    public static boolean addByString(String raw) {
        Identifier id = normalize(raw);
        if (id == null) return false;
        // validate that registry contains the block
        var opt = Registries.BLOCK.getOrEmpty(id);
        if (opt.isEmpty()) return false;
        return IDS.add(id);
    }

    public static boolean removeByString(String raw) {
        Identifier id = normalize(raw);
        if (id == null) return false;
        return IDS.remove(id);
    }

    public static List<String> listIds() {
        List<String> out = new ArrayList<>();
        for (Identifier id : IDS) out.add(id.toString());
        Collections.sort(out);
        return out;
    }

    public static String normalizeIdString(String raw) {
        Identifier id = normalize(raw);
        return id == null ? raw : id.toString();
    }

    private static Identifier normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (!s.contains(":")) s = "minecraft:" + s;
        try {
            return new Identifier(s);
        } catch (Exception e) {
            return null;
        }
    }

    // Persistence --------------------------------------------------------------------------------
    public static void load(Path file) throws IOException {
        IDS.clear();
        if (!Files.exists(file)) {
            // ensure parent exists
            Path parent = file.getParent();
            if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
            save(file); // create empty file
            return;
        }
        String json = Files.readString(file);
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement el : arr) {
                String s = el.getAsString();
                Identifier id = normalize(s);
                if (id == null) {
                    LOGGER.warn("Skipping invalid id in config: {}", s);
                    continue;
                }
                // validate block exists
                if (Registries.BLOCK.getOrEmpty(id).isPresent()) {
                    IDS.add(id);
                } else {
                    LOGGER.warn("Skipping unknown block id in config: {}", id);
                }
            }
        } catch (JsonParseException | IllegalStateException ex) {
            LOGGER.warn("Malformed Xpose config - ignoring file and starting empty", ex);
        }
    }

    public static void save(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);

        JsonArray arr = new JsonArray();
        List<String> sorted = listIds();
        for (String s : sorted) arr.add(new JsonPrimitive(s));
        Files.writeString(file, GSON.toJson(arr));
    }

    // Gson adapter for Identifier if needed later (kept minimal)
    private static class IdentifierGsonAdapter implements JsonSerializer<Identifier>, JsonDeserializer<Identifier> {
        @Override
        public JsonElement serialize(Identifier src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }

        @Override
        public Identifier deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return new Identifier(json.getAsString());
        }
    }
}