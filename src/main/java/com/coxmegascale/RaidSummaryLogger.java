package com.coxmegascale;

import com.coxmegascale.calc.RaidMath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Writes one local, human-readable JSON summary after each completed raid. */
final class RaidSummaryLogger implements AutoCloseable
{
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
        .ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.US)
        .withZone(ZoneOffset.UTC);

    private final Path directory;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "cox-raid-summary-writer");
        thread.setDaemon(true);
        return thread;
    });

    RaidSummaryLogger(Path runeLiteDirectory)
    {
        directory = runeLiteDirectory.resolve("cox-assistant").resolve("raid-logs");
    }

    void write(Snapshot snapshot)
    {
        if (snapshot == null)
        {
            return;
        }
        String json = toJson(snapshot);
        String safeMode = fileComponent(snapshot.raidMode);
        String fileName = FILE_TIME.format(snapshot.completedAt) + "_" + safeMode + "_scale-"
            + Math.max(1, snapshot.scale) + "_" + UUID.randomUUID() + ".json";
        writer.execute(() -> writeFile(fileName, json));
    }

    private void writeFile(String fileName, String json)
    {
        try
        {
            Files.createDirectories(directory);
            Files.write(directory.resolve(fileName), json.getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException ignored)
        {
            // Summary logging must never interfere with the client or gameplay.
        }
    }

    static String toJson(Snapshot snapshot)
    {
        StringBuilder json = new StringBuilder(2_048);
        json.append("{\n");
        field(json, 1, "schemaVersion", 1, true);
        field(json, 1, "startedAt", snapshot.startedAt.toString(), true);
        field(json, 1, "completedAt", snapshot.completedAt.toString(), true);
        field(json, 1, "durationMillis", snapshot.durationMillis, true);
        field(json, 1, "raidMode", snapshot.raidMode, true);
        field(json, 1, "layout", snapshot.layout, true);
        field(json, 1, "scale", snapshot.scale, true);
        field(json, 1, "olmKillers", snapshot.olmKillers, true);

        indent(json, 1).append("\"participants\": [");
        for (int i = 0; i < snapshot.participants.size(); i++)
        {
            if (i > 0) json.append(", ");
            json.append(quote(snapshot.participants.get(i)));
        }
        json.append("],\n");

        indent(json, 1).append("\"points\": {\n");
        field(json, 2, "group", snapshot.groupPoints, true);
        field(json, 2, "personal", snapshot.personalPoints, true);
        field(json, 2, "personalSharePercent", percent(snapshot.personalPoints, snapshot.groupPoints), false);
        indent(json, 1).append("},\n");

        purple(json, "groupPurple", snapshot.groupPurple, true);
        purple(json, "personalPurple", snapshot.personalPurple, true);

        indent(json, 1).append("\"trackedTotals\": {\n");
        field(json, 2, "deathPointsLost", snapshot.deathPointsLost, true);
        field(json, 2, "overloadPoints", snapshot.overloadPoints, true);
        field(json, 2, "fishPoints", snapshot.fishPoints, false);
        indent(json, 1).append("},\n");

        indent(json, 1).append("\"scoutedRooms\": [");
        for (int i = 0; i < snapshot.scoutedRooms.size(); i++)
        {
            if (i > 0) json.append(", ");
            json.append(quote(snapshot.scoutedRooms.get(i)));
        }
        json.append("],\n");

        indent(json, 1).append("\"rooms\": [");
        if (!snapshot.rooms.isEmpty()) json.append('\n');
        for (int i = 0; i < snapshot.rooms.size(); i++)
        {
            Room room = snapshot.rooms.get(i);
            indent(json, 2).append("{\n");
            field(json, 3, "name", room.name, true);
            field(json, 3, "durationMillis", room.durationMillis, true);
            field(json, 3, "groupPoints", room.groupPoints, true);
            field(json, 3, "personalPoints", room.personalPoints, true);
            field(json, 3, "personalSharePercent", percent(room.personalPoints, room.groupPoints), false);
            indent(json, 2).append('}');
            json.append(i + 1 < snapshot.rooms.size() ? ",\n" : "\n");
        }
        if (!snapshot.rooms.isEmpty()) indent(json, 1);
        json.append("],\n");

        indent(json, 1).append("\"deaths\": [");
        if (!snapshot.deaths.isEmpty()) json.append('\n');
        for (int i = 0; i < snapshot.deaths.size(); i++)
        {
            Death death = snapshot.deaths.get(i);
            indent(json, 2).append("{\n");
            field(json, 3, "room", death.room, true);
            field(json, 3, "pointsLost", death.pointsLost, false);
            indent(json, 2).append('}');
            json.append(i + 1 < snapshot.deaths.size() ? ",\n" : "\n");
        }
        if (!snapshot.deaths.isEmpty()) indent(json, 1);
        json.append("]\n}\n");
        return json.toString();
    }

    private static void purple(StringBuilder json, String name, RaidMath.PurpleChance chance, boolean comma)
    {
        indent(json, 1).append(quote(name)).append(": {\n");
        field(json, 2, "points", chance.points, true);
        field(json, 2, "chanceAtLeastOnePercent", (1.0 - chance.distribution.get(0)) * 100.0, true);
        field(json, 2, "expectedPurples", chance.expectedPurples, true);
        field(json, 2, "potentialRolls", chance.potentialRolls, true);
        indent(json, 2).append("\"rollChancesPercent\": [");
        for (int i = 0; i < chance.rollProbabilities.size(); i++)
        {
            if (i > 0) json.append(", ");
            json.append(decimal(chance.rollProbabilities.get(i) * 100.0));
        }
        json.append("]\n");
        indent(json, 1).append('}').append(comma ? ",\n" : "\n");
    }

    private static void field(StringBuilder json, int level, String name, String value, boolean comma)
    {
        indent(json, level).append(quote(name)).append(": ").append(quote(value)).append(comma ? ",\n" : "\n");
    }

    private static void field(StringBuilder json, int level, String name, long value, boolean comma)
    {
        indent(json, level).append(quote(name)).append(": ").append(value).append(comma ? ",\n" : "\n");
    }

    private static void field(StringBuilder json, int level, String name, double value, boolean comma)
    {
        indent(json, level).append(quote(name)).append(": ").append(decimal(value)).append(comma ? ",\n" : "\n");
    }

    private static StringBuilder indent(StringBuilder json, int level)
    {
        for (int i = 0; i < level; i++) json.append("  ");
        return json;
    }

    private static double percent(int numerator, int denominator)
    {
        return denominator <= 0 ? 0 : Math.max(0, numerator) * 100.0 / denominator;
    }

    private static String decimal(double value)
    {
        return String.format(Locale.US, "%.6f", Math.max(0, value));
    }

    private static String quote(String value)
    {
        if (value == null) return "null";
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            switch (c)
            {
                case '"': escaped.append("\\\""); break;
                case '\\': escaped.append("\\\\"); break;
                case '\b': escaped.append("\\b"); break;
                case '\f': escaped.append("\\f"); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                default:
                    if (c < 0x20) escaped.append(String.format(Locale.US, "\\u%04x", (int) c));
                    else escaped.append(c);
            }
        }
        return escaped.append('"').toString();
    }

    private static String fileComponent(String value)
    {
        String safe = value == null ? "unknown" : value.toLowerCase(Locale.US).replaceAll("[^a-z0-9_-]", "-");
        return safe.isEmpty() ? "unknown" : safe;
    }

    @Override
    public void close()
    {
        writer.shutdown();
        try
        {
            writer.awaitTermination(2, TimeUnit.SECONDS);
        }
        catch (InterruptedException ignored)
        {
            // The client is already shutting down; any queued log write may be abandoned.
        }
    }

    static final class Snapshot
    {
        final Instant startedAt;
        final Instant completedAt;
        final long durationMillis;
        final String raidMode;
        final String layout;
        final int scale;
        final int olmKillers;
        final List<String> participants;
        final int groupPoints;
        final int personalPoints;
        final RaidMath.PurpleChance groupPurple;
        final RaidMath.PurpleChance personalPurple;
        final int deathPointsLost;
        final int overloadPoints;
        final int fishPoints;
        final List<String> scoutedRooms;
        final List<Room> rooms;
        final List<Death> deaths;

        Snapshot(Instant startedAt, Instant completedAt, String raidMode, String layout, int scale, int olmKillers,
            List<String> participants, int groupPoints, int personalPoints, RaidMath.PurpleChance groupPurple,
            RaidMath.PurpleChance personalPurple, int deathPointsLost, int overloadPoints, int fishPoints,
            List<String> scoutedRooms, List<Room> rooms, List<Death> deaths)
        {
            this.completedAt = completedAt == null ? Instant.now() : completedAt;
            this.startedAt = startedAt == null ? this.completedAt : startedAt;
            this.durationMillis = Math.max(0, this.completedAt.toEpochMilli() - this.startedAt.toEpochMilli());
            this.raidMode = raidMode == null ? "unknown" : raidMode;
            this.layout = layout == null ? "Unknown" : layout;
            this.scale = Math.max(1, scale);
            this.olmKillers = Math.max(1, olmKillers);
            this.participants = immutable(participants);
            this.groupPoints = Math.max(0, groupPoints);
            this.personalPoints = Math.max(0, personalPoints);
            this.groupPurple = groupPurple;
            this.personalPurple = personalPurple;
            this.deathPointsLost = Math.max(0, deathPointsLost);
            this.overloadPoints = Math.max(0, overloadPoints);
            this.fishPoints = Math.max(0, fishPoints);
            this.scoutedRooms = immutable(scoutedRooms);
            this.rooms = immutable(rooms);
            this.deaths = immutable(deaths);
        }

        private static <T> List<T> immutable(List<T> values)
        {
            return values == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
        }
    }

    static final class Room
    {
        final String name;
        final long durationMillis;
        final int groupPoints;
        final int personalPoints;

        Room(String name, long durationMillis, int groupPoints, int personalPoints)
        {
            this.name = name == null ? "Unknown" : name;
            this.durationMillis = Math.max(0, durationMillis);
            this.groupPoints = Math.max(0, groupPoints);
            this.personalPoints = Math.max(0, personalPoints);
        }
    }

    static final class Death
    {
        final String room;
        final int pointsLost;

        Death(String room, int pointsLost)
        {
            this.room = room == null ? "Unknown" : room;
            this.pointsLost = Math.max(0, pointsLost);
        }
    }
}
