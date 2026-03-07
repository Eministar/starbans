package dev.eministar.starbans.model;

public record ModerationActionResult(ModerationActionType type, CaseRecord caseRecord) {

    public boolean successful() {
        return type == ModerationActionType.CREATED
                || type == ModerationActionType.EXECUTED
                || type == ModerationActionType.REMOVED;
    }
}
