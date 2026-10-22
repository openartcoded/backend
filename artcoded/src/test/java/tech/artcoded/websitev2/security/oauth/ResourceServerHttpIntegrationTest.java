package tech.artcoded.websitev2.security.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs the production SecurityFilterChain against a live Spring MVC dispatcher. */
@SpringBootTest(classes = ResourceServerHttpIntegrationTest.App.class,
        properties = {"spring.main.web-application-type=servlet"})
@AutoConfigureMockMvc
class ResourceServerHttpIntegrationTest {
    @Autowired MockMvc mvc;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({ResourceServerConfig.class, ProbeController.class})
    static class App {
        @Bean JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }
    }

    @RestController
    static class ProbeController {
        @RequestMapping(value = "/api/{section}/**")
        ResponseEntity<String> api() { return ResponseEntity.ok("api"); }
        @RequestMapping(value = "/api/sms", method = RequestMethod.POST)
        ResponseEntity<String> sms() { return ResponseEntity.ok("sms"); }
        @RequestMapping(value = "/api/form-contact/submit/check", method = RequestMethod.POST)
        ResponseEntity<String> contact() { return ResponseEntity.ok("contact"); }
        @RequestMapping(value = "/api/toolbox/public/check", method = RequestMethod.GET)
        ResponseEntity<String> toolbox() { return ResponseEntity.ok("toolbox"); }
        @RequestMapping(value = "/api/fee/find-all", method = RequestMethod.POST)
        ResponseEntity<String> fee() { return ResponseEntity.ok("fee"); }
        @RequestMapping(value = "/api/script/check", method = RequestMethod.GET)
        ResponseEntity<String> script() { return ResponseEntity.ok("script"); }
        @RequestMapping(value = "/api/main-page/check", method = RequestMethod.GET)
        ResponseEntity<String> home() { return ResponseEntity.ok("home"); }
    }

    @Test void unauthenticatedApiRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/sms")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/sms")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/sms")).andExpect(status().isUnauthorized());
    }

    @Test void publicEndpointsRemainPublic() throws Exception {
        mvc.perform(get("/api/main-page/check")).andExpect(status().isOk());
        mvc.perform(get("/api/toolbox/public/check")).andExpect(status().isOk());
        mvc.perform(post("/api/form-contact/submit/check")).andExpect(status().isOk());
        mvc.perform(options("/api/sms")).andExpect(status().isOk());
    }

    @Test void ordinaryUserCanReadButNotWriteOrDelete() throws Exception {
        mvc.perform(get("/api/other/item").with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/sms").with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/other/item").with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test void adminCanWriteAndDelete() throws Exception {
        mvc.perform(post("/api/sms").with(jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/other/item").with(jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/script/check").with(jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test void accountantHasExplicitReadAccessButNotAdminScriptAccess() throws Exception {
        mvc.perform(post("/api/fee/find-all").with(jwt().authorities(() -> "ROLE_REGULATOR_OR_ACCOUNTANT")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/fee/find-all").with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/script/check").with(jwt().authorities(() -> "ROLE_REGULATOR_OR_ACCOUNTANT")))
                .andExpect(status().isForbidden());
    }

    @Test void noRoleClaimDoesNotGrantProtectedEndpoints() throws Exception {
        mvc.perform(get("/api/other/item").with(jwt())).andExpect(status().isForbidden());
    }
}
