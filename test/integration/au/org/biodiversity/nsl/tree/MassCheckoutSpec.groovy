package au.org.biodiversity.nsl.tree

import javax.sql.DataSource

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.hibernate.SessionFactory

import spock.lang.*
import au.org.biodiversity.nsl.*;

@Mixin(BuildSampleTreeMixin)

public class MassCheckoutSpec extends Specification {
	DataSource dataSource_nsl
	SessionFactory sessionFactory_nsl
	static final Log log = LogFactory.getLog(DomainSpec.class)


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

	def 'attempt a mass checkout'() {
		Link[] sub;
		
		
		when:
		Event e = basicOperationsService.newEventTs(new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'attempt a mass checkout')

		SomeStuffEmptyTree s1 = makeSampleEmptyTree() // the working tree
		SomeStuff s2 = makeSampleTree() // some sample data

		basicOperationsService.persistNode(e, s2.t.node)
		sessionFactory_nsl.currentSession.clear()
		s1.reloadWithoutClear()
		s2.reloadWithoutClear()

		// we have to adopt a and b separately because the somestuff root node
		// never gets persisted
		
		basicOperationsService.adoptNode s1.root, s2.a, VersioningMethod.F
		basicOperationsService.adoptNode s1.root, s2.b, VersioningMethod.F
		sessionFactory_nsl.currentSession.clear()
		s1.reloadWithoutClear()
		s2.reloadWithoutClear()

		basicOperationsService.massCheckout(s1.t.node, [s2.aa, s2.b])
		sessionFactory_nsl.currentSession.clear()
		s1.reloadWithoutClear()
		s2.reloadWithoutClear()

		// check that the new nodes have been inserted into the draft tree.

		then:
			// FIRST: I want to check that my sample tree s2 is unaffected
			// this means checking all seven nodes of the tree. Sorry

			// check root node
			s2.t.node == s2.root
			!DomainUtils.isCheckedIn(s2.root)
			
		when:	
			sub = DomainUtils.getSublinksAsArray(s2.root)
		then:
			sub.length == 3
			sub[1].subnode == s2.a
			sub[2].subnode == s2.b

			// check node a
			DomainUtils.isCheckedIn(s2.a)
		when:	
			sub = DomainUtils.getSublinksAsArray(s2.a)
		then:
			sub.length == 3
			sub[1].subnode == s2.aa
			sub[2].subnode == s2.ab

			// check node aa
			DomainUtils.isCheckedIn(s2.aa)
		when:	
			sub = DomainUtils.getSublinksAsArray(s2.aa)
		then:
			sub.length == 1

			// check node ab
			DomainUtils.isCheckedIn(s2.ab)
		when:	
			sub = DomainUtils.getSublinksAsArray(s2.ab)
		then:
			sub.length == 1

			// check node b
			DomainUtils.isCheckedIn(s2.b)
		when:	
			sub = DomainUtils.getSublinksAsArray(s2.b)
		then:
			sub.length == 3
			sub[1].subnode == s2.ba
			sub[2].subnode == s2.bb

			// check node aa
			DomainUtils.isCheckedIn(s2.ba)
		when:	
			sub = DomainUtils.getSublinksAsArray(s2.ba)
		then:
			sub.length == 1

			// check node ab
			DomainUtils.isCheckedIn(s2.bb)
		when:	
			sub = DomainUtils.getSublinksAsArray(s2.bb)
		then:
			sub.length == 1

		// OKAY!!!  Now to test the work tree, which should have various checked-out subnodes
			
			// root should be a draft node with one subnode - a checked out version of s2.root
			
			s1.t.node == s1.root
			!DomainUtils.isCheckedIn(s2.root)

		when:	
			sub = DomainUtils.getSublinksAsArray(s1.t.node)
		then:
			sub.length == 3
			
			!DomainUtils.isCheckedIn(sub[1].subnode)
			sub[1].subnode != s2.a
			sub[1].subnode.prev == s2.a
			
			!DomainUtils.isCheckedIn(sub[2].subnode)
			sub[2].subnode != s2.b
			sub[2].subnode.prev == s2.b
			
			when:
			sub = DomainUtils.getSublinksAsArray(s1.t.node)
			sub = DomainUtils.getSublinksAsArray(sub[1].subnode)
			
			then:
			sub.length == 3

			!DomainUtils.isCheckedIn(sub[1].subnode)
			sub[1].subnode != s2.aa
			sub[1].subnode.prev == s2.aa
			DomainUtils.getSublinksAsArray(sub[1].subnode).length == 1
			
			DomainUtils.isCheckedIn(sub[2].subnode)
			sub[2].subnode == s2.ab
			DomainUtils.getSublinksAsArray(sub[2].subnode).length == 1
			
			when:
			sub = DomainUtils.getSublinksAsArray(s1.t.node)
			sub = DomainUtils.getSublinksAsArray(sub[2].subnode)
			
			then:
			sub.length == 3

			DomainUtils.isCheckedIn(sub[2].subnode)
			sub[1].subnode == s2.ba
			DomainUtils.getSublinksAsArray(sub[1].subnode).length == 1
			
			DomainUtils.isCheckedIn(sub[2].subnode)
			sub[2].subnode == s2.bb
			DomainUtils.getSublinksAsArray(sub[2].subnode).length == 1
			

	}
}
