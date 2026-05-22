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
