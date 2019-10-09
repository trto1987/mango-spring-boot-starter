package org.jfaster.mango.plugin.spring.datasource;

import org.jfaster.mango.datasource.AbstractDataSourceFactory;

import javax.sql.DataSource;

public abstract class AbstractProxySimpleDataSourceFactory extends AbstractDataSourceFactory {

    protected DataSource dataSource;

    protected DataSource testDataSource;

    public AbstractProxySimpleDataSourceFactory() {
    }

    public AbstractProxySimpleDataSourceFactory(DataSource dataSource, DataSource testDataSource) {
        this.dataSource = dataSource;
        this.testDataSource = testDataSource;
    }

    public AbstractProxySimpleDataSourceFactory(String name, DataSource dataSource, DataSource testDataSource) {
        super(name);
        this.dataSource = dataSource;
        this.testDataSource = testDataSource;
    }

    public abstract DataSource getMasterDataSource();

    public abstract DataSource getSlaveDataSource(Class<?> daoClass);

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void setTestDataSource(DataSource testDataSource) {
        this.testDataSource = testDataSource;
    }

}
