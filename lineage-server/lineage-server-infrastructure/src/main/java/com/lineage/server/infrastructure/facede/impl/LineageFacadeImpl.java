package com.lineage.server.infrastructure.facede.impl;

import com.hw.lineage.client.LineageClient;
import com.hw.lineage.common.enums.ParseStatus;
import com.hw.lineage.common.result.FunctionResult;
import com.hw.lineage.common.util.Base64Utils;
import com.lineage.server.domain.entity.Catalog;
import com.lineage.server.domain.entity.Task;
import com.lineage.server.domain.entity.TaskLineage;
import com.lineage.server.domain.entity.TaskSql;
import com.lineage.server.domain.facade.LineageFacade;
import com.lineage.server.infrastructure.config.LineageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @description: LineageFacadeImpl
 * @author: HamaWhite
 * @version: 1.0.0
 * @date: 2023/1/23 4:52 PM
 */
@Service
public class LineageFacadeImpl implements LineageFacade {

    private static final Logger LOG = LoggerFactory.getLogger(LineageFacadeImpl.class);

    @Resource
    private LineageConfig config;

    private LineageClient lineageClient;

    @PostConstruct
    public void initLineageClient() {
        LOG.info("start loading plugins, directory: {}", config.getPluginDir());
        this.lineageClient = new LineageClient(config.getPluginDir());
        LOG.info("finished loading plugins, directory: {}", config.getPluginDir());
    }

    @Override
    public void parseLineage(String pluginName, Task task, Catalog catalog) {
        lineageClient.setCatalog(pluginName
                , catalog.getCatalogType()
                , catalog.getCatalogName()
                , catalog.getDefaultDatabase()
        );

        for (TaskSql taskSql : task.getTaskSqlList()) {
            String sqlCode = Base64Utils.decode(taskSql.getSqlCode());
            switch (taskSql.getSqlType()) {
                case CREATE:
                case DROP:
                    lineageClient.execute(pluginName, sqlCode);
                    break;
                case INSERT:
                    parseFieldLineage(pluginName, task, taskSql, sqlCode);
                    break;
                default:
            }
        }
    }

    @Override
    public List<FunctionResult> parseFunction(String pluginName, File file) throws IOException, ClassNotFoundException {
        return lineageClient.parseFunction(pluginName, file);
    }


    private String functionName;

    private String functionFormat;

    private String functionClass;

    private String descr;

    private void parseFieldLineage(String pluginName, Task task, TaskSql taskSql, String sqlCode) {
        taskSql.setParseStatus(ParseStatus.PARSING);
        lineageClient.parseFieldLineage(pluginName, sqlCode)
                .forEach(e -> {
                    TaskLineage taskLineage = new TaskLineage()
                            .setTaskId(task.getTaskId())
                            .setSqlId(taskSql.getSqlId())
                            .setSourceCatalog(e.getSourceCatalog())
                            .setSourceDatabase(e.getSourceDatabase())
                            .setSourceTable(e.getSourceTable())
                            .setSourceColumn(e.getSourceColumn())
                            .setTargetCatalog(e.getTargetCatalog())
                            .setTargetDatabase(e.getTargetDatabase())
                            .setTargetTable(e.getTargetTable())
                            .setTargetColumn(e.getTargetColumn())
                            .setTransform(e.getTransform())
                            .setInvalid(false);

                    taskSql.setParseTime(System.currentTimeMillis());
                    taskSql.setParseStatus(ParseStatus.SUCCESS);
                    task.addTaskLineage(taskLineage);
                });
    }

}
