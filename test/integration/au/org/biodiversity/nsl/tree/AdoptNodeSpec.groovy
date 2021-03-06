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
import au.org.biodiversity.nsl.Link;
import au.org.biodiversity.nsl.VersioningMethod;
import spock.lang.*

@Mixin(BuildSampleTreeMixin)
class AdoptNodeSpec extends Specification {
	DataSource dataSource_nsl
	SessionFactory sessionFactory_nsl
	static final Log log = LogFactory.getLog(AdoptNodeSpec.class)


	// fields
	BasicOperationsService basicOperationsService

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

	void "test simple node adoption"() {
		when:
		Event e = basicOperationsService.newEventTs(TreeTestUtil.getTestNamespace(), new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test simple node adoption')
		
		SomeStuff s1 = makeSampleTree()
		SomeStuff s2 = makeSampleTree()

		basicOperationsService.persistNode(e, s2.t.node)
		sessionFactory_nsl.currentSession.clear()
		s1.reloadWithoutClear()
		s2.reloadWithoutClear()

		then:
		!DomainUtils.isCheckedIn(s1.a)
		DomainUtils.isCheckedIn(s2.a)

		when:
		basicOperationsService.adoptNode s1.a, s2.a, VersioningMethod.V, seq: 5
		sessionFactory_nsl.currentSession.clear()
		s1.reloadWithoutClear()
		s2.reloadWithoutClear()

		then:
		s1.a.subLink.size() == 3
		s2.a.supLink.size() == 2

		when:
		Link[] l = DomainUtils.getSublinksAsArray(s1.a)

		then:
		l[1].subnode == s1.aa
		l[2].subnode == s1.ab
		l[5].subnode == s2.a
	}

	void "test adopting into persistent node"() {
		when:
		Event e = basicOperationsService.newEventTs(TreeTestUtil.getTestNamespace(), new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test adopting into persistent node')
		
		SomeStuff s1 = makeSampleTree()
		SomeStuff s2 = makeSampleTree()

		basicOperationsService.persistNode(e, s1.t.node)
		sessionFactory_nsl.currentSession.clear()
		s1.reloadWithoutClear()
		s2.reloadWithoutClear()

		basicOperationsService.persistNode(e, s2.t.node)
		sessionFactory_nsl.currentSession.clear()
		s1.reloadWithoutClear()
		s2.reloadWithoutClear()

		then:
		DomainUtils.isCheckedIn(s1.a)
		DomainUtils.isCheckedIn(s2.a)

		when:

		basicOperationsService.adoptNode s1.a, s2.a, VersioningMethod.V
		sessionFactory_nsl.currentSession.clear()
		s1.reloadWithoutClear()
		s2.reloadWithoutClear()

		then:
		thrown ServiceException
	}

	void "test adopting idraft node"() {
		when:
		SomeStuff s1 = makeSampleTree()
		SomeStuff s2 = makeSampleTree()
		then:
		!DomainUtils.isCheckedIn(s1.a)
		!DomainUtils.isCheckedIn(s2.a)

		when:
		basicOperationsService.adoptNode s1.a, s2.a, VersioningMethod.V
		sessionFactory_nsl.currentSession.clear()
		s1.reloadWithoutClear()
		s2.reloadWithoutClear()

		then:
		thrown ServiceException
	}


}