package com.constructflow.support;

import com.constructflow.TestcontainersConfiguration;
import com.constructflow.security.CurrentUser;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base for integration tests: the full Spring context, real HTTP handling through MockMvc and a real
 * PostgreSQL 18 in a Docker container (Testcontainers). All subclasses share one cached context, and
 * therefore one database container, so the suite starts PostgreSQL only once.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, IntegrationTestConfig.class, TestFixtures.class})
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected TestFixtures fixtures;

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    protected ResultActions getAs(CurrentUser user, String url) throws Exception {
        return mvc.perform(authed(get(url), user));
    }

    protected ResultActions postAs(CurrentUser user, String url, Object body) throws Exception {
        return mvc.perform(authed(post(url), user).contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    protected ResultActions patchAs(CurrentUser user, String url, Object body) throws Exception {
        return mvc.perform(authed(patch(url), user).contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    protected <B extends AbstractMockHttpServletRequestBuilder<B>> B authed(B request, CurrentUser user) {
        return request.header("Authorization", "Bearer " + fixtures.token(user));
    }

    protected String toJson(Object body) {
        return body instanceof String s ? s : json.writeValueAsString(body);
    }

    protected static <T> T read(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }
}
