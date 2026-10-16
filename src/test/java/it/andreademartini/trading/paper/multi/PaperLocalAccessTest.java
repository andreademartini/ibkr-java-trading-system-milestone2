package it.andreademartini.trading.paper.multi;

import it.andreademartini.trading.paper.patternv1.*;
import it.andreademartini.trading.backtest.patternstable.PatternStableStore;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PaperLocalAccessTest {
    PatternPaperAdmin admin;
    PaperDeskService service;
    MockMvc mvc;

    @BeforeEach void setup(){
        admin=new PatternPaperAdmin("");
        service=mock(PaperDeskService.class);
        mvc=MockMvcBuilders.standaloneSetup(new PaperDeskController(service,admin,mock(PaperDeskExcelExporter.class)))
            .addFilters(new PatternPaperSecurityFilter(admin)).build();
    }

    @Test void accessDiscoveryCreatesNoSessionAndStatusStillRequiresOne() throws Exception {
        var result=mvc.perform(get("/api/paper-trading/access")).andExpect(status().isOk()).andReturn();
        var data=PatternStableStore.JSON.readTree(result.getResponse().getContentAsString());
        assertTrue(data.path("localAccess").asBoolean());assertFalse(data.path("adminConfigured").asBoolean());
        assertFalse(data.path("authenticated").asBoolean());assertNull(result.getRequest().getSession(false));
        mvc.perform(get("/api/paper-trading/status")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void localSessionWithoutTokenAllowsReadingButCommandsStillNeedCsrf() throws Exception {
        var result=mvc.perform(post("/api/paper-trading/local-session").header("X-Paper-Local-Session","1").header("Origin","http://localhost"))
            .andExpect(status().isOk()).andReturn();
        String csrf=PatternStableStore.JSON.readTree(result.getResponse().getContentAsString()).path("csrf").asText();
        assertFalse(csrf.isBlank());assertFalse(admin.configured());
        var session=(MockHttpSession)result.getRequest().getSession(false);
        mvc.perform(get("/api/paper-trading/status").session(session)).andExpect(status().isOk());
        String body="{\"commandId\":\"local-command\"}";
        mvc.perform(post("/api/paper-trading/strategies/PATTERN_V2/stop").session(session).contentType("application/json").content(body))
            .andExpect(status().isForbidden());
        verify(service,never()).command(any(),any(),any(),any(),any());
        mvc.perform(post("/api/paper-trading/strategies/PATTERN_V2/stop").session(session).header("X-Paper-CSRF",csrf).contentType("application/json").content(body))
            .andExpect(status().isOk());
        verify(service).command("PATTERN_V2","stop","local-command",null,null);
        var again=mvc.perform(post("/api/paper-trading/local-session").session(session).header("X-Paper-Local-Session","1"))
            .andExpect(status().isOk()).andReturn();
        assertEquals(csrf,PatternStableStore.JSON.readTree(again.getResponse().getContentAsString()).path("csrf").asText());
        assertSame(session,again.getRequest().getSession(false));
    }

    @Test void remoteClientsAndRebindingHostsCannotBootstrapLocalAccess() throws Exception {
        mvc.perform(post("/api/paper-trading/local-session").secure(true).header("X-Paper-Local-Session","1")
            .with(r->{r.setRemoteAddr("192.168.1.15");return r;})).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/paper-trading/local-session").header("X-Paper-Local-Session","1")
            .with(r->{r.setServerName("attacker.example");return r;})).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/paper-trading/local-session").header("X-Paper-Local-Session","1").header("Forwarded","for=127.0.0.1"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/paper-trading/local-session").header("X-Paper-Local-Session","1").header("X-Forwarded-For","127.0.0.1"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void otherOriginsAndMissingBootstrapHeaderAreRejected() throws Exception {
        for(String origin:new String[]{"http://attacker.example","http://localhost:9999","https://localhost","null"}){
            mvc.perform(post("/api/paper-trading/local-session").header("X-Paper-Local-Session","1").header("Origin",origin))
                .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/paper-trading/local-session")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void localSessionCannotBeReusedRemotelyAndIpv6IsSupported(){
        var local=new MockHttpServletRequest();local.setRemoteAddr("::1");local.setServerName("[::1]");
        local.addHeader("X-Paper-Local-Session","1");local.addHeader("Origin","http://[::1]");
        String csrf=admin.localSession(local,new MockHttpServletResponse());
        assertTrue(admin.authorized(local));assertFalse(admin.validCsrf(local));local.addHeader("X-Paper-CSRF",csrf);assertTrue(admin.validCsrf(local));
        var remote=new MockHttpServletRequest();remote.setRemoteAddr("192.168.1.15");remote.setSession(local.getSession(false));
        assertFalse(admin.authorized(remote));
    }

    @Test void remotePasswordLoginIsUnchanged(){
        var passwordAdmin=new PatternPaperAdmin("paper-admin-test-token-32-characters-minimum");
        var remote=new MockHttpServletRequest();remote.setRemoteAddr("192.168.1.15");remote.setSecure(true);
        assertThrows(SecurityException.class,()->passwordAdmin.login("wrong",remote,new MockHttpServletResponse()));
        assertNotNull(passwordAdmin.login("paper-admin-test-token-32-characters-minimum",remote,new MockHttpServletResponse()));
        assertTrue(passwordAdmin.authorized(remote));
    }
}
