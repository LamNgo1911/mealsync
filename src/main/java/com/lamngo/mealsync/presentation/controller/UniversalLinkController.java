package com.lamngo.mealsync.presentation.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for serving Universal Links (iOS) and App Links (Android) configuration files.
 * These files must be served from the root domain (cookify.dev) with DNS-only mode (not proxied through Cloudflare).
 */
@RestController
public class UniversalLinkController {
    
    @Value("${app.ios.team-id:YOUR_TEAM_ID}")
    private String iosTeamId;
    
    @Value("${app.ios.bundle-id:com.yourcompany.cookify}")
    private String iosBundleId;
    
    @Value("${app.android.package-name:com.yourcompany.cookify}")
    private String androidPackageName;
    
    @Value("${app.android.sha256-fingerprint:YOUR_SHA256_FINGERPRINT}")
    private String androidSha256Fingerprint;
    
    /**
     * iOS Universal Links - apple-app-site-association file
     * Must be served at: https://cookify.dev/.well-known/apple-app-site-association
     * Requirements:
     * - Content-Type: application/json
     * - No file extension
     * - Served over HTTPS
     * - DNS only (not proxied through Cloudflare)
     */
    @GetMapping(value = "/.well-known/apple-app-site-association", 
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> appleAppSiteAssociation() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> applinks = new HashMap<>();
        Map<String, Object> details = new HashMap<>();
        
        // Format: TEAM_ID.BUNDLE_ID
        details.put("appID", iosTeamId + "." + iosBundleId);
        
        // Paths that should open in the app
        details.put("paths", new String[]{
            "/api/v1/users/verify-email*",
            "/login*",
            "/verify*"
        });
        
        applinks.put("apps", new String[]{});
        applinks.put("details", new Object[]{details});
        response.put("applinks", applinks);
        
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(response);
    }
    
    /**
     * Android App Links - assetlinks.json file
     * Must be served at: https://cookify.dev/.well-known/assetlinks.json
     * Requirements:
     * - Content-Type: application/json
     * - Served over HTTPS
     * - DNS only (not proxied through Cloudflare)
     */
    @GetMapping(value = "/.well-known/assetlinks.json",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object[]> androidAssetLinks() {
        Map<String, Object> target = new HashMap<>();
        target.put("namespace", "android_app");
        target.put("package_name", androidPackageName);
        target.put("sha256_cert_fingerprints", new String[]{androidSha256Fingerprint});
        
        Map<String, Object> link = new HashMap<>();
        link.put("relation", new String[]{"delegate_permission/common.handle_all_urls"});
        link.put("target", target);
        
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(new Object[]{link});
    }
}

