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
		Event e = basicOperationsService.newEventTs(new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test getStatistics simple')
		
		SomeStuff s1 = makeSampleTree()
		
		QueryService.Statistics s = queryService.getStatistics(s1.t)

		then:
		s
	}

	void "test getDependencies simple"() {
		when:
		Event e = basicOperationsService.newEventTs(new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test getDependencies simple')
		
		SomeStuff s1 = makeSampleTree()
		
		QueryService.Statistics s = queryService.getDependencies(s1.t)

		then:
		s
	}


}