export function renderManagerTeams(data, ui) {
  const [teams, members, candidates, selectedId] = data;
  const { esc, panel, empty } = ui;
  const selector = teams.length
    ? '<nav class="tabs" aria-label="Equipes">'
      + teams.map(team => '<a data-link href="/manage/teams?team=' + team.id + '" '
        + (team.id === selectedId ? 'aria-current="page"' : '') + '>'
        + esc(team.name) + ' (' + team.members + ')</a>').join("") + '</nav>'
    : empty("Nenhuma equipe cadastrada.");
  const list = members.length
    ? '<div class="row-list">' + members.map(member =>
      '<div class="row"><div><strong>' + esc(member.name) + '</strong><small>' + esc(member.email)
      + '</small></div><button type="button" class="button" data-action="remove-team-member"'
      + ' data-team-id="' + selectedId + '" data-employee-id="' + member.id + '">Remover</button></div>').join("")
      + '</div>'
    : empty("Esta equipe ainda não tem membros.");
  const choices = candidates.map(person =>
    '<option value="' + person.id + '">' + esc(person.name) + ' · ' + esc(person.email) + '</option>').join("");
  const assignment = selectedId
    ? panel("Membros da equipe", list,
      '<form id="assign-team-member-form" data-team-id="' + selectedId + '" class="dashboard-team-assign">'
      + '<label>Adicionar ou mover pessoa<select name="employeeId" required>'
      + '<option value="">Selecione uma pessoa</option>' + choices + '</select></label>'
      + '<button class="button primary" type="submit" ' + (candidates.length ? "" : "disabled") + '>Adicionar</button></form>')
    : "";
  const create = panel("Nova equipe",
    '<form id="create-team-form" class="dashboard-team-create"><label>Nome da equipe'
    + '<input name="name" maxlength="120" required placeholder="Ex.: Engenharia de Produto"></label>'
    + '<button class="button primary" type="submit">Criar equipe</button></form>');
  return '<div class="manager-teams"><p class="dashboard-intro">Organize as pessoas acompanhadas. Cada gestor vê apenas membros de suas equipes.</p>'
    + selector + assignment + create + '</div>';
}
