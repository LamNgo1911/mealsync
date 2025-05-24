package com.lamngo.mealsync.application.service.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleVerifierServiceTest {
    @Mock
    private GoogleIdTokenVerifier verifier;
    @Mock
    private GoogleIdToken idToken;
    @Mock
    private GoogleIdToken.Payload payload;
    @InjectMocks
    private GoogleVerifierService googleVerifierService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        googleVerifierService = new GoogleVerifierService();
        // Inject the mock verifier
        googleVerifierService.setVerifier(verifier);
    }

    @Test
    void dummyTest() {
        assertTrue(true);
    }

    @Test
    void verify_shouldReturnPayload_whenTokenIsValid() throws Exception {
        when(verifier.verify("validToken")).thenReturn(idToken);
        when(idToken.getPayload()).thenReturn(payload);
        GoogleIdToken.Payload result = googleVerifierService.verify("validToken");
        assertNotNull(result);
        assertEquals(payload, result);
    }

    @Test
    void verify_shouldReturnNull_whenTokenIsInvalid() throws Exception {
        when(verifier.verify("invalidToken")).thenReturn(null);
        GoogleIdToken.Payload result = googleVerifierService.verify("invalidToken");
        assertNull(result);
    }

    @Test
    void verify_shouldReturnNull_whenVerifierThrowsException() throws Exception {
        when(verifier.verify("exceptionToken")).thenThrow(new RuntimeException("error"));
        GoogleIdToken.Payload result = googleVerifierService.verify("exceptionToken");
        assertNull(result);
    }
}
