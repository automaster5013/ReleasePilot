package kr.releasepilot.controlplane.audit;

import jakarta.persistence.*;

@Entity
@Table(name="audit_chain_head")
class AuditChainHead {
    @Id private Integer id;
    @Column(name="last_sequence",nullable=false) private long lastSequence;
    @Column(name="last_hash",nullable=false,length=64) private String lastHash;
    protected AuditChainHead(){}
    static AuditChainHead genesis(){var value=new AuditChainHead();value.id=1;value.lastHash=AuditTrail.GENESIS_HASH;return value;}
    long nextSequence(){return lastSequence+1;}
    long lastSequence(){return lastSequence;}
    String lastHash(){return lastHash;}
    void advance(long sequence,String hash){if(sequence!=lastSequence+1)throw new IllegalStateException("Non-contiguous audit sequence");lastSequence=sequence;lastHash=hash;}
}
