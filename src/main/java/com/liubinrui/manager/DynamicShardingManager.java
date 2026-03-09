package com.liubinrui.manager;

import com.liubinrui.enums.SpaceTypeEnum;
import com.liubinrui.model.entity.Space;
import com.liubinrui.service.SpaceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.driver.jdbc.core.connection.ShardingSphereConnection;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.rule.ShardingRule;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class DynamicShardingManager {

    private final DataSource dataSource;
    private final SpaceService spaceService;

    private static final String LOGIC_TABLE_NAME = "picture";
    // 注意：这里必须是 ShardingSphere 内部注册的逻辑数据库名
    // 逻辑数据库名（ShardingSphere 默认值）
    private static final String LOGIC_DATABASE_NAME = "logic_db";

    // 真实数据源名称（来自 application.yml）
    private static final String DATA_SOURCE_NAME = "yuntuku";

    public DynamicShardingManager(DataSource dataSource, SpaceService spaceService) {
        this.dataSource = dataSource;
        this.spaceService = spaceService;
    }

    @PostConstruct
    public void initialize() {
        log.info("初始化动态分表配置...");
        ContextManager cm = getContextManager();
        log.info("已注册的逻辑数据库: {}",
                cm.getMetaDataContexts().getMetaData().getDatabases().keySet());
        updateShardingTableNodes();
    }

    /**
     * 动态更新分表规则（核心方法）
     */
    public void updateShardingTableNodes() {
        Set<String> tableNames = fetchAllPictureTableNames();
        // actual-data-nodes 格式：logic_db.picture,logic_db.picture_1001,...
        String newActualDataNodes = tableNames.stream()
                .map(tableName -> DATA_SOURCE_NAME + "." + tableName)
                .collect(Collectors.joining(","));

        log.info("动态分表 actual-data-nodes 配置: {}", newActualDataNodes);

        ContextManager contextManager = getContextManager();

        // 获取当前 ShardingRuleConfiguration（通过规则元数据）
        ShardingSphereDatabase database = contextManager.getMetaDataContexts()
                .getMetaData()
                .getDatabases()
                .get(LOGIC_DATABASE_NAME);

        if (database == null) {
            log.error("未找到逻辑数据库: {}", LOGIC_DATABASE_NAME);
            return;
        }

        Optional<ShardingRule> shardingRuleOpt = database.getRuleMetaData()
                .findSingleRule(ShardingRule.class);

        if (!shardingRuleOpt.isPresent()) {
            log.error("未找到 ShardingRule");
            return;
        }

        // 构建新的 ShardingRuleConfiguration
        ShardingRuleConfiguration oldConfig = (ShardingRuleConfiguration) shardingRuleOpt.get().getConfiguration();
        ShardingRuleConfiguration newConfig = new ShardingRuleConfiguration();

        // 复制非 picture 表的规则
        List<ShardingTableRuleConfiguration> updatedTables = oldConfig.getTables().stream()
                .map(tableRule -> {
                    if (LOGIC_TABLE_NAME.equals(tableRule.getLogicTable())) {
                        ShardingTableRuleConfiguration newRule = new ShardingTableRuleConfiguration(
                                LOGIC_TABLE_NAME,
                                newActualDataNodes
                        );
                        newRule.setTableShardingStrategy(tableRule.getTableShardingStrategy());
                        return newRule;
                    }
                    return tableRule;
                })
                .collect(Collectors.toList());

        newConfig.setTables(updatedTables);
        newConfig.setDefaultDatabaseShardingStrategy(oldConfig.getDefaultDatabaseShardingStrategy());
        newConfig.setDefaultTableShardingStrategy(oldConfig.getDefaultTableShardingStrategy());
        newConfig.setBroadcastTables(oldConfig.getBroadcastTables());
        newConfig.setShardingAlgorithms(oldConfig.getShardingAlgorithms());
        newConfig.setKeyGenerators(oldConfig.getKeyGenerators());

        // 关键：通过反射调用 alterRuleConfiguration
        try {
            Method alterMethod = contextManager.getClass()
                    .getMethod("alterRuleConfiguration", String.class, Collection.class);
            alterMethod.setAccessible(true);
            alterMethod.invoke(contextManager, LOGIC_DATABASE_NAME, Collections.singleton(newConfig));

            // 重新加载数据库元数据
            Method reloadMethod = contextManager.getClass()
                    .getMethod("reloadDatabase", String.class);
            reloadMethod.setAccessible(true);
            reloadMethod.invoke(contextManager, LOGIC_DATABASE_NAME);

            log.info("动态分表规则更新成功！");
        } catch (Exception e) {
            log.error("动态更新分表规则失败", e);
            throw new RuntimeException("Failed to update sharding rule", e);
        }
    }

    private Set<String> fetchAllPictureTableNames() {
        List<Space> teamSpaces = spaceService.lambdaQuery()
                .eq(Space::getSpaceType, SpaceTypeEnum.TEAM.getValue())
                .list();

        Set<String> tableNames = teamSpaces.stream()
                .map(space -> LOGIC_TABLE_NAME + "_" + space.getId())
                .collect(Collectors.toSet());

        // 保留默认表（用于个人空间）
        tableNames.add(LOGIC_TABLE_NAME);
        return tableNames;
    }

    private ContextManager getContextManager() {
        try {
            // 通过连接获取 ContextManager
            ShardingSphereConnection connection = dataSource.getConnection()
                    .unwrap(ShardingSphereConnection.class);
            return connection.getContextManager();
        } catch (SQLException e) {
            throw new RuntimeException("获取 ContextManager 失败", e);
        }
    }
}

