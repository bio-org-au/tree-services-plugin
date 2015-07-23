dataSource {
    pooled = true
    jmxExport = true

    // configured for "production" being the jenkins instance
    maxActive = 5
    initialSize = 2
}

hibernate {
    cache.use_second_level_cache = false
    cache.use_query_cache = false
    //cache.region.factory_class = 'net.sf.ehcache.hibernate.EhCacheRegionFactory' // Hibernate 3
    cache.region.factory_class = 'org.hibernate.cache.ehcache.EhCacheRegionFactory' // Hibernate 4
    //	singleSession = true // configure OSIV singleSession mode
}

// environment specific settings
environments {
    development {
        dataSource_nsl {
            dbCreate = "update" // one of 'create', 'create-drop', 'update', 'validate', ''
            dialect = "org.hibernate.dialect.PostgreSQLDialect"
            driverClassName = 'org.postgresql.Driver'
            url = 'jdbc:postgresql://localhost:5432/nsl?prepareThreshold=2'
            username = 'nsldev'
            password = 'nsldev'
            persistenceInterceptor = true
        }
    }
    test {
        dataSource_nsl {
            dbCreate = "validate" // one of 'create', 'create-drop', 'update', 'validate', ''
            dialect = "org.hibernate.dialect.PostgreSQLDialect"
            driverClassName = 'org.postgresql.Driver'
            url = 'jdbc:postgresql://localhost:5432/nsl?prepareThreshold=2'
            username = 'nsldev'
            password = 'nsldev'
            persistenceInterceptor = true
            formatSql = false
            logSql = false
        }
    }
}
