/*
 * Copyright 2017 StreamSets Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.datacollector.credential.javakeystore;

import com.google.common.annotations.VisibleForTesting;
import com.streamsets.datacollector.io.DataStore;
import com.streamsets.lib.security.SshUtils;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.credential.CredentialStore;
import com.streamsets.pipeline.api.credential.CredentialStoreDef;
import com.streamsets.pipeline.api.credential.CredentialValue;
import com.streamsets.pipeline.api.impl.Utils;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Credential store backed by a Java Keystore, JCEKS or PCKS12 formats only.
 * <p/>
 * The credentials are stored as if they would be AES keys leveraging the fact that the key length is not checked.
 * <p/>
 * The store uses data collector's <code>DataStore</code> to avoid corruption on the keystore file.
 * <p/>
 * This implementation has 3 (required) configuration values:
 * <ul>
 *   <li>credentialStore.[ID].keystore.type: the type of the keystore JCEKS or PCKS12</li>
 *   <li>credentialStore.[ID].keystore.file: the keystore file. If a relative path is set,the keystore file is
 *   looked into the SDC configuration directory. If an absolute path is set, the keystore file is looked into that
 *   absolute location.</li>
 *   <li>credentialStore.[ID].keystore.storePassword: the keystore file and entries password.</li>
 * </ul>
 * <p/>
 * This implementation has additional methods, not part of the CredentialStore API, to add, delete and list credentials.
 * These methods are used by the command line tool 'streamsets jks-cs'.
 * <p/>
 * NOTE: there can be multiple credential stores of the same implementation, each one is identified an configured
 * via its ID in the configuration.
 */
@CredentialStoreDef(label = "Java KeyStore")
public class JavaKeyStoreCredentialStore implements CredentialStore {
  private static final Logger LOG = LoggerFactory.getLogger(JavaKeyStoreCredentialStore.class);
  private static final Set<String> VALID_KEYSTORE_TYPES = new HashSet<>();

  static final String KEYSTORE_TYPE_KEY = "keystore.type";
  static final String KEYSTORE_FILE_KEY = "keystore.file";
  static final String KEYSTORE_PASSWORD_KEY = "keystore.storePassword"; // NOSONAR

  static final String SSH_PRIVATE_KEY_SECRET = "sdc/defaultPrivateKey";
  static final String SSH_PRIVATE_KEY_PASSWORD_SECRET = "sdc/defaultPrivateKeyPassword";
  static final String SSH_PUBLIC_KEY_SECRET = "sdc/defaultPublicKey";

  static {
    VALID_KEYSTORE_TYPES.add("JCEKS");
    VALID_KEYSTORE_TYPES.add("PKCS12");
  }

  private Context context;
  private String keystoreType;
  private String keystorePassword;
  private File keyStoreFile;
  private JKSManager manager;

  @Override
  public List<ConfigIssue> init(Context context) {
    List<ConfigIssue> issues = new ArrayList<>();
    this.context = context;

    keystoreType = context.getConfig(KEYSTORE_TYPE_KEY);
    if (keystoreType == null) {
      issues.add(context.createConfigIssue(Errors.JKS_CRED_STORE_000, KEYSTORE_TYPE_KEY));
    } else {
      if (!VALID_KEYSTORE_TYPES.contains(keystoreType.toUpperCase())) {
        issues.add(context.createConfigIssue(Errors.JKS_CRED_STORE_002, keystoreType));
      }
    }

    String fileName = context.getConfig(KEYSTORE_FILE_KEY);
    if (fileName == null) {
      issues.add(context.createConfigIssue(Errors.JKS_CRED_STORE_000, KEYSTORE_FILE_KEY));
    } else {
      keyStoreFile = new File(fileName);
      if (!keyStoreFile.isAbsolute()) {
        keyStoreFile = new File(System.getProperty("sdc.conf.dir"), fileName);
      }
      manager = createManager();
    }

    keystorePassword = context.getConfig(KEYSTORE_PASSWORD_KEY);
    if (keystorePassword == null) {
      issues.add(context.createConfigIssue(Errors.JKS_CRED_STORE_000, KEYSTORE_PASSWORD_KEY));
    }
    if (issues.isEmpty()) {
      try {
        manager.initOrCreateKeyStore();
        if (manager.getEntry(SSH_PRIVATE_KEY_SECRET) == null) {
          generateDefaultSshKeyInfo();
          LOG.info("Autogenerated default SDC SSH key info");
        }
      } catch (Exception ex) {
        issues.add(context.createConfigIssue(Errors.JKS_CRED_STORE_004, SSH_PRIVATE_KEY_PASSWORD_SECRET));
      }
    }
    return issues;
  }

  Context getContext() {
    return context;
  }


  @VisibleForTesting
  File getKeyStoreFile() {
    return keyStoreFile;
  }

  String getKeystoreType() {
    return keystoreType;
  }

  String getKeystorePassword() {
    return keystorePassword;
  }


  @VisibleForTesting
  JKSManager createManager() {
    return new JKSManager();
  }

  class JksCredentialValue implements CredentialValue {
    private final String name;

    JksCredentialValue(String name) {
      this.name = name;
    }

    @Override
    public String get() throws StageException {
      String credential = null;
      if (!manager.isKeyStoreExists()) {
        throw new StageException(Errors.JKS_CRED_STORE_003, name);
      } else {
        try {
          KeyStore.SecretKeyEntry entry = manager.getEntry(name);
          if (entry != null) {
            credential = new String(entry.getSecretKey().getEncoded(), StandardCharsets.UTF_8);
          } else {
            throw new StageException(Errors.JKS_CRED_STORE_003, name);
          }
        } catch (Exception ex) {
          throw new StageException(Errors.JKS_CRED_STORE_004, name, ex);
        }
      }
      return credential;
    }

    @Override
    public String toString() {
      return "JksCredentialValue{}";
    }

  }

  @Override
  public CredentialValue get(String group, String name, String credentialStoreOptions) throws StageException {
    Utils.checkNotNull(group, "group cannot be NULL");
    Utils.checkNotNull(name, "name cannot be NULL");
    CredentialValue credential = new JksCredentialValue(name);
    credential.get();
    return credential;
  }

  @Override
  public void destroy() {

  }

  long now() {
    return System.currentTimeMillis();
  }

  public void storeCredential(String name, String credential) {
    Utils.checkNotNull(name, "name cannot be NULL");
    Utils.checkNotNull(credential, "credential cannot be NULL");
    try {
      // the keystore does not check the length of the KEY whe adding a AES key,
      // so we just put the credential as AES key payload.
      SecretKey secret = new SecretKeySpec(credential.getBytes(StandardCharsets.UTF_8), "AES");
      manager.addOrUpdateEntry(name, secret);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public void deleteCredential(String name) {
    Utils.checkNotNull(name, "name cannot be NULL");
    manager.deleteEntry(name);
  }

  public void generateDefaultSshKeyInfo() {
    SshUtils.SshKeyInfoBean bean = createSshKeyInfo();
    storeCredential(SSH_PRIVATE_KEY_SECRET, bean.getPrivateKey());
    storeCredential(SSH_PRIVATE_KEY_PASSWORD_SECRET, bean.getPassword());
    storeCredential(SSH_PUBLIC_KEY_SECRET, bean.getPublicKey());
  }

  @VisibleForTesting
  SshUtils.SshKeyInfoBean createSshKeyInfo() {
    return SshUtils.createSshKeyInfoBean(4096, "Default SDC SSH key info");
  }

  public List<String> getAliases() {
    List<String> aliases = new ArrayList<>();
    try {
      KeyStore keyStore = manager.getKeyStore();
      if (keyStore != null) {
        Enumeration<String> it = keyStore.aliases();
        while (it.hasMoreElements()) {
          aliases.add(it.nextElement());
        }
      }
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
    return aliases;
  }


  class JKSManager {
    private volatile long keystoreTimestamp;
    private volatile KeyStore keyStore;
    //Lock to manage concurrent access to JKS
    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    JKSManager() {
    }

    @VisibleForTesting
    long getKeystoreTimestamp() {
      return keystoreTimestamp;
    }

    KeyStore getKeyStore() {
      if (needsToReloadKeyStore()) {
        keyStore = loadKeyStore();
      }
      return keyStore;
    }

    @VisibleForTesting
    boolean isKeyStoreExists() {
      return getKeyStore() != null;
    }

    private void initOrCreateKeyStore() {
      lock.writeLock().lock();
      try {
        if (needsToReloadKeyStore()) {
          keyStore = loadKeyStore();
        }
        if (keyStore == null) {
          keyStore = createKeyStore();
        }
      } finally {
        lock.writeLock().unlock();
      }
    }

    @VisibleForTesting
    boolean needsToReloadKeyStore() {
      lock.readLock().lock();
      try {
        return (keyStore == null) || (getKeyStoreFile().lastModified() > getKeystoreTimestamp() &&
            (now() - getKeyStoreFile().lastModified() > 10000));
      } finally {
        lock.readLock().unlock();
      }
    }

    private KeyStore createKeyStore() {
      lock.writeLock().lock();
      try {
        KeyStore kStore = KeyStore.getInstance(getKeystoreType());
        kStore.load(null);
        return kStore;
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      } finally {
        lock.writeLock().unlock();
      }
    }

    @VisibleForTesting
    KeyStore loadKeyStore() {
      KeyStore kStore = null;
      lock.readLock().lock();
      try {
        if (keyStoreFile.exists()) {
          keystoreTimestamp = keyStoreFile.lastModified();
          DataStore dataStore = new DataStore(keyStoreFile);
          try (InputStream is = dataStore.getInputStream()) {
            kStore = KeyStore.getInstance(getKeystoreType());
            kStore.load(is, getKeystorePassword().toCharArray());
            LOG.debug("Loaded keystore '{}'", keyStoreFile);
          } catch (Exception ex) {
            LOG.error("Failed to load keystore '{}': {}", keyStoreFile, ex);
            kStore = null;
          }
        }
        return kStore;
      } finally {
        lock.readLock().unlock();
      }
    }

    private void persistStore() throws IOException {
      lock.writeLock().lock();
      try {
        DataStore dataStore = new DataStore(keyStoreFile);
        try (OutputStream os = dataStore.getOutputStream()) {
          OutputStream csos = new CloseShieldOutputStream(os);
          keyStore.store(csos, getKeystorePassword().toCharArray());
          dataStore.commit(os);
        } catch (IOException ex) {
          throw ex;
        } catch (Exception ex) {
          throw new IOException(ex);
        } finally {
          dataStore.release();
        }
      } finally {
        lock.writeLock().unlock();
      }
    }

    private void deleteEntry(String name) {
      lock.writeLock().lock();
      try {
        manager.initOrCreateKeyStore();
        if (keyStore.getKey(name, getKeystorePassword().toCharArray()) != null) {
          keyStore.deleteEntry(name);
          persistStore();
        } else {
          throw new IOException(Utils.format("Credential Store '{}', alias '{}' not found", getContext().getId(), name));
        }
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      } finally {
        lock.writeLock().unlock();
      }
    }

    @VisibleForTesting
    KeyStore.SecretKeyEntry getEntry(String name) throws Exception {
      lock.readLock().lock();
      try {
        if (keyStore == null) {
          return null;
        }
        return (KeyStore.SecretKeyEntry) keyStore.getEntry(
            name,
            new KeyStore.PasswordProtection(getKeystorePassword().toCharArray())
        );
      } finally {
        lock.readLock().unlock();
      }
    }


    private void addOrUpdateEntry(String name, SecretKey secret) {
      lock.writeLock().lock();
      try {
        manager.initOrCreateKeyStore();
        keyStore.setEntry(
            name,
            new KeyStore.SecretKeyEntry(secret),
            new KeyStore.PasswordProtection(getKeystorePassword().toCharArray())
        );
        persistStore();
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      } finally {
        lock.writeLock().unlock();
      }
    }
  }
}
