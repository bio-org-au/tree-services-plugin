/*
    Copyright 2015 Australian National Botanic Gardens

    This file is part of NSL tree services plugin project.

    Licensed under the Apache License, Version 2.0 (the "License"); you may not
    use this file except in compliance with the License. You may obtain a copy
    of the License at http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"

grails.project.fork = [
    // configure settings for compilation JVM, note that if you alter the Groovy version forked compilation is required
    //  compile: [maxMemory: 256, minMemory: 64, debug: false, maxPerm: 256, daemon:true],

    // configure settings for the test-app JVM, uses the daemon by default
    test: false,//[maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, daemon:true],
    // configure settings for the run-app JVM
    run: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, forkReserve:false],
    // configure settings for the run-war JVM
    war: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, forkReserve:false],
    // configure settings for the Console UI JVM
    console: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256]
]

grails.project.dependency.resolver = "maven" // or ivy
grails.project.repos.default = "snapshots"

grails.project.repos.snapshots.url = "http://155.187.10.62:8081/nexus/content/repositories/snapshots/"
grails.project.repos.snapshots.username = "nsldev"
grails.project.repos.snapshots.password = "nsldev"

grails.project.repos.releases.url = "http://155.187.10.62:8081/nexus/content/repositories/releases/"
grails.project.repos.releases.username = "nsldev"
grails.project.repos.releases.password = "nsldev"

grails.project.ivy.authentication = {
	repositories {
        mavenLocal()
		mavenRepo("http://155.187.10.62:8081/nexus/content/groups/public/") 
	}

    credentials {
        realm = "Sonatype Nexus Repository Manager"
        host = "155.187.10.62"
        username = "nsldev"
        password = "nsldev"
    }
}

grails.plugin.location."nslDomainPlugin" = "../nsl-domain-plugin"

grails.project.dependency.resolution = {
    // inherit Grails' default dependencies
    inherits("global") {
        // uncomment to disable ehcache
        // excludes 'ehcache'
    }
    log "warn" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
    repositories {
        mavenRepo("http://155.187.10.62:8081/nexus/content/groups/public/")
        mavenRepo("http://155.187.10.62:8081/nexus/content/repositories/grails-plugins")
		grailsHome()
        mavenLocal()
		
    }
    dependencies {
        runtime 'org.postgresql:postgresql:9.3-1101-jdbc41'
        test "org.grails:grails-datastore-test-support:1.0-grails-2.4"
    }

    plugins {
        build(":release:3.0.1",
              ":rest-client-builder:2.0.3") {
            export = false
        }

        runtime(":hibernate4:4.3.5.5") {
            export = false
        }

        compile ":scaffolding:2.1.2"

        compile ":shiro:1.2.1", {
            excludes([name: 'quartz', group: 'org.opensymphony.quartz']) // why do we need this?
            export = false
        }

//		compile ('au.org.biodiversity.grails.plugins:nsl-domain-plugin:1.1-SNAPSHOT'){
//			excludes "scaffolding"
//			excludes "cache"
//		}
		
    }
}