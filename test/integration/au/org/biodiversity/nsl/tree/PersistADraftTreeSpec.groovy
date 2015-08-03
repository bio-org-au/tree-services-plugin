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

import au.org.biodiversity.nsl.Event
import org.hibernate.SessionFactory
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection

@Mixin(BuildSampleTreeMixin)
class PersistADraftTreeSpec extends Specification {
    DataSource dataSource_nsl
    SessionFactory sessionFactory_nsl

    def queryService

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

    void "make a sample tree and persist it"() {
        when:
        Event e = basicOperationsService.newEventTs(new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'make a sample tree and persist it')
        SomeStuff s = makeSampleTree()

        println queryService.dumpNodes([s.t.node])

//		s.reload()

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
        basicOperationsService.persistNode(e, s.t.node)
        s.reload()

        then:
        !DomainUtils.isCheckedIn(s.t.node)
        DomainUtils.isCheckedIn(s.a)
        DomainUtils.isCheckedIn(s.aa)
        DomainUtils.isCheckedIn(s.ab)
        DomainUtils.isCheckedIn(s.b)
        DomainUtils.isCheckedIn(s.ba)
        DomainUtils.isCheckedIn(s.bb)
    }

    void "put tree in an illegal state and attempt to persist it"() {
        when:
        Event e = basicOperationsService.newEventTs(new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'put tree in an illegal state and attempt to persist it')
        SomeStuff s1 = makeSampleTree()
        SomeStuff s2 = makeSampleTree()

        HibernateSessionUtils.doWork sessionFactory_nsl, { Connection cnct ->
            cnct.createStatement().executeUpdate("""
					insert into tree_link (
					 ID,					   --NOT NULL NUMBER(38)
					 LOCK_VERSION,				   --NOT NULL NUMBER(38)
					 SUPERNODE_ID,			   --NOT NULL NUMBER(38)
					 SUBNODE_ID,			   --NOT NULL NUMBER(38)
					 TYPE_URI_NS_PART_ID,	   -- NUMBER(38)
					 TYPE_URI_ID_PART,		   -- VARCHAR2(255)
					 LINK_SEQ,				   --NOT NULL NUMBER(38)
					 VERSIONING_METHOD,		   --NOT NULL CHAR(1)
					 IS_SYNTHETIC			   --NOT NULL CHAR(1)
					)
					values (
						nextval('nsl_global_seq'),
						1,
						${s1.bbid},
						${s2.aaid},
						0,
						null,
						1,
						'V',
						'N'
					)		
			""")
        }

        basicOperationsService.persistNode(e, s1.t.node)

        then:
        thrown IllegalStateException

    }

}
