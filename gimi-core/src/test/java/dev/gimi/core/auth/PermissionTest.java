package dev.gimi.core.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionTest {

    @Test
    void adminShouldHaveAllPermissions() {
        Set<Permission> permissions = Permission.forRole(Role.ADMIN);
        assertThat(permissions).containsExactlyInAnyOrder(Permission.values());
    }

    @Test
    void operatorShouldHaveExpectedPermissions() {
        Set<Permission> permissions = Permission.forRole(Role.OPERATOR);

        assertThat(permissions).containsExactlyInAnyOrder(
                Permission.PIPELINE_READ, Permission.PIPELINE_WRITE, Permission.PIPELINE_EXECUTE,
                Permission.RUN_VIEW, Permission.RUN_CANCEL, Permission.APPROVAL_MANAGE,
                Permission.WORKER_MANAGE, Permission.ARTIFACT_READ, Permission.ARTIFACT_WRITE
        );
    }

    @Test
    void operatorShouldNotHaveAdminPermissions() {
        Set<Permission> permissions = Permission.forRole(Role.OPERATOR);

        assertThat(permissions).doesNotContain(
                Permission.PIPELINE_DELETE,
                Permission.USER_MANAGE,
                Permission.SYSTEM_ADMIN
        );
    }

    @Test
    void developerShouldHaveExpectedPermissions() {
        Set<Permission> permissions = Permission.forRole(Role.DEVELOPER);

        assertThat(permissions).containsExactlyInAnyOrder(
                Permission.PIPELINE_READ, Permission.PIPELINE_EXECUTE,
                Permission.RUN_VIEW, Permission.ARTIFACT_READ, Permission.ARTIFACT_WRITE
        );
    }

    @Test
    void developerShouldNotHaveWriteOrManagePermissions() {
        Set<Permission> permissions = Permission.forRole(Role.DEVELOPER);

        assertThat(permissions).doesNotContain(
                Permission.PIPELINE_WRITE,
                Permission.PIPELINE_DELETE,
                Permission.RUN_CANCEL,
                Permission.APPROVAL_MANAGE,
                Permission.WORKER_MANAGE,
                Permission.USER_MANAGE,
                Permission.SYSTEM_ADMIN
        );
    }

    @Test
    void viewerShouldHaveReadOnlyPermissions() {
        Set<Permission> permissions = Permission.forRole(Role.VIEWER);

        assertThat(permissions).containsExactlyInAnyOrder(
                Permission.PIPELINE_READ, Permission.RUN_VIEW, Permission.ARTIFACT_READ
        );
    }

    @Test
    void viewerShouldNotHaveAnyWritePermissions() {
        Set<Permission> permissions = Permission.forRole(Role.VIEWER);

        assertThat(permissions).doesNotContain(
                Permission.PIPELINE_WRITE,
                Permission.PIPELINE_EXECUTE,
                Permission.PIPELINE_DELETE,
                Permission.RUN_CANCEL,
                Permission.APPROVAL_MANAGE,
                Permission.WORKER_MANAGE,
                Permission.ARTIFACT_WRITE,
                Permission.USER_MANAGE,
                Permission.SYSTEM_ADMIN
        );
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void everyRoleShouldHavePipelineRead(Role role) {
        Set<Permission> permissions = Permission.forRole(role);
        assertThat(permissions).contains(Permission.PIPELINE_READ);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void everyRoleShouldHaveRunView(Role role) {
        Set<Permission> permissions = Permission.forRole(role);
        assertThat(permissions).contains(Permission.RUN_VIEW);
    }

    @Test
    void roleHierarchyShouldBeSubsetBased() {
        Set<Permission> viewerPerms = Permission.forRole(Role.VIEWER);
        Set<Permission> developerPerms = Permission.forRole(Role.DEVELOPER);
        Set<Permission> operatorPerms = Permission.forRole(Role.OPERATOR);
        Set<Permission> adminPerms = Permission.forRole(Role.ADMIN);

        assertThat(developerPerms).containsAll(viewerPerms);
        assertThat(operatorPerms).containsAll(viewerPerms);
        assertThat(adminPerms).containsAll(operatorPerms);
        assertThat(adminPerms).containsAll(developerPerms);
    }
}
