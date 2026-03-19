package dev.gimi.server.controller;

import dev.gimi.core.model.tenant.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, Organization> organizations = new ConcurrentHashMap<>();
    private final Map<String, Project> projects = new ConcurrentHashMap<>();

    // --- Accounts ---
    @PostMapping("/accounts")
    public ResponseEntity<Account> createAccount(@RequestBody Map<String, Object> body) {
        String id = UUID.randomUUID().toString();
        Account account = new Account(id, (String) body.get("name"),
            (String) body.get("description"),
            AccountPlan.valueOf(((String) body.getOrDefault("plan", "FREE")).toUpperCase()),
            Map.of(), true, Instant.now());
        accounts.put(id, account);
        return ResponseEntity.ok(account);
    }

    @GetMapping("/accounts")
    public ResponseEntity<Collection<Account>> listAccounts() {
        return ResponseEntity.ok(accounts.values());
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<Account> getAccount(@PathVariable String id) {
        Account account = accounts.get(id);
        return account != null ? ResponseEntity.ok(account) : ResponseEntity.notFound().build();
    }

    // --- Organizations ---
    @PostMapping("/accounts/{accountId}/organizations")
    public ResponseEntity<Organization> createOrganization(
            @PathVariable String accountId, @RequestBody Map<String, Object> body) {
        if (!accounts.containsKey(accountId)) return ResponseEntity.notFound().build();
        String id = UUID.randomUUID().toString();
        Organization org = new Organization(id, accountId, (String) body.get("name"),
            (String) body.get("description"), Map.of(), true, Instant.now());
        organizations.put(id, org);
        return ResponseEntity.ok(org);
    }

    @GetMapping("/accounts/{accountId}/organizations")
    public ResponseEntity<List<Organization>> listOrganizations(@PathVariable String accountId) {
        List<Organization> orgs = organizations.values().stream()
            .filter(o -> o.accountId().equals(accountId)).toList();
        return ResponseEntity.ok(orgs);
    }

    // --- Projects ---
    @PostMapping("/organizations/{orgId}/projects")
    public ResponseEntity<Project> createProject(
            @PathVariable String orgId, @RequestBody Map<String, Object> body) {
        Organization org = organizations.get(orgId);
        if (org == null) return ResponseEntity.notFound().build();
        String id = UUID.randomUUID().toString();
        Project project = new Project(id, orgId, org.accountId(), (String) body.get("name"),
            (String) body.get("description"), Map.of(), true, Instant.now());
        projects.put(id, project);
        return ResponseEntity.ok(project);
    }

    @GetMapping("/organizations/{orgId}/projects")
    public ResponseEntity<List<Project>> listProjects(@PathVariable String orgId) {
        List<Project> projs = projects.values().stream()
            .filter(p -> p.organizationId().equals(orgId)).toList();
        return ResponseEntity.ok(projs);
    }

    @GetMapping("/projects/{id}")
    public ResponseEntity<Project> getProject(@PathVariable String id) {
        Project project = projects.get(id);
        return project != null ? ResponseEntity.ok(project) : ResponseEntity.notFound().build();
    }
}
