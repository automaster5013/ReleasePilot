package kr.releasepilot.controlplane.connection;

public interface PrometheusConnectionValidationGateway {
    Result validate(PrometheusConnection connection);

    record Result(ConnectionStatus status,String failureCode) {
        public Result {
            if(status!=ConnectionStatus.ACTIVE&&status!=ConnectionStatus.INVALID)
                throw new IllegalArgumentException("Validation status must be ACTIVE or INVALID");
            failureCode=failureCode==null?"":failureCode;
        }
    }
}
