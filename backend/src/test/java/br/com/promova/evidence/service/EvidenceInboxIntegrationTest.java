package br.com.promova.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.promova.analysis.persistence.SavedAnalysis;
import br.com.promova.analysis.persistence.SavedAnalysisRepository;
import br.com.promova.analysis.review.ReviewStatus;
import br.com.promova.analysis.review.persistence.SavedAnalysisReview;
import br.com.promova.analysis.review.persistence.SavedAnalysisReviewRepository;
import br.com.promova.evidence.Evidence;
import br.com.promova.evidence.EvidenceRepository;
import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EvidenceInboxIntegrationTest {
  @Autowired private EvidenceService service;
  @Autowired private EvidenceRepository evidences;
  @Autowired private SavedAnalysisRepository analyses;
  @Autowired private SavedAnalysisReviewRepository reviews;
  @Autowired private UserRepository users;

  @Test
  void acceptedMeansLatestAcceptedReviewAndFiltersAreServerSide() {
    User owner = users.save(new User("Inbox Owner", "inbox-owner-contract@example.com", "hash", UserRole.EMPLOYEE));
    User outsider = users.save(new User("Inbox Outsider", "inbox-outsider-contract@example.com", "hash", UserRole.EMPLOYEE));
    User reviewer = users.save(new User("Inbox Reviewer", "inbox-reviewer-contract@example.com", "hash", UserRole.MANAGER));
    Instant day = Instant.parse("2026-09-01T12:00:00Z");
    Evidence older = evidences.save(new Evidence(owner,"GitHub","pr-older","PR #1 - team/repo",
        "Titulo: OAuth seguro\nDescricao/resumo:\nNova validação do fluxo",null,day,day));
    older.setSourceFacts("dev-login", day, "segurança,backend");
    Evidence newer = evidences.save(new Evidence(owner,"GitHub","pr-newer","PR #2 - team/repo",
        "Titulo: Ajuste de interface\nDescricao/resumo: Layout de leitura",null,day.plusSeconds(60),day));
    Evidence hidden = evidences.save(new Evidence(outsider,"GitHub","pr-hidden","PR #3 - team/repo",
        "Titulo: OAuth oculto",null,day,day));
    Evidence dismissed = evidences.save(new Evidence(owner,"Slack","slack-1","Discussão",
        "Discussão de carreira",null,day.plusSeconds(120),day));
    dismissed.dismiss();
    newer.markAnalyzed();
    SavedAnalysis analysis = analyses.save(new SavedAnalysis(newer,"L3","L4","L4","HIGH",
        "Test justification","[]","[]","Ready",day));

    var initial = service.inbox(owner,"accepted",null,null,null,null,"newest",PageRequest.of(0,25));
    assertThat(initial.total()).isZero();
    assertThat(initial.counts()).containsEntry("pending",1L).containsEntry("accepted",0L)
        .containsEntry("dismissed",1L).containsEntry("all",3L);
    var newest = service.inbox(owner,"all",null,null,null,null,"newest",PageRequest.of(0,25));
    var oldest = service.inbox(owner,"all",null,null,null,null,"oldest",PageRequest.of(0,25));
    assertThat(newest.items()).extracting(item -> item.id())
        .containsExactly(dismissed.getId(),newer.getId(),older.getId());
    assertThat(oldest.items()).extracting(item -> item.id())
        .containsExactly(older.getId(),newer.getId(),dismissed.getId());
    assertThat(newest.items().get(0).summary()).isEqualTo("Sem resumo na fonte.");

    reviews.saveAndFlush(new SavedAnalysisReview(analysis,reviewer,ReviewStatus.ACCEPTED,null,day));
    var accepted = service.inbox(owner,"accepted",null,null,null,null,"newest",PageRequest.of(0,25));
    assertThat(accepted.total()).isEqualTo(1);
    assertThat(accepted.items().get(0).id()).isEqualTo(newer.getId());
    assertThat(accepted.items().get(0).currentReviewStatus()).isEqualTo("ACCEPTED");
    assertThat(accepted.counts()).containsEntry("accepted",1L);

    var search = service.inbox(owner,"pending","oAuTh","github",day,day,"oldest",PageRequest.of(0,25));
    assertThat(search.total()).isEqualTo(1);
    assertThat(search.items().get(0).id()).isEqualTo(older.getId());
    assertThat(search.items().get(0).tags()).containsExactly("segurança","backend");
    assertThat(search.items().get(0).author()).isEqualTo("dev-login");
    assertThat(search.items().get(0).summary()).contains("Nova validação");
    assertThat(search.counts()).containsEntry("pending",1L).containsEntry("all",1L);

    reviews.saveAndFlush(new SavedAnalysisReview(analysis,reviewer,ReviewStatus.NEEDS_CONTEXT,null,day.plusSeconds(10)));
    assertThat(service.inbox(owner,"accepted",null,null,null,null,"newest",PageRequest.of(0,25)).total()).isZero();
    assertThat(hidden.getId()).isNotEqualTo(older.getId());
  }
}
