CREATE INDEX expenses_group_deleted_cursor_idx
    ON expenses (group_id, deleted, expense_id);
