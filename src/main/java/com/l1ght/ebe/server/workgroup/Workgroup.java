package com.l1ght.ebe.server.workgroup;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Workgroup {
    public UUID id = UUID.randomUUID();
    public String name = "";
    public String password = "";
    public UUID leader;
    public String leaderName = "";
    public Map<UUID, String> members = new LinkedHashMap<>();
    public Map<UUID, ProjectionState> projections = new LinkedHashMap<>();
    public List<ChatMessage> chat = new ArrayList<>();

    public boolean isMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    public boolean isLeader(UUID playerId) {
        return leader != null && leader.equals(playerId);
    }

    public record ProjectionState(UUID id, UUID owner, String ownerName, String fileName,
                                  int x, int y, int z, boolean visible, long updatedAt) {}

    public record ChatMessage(UUID sender, String senderName, String message, long sentAt) {}
}
