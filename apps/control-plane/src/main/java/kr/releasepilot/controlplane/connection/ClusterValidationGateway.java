package kr.releasepilot.controlplane.connection;

import java.util.List;

public interface ClusterValidationGateway {
    Result validate(ClusterConnection connection);

    record NamespaceAccess(String namespace,boolean allowed,List<String> missingVerbs) {}
    record Result(ConnectionStatus status,String serverVersion,String failureCode,List<NamespaceAccess> namespaces) {
        public Result {
            if(status!=ConnectionStatus.ACTIVE&&status!=ConnectionStatus.INVALID)
                throw new IllegalArgumentException("Validation status must be ACTIVE or INVALID");
            serverVersion=serverVersion==null?"":serverVersion;
            failureCode=failureCode==null?"":failureCode;
            namespaces=List.copyOf(namespaces);
        }
    }
}
