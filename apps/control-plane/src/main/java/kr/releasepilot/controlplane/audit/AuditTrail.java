package kr.releasepilot.controlplane.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.HexFormat;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuditTrail {
    public static final String GENESIS_HASH="0".repeat(64);
    private final AuditEventRepository events;
    private final AuditChainHeadRepository heads;
    private final AuditArchiveDeliveryRepository deliveries;
    public AuditTrail(AuditEventRepository events,AuditChainHeadRepository heads,AuditArchiveDeliveryRepository deliveries){this.events=events;this.heads=heads;this.deliveries=deliveries;}

    @Transactional
    public AuditEvent record(AuditEvent event){
        var head=heads.lockById(1);
        if(head==null)head=heads.saveAndFlush(AuditChainHead.genesis());
        long sequence=head.nextSequence();
        String previous=head.lastHash();
        String hash=hash(sequence,previous,event);
        event.seal(sequence,previous,hash);
        var saved=events.save(event);
        deliveries.save(AuditArchiveDelivery.pending(saved.getId(),saved.getOccurredAt()));
        head.advance(sequence,hash);
        return saved;
    }

    static String hash(long sequence,String previous,AuditEvent event){
        String material=sequence+"\n"+previous+"\n"+event.getId()+"\n"+event.getAggregateType()+"\n"+event.getAggregateId()+"\n"+event.getEventType()+"\n"+event.getActorType()+"\n"+(event.getActorId()==null?"":event.getActorId())+"\n"+event.getOccurredAt().truncatedTo(ChronoUnit.MICROS)+"\n"+event.getCorrelationId()+"\n"+canonicalPayload(event.getPayloadJson());
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));}catch(Exception failure){throw new IllegalStateException("SHA-256 unavailable",failure);}
    }
    private static String canonicalPayload(String payload){try{return new ObjectMapper().writeValueAsString(sort(new ObjectMapper().readValue(payload,Object.class)));}catch(Exception failure){throw new IllegalArgumentException("Audit payload must be valid JSON",failure);}}
    private static Object sort(Object value){if(value instanceof Map<?,?> map){var sorted=new TreeMap<String,Object>();map.forEach((key,item)->sorted.put(String.valueOf(key),sort(item)));return sorted;}if(value instanceof List<?> list)return list.stream().map(AuditTrail::sort).toList();return value;}
}
