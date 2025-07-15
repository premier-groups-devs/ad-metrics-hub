package com.premiergroup.ad_metrics_hub.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Entity
@Table(name = "campaigns")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marketing_channels_id", nullable = false)
    private MarketingChannel marketingChannel;

    @Column(name = "campaign_id", nullable = false)
    private String campaignId;

    private String name;
    private String status;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL)
    private Set<CampaignMetric> metrics;
}