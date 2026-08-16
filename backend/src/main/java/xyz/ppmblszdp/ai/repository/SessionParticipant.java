package xyz.ppmblszdp.ai.repository;

/**
 * 共享会话参与者（会话 ID + 用户 ID + 角色）。
 *
 * <p>角色语义：
 * <ul>
 *   <li>{@code OWNER}  — 会话所有者，可管理协作者、删除会话。</li>
 *   <li>{@code EDITOR} — 可编辑协作者，可发送/编辑/删除消息。</li>
 *   <li>{@code VIEWER} — 只读协作者，仅可查看与跟随光标。</li>
 * </ul>
 */
public record SessionParticipant(String sessionId, String userId, Role role) {

    public enum Role {
        OWNER,
        EDITOR,
        VIEWER;

        /** 是否至少具备给定角色等级（OWNER > EDITOR > VIEWER）。 */
        public boolean atLeast(Role min) {
            return this.ordinal() <= min.ordinal();
        }
    }
}
