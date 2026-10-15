package com.glitchbytez.splitwise.activity;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes activity entries. Always called inside the transaction that made the change, so the change and its
 * log line are committed (or rolled back) together.
 */
@Service
public class ActivityService {

    private final ActivityRepository activities;

    public ActivityService(ActivityRepository activities) {
        this.activities = activities;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID groupId, UUID actorId, ActivityType type, String entityType, UUID entityId,
                       Map<String, Object> payload) {
        activities.save(new ActivityEntry(groupId, actorId, type, entityType, entityId, payload));
    }
}
