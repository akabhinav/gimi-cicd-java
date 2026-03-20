package dev.gimi.engine.secret.aws;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * AWS Signature Version 4 signer for authenticating REST API requests.
 * Implements the SigV4 signing process using Java standard library only.
 */
public class AwsSigV4Signer {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final String accessKeyId;
    private final String secretAccessKey;
    private final String region;

    public AwsSigV4Signer(String accessKeyId, String secretAccessKey, String region) {
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.region = region;
    }

    /**
     * Generates the Authorization header value for an AWS API request.
     *
     * @param method      HTTP method (GET, POST, etc.)
     * @param uri         request URI
     * @param service     AWS service name (secretsmanager, kms)
     * @param headers     request headers (must include host and x-amz-date)
     * @param payload     request body (empty string for GET)
     * @return map of headers to add (Authorization, x-amz-date, host)
     */
    public Map<String, String> sign(String method, URI uri, String service,
                                     Map<String, String> headers, String payload) {
        Instant now = Instant.now();
        String dateStamp = DATE_FORMAT.format(now);
        String amzDate = DATETIME_FORMAT.format(now);

        Map<String, String> signedHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        signedHeaders.putAll(headers);
        signedHeaders.put("host", uri.getHost());
        signedHeaders.put("x-amz-date", amzDate);

        String payloadHash = sha256Hex(payload != null ? payload : "");
        signedHeaders.put("x-amz-content-sha256", payloadHash);

        // Canonical request
        String signedHeaderNames = String.join(";",
                signedHeaders.keySet().stream().sorted().toList());
        String canonicalHeaders = signedHeaders.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey().toLowerCase() + ":" + e.getValue().trim())
                .reduce("", (a, b) -> a + b + "\n");

        String canonicalUri = uri.getPath().isEmpty() ? "/" : uri.getPath();
        String canonicalQueryString = uri.getQuery() != null ? uri.getQuery() : "";

        String canonicalRequest = method + "\n" + canonicalUri + "\n" +
                canonicalQueryString + "\n" + canonicalHeaders + "\n" +
                signedHeaderNames + "\n" + payloadHash;

        // String to sign
        String credentialScope = dateStamp + "/" + region + "/" + service + "/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" +
                credentialScope + "\n" + sha256Hex(canonicalRequest);

        // Signing key
        byte[] signingKey = getSignatureKey(secretAccessKey, dateStamp, region, service);
        String signature = hmacSha256Hex(signingKey, stringToSign);

        String authorization = "AWS4-HMAC-SHA256 " +
                "Credential=" + accessKeyId + "/" + credentialScope + ", " +
                "SignedHeaders=" + signedHeaderNames + ", " +
                "Signature=" + signature;

        Map<String, String> result = new HashMap<>(signedHeaders);
        result.put("Authorization", authorization);
        return result;
    }

    private byte[] getSignatureKey(String key, String dateStamp, String region, String service) {
        byte[] kDate = hmacSha256(("AWS4" + key).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, "aws4_request");
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 failed", e);
        }
    }

    private static String hmacSha256Hex(byte[] key, String data) {
        return bytesToHex(hmacSha256(key, data));
    }

    private static String sha256Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 failed", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
