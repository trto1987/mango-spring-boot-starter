/*
 * Copyright 2014 mango.jfaster.org
 *
 * The Mango Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.jfaster.mango.plugin.spring.starter;

import com.zaxxer.hikari.HikariDataSource;
import org.jfaster.mango.annotation.DB;
import org.jfaster.mango.datasource.AbstractDataSourceFactory;
import org.jfaster.mango.datasource.DataSourceFactory;
import org.jfaster.mango.datasource.MasterSlaveDataSourceFactory;
import org.jfaster.mango.datasource.SimpleDataSourceFactory;
import org.jfaster.mango.interceptor.Interceptor;
import org.jfaster.mango.operator.Mango;
import org.jfaster.mango.operator.cache.CacheHandler;
import org.jfaster.mango.plugin.spring.DefaultMangoFactoryBean;
import org.jfaster.mango.plugin.spring.config.*;
import org.jfaster.mango.plugin.spring.datasource.AbstractProxyMasterSlaveDataSourceFactory;
import org.jfaster.mango.plugin.spring.datasource.AbstractProxySimpleDataSourceFactory;
import org.jfaster.mango.plugin.spring.exception.MangoAutoConfigException;
import org.jfaster.mango.util.Strings;
import org.jfaster.mango.util.logging.InternalLogger;
import org.jfaster.mango.util.logging.InternalLoggerFactory;
import org.jfaster.mango.util.reflect.Reflection;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author  fangyanpeng.
 */
public class MangoDaoAutoCreator implements BeanFactoryPostProcessor,BeanPostProcessor,ApplicationContextAware {

    private final static InternalLogger logger = InternalLoggerFactory.getInstance(MangoDaoAutoCreator.class);


    private static final String PREFIX = "mango";
    private static final String TEST_PREFIX = "test";

    private static final List<String> DAO_ENDS = Arrays.asList("Dao", "DAO");

    Class<?> factoryBeanClass = DefaultMangoFactoryBean.class;

    private ApplicationContext context;

    private MangoConfig config;
    private TestConfig testConfig;


    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) configurableListableBeanFactory;
        config = MangoConfigFactory.getMangoConfig(beanFactory,PREFIX);
        testConfig = TestConfigFactory.getTestConfig(beanFactory,TEST_PREFIX);
        if(config == null){
            throw new MangoAutoConfigException("Mango config file does not exist!");
        }
        String mangoDaoPackage = config.getScanPackage();
        if(Strings.isEmpty(mangoDaoPackage)){
            throw new MangoAutoConfigException("mango.scan-package is not configured");
        }
        if(!Strings.isEmpty(config.getFactoryClass())){
            try {
                factoryBeanClass = ClassUtils.forName(config.getFactoryClass(), MangoAutoConfiguration.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new MangoAutoConfigException(e);
            }
        }
        if(factoryBeanClass.equals(DefaultMangoFactoryBean.class)){
            List<MangoDataSourceConfig> datasources = config.getDatasources();
            if(datasources == null || datasources.size() == 0){
                throw new MangoAutoConfigException("mango.datasources is not configured");
            }
            List<MangoDataSourceConfig> testDatasources = testConfig.getDatasources();
            if(testDatasources == null || testDatasources.size() == 0){
                logger.warn("test.datasources is not configured");
            }
            registryMangoInstance(beanFactory);
        }
        registryMangoDao(beanFactory);

    }

    /**
     * 向spring中注入mango实例
     * @param beanFactory
     */
    private void registryMangoInstance(DefaultListableBeanFactory beanFactory){
        BeanDefinitionBuilder mangoBuilder = BeanDefinitionBuilder.rootBeanDefinition(Mango.class);

        mangoBuilder.setFactoryMethod("newInstance");
        mangoBuilder.addPropertyValue("checkColumn",config.isCheckColumn());
        mangoBuilder.addPropertyValue("compatibleWithEmptyList",config.isCompatibleWithEmptyList());
        mangoBuilder.addPropertyValue("lazyInit",config.isLazyInit());
        mangoBuilder.addPropertyValue("useActualParamName",config.isUseActualParamName());
        mangoBuilder.addPropertyValue("useTransactionForBatchUpdate",config.isUseTransactionForBatchUpdate());

        configCacheHandler(mangoBuilder);

        beanFactory.registerBeanDefinition(Mango.class.getName(),mangoBuilder.getBeanDefinition());
    }

    /**
     * 设置缓存处理器
     * @param mangoBuilder
     */

    private void configCacheHandler(BeanDefinitionBuilder mangoBuilder){
        String cacheHandlerClassPath = config.getCacheHandler();
        if(!Strings.isEmpty(cacheHandlerClassPath)) {
            try {
                Class<? extends CacheHandler> cachHandlerClz = (Class<? extends CacheHandler>) Class.forName(cacheHandlerClassPath);
                CacheHandler cacheHandler = Reflection.instantiateClass(cachHandlerClz);
                mangoBuilder.addPropertyValue("cacheHandler", cacheHandler);
            } catch (Throwable e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

    /**
     * 设置datasource和测试datasource
     * @param mango
     */
    private void configMangoDatasourceFactory(Mango mango){
        List<MangoDataSourceConfig> datasources = config.getDatasources();
        List<MangoDataSourceConfig> testDatasources = testConfig.getDatasources();
        for (int i = 0; i < datasources.size(); i++) {
            MangoDataSourceConfig dataSourceConfig = datasources.get(i);

            String name = dataSourceConfig.getName();
            MangoHikaricpConfig masterConfig = dataSourceConfig.getMaster();
            List<MangoHikaricpConfig> slaveConfigs = dataSourceConfig.getSlaves();
            if(masterConfig == null){
                throw new MangoAutoConfigException("Does not exist master datasource");
            }
            if(Strings.isEmpty(name)){
                name = AbstractDataSourceFactory.DEFULT_NAME;
            }
            DataSourceFactory dataSourceFactory;
            DataSource masterDataSource = getDataSource(masterConfig);
            if(slaveConfigs == null || slaveConfigs.isEmpty()){
                if (testDatasources == null || testDatasources.size() == 0) {
                    dataSourceFactory = new SimpleDataSourceFactory(name,masterDataSource);
                } else {
                    MangoDataSourceConfig testDataSourceConfig = null;
                    if (name.equals(AbstractDataSourceFactory.DEFULT_NAME)) {
                        testDataSourceConfig = testDatasources.get(i);
                    } else {
                        for (MangoDataSourceConfig t: testDatasources) {
                            String testName = t.getName();
                            if (name.equals(testName)) {
                                testDataSourceConfig = t;
                            }
                        }
                        if (testDataSourceConfig == null) {
                            throw new MangoAutoConfigException("Does not exist test master datasource named: " + name);
                        }
                    }

                    MangoHikaricpConfig testMasterConfig = testDataSourceConfig.getMaster();
                    if (testMasterConfig == null) {
                        throw new MangoAutoConfigException("Does not exist test master datasource");
                    }
                    DataSource testMasterDataSource = getDataSource(testMasterConfig);

                    if(Strings.isEmpty(testConfig.getSimpleDataSourceFactoryClass())){
                        throw new MangoAutoConfigException("Does not exist test simpleDataSourceFactoryClass");
                    }

                    Class dataSourceFactoryClass;
                    try {
                        dataSourceFactoryClass = ClassUtils.forName(
                                testConfig.getSimpleDataSourceFactoryClass(), MangoAutoConfiguration.class.getClassLoader());
                    } catch (ClassNotFoundException e) {
                        throw new MangoAutoConfigException(e);
                    }

                    AbstractProxySimpleDataSourceFactory instance;
                    try {
                        instance = (AbstractProxySimpleDataSourceFactory) dataSourceFactoryClass.newInstance();
                        instance.setName(name);
                        instance.setDataSource(masterDataSource);
                        instance.setTestDataSource(testMasterDataSource);
                    } catch (InstantiationException e) {
                        throw new MangoAutoConfigException(e);
                    } catch (IllegalAccessException e) {
                        throw new MangoAutoConfigException(e);
                    }

                    dataSourceFactory = instance;
                }
            }else {
                List<DataSource> slaves = new ArrayList<>(slaveConfigs.size());
                for(MangoHikaricpConfig hikaricpConfig : slaveConfigs){
                    slaves.add(getDataSource(hikaricpConfig));
                }

                if (testDatasources == null || testDatasources.size() == 0) {
                    dataSourceFactory = new MasterSlaveDataSourceFactory(name,masterDataSource,slaves);
                } else {
                    MangoDataSourceConfig testDataSourceConfig = null;

                    if (name.equals(AbstractDataSourceFactory.DEFULT_NAME)) {
                        testDataSourceConfig = testDatasources.get(i);
                    } else {
                        for (MangoDataSourceConfig t: testDatasources) {
                            String testName = t.getName();
                            if (name.equals(testName)) {
                                testDataSourceConfig = t;
                            }
                        }
                        if (testDataSourceConfig == null) {
                            throw new MangoAutoConfigException("Does not exist test master datasource named: " + name);
                        }
                    }

                    List<MangoHikaricpConfig> testSlaveConfigs = testDataSourceConfig.getSlaves();
                    if(testSlaveConfigs == null || testSlaveConfigs.isEmpty()) {
                        throw new MangoAutoConfigException("Test master datasource [" + name + "] does not exist slave datasource");
                    }

                    MangoHikaricpConfig testMasterConfig = testDataSourceConfig.getMaster();
                    if (testMasterConfig == null) {
                        throw new MangoAutoConfigException("Does not exist test master datasource");
                    }
                    DataSource testMasterDataSource = getDataSource(testMasterConfig);

                    List<DataSource> testSlaves = new ArrayList<>(testSlaveConfigs.size());
                    for(MangoHikaricpConfig hikaricpConfig : testSlaveConfigs){
                        testSlaves.add(getDataSource(hikaricpConfig));
                    }

                    if(Strings.isEmpty(testConfig.getMasterSlaveDataSourceFactoryClass())){
                        throw new MangoAutoConfigException("Does not exist test masterSlaveDataSourceFactoryClass");
                    }

                    Class dataSourceFactoryClass;
                    try {
                        dataSourceFactoryClass = ClassUtils.forName(
                                testConfig.getMasterSlaveDataSourceFactoryClass(), MangoAutoConfiguration.class.getClassLoader());
                    } catch (ClassNotFoundException e) {
                        throw new MangoAutoConfigException(e);
                    }

                    AbstractProxyMasterSlaveDataSourceFactory instance;
                    try {
                        instance = (AbstractProxyMasterSlaveDataSourceFactory) dataSourceFactoryClass.newInstance();
                        instance.setName(name);
                        instance.setMaster(masterDataSource);
                        instance.setSlaves(slaves);
                        instance.setTestMaster(testMasterDataSource);
                        instance.setTestSlaves(testSlaves);
                    } catch (InstantiationException e) {
                        throw new MangoAutoConfigException(e);
                    } catch (IllegalAccessException e) {
                        throw new MangoAutoConfigException(e);
                    }

                    dataSourceFactory = instance;
                }

            }
            mango.addDataSourceFactory(dataSourceFactory);
        }
    }

    private DataSource getDataSource(MangoHikaricpConfig dataSourceConfig){
        if(!Strings.isEmpty(dataSourceConfig.getRef())) {
            DataSource dataSource = context.getBean(dataSourceConfig.getRef(), DataSource.class);
            if (dataSource == null) {
                throw new MangoAutoConfigException("'%s' not exist in spring context", dataSourceConfig.getRef());
            }
            return dataSource;
        }
        return new HikariDataSource(dataSourceConfig);
    }

    /**
     * 向spring中注入dao代理
     * @param beanFactory
     */
    private void registryMangoDao(DefaultListableBeanFactory beanFactory){
        for (Class<?> daoClass : findMangoDaoClasses(config.getScanPackage())) {
            GenericBeanDefinition bf = new GenericBeanDefinition();
            bf.setBeanClassName(daoClass.getName());
            MutablePropertyValues pvs = bf.getPropertyValues();
            pvs.addPropertyValue("daoClass", daoClass);
            bf.setBeanClass(factoryBeanClass);
            bf.setPropertyValues(pvs);
            bf.setLazyInit(false);
            beanFactory.registerBeanDefinition(daoClass.getName(), bf);
        }
    }


    private List<Class<?>> findMangoDaoClasses(String packages) {
        try {
            List<Class<?>> daos = new ArrayList<Class<?>>();
            ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
            MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resourcePatternResolver);
            for (String locationPattern : getLocationPattern(packages)) {
                Resource[] rs = resourcePatternResolver.getResources(locationPattern);
                for (Resource r : rs) {
                    MetadataReader reader = metadataReaderFactory.getMetadataReader(r);
                    AnnotationMetadata annotationMD = reader.getAnnotationMetadata();
                    if (annotationMD.hasAnnotation(DB.class.getName())) {
                        ClassMetadata clazzMD = reader.getClassMetadata();
                        daos.add(Class.forName(clazzMD.getClassName()));
                    }
                }
            }
            return daos;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private List<String> getLocationPattern(String packages) {
        List<String> locationPatterns = new ArrayList<>();
        String[] locationPackages = packages.split("\\s*[,;]+\\s*");
        for (String p : locationPackages) {
            for (String daoEnd : DAO_ENDS) {
                String locationPattern = "classpath*:" + p.replaceAll("\\.", "/") + "/**/*" + daoEnd + ".class";
                logger.info("trnas package[" + p + "] to locationPattern[" + locationPattern + "]");
                locationPatterns.add(locationPattern);
            }
        }
        return locationPatterns;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String s) throws BeansException {
        return bean;
    }

    /**
     * 由于Mango没有提供setInterceptor方法，所以通过beanPostProcessor的方式添加interceptor。同时数据源支持内置和引用混合所以也需要这种方式注入
     * @param bean
     * @param s
     * @return
     * @throws BeansException
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String s) throws BeansException {
        if(bean instanceof Mango){
            /*设置拦截器*/
            Mango mango = (Mango) bean;
            List<String> interceptorClassPaths = config.getInterceptors();
            if(interceptorClassPaths != null && interceptorClassPaths.size() != 0){
                for (String interceptorClassPath : interceptorClassPaths){
                    try {
                        Class< ? extends Interceptor> interceptorClz = (Class<? extends Interceptor>) Class.forName(interceptorClassPath);
                        Interceptor interceptor = Reflection.instantiateClass(interceptorClz);
                        mango.addInterceptor(interceptor);
                    } catch (Throwable e) {
                        throw new IllegalStateException(e.getMessage(), e);
                    }
                }
            }
            /*设置datasource*/
            if(factoryBeanClass.equals(DefaultMangoFactoryBean.class)){
                configMangoDatasourceFactory(mango);
            }
        }
        return bean;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }
}
