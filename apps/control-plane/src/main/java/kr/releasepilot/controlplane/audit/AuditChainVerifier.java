package kr.releasepilot.controlplane.audit;

import org.springframework.stereotype.Service;

@Service
public class AuditChainVerifier {
    private final AuditEventRepository events;
    private final AuditChainHeadRepository heads;
    public AuditChainVerifier(AuditEventRepository events,AuditChainHeadRepository heads){this.events=events;this.heads=heads;}
    @org.springframework.transaction.annotation.Transactional
    public Result verify(){
        // Serialize with AuditTrail writers so the events and head describe one committed chain.
        var head=heads.lockById(1);
        long expected=1;
        String previous=AuditTrail.GENESIS_HASH;
        for(var event:events.findChainCandidates()){
            if(event.getChainSequence()==null||event.getChainSequence()!=expected||!previous.equals(event.getPreviousHash()))return new Result(false,expected-1,event.getId(),previous);
            try {
                if(!AuditTrail.hash(expected,previous,event).equals(event.getEventHash()))return new Result(false,expected-1,event.getId(),previous);
            } catch (IllegalArgumentException exception) {
                return new Result(false,expected-1,event.getId(),previous);
            }
            previous=event.getEventHash();expected++;
        }
        if(head==null||head.lastSequence()!=expected-1||!previous.equals(head.lastHash()))return new Result(false,expected-1,null,previous);
        return new Result(true,expected-1,null,previous);
    }
    public record Result(boolean valid,long verifiedEvents,java.util.UUID failedEventId,String headHash){}
}
