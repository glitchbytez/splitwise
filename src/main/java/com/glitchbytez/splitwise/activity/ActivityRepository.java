package com.glitchbytez.splitwise.activity;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<ActivityEntry, UUID> {
}
