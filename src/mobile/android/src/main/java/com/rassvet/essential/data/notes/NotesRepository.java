package com.rassvet.essential.data.notes;

import com.rassvet.essential.data.local.AppDatabase;
import com.rassvet.essential.data.local.StoredNoteDao;
import com.rassvet.essential.data.local.StoredNoteEntity;
import com.rassvet.essential.data.local.WikiEdgeDao;
import com.rassvet.essential.data.local.WikiEdgeEntity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class NotesRepository {
    private static final Pattern WIKILINK =
            Pattern.compile("\\[\\[([^\\]|]+)(?:\\|[^\\]]+)?]]");

    private final StoredNoteDao notes;
    private final WikiEdgeDao edges;

    public NotesRepository(AppDatabase db) {
        this.notes = db.storedNoteDao();
        this.edges = db.wikiEdgeDao();
    }

    private static String previewFromBody(String body) {
        String p = body == null ? "" : body.replace('\n', ' ');
        if (p.length() > 240) return p.substring(0, 240);
        return p;
    }

    public List<StoredNoteEntity> allByTitleAscending() {
        return notes.allByTitleAsc();
    }

    public List<StoredNoteEntity> recentByUpdatedDescending() {
        return notes.allByUpdatedDesc();
    }

    public StoredNoteEntity getById(String id) {
        if (id == null || id.isEmpty()) return null;
        return notes.getById(id);
    }


    public String createNote(String title) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        String t =
                title == null || title.trim().isEmpty()
                        ? "Untitled"
                        : title.trim();
        notes.upsert(new StoredNoteEntity(id, t, "", "", now));
        return id;
    }


    public void save(String noteId, String title, String body) {
        if (noteId == null || noteId.isEmpty()) return;
        String b = body == null ? "" : body;
        String rawTitle = title == null ? "" : title.trim();
        String displayTitle = rawTitle.isEmpty() ? "Untitled" : rawTitle;
        String preview = previewFromBody(b);
        long now = System.currentTimeMillis();
        notes.upsert(new StoredNoteEntity(noteId, displayTitle, preview, b, now));
        edges.deleteFromNote(noteId);
        insertWikiEdges(noteId, b);
    }

    private void insertWikiEdges(String fromNoteId, String text) {
        Matcher m = WIKILINK.matcher(text == null ? "" : text);
        Set<String> linkTitles = new LinkedHashSet<>();
        while (m.find()) {
            linkTitles.add(m.group(1).trim());
        }
        if (linkTitles.isEmpty()) return;
        List<WikiEdgeEntity> list = new ArrayList<>();
        for (String to : linkTitles) {
            list.add(new WikiEdgeEntity(fromNoteId, to));
        }
        edges.insertAll(list);
    }


    public String buildAiContextSnippet(int noteLimit, int maxCharsPerNote) {
        List<StoredNoteEntity> rows = notes.allByUpdatedDesc();
        if (rows.size() > noteLimit) {
            rows = rows.subList(0, noteLimit);
        }
        int cap = Math.max(128, Math.min(maxCharsPerNote, 16000));
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (StoredNoteEntity n : rows) {
            String body = n.body == null ? "" : n.body;
            if (body.length() > cap) {
                body = body.substring(0, cap).trim() + "…";
            }
            if (body.trim().isEmpty()) continue;
            if (!first) sb.append("\n\n---\n\n");
            first = false;
            sb.append(n.title != null ? n.title : "").append('\n').append(body);
        }
        return sb.toString();
    }
}


