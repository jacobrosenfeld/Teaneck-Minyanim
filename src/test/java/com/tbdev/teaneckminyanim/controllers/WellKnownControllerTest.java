package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The association files through the real stack when the app.links properties are set (404s are in MobileAuthApiIntegrationTest). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:well-known;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "superadmin.email="
})
@TestPropertySource(properties = {
        "app.links.apple-team-id=ABCDE12345",
        "app.links.ios-bundle-id=com.teaneckminyanim.app",
        "app.links.android-package=com.teaneckminyanim.app",
        "app.links.android-sha256-fingerprints= 14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5 ,"
                + "aa:bb:cc:dd:ee:ff:00:11:22:33:44:55:66:77:88:99:aa:bb:cc:dd:ee:ff:00:11:22:33:44:55:66:77:88:99"
})
@AutoConfigureMockMvc
class WellKnownControllerTest {
    @Autowired private MockMvc mvc;

    @MockitoBean private RecaptchaService recaptchaService;

    @Test
    void appleAppSiteAssociationIsExactJsonAtTheExtensionlessPath() throws Exception {
        MvcResult result = mvc.perform(get("/.well-known/apple-app-site-association"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn();
        assertThat(result.getResponse().getRedirectedUrl()).isNull();
        assertThat(result.getResponse().getContentAsString()).isEqualTo(
                "{\"applinks\":{\"details\":[{\"appIDs\":[\"ABCDE12345.com.teaneckminyanim.app\"],"
                        + "\"components\":[{\"/\":\"/app/auth/*\"}]}]},"
                        + "\"webcredentials\":{\"apps\":[\"ABCDE12345.com.teaneckminyanim.app\"]}}");
    }

    @Test
    void assetLinksIsExactJson() throws Exception {
        MvcResult result = mvc.perform(get("/.well-known/assetlinks.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).isEqualTo(
                "[{\"relation\":[\"delegate_permission/common.handle_all_urls\","
                        + "\"delegate_permission/common.get_login_creds\"],"
                        + "\"target\":{\"namespace\":\"android_app\",\"package_name\":\"com.teaneckminyanim.app\","
                        + "\"sha256_cert_fingerprints\":["
                        + "\"14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5\","
                        + "\"AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99\"]}}]");
    }
}
