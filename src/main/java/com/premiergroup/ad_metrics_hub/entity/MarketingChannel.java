package com.premiergroup.ad_metrics_hub.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "marketing_channels")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class MarketingChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "source_name")
    private String sourceName;

    @Column(name = "is_active")
    private Boolean isActive;

    private String url;

    @Column(name = "date_create")
    private LocalDateTime dateCreate;

    @OneToMany(mappedBy = "marketingChannel", cascade = CascadeType.ALL)
    private Set<Campaign> campaigns;
}