package com.glitchbytez.splitwise.activity;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<ActivityEntry, UUID> {

    Page<ActivityEntry> findByGroupId(UUID groupId, Pageable pageable);
}
