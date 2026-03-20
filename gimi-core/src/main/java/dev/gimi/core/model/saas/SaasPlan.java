package dev.gimi.core.model.saas;

/** SaaS pricing plans with associated limits. */
public enum SaasPlan {
    FREE(1, 100, 5, 1, 0),
    TEAM(5, 1000, 25, 3, 29),
    PROFESSIONAL(25, 10000, 100, 10, 99),
    ENTERPRISE(0, 0, 0, 0, 499); // 0 = unlimited

    private final int maxUsers;
    private final int maxBuildsPerMonth;
    private final int maxPipelines;
    private final int maxConcurrentBuilds;
    private final int pricePerMonth;

    SaasPlan(int maxUsers, int maxBuildsPerMonth, int maxPipelines,
             int maxConcurrentBuilds, int pricePerMonth) {
        this.maxUsers = maxUsers;
        this.maxBuildsPerMonth = maxBuildsPerMonth;
        this.maxPipelines = maxPipelines;
        this.maxConcurrentBuilds = maxConcurrentBuilds;
        this.pricePerMonth = pricePerMonth;
    }

    public int maxUsers() { return maxUsers; }
    public int maxBuildsPerMonth() { return maxBuildsPerMonth; }
    public int maxPipelines() { return maxPipelines; }
    public int maxConcurrentBuilds() { return maxConcurrentBuilds; }
    public int pricePerMonth() { return pricePerMonth; }
    public boolean isUnlimited() { return this == ENTERPRISE; }
}
