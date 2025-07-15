package com.premiergroup.ad_metrics_hub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "marketing_channels")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
//@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@EqualsAndHashCode(exclude = "campaigns")
@ToString(exclude = "campaigns")
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