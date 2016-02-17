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
class PerformVersioningSpec extends Specification {
	DataSource dataSource_nsl
	SessionFactory sessionFactory_nsl
	static final Log log = LogFactory.getLog(PerformVersioningSpec.class)


	// fields
	BasicOperationsService basicOperationsService
	VersioningService versioningService

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

	void "test versioning" () {
		when:
		Event e = basicOperationsService.newEventTs(TreeTestUtil.getTestNamespace(), new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test versioning')
		SomeStuff s1 = makeSampleTree()
		SomeStuff s2 = makeSampleTree()
		basicOperationsService.persistNode(e, s1.t.node)
		basicOperationsService.persistNode(e, s2.t.node)
		sessionFactory_nsl.currentSession.clear()
		s1.reloadWithoutClear()
		s2.reloadWithoutClear()

		then:
		s1.a
		s2.a

		when:

		Map<Node, Node> replace = new HashMap<Node, Node>()
		replace.put(s1.aa, s2.aa)

		versioningService.performVersioning e, replace, s1.t
		sessionFactory_nsl.currentSession.clear()
		s1.reloadWithoutClear()
		s2.reloadWithoutClear()

		then:
		s1.a.next
		s1.aa.next == s2.aa
		!s1.ab.next

		!s1.b.next
		!s1.ba.next
		!s1.bb.next

		!DomainUtils.isCurrent(s1.a)
		!DomainUtils.isCurrent(s1.aa)
		DomainUtils.isCurrent(s1.ab)

		DomainUtils.isCurrent(s1.b)
		DomainUtils.isCurrent(s1.ba)
		DomainUtils.isCurrent(s1.bb)
	}

	void "test versioning replacing 2" () {
		when:
		Event e = basicOperationsService.newEventTs(TreeTestUtil.getTestNamespace(), new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test versioning replacing 2')
		SomeStuff s1 = makeSampleTree()
		SomeStuff s2 = makeSampleTree()
		basicOperationsService.persistNode(e, s1.t.node)
		basicOperationsService.persistNode(e, s2.t.node)
		sessionFactory_nsl.currentSession.clear()
		s1.reloadWithoutClear()
		s2.reloadWithoutClear()

		then:
		s1.a
		s2.a

		when:

		Map<Node, Node> replace = new HashMap<Node, Node>()
		replace.put(s1.aa, s2.aa)
		replace.put(s1.ab, s2.ab)

		versioningService.performVersioning e, replace, s1.t
		sessionFactory_nsl.currentSession.clear()
		s1.reloadWithoutClear()
		s2.reloadWithoutClear()

		dumpStuff([s1, s2])

		then:
		s1.a.next
		s1.aa.next == s2.aa
		s1.ab.next == s2.ab

		!s1.b.next
		!s1.ba.next
		!s1.bb.next

		!DomainUtils.isCurrent(s1.a)
		!DomainUtils.isCurrent(s1.aa)
		!DomainUtils.isCurrent(s1.ab)

		DomainUtils.isCurrent(s1.b)
		DomainUtils.isCurrent(s1.ba)
		DomainUtils.isCurrent(s1.bb)
	}

	void "test versioning replacing parent and child" () {
		when:
		Event e = basicOperationsService.newEventTs(TreeTestUtil.getTestNamespace(), new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test versioning replacing parent and child')
		SomeStuff s1 = makeSampleTree()
		SomeStuff s2 = makeSampleTree()
		basicOperationsService.persistNode(e, s1.t.node)
		basicOperationsService.persistNode(e, s2.t.node)
		sessionFactory_nsl.currentSession.clear()
		s1.reloadWithoutClear()
		s2.reloadWithoutClear()

		then:
		s1.a
		s2.a

		when:

		Map<Node, Node> replace = new HashMap<Node, Node>()
		replace.put(s1.a, s2.a)
		replace.put(s1.aa, s2.aa)

		versioningService.performVersioning e, replace, s1.t
		sessionFactory_nsl.currentSession.clear()
		s1.reloadWithoutClear()
		s2.reloadWithoutClear()

		dumpStuff([s1, s2])

		then:
		thrown ServiceException
	}

	void "test versioning replacing parent and child without orphans" () {
		when:
		Event e = basicOperationsService.newEventTs(TreeTestUtil.getTestNamespace(), new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test versioning replacing parent and child without orphans')
		SomeStuff s1 = makeSampleTree()
		SomeStuff s2 = makeSampleTree()
		basicOperationsService.persistNode(e, s1.t.node)
		basicOperationsService.persistNode(e, s2.t.node)
		sessionFactory_nsl.currentSession.clear()
		s1.reloadWithoutClear()
		s2.reloadWithoutClear()

		then:
		s1.a
		s2.a

		when:

		Map<Node, Node> replace = new HashMap<Node, Node>()
		replace.put(s1.a, s2.a)
		replace.put(s1.aa, s2.aa)
		replace.put(s1.ab, DomainUtils.getEndNode())

		versioningService.performVersioning e, replace, s1.t
		sessionFactory_nsl.currentSession.clear()
		s1.reloadWithoutClear()
		s2.reloadWithoutClear()

		dumpStuff([s1, s2])

		then:
		s1.a.next == s2.a
		s1.aa.next == s2.aa
		s1.ab.next == DomainUtils.getEndNode()

		!s1.b.next
		!s1.ba.next
		!s1.bb.next

		!DomainUtils.isCurrent(s1.a)
		!DomainUtils.isCurrent(s1.aa)
		!DomainUtils.isCurrent(s1.ab)

		DomainUtils.isCurrent(s1.b)
		DomainUtils.isCurrent(s1.ba)
		DomainUtils.isCurrent(s1.bb)
	}

	void "test a more-or-less believable versioning"() {
		/**
		 * Ok! I am going to checkout node aa and modify it, checkout node b, delete bb, add bc,
		 * then persist and version the changes
		 */
		when:
		Event e = basicOperationsService.newEventTs(TreeTestUtil.getTestNamespace(), new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test a more-or-less believable versioning')
		SomeStuffWithHistory afd = makeSampleTreeWithHistory()
		SomeStuffEmptyTree ws = makeSampleEmptyTree()

		Closure reset = {
			sessionFactory_nsl.currentSession.clear()
			afd.reloadWithoutClear()
			ws.reloadWithoutClear()
		}

		basicOperationsService.persistNode(e, afd.t.node)
		reset()

		basicOperationsService.adoptNode(ws.t.node, afd.aa, VersioningMethod.V)
		reset()

		long newAAid = basicOperationsService.checkoutNode(ws.t.node, afd.aa).id
		reset()

		basicOperationsService.updateDraftNode Node.get(newAAid), name: DomainUtils.uri('afd-name', 'newAA')
		reset()

		basicOperationsService.adoptNode(ws.t.node, afd.b, VersioningMethod.V)
		reset()

		long newBid = basicOperationsService.checkoutNode(ws.t.node, afd.b).id
		reset()

		basicOperationsService.deleteLink Node.get(newBid), 2
		reset()

		Node newBC = basicOperationsService.createDraftNode Node.get(newBid), VersioningMethod.V, NodeInternalType.T, seq: 3, name: DomainUtils.uri('afd-name', 'newBC')
		reset()

		basicOperationsService.persistNode e, ws.t.node
		reset()

		BuildSampleTreeUtil.dumpStuff sessionFactory_nsl, log, [afd, ws]

		Map<Node, Node> version = new HashMap<Node, Node>()

		version.put(afd.aa, Node.get(newAAid))
		version.put(afd.b, Node.get(newBid))
		version.put(afd.bb, DomainUtils.getEndNode())

		log.debug "replacing ${afd.bb} with ${version.get(afd.bb)}"

		versioningService.performVersioning (e, version, afd.t)
		reset()

		BuildSampleTreeUtil.dumpStuff sessionFactory_nsl, log, [afd, ws]

		basicOperationsService.moveFinalNodesFromTreeToTree ws.t, afd.t
		reset()

		basicOperationsService.deleteArrangement(ws.t)
		// dont use reset, because ws.t will be cleared and I want to see that it's properly gone
		sessionFactory_nsl.currentSession.clear()
		afd.reloadWithoutClear()

		BuildSampleTreeUtil.dumpStuff sessionFactory_nsl, log, [afd, ws]

		then:
		1 == 1


	}

}