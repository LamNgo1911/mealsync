package com.lamngo.mealsync.presentation.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class UniversalLinkControllerTest {

    @InjectMocks
    private UniversalLinkController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "iosTeamId", "M2NZ958MSC");
        ReflectionTestUtils.setField(controller, "iosBundleId", "com.yourcompany.cookify");
        ReflectionTestUtils.setField(controller, "androidPackageName", "com.yourcompany.cookify");
        ReflectionTestUtils.setField(controller, "androidSha256Fingerprint", "AA:BB:CC:DD:EE:FF");
    }

    @Test
    void appleAppSiteAssociation_shouldReturnValidJson() {
        // When
        ResponseEntity<Map<String, Object>> response = controller.appleAppSiteAssociation();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertNotNull(response.getBody());

        Map<String, Object> body = response.getBody();
        assertTrue(body.containsKey("applinks"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> applinks = (Map<String, Object>) body.get("applinks");
        assertNotNull(applinks);
        assertTrue(applinks.containsKey("details"));
        assertTrue(applinks.containsKey("apps"));
    }

    @Test
    void appleAppSiteAssociation_shouldContainCorrectAppId() {
        // When
        ResponseEntity<Map<String, Object>> response = controller.appleAppSiteAssociation();

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> applinks = (Map<String, Object>) response.getBody().get("applinks");
        @SuppressWarnings("unchecked")
        Object[] details = (Object[]) applinks.get("details");
        
        assertNotNull(details);
        assertEquals(1, details.length);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) details[0];
        assertEquals("M2NZ958MSC.com.yourcompany.cookify", detail.get("appID"));
    }

    @Test
    void appleAppSiteAssociation_shouldContainCorrectPaths() {
        // When
        ResponseEntity<Map<String, Object>> response = controller.appleAppSiteAssociation();

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> applinks = (Map<String, Object>) response.getBody().get("applinks");
        @SuppressWarnings("unchecked")
        Object[] details = (Object[]) applinks.get("details");
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) details[0];
        String[] paths = (String[]) detail.get("paths");
        
        assertNotNull(paths);
        assertTrue(paths.length > 0);
        assertTrue(java.util.Arrays.asList(paths).contains("/api/v1/users/verify-email*"));
        assertTrue(java.util.Arrays.asList(paths).contains("/login*"));
        assertTrue(java.util.Arrays.asList(paths).contains("/verify*"));
    }

    @Test
    void androidAssetLinks_shouldReturnValidJson() {
        // When
        ResponseEntity<Object[]> response = controller.androidAssetLinks();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().length);
    }

    @Test
    void androidAssetLinks_shouldContainCorrectPackageName() {
        // When
        ResponseEntity<Object[]> response = controller.androidAssetLinks();

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> link = (Map<String, Object>) response.getBody()[0];
        @SuppressWarnings("unchecked")
        Map<String, Object> target = (Map<String, Object>) link.get("target");
        
        assertEquals("android_app", target.get("namespace"));
        assertEquals("com.yourcompany.cookify", target.get("package_name"));
    }

    @Test
    void androidAssetLinks_shouldContainCorrectFingerprint() {
        // When
        ResponseEntity<Object[]> response = controller.androidAssetLinks();

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> link = (Map<String, Object>) response.getBody()[0];
        @SuppressWarnings("unchecked")
        Map<String, Object> target = (Map<String, Object>) link.get("target");
        String[] fingerprints = (String[]) target.get("sha256_cert_fingerprints");
        
        assertNotNull(fingerprints);
        assertEquals(1, fingerprints.length);
        assertEquals("AA:BB:CC:DD:EE:FF", fingerprints[0]);
    }

    @Test
    void androidAssetLinks_shouldContainCorrectRelation() {
        // When
        ResponseEntity<Object[]> response = controller.androidAssetLinks();

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> link = (Map<String, Object>) response.getBody()[0];
        String[] relations = (String[]) link.get("relation");
        
        assertNotNull(relations);
        assertEquals(1, relations.length);
        assertEquals("delegate_permission/common.handle_all_urls", relations[0]);
    }
}

