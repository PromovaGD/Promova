package br.com.promova.analysis.review.persistence;

public interface ReviewQueueCountProjection {
  String getCurrentStatus();
  Long getTotal();
}
