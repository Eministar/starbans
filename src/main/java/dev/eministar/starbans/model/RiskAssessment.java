package dev.eministar.starbans.model;

import java.util.List;

public record RiskAssessment(int score, String level, List<String> reasons) {
}
