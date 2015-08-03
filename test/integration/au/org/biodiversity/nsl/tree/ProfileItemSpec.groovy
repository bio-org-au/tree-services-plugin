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

import spock.lang.*
import au.org.biodiversity.nsl.Event
import au.org.biodiversity.nsl.Node
import au.org.biodiversity.nsl.NodeInternalType
import au.org.biodiversity.nsl.VersioningMethod

@Mixin(BuildSampleTreeMixin)

class ProfileItemSpec  extends Specification {
	DataSource dataSource_nsl
	SessionFactory sessionFactory_nsl
	static final Log log = LogFactory.getLog(ProfileItemSpec.class)


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

	def 'simple create and update'() {
		when:
		Event e = basicOperationsService.newEventTs(new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'simple create and update')
		SomeStuff s = makeSampleTree()
		s.reload()

		then:
		s
		
		when:
		Node n = basicOperationsService.createDraftNode s.a, VersioningMethod.F, NodeInternalType.V, literal: 'test1'
		s.reload()

		then:
		n
		n.literal == 'test1'
		
		when:
		basicOperationsService.updateDraftNode n, literal: 'test2'
		s.reload()
		n = DomainUtils.refetchNode(n)

		then:
		n.literal == 'test2'
	}

}
