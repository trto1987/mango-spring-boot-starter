package org.jfaster.mango.plugin.spring.datasource;

import org.jfaster.mango.datasource.AbstractDataSourceFactory;

import javax.sql.DataSource;
import java.util.List;

public abstract class AbstractProxyMasterSlaveDataSourceFactory extends AbstractDataSourceFactory {

    protected DataSource master;
    protected DataSource testMaster;
    protected List<DataSource> slaves;
    protected List<DataSource> testSlaves;

    public AbstractProxyMasterSlaveDataSourceFactory() {
    }

    public AbstractProxyMasterSlaveDataSourceFactory(
            DataSource master, List<DataSource> slaves, DataSource testMaster, List<DataSource> testSlaves) {
        this.master = master;
        this.slaves = slaves;
        this.testMaster = testMaster;
        this.testSlaves = testSlaves;
    }

    public AbstractProxyMasterSlaveDataSourceFactory(
            String name,
            DataSource master, List<DataSource> slaves,
            DataSource testMaster, List<DataSource> testSlaves) {
        super(name);
        this.master = master;
        this.slaves = slaves;
        this.testMaster = testMaster;
        this.testSlaves = testSlaves;
    }

    public abstract DataSource getMasterDataSource();

    public abstract DataSource getSlaveDataSource(Class<?> daoClass);

    public DataSource getMaster() {
        return master;
    }

    public DataSource getTestMaster() {
        return testMaster;
    }

    public void setMaster(DataSource master) {
        this.master = master;
    }

    public void setTestMaster(DataSource testMaster) {
        this.testMaster = testMaster;
    }

    public List<DataSource> getSlaves() {
        return slaves;
    }

    public List<DataSource> getTestSlaves() {
        return testSlaves;
    }

    public void setSlaves(List<DataSource> slaves) {
        this.slaves = slaves;
    }

    public void setTestSlaves(List<DataSource> testSlaves) {
        this.testSlaves = testSlaves;
    }

}
