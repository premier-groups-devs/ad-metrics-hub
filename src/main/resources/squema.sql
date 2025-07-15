-- Tabla de campañas
CREATE TABLE campaigns (
    id INT AUTO_INCREMENT PRIMARY KEY,
    marketing_channels_id INT NOT NULL,
    campaign_id VARCHAR(50) NOT NULL,
    name VARCHAR(255),
    status VARCHAR(50),
    FOREIGN KEY (marketing_channels_id) REFERENCES marketing_channels(id),
    UNIQUE KEY unique_campaign (marketing_channels_id, campaign_id)
);

-- Tabla de métricas por campaña y dia
CREATE TABLE campaign_metrics (
    id INT AUTO_INCREMENT PRIMARY KEY,
    campaign_id INT NOT NULL,
    stats_date DATE NOT NULL,
    clicks INT,
    impressions INT,
    cost DECIMAL(10,2),
    ctr DECIMAL(5,2),
    avg_cpc DECIMAL(10,2),
    conversions INT,
    conversion_rate DECIMAL(5,2),
    cost_per_conversion DECIMAL(10,2),
    conversion_value DECIMAL(10,2),       -- Google Ads solamente
    value_per_conversion DECIMAL(10,2),   -- Google Ads solamente
    roas DECIMAL(6,2),                    -- Google Ads: conversion_value / cost
    FOREIGN KEY (campaign_id) REFERENCES campaigns(id),
    UNIQUE KEY unique_campaign_metric (campaign_id, stats_date),
    INDEX idx_campaign_date (campaign_id, stats_date)
);