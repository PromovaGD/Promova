package br.com.promova.analysis.engine.ai;

import java.util.List;

public interface AiChatClient {
  String complete(List<AiChatMessage> messages);
}
