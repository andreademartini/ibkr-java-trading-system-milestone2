package it.andreademartini.trading.paper.patternv1;

import static it.andreademartini.trading.paper.patternv1.PatternPaperModel.*;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;

@RestController
@RequestMapping("/api/pattern-v1/paper")
public class PatternPaperController {
    private final PatternPaperService service;
    private final PatternPaperStore store;
    private final PatternPaperAdmin admin;
    private final PatternPaperExcelExporter excel;
    public PatternPaperController(PatternPaperService service,PatternPaperStore store,PatternPaperAdmin admin,PatternPaperExcelExporter excel){this.service=service;this.store=store;this.admin=admin;this.excel=excel;}
    public record Login(String token) {}
    public record Command(String commandId,String confirmation) {}
    public record Settings(String commandId,Limits limits) {}
    @GetMapping("/access") public Map<String,Object> access(HttpServletRequest request){return Map.of("adminConfigured",admin.configured(),"authenticated",admin.authorized(request));}
    @PostMapping("/login") public Map<String,Object> login(@RequestBody Login login,HttpServletRequest request,HttpServletResponse response){return Map.of("csrf",admin.login(login.token(),request,response));}
    @PostMapping("/logout") public void logout(HttpServletRequest request){var session=request.getSession(false);if(session!=null)session.invalidate();}
    @GetMapping("/status") public Dashboard status(){return service.dashboard(admin.configured());}
    @PostMapping("/settings") public Dashboard settings(@RequestBody Settings request){service.configure(request.limits(),request.commandId());return status();}
    @PostMapping("/connect") public Dashboard connect(@RequestBody Command request){service.connect(request.commandId());return status();}
    @PostMapping("/refresh") public Dashboard refresh(@RequestBody Command request){service.refresh();return status();}
    @PostMapping("/start") public Dashboard start(@RequestBody Command request){service.start(request.commandId());return status();}
    @PostMapping("/shadow") public Dashboard shadow(@RequestBody Command request){service.shadow(request.commandId());return status();}
    @PostMapping("/stop") public Dashboard stop(@RequestBody Command request){service.stop(request.commandId());return status();}
    @PostMapping("/kill") public Dashboard kill(@RequestBody Command request){service.kill(request.commandId(),request.confirmation());return status();}
    @PostMapping("/reset") public Dashboard reset(@RequestBody Command request){service.reset(request.commandId(),request.confirmation());return status();}
    @GetMapping("/export/excel") public ResponseEntity<byte[]> export(){var dashboard=status();return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=PATTERN_V1_PAPER.xlsx").body(excel.export(dashboard,store.allEvents()));}
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class}) public ResponseEntity<Map<String,String>> invalid(RuntimeException error){return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",error.getMessage()==null?"Operazione PAPER bloccata":error.getMessage()));}
    @ExceptionHandler(SecurityException.class) public ResponseEntity<Map<String,String>> denied(SecurityException error){return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message",error.getMessage()));}
}
