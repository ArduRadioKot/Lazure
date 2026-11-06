package com.rassvet.essential.data.sync;

import android.content.Context;
import com.rassvet.essential.data.api.EssentialApi;
import com.rassvet.essential.data.api.VaultSnapshotItemJson;
import java.util.Comparator;
import java.util.List;

public final class VaultSyncRepository {
    private final EssentialApi api;

    public VaultSyncRepository(EssentialApi api) {
        this.api = api;
    }

    public void uploadEncryptedSnapshot(Context context, String vaultStored, String token, String passphrase)
            throws Exception {
        byte[] plain = VaultPackager.zipMarkdownVault(context, vaultStored);
        byte[] cipher = VaultCryptography.encrypt(plain, passphrase);
        api.uploadSnapshot(token, cipher);
    }

    public void restoreLatestSnapshot(Context context, String vaultStored, String token, String passphrase)
            throws Exception {
        List<VaultSnapshotItemJson> snapshots = api.listSnapshots(token);
        VaultSnapshotItemJson latest =
                snapshots.stream()
                        .max(
                                Comparator.comparingLong(VaultSnapshotItemJson::getVersion)
                                        .thenComparingLong(VaultSnapshotItemJson::getCreatedAtEpochMs))
                        .orElseThrow(() -> new IllegalStateException("no snapshots"));
        byte[] cipher = api.downloadSnapshot(token, latest.getId());
        byte[] plain = VaultCryptography.decrypt(cipher, passphrase);
        VaultPackager.unzipIntoVault(context, vaultStored, plain);
    }
}


