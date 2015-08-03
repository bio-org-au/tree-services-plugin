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

@Mixin(BuildSampleTreeMixin)
class MoveDraftLinkSpec extends Specification {
	DataSource dataSource_nsl
	SessionFactory sessionFactory_nsl
	static final Log log = LogFactory.getLog(MoveDraftLinkSpec.class)


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

	void "make a sample tree and check it"() {
		when:
		SomeStuff s = makeSampleTree()
		s.reload()

		then:
		s.t
		s.a
		s.b
		s.aa
		s.ab
		s.ba
		s.bb

		s.a != s.aa
		s.a != s.ab
		s.a != s.b
		s.a != s.ba
		s.a != s.bb

		s.aa != s.ab
		s.aa != s.b
		s.aa != s.ba
		s.aa != s.bb

		s.ab != s.b
		s.ab != s.ba
		s.ab != s.bb

		s.b != s.ba
		s.b != s.bb

		s.ba != s.bb

		s.t.node.supLink.size() == 0
		s.a.supLink.size() == 1
		s.aa.supLink.size() == 1
		s.ab.supLink.size() == 1
		s.b.supLink.size() == 1
		s.ba.supLink.size() == 1
		s.bb.supLink.size() == 1

		DomainUtils.getDraftNodeSupernode(s.a) == s.t.node
		DomainUtils.getDraftNodeSuperlink(s.a).linkSeq == 1
		DomainUtils.getDraftNodeSupernode(s.aa) == s.a
		DomainUtils.getDraftNodeSuperlink(s.aa).linkSeq == 1
		DomainUtils.getDraftNodeSupernode(s.ab) == s.a
		DomainUtils.getDraftNodeSuperlink(s.ab).linkSeq == 2

		DomainUtils.getDraftNodeSupernode(s.b) == s.t.node
		DomainUtils.getDraftNodeSuperlink(s.b).linkSeq == 2
		DomainUtils.getDraftNodeSupernode(s.ba) == s.b
		DomainUtils.getDraftNodeSuperlink(s.ba).linkSeq == 1
		DomainUtils.getDraftNodeSupernode(s.bb) == s.b
		DomainUtils.getDraftNodeSuperlink(s.bb).linkSeq == 2

		s.t.node.subLink.size() == 2
		s.a.subLink.size() == 2
		s.aa.subLink.size() == 0
		s.ab.subLink.size() == 0
		s.b.subLink.size() == 2
		s.ba.subLink.size() == 0
		s.bb.subLink.size() == 0

		when:
		basicOperationsService.deleteArrangement(s.t)
		s.reload()

		then:
		!s.t
		!s.a
		!s.b
		!s.aa
		!s.ab
		!s.ba
		!s.bb
	}

	void "perform a simple, legal move"() {
		when:
		SomeStuff s = makeSampleTree()

		then:
		s.a.subLink.size() == 2
		s.b.subLink.size() == 2

		when:
		basicOperationsService.updateDraftNodeLink(s.a, 1, supernode: s.b)
		s.reload()

		then:
		s.a.subLink.size() == 1
		s.b.subLink.size() == 3
		DomainUtils.getDraftNodeSupernode(s.aa) == s.b
		DomainUtils.getDraftNodeSuperlink(s.aa).linkSeq == 3

	}

	void "attempt an illegal move"() {
		when:
		SomeStuff s = makeSampleTree()

		basicOperationsService.updateDraftNodeLink(s.t.node, 1, supernode: s.aa)
		s.reload()

		then:
		thrown ServiceException
	}

	void "attempt a move to a different tree"() {
		when:
		SomeStuff s1 = makeSampleTree()
		SomeStuff s2 = makeSampleTree()

		basicOperationsService.updateDraftNodeLink(s1.a, 1, supernode: s2.b)
		sessionFactory_nsl.currentSession.clear()
		s1.reloadWithoutClear()
		s2.reloadWithoutClear()


		then:
		thrown ServiceException
	}
}


