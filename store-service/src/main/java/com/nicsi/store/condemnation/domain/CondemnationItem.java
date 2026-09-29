package com.nicsi.store.condemnation.domain;

import com.nicsi.store.asset.domain.Asset;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "condemnation_item", schema = "store")
public class CondemnationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condemnation_id", nullable = false)
    private Condemnation condemnation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "assessed_condition", length = 30)
    private String assessedCondition;

    @Column(name = "residual_value", precision = 18, scale = 2)
    private BigDecimal residualValue;

    @Column(name = "recommended_method", length = 30)
    private String recommendedMethod;

    @Column(columnDefinition = "text")
    private String remarks;

    public UUID getId() { return id; }
    public Condemnation getCondemnation() { return condemnation; }
    public void setCondemnation(Condemnation condemnation) { this.condemnation = condemnation; }
    public Asset getAsset() { return asset; }
    public void setAsset(Asset asset) { this.asset = asset; }
    public String getAssessedCondition() { return assessedCondition; }
    public void setAssessedCondition(String assessedCondition) { this.assessedCondition = assessedCondition; }
    public BigDecimal getResidualValue() { return residualValue; }
    public void setResidualValue(BigDecimal residualValue) { this.residualValue = residualValue; }
    public String getRecommendedMethod() { return recommendedMethod; }
    public void setRecommendedMethod(String recommendedMethod) { this.recommendedMethod = recommendedMethod; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
