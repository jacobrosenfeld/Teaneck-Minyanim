package com.tbdev.teaneckminyanim.security;

import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With passkeys turned off the app still starts, the API chain works and no WebAuthn filters are installed. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:passkeys-disabled;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "logging.level.org.springframework.web=INFO",
        "auth.passkeys.enabled=false",
        "superadmin.email="
})
@AutoConfigureMockMvc
class PasskeysDisabledContextTest {
    @Autowired private MockMvc mvc;
    @Autowired private FilterChainProxy filterChainProxy;
    @Autowired private WebAuthnRelyingPartyOperations relyingParty;

    @MockitoBean private RecaptchaService recaptchaService;

    @Test
    void startsWithoutWebAuthnFiltersAndServesTheApi() throws Exception {
        assertThat(relyingParty).isNotNull();
        assertThat(filterChainProxy.getFilterChains()).allSatisfy(chain ->
                assertThat(chain.getFilters()).noneMatch(WebAuthnAuthenticationFilter.class::isInstance));

        mvc.perform(get("/api/v1/organizations")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/organizations").header("Authorization", "Bearer nope"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }
}
