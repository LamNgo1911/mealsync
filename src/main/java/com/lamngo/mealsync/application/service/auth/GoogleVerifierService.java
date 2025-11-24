package com.lamngo.mealsync.application.service.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GoogleVerifierService {

    @Value("${GOOGLE_CLIENT_ID}")
    private String googleClientId;

    @Value("${GOOGLE_IOS_CLIENT_ID:}")
    private String googleIosClientId;

    @Value("${GOOGLE_ANDROID_CLIENT_ID:}")
    private String googleAndroidClientId;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    public void initVerifier() {
        List<String> audiences = new ArrayList<>();
        audiences.add(googleClientId);
        if (googleIosClientId != null && !googleIosClientId.isEmpty()) {
            audiences.add(googleIosClientId);
        }
        if (googleAndroidClientId != null && !googleAndroidClientId.isEmpty()) {
            audiences.add(googleAndroidClientId);
        }

        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(audiences)
                .build();
    }

    // Setter for test injection
    void setVerifier(GoogleIdTokenVerifier verifier) {
        this.verifier = verifier;
    }

    public GoogleIdToken.Payload verify(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            return idToken != null ? idToken.getPayload() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
