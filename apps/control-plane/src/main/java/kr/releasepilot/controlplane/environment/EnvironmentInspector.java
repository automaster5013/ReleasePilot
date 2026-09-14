package kr.releasepilot.controlplane.environment;
import java.util.List;
public interface EnvironmentInspector {
 List<Check> inspect(Environment environment);
 record Check(String code,ValidationOutcome outcome,String message,String detailsJson){}
}
