package com.urlshortener.url_shortener.entity;

import com.urlshortener.url_shortener.enums.TierType;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tiers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(length = 50,  unique = true, nullable = false)
    private TierType name;

    @Column(name = "can_use_bulk_shorten", nullable = false)
    private boolean canUseBulkCreation;
}
