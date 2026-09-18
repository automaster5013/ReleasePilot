package kr.releasepilot.controlplane.analysis;
import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/v1/releases/{releaseId}/analyses")public class AnalysisController{private final AnalysisQueryService service;public AnalysisController(AnalysisQueryService service){this.service=service;}@GetMapping public List<View> list(@PathVariable UUID releaseId,Authentication authentication){return service.byRelease(releaseId,authentication);}}
