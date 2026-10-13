package ru.frend.ai;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FrendPersistence {
    private static final String FILE = "aifrend_state.json";

    public static void load(MinecraftServer server) {
        try {
            FrendState st = FrendState.get(server);
            Path p = server.getSavePath(WorldSavePath.ROOT).resolve(FILE);
            if (!Files.exists(p)) return;
            String s = Files.readString(p);
            String mode = matchString(s, "\"mode\"\\s*:\\s*\"([^\"]+)\"");
            if (mode != null) {
                try { st.setMode(FrendState.Mode.valueOf(mode)); } catch (Exception ignored) {}
            }
            String uuid = matchString(s, "\"controlledPlayer\"\\s*:\\s*\"([^\"]+)\"");
            if (uuid != null) {
                try { st.setControlledPlayerUuid(UUID.fromString(uuid)); } catch (Exception ignored) {}
            }
            st.setSpeedrunStartMs(matchLong(s, "\"speedrunStartMs\"\\s*:\\s*(-?\\d+)"));
            st.setSpeedrunEndMs(matchLong(s, "\"speedrunEndMs\"\\s*:\\s*(-?\\d+)"));
            st.setSpeedrunDone(matchBool(s, "\"speedrunDone\"\\s*:\\s*(true|false)"));
            st.setHelping(matchBool(s, "\"helping\"\\s*:\\s*(true|false)"));
            boolean enabled = true;
            String en = matchString(s, "\"speedrunEnabled\"\\s*:\\s*(true|false)");
            if (en != null) enabled = Boolean.parseBoolean(en);
            st.setSpeedrunEnabled(enabled);
            String phase = matchString(s, "\"phase\"\\s*:\\s*\"([^\"]+)\"");
            if (phase != null) {
                try { st.setSavedPhase(SpeedrunEngine.Phase.valueOf(phase)); } catch (Exception ignored) {}
            }
            st.setSavedLogs((int) matchLong(s, "\"logs\"\\s*:\\s*(-?\\d+)"));
            st.setSavedCobble((int) matchLong(s, "\"cobble\"\\s*:\\s*(-?\\d+)"));
            Integer sx = matchInt(s, "\"strongholdX\"\\s*:\\s*(-?\\d+)");
            Integer sy = matchInt(s, "\"strongholdY\"\\s*:\\s*(-?\\d+)");
            Integer sz = matchInt(s, "\"strongholdZ\"\\s*:\\s*(-?\\d+)");
            if (sx != null && sy != null && sz != null) st.setSavedStronghold(new BlockPos(sx, sy, sz));
            Integer px = matchInt(s, "\"posX\"\\s*:\\s*(-?\\d+)");
            Integer py = matchInt(s, "\"posY\"\\s*:\\s*(-?\\d+)");
            Integer pz = matchInt(s, "\"posZ\"\\s*:\\s*(-?\\d+)");
            if (px != null && py != null && pz != null) st.setSavedPosition(new BlockPos(px, py, pz));
        } catch (Exception e) {
            // ignore load errors
        }
    }

    public static void save(MinecraftServer server) {
        try {
            FrendState st = FrendState.get(server);
            st.storeEngine(st.getEngine());
            Path p = server.getSavePath(WorldSavePath.ROOT).resolve(FILE);
            StringBuilder b = new StringBuilder();
            b.append("{");
            b.append("\"mode\":\"").append(st.getMode().name()).append("\",");
            if (st.getControlledPlayerUuid() != null) b.append("\"controlledPlayer\":\"").append(st.getControlledPlayerUuid()).append("\",");
            b.append("\"speedrunStartMs\":").append(st.getSpeedrunStartMs()).append(",");
            b.append("\"speedrunEndMs\":").append(st.getSpeedrunEndMs()).append(",");
            b.append("\"speedrunDone\":").append(st.isSpeedrunDone()).append(",");
            b.append("\"helping\":").append(st.isHelping()).append(",");
            b.append("\"speedrunEnabled\":").append(st.isSpeedrunEnabled()).append(",");
            b.append("\"phase\":\"").append(st.getSavedPhase().name()).append("\",");
            b.append("\"logs\":").append(st.getSavedLogs()).append(",");
            b.append("\"cobble\":").append(st.getSavedCobble()).append(",");
            if (st.getSavedStronghold() != null) {
                b.append("\"strongholdX\":").append(st.getSavedStronghold().getX()).append(",");
                b.append("\"strongholdY\":").append(st.getSavedStronghold().getY()).append(",");
                b.append("\"strongholdZ\":").append(st.getSavedStronghold().getZ()).append(",");
            }
            if (st.getSavedPosition() != null) {
                b.append("\"posX\":").append(st.getSavedPosition().getX()).append(",");
                b.append("\"posY\":").append(st.getSavedPosition().getY()).append(",");
                b.append("\"posZ\":").append(st.getSavedPosition().getZ()).append(",");
            }
            b.append("\"ok\":true}");
            Files.writeString(p, b.toString());
        } catch (Exception ignored) {}
    }

    private static String matchString(String s, String regex) {
        Matcher m = Pattern.compile(regex).matcher(s);
        return m.find() ? m.group(1) : null;
    }
    private static long matchLong(String s, String regex) {
        String v = matchString(s, regex);
        if (v == null) return 0;
        try { return Long.parseLong(v); } catch (Exception e) { return 0; }
    }
    private static boolean matchBool(String s, String regex) {
        String v = matchString(s, regex);
        return "true".equalsIgnoreCase(v);
    }
    private static Integer matchInt(String s, String regex) {
        String v = matchString(s, regex);
        if (v == null) return null;
        try { return Integer.parseInt(v); } catch (Exception e) { return null; }
    }
}
