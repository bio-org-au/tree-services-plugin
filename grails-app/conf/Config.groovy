// configuration for plugin testing - will not be included in the plugin zip


// Set to false to use the new Grails 1.2 JSONBuilder in the render method
grails.json.legacy.builder = false
// enabled native2ascii conversion of i18n properties files
grails.enable.native2ascii = true
// packages to include in Spring bean scanning
grails.spring.bean.packages = []
// whether to disable processing of multi part requests
grails.web.disable.multipart = false

// request parameters to mask when logging exceptions
grails.exceptionresolver.params.exclude = ['password']

// configure auto-caching of queries by default (if false you can cache individual queries with 'cache: true')
grails.hibernate.cache.queries = false

// configure passing transaction's read-only attribute to Hibernate session, queries and criterias
// set "singleSession = false" OSIV mode in hibernate configuration after enabling
grails.hibernate.pass.readonly = false
// configure passing read-only to OSIV session by default, requires "singleSession = false" OSIV mode
grails.hibernate.osiv.readonly = false

grails.gorm.failOnError = true

grails.web.url.converter = 'hyphenated'

log4j = {
    // Example of changing the log pattern for the default console
    // appender:
    //
    //appenders {
    //    console name:'stdout', layout:pattern(conversionPattern: '%c{2} %m%n')
    //}

    error  'org.codehaus.groovy.grails.web.servlet',  //  controllers
           'org.codehaus.groovy.grails.web.pages', //  GSP
           'org.codehaus.groovy.grails.web.sitemesh', //  layouts
           'org.codehaus.groovy.grails.web.mapping.filter', // URL mapping
           'org.codehaus.groovy.grails.web.mapping', // URL mapping
           'org.codehaus.groovy.grails.commons', // core / classloading
           'org.codehaus.groovy.grails.plugins', // plugins
           'org.codehaus.groovy.grails.orm.hibernate', // hibernate integration
           'org.springframework',
           'org.hibernate',
           'net.sf.ehcache.hibernate'

    debug 'grails.app'
}

grails.assets.minifyCss = false
grails.assets.minifyJs = false

cors.url.pattern = '/*'
cors.headers = ['Access-Control-Allow-Origin': '*']

nslServices.system.message.file = "${userHome}/.nsl/broadcast.txt"
nslServices.temp.file.directory = "/tmp"

nslTreePlugin.nslInstanceNamespace = 'nsl-instance'
nslTreePlugin.nslNameNamespace = 'nsl-name'

services.mapper.apikey = 'not set'
services.link.mapperURL = 'http://localhost:7070/nsl-mapper'
services.link.internalMapperURL = 'http://localhost:7070/nsl-mapper'
services.link.editor = 'https://biodiversity.org.au/test-nsl-editor'