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

package au.org.biodiversity.nsl.tree

import javax.sql.DataSource

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.hibernate.SessionFactory
import au.org.biodiversity.nsl.Event;
import spock.lang.*

@Mixin(BuildSampleTreeMixin)
class QueryServiceSpec extends Specification {
	DataSource dataSource_nsl
	SessionFactory sessionFactory_nsl
	static final Log log = LogFactory.getLog(QueryServiceSpec.class)


	// fields
	BasicOperationsService basicOperationsService
	QueryService queryService
	
	// fixture methods

	def setup() {
	}

	def cleanup() {
	}

	def setupSpec() {
	}

	def cleanupSpec() {
	}

	// feature methods

	void "test getStatistics simple"() {
		when:
		Event e = basicOperationsService.newEventTs(TreeTestUtil.getTestNamespace(), new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test getStatistics simple')
		
		SomeStuff s1 = makeSampleTree()
		
		QueryService.Statistics s = queryService.getStatistics(s1.t)

		then:
		s
	}

	void "test getDependencies simple"() {
		when:
		Event e = basicOperationsService.newEventTs(TreeTestUtil.getTestNamespace(), new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test getDependencies simple')
		
		SomeStuff s1 = makeSampleTree()
		
		QueryService.Statistics s = queryService.getDependencies(s1.t)

		then:
		s
	}


}