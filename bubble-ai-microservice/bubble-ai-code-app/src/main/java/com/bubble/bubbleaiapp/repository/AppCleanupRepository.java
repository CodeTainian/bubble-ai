package com.bubble.bubbleaiapp.repository;

import com.bubble.bubbleai.model.entity.App;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 已逻辑删除应用的物理清理数据访问。
 */
@Repository
public class AppCleanupRepository {

    public List<App> listDeletedApps(int limit) {
        List<Row> rows = Db.selectListBySql("""
                select
                    id,
                    appName,
                    cover,
                    initPrompt,
                    codeGenType,
                    deployKey,
                    deployedTime,
                    priority,
                    userId,
                    editTime,
                    createTime,
                    updateTime,
                    isDelete
                from app
                where isDelete = 1
                order by updateTime asc, id asc
                limit ?
                """, limit);
        return rows.stream()
                .map(row -> row.toEntity(App.class))
                .toList();
    }

    public int physicalDeleteChatHistoryByAppId(Long appId) {
        return Db.deleteBySql("delete from chat_history where appId = ?", appId);
    }

    public int physicalDeleteDeletedAppById(Long appId) {
        return Db.deleteBySql("delete from app where id = ? and isDelete = 1", appId);
    }
}
