package dev.gimi.engine.secret.aws;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class AwsSigV4SignerTest {

    private static final String ACCESS_KEY = "AKIAIOSFODNN7EXAMPLE";
    private static final String SECRET_KEY = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
    private static final String REGION = "us-east-1";

    @Test
    void sign_producesAuthorizationHeader() {
        AwsSigV4Signer signer = new AwsSigV4Signer(ACCESS_KEY, SECRET_KEY, REGION);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/x-amz-json-1.1");
        headers.put("X-Amz-Target", "secretsmanager.GetSecretValue");

        Map<String, String> signed = signer.sign("POST",
                URI.create("https://secretsmanager.us-east-1.amazonaws.com"),
                "secretsmanager", headers, "{\"SecretId\":\"test\"}");

        assertThat(signed).containsKey("Authorization");
        assertThat(signed.get("Authorization")).startsWith("AWS4-HMAC-SHA256");
        assertThat(signed.get("Authorization")).contains("Credential=" + ACCESS_KEY);
        assertThat(signed.get("Authorization")).contains("SignedHeaders=");
        assertThat(signed.get("Authorization")).contains("Signature=");
    }

    @Test
    void sign_includesAmzDateHeader() {
        AwsSigV4Signer signer = new AwsSigV4Signer(ACCESS_KEY, SECRET_KEY, REGION);

        Map<String, String> signed = signer.sign("POST",
                URI.create("https://kms.us-east-1.amazonaws.com"),
                "kms", new HashMap<>(), "{}");

        assertThat(signed).containsKey("x-amz-date");
        assertThat(signed.get("x-amz-date")).matches("\\d{8}T\\d{6}Z");
    }

    @Test
    void sign_includesHostHeader() {
        AwsSigV4Signer signer = new AwsSigV4Signer(ACCESS_KEY, SECRET_KEY, REGION);

        Map<String, String> signed = signer.sign("POST",
                URI.create("https://secretsmanager.us-east-1.amazonaws.com"),
                "secretsmanager", new HashMap<>(), "");

        assertThat(signed).containsKey("host");
        assertThat(signed.get("host")).isEqualTo("secretsmanager.us-east-1.amazonaws.com");
    }

    @Test
    void sign_includesContentSha256Header() {
        AwsSigV4Signer signer = new AwsSigV4Signer(ACCESS_KEY, SECRET_KEY, REGION);

        Map<String, String> signed = signer.sign("POST",
                URI.create("https://secretsmanager.us-east-1.amazonaws.com"),
                "secretsmanager", new HashMap<>(), "{\"test\":true}");

        assertThat(signed).containsKey("x-amz-content-sha256");
        assertThat(signed.get("x-amz-content-sha256")).hasSize(64); // SHA-256 hex = 64 chars
    }

    @Test
    void sign_credentialContainsRegionAndService() {
        AwsSigV4Signer signer = new AwsSigV4Signer(ACCESS_KEY, SECRET_KEY, "eu-west-1");

        Map<String, String> signed = signer.sign("POST",
                URI.create("https://secretsmanager.eu-west-1.amazonaws.com"),
                "secretsmanager", new HashMap<>(), "{}");

        String auth = signed.get("Authorization");
        assertThat(auth).contains("eu-west-1/secretsmanager/aws4_request");
    }

    @Test
    void sign_differentPayloads_produceDifferentSignatures() {
        AwsSigV4Signer signer = new AwsSigV4Signer(ACCESS_KEY, SECRET_KEY, REGION);
        URI uri = URI.create("https://secretsmanager.us-east-1.amazonaws.com");

        Map<String, String> signed1 = signer.sign("POST", uri, "secretsmanager",
                new HashMap<>(), "{\"SecretId\":\"secret-1\"}");
        Map<String, String> signed2 = signer.sign("POST", uri, "secretsmanager",
                new HashMap<>(), "{\"SecretId\":\"secret-2\"}");

        // Content SHA will differ
        assertThat(signed1.get("x-amz-content-sha256"))
                .isNotEqualTo(signed2.get("x-amz-content-sha256"));
    }

    @Test
    void sign_nullPayload_treatedAsEmpty() {
        AwsSigV4Signer signer = new AwsSigV4Signer(ACCESS_KEY, SECRET_KEY, REGION);

        Map<String, String> signed = signer.sign("GET",
                URI.create("https://secretsmanager.us-east-1.amazonaws.com"),
                "secretsmanager", new HashMap<>(), null);

        assertThat(signed).containsKey("Authorization");
        // SHA-256 of empty string
        assertThat(signed.get("x-amz-content-sha256"))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void sign_getRequest_works() {
        AwsSigV4Signer signer = new AwsSigV4Signer(ACCESS_KEY, SECRET_KEY, REGION);

        Map<String, String> signed = signer.sign("GET",
                URI.create("https://kms.us-east-1.amazonaws.com/"),
                "kms", new HashMap<>(), "");

        assertThat(signed.get("Authorization")).startsWith("AWS4-HMAC-SHA256");
    }

    @Test
    void sign_preservesExistingHeaders() {
        AwsSigV4Signer signer = new AwsSigV4Signer(ACCESS_KEY, SECRET_KEY, REGION);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/x-amz-json-1.1");
        headers.put("X-Amz-Target", "TrentService.Encrypt");

        Map<String, String> signed = signer.sign("POST",
                URI.create("https://kms.us-east-1.amazonaws.com"),
                "kms", headers, "{}");

        assertThat(signed.get("Content-Type")).isEqualTo("application/x-amz-json-1.1");
        assertThat(signed.get("X-Amz-Target")).isEqualTo("TrentService.Encrypt");
    }

    @Test
    void sign_signedHeadersListIsLowercase() {
        AwsSigV4Signer signer = new AwsSigV4Signer(ACCESS_KEY, SECRET_KEY, REGION);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        Map<String, String> signed = signer.sign("POST",
                URI.create("https://secretsmanager.us-east-1.amazonaws.com"),
                "secretsmanager", headers, "{}");

        String auth = signed.get("Authorization");
        // SignedHeaders should contain header names separated by ;
        assertThat(auth).containsPattern("SignedHeaders=[a-zA-Z0-9;_-]+,");
    }

    @Test
    void sign_differentRegions_differentCredentials() {
        AwsSigV4Signer signer1 = new AwsSigV4Signer(ACCESS_KEY, SECRET_KEY, "us-east-1");
        AwsSigV4Signer signer2 = new AwsSigV4Signer(ACCESS_KEY, SECRET_KEY, "ap-southeast-1");
        URI uri = URI.create("https://secretsmanager.us-east-1.amazonaws.com");

        Map<String, String> signed1 = signer1.sign("POST", uri, "secretsmanager", new HashMap<>(), "{}");
        Map<String, String> signed2 = signer2.sign("POST", uri, "secretsmanager", new HashMap<>(), "{}");

        assertThat(signed1.get("Authorization")).contains("us-east-1");
        assertThat(signed2.get("Authorization")).contains("ap-southeast-1");
    }

    @Test
    void sign_differentServices_differentCredentials() {
        AwsSigV4Signer signer = new AwsSigV4Signer(ACCESS_KEY, SECRET_KEY, REGION);
        URI uri = URI.create("https://example.us-east-1.amazonaws.com");

        Map<String, String> signedSM = signer.sign("POST", uri, "secretsmanager", new HashMap<>(), "{}");
        Map<String, String> signedKMS = signer.sign("POST", uri, "kms", new HashMap<>(), "{}");

        assertThat(signedSM.get("Authorization")).contains("secretsmanager/aws4_request");
        assertThat(signedKMS.get("Authorization")).contains("kms/aws4_request");
    }
}
