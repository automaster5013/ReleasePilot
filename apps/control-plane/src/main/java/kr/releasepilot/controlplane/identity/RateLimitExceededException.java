package kr.releasepilot.controlplane.identity;

public class RateLimitExceededException extends RuntimeException {
    private final String code;
    private final long retryAfterSeconds;

    public RateLimitExceededException(String code, long retryAfterSeconds) {
        super("Too many authentication requests");
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String code() { return code; }
    public long retryAfterSeconds() { return retryAfterSeconds; }
}
