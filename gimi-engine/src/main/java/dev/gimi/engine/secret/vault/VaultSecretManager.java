package dev.gimi.engine.secret.vault;

import dev.gimi.core.model.secret.*;
import java.util.*;

public interface VaultSecretManager {
    String getSecret(String storeId, String path);
    void putSecret(String storeId, String path, String value);
    void deleteSecret(String storeId, String path);
    List<String> listSecrets(String storeId, String prefix);

    // Managed secrets (with metadata)
    ManagedSecret create(ManagedSecret secret, String value);
    ManagedSecret update(String id, String value);
    String read(String id);
    void delete(String id);
    Optional<ManagedSecret> getMeta(String id);
    List<ManagedSecret> listManaged();

    // Secret Stores
    SecretStore createStore(SecretStore store);
    List<SecretStore> listStores();
    Optional<SecretStore> getStore(String id);
    void deleteStore(String id);
}
