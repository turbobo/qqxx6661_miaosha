package cn.monitor4all.miaoshaservice.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import javax.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * MyBatis配置类
 */
@Configuration
@MapperScan("cn.monitor4all.miaoshadao.mapper")
public class MyBatisConfig {
    
    @Resource
    private DataSource dataSource;
    
    @Bean
    public SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);
        
        // 设置Mapper XML文件位置
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        sessionFactory.setMapperLocations(
            resolver.getResources("classpath*:mapper/*.xml")
        );
        
        // 设置类型别名包
        sessionFactory.setTypeAliasesPackage("cn.monitor4all.miaoshadao.dao");
        
        return sessionFactory.getObject();
    }
}
