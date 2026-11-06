package com.rassvet.essential.data.sync;

import android.content.Context;
import com.rassvet.essential.data.vault.VaultDocuments;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class VaultPackager {
    private VaultPackager() {}

    public static byte[] zipMarkdownVault(Context context, String vaultStored) throws IOException {
        androidx.documentfile.provider.DocumentFile root = VaultDocuments.resolveRoot(context, vaultStored);
        if (root == null) {
            throw new IllegalStateException("vault not available");
        }
        java.util.List<androidx.documentfile.provider.DocumentFile> files =
                VaultDocuments.listMarkdown(root);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(output)) {
            for (androidx.documentfile.provider.DocumentFile file : files) {
                String name = file.getName();
                if (name == null) continue;
                String entryName = sanitizeEntryName(name);
                zos.putNextEntry(new ZipEntry(entryName));
                String text = VaultDocuments.readText(context, file, vaultStored);
                zos.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return output.toByteArray();
    }


    public static void unzipIntoVault(Context context, String vaultStored, byte[] zipBytes)
            throws IOException {
        androidx.documentfile.provider.DocumentFile root =
                VaultDocuments.resolveRoot(context, vaultStored);
        if (root == null) {
            throw new IllegalStateException("vault not available");
        }
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    String name = sanitizeEntryName(basename(entry.getName()));
                    if (name.toLowerCase().endsWith(".md")) {
                        androidx.documentfile.provider.DocumentFile existing = root.findFile(name);
                        androidx.documentfile.provider.DocumentFile target =
                                existing != null
                                        ? existing
                                        : root.createFile("application/octet-stream", name);
                        if (target != null) {
                            android.net.Uri uri =
                                    VaultDocuments.writableUriForVaultDocument(
                                            context, vaultStored, target.getUri());
                            byte[] bytes = readCurrentEntryFully(zis);
                            try (java.io.OutputStream out =
                                            context.getContentResolver()
                                                    .openOutputStream(uri, "wt")) {
                                if (out != null) {
                                    out.write(bytes);
                                }
                            }
                        }
                    }
                }
                zis.closeEntry();
                entry = zis.getNextEntry();
            }
        }
    }

    private static String basename(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static byte[] readCurrentEntryFully(InputStream zis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = zis.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    private static String sanitizeEntryName(String raw) {
        String cleaned = raw.replace("..", "").trim();
        return cleaned.isEmpty() ? "note.md" : cleaned;
    }
}


