package com.glitchbytez.splitwise.activity;

import java.util.Map;
import java.util.UUID;

import com.glitchbytez.splitwise.common.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes and reads activity entries. Always called inside the transaction that made the change, so the change and its
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

    /**
     * Newest first. Does no access check of its own: callers must have verified group membership
     * (see {@code GroupService#activity}).
     */
    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> feed(UUID groupId, int page, int size) {
        Pageable newestFirst = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.from(activities.findByGroupId(groupId, newestFirst), ActivityResponse::from);
    }
}
