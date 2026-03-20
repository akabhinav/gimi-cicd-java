package dev.gimi.core.model.saas;

/** Available hosting regions for SaaS tenants. */
public enum SaasRegion {
    US_EAST_1("us-east-1", "N. Virginia", "AWS"),
    US_WEST_2("us-west-2", "Oregon", "AWS"),
    EU_WEST_1("eu-west-1", "Ireland", "AWS"),
    EU_CENTRAL_1("eu-central-1", "Frankfurt", "AWS"),
    AP_SOUTH_1("ap-south-1", "Mumbai", "AWS"),
    AP_SOUTHEAST_1("ap-southeast-1", "Singapore", "AWS"),
    GCP_US_CENTRAL1("us-central1", "Iowa", "GCP"),
    GCP_EUROPE_WEST1("europe-west1", "Belgium", "GCP"),
    AZURE_EASTUS("eastus", "Virginia", "Azure"),
    AZURE_WESTEUROPE("westeurope", "Netherlands", "Azure");

    private final String code;
    private final String displayName;
    private final String cloudProvider;

    SaasRegion(String code, String displayName, String cloudProvider) {
        this.code = code;
        this.displayName = displayName;
        this.cloudProvider = cloudProvider;
    }

    public String code() { return code; }
    public String displayName() { return displayName; }
    public String cloudProvider() { return cloudProvider; }
}
