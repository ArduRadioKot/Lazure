package com.rassvet.essential.data.index;

import android.content.Context;
import android.database.Cursor;
import androidx.documentfile.provider.DocumentFile;
import com.rassvet.essential.data.local.AppDatabase;
import com.rassvet.essential.data.local.NoteIndexDao;
import com.rassvet.essential.data.local.BacklinkRow;
import com.rassvet.essential.data.local.NoteIndexEntity;
import com.rassvet.essential.data.local.NoteFtsResult;
import com.rassvet.essential.data.local.WikiEdgeDao;
import com.rassvet.essential.data.local.WikiEdgeEntity;
import com.rassvet.essential.data.vault.VaultDocuments;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IndexRepository {
    private static final Pattern WIKILINK =
            Pattern.compile("\\[\\[([^\\]|]+)(?:\\|[^\\]]+)?]]");
    private static final int FTS_BODY_CAP = 12_000;

    private final AppDatabase db;
    private final NoteIndexDao notes;
    private final WikiEdgeDao edges;

    public IndexRepository(AppDatabase db) {
        this.db = db;
        this.notes = db.noteIndexDao();
        this.edges = db.wikiEdgeDao();
    }

    public void rebuild(Context context, String vaultStored) {
        DocumentFile root = VaultDocuments.resolveRoot(context, vaultStored);
        if (root == null) return;
        List<DocumentFile> files = VaultDocuments.listMarkdown(root);
        notes.clear();
        edges.clear();
        clearFts();
        for (DocumentFile file : files) {
            String text;
            try {
                text = VaultDocuments.readText(context, file, vaultStored);
            } catch (Exception e) {
                text = "";
            }
            String rawHeading = null;
            for (String line : text.split("\n", -1)) {
                if (line.trim().isEmpty()) continue;
                String lineTrim = line.trim();
                if (lineTrim.startsWith("#")) {
                    rawHeading = lineTrim.substring(1).trim();
                } else {
                    rawHeading = lineTrim;
                }
                break;
            }
            String title;
            if (rawHeading != null && !rawHeading.trim().isEmpty()) {
                title = rawHeading;
            } else {
                title = VaultDocuments.displayName(file);
            }
            String preview = text.replace('\n', ' ');
            if (preview.length() > 240) preview = preview.substring(0, 240);

            String uri = file.getUri().toString();
            notes.upsert(new NoteIndexEntity(uri, title, preview, System.currentTimeMillis()));
            insertFts(uri, title, text);

            Matcher m = WIKILINK.matcher(text);
            Set<String> linkTitles = new LinkedHashSet<>();
            while (m.find()) {
                linkTitles.add(m.group(1).trim());
            }
            if (!linkTitles.isEmpty()) {
                List<WikiEdgeEntity> list = new ArrayList<>();
                for (String to : linkTitles) {
                    list.add(new WikiEdgeEntity(uri, to));
                }
                edges.insertAll(list);
            }
        }
    }


    public List<NoteFtsResult> searchByQuery(String query, int limit) {
        if (!com.rassvet.essential.data.local.AppDatabaseFts.isAvailable()) {
            return topRecentNotes(limit);
        }
        try {
            String fts5q = buildFts5Query(query);
            if (fts5q.isEmpty()) return topRecentNotes(limit);
            Cursor c = db.getOpenHelper().getReadableDatabase().query(
                    "SELECT uri, title, body FROM note_fts WHERE note_fts MATCH ?"
                            + com.rassvet.essential.data.local.AppDatabaseFts.ftsSearchOrderClause()
                            + " LIMIT ?",
                    new Object[]{fts5q, limit});
            List<NoteFtsResult> results = new ArrayList<>();
            try {
                while (c.moveToNext()) {
                    results.add(new NoteFtsResult(
                            c.getString(0),
                            c.getString(1) != null ? c.getString(1) : "",
                            c.getString(2) != null ? c.getString(2) : ""));
                }
            } finally {
                c.close();
            }
            return results.isEmpty() ? topRecentNotes(limit) : results;
        } catch (Exception e) {
            return topRecentNotes(limit);
        }
    }

    public List<NoteFtsResult> topRecentNotes(int n) {
        List<NoteIndexEntity> entities = notes.topRecent(n);
        List<NoteFtsResult> result = new ArrayList<>();
        for (NoteIndexEntity e : entities) {
            result.add(new NoteFtsResult(e.uri, e.title, e.preview));
        }
        return result;
    }

    public List<NoteIndexEntity> allNotes() {
        return notes.all();
    }

    public List<String> linksFor(String uri) {
        return edges.linksFrom(uri);
    }

    public List<BacklinkRow> backlinksForTitle(String title) {
        if (title == null || title.trim().isEmpty()) return new ArrayList<>();
        try {
            return edges.backlinksTo(title.trim());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }


    public List<NoteFtsResult> searchNotes(String query, int limit) {
        return searchByQuery(query, limit);
    }


    private void clearFts() {
        if (!com.rassvet.essential.data.local.AppDatabaseFts.isAvailable()) return;
        try {
            db.getOpenHelper().getWritableDatabase().execSQL("DELETE FROM note_fts");
        } catch (Exception ignored) {}
    }

    private void insertFts(String uri, String title, String body) {
        if (!com.rassvet.essential.data.local.AppDatabaseFts.isAvailable()) return;
        try {
            String b = body.length() > FTS_BODY_CAP ? body.substring(0, FTS_BODY_CAP) : body;
            db.getOpenHelper().getWritableDatabase().execSQL(
                    "INSERT INTO note_fts(uri, title, body) VALUES (?, ?, ?)",
                    new Object[]{uri, title, b});
        } catch (Exception ignored) {}
    }


    private static String buildFts5Query(String query) {
        String[] tokens = query.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            String safe = token.replaceAll("[^\\p{L}\\p{N}]", "");
            if (safe.length() < 2) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(safe).append("*");
        }
        return sb.toString();
    }
}


