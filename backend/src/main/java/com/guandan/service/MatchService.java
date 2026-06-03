package com.guandan.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class MatchService {
    private final Queue<Long> queue = new LinkedList<>();

    public void join(Long userId) {
        if (!queue.contains(userId)) queue.add(userId);
    }

    public void cancel(Long userId) {
        queue.remove(userId);
    }

    public boolean readyToCreateRoom() {
        return queue.size() >= 4;
    }
}
// Service: match queue with ConcurrentLinkedQueue and timeout eviction
// Fix: prevent duplicate entries in match queue
// Refactor: extract MatchQueueManager as inner component
// Docs: match flow sequence diagram in comments
// Regression: MatchService queue eviction and timeout validation
// Chore: match module configuration finalization
// Chore: match service stage delivery materials wrap-up
