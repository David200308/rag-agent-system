package com.ragagent.skill;

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

    @Transactional(readOnly = true)
    public List<Skill> list(String ownerEmail) {
        if (ownerEmail == null) return repo.findAllByOrderByCreatedAtDesc();
        return repo.findByOwnerEmailOrderByCreatedAtDesc(ownerEmail);
    }

    @Transactional
    public Skill create(String ownerEmail, String name, String fileName,
                        String fileType, long size, String content) {
        Skill skill = new Skill(UUID.randomUUID().toString(), ownerEmail,
                name, fileName, fileType, size, content);
        repo.save(skill);
        log.info("[SkillService] Created skill '{}' (id={}) for {}", name, skill.getId(), ownerEmail);
        return skill;
    }

    @Transactional(readOnly = true)
    public Optional<String> getContent(String id) {
        return repo.findById(id).map(Skill::getContent);
    }

    @Transactional
    public void delete(String id, String callerEmail) {
        repo.findById(id).ifPresent(skill -> {
            if (callerEmail != null && skill.getOwnerEmail() != null
                    && !skill.getOwnerEmail().equalsIgnoreCase(callerEmail)) {
                throw new SecurityException("Only the owner can delete this skill.");
            }
            repo.deleteById(id);
            log.info("[SkillService] Deleted skill id={} by {}", id, callerEmail);
        });
    }
}
