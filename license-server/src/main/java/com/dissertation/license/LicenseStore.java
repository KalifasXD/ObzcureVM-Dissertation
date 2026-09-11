package com.dissertation.license;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory license store with node-locking (trust-on-first-use).
 *
 * Each license maps to a per-build seed and, once a client first activates, to the hardware
 * fingerprint of that machine. A later request presenting a DIFFERENT fingerprint for the same
 * license is refused (license sharing defeated). Re-registering a license (a fresh build) resets the
 * binding so the next machine to activate becomes the bound one.
 *
 * PoC scope: the fingerprint is computed client-side, so a determined attacker who controls the client
 * can spoof it. This demonstrates the license-to-machine binding mechanism, not spoof-proof
 * node-locking (which needs hardware attestation / TPM). See EVALUATION_EVIDENCE.md boundaries.
 */
@Component
public class LicenseStore {

    /** One license's state: its seed, plus the machine it is bound to (null until first activation). */
    public static final class Entry {
        final long seed;
        volatile String fingerprint; // null until first activation
        Entry(long seed) { this.seed = seed; }
        public long seed() { return seed; }
        public String fingerprint() { return fingerprint; }
    }

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();

    /** Vendor/build step: (re)register a license with its per-build seed. Resets any machine binding. */
    public void register(String license, long seed) { map.put(license, new Entry(seed)); }

    public Entry get(String license) { return map.get(license); }
}
