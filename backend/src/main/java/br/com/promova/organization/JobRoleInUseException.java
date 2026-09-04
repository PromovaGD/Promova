package br.com.promova.organization;

public class JobRoleInUseException extends RuntimeException {
  private final long affectedCount;

  public JobRoleInUseException(long affectedCount) {
    super("O cargo está atribuído a " + affectedCount + " usuário(s). Selecione um cargo alternativo.");
    this.affectedCount = affectedCount;
  }

  public long affectedCount() {
    return affectedCount;
  }
}
