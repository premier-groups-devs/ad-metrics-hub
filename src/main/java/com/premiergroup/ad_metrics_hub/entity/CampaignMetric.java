package com.premiergroup.ad_metrics_hub.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "campaign_metrics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CampaignMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(name = "stats_date", nullable = false)
    private LocalDate statsDate;

    private Integer clicks;
    private Integer impressions;
    private BigDecimal cost;
    private BigDecimal ctr;
    private BigDecimal avgCpc;
    private Integer conversions;
    private BigDecimal conversionRate;
    private BigDecimal costPerConversion;
    private BigDecimal conversionValue;
    private BigDecimal valuePerConversion;
    private BigDecimal roas;
}
