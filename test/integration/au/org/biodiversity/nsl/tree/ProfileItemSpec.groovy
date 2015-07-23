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
