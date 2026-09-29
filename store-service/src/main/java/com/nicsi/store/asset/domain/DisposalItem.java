package com.nicsi.store.asset.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "disposal_item", schema = "store")
public class DisposalItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disposal_id", nullable = false)
    private Disposal disposal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "realized_value", precision = 18, scale = 2, nullable = false)
    private BigDecimal realizedValue = BigDecimal.ZERO;

    @Column(columnDefinition = "text")
    private String remarks;

    public DisposalItem() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Disposal getDisposal() { return disposal; }
    public void setDisposal(Disposal disposal) { this.disposal = disposal; }

    public Asset getAsset() { return asset; }
    public void setAsset(Asset asset) { this.asset = asset; }

    public BigDecimal getRealizedValue() { return realizedValue; }
    public void setRealizedValue(BigDecimal realizedValue) { this.realizedValue = realizedValue; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
