package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.Location;
import com.tbdev.teaneckminyanim.model.Minyan;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.CalendarEventRepository;
import com.tbdev.teaneckminyanim.repo.LocationRepository;
import com.tbdev.teaneckminyanim.repo.MinyanRepository;
import com.tbdev.teaneckminyanim.repo.OrganizationRepository;
import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The panel's enable, disable, minyan update and shul update forms, through the real stack with CSRF. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:minyan-panel-forms;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "logging.level.org.springframework.web=INFO",
        "superadmin.email="
})
@AutoConfigureMockMvc
class MinyanPanelFormsFullStackTest {
    @Autowired private MockMvc mvc;
    @Autowired private TNMUserRepository users;
    @Autowired private OrganizationRepository organizations;
    @Autowired private LocationRepository locations;
    @Autowired private MinyanRepository minyanim;
    @Autowired private CalendarEventRepository calendarEvents;

    @MockitoBean private RecaptchaService recaptchaService;

    @BeforeEach
    void seed() {
        calendarEvents.deleteAll();
        minyanim.deleteAll();
        users.deleteAll();
        locations.deleteAll();
        organizations.deleteAll();
        organizations.save(shul("PA", "Panel A", "panel-a"));
        organizations.save(shul("PB", "Panel B", "panel-b"));
        locations.save(new Location("PLA", "Main", "PA"));
        locations.save(new Location("PLB", "Other", "PB"));
        minyanim.save(new Minyan("PM1", MinyanType.SHACHARIS, "PLA", "PA", true,
                "T7:0:0:0", "T7:0:0:0", "T7:0:0:0", "T7:0:0:0", "T7:0:0:0", "T7:0:0:0", "T8:0:0:0",
                "NM", "NM", "NM", "NM", null, Nusach.ASHKENAZ, "#275ed8", null));
        users.save(TNMUser.builder().id("PMGR").username("panel-mgr").email("p@example.com")
                .encryptedPassword("x").organizationId("PA").roleId(Role.ADMIN.getId()).enabled(true).build());
    }

    private static RequestPostProcessor mgr() {
        return user("panel-mgr").authorities(new SimpleGrantedAuthority(Role.ADMIN.getName()));
    }

    @Test
    void disableAndEnableFormsRedirectAndSave() throws Exception {
        mvc.perform(post("/admin/minyanim/PM1/disable").with(mgr()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/PA/minyanim"));
        assertThat(minyanim.findById("PM1").orElseThrow().isEnabled()).isFalse();

        mvc.perform(post("/admin/minyanim/PM1/enable").with(mgr()).with(csrf()).param("rd", "/admin/organization?id=PA"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/organization?id=PA"));
        assertThat(minyanim.findById("PM1").orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void formsRequireCsrf() throws Exception {
        mvc.perform(post("/admin/minyanim/PM1/disable").with(mgr())).andExpect(status().isForbidden());
        assertThat(minyanim.findById("PM1").orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void updateFormSavesSchedulePreservesEnabledAndRedirects() throws Exception {
        MockHttpServletRequestBuilder form = post("/admin/PA/minyanim/PM1/update").with(mgr()).with(csrf())
                .param("type", "MINCHA").param("location", "PLA").param("nusach", "SEFARD")
                .param("notes", "Updated").param("whatsapp", "");
        for (String day : new String[]{"sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "shabbos",
                "rc", "yt", "chanuka", "rcc"}) {
            if (!day.equals("monday") && !day.equals("sunday")) {
                form.param(day + "-time-type", "nm");
            }
        }
        form.param("monday-time-type", "dynamic").param("monday-zman", "NETZ").param("monday-zman-offset", "-5")
                .param("sunday-time-type", "fixed").param("sunday-fixed-time", "06:45");

        var response = mvc.perform(form).andReturn();
        assertThat(response.getModelAndView() == null ? "" : String.valueOf(response.getModelAndView().getModel().get("errormessage")))
                .as("error message").isIn("", "null");
        mvc.perform(form)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/minyanim/PM1/view?success=updated"));

        Minyan saved = minyanim.findById("PM1").orElseThrow();
        assertThat(saved.getType()).isEqualTo(MinyanType.MINCHA);
        assertThat(saved.getNusach()).isEqualTo(Nusach.SEFARD);
        assertThat(saved.getNotes()).isEqualTo("Updated");
        assertThat(saved.getStartTime1()).isEqualTo("T6:45:0:0");
        assertThat(saved.getStartTime2()).isEqualTo("RNETZ:-5");
        assertThat(saved.getStartTime3()).isEqualTo("NM");
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void updateFormForAnotherShulIsForbidden() throws Exception {
        MockHttpServletRequestBuilder form = post("/admin/PB/minyanim/PM1/update").with(mgr()).with(csrf())
                .param("type", "MINCHA").param("location", "PLB").param("nusach", "SEFARD");
        for (String day : new String[]{"sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "shabbos",
                "rc", "yt", "chanuka", "rcc"}) {
            form.param(day + "-time-type", "nm");
        }
        mvc.perform(form).andExpect(status().isForbidden());
    }

    @Test
    void shulFormSavesAndRedirects() throws Exception {
        mvc.perform(post("/admin/update-organization").with(mgr()).with(csrf())
                        .param("id", "PA").param("name", "Panel A Renamed").param("nusach", "ASHKENAZ")
                        .param("orgColor", "#112233").param("url-slug", "panel-a-new").param("site-url", "panel.org"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/organization?id=PA"));
        Organization saved = organizations.findById("PA").orElseThrow();
        assertThat(saved.getName()).isEqualTo("Panel A Renamed");
        assertThat(saved.getUrlSlug()).isEqualTo("panel-a-new");
        assertThat(saved.getOrgColor()).isEqualTo("#112233");
        assertThat(saved.getWebsiteURIStr()).isEqualTo("panel.org");
    }

    private static Organization shul(String id, String name, String slug) {
        Organization o = new Organization();
        o.setId(id);
        o.setName(name);
        o.setUrlSlug(slug);
        o.setEnabled(true);
        o.setOrgColor("#275ed8");
        o.setNusach(Nusach.ASHKENAZ);
        return o;
    }
}
