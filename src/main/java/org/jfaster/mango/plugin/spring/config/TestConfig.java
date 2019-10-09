package org.jfaster.mango.plugin.spring.config;

import java.util.List;

public class TestConfig {

    private List<MangoDataSourceConfig> datasources;

    private String masterSlaveDataSourceFactoryClass;
    private String simpleDataSourceFactoryClass;

    public List<MangoDataSourceConfig> getDatasources() {
        return datasources;
    }

    public void setDatasources(List<MangoDataSourceConfig> datasources) {
        this.datasources = datasources;
    }

    public String getMasterSlaveDataSourceFactoryClass() {
        return masterSlaveDataSourceFactoryClass;
    }

    public void setMasterSlaveDataSourceFactoryClass(String masterSlaveDataSourceFactoryClass) {
        this.masterSlaveDataSourceFactoryClass = masterSlaveDataSourceFactoryClass;
    }

    public String getSimpleDataSourceFactoryClass() {
        return simpleDataSourceFactoryClass;
    }

    public void setSimpleDataSourceFactoryClass(String simpleDataSourceFactoryClass) {
        this.simpleDataSourceFactoryClass = simpleDataSourceFactoryClass;
    }
}
