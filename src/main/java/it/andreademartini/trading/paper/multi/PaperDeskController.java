package it.andreademartini.trading.paper.multi;

import it.andreademartini.trading.paper.patternv1.*;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/paper-trading")
public class PaperDeskController {
    private final PaperDeskService service;private final PatternPaperAdmin admin;private final PaperDeskExcelExporter excel;
    public PaperDeskController(PaperDeskService service,PatternPaperAdmin admin,PaperDeskExcelExporter excel){this.service=service;this.admin=admin;this.excel=excel;}
    public record Login(String token) {}
    public record Command(String commandId,String confirmation,PatternPaperModel.Limits limits) {}
    @GetMapping("/access") public Object access(HttpServletRequest request){return Map.of("authenticated",admin.authorized(request),"adminConfigured",admin.configured(),"localAccess",admin.localAccess(request));}
    @PostMapping("/local-session") public Object localSession(HttpServletRequest request,HttpServletResponse response){return Map.of("csrf",admin.localSession(request,response));}
    @PostMapping("/login") public Object login(@RequestBody Login login,HttpServletRequest request,HttpServletResponse response){return Map.of("csrf",admin.login(login.token(),request,response));}
    @PostMapping("/logout") public void logout(HttpServletRequest request){var session=request.getSession(false);if(session!=null)session.invalidate();}
    @GetMapping("/status") public Object status(@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){return service.status(from,to);}
    @PostMapping("/strategies/{id}/{operation}") public Object command(@PathVariable String id,@PathVariable String operation,@RequestBody Command command){service.command(id,operation,command.commandId(),command.confirmation(),command.limits());return service.status(null,null);}
    @PostMapping("/account/{operation}") public Object account(@PathVariable String operation,@RequestBody Command command){service.accountCommand(operation,command.commandId(),command.confirmation());return service.status(null,null);}
    @GetMapping("/export/excel") public ResponseEntity<byte[]> export(@RequestParam(required=false) String strategy,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=PAPER_TRADING.xlsx").body(excel.export(service.status(from,to),service.allEvents(),strategy,from,to));}
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class}) public ResponseEntity<?> invalid(RuntimeException e){return ResponseEntity.status(409).body(Map.of("message",e.getMessage()==null?"PAPER operation blocked":e.getMessage()));}
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied(RuntimeException e){return ResponseEntity.status(401).body(Map.of("message",e.getMessage()));}
}
