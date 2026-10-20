package com.constructflow.user.dto;

import com.constructflow.user.entity.User;

import java.util.UUID;

/** The minimal public view of a user, embedded in other responses (assignee, manager, actor...). */
public record UserSummary(UUID id, String fullName, String email) {

    public static UserSummary of(User user) {
        return user == null ? null : new UserSummary(user.getId(), user.getFullName(), user.getEmail());
    }
}
