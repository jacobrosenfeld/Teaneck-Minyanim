package com.tbdev.teaneckminyanim.security;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the full application on H2 with Spring Session JDBC and checks that an admin login is
 * stored in SPRING_SESSION (so it survives a redeploy), that disabling the account kills the
 * persisted session, that logout deletes the row, and that the passkey flows' session state
 * serializes into the JDBC session.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jdbc-sessions;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "logging.level.org.springframework.web=INFO",
        "auth.password-login.enabled=true",
        "superadmin.email="
})
@AutoConfigureMockMvc
class JdbcSessionPersistenceTest {

    private static final String USERNAME = "session-admin";
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TNMUserRepository users;

    @Autowired
    private FindByIndexNameSessionRepository<?> sessionRepository;

    @Autowired
    private FilterChainProxy filterChainProxy;

    @MockitoBean
    private RecaptchaService recaptchaService;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM SPRING_SESSION");
        users.findById("SESSION1").ifPresent(users::delete);
        users.save(TNMUser.builder()
                .id("SESSION1")
                .username(USERNAME)
                .email("session-admin@example.com")
                .encryptedPassword(new BCryptPasswordEncoder().encode(PASSWORD))
                .roleId(Role.ADMIN.getId())
                .organizationId(null)
                .enabled(true)
                .build());
    }

    @Test
    void sessionRepositoryIsJdbcBacked() {
        assertThat(sessionRepository).isInstanceOf(JdbcIndexedSessionRepository.class);
    }

    @Test
    void loginPersistsSessionRowAndLogoutDeletesIt() throws Exception {
        Cookie session = login();

        assertThat(sessionRowsFor(USERNAME)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES a JOIN SPRING_SESSION s "
                        + "ON a.SESSION_PRIMARY_ID = s.PRIMARY_ID WHERE s.PRINCIPAL_NAME = ? "
                        + "AND a.ATTRIBUTE_NAME = 'SPRING_SECURITY_CONTEXT'",
                Integer.class, USERNAME)).isEqualTo(1);

        // The persisted session authenticates a later request on its own (as after a redeploy).
        mvc.perform(get("/admin/settings").cookie(session))
                .andExpect(status().isOk());

        mvc.perform(post("/admin/logout").cookie(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?logout=true"));

        assertThat(sessionRowsFor(USERNAME)).isZero();
    }

    @Test
    void loginRotatesPreLoginSessionId() throws Exception {
        // An anonymous request to a protected page creates a session holding the saved request.
        MvcResult anonymous = mvc.perform(get("/admin/settings"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Cookie preLogin = anonymous.getResponse().getCookie("SESSION");
        assertThat(preLogin).isNotNull();

        MvcResult result = mvc.perform(post("/j_spring_security_check")
                        .cookie(preLogin)
                        .param("username", USERNAME)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Cookie postLogin = result.getResponse().getCookie("SESSION");

        assertThat(postLogin).isNotNull();
        assertThat(postLogin.getValue()).isNotEqualTo(preLogin.getValue());
        mvc.perform(get("/admin/settings").cookie(preLogin))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    @Test
    void passkeyLoginFilterRotatesSessionId() {
        WebAuthnAuthenticationFilter filter = filterChainProxy.getFilterChains().stream()
                .flatMap(chain -> chain.getFilters().stream())
                .filter(WebAuthnAuthenticationFilter.class::isInstance)
                .map(WebAuthnAuthenticationFilter.class::cast)
                .findFirst()
                .orElseThrow();

        Object strategy = ReflectionTestUtils.getField(filter, AbstractAuthenticationProcessingFilter.class,
                "sessionStrategy");
        assertThat(strategy).isNotInstanceOf(NullAuthenticatedSessionStrategy.class);
    }

    @Test
    void passkeyOptionsAreStoredInJdbcSession() throws Exception {
        // Authentication options (anonymous) are stored in the session for the follow-up POST.
        MvcResult authOptions = mvc.perform(post("/webauthn/authenticate/options").with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(authOptions.getResponse().getCookie("SESSION")).isNotNull();

        // Registration options must serialize too, or the session commit fails after the response.
        Cookie session = login();
        mvc.perform(post("/webauthn/register/options").cookie(session).with(csrf()))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES WHERE ATTRIBUTE_NAME = ?",
                Integer.class, SerializablePublicKeyCredentialCreationOptionsRepository.ATTR_NAME)).isEqualTo(1);
    }

    @Test
    void sessionCookieIsHttpOnlyAndLax() throws Exception {
        MvcResult result = mvc.perform(post("/j_spring_security_check")
                        .param("username", USERNAME)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andReturn();

        String setCookie = String.join("\n", result.getResponse().getHeaders("Set-Cookie"));
        assertThat(setCookie).contains("SESSION=").contains("HttpOnly").contains("SameSite=Lax");
    }

    @Test
    void disablingAccountInvalidatesPersistedSession() throws Exception {
        Cookie session = login();
        assertThat(sessionRowsFor(USERNAME)).isEqualTo(1);

        TNMUser user = users.findById("SESSION1").orElseThrow();
        user.setEnabled(false);
        users.save(user);

        mvc.perform(get("/admin/settings").cookie(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        assertThat(sessionRowsFor(USERNAME)).isZero();
    }

    private Cookie login() throws Exception {
        MvcResult result = mvc.perform(post("/j_spring_security_check")
                        .param("username", USERNAME)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).as("Spring Session cookie").isNotNull();
        return cookie;
    }

    private int sessionRowsFor(String principal) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", Integer.class, principal);
        return count == null ? 0 : count;
    }
}
