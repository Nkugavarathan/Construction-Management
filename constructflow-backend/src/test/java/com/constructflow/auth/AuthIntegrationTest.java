package com.constructflow.auth;

import com.constructflow.security.CurrentUser;
import com.constructflow.security.Role;
import com.constructflow.support.IntegrationTest;
import com.constructflow.support.TestFixtures;
import com.constructflow.support.TestFixtures.Org;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AUTH-1..4, ORG-1: login, refresh rotation, reuse detection, lockout, logout and membership checks. */
class AuthIntegrationTest extends IntegrationTest {

    @Test
    void registerCreatesOrganizationAdminAndSession() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@test.dev";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of(
                        "organizationName", "New Co", "fullName", "Owner", "email", email, "password", TestFixtures.PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.me.role").value("ADMIN"))
                .andExpect(header().string("Set-Cookie", Matchers.allOf(
                        Matchers.containsString("cf_refresh="),
                        Matchers.containsString("HttpOnly"),
                        Matchers.containsString("SameSite=Strict"),
                        Matchers.containsString("Path=/api/v1/auth"))));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of(
                        "organizationName", "Other", "fullName", "X", "email", email.toUpperCase(), "password", TestFixtures.PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void loginErrorsDoNotRevealWhetherTheEmailExists() throws Exception {
        Org org = fixtures.organization();
        String knownEmail = org.user(Role.VIEWER).email();
        login(knownEmail, "wrong-password-123").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login("nobody-" + UUID.randomUUID() + "@test.dev", "wrong-password-123").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void fiveFailuresLockTheAccountEvenForTheCorrectPassword() throws Exception {
        Org org = fixtures.organization();
        String email = org.user(Role.STOREKEEPER).email();
        for (int i = 0; i < 5; i++) {
            login(email, "wrong-password-123").andExpect(status().isUnauthorized());
        }
        login(email, TestFixtures.PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        clock.advance(Duration.ofMinutes(16));
        login(email, TestFixtures.PASSWORD).andExpect(status().isOk());
    }

    @Test
    void refreshRotatesAndReuseAfterGraceRevokesTheWholeSession() throws Exception {
        Org org = fixtures.organization();
        String first = refreshCookie(login(org.user(Role.PROJECT_MANAGER).email(), TestFixtures.PASSWORD)
                .andExpect(status().isOk()).andReturn());

        String second = refreshCookie(refresh(first).andExpect(status().isOk()).andReturn());
        assertThat(second).isNotEqualTo(first);

        // Someone replays the old token well after it was rotated: treated as theft.
        clock.advance(Duration.ofMinutes(1));
        refresh(first).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        // The legitimate holder's newer token was revoked with the family.
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        Org org = fixtures.organization();
        String token = refreshCookie(login(org.user(Role.VIEWER).email(), TestFixtures.PASSWORD).andReturn());
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("cf_refresh", token)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", Matchers.containsString("Max-Age=0")));
        refresh(token).andExpect(status().isUnauthorized());
    }

    /**
     * Two layers stop a cross-site refresh: the CORS filter rejects disallowed origins before the
     * controller runs, and OriginValidator would reject it again if CORS were ever loosened.
     */
    @Test
    void refreshFromAForeignOriginIsRejected() throws Exception {
        Org org = fixtures.organization();
        String token = refreshCookie(login(org.user(Role.VIEWER).email(), TestFixtures.PASSWORD).andReturn());
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("cf_refresh", token)).header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
        // Rejected before rotation, so the legitimate session is untouched.
        refresh(token).andExpect(status().isOk());
    }

    @Test
    void disabledMemberIsLockedOutImmediatelyDespiteValidToken() throws Exception {
        Org org = fixtures.organization();
        CurrentUser engineer = org.user(Role.SITE_ENGINEER);
        String engineerToken = fixtures.token(engineer);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + engineerToken)).andExpect(status().isOk());

        String membershipId = membershipId(org, engineer);
        patchAs(org.user(Role.ADMIN), "/api/v1/members/" + membershipId, Map.of("status", "DISABLED", "version", 0))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lastActiveAdminCannotBeDemoted() throws Exception {
        Org org = fixtures.organization();
        CurrentUser admin = org.user(Role.ADMIN);
        patchAs(admin, "/api/v1/members/" + membershipId(org, admin), Map.of("role", "VIEWER", "version", 0))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));
    }

    @Test
    void nonAdminsCannotManageMembers() throws Exception {
        Org org = fixtures.organization();
        postAs(org.user(Role.PROJECT_MANAGER), "/api/v1/members", Map.of("fullName", "X", "email", "x@test.dev",
                "role", "ADMIN", "temporaryPassword", "temporary-pass-1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void newMembersMustChangeTheirPasswordBeforeDoingAnything() throws Exception {
        Org org = fixtures.organization();
        String email = "new-" + UUID.randomUUID() + "@test.dev";
        postAs(org.user(Role.ADMIN), "/api/v1/members", Map.of("fullName", "New Person", "email", email,
                "role", "PROJECT_MANAGER", "temporaryPassword", "temporary-pass-1")).andExpect(status().isCreated());
        String token = read(login(email, "temporary-pass-1").andExpect(status().isOk())
                .andExpect(jsonPath("$.me.mustChangePassword").value(true)), "$.accessToken");
        mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("currentPassword", "temporary-pass-1", "newPassword", "a-brand-new-pass-9"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.mustChangePassword").value(false))
                .andExpect(jsonPath("$.me.permissions").isNotEmpty());
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", email, "password", password))));
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("cf_refresh", token)));
    }

    private static String refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("cf_refresh");
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }

    private String membershipId(Org org, CurrentUser member) throws Exception {
        return read(getAs(org.user(Role.ADMIN), "/api/v1/members?q=" + member.email()), "$.content[0].id");
    }
}
