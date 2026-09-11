package com.dissertation.license;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-first enforcement: the client gets the decode seed ONLY after presenting a valid license.
 * Unknown/invalid license -> 403 -> no seed -> the protected method cannot decode -> app locks
 * (fail-secure). One exchange validates the license AND delivers the decode secret (interlocked).
 */
@RestController
public class SeedController {

    private static final Logger log = LoggerFactory.getLogger(SeedController.class);

    // A1 (server-side failure-oracle suppression, the twin of the client-side F7): every /seed refusal
    // returns this SAME opaque body, so a probing attacker cannot distinguish "unknown license" from
    // "bound to another machine" from "missing fingerprint". The real reason is written to the server log
    // only, never crossing the trust boundary to the client. Behaviour-neutral: the client keys only on
    // the HTTP status (200 vs not), never on the body.
    private static final String REFUSED = "license request refused";

    private final LicenseStore store;

    // F5: shared vendor secret required to (re)register a license. Configured in application.properties;
    // if unset/empty, registration is refused (secure-by-default). The build presents it as ?token=...
    @org.springframework.beans.factory.annotation.Value("${obzcure.register.secret:}")
    private String registerSecret;

    public SeedController(LicenseStore store) { this.store = store; }

    // Vendor / build step: register the per-build seed (printed as OBZCURE_SEED=...) against a license.
    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestParam String license, @RequestParam long seed,
                                           @RequestParam(required = false) String token) {
        // F5: without a valid vendor token an unauthenticated caller could (re)register a license and
        // reset its node-lock binding to a null fingerprint (binding hijack). Missing/wrong token -> 403.
        if (registerSecret == null || registerSecret.isEmpty() || !registerSecret.equals(token))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("register: invalid or missing vendor token");
        store.register(license, seed);
        return ResponseEntity.ok("registered license=" + license);
    }

    // Client: fetch the seed for a license, node-locked to the first machine that activates it.
    // Every refusal returns HTTP 403 with the SAME opaque body (A1); the reason is logged server-side only.
    @GetMapping("/seed")
    public ResponseEntity<String> seed(@RequestParam String license,
                                       @RequestParam(required = false) String fingerprint) {
        // A2: node-lock requires a fingerprint. A null/blank one is refused (previously it bound null and
        // handed the seed out unbound). required=false is kept so a missing param yields our uniform 403,
        // not Spring's distinguishable 400.
        if (fingerprint == null || fingerprint.isBlank()) {
            log.warn("seed refused: missing fingerprint for license '{}'", license);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(REFUSED);
        }
        LicenseStore.Entry e = store.get(license);
        if (e == null) {
            log.warn("seed refused: unknown license '{}'", license);            // A1: reason to log only
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(REFUSED);
        }
        synchronized (e) {
            if (e.fingerprint() == null)
                e.fingerprint = fingerprint;                          // trust-on-first-use activation
            else if (!java.util.Objects.equals(e.fingerprint(), fingerprint)) {
                log.warn("seed refused: license '{}' bound to another machine (fingerprint mismatch)", license); // A1
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(REFUSED);
            }
        }
        log.info("seed delivered: license '{}' (fingerprint bound)", license);
        return ResponseEntity.ok(Long.toString(e.seed()));
    }
}
