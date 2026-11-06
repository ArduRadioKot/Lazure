package com.rassvet.essential.data.vault;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import androidx.documentfile.provider.DocumentFile;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public final class VaultDocuments {

    public static final String INTERNAL_PREFIX = "internal:";

    private VaultDocuments() {}


    public static boolean isSafTreeVault(String vaultStored) {
        String s = vaultStored == null ? "" : vaultStored.trim();
        if (s.isEmpty() || s.startsWith(INTERNAL_PREFIX)) return false;
        Uri uri = null;
        try {
            uri = Uri.parse(s);
        } catch (Exception ignored) {
        }
        if (uri == null) return false;
        return DocumentsContract.isTreeUri(uri);
    }


    public static boolean hasPersistableVaultAccess(Context context, String vaultStored) {
        String s = vaultStored == null ? "" : vaultStored.trim();
        if (s.isEmpty()) return false;
        if (s.startsWith(INTERNAL_PREFIX)) {
            String slug = s.substring(INTERNAL_PREFIX.length()).trim();
            if (slug.isEmpty()) return false;
            File dir = new File(context.getFilesDir(), "vaults/" + slug);
            return dir.isDirectory();
        }
        Uri treeUri;
        try {
            treeUri = Uri.parse(s);
        } catch (Exception e) {
            return false;
        }
        if (!DocumentsContract.isTreeUri(treeUri)) return false;
        String wantId;
        try {
            wantId = DocumentsContract.getTreeDocumentId(treeUri);
        } catch (Exception e) {
            return false;
        }
        final String wantIdFinal = wantId;
        for (android.content.UriPermission perm : context.getContentResolver().getPersistedUriPermissions()) {
            if (!perm.isReadPermission() || !perm.isWritePermission()) continue;
            Uri pUri = perm.getUri();
            if (pUri.equals(treeUri) || pUri.toString().equals(treeUri.toString())) {
                return true;
            }
            try {
                if (DocumentsContract.isTreeUri(pUri)
                        && DocumentsContract.getTreeDocumentId(pUri).equals(wantIdFinal)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }


    public static boolean needsSafFolderRegrant(Context context, String vaultStored) {
        return isSafTreeVault(vaultStored) && !hasPersistableVaultAccess(context, vaultStored);
    }


    public static Uri writableUriForVaultDocument(
            Context context, String vaultStored, Uri documentUri) {
        String stored = vaultStored == null ? "" : vaultStored.trim();
        if (stored.startsWith(INTERNAL_PREFIX)) return documentUri;
        if (!DocumentsContract.isDocumentUri(context, documentUri)) return documentUri;
        Uri treeUri;
        try {
            treeUri = Uri.parse(stored);
        } catch (Exception e) {
            return documentUri;
        }
        if (!DocumentsContract.isTreeUri(treeUri)) return documentUri;
        String docId;
        try {
            docId = DocumentsContract.getDocumentId(documentUri);
        } catch (Exception e) {
            return documentUri;
        }
        try {
            Uri built = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
            return built != null ? built : documentUri;
        } catch (Exception e) {
            return documentUri;
        }
    }


    public static DocumentFile singleDocumentUnderVault(
            Context context, String vaultStored, Uri documentUri) {
        Uri u = writableUriForVaultDocument(context, vaultStored, documentUri);
        return DocumentFile.fromSingleUri(context, u);
    }


    public static DocumentFile resolveRoot(Context context, String stored) {
        if (stored.startsWith(INTERNAL_PREFIX)) {
            String slug = stored.substring(INTERNAL_PREFIX.length()).trim();
            if (slug.isEmpty()) return null;
            File dir = new File(context.getFilesDir(), "vaults/" + slug);
            if (!dir.isDirectory()) return null;
            return DocumentFile.fromFile(dir);
        }
        Uri uri;
        try {
            uri = Uri.parse(stored);
        } catch (Exception e) {
            return null;
        }
        return DocumentFile.fromTreeUri(context, uri);
    }

    public static DocumentFile tree(Context context, Uri treeUri) {
        return DocumentFile.fromTreeUri(context, treeUri);
    }

    public static List<DocumentFile> listMarkdown(DocumentFile root) {
        List<DocumentFile> out = new ArrayList<>();
        walkFiles(root, out);
        return out;
    }

    private static void walkFiles(DocumentFile dir, List<DocumentFile> out) {
        if (dir == null || !dir.isDirectory()) return;
        DocumentFile[] children = dir.listFiles();
        if (children == null) return;
        for (DocumentFile child : children) {
            if (child.isDirectory()) {
                walkFiles(child, out);
            } else {
                String n = child.getName();
                if (n != null && n.toLowerCase().endsWith(".md")) {
                    out.add(child);
                }
            }
        }
    }

    public static String readText(Context context, DocumentFile file, String vaultStored)
            throws IOException {
        return readDocumentText(context, vaultStored, file.getUri());
    }


    public static String readDocumentText(Context context, String vaultStored, Uri documentUri)
            throws IOException {
        Uri writable = writableUriForVaultDocument(context, vaultStored, documentUri);
        File internal = internalFileFromUri(vaultStored, writable);
        if (internal != null) {
            if (!internal.isFile()) throw new IOException("not a file: " + internal);
            try (java.io.InputStream in = new java.io.FileInputStream(internal);
                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            in, java.nio.charset.StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
                    sb.deleteCharAt(sb.length() - 1);
                }
                return sb.toString();
            }
        }
        java.io.InputStream in = context.getContentResolver().openInputStream(writable);
        if (in == null) throw new IOException("openInputStream null");
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                in, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
                sb.deleteCharAt(sb.length() - 1);
            }
            return sb.toString();
        }
    }


    public static String readText(Context context, DocumentFile file) throws IOException {
        return readText(context, file, null);
    }

    public static boolean writeText(
            Context context, DocumentFile file, String text, String vaultStored) {
        return writeDocumentText(context, vaultStored, file.getUri(), text);
    }


    public static boolean writeDocumentText(
            Context context, String vaultStored, Uri documentUri, String text) {
        Uri writable = writableUriForVaultDocument(context, vaultStored, documentUri);
        File internal = internalFileFromUri(vaultStored, writable);
        if (internal != null) {
            try {
                File parent = internal.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    return false;
                }
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(internal, false)) {
                    fos.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    fos.flush();
                    try {
                        fos.getFD().sync();
                    } catch (Exception ignored) {

                    }
                    return true;
                }
            } catch (Exception e) {
                return false;
            }
        }
        try {
            java.io.OutputStream out = context.getContentResolver().openOutputStream(writable, "wt");
            if (out == null) {
                out = context.getContentResolver().openOutputStream(writable);
            }
            if (out == null) {
                DocumentFile df = DocumentFile.fromSingleUri(context, writable);
                if (df != null) {
                    out = context.getContentResolver().openOutputStream(df.getUri(), "wt");
                    if (out == null) {
                        out = context.getContentResolver().openOutputStream(df.getUri());
                    }
                }
            }
            if (out == null) return false;
            try (java.io.OutputStream o = out) {
                o.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return true;
            }
        } catch (SecurityException | IOException e) {
            return false;
        }
    }


    public static boolean deleteDocumentFromVault(
            Context context, String vaultStored, Uri documentUri) {
        if (vaultStored == null || vaultStored.trim().isEmpty() || documentUri == null) {
            return false;
        }
        String stored = vaultStored.trim();
        if (needsSafFolderRegrant(context, stored)) {
            return false;
        }
        Uri writable = writableUriForVaultDocument(context, stored, documentUri);

        File internal = internalFileFromUri(stored, writable);
        if (internal != null) {
            return internal.isFile() && internal.delete();
        }

        if (DocumentsContract.isDocumentUri(context, writable)) {
            try {
                if (DocumentsContract.deleteDocument(context.getContentResolver(), writable)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }

        DocumentFile direct = DocumentFile.fromSingleUri(context, writable);
        if (direct != null && direct.exists() && direct.delete()) {
            return true;
        }

        DocumentFile root = resolveRoot(context, stored);
        if (root == null) return false;
        for (DocumentFile file : listMarkdown(root)) {
            Uri fileUri = file.getUri();
            if (uriSameDocument(context, fileUri, documentUri)
                    || uriSameDocument(context, fileUri, writable)) {
                if (DocumentsContract.isDocumentUri(context, fileUri)) {
                    try {
                        Uri treeWritable =
                                writableUriForVaultDocument(context, stored, fileUri);
                        if (DocumentsContract.deleteDocument(
                                context.getContentResolver(), treeWritable)) {
                            return true;
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (file.delete()) return true;
            }
        }
        return false;
    }


    private static File internalFileFromUri(String vaultStored, Uri uri) {
        if (vaultStored == null || !vaultStored.trim().startsWith(INTERNAL_PREFIX)) return null;
        if (uri == null || !"file".equals(uri.getScheme())) return null;
        String path = uri.getPath();
        if (path == null || path.isEmpty()) return null;
        return new File(path);
    }

    public static String displayName(DocumentFile file) {
        String name = file.getName();
        if (name != null) return name;
        Uri u = file.getUri();
        String last = u != null ? u.getLastPathSegment() : null;
        return last != null ? last : "note.md";
    }


    public static String resolveDocumentDisplayName(
            Context context, String vaultStored, Uri documentUri) {
        Uri writable = writableUriForVaultDocument(context, vaultStored, documentUri);
        try (android.database.Cursor c =
                context.getContentResolver()
                        .query(
                                writable,
                                new String[] {android.provider.OpenableColumns.DISPLAY_NAME},
                                null,
                                null,
                                null)) {
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null && !n.trim().isEmpty()) return n;
            }
        } catch (Exception ignored) {
        }
        DocumentFile df = DocumentFile.fromSingleUri(context, writable);
        if (df != null) {
            String n = df.getName();
            if (n != null && !n.trim().isEmpty()) return n;
        }
        String last = writable.getLastPathSegment();
        return last != null && !last.trim().isEmpty() ? last : "note.md";
    }


    public static VaultRenameResult renameMarkdownFile(
            Context context, String vaultStored, Uri documentUri, String targetBase) {
        String sanitized = sanitizeFileBase(targetBase);
        Uri writable = writableUriForVaultDocument(context, vaultStored, documentUri);
        String currentName = resolveDocumentDisplayName(context, vaultStored, documentUri);
        boolean isInternal =
                vaultStored != null && vaultStored.trim().startsWith(INTERNAL_PREFIX);

        for (int suffix = 0; suffix <= 50; suffix++) {
            String candidate =
                    suffix == 0 ? sanitized + ".md" : sanitized + " (" + suffix + ").md";
            if (candidate.equalsIgnoreCase(currentName)) {
                return new VaultRenameResult(true, writable, null);
            }

            if (isInternal) {
                File src = internalFileFromUri(vaultStored, writable);
                if (src != null) {
                    File dest = new File(src.getParentFile(), candidate);
                    if (dest.equals(src)) {
                        return new VaultRenameResult(true, Uri.fromFile(src), null);
                    }
                    if (src.renameTo(dest)) {
                        return new VaultRenameResult(true, Uri.fromFile(dest), null);
                    }
                }
                DocumentFile file = DocumentFile.fromSingleUri(context, writable);
                if (file != null && file.renameTo(candidate)) {
                    return new VaultRenameResult(true, file.getUri(), null);
                }
            } else {
                try {
                    Uri newUri =
                            DocumentsContract.renameDocument(
                                    context.getContentResolver(), writable, candidate);
                    if (newUri != null) {
                        return new VaultRenameResult(true, newUri, null);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return new VaultRenameResult(
                false,
                writable,
                "не удалось переименовать (имя занято или не поддерживается)");
    }


    public static String vaultDisplayName(Context context, String stored) {
        DocumentFile root = resolveRoot(context, stored);
        if (root == null) return "Lazure";
        String n = root.getName();
        if (n != null && !n.trim().isEmpty()) return n;
        if (stored.startsWith(INTERNAL_PREFIX)) {
            String slug = stored.substring(INTERNAL_PREFIX.length()).trim();
            return slug.isEmpty() ? "vault" : slug;
        }
        return "Lazure";
    }


    public static String sanitizeFileBase(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) t = "Untitled";
        String cleaned = t.replaceAll("[\\\\/:*?\"<>|]", "_").trim().replaceAll("^\\.+", "").replaceAll("\\.+$", "");
        if (cleaned.isEmpty()) cleaned = "Untitled";
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    public static String markdownMimeType() {
        return Build.VERSION.SDK_INT >= 34 ? "text/markdown" : "application/octet-stream";
    }

    private static boolean uriSameDocument(Context context, Uri a, Uri b) {
        if (a.equals(b)) return true;
        if (!DocumentsContract.isDocumentUri(context, a)) return false;
        if (!DocumentsContract.isDocumentUri(context, b)) return false;
        try {
            return DocumentsContract.getDocumentId(a).equals(DocumentsContract.getDocumentId(b));
        } catch (Exception e) {
            return false;
        }
    }


    public static String uniqueMarkdownFilenameInParent(
            Context context,
            DocumentFile parent,
            String titleSanitizedBase,
            DocumentFile excluding) {
        String base = sanitizeFileBase(titleSanitizedBase);
        if (base.toLowerCase().endsWith(".md")) {
            base = base.substring(0, base.length() - 3).trim();
            base = base.replaceAll("\\.+$", "").trim();
            if (base.isEmpty()) base = "Untitled";
            base = base.length() > 120 ? base.substring(0, 120) : base;
        }
        if (base.isEmpty()) base = "Untitled";

        String finalBase = base;
        java.util.function.Predicate<String> clashes =
                candidate -> {
                    DocumentFile found = parent.findFile(candidate);
                    if (found == null) return false;
                    if (excluding == null) return true;
                    return !uriSameDocument(context, found.getUri(), excluding.getUri());
                };

        String candidate = finalBase + ".md";
        if (!clashes.test(candidate)) return candidate;
        for (int i = 2; i < 500; i++) {
            candidate = finalBase + " (" + i + ").md";
            if (!clashes.test(candidate)) return candidate;
        }
        return candidate;
    }


    public static DocumentFile createNewMarkdownFile(DocumentFile root) {
        if (root == null || !root.isDirectory()) return null;
        String name = uniqueDraftName();
        String[] mimeTypes = {markdownMimeType(), "text/plain", "application/octet-stream", "*/*"};
        for (String mime : mimeTypes) {
            try {
                DocumentFile created = root.createFile(mime, name);
                if (created != null) return created;
            } catch (Exception ignored) {

            }
        }
        return null;
    }


    public static String uniqueDraftName() {
        long ts = System.currentTimeMillis();
        int nonce = (int) (Math.random() * 10000);
        return "draft-" + ts + "-" + nonce + ".md";
    }


    public static Uri createMarkdownDocumentRaw(Context context, String vaultStored, String displayName) {
        try {
            if (vaultStored == null) return null;
            if (vaultStored.startsWith(INTERNAL_PREFIX)) {
                DocumentFile root = resolveRoot(context, vaultStored);
                DocumentFile created = createNewMarkdownFile(root);
                return created == null ? null : created.getUri();
            }
            Uri treeUri = Uri.parse(vaultStored);
            String parentDocId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId);
            String name = (displayName == null || displayName.trim().isEmpty())
                    ? uniqueDraftName() : displayName;
            String[] mimeTypes = {markdownMimeType(), "text/plain", "application/octet-stream", "*/*"};
            for (String mime : mimeTypes) {
                try {
                    Uri uri = DocumentsContract.createDocument(
                            context.getContentResolver(), parentDocUri, mime, name);
                    if (uri != null) return uri;
                } catch (Exception ignored) {

                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}


