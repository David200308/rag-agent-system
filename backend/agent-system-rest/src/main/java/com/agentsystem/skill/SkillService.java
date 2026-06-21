package com.ragagent.skill;

import com.ragagent.org.OrgContext;
import com.ragagent.skill.entity.Skill;
import com.ragagent.skill.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository repo;

    /**
     * List skills for the UI.
     * Team mode: APPROVED skills + caller's own PENDING/REJECTED submissions.
     * Personal: owned skills only.
     */
    @Transactional(readOnly = true)
    public List<Skill> list(OrgContext ctx) {
        if (ctx == null || ctx.email() == null) return repo.findAllByOrderByCreatedAtDesc();
        if (ctx.isTeam()) return repo.findByOrgIdForMember(ctx.orgId(), ctx.email());
        return repo.findByOwnerEmailAndOrgIdIsNullOrderByCreatedAtDesc(ctx.email());
    }

    @Transactional(readOnly = true)
    public List<Skill> list(String ownerEmail) {
        if (ownerEmail == null) return repo.findAllByOrderByCreatedAtDesc();
        return repo.findByOwnerEmailAndOrgIdIsNullOrderByCreatedAtDesc(ownerEmail);
    }

    /** All PENDING skills for the org (owner approval queue). */
    @Transactional(readOnly = true)
    public List<Skill> listPendingByOrg(String orgId) {
        return repo.findPendingByOrgId(orgId);
    }

    @Transactional
    public Skill create(OrgContext ctx, String name, String fileName,
                        String fileType, long size, String content) {
        Skill skill = new Skill(UUID.randomUUID().toString(), ctx.email(),
                name, fileName, fileType, size, content);
        if (ctx.isTeam()) {
            skill.setOrgId(ctx.orgId());
            skill.setStatus("PENDING");
        }
        repo.save(skill);
        log.info("[SkillService] Created skill '{}' (id={}) for {} (org={}) status={}",
                name, skill.getId(), ctx.email(), ctx.orgId(), skill.getStatus());
        return skill;
    }

    /** Approve a pending skill (owner only — caller must enforce ownership). */
    @Transactional
    public Skill approve(String id) {
        Skill skill = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + id));
        skill.setStatus("APPROVED");
        log.info("[SkillService] Approved skill id={} name='{}'", id, skill.getName());
        return repo.save(skill);
    }

    /** Reject a pending skill: sets REJECTED status. */
    @Transactional
    public void reject(String id) {
        Skill skill = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + id));
        skill.setStatus("REJECTED");
        repo.save(skill);
        log.info("[SkillService] Rejected skill id={} name='{}'", id, skill.getName());
    }

    @Transactional
    public Skill create(String ownerEmail, String name, String fileName,
                        String fileType, long size, String content) {
        return create(new OrgContext(ownerEmail, "PERSONAL", null), name, fileName, fileType, size, content);
    }

    @Transactional(readOnly = true)
    public Optional<String> getContent(String id) {
        return repo.findById(id).map(Skill::getContent);
    }

    @Transactional
    public void delete(String id, OrgContext ctx) {
        repo.findById(id).ifPresent(skill -> {
            // Team mode: any org member may delete
            if (!ctx.isTeam()) {
                String callerEmail = ctx.email();
                if (callerEmail != null && skill.getOwnerEmail() != null
                        && !skill.getOwnerEmail().equalsIgnoreCase(callerEmail)) {
                    throw new SecurityException("Only the owner can delete this skill.");
                }
            }
            repo.deleteById(id);
            log.info("[SkillService] Deleted skill id={} by {}", id, ctx.email());
        });
    }

    @Transactional
    public void delete(String id, String callerEmail) {
        delete(id, new OrgContext(callerEmail, "PERSONAL", null));
    }
}
