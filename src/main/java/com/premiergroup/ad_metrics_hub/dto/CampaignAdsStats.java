package com.premiergroup.ad_metrics_hub.dto;

import java.util.List;
import java.util.Map;

public record CampaignAdsStats(List<Map<String, List<Integer>>> campaignValues, List<String> labels) {

}
