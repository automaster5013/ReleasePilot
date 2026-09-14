package kr.releasepilot.controlplane.audit;

import org.springframework.stereotype.Service;

@Service
public class AuditChainVerifier {
    private final AuditEventRepository events;
    public AuditChainVerifier(AuditEventRepository events){this.events=events;}
    public Result verify(){
        long expected=1;
        String previous=AuditTrail.GENESIS_HASH;
        for(var event:events.findByEventHashIsNotNullOrderByChainSequenceAsc()){
            if(event.getChainSequence()==null||event.getChainSequence()!=expected||!previous.equals(event.getPreviousHash())||!AuditTrail.hash(expected,previous,event).equals(event.getEventHash()))return new Result(false,expected,event.getId(),previous);
            previous=event.getEventHash();expected++;
        }
        return new Result(true,expected-1,null,previous);
    }
    public record Result(boolean valid,long verifiedEvents,java.util.UUID failedEventId,String headHash){}
}
