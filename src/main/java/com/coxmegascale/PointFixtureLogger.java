package com.coxmegascale;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Opt-in, local-only anonymous JSONL recorder for calibrating raid points.
 * Records contain bounded raid metadata and relative timing, never display
 * names, account/session data, chat, coordinates, or wall-clock timestamps.
 * Disk writes run on one daemon thread so I/O cannot block the client thread.
 */
final class PointFixtureLogger implements AutoCloseable
{
    static final int SCHEMA_VERSION = 2;

    private final boolean enabled;
    private final Path directory;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r ->
    {
        Thread thread = new Thread(r, "cox-point-fixture-writer");
        thread.setDaemon(true);
        return thread;
    });

    private BufferedWriter output;
    private String sessionId;
    private Path sessionDirectory;
    private String sessionFileName;
    private Metadata metadata;
    private long sessionStartedNanos;
    private int sequence;
    private int lastGroupPoints;
    private int lastPersonalPoints;
    private String pendingStartLine;

    PointFixtureLogger(boolean enabled, Path configDirectory)
    {
        this.enabled = enabled;
        this.directory = configDirectory.resolve("cox-assistant").resolve("point-fixtures");
    }

    void startRaid(Metadata metadata, int groupPoints, int personalPoints)
    {
        // A random session id separates files without encoding player identity.
        synchronized (this)
        {
            if (!enabled || sessionId != null)
            {
                return;
            }
            this.metadata = metadata;
            sessionId = UUID.randomUUID().toString();
            lastGroupPoints = groupPoints;
            lastPersonalPoints = personalPoints;
            sequence = 0;
            pendingStartLine = null;
            sessionStartedNanos = System.nanoTime();
            String modeDirectory = modeDirectory(metadata.raidMode);
            sessionDirectory = directory.resolve(modeDirectory);
            sessionFileName = modeDirectory + "-" + sessionId + ".jsonl";
        }
        record("raid_start", null, groupPoints, personalPoints, "raid_start", null, null, true);
    }

    synchronized boolean hasActiveSession()
    {
        return enabled && sessionId != null;
    }

    void room(String event, String room, int groupPoints, int personalPoints, String pointSource)
    {
        record(event, room, groupPoints, personalPoints, pointSource, null, null, false);
    }

    void raidComplete(int groupPoints, int personalPoints)
    {
        record("raid_complete", null, groupPoints, personalPoints, "raid_complete", true, "completed", false);
        finishSessionWithoutRecord();
    }

    void endSession(String reason)
    {
        if (!hasActiveSession())
        {
            return;
        }
        record("raid_end", null, currentGroupPoints(), currentPersonalPoints(), "session_boundary", false, reason, false);
        finishSessionWithoutRecord();
    }

    private synchronized int currentGroupPoints()
    {
        return lastGroupPoints;
    }

    private synchronized int currentPersonalPoints()
    {
        return lastPersonalPoints;
    }

    private void finishSessionWithoutRecord()
    {
        if (!enabled)
        {
            return;
        }

        synchronized (this)
        {
            if (sessionId == null)
            {
                return;
            }
            sessionId = null;
            sessionDirectory = null;
            sessionFileName = null;
            metadata = null;
            pendingStartLine = null;
        }
        // Queue closure behind all pending records to preserve JSONL ordering.
        writer.execute(this::closeOutput);
    }

    private synchronized void record(
        String event,
        String room,
        int groupPoints,
        int personalPoints,
        String pointSource,
        Boolean completed,
        String endReason,
        boolean includeMetadata
    )
    {
        if (!enabled || sessionId == null || metadata == null)
        {
            return;
        }

        // Absolute totals make fixtures independently auditable while deltas are
        // the values used to calibrate room boundaries and point sources.
        int groupDelta = groupPoints - lastGroupPoints;
        int personalDelta = personalPoints - lastPersonalPoints;
        lastGroupPoints = groupPoints;
        lastPersonalPoints = personalPoints;
        long elapsedMillis = Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sessionStartedNanos));
        StringBuilder line = new StringBuilder(320)
            .append("{\"schemaVersion\":").append(SCHEMA_VERSION)
            .append(",\"event\":").append(quote(event))
            .append(",\"sequence\":").append(sequence++)
            .append(",\"elapsedMs\":").append(elapsedMillis)
            .append(",\"roomContext\":").append(quote(bounded(room, 40)))
            .append(",\"pointSource\":").append(quote(bounded(pointSource, 40)))
            .append(",\"groupPoints\":").append(groupPoints)
            .append(",\"groupDelta\":").append(groupDelta)
            .append(",\"personalPoints\":").append(personalPoints)
            .append(",\"personalDelta\":").append(personalDelta);

        if (includeMetadata)
        {
            line.append(",\"raidMode\":").append(quote(metadata.raidMode))
                .append(",\"startReason\":").append(quote(metadata.startReason))
                .append(",\"layoutCode\":").append(quote(metadata.layoutCode))
                .append(",\"scale\":").append(metadata.scale)
                .append(",\"scaleSource\":").append(quote(metadata.scaleSource))
                .append(",\"thievingLevel\":").append(metadata.thievingLevel)
                .append(",\"thievingSource\":").append(quote(metadata.thievingSource))
                .append(",\"fishingLevel\":").append(metadata.fishingLevel)
                .append(",\"fishingSource\":").append(quote(metadata.fishingSource));
        }
        if (completed != null)
        {
            line.append(",\"completed\":").append(completed);
        }
        if (endReason != null)
        {
            line.append(",\"endReason\":").append(quote(bounded(endReason, 32)));
        }
        line.append("}\n");

        Path writeDirectory = sessionDirectory;
        String writeFileName = sessionFileName;
        if ("raid_start".equals(event))
        {
            pendingStartLine = line.toString();
            return;
        }
        if ("raid_end".equals(event) && pendingStartLine != null)
        {
            // Do not create an empty/start-only file when tracking ends before
            // the first room, point change, or completion record.
            pendingStartLine = null;
            return;
        }
        String writeLine = (pendingStartLine == null ? "" : pendingStartLine) + line;
        pendingStartLine = null;
        writer.execute(() -> write(writeLine, writeDirectory, writeFileName));
    }

    private synchronized void write(String line, Path writeDirectory, String writeFileName)
    {
        try
        {
            if (output == null)
            {
                Files.createDirectories(writeDirectory);
                pruneOldFiles(writeDirectory);
                output = Files.newBufferedWriter(writeDirectory.resolve(writeFileName), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            output.write(line);
            output.flush();
        }
        catch (IOException ignored)
        {
            // Logging must never interfere with plugin or client operation.
        }
    }

    private void pruneOldFiles(Path targetDirectory)
    {
        // Retain at most 100 fixtures per raid mode to bound local disk usage.
        try (java.util.stream.Stream<Path> files = Files.list(targetDirectory))
        {
            java.util.List<Path> old = files
                .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                .sorted(Comparator.comparingLong(this::modifiedTime))
                .collect(java.util.stream.Collectors.toList());
            while (old.size() >= 100)
            {
                Files.deleteIfExists(old.remove(0));
            }
        }
        catch (IOException ignored)
        {
        }
    }

    private long modifiedTime(Path path)
    {
        try
        {
            return Files.getLastModifiedTime(path).toMillis();
        }
        catch (IOException ignored)
        {
            return Long.MIN_VALUE;
        }
    }

    private synchronized void closeOutput()
    {
        if (output != null)
        {
            try
            {
                output.close();
            }
            catch (IOException ignored)
            {
            }
            output = null;
        }
    }

    private static String quote(String value)
    {
        if (value == null)
        {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String bounded(String value, int maxLength)
    {
        if (value == null)
        {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static String modeDirectory(String raidMode)
    {
        if ("cm".equalsIgnoreCase(raidMode) || "challenge".equalsIgnoreCase(raidMode))
        {
            return "cm";
        }
        if ("full".equalsIgnoreCase(raidMode))
        {
            return "full";
        }
        return "normal";
    }

    @Override
    public void close()
    {
        if (!enabled)
        {
            writer.shutdownNow();
            return;
        }
        endSession("plugin_shutdown");
        writer.shutdown();
        try
        {
            writer.awaitTermination(2, TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
        }
    }

    static final class Metadata
    {
        // Construction bounds every caller-supplied value before persistence.
        final String raidMode;
        final String startReason;
        final String layoutCode;
        final int scale;
        final String scaleSource;
        final int thievingLevel;
        final String thievingSource;
        final int fishingLevel;
        final String fishingSource;

        Metadata(
            String raidMode,
            String startReason,
            String layoutCode,
            int scale,
            String scaleSource,
            int thievingLevel,
            String thievingSource,
            int fishingLevel,
            String fishingSource
        )
        {
            this.raidMode = bounded(raidMode, 12);
            this.startReason = bounded(startReason, 24);
            this.layoutCode = bounded(layoutCode, 64);
            this.scale = Math.max(1, Math.min(100, scale));
            this.scaleSource = bounded(scaleSource, 16);
            this.thievingLevel = Math.max(0, Math.min(5_000, thievingLevel));
            this.thievingSource = bounded(thievingSource, 24);
            this.fishingLevel = Math.max(1, Math.min(99, fishingLevel));
            this.fishingSource = bounded(fishingSource, 16);
        }
    }
}
